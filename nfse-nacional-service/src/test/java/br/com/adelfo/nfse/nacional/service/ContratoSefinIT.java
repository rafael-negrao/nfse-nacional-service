package br.com.adelfo.nfse.nacional.service;

import br.com.adelfo.nfse.nacional.client.CertificadoDigital;
import br.com.adelfo.nfse.nacional.client.GZipBase64;
import br.com.adelfo.nfse.nacional.client.NfseHttpClient;
import br.com.adelfo.nfse.nacional.client.NfseHttpException;
import br.com.adelfo.nfse.nacional.client.TipoAmbiente;
import br.com.adelfo.nfse.nacional.service.signing.XmlSigningService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Ferramenta de diagnóstico do contrato JSON, não um teste de regressão.
 *
 * <p>Os nomes dos campos JSON usados por {@code NfseServiceImpl} ({@code dpsXmlGZipB64} e
 * companhia) vieram da documentação pública e de implementações de terceiros — a Swagger oficial
 * responde 403 sem certificado. Este teste faz o {@code POST /nfse} <b>sem passar pela fachada</b>
 * e despeja requisição e resposta cruas, para que os nomes sejam confirmados ou corrigidos contra
 * o que a Sefin de fato responde.
 *
 * <p>Um nome de campo errado tende a aparecer aqui como erro de validação do corpo (400), distinto
 * das rejeições de regra de negócio da DPS. Corrigido o contrato, as constantes {@code CAMPO_*} de
 * {@code NfseServiceImpl} são o único ponto a ajustar.
 *
 * <p>Sempre passa: o objetivo é a saída no console, não a asserção.
 *
 * <pre>
 * mvn test -pl nfse-nacional-service -DfailIfNoTests=false -Dtest=ContratoSefinIT \
 *     -Dnfse.cert.p12=/caminho/cert.pfx -Dnfse.cert.senha=senha -Dnfse.municipio=3550308
 * </pre>
 */
class ContratoSefinIT {

    /** Candidatos ao nome do campo que carrega a DPS no corpo do POST /nfse. */
    private static final String[] CANDIDATOS_CAMPO_DPS = {
            "dpsXmlGZipB64",
            "DpsXmlGZipB64",
            "dpsXmlGzipB64",
    };

    private static TipoAmbiente ambiente;
    private static NfseHttpClient httpClient;
    private static XmlSigningService signer;
    private static String cnpjPrestador;

    @BeforeAll
    static void setup() throws Exception {
        ambiente = CertificadoDeTeste.ambiente();
        CertificadoDigital certificado = CertificadoDeTeste.carregar(ambiente);
        cnpjPrestador = CertificadoDeTeste.inscricaoFederalDoCertificado(certificado);
        httpClient = new NfseHttpClient(certificado, Duration.ofSeconds(15), Duration.ofSeconds(60));
        signer = new XmlSigningService(certificado);
    }

    @Test
    void despejaRequisicaoERespostaDoPostNfse() throws Exception {
        DpsBuilder builder = DpsDeTeste.simples(ambiente, cnpjPrestador, DpsDeTeste.proximoNumeroDps());
        String xmlDps = builder.build();
        String xmlAssinado = new String(
                signer.assinar(xmlDps.getBytes(StandardCharsets.UTF_8), "#" + builder.id()),
                StandardCharsets.UTF_8);
        String gzipB64 = GZipBase64.comprimir(xmlAssinado);

        System.out.println("=== XML da DPS assinado ===");
        System.out.println(xmlAssinado);

        for (String campo : CANDIDATOS_CAMPO_DPS) {
            String corpo = "{\"" + campo + "\":\"" + gzipB64 + "\"}";
            System.out.println();
            System.out.println("=== POST /nfse com campo \"" + campo + "\" ===");
            try {
                String resposta = httpClient.post(ambiente.getUrlSefinNacional() + "/nfse", corpo);
                System.out.println("SUCESSO — corpo da resposta:");
                System.out.println(resposta);
                System.out.println(">>> Campo correto confirmado: " + campo);
                return;
            } catch (NfseHttpException e) {
                System.out.println("HTTP " + e.getStatus());
                System.out.println(e.getCorpo());
            } catch (Exception e) {
                System.out.println("Falha de transporte: " + e);
            }
        }

        System.out.println();
        System.out.println(">>> Nenhum dos candidatos foi aceito. Leia os corpos acima: se a Sefin "
                + "reclamou de regra de negócio (município, cadastro do CNPJ), o nome do campo está "
                + "certo e o bloqueio é de habilitação. Se reclamou do formato do corpo, o nome está errado.");
    }
}
