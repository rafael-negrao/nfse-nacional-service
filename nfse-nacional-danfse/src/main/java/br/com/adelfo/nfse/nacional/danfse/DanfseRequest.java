package br.com.adelfo.nfse.nacional.danfse;

import br.com.adelfo.nfse.nacional.service.dto.response.SituacaoNfse;

/**
 * O que imprimir.
 *
 * <p>A situação vem à parte porque <b>não está no XML da NFS-e</b>: a nota continua com
 * {@code cStat 100} depois de cancelada, e o cancelamento é um evento separado. Sem ela, o DANFSe
 * de uma nota cancelada sairia sem a marca d'água "CANCELADA" que a NT 008 (item 2.5.1) exige.
 * Obtenha-a com {@code NfseService.consultarSituacao}.
 *
 * @param xmlNfse  XML da NFS-e, como devolvido pela emissão ou pela consulta por chave
 * @param situacao situação da nota; {@link SituacaoNfse#CANCELADA} e
 *                 {@link SituacaoNfse#SUBSTITUIDA} imprimem a marca d'água
 * @param canhoto  imprime o bloco de canhoto, que a NT 008 torna opcional (item 2.3.3)
 */
public record DanfseRequest(String xmlNfse, SituacaoNfse situacao, boolean canhoto) {

    public DanfseRequest {
        if (xmlNfse == null || xmlNfse.isBlank()) {
            throw new IllegalArgumentException("xmlNfse é obrigatório");
        }
        if (situacao == null) {
            situacao = SituacaoNfse.VALIDA;
        }
    }

    /** Nota válida, com canhoto. */
    public static DanfseRequest de(String xmlNfse) {
        return new DanfseRequest(xmlNfse, SituacaoNfse.VALIDA, true);
    }

    public DanfseRequest comSituacao(SituacaoNfse situacao) {
        return new DanfseRequest(xmlNfse, situacao, canhoto);
    }

    public DanfseRequest semCanhoto() {
        return new DanfseRequest(xmlNfse, situacao, false);
    }
}
