package br.com.adelfo.nfse.nacional.service.dto.request;

import br.com.adelfo.nfse.nacional.client.TipoAmbiente;

/**
 * Distribuição de DF-e a partir de um NSU.
 *
 * <p>Não existe consulta de NFS-e por período no Sistema Nacional: para varrer os documentos em que
 * o contribuinte figura como emitente, tomador ou intermediário, percorre-se a numeração sequencial
 * (NSU), como na distribuição de DF-e da NF-e. Filtrar por data é trabalho do chamador, sobre o que
 * a varredura trouxer.
 *
 * @param ambiente     ambiente alvo
 * @param nsu          último NSU já processado; o ADN devolve os documentos <b>posteriores</b> a ele.
 *                     Comece em {@code 0} para varrer desde o início
 * @param cnpjConsulta CNPJ a consultar, quando diferente do titular do certificado; precisa ter a
 *                     mesma <b>raiz</b> — útil para a matriz varrer filiais. {@code null} usa o
 *                     próprio certificado
 * @param lote         pede o retorno em lote em vez de um documento por vez
 */
public record ConsultaDFeRequest(TipoAmbiente ambiente, long nsu, String cnpjConsulta, boolean lote) {

    public ConsultaDFeRequest {
        if (ambiente == null) throw new IllegalArgumentException("ambiente é obrigatório");
        if (nsu < 0) throw new IllegalArgumentException("nsu não pode ser negativo: " + nsu);
    }

    /** Varredura em lote a partir do NSU informado, com o CNPJ do próprio certificado. */
    public static ConsultaDFeRequest aPartirDe(TipoAmbiente ambiente, long nsu) {
        return new ConsultaDFeRequest(ambiente, nsu, null, true);
    }

    /** Varredura em lote desde o início. */
    public static ConsultaDFeRequest doInicio(TipoAmbiente ambiente) {
        return aPartirDe(ambiente, 0);
    }
}
