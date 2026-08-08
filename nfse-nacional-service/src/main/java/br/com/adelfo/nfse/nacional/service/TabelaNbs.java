package br.com.adelfo.nfse.nacional.service;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Nomenclatura Brasileira de Serviços 2.0 — o domínio de {@code cNBS}, código de 9 dígitos.
 *
 * <p>A NBS é hierárquica e o anexo publica os níveis intermediários junto com as folhas. Das 1.210
 * linhas da aba {@code LISTA.NBS_v2.0}, <b>918 são códigos utilizáveis</b>; as outras 292 são nós
 * de agrupamento com 5, 6, 7 ou 8 dígitos, que o XSD não aceita — ele tipa {@code cNBS} como
 * {@code [0-9]{9}}. Exemplo: {@code 1.0401.11} e {@code 1.0401.11.1} agrupam {@code 1.0401.11.11},
 * {@code 1.0401.11.19} e {@code 1.0401.11.20}, e só estes três podem ir na DPS.
 *
 * <p>O código {@code 999999999} — o genérico, e o que a NFS-e real consultada em produção usa —
 * vem <b>sem descrição</b> no anexo. Está no catálogo assim: {@link #descricao(String)} devolve
 * vazio para ele, porque o catálogo não inventa texto.
 *
 * <p>Dado versionado no jar; ver {@link ListaServicoNacional} sobre regeneração.
 */
public final class TabelaNbs {

    public static final String VERSAO_ANEXO = "ANEXO_B v1.01 (20260122)";

    private static final Map<String, String> POR_CODIGO = TabelaTsv.carregar(
            "/tabelas/nbs-v2.0.tsv", c -> c[0], c -> c.length > 1 ? c[1] : "");

    private TabelaNbs() {
    }

    /** {@code true} se o código é uma folha válida da NBS. */
    public static boolean existe(String cNBS) {
        return POR_CODIGO.containsKey(cNBS);
    }

    /**
     * Descrição do código — alimenta o {@code xNBS}. Vazio quando o anexo não a traz, o que hoje
     * só ocorre em {@code 999999999}.
     */
    public static Optional<String> descricao(String cNBS) {
        return Optional.ofNullable(POR_CODIGO.get(cNBS)).filter(d -> !d.isBlank());
    }

    public static Collection<String> codigos() {
        return POR_CODIGO.keySet();
    }

    /**
     * Código na forma pontuada em que o anexo o publica: {@code 1.0101.11.00} para
     * {@code 101011100}. Só apresentação — no XML vai sem pontos.
     */
    public static String formatar(String cNBS) {
        if (cNBS == null || !cNBS.matches("[0-9]{9}")) {
            throw new IllegalArgumentException("cNBS deve ter 9 dígitos; recebido: " + cNBS);
        }
        return cNBS.charAt(0) + "." + cNBS.substring(1, 5) + "."
                + cNBS.substring(5, 7) + "." + cNBS.substring(7, 9);
    }
}
