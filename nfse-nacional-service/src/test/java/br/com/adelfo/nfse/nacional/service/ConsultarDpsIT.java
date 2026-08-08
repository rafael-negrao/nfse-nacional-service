package br.com.adelfo.nfse.nacional.service;

import br.com.adelfo.nfse.nacional.client.CertificadoDigital;
import br.com.adelfo.nfse.nacional.client.NfseHttpClient;
import br.com.adelfo.nfse.nacional.client.TipoAmbiente;
import br.com.adelfo.nfse.nacional.service.dto.request.ConsultaDpsRequest;
import br.com.adelfo.nfse.nacional.service.dto.response.ConsultaDpsResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Menor teste de integração útil: confirma que o mTLS fecha com a Sefin Nacional e que a rota
 * {@code /dps/{id}} responde.
 *
 * <p>É o primeiro a rodar quando algo está errado — {@code HEAD /dps/{id}} é liberada a qualquer
 * certificado válido, então uma falha aqui é de conectividade, cadeia de certificação ou
 * certificado, nunca de regra de negócio.
 *
 * <pre>
 * mvn test -pl nfse-nacional-service -DfailIfNoTests=false -Dtest=ConsultarDpsIT \
 *     -Dnfse.cert.p12=/caminho/cert.pfx -Dnfse.cert.senha=senha
 * </pre>
 */
class ConsultarDpsIT {

    private static TipoAmbiente ambiente;
    private static NfseService service;
    private static String cnpjPrestador;

    @BeforeAll
    static void setup() throws Exception {
        ambiente = CertificadoDeTeste.ambiente();
        CertificadoDigital certificado = CertificadoDeTeste.carregar(ambiente);
        cnpjPrestador = CertificadoDeTeste.inscricaoFederalDoCertificado(certificado);

        NfseHttpClient httpClient = new NfseHttpClient(
                certificado, Duration.ofSeconds(15), Duration.ofSeconds(30));
        service = new NfseServiceImpl(certificado, httpClient);

        System.out.println("[setup] Prestador   : " + cnpjPrestador);
        System.out.println("[setup] Sefin       : " + ambiente.getUrlSefinNacional());
    }

    @Test
    void consultarDpsInexistente_respondeQueNaoFoiGerada() {
        // Id sintético, no formato TSIdDPS, de uma DPS que nunca foi enviada.
        String idDps = DpsBuilder.novo()
                .ambiente(ambiente)
                .municipioEmissor(DpsDeTeste.municipio())
                .identificacao("99999", "999999999999999", java.time.LocalDate.now())
                .prestadorCnpj(cnpjPrestador)
                .id();

        System.out.println("=== consultarPorIdDps (DPS inexistente) ===");
        System.out.println("idDps  : " + idDps);

        ConsultaDpsResponse resposta = service.consultarPorIdDps(
                new ConsultaDpsRequest(ambiente, idDps));

        System.out.println("gerada : " + resposta.gerada());
        System.out.println("chave  : " + resposta.chaveAcesso());

        assertNotNull(resposta, "a consulta deve responder em vez de estourar no transporte");
    }
}
