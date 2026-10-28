package br.com.adelfo.nfse.nacional.danfse;

/** Falha ao gerar o DANFSe — XML ilegível ou erro na montagem do PDF. */
public class DanfseException extends RuntimeException {

    public DanfseException(String mensagem) {
        super(mensagem);
    }

    public DanfseException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
