package br.com.adelfo.nfse.nacional.service;

import br.com.adelfo.nfse.nacional.client.NfseHttpException;
import br.com.adelfo.nfse.nacional.service.exception.NfseException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Os corpos usados aqui foram <b>capturados da Sefin Nacional em produção restrita</b>
 * (SefinNacional_1.6.0) pelo {@code ConsultasSefinIT}, não inventados.
 */
class RespostaDeErroTest {

    /** Resposta real de GET /nfse/{chave} para uma chave inexistente. */
    private static final String CHAVE_NAO_ENCONTRADA = """
            {
              "tipoAmbiente": 2,
              "versaoAplicativo": "SefinNacional_1.6.0",
              "dataHoraProcessamento": "2026-08-07T21:58:58.0067623-03:00",
              "erro": {
                "codigo": "E2401",
                "descricao": "Chave de acesso não encontrada."
              }
            }""";

    /** Resposta real de GET /dps/{id} para uma DPS que não gerou nota. */
    private static final String DPS_SEM_NOTA = """
            {
              "tipoAmbiente": 0,
              "dataHoraProcessamento": "2026-08-07T21:58:57.9699743-03:00",
              "erro": {
                "codigo": "E2404",
                "descricao": "Não foi gerada uma NFS-e com o identificador de DPS informado"
              }
            }""";

    /**
     * Resposta real de POST /nfse — a <b>primeira rejeição de emissão</b> que este projeto
     * recebeu. Repare em duas diferenças em relação às consultas: {@code erros} é uma lista e as
     * chaves vêm em <b>PascalCase</b>. Enquanto a busca era sensível a maiúsculas, esta rejeição
     * chegava ao chamador como código {@code "400"} com o JSON inteiro por mensagem.
     */
    private static final String EMISSAO_REJEITADA = """
            {
              "tipoAmbiente": 2,
              "versaoAplicativo": "SefinNacional_1.6.0",
              "dataHoraProcessamento": "2026-08-08T12:21:58.5802588-03:00",
              "idDPS": "DPS355030820616996600013300001000000000000900",
              "erros": [
                {
                  "Codigo": "E0120",
                  "Descricao": "IM do prestador não deve ser informado, pois não existem informações complementares registradas no CNC NFS-e do município emissor informado na DPS."
                }
              ]
            }""";

    /** Mesma forma, com mais de um apontamento — a validação da DPS não para no primeiro. */
    private static final String EMISSAO_REJEITADA_EM_LOTE = """
            {
              "tipoAmbiente": 2,
              "erros": [
                { "Codigo": "E0120", "Descricao": "IM do prestador não deve ser informado." },
                { "Codigo": "E1300", "Descricao": "Alíquota aplicada não pode ser superior a 5%." }
              ]
            }""";

    @Test
    void emissaoRejeitada_extraiOCodigoAindaQueAsChavesVenhamEmPascalCase() {
        NfseException e = RespostaDeErro.traduzir(new NfseHttpException(400, EMISSAO_REJEITADA));

        assertEquals("E0120", e.getCodigo(), "o código de negócio não pode virar o status HTTP");
        assertTrue(e.getMensagem().startsWith("IM do prestador não deve ser informado"),
                "a mensagem deve ser a descrição da regra, não o JSON inteiro: " + e.getMensagem());
        assertFalse(e.isLimiteDeRequisicoes());
        assertFalse(e.isServicoIndisponivel());
    }

    @Test
    void varriasRejeicoes_saoTodasReportadas() {
        NfseException e = RespostaDeErro.traduzir(
                new NfseHttpException(400, EMISSAO_REJEITADA_EM_LOTE));

        // Reportar só a primeira faria o chamador corrigir um campo por reenvio.
        assertEquals("E0120", e.getCodigo(), "o código é o da primeira rejeição");
        assertTrue(e.getMensagem().contains("E0120"), e.getMensagem());
        assertTrue(e.getMensagem().contains("E1300"),
                "a segunda rejeição não pode se perder: " + e.getMensagem());
        assertTrue(e.getMensagem().contains("2 rejeições"), e.getMensagem());
    }

    @Test
    void envelopeComErroSingular_extraiCodigoEDescricao() {
        NfseException e = RespostaDeErro.traduzir(new NfseHttpException(404, CHAVE_NAO_ENCONTRADA));

        assertEquals("E2401", e.getCodigo());
        assertEquals("Chave de acesso não encontrada.", e.getMensagem());
        assertEquals(CHAVE_NAO_ENCONTRADA, e.getCorpoResposta(), "o corpo bruto é preservado");
    }

    @Test
    void envelopeDaConsultaPorDps_extraiCodigoEDescricao() {
        NfseException e = RespostaDeErro.traduzir(new NfseHttpException(404, DPS_SEM_NOTA));

        assertEquals("E2404", e.getCodigo());
        assertTrue(e.getMensagem().startsWith("Não foi gerada uma NFS-e"), e.getMensagem());
    }

    @Test
    void listaDeErros_tomaOPrimeiroCodigoEPreservaTodasAsDescricoes() {
        String corpo = """
                {"erros":[{"codigo":"E0004","descricao":"Identificador da DPS inválido"},
                          {"codigo":"E0010","descricao":"Outro apontamento"}]}""";

        NfseException e = RespostaDeErro.traduzir(new NfseHttpException(400, corpo));

        // getCodigo() é campo único, então fica o do primeiro apontamento; a mensagem, porém,
        // carrega todos — ver varriasRejeicoes_saoTodasReportadas para o porquê.
        assertEquals("E0004", e.getCodigo());
        assertTrue(e.getMensagem().contains("Identificador da DPS inválido"), e.getMensagem());
        assertTrue(e.getMensagem().contains("Outro apontamento"), e.getMensagem());
    }

    @Test
    void umUnicoApontamento_naoGanhaNumeracao() {
        String corpo = """
                {"erros":[{"codigo":"E0004","descricao":"Identificador da DPS inválido"}]}""";

        NfseException e = RespostaDeErro.traduzir(new NfseHttpException(400, corpo));

        assertEquals("Identificador da DPS inválido", e.getMensagem(),
                "com uma só rejeição a mensagem é a descrição limpa");
    }

    @Test
    void paginaHtmlDeRotaInexistente_caiNoStatusHttp() {
        // Rota que não existe devolve a página de erro do ASP.NET em que a Sefin roda; inventar um
        // código de negócio a partir disso mascararia um erro de URL como rejeição fiscal.
        String html = "<!DOCTYPE html><html><body>The resource cannot be found.</body></html>";

        NfseException e = RespostaDeErro.traduzir(new NfseHttpException(404, html));

        assertEquals("404", e.getCodigo());
        assertEquals(html, e.getMensagem());
    }

    @Test
    void limiteDeRequisicoes_viraMensagemAcionavel() {
        // O ADN devolve uma página HTML no 429; despejá-la como "mensagem de rejeição" faz o
        // chamador procurar erro no documento, quando o problema é ritmo de chamada.
        String html = "<html><body><h1>429 Too Many Requests</h1>"
                + "You have sent too many requests in a given amount of time.</body></html>";

        NfseException e = RespostaDeErro.traduzir(new NfseHttpException(429, html));

        assertEquals("429", e.getCodigo());
        assertTrue(e.isLimiteDeRequisicoes());
        assertFalse(e.getMensagem().contains("<html>"), "a mensagem não deve ser a página HTML");
        assertTrue(e.getMensagem().contains("Espace as chamadas"), e.getMensagem());
        assertEquals(html, e.getCorpoResposta(), "o corpo bruto continua disponível");
    }

    @Test
    void servicoIndisponivel_eDistinguidoDeRejeicao() {
        NfseException e = RespostaDeErro.traduzir(new NfseHttpException(503,
                "<html><body><h1>503 Service Unavailable</h1></body></html>"));

        assertTrue(e.isServicoIndisponivel());
        assertFalse(e.isLimiteDeRequisicoes());
        assertTrue(e.getMensagem().contains("indisponível"), e.getMensagem());
    }

    @Test
    void corpoVazio_naoQuebra() {
        NfseException e = RespostaDeErro.traduzir(new NfseHttpException(500, ""));

        assertEquals("500", e.getCodigo());
    }
}
