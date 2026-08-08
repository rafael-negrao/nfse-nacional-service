package br.com.adelfo.nfse.nacional.service;

import br.com.adelfo.nfse.nacional.client.TipoAmbiente;
import br.com.adelfo.nfse.nacional.service.dto.request.CancelamentoRequest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Valida o XML produzido pelo {@link EventoBuilder} contra o XSD oficial
 * {@code pedRegEvento_v1.01.xsd}, empacotado em {@code nfse-nacional-schemas}.
 *
 * <p>Não exige certificado nem rede: a assinatura é opcional no leiaute do pedido
 * ({@code ds:Signature minOccurs="0"}), então o documento não assinado já é validável.
 */
class EventoBuilderTest {

    /** Chave de acesso fictícia com os 50 dígitos que o XSD exige. */
    private static final String CHAVE_ACESSO = "1".repeat(49) + "9";

    private static List<String> errosDeSchema(String xml) throws Exception {
        return ValidacaoXsd.erros("pedRegEvento_v1.01.xsd", xml);
    }

    private static void validar(String xml) throws Exception {
        assertEquals(List.of(), errosDeSchema(xml), "XML deve ser válido no XSD oficial");
    }

    @Test
    void cancelamentoPorCnpj_geraXmlValidoNoSchema() throws Exception {
        CancelamentoRequest request = CancelamentoRequest.de(
                TipoAmbiente.PRODUCAO_RESTRITA, CHAVE_ACESSO, "1", "Erro na emissão da nota");

        String xml = EventoBuilder.cancelamento(request, "12345678000195");

        validar(xml);
        assertTrue(xml.contains("<xDesc>Cancelamento de NFS-e</xDesc>"));
        assertTrue(xml.contains("Id=\"PRE" + CHAVE_ACESSO + "101101\""));
    }

    @Test
    void cancelamentoPorCpf_geraXmlValidoNoSchema() throws Exception {
        CancelamentoRequest request = CancelamentoRequest.de(
                TipoAmbiente.PRODUCAO, CHAVE_ACESSO, "2", "Serviço não prestado ao cliente");

        String xml = EventoBuilder.cancelamento(request, "12345678909");

        validar(xml);
        assertTrue(xml.contains("<CPFAutor>12345678909</CPFAutor>"));
    }

    @Test
    void dhEvento_naoPodeTerFracaoDeSegundos() {
        // O pattern de TSDateTimeUTC rejeita fração de segundos; ISO_OFFSET_DATE_TIME a emite.
        // Este teste trava a regressão, que só apareceria como rejeição de schema em produção.
        String xml = EventoBuilder.cancelamento(CancelamentoRequest.de(
                TipoAmbiente.PRODUCAO_RESTRITA, CHAVE_ACESSO, "9", "Cancelamento por outros motivos"),
                "12345678000195");

        String dhEvento = xml.replaceAll("(?s).*<dhEvento>(.*?)</dhEvento>.*", "$1");
        assertTrue(dhEvento.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}[+-]\\d{2}:\\d{2}"),
                "dhEvento fora do formato AAAA-MM-DDThh:mm:ssTZD: " + dhEvento);
    }

    @Test
    void idPedidoRegistroEvento_seguePadraoDoXsd() {
        String id = EventoBuilder.idPedidoRegistroEvento(CHAVE_ACESSO, EventoBuilder.EVENTO_CANCELAMENTO);

        // TSIdPedRegEvt: pattern PRE[0-9]{56} = chave (50) + tipo do evento (6)
        assertEquals(59, id.length());
        assertTrue(id.matches("PRE[0-9]{56}"), "Id fora do pattern TSIdPedRegEvt: " + id);
    }

    /**
     * Amostra <b>real</b>, colhida da distribuição de DF-e em produção (NSU 35), do pedido de
     * registro embutido no evento de cancelamento que a Sefin gerou:
     *
     * <pre>
     * &lt;infPedReg Id="PRE...101101"&gt;
     *   &lt;tpAmb&gt;1&lt;/tpAmb&gt;&lt;verAplic&gt;…&lt;/verAplic&gt;&lt;dhEvento&gt;2026-07-17T15:31:30-03:00&lt;/dhEvento&gt;
     *   &lt;CNPJAutor&gt;…&lt;/CNPJAutor&gt;&lt;chNFSe&gt;…&lt;/chNFSe&gt;
     *   &lt;e101101&gt;&lt;xDesc&gt;Cancelamento de NFS-e&lt;/xDesc&gt;&lt;cMotivo&gt;9&lt;/cMotivo&gt;&lt;xMotivo&gt;…&lt;/xMotivo&gt;&lt;/e101101&gt;
     * &lt;/infPedReg&gt;
     * </pre>
     *
     * <p>Este teste fixa a sequência de elementos contra essa amostra. O {@code cancelar} nunca foi
     * exercitado contra a Sefin, então a amostra é a evidência mais forte disponível de que o
     * documento que produzimos tem a forma certa.
     */
    @Test
    void sequenciaDeElementos_bateComAmostraRealDeProducao() {
        String xml = EventoBuilder.cancelamento(CancelamentoRequest.de(
                TipoAmbiente.PRODUCAO, CHAVE_ACESSO, "9", "Cancelamento de NFS-e por outro motivo"),
                "12345678000195");

        List<String> esperada = List.of(
                "pedRegEvento", "infPedReg", "tpAmb", "verAplic", "dhEvento",
                "CNPJAutor", "chNFSe", "e101101", "xDesc", "cMotivo", "xMotivo");

        List<String> obtida = new ArrayList<>();
        Matcher m = Pattern.compile("<([A-Za-z][\\w.]*)[ >]").matcher(xml);
        while (m.find()) {
            obtida.add(m.group(1));
        }

        assertEquals(esperada, obtida, "a ordem dos elementos deve reproduzir a amostra de produção");
        assertTrue(xml.contains("versao=\"1.01\""));
    }

    @Test
    void descricaoDoMotivo_abaixoDoMinimo_eRecusadaAntesDeGerarXml() {
        // TSMotivo exige 15 a 255 caracteres e o leiaute marca xMotivo como 1-1 no e101101.
        // Sem esta validação, uma justificativa curta ou nula só quebraria no schema da Sefin.
        assertThrows(IllegalArgumentException.class, () -> CancelamentoRequest.de(
                TipoAmbiente.PRODUCAO_RESTRITA, CHAVE_ACESSO, "1", "Erro"));

        assertThrows(IllegalArgumentException.class, () -> CancelamentoRequest.de(
                TipoAmbiente.PRODUCAO_RESTRITA, CHAVE_ACESSO, "1", null));

        assertThrows(IllegalArgumentException.class, () -> CancelamentoRequest.de(
                TipoAmbiente.PRODUCAO_RESTRITA, CHAVE_ACESSO, "1", "x".repeat(256)));
    }

    @Test
    void autorPessoaFisica_saiComoCPFAutor() {
        String xml = EventoBuilder.cancelamento(CancelamentoRequest.de(
                TipoAmbiente.PRODUCAO, CHAVE_ACESSO, "1", "Erro na emissao da nota"), "12345678909");

        assertTrue(xml.contains("<CPFAutor>12345678909</CPFAutor>"));
        assertFalse(xml.contains("CNPJAutor"), "CNPJAutor e CPFAutor são exclusivos no leiaute");
    }

    @Test
    void autorForaDoFormato_eRecusado() {
        // O autor sai do certificado; um valor com tamanho errado indica extração falha, e é
        // melhor estourar aqui do que emitir um pedido que a Sefin recusa por E0812.
        assertThrows(IllegalArgumentException.class, () -> EventoBuilder.cancelamento(
                CancelamentoRequest.de(TipoAmbiente.PRODUCAO, CHAVE_ACESSO, "1", "Erro na emissao"), "123"));
    }

    @Test
    void codigoMotivoForaDoDominio_eRejeitadoPeloSchema() throws Exception {
        // TSCodJustCanc admite apenas 1, 2 e 9 — o XSD é a última linha de defesa contra
        // um código inválido chegar à Sefin.
        String xml = EventoBuilder.cancelamento(CancelamentoRequest.de(
                TipoAmbiente.PRODUCAO_RESTRITA, CHAVE_ACESSO, "7", "Motivo fora do dominio"),
                "12345678000195");

        assertFalse(errosDeSchema(xml).isEmpty(), "código de motivo fora do domínio deve ser rejeitado");
    }
}
