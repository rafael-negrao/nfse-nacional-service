package br.com.adelfo.nfse.nacional.service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propriedades de configuração do cliente NFS-e Nacional.
 * Prefixo: {@code nfse.nacional}
 *
 * <p>Exemplo em application.properties:
 * <pre>
 *   nfse.nacional.connection-timeout-ms=10000
 *   nfse.nacional.read-timeout-ms=30000
 * </pre>
 */
@ConfigurationProperties(prefix = "nfse.nacional")
public class NfseProperties {

    /** Timeout de conexão TCP em milissegundos (padrão: 10000). */
    private int connectionTimeoutMs = 10_000;

    /** Timeout de leitura/resposta em milissegundos (padrão: 30000). */
    private int readTimeoutMs = 30_000;

    public int getConnectionTimeoutMs() {
        return connectionTimeoutMs;
    }

    public void setConnectionTimeoutMs(int connectionTimeoutMs) {
        this.connectionTimeoutMs = connectionTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }
}
