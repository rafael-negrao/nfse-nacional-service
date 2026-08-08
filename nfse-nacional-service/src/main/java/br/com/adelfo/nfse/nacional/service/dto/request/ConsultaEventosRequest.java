package br.com.adelfo.nfse.nacional.service.dto.request;

import br.com.adelfo.nfse.nacional.client.TipoAmbiente;
import br.com.adelfo.nfse.nacional.service.Documentos;

/**
 * Consulta de <b>todos</b> os eventos vinculados a uma NFS-e.
 *
 * <p>Diferente das demais consultas, esta rota fica no <b>ADN</b>
 * ({@code /contribuintes/NFSe/{chave}/Eventos}), não na Sefin — a Sefin só devolve um evento por
 * vez, identificado por tipo e sequencial.
 *
 * @param ambiente    ambiente alvo
 * @param chaveAcesso chave de acesso da NFS-e (50 dígitos)
 */
public record ConsultaEventosRequest(TipoAmbiente ambiente, String chaveAcesso) {

    public ConsultaEventosRequest {
        if (ambiente == null) throw new IllegalArgumentException("ambiente é obrigatório");
        if (chaveAcesso == null || chaveAcesso.isBlank())
            throw new IllegalArgumentException("chaveAcesso é obrigatória");
        chaveAcesso = Documentos.chaveAcesso(chaveAcesso, "chaveAcesso");
    }
}
