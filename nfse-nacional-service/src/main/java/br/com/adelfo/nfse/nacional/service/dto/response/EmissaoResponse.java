package br.com.adelfo.nfse.nacional.service.dto.response;

/**
 * Resultado da emissão. O processamento da Sefin Nacional é <b>síncrono</b>: ou a DPS é rejeitada
 * (e vira {@code NfseException}) ou a NFS-e já volta gerada nesta resposta.
 *
 * @param chaveAcesso chave de acesso da NFS-e gerada (50 dígitos)
 * @param xmlNfse     XML da NFS-e, já decodificado de GZip+Base64
 */
public record EmissaoResponse(String chaveAcesso, String xmlNfse) {
}
