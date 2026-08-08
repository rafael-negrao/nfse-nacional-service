package br.com.adelfo.nfse.nacional.service.dto.request;

/**
 * Manifestações de NFS-e que um <b>contribuinte</b> pode registrar.
 *
 * <p>O leiaute prevê oito manifestações; duas ficam de fora aqui porque o AnexoII, aba
 * "TIPO EVENTOS DE NFSe", atribui a autoria delas ao <b>município de incidência</b> e não ao
 * contribuinte: Confirmação Tácita ({@code 205204}) e Anulação da Rejeição ({@code 205208}).
 *
 * <p>Cada manifestação é <b>única por autor</b> ({@code E1833}): o mesmo não emitente não registra
 * confirmação e rejeição para a mesma nota.
 */
public enum TipoManifestacao {

    CONFIRMACAO_PRESTADOR("202201", "Manifestação de NFS-e - Confirmação do Prestador", false),
    CONFIRMACAO_TOMADOR("203202", "Manifestação de NFS-e - Confirmação do Tomador", false),
    CONFIRMACAO_INTERMEDIARIO("204203", "Manifestação de NFS-e - Confirmação do Intermediário", false),
    REJEICAO_PRESTADOR("202205", "Manifestação de NFS-e - Rejeição do Prestador", true),
    REJEICAO_TOMADOR("203206", "Manifestação de NFS-e - Rejeição do Tomador", true),
    REJEICAO_INTERMEDIARIO("204207", "Manifestação de NFS-e - Rejeição do Intermediário", true);

    private final String codigo;
    private final String descricao;
    private final boolean rejeicao;

    TipoManifestacao(String codigo, String descricao, boolean rejeicao) {
        this.codigo = codigo;
        this.descricao = descricao;
        this.rejeicao = rejeicao;
    }

    /** Código de 6 dígitos usado no {@code Id} do pedido de registro. */
    public String getCodigo() {
        return codigo;
    }

    /** Texto exato que o XSD exige em {@code xDesc} — é enumeração de valor único. */
    public String getDescricao() {
        return descricao;
    }

    /** Rejeições carregam motivo; confirmações não. */
    public boolean ehRejeicao() {
        return rejeicao;
    }
}
