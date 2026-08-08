package br.com.adelfo.nfse.nacional.service.exception;

/**
 * Rejeição de negócio devolvida pelo Sistema Nacional NFS-e.
 *
 * <p>Lançada nas operações de escrita (emitir, cancelar). Operações de consulta devolvem o objeto
 * de resposta para o chamador interpretar — mesma divisão adotada em {@code nfe-sefaz-sp}.
 *
 * <pre>
 * try {
 *     EmissaoResponse resp = nfseService.emitir(req);
 * } catch (NfseException e) {
 *     log.error("Rejeição [{}]: {}", e.getCodigo(), e.getMensagem());
 * }
 * </pre>
 */
public class NfseException extends RuntimeException {

    private final String codigo;
    private final String mensagem;
    private final String corpoResposta;

    public NfseException(String codigo, String mensagem, String corpoResposta) {
        super("NFS-e Nacional [" + codigo + "]: " + mensagem);
        this.codigo = codigo;
        this.mensagem = mensagem;
        this.corpoResposta = corpoResposta;
    }

    /** Código da rejeição informado pelo Sistema Nacional. */
    public String getCodigo() {
        return codigo;
    }

    /** Descrição textual da rejeição. */
    public String getMensagem() {
        return mensagem;
    }

    /** Corpo bruto da resposta, preservado para diagnóstico das rejeições não mapeadas. */
    public String getCorpoResposta() {
        return corpoResposta;
    }

    /**
     * {@code true} quando o Sistema Nacional recusou por excesso de requisições, e não por conteúdo
     * do documento. Vale reprocessar a mesma chamada depois de uma pausa — o ADN limita o ritmo
     * das rotas de distribuição e parametrização.
     */
    public boolean isLimiteDeRequisicoes() {
        return "429".equals(codigo);
    }

    /**
     * {@code true} quando o serviço está indisponível (5xx). Também não é rejeição do documento;
     * o {@code /danfse}, por exemplo, responde 503 por não ter backend no ar.
     */
    public boolean isServicoIndisponivel() {
        return codigo != null && codigo.length() == 3 && codigo.charAt(0) == '5';
    }
}
