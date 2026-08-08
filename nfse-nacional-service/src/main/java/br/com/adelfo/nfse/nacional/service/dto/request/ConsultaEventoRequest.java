package br.com.adelfo.nfse.nacional.service.dto.request;

import br.com.adelfo.nfse.nacional.client.TipoAmbiente;
import br.com.adelfo.nfse.nacional.service.Documentos;

/**
 * Consulta de um evento específico de uma NFS-e.
 *
 * <p>A Swagger da Sefin expõe <b>apenas</b> a forma completa
 * {@code /nfse/{chave}/eventos/{tipoEvento}/{numSeqEvento}}. As variantes sem sequencial e sem
 * tipo, descritas no manual em prosa, não existem: sem o sequencial a rota responde 405 (só
 * aceita POST, que é o registro do evento). Para listar <b>todos</b> os eventos de uma nota use
 * {@code consultarEventos}, que vai ao ADN.
 *
 * @param ambiente    ambiente alvo
 * @param chaveAcesso chave de acesso da NFS-e (50 dígitos)
 * @param tipoEvento  código do evento, ex.: {@code "101101"} para cancelamento
 * @param sequencial  número sequencial do evento; {@code 1} quando o tipo admite um só por nota
 */
public record ConsultaEventoRequest(TipoAmbiente ambiente,
                                    String chaveAcesso,
                                    String tipoEvento,
                                    int sequencial) {

    public ConsultaEventoRequest {
        if (ambiente == null) throw new IllegalArgumentException("ambiente é obrigatório");
        if (chaveAcesso == null || chaveAcesso.isBlank())
            throw new IllegalArgumentException("chaveAcesso é obrigatória");
        chaveAcesso = Documentos.chaveAcesso(chaveAcesso, "chaveAcesso");
        if (tipoEvento == null || tipoEvento.isBlank())
            throw new IllegalArgumentException("tipoEvento é obrigatório");
        if (sequencial < 1) throw new IllegalArgumentException("sequencial começa em 1");
    }

    /** Consulta o cancelamento (evento 101101), que admite um único registro por nota. */
    public static ConsultaEventoRequest cancelamento(TipoAmbiente ambiente, String chaveAcesso) {
        return new ConsultaEventoRequest(ambiente, chaveAcesso, "101101", 1);
    }
}
