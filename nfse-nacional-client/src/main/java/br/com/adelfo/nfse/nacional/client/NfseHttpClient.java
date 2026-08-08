package br.com.adelfo.nfse.nacional.client;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.time.Duration;

/**
 * Cliente HTTP com mTLS para as APIs REST do Sistema Nacional NFS-e.
 *
 * <p>Diferentemente do NF-e SEFAZ (SOAP, stubs gerados por {@code wsimport}), a NFS-e Nacional é
 * REST/JSON — não há WSDL. Este cliente é o único ponto que conhece HTTP: monta o
 * {@link SSLContext} a partir do {@link CertificadoDigital} e expõe GET/POST devolvendo o corpo
 * como String.
 *
 * <p>O {@link HttpClient} do JDK é thread-safe e caro de construir; uma instância é criada no
 * construtor e reaproveitada em todas as chamadas.
 */
public class NfseHttpClient {

    private final HttpClient httpClient;
    private final Duration readTimeout;

    public NfseHttpClient(CertificadoDigital certificado, Duration connectTimeout, Duration readTimeout) {
        this.readTimeout = readTimeout;
        this.httpClient = HttpClient.newBuilder()
                .sslContext(montarSslContext(certificado))
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                // A Sefin Nacional recusa HTTP/2: o servidor derruba o stream com
                // "RST_STREAM: Use HTTP/1.1 for request", que o HttpClient repassa como um
                // IOException genérico, sem nenhuma pista da causa. O padrão do JDK é negociar
                // HTTP/2, então a versão precisa ser fixada aqui.
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    /**
     * SSLContext com o PKCS#12 do emitente como keystore (autenticação mútua) e a cadeia
     * ICP-Brasil como truststore.
     */
    private static SSLContext montarSslContext(CertificadoDigital certificado) {
        try {
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(certificado.toKeyStore(), certificado.getSenha());

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(certificado.toTrustStore());

            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());
            return ctx;
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao montar SSLContext mTLS a partir do certificado digital", e);
        }
    }

    /** GET devolvendo o corpo da resposta. Lança {@link NfseHttpException} para status >= 400. */
    public String get(String url) {
        return enviar(requestBase(url).GET().build());
    }

    /** POST de corpo JSON devolvendo o corpo da resposta. Lança {@link NfseHttpException} para status >= 400. */
    public String post(String url, String json) {
        return enviar(requestBase(url)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build());
    }

    /**
     * HEAD devolvendo apenas o status. Usado pela rota {@code HEAD /dps/{id}}, que informa se a
     * NFS-e foi gerada sem revelar a chave de acesso.
     */
    public int head(String url) {
        try {
            HttpRequest req = requestBase(url)
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();
            return httpClient.send(req, HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Chamada HEAD interrompida: " + url, e);
        } catch (Exception e) {
            throw new IllegalStateException("Falha na chamada HEAD: " + url, e);
        }
    }

    private HttpRequest.Builder requestBase(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(readTimeout)
                .header("Accept", "application/json");
    }

    private String enviar(HttpRequest request) {
        try {
            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 400) {
                throw new NfseHttpException(resp.statusCode(), resp.body());
            }
            return resp.body();
        } catch (NfseHttpException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Chamada interrompida: " + request.uri(), e);
        } catch (Exception e) {
            throw new IllegalStateException("Falha na chamada HTTP: " + request.uri(), e);
        }
    }
}
