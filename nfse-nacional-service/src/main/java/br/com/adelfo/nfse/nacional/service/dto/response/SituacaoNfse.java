package br.com.adelfo.nfse.nacional.service.dto.response;

/**
 * Situação de uma NFS-e, derivada dos eventos vinculados a ela.
 *
 * <p><b>A NFS-e não muda depois de emitida.</b> O {@code cStat} gravado nela continua
 * {@code 100 - NFS-e Gerada} mesmo após o cancelamento — é o que o portal mostra ao abrir a nota,
 * e o que faz parecer que o cancelamento não pegou. Cancelar registra um <b>evento</b> separado,
 * ligado à nota pela chave de acesso; a situação real só se conhece lendo a nota junto com os
 * eventos. Ver {@link ConsultaEventosResponse#situacao()}.
 */
public enum SituacaoNfse {

    /** Nenhum evento que encerre a validade, e nenhuma análise de cancelamento pendente. */
    VALIDA,

    /**
     * Cancelada pelo emitente ({@code e101101}), por deferimento de análise fiscal
     * ({@code e105104}) ou por ofício do município ({@code e305101}).
     */
    CANCELADA,

    /**
     * Cancelada por substituição ({@code e105102}): outra NFS-e a substituiu. É cancelamento —
     * a nota não vale mais —, mas, diferente de {@link #CANCELADA}, a operação continua
     * documentada, agora pela substituta.
     */
    SUBSTITUIDA,

    /**
     * Há solicitação de análise fiscal para cancelamento ({@code e101103}) ainda sem deferimento
     * nem indeferimento. A nota segue válida até a decisão do município.
     */
    CANCELAMENTO_EM_ANALISE,

    /** O ADN não devolveu documento algum para a chave — ela não existe naquele ambiente. */
    NAO_ENCONTRADA;

    /** {@code true} quando a nota não vale mais, qualquer que tenha sido a forma de cancelamento. */
    public boolean cancelada() {
        return this == CANCELADA || this == SUBSTITUIDA;
    }
}
