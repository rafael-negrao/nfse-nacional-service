package br.com.adelfo.nfse.nacional.client;

/**
 * Ambientes do Sistema Nacional NFS-e.
 *
 * <p>Carrega tanto o código {@code tpAmb} usado dentro do XML (DPS / evento) quanto as URLs base
 * das duas APIs distintas do sistema:
 * <ul>
 *   <li><b>Sefin Nacional</b> — emissão, consulta e eventos (o que esta biblioteca consome);</li>
 *   <li><b>ADN</b> (Ambiente de Dados Nacional) — distribuição/compartilhamento de DF-e e DANFSE.</li>
 * </ul>
 *
 * <p>Manter as URLs aqui evita que endpoint fique espalhado pelo código — o mesmo padrão adotado
 * em {@code nfe-sefaz-sp}.
 */
public enum TipoAmbiente {

    /** tpAmb=1 — produção. */
    PRODUCAO("1",
            "https://sefin.nfse.gov.br/SefinNacional",
            "https://adn.nfse.gov.br"),

    /** tpAmb=2 — produção restrita (equivalente à homologação da NF-e). */
    PRODUCAO_RESTRITA("2",
            "https://sefin.producaorestrita.nfse.gov.br/API/SefinNacional",
            "https://adn.producaorestrita.nfse.gov.br");

    private final String codigo;
    private final String urlSefinNacional;
    private final String urlAdn;

    TipoAmbiente(String codigo, String urlSefinNacional, String urlAdn) {
        this.codigo = codigo;
        this.urlSefinNacional = urlSefinNacional;
        this.urlAdn = urlAdn;
    }

    /** Valor do campo {@code tpAmb} no XML da DPS e do evento. */
    public String getCodigo() {
        return codigo;
    }

    /** URL base da API Sefin Nacional, sem barra final. */
    public String getUrlSefinNacional() {
        return urlSefinNacional;
    }

    /** URL base da API do ADN, sem barra final. */
    public String getUrlAdn() {
        return urlAdn;
    }
}
