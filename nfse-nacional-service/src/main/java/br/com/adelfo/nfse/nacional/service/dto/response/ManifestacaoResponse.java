package br.com.adelfo.nfse.nacional.service.dto.response;

import br.com.adelfo.nfse.nacional.service.dto.request.TipoManifestacao;

/**
 * Resultado do registro de uma manifestação — síncrono, como o cancelamento.
 *
 * @param chaveAcesso chave da NFS-e manifestada
 * @param tipo        manifestação registrada
 * @param xmlEvento   XML do evento gerado, já decodificado de GZip+Base64
 */
public record ManifestacaoResponse(String chaveAcesso, TipoManifestacao tipo, String xmlEvento) {
}
