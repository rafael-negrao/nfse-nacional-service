package br.com.adelfo.nfse.nacional.service.dto.request;

import br.com.adelfo.nfse.nacional.client.TipoAmbiente;

/**
 * Emissão de NFS-e por decisão administrativa ou judicial — o fluxo "bypass".
 *
 * <p>Diferente de {@link EmissaoRequest}, o XML aqui é a <b>NFS-e completa</b>, não a DPS: a
 * plataforma não calcula nada, apenas valida o mínimo. Monte-o com
 * {@code NfseDecisaoJudicialBuilder}.
 *
 * @param ambiente ambiente alvo
 * @param xmlNfse  XML da NFS-e <b>não assinado</b>; a assinatura é aplicada pela biblioteca
 */
public record EmissaoDecisaoJudicialRequest(TipoAmbiente ambiente, String xmlNfse) {

    public EmissaoDecisaoJudicialRequest {
        if (ambiente == null) throw new IllegalArgumentException("ambiente é obrigatório");
        if (xmlNfse == null || xmlNfse.isBlank())
            throw new IllegalArgumentException("xmlNfse é obrigatório");
    }
}
