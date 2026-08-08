package br.com.adelfo.nfse.nacional.service.dto.response;

/**
 * Resultado do pedido de registro de evento de cancelamento — também síncrono.
 *
 * @param chaveAcesso chave da NFS-e cancelada
 * @param xmlEvento   XML do evento registrado, já decodificado de GZip+Base64
 */
public record CancelamentoResponse(String chaveAcesso, String xmlEvento) {
}
