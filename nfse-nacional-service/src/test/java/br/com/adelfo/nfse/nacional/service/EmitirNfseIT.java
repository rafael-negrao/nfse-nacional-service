package br.com.adelfo.nfse.nacional.service;

import br.com.adelfo.nfse.nacional.client.CertificadoDigital;
import br.com.adelfo.nfse.nacional.client.NfseHttpClient;
import br.com.adelfo.nfse.nacional.client.TipoAmbiente;
import br.com.adelfo.nfse.nacional.service.dto.request.EmissaoRequest;
import br.com.adelfo.nfse.nacional.service.dto.response.EmissaoResponse;
import br.com.adelfo.nfse.nacional.service.exception.NfseException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Emite uma NFS-e em produção restrita a partir de uma DPS montada pelo {@link DpsBuilder}.
 *
 * <p>Exige, além do certificado, que o município emissor seja conveniado e que o CNPJ do
 * certificado esteja autorizado nele — ver {@link DpsDeTeste}. Uma rejeição de cadastro é o
 * resultado esperado enquanto essa habilitação não existir; o teste imprime o corpo bruto da
 * resposta para que o motivo fique visível.
 *
 * <pre>
 * mvn test -pl nfse-nacional-service -DfailIfNoTests=false -Dtest=EmitirNfseIT \
 *     -Dnfse.cert.p12=/caminho/cert.pfx -Dnfse.cert.senha=senha -Dnfse.municipio=3550308
 * </pre>
 */
class EmitirNfseIT {

    private static TipoAmbiente ambiente;
    private static NfseService service;
    private static String cnpjPrestador;

    @BeforeAll
    static void setup() throws Exception {
        ambiente = CertificadoDeTeste.ambiente();
        CertificadoDigital certificado = CertificadoDeTeste.carregar(ambiente);
        cnpjPrestador = CertificadoDeTeste.inscricaoFederalDoCertificado(certificado);

        NfseHttpClient httpClient = new NfseHttpClient(
                certificado, Duration.ofSeconds(15), Duration.ofSeconds(60));
        service = new NfseServiceImpl(certificado, httpClient);

        System.out.println("[setup] Prestador   : " + cnpjPrestador);
        System.out.println("[setup] Município   : " + DpsDeTeste.municipio());
    }

    @Test
    void emitirNfse_retornaChaveDeAcessoEXmlDaNota() throws Exception {
        String numeroDps = DpsDeTeste.proximoNumeroDps();
        DpsBuilder builder = DpsDeTeste.simples(ambiente, cnpjPrestador, numeroDps);
        String xmlDps = builder.build();

        System.out.println("=== DPS enviada ===");
        System.out.println("Id  : " + builder.id());
        System.out.println(xmlDps);

        EmissaoResponse resposta;
        try {
            resposta = service.emitir(new EmissaoRequest(ambiente, xmlDps));
        } catch (NfseException e) {
            System.out.println("=== Rejeição da Sefin ===");
            System.out.println("código  : " + e.getCodigo());
            System.out.println("mensagem: " + e.getMensagem());
            System.out.println("corpo   : " + e.getCorpoResposta());
            throw e;
        }

        System.out.println("=== NFS-e gerada ===");
        System.out.println("chaveAcesso: " + resposta.chaveAcesso());
        System.out.println(resposta.xmlNfse());

        assertNotNull(resposta.chaveAcesso(), "a emissão síncrona deve devolver a chave de acesso");
        assertEquals(50, resposta.chaveAcesso().length(), "a chave de acesso tem 50 dígitos");
        assertNotNull(resposta.xmlNfse(), "a emissão síncrona deve devolver o XML da NFS-e");
    }

    @Test
    void emitirDpsDuplicada_eRejeitada() throws Exception {
        // Reenviar a mesma série/número é a rejeição mais fácil de provocar sem depender de
        // habilitação municipal, e confirma que a rejeição chega como NfseException e não como
        // falha de transporte.
        String numeroDps = DpsDeTeste.proximoNumeroDps();
        String xmlDps = DpsDeTeste.simples(ambiente, cnpjPrestador, numeroDps).build();

        try {
            service.emitir(new EmissaoRequest(ambiente, xmlDps));
        } catch (NfseException primeiraTentativa) {
            System.out.println("Primeira emissão já rejeitada (" + primeiraTentativa.getCodigo()
                    + "); o teste de duplicidade não se aplica: " + primeiraTentativa.getMensagem());
            return;
        }

        try {
            service.emitir(new EmissaoRequest(ambiente, xmlDps));
            fail("a segunda emissão da mesma DPS deveria ser rejeitada");
        } catch (NfseException e) {
            System.out.println("=== Rejeição esperada por duplicidade ===");
            System.out.println("código  : " + e.getCodigo());
            System.out.println("mensagem: " + e.getMensagem());
        }
    }
}
