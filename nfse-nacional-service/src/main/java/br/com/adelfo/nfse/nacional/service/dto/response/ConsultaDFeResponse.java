package br.com.adelfo.nfse.nacional.service.dto.response;

import java.util.List;
import java.util.Optional;

/**
 * Lote de documentos devolvido pela distribuição do ADN.
 *
 * <p>Padrão de varredura:
 * <pre>
 * long nsu = 0;
 * ConsultaDFeResponse lote;
 * do {
 *     lote = nfseService.consultarDFe(ConsultaDFeRequest.aPartirDe(ambiente, nsu));
 *     processar(lote.documentos());
 *     nsu = lote.ultimoNsu().orElse(nsu);
 * } while (lote.temMaisDocumentos());
 * </pre>
 *
 * <p>Guarde o {@code ultimoNsu} entre execuções: é ele que torna a varredura incremental. E espace
 * as chamadas — o ADN aplica rate limit e passa a devolver 429.
 *
 * @param status     situação do processamento; controla o fim da varredura
 * @param documentos documentos do lote, em ordem de NSU
 * @param alertas    mensagens informativas do ADN
 * @param erros      mensagens de erro do ADN, preenchidas quando o status é {@code REJEICAO}
 */
public record ConsultaDFeResponse(StatusDistribuicao status,
                                  List<DocumentoDistribuido> documentos,
                                  List<String> alertas,
                                  List<String> erros) {

    public ConsultaDFeResponse {
        documentos = documentos == null ? List.of() : List.copyOf(documentos);
        alertas = alertas == null ? List.of() : List.copyOf(alertas);
        erros = erros == null ? List.of() : List.copyOf(erros);
    }

    /** Maior NSU do lote — o ponto de partida da próxima consulta. Vazio se o lote veio vazio. */
    public Optional<Long> ultimoNsu() {
        return documentos.stream().mapToLong(DocumentoDistribuido::nsu).max().stream().boxed().findFirst();
    }

    /** {@code true} enquanto vale a pena pedir o próximo lote. */
    public boolean temMaisDocumentos() {
        return status == StatusDistribuicao.DOCUMENTOS_LOCALIZADOS && !documentos.isEmpty();
    }
}
