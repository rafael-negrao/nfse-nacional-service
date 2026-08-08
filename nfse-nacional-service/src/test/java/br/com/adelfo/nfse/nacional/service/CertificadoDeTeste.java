package br.com.adelfo.nfse.nacional.service;

import br.com.adelfo.nfse.nacional.client.CertificadoDigital;
import br.com.adelfo.nfse.nacional.client.TipoAmbiente;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Localiza o certificado A1 e monta o {@link CertificadoDigital} usado pelos testes de integração.
 *
 * <p>Ordem de resolução do certificado:
 * <ol>
 *   <li>{@code -Dnfse.cert.p12=/caminho/cert.pfx} e {@code -Dnfse.cert.senha=…};</li>
 *   <li>na ausência das properties, o primeiro {@code .pfx}/{@code .p12} sob {@code certificado/}
 *       na raiz do repositório, com a senha lida do {@code senha.txt} irmão.</li>
 * </ol>
 *
 * <p>O diretório {@code certificado/} é ignorado pelo git; nenhuma senha é gravada em código nem
 * impressa no log. Sem certificado, os testes são <b>pulados</b> via {@code assumeTrue} em vez de
 * falharem — o build normal não depende de credencial.
 *
 * <p>Ambiente: {@code -Dnfse.ambiente=producao} para produção; qualquer outro valor (ou a ausência)
 * significa produção restrita. O padrão é deliberadamente o ambiente inócuo.
 */
final class CertificadoDeTeste {

    private CertificadoDeTeste() {
    }

    /** Diretório varrido quando as system properties não são informadas. */
    private static final Path DIRETORIO_CERTIFICADOS =
            Paths.get("..", "certificado").toAbsolutePath().normalize();

    static TipoAmbiente ambiente() {
        return "producao".equalsIgnoreCase(System.getProperty("nfse.ambiente", "producao-restrita"))
                ? TipoAmbiente.PRODUCAO
                : TipoAmbiente.PRODUCAO_RESTRITA;
    }

    /**
     * Certificado pronto para uso, ou {@code assumeTrue} falho — o que pula a classe de teste
     * inteira quando não há credencial utilizável.
     *
     * <p>Quando o certificado não vem por system property, cada candidato encontrado é aberto de
     * fato com a senha do {@code senha.txt} irmão antes de ser aceito: num diretório com vários
     * certificados, escolher pelo nome acerta por acaso, e o erro que sobra
     * ({@code keystore password was incorrect}) não diz qual arquivo falhou.
     */
    static CertificadoDigital carregar(TipoAmbiente ambiente) throws Exception {
        List<Path> candidatos = localizarP12();
        assumeTrue(!candidatos.isEmpty(),
                "Teste ignorado: nenhum certificado encontrado. Informe -Dnfse.cert.p12 e "
                        + "-Dnfse.cert.senha, ou coloque um .pfx em " + DIRETORIO_CERTIFICADOS);

        List<String> recusados = new ArrayList<>();
        for (Path caminho : candidatos) {
            Optional<char[]> senha = localizarSenha(caminho);
            if (senha.isEmpty()) {
                recusados.add(caminho.getFileName() + ": sem senha (nem -Dnfse.cert.senha nem senha.txt)");
                continue;
            }
            byte[] bytes = Files.readAllBytes(caminho);
            if (!abre(bytes, senha.get())) {
                recusados.add(caminho.getFileName() + ": a senha disponível não abre o arquivo");
                continue;
            }

            System.out.println("[setup] Certificado : " + caminho.getFileName());
            System.out.println("[setup] Ambiente    : " + ambiente);
            if (!recusados.isEmpty()) {
                System.out.println("[setup] Descartados : " + recusados);
            }
            return montar(bytes, senha.get(), ambiente);
        }

        assumeTrue(false, "Teste ignorado: nenhum certificado utilizável. " + recusados);
        throw new IllegalStateException("inalcançável");
    }

    /** Verdadeiro quando o PKCS#12 abre com a senha — sem propagar a senha para a mensagem. */
    private static boolean abre(byte[] p12, char[] senha) {
        try {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(new java.io.ByteArrayInputStream(p12), senha);
            return ks.aliases().hasMoreElements();
        } catch (Exception e) {
            return false;
        }
    }

    /** CNPJ ou CPF do titular do certificado; {@code -Dnfse.cnpj.emitente} sobrescreve. */
    static String inscricaoFederalDoCertificado(CertificadoDigital certificado) throws Exception {
        String informado = System.getProperty("nfse.cnpj.emitente");
        return informado != null && !informado.isBlank() ? informado : certificado.inscricaoFederal();
    }

    private static CertificadoDigital montar(byte[] p12, char[] senha, TipoAmbiente ambiente)
            throws Exception {
        try {
            System.out.println("[setup] Baixando truststore ICP-Brasil do repositório ITI...");
            return CertificadoDigital.comTruststoreIcpBrasil(p12, senha);
        } catch (Exception e) {
            // Os dois hosts do Sistema Nacional têm cadeias distintas; capturar só a da Sefin faz
            // qualquer chamada ao ADN falhar no handshake, com cara de indisponibilidade de rede.
            String sefin = URI.create(ambiente.getUrlSefinNacional()).getHost();
            String adn = URI.create(ambiente.getUrlAdn()).getHost();
            System.out.println("[setup] ITI inacessível. Capturando as cadeias de " + sefin
                    + " e " + adn + "...");
            return CertificadoDigital.comTruststoreCapturaDosServidores(p12, senha, sefin, adn);
        }
    }

    /** Todos os certificados candidatos, em ordem estável. */
    private static List<Path> localizarP12() throws Exception {
        String informado = System.getProperty("nfse.cert.p12");
        if (informado != null && !informado.isBlank()) {
            Path caminho = Paths.get(informado);
            return Files.exists(caminho) ? List.of(caminho) : List.of();
        }
        if (!Files.isDirectory(DIRETORIO_CERTIFICADOS)) {
            return List.of();
        }
        try (Stream<Path> arquivos = Files.walk(DIRETORIO_CERTIFICADOS)) {
            return arquivos
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String nome = p.getFileName().toString().toLowerCase();
                        return nome.endsWith(".pfx") || nome.endsWith(".p12");
                    })
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }
    }

    private static Optional<char[]> localizarSenha(Path p12) throws Exception {
        String informada = System.getProperty("nfse.cert.senha");
        if (informada != null && !informada.isBlank()) {
            return Optional.of(informada.toCharArray());
        }
        Path senhaTxt = p12.getParent().resolve("senha.txt");
        if (Files.exists(senhaTxt)) {
            return Optional.of(Files.readString(senhaTxt).trim().toCharArray());
        }
        return Optional.empty();
    }
}
