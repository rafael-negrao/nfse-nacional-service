package br.com.adelfo.nfse.nacional.service;

import br.com.adelfo.nfse.nacional.service.dto.response.ConsultaEventosResponse;
import br.com.adelfo.nfse.nacional.service.dto.response.DocumentoDistribuido;
import br.com.adelfo.nfse.nacional.service.dto.response.SituacaoNfse;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Situação da NFS-e derivada dos eventos do ADN. A nota em si fica com cStat 100 depois de
 * cancelada; só os eventos dizem o contrário.
 */
class SituacaoNfseTest {

    private static final String CHAVE = "35503082206169966000133000000000000226100127014831";

    private static DocumentoDistribuido nfse() {
        return new DocumentoDistribuido(2, CHAVE, "NFSE", null, "<NFSe/>", null);
    }

    private static DocumentoDistribuido evento(long nsu, String tipo) {
        return new DocumentoDistribuido(nsu, CHAVE, "EVENTO", tipo, "<evento/>", null);
    }

    private static ConsultaEventosResponse lote(DocumentoDistribuido... eventos) {
        List<DocumentoDistribuido> docs = new ArrayList<>(List.of(nfse()));
        docs.addAll(List.of(eventos));
        return new ConsultaEventosResponse(CHAVE, docs);
    }

    @Test
    void notaSemEventos_eValida() {
        assertEquals(SituacaoNfse.VALIDA, lote().situacao());
        assertFalse(lote().cancelada());
    }

    @Test
    void cancelamentoDoEmitente_comoORegistradoEm27_10_2026() {
        // NSU 2 = NFS-e, NSU 3 = evento de cancelamento: o que o ADN devolveu para esta chave.
        ConsultaEventosResponse r = lote(evento(3, "CANCELAMENTO"));

        assertEquals(SituacaoNfse.CANCELADA, r.situacao());
        assertTrue(r.cancelada());
        assertEquals(3, r.eventoDeCancelamento().orElseThrow().nsu());
    }

    @Test
    void cancelamentosDoMunicipio_tambemContam() {
        // O ANEXO_II lista quatro formas de cancelamento; três são do município.
        assertEquals(SituacaoNfse.CANCELADA, lote(evento(3, "CANCELAMENTO_POR_OFICIO")).situacao());
        assertEquals(SituacaoNfse.CANCELADA,
                lote(evento(3, "SOLICITACAO_CANCELAMENTO_ANALISE_FISCAL"),
                        evento(4, "CANCELAMENTO_DEFERIDO_ANALISE_FISCAL")).situacao());
    }

    @Test
    void substituicao_eCancelamento() {
        ConsultaEventosResponse r = lote(evento(3, "CANCELAMENTO_POR_SUBSTITUICAO"));

        assertEquals(SituacaoNfse.SUBSTITUIDA, r.situacao());
        assertTrue(r.cancelada());
        assertTrue(r.eventoDeCancelamento().isPresent());
    }

    @Test
    void solicitacaoDeAnalise_deixaANotaEmSuspensoAteADecisao() {
        assertEquals(SituacaoNfse.CANCELAMENTO_EM_ANALISE,
                lote(evento(3, "SOLICITACAO_CANCELAMENTO_ANALISE_FISCAL")).situacao());
        assertFalse(lote(evento(3, "SOLICITACAO_CANCELAMENTO_ANALISE_FISCAL")).cancelada());

        assertEquals(SituacaoNfse.VALIDA,
                lote(evento(3, "SOLICITACAO_CANCELAMENTO_ANALISE_FISCAL"),
                        evento(4, "CANCELAMENTO_INDEFERIDO_ANALISE_FISCAL")).situacao());
    }

    @Test
    void novaSolicitacaoDepoisDeUmIndeferimento_voltaAoSuspenso() {
        assertEquals(SituacaoNfse.CANCELAMENTO_EM_ANALISE,
                lote(evento(3, "SOLICITACAO_CANCELAMENTO_ANALISE_FISCAL"),
                        evento(4, "CANCELAMENTO_INDEFERIDO_ANALISE_FISCAL"),
                        evento(5, "SOLICITACAO_CANCELAMENTO_ANALISE_FISCAL")).situacao());
    }

    @Test
    void manifestacoesEBloqueio_naoAlteramAValidade() {
        assertEquals(SituacaoNfse.VALIDA,
                lote(evento(3, "CONFIRMACAO_TOMADOR"), evento(4, "BLOQUEIO_POR_OFICIO")).situacao());
    }

    @Test
    void chaveSemDocumentos_naoFoiEncontrada() {
        assertEquals(SituacaoNfse.NAO_ENCONTRADA, new ConsultaEventosResponse(CHAVE, List.of()).situacao());
    }
}
