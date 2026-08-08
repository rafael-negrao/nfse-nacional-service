package br.com.adelfo.nfse.nacional.service.dto.request;

import br.com.adelfo.nfse.nacional.client.TipoAmbiente;
import br.com.adelfo.nfse.nacional.service.Documentos;

/**
 * Consulta de NFS-e pela chave de acesso.
 *
 * @param ambiente    ambiente alvo
 * @param chaveAcesso chave de acesso da NFS-e (50 dígitos)
 */
public record ConsultaNfseRequest(TipoAmbiente ambiente, String chaveAcesso) {

    public ConsultaNfseRequest {
        if (ambiente == null) throw new IllegalArgumentException("ambiente é obrigatório");
        if (chaveAcesso == null || chaveAcesso.isBlank())
            throw new IllegalArgumentException("chaveAcesso é obrigatória");
        chaveAcesso = Documentos.chaveAcesso(chaveAcesso, "chaveAcesso");
    }
}
