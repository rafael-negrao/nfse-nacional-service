package br.com.adelfo.nfse.nacional.service.dto.request;

import br.com.adelfo.nfse.nacional.client.TipoAmbiente;

/**
 * Consulta da chave de acesso da NFS-e a partir do identificador da DPS.
 *
 * <p>Composição do {@code idDps}: código IBGE do município emissor (7) + tipo de inscrição (1) +
 * inscrição federal (14 — CPF completado com zeros à esquerda) + série da DPS (5) + número da
 * DPS (15).
 *
 * <p>Por sigilo fiscal, a Sefin só devolve a chave se o certificado da conexão for de um ator da
 * NFS-e (prestador, tomador ou intermediário).
 *
 * @param ambiente ambiente alvo
 * @param idDps    identificador da DPS
 */
public record ConsultaDpsRequest(TipoAmbiente ambiente, String idDps) {

    public ConsultaDpsRequest {
        if (ambiente == null) throw new IllegalArgumentException("ambiente é obrigatório");
        if (idDps == null || idDps.isBlank()) throw new IllegalArgumentException("idDps é obrigatório");
    }
}
