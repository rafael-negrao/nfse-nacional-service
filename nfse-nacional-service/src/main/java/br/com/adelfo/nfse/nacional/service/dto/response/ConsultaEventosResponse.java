package br.com.adelfo.nfse.nacional.service.dto.response;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Documentos que o ADN devolve para uma chave de acesso.
 *
 * <p>A rota se chama {@code /Eventos}, mas <b>não devolve só eventos</b>: junto vem a própria
 * NFS-e. Confirmado em produção — uma nota cancelada retorna dois documentos, a NFS-e e o evento
 * de cancelamento, distinguidos por {@code TipoDocumento}. Por isso o campo é {@code documentos},
 * e não {@code eventos}: chamar tudo de evento faria o consumidor tratar a nota como um.
 *
 * <p>É aqui que se descobre se a nota está cancelada: a NFS-e em si continua com
 * {@code cStat 100} depois do cancelamento. Ver {@link #situacao()}.
 *
 * @param chaveAcesso chave consultada
 * @param documentos  a NFS-e e seus eventos, em ordem de NSU; vazio quando a chave não existe
 */
public record ConsultaEventosResponse(String chaveAcesso, List<DocumentoDistribuido> documentos) {

    /**
     * Eventos que encerram a validade da nota, com o nome que o ADN usa em {@code TipoEvento}
     * (enum da Swagger de {@code adn-contribuintes}). O ANEXO_II lista quatro formas de
     * cancelamento; só a primeira é do emitente, as outras três são do município.
     */
    private static final Set<String> CANCELAMENTOS = Set.of(
            "CANCELAMENTO",                          // e101101 — emitente
            "CANCELAMENTO_DEFERIDO_ANALISE_FISCAL",  // e105104 — município, após análise
            "CANCELAMENTO_POR_OFICIO");              // e305101 — município, de ofício

    private static final String SUBSTITUICAO = "CANCELAMENTO_POR_SUBSTITUICAO";      // e105102
    private static final String SOLICITACAO_ANALISE = "SOLICITACAO_CANCELAMENTO_ANALISE_FISCAL"; // e101103
    private static final String INDEFERIMENTO = "CANCELAMENTO_INDEFERIDO_ANALISE_FISCAL";       // e105105

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

    /**
     * Situação da nota, derivada dos eventos.
     *
     * <p>Cancelamento e substituição prevalecem sobre tudo. Uma solicitação de análise fiscal
     * deixa a nota {@link SituacaoNfse#CANCELAMENTO_EM_ANALISE} até haver indeferimento com NSU
     * posterior a ela; o deferimento já é um dos cancelamentos.
     */
    public SituacaoNfse situacao() {
        if (documentos.isEmpty()) {
            return SituacaoNfse.NAO_ENCONTRADA;
        }
        if (eventoDoTipo(SUBSTITUICAO).isPresent()) {
            return SituacaoNfse.SUBSTITUIDA;
        }
        if (eventoDeCancelamento().isPresent()) {
            return SituacaoNfse.CANCELADA;
        }
        Optional<DocumentoDistribuido> solicitacao = ultimoDoTipo(SOLICITACAO_ANALISE);
        if (solicitacao.isPresent()) {
            boolean indeferida = ultimoDoTipo(INDEFERIMENTO)
                    .filter(i -> i.nsu() > solicitacao.get().nsu())
                    .isPresent();
            if (!indeferida) {
                return SituacaoNfse.CANCELAMENTO_EM_ANALISE;
            }
        }
        return SituacaoNfse.VALIDA;
    }

    /**
     * {@code true} quando a nota não vale mais — cancelada pelo emitente, pelo município ou por
     * substituição.
     */
    public boolean cancelada() {
        return situacao().cancelada();
    }

    /** O evento que encerrou a validade da nota, se houver — inclusive o de substituição. */
    public Optional<DocumentoDistribuido> eventoDeCancelamento() {
        return eventos().stream()
                .filter(e -> e.tipoEvento() != null)
                .filter(e -> CANCELAMENTOS.contains(e.tipoEvento().toUpperCase())
                        || SUBSTITUICAO.equalsIgnoreCase(e.tipoEvento()))
                .findFirst();
    }

    private Optional<DocumentoDistribuido> eventoDoTipo(String tipo) {
        return eventos().stream().filter(e -> tipo.equalsIgnoreCase(e.tipoEvento())).findFirst();
    }

    private Optional<DocumentoDistribuido> ultimoDoTipo(String tipo) {
        return eventos().stream()
                .filter(e -> tipo.equalsIgnoreCase(e.tipoEvento()))
                .max(Comparator.comparingLong(DocumentoDistribuido::nsu));
    }
}
