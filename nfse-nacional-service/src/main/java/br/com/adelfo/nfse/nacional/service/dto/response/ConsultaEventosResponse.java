package br.com.adelfo.nfse.nacional.service.dto.response;

import java.util.List;
import java.util.Optional;

/**
 * Documentos que o ADN devolve para uma chave de acesso.
 *
 * <p>A rota se chama {@code /Eventos}, mas <b>não devolve só eventos</b>: junto vem a própria
 * NFS-e. Confirmado em produção — uma nota cancelada retorna dois documentos, a NFS-e e o evento
 * de cancelamento, distinguidos por {@code TipoDocumento}. Por isso o campo é {@code documentos},
 * e não {@code eventos}: chamar tudo de evento faria o consumidor tratar a nota como um.
 *
 * @param chaveAcesso chave consultada
 * @param documentos  a NFS-e e seus eventos, em ordem de NSU; vazio quando a chave não existe
 */
public record ConsultaEventosResponse(String chaveAcesso, List<DocumentoDistribuido> documentos) {

    public ConsultaEventosResponse {
        documentos = documentos == null ? List.of() : List.copyOf(documentos);
    }

    /** Apenas os eventos, sem a NFS-e. */
    public List<DocumentoDistribuido> eventos() {
        return documentos.stream().filter(d -> !d.ehNfse()).toList();
    }

    /** A NFS-e a que os eventos se vinculam, se veio no lote. */
    public Optional<DocumentoDistribuido> nfse() {
        return documentos.stream().filter(DocumentoDistribuido::ehNfse).findFirst();
    }

    /** {@code true} quando existe evento de cancelamento vinculado à nota. */
    public boolean cancelada() {
        return eventos().stream().anyMatch(e -> "CANCELAMENTO".equalsIgnoreCase(e.tipoEvento()));
    }
}
