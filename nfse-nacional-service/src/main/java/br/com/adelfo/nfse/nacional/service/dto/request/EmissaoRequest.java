package br.com.adelfo.nfse.nacional.service.dto.request;

import br.com.adelfo.nfse.nacional.client.TipoAmbiente;

/**
 * Emissão de NFS-e a partir de uma DPS.
 *
 * @param ambiente ambiente alvo — precisa coincidir com o {@code tpAmb} dentro do XML
 * @param xmlDps   XML da DPS <b>não assinado</b>; a assinatura XMLDSig é aplicada pela biblioteca
 */
public record EmissaoRequest(TipoAmbiente ambiente, String xmlDps) {

    public EmissaoRequest {
        if (ambiente == null) throw new IllegalArgumentException("ambiente é obrigatório");
        if (xmlDps == null || xmlDps.isBlank()) throw new IllegalArgumentException("xmlDps é obrigatório");
    }
}
