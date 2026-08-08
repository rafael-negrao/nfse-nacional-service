package br.com.adelfo.nfse.nacional.service.dto.response;

/**
 * Um documento fiscal devolvido pela distribuição do ADN.
 *
 * @param nsu             número sequencial único; guarde o maior do lote para a próxima consulta
 * @param chaveAcesso     chave de acesso da NFS-e a que o documento se refere
 * @param tipoDocumento   {@code NFSE}, {@code EVENTO}, {@code DPS}, {@code PEDIDO_REGISTRO_EVENTO},
 *                        {@code CNC} ou {@code NENHUM} — String, e não enum, porque a lista cresce
 *                        a cada versão do sistema e um valor novo não pode quebrar o consumidor
 * @param tipoEvento      preenchido quando {@code tipoDocumento} é um evento; ex.: {@code CANCELAMENTO}
 * @param xml             XML do documento, já decodificado de GZip+Base64
 * @param dataHoraGeracao carimbo de geração informado pelo ADN
 */
public record DocumentoDistribuido(long nsu,
                                   String chaveAcesso,
                                   String tipoDocumento,
                                   String tipoEvento,
                                   String xml,
                                   String dataHoraGeracao) {

    /** {@code true} quando o documento é a própria NFS-e, e não um evento dela. */
    public boolean ehNfse() {
        return "NFSE".equalsIgnoreCase(tipoDocumento);
    }
}
