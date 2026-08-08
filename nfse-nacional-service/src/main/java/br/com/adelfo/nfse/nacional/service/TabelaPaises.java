package br.com.adelfo.nfse.nacional.service;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Países ISO 3166-1 alpha-2 — o domínio de {@code cPais}, {@code cPaisPrestacao} e
 * {@code cPaisResult}.
 *
 * <p>250 códigos, de {@code ANEXO_A-MUNICIPIO_IBGE-PAISES_ISO2-v1.00-SNNFSe-20251210}, aba
 * {@code TAB.PAÍS_ISO2}.
 */
public final class TabelaPaises {

    public static final String VERSAO_ANEXO = "ANEXO_A v1.00 (20251210)";

    private static final Map<String, String> POR_CODIGO = TabelaTsv.carregar(
            "/tabelas/paises-iso2-v1.00.tsv", c -> c[0], c -> c[1]);

    private TabelaPaises() {
    }

    public static boolean existe(String codigoIso2) {
        return POR_CODIGO.containsKey(codigoIso2);
    }

    public static Optional<String> nome(String codigoIso2) {
        return Optional.ofNullable(POR_CODIGO.get(codigoIso2));
    }

    public static Collection<String> codigos() {
        return POR_CODIGO.keySet();
    }
}
