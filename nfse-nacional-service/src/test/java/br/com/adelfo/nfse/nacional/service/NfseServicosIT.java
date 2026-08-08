package br.com.adelfo.nfse.nacional.service;

import br.com.adelfo.nfse.nacional.client.CertificadoDigital;
import br.com.adelfo.nfse.nacional.client.NfseHttpClient;
import br.com.adelfo.nfse.nacional.client.TipoAmbiente;
import br.com.adelfo.nfse.nacional.service.dto.request.CancelamentoRequest;
import br.com.adelfo.nfse.nacional.service.dto.request.ConsultaDpsRequest;
import br.com.adelfo.nfse.nacional.service.dto.request.ConsultaNfseRequest;
import br.com.adelfo.nfse.nacional.service.dto.request.EmissaoRequest;
import br.com.adelfo.nfse.nacional.service.dto.response.CancelamentoResponse;
import br.com.adelfo.nfse.nacional.service.dto.response.ConsultaDpsResponse;
import br.com.adelfo.nfse.nacional.service.dto.response.ConsultaNfseResponse;
import br.com.adelfo.nfse.nacional.service.dto.response.EmissaoResponse;
import br.com.adelfo.nfse.nacional.service.exception.NfseException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Ciclo completo contra a Sefin Nacional: emitir → consultar por chave → consultar por DPS →
 * cancelar.
 *
 * <p>O {@code @BeforeAll} emite a NFS-e que dá objeto aos demais passos. Se a emissão for rejeitada
 * — cenário provável enquanto o CNPJ não estiver habilitado num município conveniado —, os passos
 * seguintes são <b>pulados</b> com a mensagem da rejeição, em vez de falharem em cascata.
 *
 * <pre>
 * mvn test -pl nfse-nacional-service -DfailIfNoTests=false -Dtest=NfseServicosIT \
 *     -Dnfse.cert.p12=/caminho/cert.pfx -Dnfse.cert.senha=senha -Dnfse.municipio=3550308
 * </pre>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NfseServicosIT {

    private static TipoAmbiente ambiente;
    private static NfseService service;
    private static String cnpjPrestador;

    /** Chave da NFS-e emitida no setup; nula quando a emissão foi rejeitada. */
    private static String chaveAcesso;

    /** Id da DPS que originou a NFS-e — insumo da consulta por DPS. */
    private static String idDps;

    /** Motivo da rejeição no setup, usado nas mensagens de skip dos passos seguintes. */
    private static String motivoIndisponibilidade;

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

        emitirNfseDeApoio();
    }

    private static void emitirNfseDeApoio() throws Exception {
        DpsBuilder builder = DpsDeTeste.simples(ambiente, cnpjPrestador, DpsDeTeste.proximoNumeroDps());
        idDps = builder.id();

        try {
            EmissaoResponse resposta = service.emitir(new EmissaoRequest(ambiente, builder.build()));
            chaveAcesso = resposta.chaveAcesso();
            System.out.println("[setup] NFS-e emitida: " + chaveAcesso);
        } catch (NfseException e) {
            motivoIndisponibilidade = "emissão rejeitada [" + e.getCodigo() + "]: " + e.getMensagem();
            System.out.println("[setup] " + motivoIndisponibilidade);
            System.out.println("[setup] corpo: " + e.getCorpoResposta());
        }
    }

    private static void exigeNfseEmitida() {
        assumeTrue(chaveAcesso != null,
                "Passo ignorado: não há NFS-e emitida — " + motivoIndisponibilidade);
    }

    @Test
    @Order(1)
    void emitir_produziuChaveDeAcessoDe50Digitos() {
        exigeNfseEmitida();

        System.out.println("=== 1. emitir ===");
        System.out.println("chaveAcesso: " + chaveAcesso);

        assertEquals(50, chaveAcesso.length());
        assertTrue(chaveAcesso.matches("[0-9]{50}"), "chave fora do formato: " + chaveAcesso);
    }

    @Test
    @Order(2)
    void consultarPorChave_devolveOXmlDaNota() {
        exigeNfseEmitida();

        ConsultaNfseResponse resposta = service.consultarPorChave(
                new ConsultaNfseRequest(ambiente, chaveAcesso));

        System.out.println("=== 2. consultarPorChave ===");
        System.out.println(resposta.xmlNfse());

        assertNotNull(resposta.xmlNfse(), "a consulta por chave deve devolver o XML da NFS-e");
        assertTrue(resposta.xmlNfse().contains("NFSe"), "o XML devolvido deve ser uma NFS-e");
    }

    @Test
    @Order(3)
    void consultarPorIdDps_devolveAChaveDaNotaGerada() {
        exigeNfseEmitida();

        ConsultaDpsResponse resposta = service.consultarPorIdDps(
                new ConsultaDpsRequest(ambiente, idDps));

        System.out.println("=== 3. consultarPorIdDps ===");
        System.out.println("idDps  : " + idDps);
        System.out.println("gerada : " + resposta.gerada());
        System.out.println("chave  : " + resposta.chaveAcesso());

        assertTrue(resposta.gerada(), "a DPS emitida no setup deve constar como gerada");
        // O certificado da conexão é o do prestador, então o sigilo fiscal não se aplica e a
        // chave deve vir preenchida.
        assertEquals(chaveAcesso, resposta.chaveAcesso());
    }

    @Test
    @Order(4)
    void cancelar_registraOEventoE101101() {
        exigeNfseEmitida();

        // Sem autor: a fachada o tira do certificado, como a regra E0812 exige.
        CancelamentoResponse resposta = service.cancelar(CancelamentoRequest.de(
                ambiente, chaveAcesso, "1", "Cancelamento de teste de integracao"));

        System.out.println("=== 4. cancelar ===");
        System.out.println(resposta.xmlEvento());

        assertEquals(chaveAcesso, resposta.chaveAcesso());
        assertNotNull(resposta.xmlEvento(), "o cancelamento síncrono deve devolver o XML do evento");
    }
}
