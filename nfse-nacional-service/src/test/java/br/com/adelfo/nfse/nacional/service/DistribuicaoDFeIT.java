package br.com.adelfo.nfse.nacional.service;

import br.com.adelfo.nfse.nacional.client.CertificadoDigital;
import br.com.adelfo.nfse.nacional.client.NfseHttpClient;
import br.com.adelfo.nfse.nacional.client.TipoAmbiente;
import br.com.adelfo.nfse.nacional.service.dto.request.ConsultaDFeRequest;
import br.com.adelfo.nfse.nacional.service.dto.response.ConsultaDFeResponse;
import br.com.adelfo.nfse.nacional.service.dto.response.DocumentoDistribuido;
import br.com.adelfo.nfse.nacional.service.dto.response.StatusDistribuicao;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Distribuição de DF-e por NSU contra o ambiente real. Só leitura.
 *
 * <p>É a única forma de descobrir notas sem já conhecer a chave — não há consulta por período.
 *
 * <p>A varredura é limitada por {@link #MAX_LOTES} e espaçada por {@link #PAUSA}: o ADN aplica rate
 * limit e passa a responder 429 quando as chamadas se acumulam.
 *
 * <pre>
 * mvn test -pl nfse-nacional-service -DfailIfNoTests=false \
 *     -Dsurefire.failIfNoSpecifiedTests=false -Dtest=DistribuicaoDFeIT \
 *     -Dnfse.ambiente=producao -Dnfse.cert.p12=/caminho/cert.pfx -Dnfse.cert.senha=senha
 * </pre>
 */
class DistribuicaoDFeIT {

    private static final int MAX_LOTES = 3;
    private static final Duration PAUSA = Duration.ofSeconds(8);

    private static TipoAmbiente ambiente;
    private static NfseService service;

    @BeforeAll
    static void setup() throws Exception {
        ambiente = CertificadoDeTeste.ambiente();
        CertificadoDigital certificado = CertificadoDeTeste.carregar(ambiente);
        NfseHttpClient httpClient = new NfseHttpClient(
                certificado, Duration.ofSeconds(15), Duration.ofSeconds(60));
        service = new NfseServiceImpl(certificado, httpClient);
    }

    @Test
    void primeiroLote_trazDocumentosENumeraOsNsu() {
        ConsultaDFeResponse lote = service.consultarDFe(ConsultaDFeRequest.doInicio(ambiente));

        System.out.println("=== distribuição a partir do NSU 0 ===");
        System.out.println("status     : " + lote.status());
        System.out.println("documentos : " + lote.documentos().size());
        System.out.println("último NSU : " + lote.ultimoNsu().orElse(null));
        System.out.println("alertas    : " + lote.alertas());
        System.out.println("erros      : " + lote.erros());

        assertNotNull(lote.status());
        if (lote.documentos().isEmpty()) {
            System.out.println("(certificado sem documentos distribuídos — nada mais a verificar)");
            return;
        }

        assertEquals(StatusDistribuicao.DOCUMENTOS_LOCALIZADOS, lote.status());
        assertTrue(lote.ultimoNsu().isPresent());

        DocumentoDistribuido primeiro = lote.documentos().get(0);
        System.out.println("1º documento: NSU=" + primeiro.nsu()
                + " tipo=" + primeiro.tipoDocumento()
                + " chave=" + primeiro.chaveAcesso());

        // O ArquivoXml vem em GZip+Base64; se a descompressão falhasse, viria nulo ou lixo.
        assertNotNull(primeiro.xml(), "o XML do documento deve vir descomprimido");
        assertTrue(primeiro.xml().contains("<"), "o conteúdo descomprimido deve ser XML");
    }

    @Test
    void varreduraIncremental_avancaOsNsuSemRepetir() throws Exception {
        List<DocumentoDistribuido> todos = new ArrayList<>();
        long nsu = 0;
        ConsultaDFeResponse lote = null;

        // Pausa antes da primeira chamada também: o teste anterior desta classe já consumiu uma
        // requisição, e o limite do ADN é apertado o bastante para o par seguido estourar 429.
        Thread.sleep(PAUSA.toMillis());

        for (int i = 0; i < MAX_LOTES; i++) {
            if (i > 0) {
                Thread.sleep(PAUSA.toMillis());
            }
            lote = service.consultarDFe(ConsultaDFeRequest.aPartirDe(ambiente, nsu));
            System.out.println("lote " + (i + 1) + ": " + lote.status()
                    + ", " + lote.documentos().size() + " docs, a partir do NSU " + nsu);
            todos.addAll(lote.documentos());
            if (!lote.temMaisDocumentos()) {
                break;
            }
            nsu = lote.ultimoNsu().orElseThrow();
        }

        if (todos.isEmpty()) {
            System.out.println("(certificado sem documentos distribuídos)");
            return;
        }

        // O contrato da varredura: cada lote começa depois do último NSU do anterior. Se houvesse
        // sobreposição, o chamador reprocessaria documentos a cada execução.
        List<Long> nsus = todos.stream().map(DocumentoDistribuido::nsu).toList();
        assertEquals(nsus.stream().distinct().count(), nsus.size(), "NSU repetido entre lotes: " + nsus);
        assertEquals(nsus.stream().sorted().toList(), nsus, "os NSU devem vir em ordem crescente");

        Map<String, Long> porTipo = new TreeMap<>();
        for (DocumentoDistribuido d : todos) {
            porTipo.merge(String.valueOf(d.tipoDocumento()), 1L, Long::sum);
        }
        System.out.println("=== " + todos.size() + " documentos: " + porTipo);
        System.out.println("faixa de NSU: " + nsus.get(0) + " a " + nsus.get(nsus.size() - 1));
    }
}
