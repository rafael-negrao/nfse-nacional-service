package br.com.adelfo.nfse.nacional.service;

import br.com.adelfo.nfse.nacional.client.CertificadoDigital;
import br.com.adelfo.nfse.nacional.client.NfseHttpClient;
import br.com.adelfo.nfse.nacional.client.TipoAmbiente;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

/**
 * Baixa as especificações OpenAPI do Sistema Nacional NFS-e para {@code doc/openapi/}.
 *
 * <p>A Swagger responde 403 a um navegador comum, o que levou este projeto a inferir contratos da
 * documentação em prosa — e a errar (ver o histórico das rotas de parâmetros municipais no
 * CLAUDE.md). Com o certificado A1 na conexão, porém, ela responde 200: é a mesma exigência de
 * mTLS das demais rotas.
 *
 * <p>Rode-o quando a Receita publicar uma versão nova; os arquivos gerados passam a ser a fonte de
 * verdade dos contratos, acima do PDF.
 *
 * <pre>
 * mvn test -pl nfse-nacional-service -DfailIfNoTests=false \
 *     -Dsurefire.failIfNoSpecifiedTests=false -Dtest=BaixarSwaggerIT
 * </pre>
 */
class BaixarSwaggerIT {

    private static final Path DESTINO = Paths.get("..", "doc", "openapi");

    private static TipoAmbiente ambiente;
    private static NfseHttpClient httpClient;

    @BeforeAll
    static void setup() throws Exception {
        ambiente = CertificadoDeTeste.ambiente();
        CertificadoDigital certificado = CertificadoDeTeste.carregar(ambiente);
        httpClient = new NfseHttpClient(certificado, Duration.ofSeconds(15), Duration.ofSeconds(60));
    }

    @Test
    void baixarEspecificacoes() throws Exception {
        Files.createDirectories(DESTINO);
        String adn = ambiente.getUrlAdn();
        String sefin = ambiente.getUrlSefinNacional();

        for (String candidato : new String[]{
                "/swagger/v1/swagger.json", "/docs/v1/swagger.json", "/swagger/docs/v1",
                "/docs/swagger.json", "/swagger.json", "/openapi.json", "/docs/index",
        }) {
            if (baixar("sefin-nacional", sefin + candidato)) {
                break;
            }
        }
        baixar("adn-parametrizacao", adn + "/parametrizacao/swagger/v1/swagger.json");
        baixar("adn", adn + "/swagger/v1/swagger.json");
        baixar("adn-cnc", adn + "/cnc/swagger/v1/swagger.json");
        baixar("adn-danfse", adn + "/danfse/swagger/v1/swagger.json");
        baixar("adn-contribuintes", adn + "/contribuintes/swagger/v1/swagger.json");
    }

    private static boolean baixar(String nome, String url) {
        try {
            String corpo = httpClient.get(url);
            if (!corpo.stripLeading().startsWith("{")) {
                System.out.println("PULA  " + nome + " <- " + url + " : resposta não é JSON");
                return false;
            }
            Files.writeString(DESTINO.resolve(nome + ".json"), corpo);
            System.out.println("OK    " + nome + " (" + corpo.length() + " bytes) <- " + url);
            return true;
        } catch (Exception e) {
            String msg = e.getMessage();
            System.out.println("FALHA " + nome + " <- " + url + " : "
                    + (msg == null ? e.toString() : msg.substring(0, Math.min(90, msg.length()))));
            return false;
        }
    }
}
