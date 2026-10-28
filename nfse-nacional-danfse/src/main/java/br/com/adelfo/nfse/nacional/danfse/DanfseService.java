package br.com.adelfo.nfse.nacional.danfse;

import br.com.adelfo.nfse.nacional.danfse.internal.ConteudoDanfse;
import br.com.adelfo.nfse.nacional.danfse.internal.DesenhoDanfse;
import br.com.adelfo.nfse.nacional.danfse.internal.XmlNfse;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Gera o DANFSe — o PDF da NFS-e — localmente, conforme a NT 008/2026 v1.02.
 *
 * <p><b>Por que local:</b> a API nacional de geração ({@code adn.nfse.gov.br/danfse}) foi
 * suspensa em 03/08/2026, e a NT 008 passou a geração para o sistema emissor. Não depende de
 * rede nem de certificado: basta o XML da NFS-e, que a emissão e a consulta por chave devolvem.
 *
 * <pre>
 * DanfseService danfse = new DanfseService();
 * String xml = nfseService.consultarPorChave(req).xmlNfse();
 * SituacaoNfse situacao = nfseService.consultarSituacao(ambiente, chave);
 * byte[] pdf = danfse.gerarPdf(DanfseRequest.de(xml).comSituacao(situacao));
 * </pre>
 *
 * <p>Thread-safe: a logomarca é carregada uma vez, e cada geração usa objetos próprios.
 */
public class DanfseService {

    private static final String LOGO = "/danfse/logo-nfse-horizontal.png";

    private final byte[] logo;

    public DanfseService() {
        try (InputStream in = DanfseService.class.getResourceAsStream(LOGO)) {
            if (in == null) {
                throw new DanfseException("logomarca da NFS-e ausente do classpath: " + LOGO);
            }
            this.logo = in.readAllBytes();
        } catch (IOException e) {
            throw new DanfseException("falha ao carregar a logomarca da NFS-e", e);
        }
    }

    /** PDF de uma página A4. */
    public byte[] gerarPdf(DanfseRequest request) {
        ConteudoDanfse conteudo = new ConteudoDanfse(XmlNfse.ler(request.xmlNfse()));
        return new DesenhoDanfse().desenhar(conteudo, request.situacao(), request.canhoto(), logo);
    }

    /** Atalho para uma nota válida, com canhoto. */
    public byte[] gerarPdf(String xmlNfse) {
        return gerarPdf(DanfseRequest.de(xmlNfse));
    }

    public void gerarPdf(DanfseRequest request, Path destino) {
        try {
            Files.write(destino, gerarPdf(request));
        } catch (IOException e) {
            throw new UncheckedIOException("falha ao gravar o DANFSe em " + destino, e);
        }
    }
}
