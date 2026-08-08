package br.com.adelfo.nfse.nacional.service;

import br.com.adelfo.nfse.nacional.client.CertificadoDigital;
import br.com.adelfo.nfse.nacional.client.NfseHttpClient;
import br.com.adelfo.nfse.nacional.client.NfseHttpException;
import br.com.adelfo.nfse.nacional.client.TipoAmbiente;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Sonda a API do DANFSE (representação em PDF da NFS-e), que fica no ADN sob {@code /danfse} — a
 * rota {@code GET /DANFSe} da Sefin responde 501 e a documentação diz que o serviço foi movido.
 *
 * <p>O ADN aplica <b>rate limit</b> nesse caminho: uma sequência de chamadas seguidas passa a
 * receber 429 e 503, e aí qualquer conclusão sobre a rota vira ruído. Por isso as tentativas aqui
 * são poucas e espaçadas.
 *
 * <pre>
 * mvn test -pl nfse-nacional-service -DfailIfNoTests=false \
 *     -Dsurefire.failIfNoSpecifiedTests=false -Dtest=DanfseIT \
 *     -Dnfse.ambiente=producao -Dnfse.chave=&lt;50 dígitos&gt;
 * </pre>
 */
class DanfseIT {

    private static final Duration PAUSA_ENTRE_TENTATIVAS = Duration.ofSeconds(20);

    private static TipoAmbiente ambiente;
    private static NfseHttpClient httpClient;
    private static String chave;

    @BeforeAll
    static void setup() throws Exception {
        chave = System.getProperty("nfse.chave");
        assumeTrue(chave != null && !chave.isBlank(),
                "Teste ignorado: informe -Dnfse.chave=<chave de 50 dígitos>");

        ambiente = CertificadoDeTeste.ambiente();
        CertificadoDigital certificado = CertificadoDeTeste.carregar(ambiente);
        httpClient = new NfseHttpClient(certificado, Duration.ofSeconds(15), Duration.ofSeconds(60));
    }

    @Test
    void sondarRotasDoDanfse() throws Exception {
        String adn = ambiente.getUrlAdn();

        String[] candidatas = {
                adn + "/danfse/" + chave,
                adn + "/danfse/danfse/" + chave,
                adn + "/danfse/swagger/docs/v1",
        };

        for (int i = 0; i < candidatas.length; i++) {
            if (i > 0) {
                Thread.sleep(PAUSA_ENTRE_TENTATIVAS.toMillis());
            }
            System.out.println();
            System.out.println("GET " + candidatas[i]);
            try {
                String corpo = httpClient.get(candidatas[i]);
                System.out.println("  HTTP 200 — " + corpo.length() + " bytes");
                System.out.println("  início: " + corpo.substring(0, Math.min(120, corpo.length())));
            } catch (NfseHttpException e) {
                System.out.println("  HTTP " + e.getStatus() + " — " + natureza(e.getCorpo()));
            } catch (Exception e) {
                System.out.println("  falha de transporte: " + e);
            }
        }
    }

    private static String natureza(String corpo) {
        if (corpo == null || corpo.isBlank()) {
            return "(corpo vazio)";
        }
        String limpo = corpo.strip();
        if (limpo.startsWith("<!DOCTYPE") || limpo.startsWith("<html")) {
            return "página HTML (rate limit ou indisponibilidade do gateway)";
        }
        return limpo.substring(0, Math.min(300, limpo.length()));
    }
}
