package br.com.adelfo.nfse.nacional.client;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Conversão entre XML e a representação {@code GZip + Base64} exigida pelas APIs do Sistema
 * Nacional NFS-e.
 *
 * <p>As rotas trafegam JSON, mas o documento fiscal em si (DPS, pedido de registro de evento,
 * NFS-e, evento) segue em XML comprimido com GZip e codificado em Base64 dentro de um campo
 * string do JSON. Este é o único ponto da biblioteca que faz essa conversão.
 */
public final class GZipBase64 {

    private GZipBase64() {
    }

    /** Comprime o XML em GZip e devolve o resultado codificado em Base64. */
    public static String comprimir(String xml) {
        return comprimir(xml.getBytes(StandardCharsets.UTF_8));
    }

    /** Comprime os bytes em GZip e devolve o resultado codificado em Base64. */
    public static String comprimir(byte[] bytes) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
                gzip.write(bytes);
            }
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao comprimir XML em GZip+Base64", e);
        }
    }

    /** Decodifica o Base64, descomprime o GZip e devolve o XML como String UTF-8. */
    public static String descomprimir(String gzipBase64) {
        byte[] comprimido = Base64.getDecoder().decode(gzipBase64);
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(comprimido));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = gzip.read(buf)) != -1) out.write(buf, 0, n);
            return out.toString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao descomprimir XML de GZip+Base64", e);
        }
    }
}
