package br.com.adelfo.nfse.nacional.service.dto.response;

/** Situação de um lote de distribuição de DF-e, conforme o campo {@code StatusProcessamento}. */
public enum StatusDistribuicao {

    /** Vieram documentos; há possivelmente mais adiante. */
    DOCUMENTOS_LOCALIZADOS,

    /** Não há documentos após o NSU consultado — é o sinal de fim da varredura. */
    NENHUM_DOCUMENTO_LOCALIZADO,

    /** O ADN recusou a consulta; veja os erros da resposta. */
    REJEICAO,

    /** Valor não previsto quando esta versão foi escrita — trate como fim da varredura. */
    DESCONHECIDO;

    public static StatusDistribuicao de(String valor) {
        if (valor == null) {
            return DESCONHECIDO;
        }
        for (StatusDistribuicao s : values()) {
            if (s.name().equalsIgnoreCase(valor)) {
                return s;
            }
        }
        return DESCONHECIDO;
    }
}
