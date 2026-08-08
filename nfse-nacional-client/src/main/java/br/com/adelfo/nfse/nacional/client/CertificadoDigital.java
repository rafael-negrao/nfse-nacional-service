package br.com.adelfo.nfse.nacional.client;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Value object que encapsula o certificado digital A1 do emitente.
 *
 * <p>A aplicação consumidora carrega os bytes do {@code .pfx} de onde quiser (banco, Secrets
 * Manager, cofre) e expõe esta instância como {@code @Bean}. <b>Esta biblioteca nunca acessa
 * arquivo, banco ou variável de ambiente para obter o certificado.</b>
 *
 * <p>Factory methods para montar o truststore em tempo de execução:
 * <ul>
 *   <li>{@link #comTruststoreIcpBrasil(byte[], char[])} — baixa as ACs Raiz do repositório ITI;</li>
 *   <li>{@link #comTruststoreCapturaDoServidor(byte[], char[], String, int)} — captura a cadeia
 *       apresentada pelo servidor, útil quando o ITI não é acessível.</li>
 * </ul>
 */
public final class CertificadoDigital {

    private static final char[] TRUSTSTORE_SENHA_PADRAO = "changeit".toCharArray();

    /** Conteúdo do PKCS#12 (.pfx / .p12) como bytes. */
    private final byte[] pkcs12Bytes;

    /** Senha do PKCS#12. */
    private final char[] senha;

    /** Truststore JKS com a cadeia ICP-Brasil. */
    private final byte[] truststoreBytes;

    /** Senha do truststore. */
    private final char[] senhaTruststore;

    public CertificadoDigital(byte[] pkcs12Bytes, char[] senha,
                              byte[] truststoreBytes, char[] senhaTruststore) {
        this.pkcs12Bytes = pkcs12Bytes;
        this.senha = senha;
        this.truststoreBytes = truststoreBytes;
        this.senhaTruststore = senhaTruststore;
    }

    public byte[] getPkcs12Bytes() {
        return pkcs12Bytes;
    }

    public char[] getSenha() {
        return senha;
    }

    public byte[] getTruststoreBytes() {
        return truststoreBytes;
    }

    public char[] getSenhaTruststore() {
        return senhaTruststore;
    }

    public KeyStore toKeyStore() throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(new ByteArrayInputStream(pkcs12Bytes), senha);
        return ks;
    }

    /**
     * CNPJ (14 dígitos) ou CPF (11) do titular do certificado.
     *
     * <p>É o valor que precisa ir em {@code CNPJAutor}/{@code CPFAutor} do pedido de registro de
     * evento: a regra {@code E0812} do AnexoII exige que o autor informado corresponda ao titular
     * do certificado que assina — comparando apenas o CNPJ base.
     *
     * <p>Extraído do {@code subjectAltName}, no {@code OtherName} dos OIDs que a ICP-Brasil
     * reserva — {@code 2.16.76.1.3.3} para CNPJ e {@code 2.16.76.1.3.1} para CPF, os mesmos que as
     * regras {@code E1986}/{@code E2026} citam. Só na ausência deles é que se recorre ao CN do
     * titular, que é convenção e não garantia.
     */
    public String inscricaoFederal() throws Exception {
        KeyStore ks = toKeyStore();
        X509Certificate cert = (X509Certificate) ks.getCertificate(ks.aliases().nextElement());

        String cnpj = extrairDoOtherName(cert, OID_CNPJ, 14);
        if (cnpj != null) {
            return cnpj;
        }
        // No OtherName de pessoa física o conteúdo é uma cadeia posicional: data de nascimento (8)
        // seguida do CPF (11). Pegar a primeira sequência de 19 dígitos e cortar os 8 iniciais.
        String pf = extrairDoOtherName(cert, OID_CPF, 19);
        if (pf != null) {
            return pf.substring(8);
        }

        String subject = cert.getSubjectX500Principal().getName();
        Matcher m = Pattern.compile("(\\d{14})").matcher(subject.replaceAll("[./-]", ""));
        if (m.find()) {
            return m.group(1);
        }
        throw new IllegalStateException(
                "Não foi possível extrair CNPJ ou CPF do certificado. Subject: " + subject);
    }

    /** OID que a ICP-Brasil usa para o CNPJ do titular no subjectAltName. */
    private static final String OID_CNPJ = "2.16.76.1.3.3";

    /** OID que a ICP-Brasil usa para os dados de pessoa física, com o CPF embutido. */
    private static final String OID_CPF = "2.16.76.1.3.1";

    /**
     * Primeira sequência de {@code digitos} algarismos consecutivos dentro do OtherName do OID.
     *
     * <p>O valor é DER, e decodificá-lo por completo exigiria um parser ASN.1. Como o conteúdo
     * destes OIDs é sempre uma cadeia de dígitos ASCII, varrer os bytes imprimíveis é suficiente e
     * não depende da variação de codificação entre ACs.
     */
    private static String extrairDoOtherName(X509Certificate cert, String oid, int digitos)
            throws Exception {
        if (cert.getSubjectAlternativeNames() == null) {
            return null;
        }
        for (List<?> entrada : cert.getSubjectAlternativeNames()) {
            // Tipo 0 = otherName; o segundo elemento é o valor, que pode vir como byte[] DER.
            if (entrada.size() < 2 || !Integer.valueOf(0).equals(entrada.get(0))) {
                continue;
            }
            Object valor = entrada.get(1);
            if (!(valor instanceof byte[] der)) {
                continue;
            }
            if (!contemOidDer(der, oid)) {
                continue;
            }
            String ascii = new String(der, java.nio.charset.StandardCharsets.ISO_8859_1);
            Matcher m = Pattern.compile("(\\d{" + digitos + ",})").matcher(ascii);
            if (m.find()) {
                return m.group(1).substring(0, digitos);
            }
        }
        return null;
    }

    /** Verifica se o DER contém o OID codificado, que é como ele de fato aparece nos bytes. */
    private static boolean contemOidDer(byte[] der, String oid) {
        byte[] alvo = derDoOid(oid);
        outer:
        for (int i = 0; i + alvo.length <= der.length; i++) {
            for (int j = 0; j < alvo.length; j++) {
                if (der[i + j] != alvo[j]) continue outer;
            }
            return true;
        }
        return false;
    }

    /** Codificação DER do corpo de um OID (sem a tag e o comprimento). */
    private static byte[] derDoOid(String oid) {
        String[] partes = oid.split("\\.");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(Integer.parseInt(partes[0]) * 40 + Integer.parseInt(partes[1]));
        for (int i = 2; i < partes.length; i++) {
            long v = Long.parseLong(partes[i]);
            ByteArrayOutputStream tmp = new ByteArrayOutputStream();
            tmp.write((int) (v & 0x7F));
            v >>= 7;
            while (v > 0) {
                tmp.write((int) ((v & 0x7F) | 0x80));
                v >>= 7;
            }
            byte[] b = tmp.toByteArray();
            for (int k = b.length - 1; k >= 0; k--) {
                out.write(b[k]);
            }
        }
        return out.toByteArray();
    }

    public KeyStore toTrustStore() throws Exception {
        KeyStore ts = KeyStore.getInstance("JKS");
        ts.load(new ByteArrayInputStream(truststoreBytes), senhaTruststore);
        return ts;
    }

    // -------------------------------------------------------------------------
    // Factory methods — truststore em memória
    // -------------------------------------------------------------------------

    /**
     * Cria um {@code CertificadoDigital} baixando as ACs Raiz ICP-Brasil do repositório ITI
     * em tempo de execução, direto em memória (sem gravação em disco).
     *
     * <p>Tenta as URLs do ITI em sequência; basta uma ter sucesso para montar o truststore.
     * Requer conectividade com {@code acraiz.icpbrasil.gov.br}.
     */
    public static CertificadoDigital comTruststoreIcpBrasil(byte[] pkcs12Bytes, char[] senha)
            throws Exception {
        byte[] ts = baixarTruststoreIcpBrasil();
        return new CertificadoDigital(pkcs12Bytes, senha, ts, TRUSTSTORE_SENHA_PADRAO);
    }

    /**
     * Cria um {@code CertificadoDigital} capturando a cadeia de certificados diretamente do
     * servidor alvo via TLS (sem validar a cadeia — apenas para captura).
     *
     * @param host host do servidor (ex.: {@code "sefin.producaorestrita.nfse.gov.br"})
     * @param port porta TLS (normalmente {@code 443})
     */
    public static CertificadoDigital comTruststoreCapturaDoServidor(
            byte[] pkcs12Bytes, char[] senha, String host, int port) throws Exception {
        byte[] ts = capturarTruststoreDoServidor(host, port);
        return new CertificadoDigital(pkcs12Bytes, senha, ts, TRUSTSTORE_SENHA_PADRAO);
    }

    /**
     * Como {@link #comTruststoreCapturaDoServidor(byte[], char[], String, int)}, mas reunindo as
     * cadeias de vários hosts na porta 443.
     *
     * <p>O Sistema Nacional NFS-e é servido por dois hosts distintos — Sefin Nacional e ADN — com
     * cadeias distintas. Um truststore montado só com a cadeia de um deles derruba as chamadas ao
     * outro no handshake TLS, e o erro que chega é um {@code IOException} genérico que parece
     * indisponibilidade de rede.
     */
    public static CertificadoDigital comTruststoreCapturaDosServidores(
            byte[] pkcs12Bytes, char[] senha, String... hosts) throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        KeyStore ts = KeyStore.getInstance("JKS");
        ts.load(null, null);

        int adicionados = 0;
        List<String> erros = new ArrayList<>();
        for (String host : hosts) {
            try {
                KeyStore parcial = KeyStore.getInstance("JKS");
                parcial.load(new ByteArrayInputStream(capturarTruststoreDoServidor(host, 443)),
                        TRUSTSTORE_SENHA_PADRAO);
                for (String alias : java.util.Collections.list(parcial.aliases())) {
                    ts.setCertificateEntry(host + "_" + alias, parcial.getCertificate(alias));
                    adicionados++;
                }
            } catch (Exception e) {
                erros.add(host + ": " + e.getMessage());
            }
        }
        if (adicionados == 0) {
            throw new IllegalStateException("Nenhuma cadeia capturada. Erros: " + erros);
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ts.store(out, TRUSTSTORE_SENHA_PADRAO);
        return new CertificadoDigital(pkcs12Bytes, senha, out.toByteArray(), TRUSTSTORE_SENHA_PADRAO);
    }

    // -------------------------------------------------------------------------
    // Download / captura — utilizáveis isoladamente
    // -------------------------------------------------------------------------

    /**
     * Baixa as ACs Raiz ICP-Brasil do repositório ITI e devolve um JKS em memória.
     * Tenta múltiplas URLs; basta uma ter êxito.
     */
    public static byte[] baixarTruststoreIcpBrasil() throws Exception {
        String[] urls = {
                "http://acraiz.icpbrasil.gov.br/download/ACRaizV5.crt",
                "http://acraiz.icpbrasil.gov.br/download/ACRaizV2.crt",
                "http://acraiz.icpbrasil.gov.br/download/ACRaizV1.crt",
                "http://acraiz.icpbrasil.gov.br/download/ICP-Brasilv5.crt",
                "http://acraiz.icpbrasil.gov.br/download/ICP-Brasilv2.crt",
                "http://acraiz.icpbrasil.gov.br/download/ICP-Brasilv1.crt",
        };

        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        KeyStore ts = KeyStore.getInstance("JKS");
        ts.load(null, null);
        int adicionados = 0;
        List<String> erros = new ArrayList<>();

        for (String url : urls) {
            try {
                byte[] bytes = httpGet(url, 15_000);
                Certificate cert = cf.generateCertificate(new ByteArrayInputStream(bytes));
                ts.setCertificateEntry("icpbrasil_" + (++adicionados), cert);
            } catch (Exception e) {
                erros.add(url + ": " + e.getMessage());
            }
        }

        if (adicionados == 0) {
            throw new IllegalStateException(
                    "Não foi possível baixar nenhuma AC Raiz ICP-Brasil. Erros: " + erros);
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ts.store(out, TRUSTSTORE_SENHA_PADRAO);
        return out.toByteArray();
    }

    /**
     * Conecta ao servidor com um TrustManager que aceita qualquer certificado, captura a cadeia
     * apresentada e monta um JKS em memória com ela.
     *
     * <p><b>Atenção:</b> não valida a autenticidade da cadeia durante a captura — use apenas para
     * obter certificados de servidores confiáveis em ambiente controlado.
     */
    public static byte[] capturarTruststoreDoServidor(String host, int port) throws Exception {
        AtomicReference<X509Certificate[]> cadeiaRef = new AtomicReference<>();

        TrustManager capturador = new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }

            public void checkClientTrusted(X509Certificate[] c, String a) {
            }

            public void checkServerTrusted(X509Certificate[] cadeia, String authType) {
                cadeiaRef.set(cadeia);
            }
        };

        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, new TrustManager[]{capturador}, null);
        SSLSocketFactory factory = ctx.getSocketFactory();

        try (SSLSocket socket = (SSLSocket) factory.createSocket(host, port)) {
            socket.setSoTimeout(15_000);
            socket.startHandshake();
        }

        X509Certificate[] cadeia = cadeiaRef.get();
        if (cadeia == null || cadeia.length == 0) {
            throw new IllegalStateException("Nenhum certificado capturado do servidor " + host + ":" + port);
        }

        KeyStore ts = KeyStore.getInstance("JKS");
        ts.load(null, null);
        for (int i = 0; i < cadeia.length; i++) {
            ts.setCertificateEntry("servidor_" + i, cadeia[i]);
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ts.store(out, TRUSTSTORE_SENHA_PADRAO);
        return out.toByteArray();
    }

    private static byte[] httpGet(String urlStr, int timeoutMs) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setConnectTimeout(timeoutMs);
        conn.setReadTimeout(timeoutMs);
        conn.setRequestMethod("GET");
        try (InputStream in = conn.getInputStream();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            return out.toByteArray();
        } finally {
            conn.disconnect();
        }
    }
}
