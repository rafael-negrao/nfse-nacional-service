package br.com.adelfo.nfse.nacional.service.dto.response;

/**
 * Resposta de uma consulta de parametrização municipal cujo conteúdo o próprio contrato OpenAPI
 * do ADN declara como objeto <b>sem esquema</b> ({@code "type": "object"}, sem propriedades) —
 * caso de alíquotas e regimes especiais — ou cuja estrutura é profunda e ainda não estabilizou.
 *
 * <p>Modelar isso em records seria inventar um contrato que a Receita não publicou; o conteúdo é
 * entregue como JSON bruto para o chamador interpretar, e {@link #encontrado()} distingue
 * "não parametrizado" de "parametrizado".
 *
 * @param encontrado se o ADN devolveu conteúdo (HTTP 200) para os parâmetros consultados
 * @param mensagem   texto devolvido pelo ADN, presente inclusive quando nada foi encontrado
 * @param conteudo   JSON bruto do nó de dados; {@code null} quando não há conteúdo
 */
public record ParametroMunicipalResponse(boolean encontrado, String mensagem, String conteudo) {
}
