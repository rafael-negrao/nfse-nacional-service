package br.com.adelfo.nfse.nacional.client;

/**
 * Falha de transporte HTTP contra as APIs do Sistema Nacional NFS-e — status >= 400.
 *
 * <p>Carrega o corpo bruto da resposta porque a Sefin Nacional devolve o detalhamento da rejeição
 * em JSON; a camada de serviço traduz esse corpo em {@code NfseException} de negócio.
 */
public class NfseHttpException extends RuntimeException {

    private final int status;
    private final String corpo;

    public NfseHttpException(int status, String corpo) {
        super("HTTP " + status + " ao chamar o Sistema Nacional NFS-e: " + corpo);
        this.status = status;
        this.corpo = corpo;
    }

    /** Código de status HTTP retornado. */
    public int getStatus() {
        return status;
    }

    /** Corpo bruto da resposta — normalmente JSON com o detalhamento da rejeição. */
    public String getCorpo() {
        return corpo;
    }
}
