package br.com.adelfo.nfse.nacional.service;

import br.com.adelfo.nfse.nacional.client.TipoAmbiente;
import br.com.adelfo.nfse.nacional.service.dto.request.ManifestacaoRequest;
import br.com.adelfo.nfse.nacional.service.dto.request.TipoManifestacao;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Valida as manifestações contra o XSD oficial {@code pedRegEvento_v1.01.xsd}.
 */
class ManifestacaoTest {

    private static final String CHAVE = "1".repeat(49) + "9";
    private static final String CNPJ = "12345678000195";

    private static void validar(String xml) throws Exception {
        assertEquals(List.of(), ValidacaoXsd.erros("pedRegEvento_v1.01.xsd", xml),
                "XML deve ser válido no XSD oficial");
    }

    private static ManifestacaoRequest requisicao(TipoManifestacao tipo) {
        return tipo.ehRejeicao()
                ? ManifestacaoRequest.rejeicao(TipoAmbiente.PRODUCAO_RESTRITA, CHAVE, tipo,
                        "3", "Nao ocorrencia do fato gerador do servico")
                : ManifestacaoRequest.confirmacao(TipoAmbiente.PRODUCAO_RESTRITA, CHAVE, tipo);
    }

    @ParameterizedTest
    @EnumSource(TipoManifestacao.class)
    void todaManifestacao_geraXmlValidoNoSchema(TipoManifestacao tipo) throws Exception {
        String xml = EventoBuilder.manifestacao(requisicao(tipo), CNPJ);

        validar(xml);
        // xDesc é enumeração de valor único por tipo — daí vir do enum, e não do chamador.
        assertTrue(xml.contains("<xDesc>" + tipo.getDescricao() + "</xDesc>"),
                "xDesc fora da enumeração para " + tipo);
        assertTrue(xml.contains("<e" + tipo.getCodigo() + ">"),
                "cada manifestação é um elemento próprio no leiaute: e" + tipo.getCodigo());
    }

    @ParameterizedTest
    @EnumSource(TipoManifestacao.class)
    void idDoPedido_usaOCodigoDaManifestacao(TipoManifestacao tipo) {
        String xml = EventoBuilder.manifestacao(requisicao(tipo), CNPJ);

        String id = xml.replaceAll("(?s).*<infPedReg Id=\"([^\"]+)\".*", "$1");
        assertEquals("PRE" + CHAVE + tipo.getCodigo(), id);
        assertTrue(id.matches("PRE[0-9]{56}"), "Id fora do pattern TSIdPedRegEvt: " + id);
    }

    @Test
    void confirmacao_naoCarregaMotivo() {
        String xml = EventoBuilder.manifestacao(
                ManifestacaoRequest.confirmacao(
                        TipoAmbiente.PRODUCAO, CHAVE, TipoManifestacao.CONFIRMACAO_TOMADOR), CNPJ);

        assertFalse(xml.contains("cMotivo"), "o leiaute da confirmação só tem xDesc");
        assertFalse(xml.contains("xMotivo"));
    }

    @Test
    void rejeicao_carregaMotivoEDescricao() {
        String xml = EventoBuilder.manifestacao(ManifestacaoRequest.rejeicao(
                TipoAmbiente.PRODUCAO, CHAVE, TipoManifestacao.REJEICAO_TOMADOR,
                "1", "NFS-e emitida em duplicidade"), CNPJ);

        assertTrue(xml.contains("<cMotivo>1</cMotivo>"));
        assertTrue(xml.contains("<xMotivo>NFS-e emitida em duplicidade</xMotivo>"));
    }

    @Test
    void confirmacaoComMotivo_eRecusada() {
        assertThrows(IllegalArgumentException.class, () -> new ManifestacaoRequest(
                TipoAmbiente.PRODUCAO, CHAVE, TipoManifestacao.CONFIRMACAO_TOMADOR,
                "1", "Motivo que nao cabe aqui", null));
    }

    @Test
    void trocarConfirmacaoPorRejeicaoNasFactories_eRecusado() {
        assertThrows(IllegalArgumentException.class, () -> ManifestacaoRequest.confirmacao(
                TipoAmbiente.PRODUCAO, CHAVE, TipoManifestacao.REJEICAO_TOMADOR));

        assertThrows(IllegalArgumentException.class, () -> ManifestacaoRequest.rejeicao(
                TipoAmbiente.PRODUCAO, CHAVE, TipoManifestacao.CONFIRMACAO_TOMADOR, "1", null));
    }

    @Test
    void motivoOutros_exigeDescricao() {
        // E1944: com cMotivo = 9 (Outros), a descrição passa a ser obrigatória.
        assertThrows(IllegalArgumentException.class, () -> ManifestacaoRequest.rejeicao(
                TipoAmbiente.PRODUCAO, CHAVE, TipoManifestacao.REJEICAO_TOMADOR, "9", null));

        ManifestacaoRequest ok = ManifestacaoRequest.rejeicao(
                TipoAmbiente.PRODUCAO, CHAVE, TipoManifestacao.REJEICAO_TOMADOR,
                "9", "Outro motivo devidamente descrito");
        assertEquals("9", ok.codigoMotivo());
    }

    @Test
    void codigoDeMotivoForaDoDominio_eRecusado() {
        // TSCodMotivoRejeicao admite 1, 2, 3, 4, 5 e 9 — o 6 não existe.
        assertThrows(IllegalArgumentException.class, () -> ManifestacaoRequest.rejeicao(
                TipoAmbiente.PRODUCAO, CHAVE, TipoManifestacao.REJEICAO_TOMADOR,
                "6", "Motivo inexistente na tabela"));
    }

    @Test
    void autorPessoaFisica_saiComoCPFAutor() {
        String xml = EventoBuilder.manifestacao(
                ManifestacaoRequest.confirmacao(
                        TipoAmbiente.PRODUCAO, CHAVE, TipoManifestacao.CONFIRMACAO_TOMADOR),
                "12345678909");

        assertTrue(xml.contains("<CPFAutor>12345678909</CPFAutor>"));
        assertFalse(xml.contains("CNPJAutor"));
    }

    @Test
    void osDoisEventosDeMunicipio_ficamForaDoEnum() {
        // 205204 (Confirmação Tácita) e 205208 (Anulação da Rejeição) têm autoria do município de
        // incidência, conforme o AnexoII; expô-los como se o contribuinte pudesse emiti-los levaria
        // a rejeição por E0813.
        List<String> codigos = List.of(TipoManifestacao.values()).stream()
                .map(TipoManifestacao::getCodigo).toList();

        assertEquals(6, codigos.size());
        assertFalse(codigos.contains("205204"));
        assertFalse(codigos.contains("205208"));
    }
}
