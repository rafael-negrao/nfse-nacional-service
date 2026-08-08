package br.com.adelfo.nfse.nacional.service.dto.response;

/**
 * NFS-e recuperada por chave de acesso.
 *
 * @param chaveAcesso chave consultada
 * @param xmlNfse     XML da NFS-e, já decodificado de GZip+Base64
 */
public record ConsultaNfseResponse(String chaveAcesso, String xmlNfse) {
}
