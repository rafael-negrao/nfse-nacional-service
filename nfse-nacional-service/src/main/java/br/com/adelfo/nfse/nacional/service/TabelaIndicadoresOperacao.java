package br.com.adelfo.nfse.nacional.service;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Indicadores de operação do IBS/CBS — o domínio de {@code cIndOp}, código de 6 dígitos.
 *
 * <p>26 códigos, de {@code ANEXO_C-INDOP_IBSCBS-SNNFSe-v1.01-20260122}. Cada um combina a
 * característica do fornecimento com o local que precisa ser identificado no DF-e — é o indicador
 * que diz, por exemplo, se o local relevante é o do imóvel, o do adquirente ou o do destinatário.
 *
 * <p>{@code E0901} rejeita código fora desta tabela.
 */
public final class TabelaIndicadoresOperacao {

    public static final String VERSAO_ANEXO = "ANEXO_C v1.01 (20260122)";

    private static final Map<String, IndicadorOperacao> POR_CODIGO = TabelaTsv.carregar(
            "/tabelas/indicadores-operacao-ibscbs-v1.01.tsv",
            c -> c[0],
            c -> new IndicadorOperacao(c[0], c[1], c[2]));

    private TabelaIndicadoresOperacao() {
    }

    public static boolean existe(String cIndOp) {
        return POR_CODIGO.containsKey(cIndOp);
    }

    public static Optional<IndicadorOperacao> buscar(String cIndOp) {
        return Optional.ofNullable(POR_CODIGO.get(cIndOp));
    }

    public static Collection<IndicadorOperacao> todos() {
        return POR_CODIGO.values();
    }

    /**
     * @param codigo           {@code cIndOp} de 6 dígitos
     * @param caracteristica   característica do fornecimento
     * @param localAIdentificar local que o DF-e precisa identificar para esta operação
     */
    public record IndicadorOperacao(String codigo, String caracteristica, String localAIdentificar) {
    }
}
