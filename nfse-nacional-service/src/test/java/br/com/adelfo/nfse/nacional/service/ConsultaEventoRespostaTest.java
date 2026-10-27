package br.com.adelfo.nfse.nacional.service;

import br.com.adelfo.nfse.nacional.client.GZipBase64;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Leitura da resposta de {@code GET /nfse/{chave}/eventos/{tipo}/{nSeq}}, que diverge da Swagger.
 * Ver {@link NfseServiceImpl#xmlDoEventoConsultado(JsonNode)}.
 */
class ConsultaEventoRespostaTest {

    private static final String XML = "<evento versao=\"1.01\"><infEvento Id=\"EVT1\"/></evento>";

    private static JsonNode json(String texto) throws Exception {
        return new ObjectMapper().readTree(texto);
    }

    @Test
    void formaReal_eventosComArquivoXmlEmBase64Duplo() throws Exception {
        // Como a Sefin respondeu em produção restrita, 27/10/2026.
        String duplo = Base64.getEncoder().encodeToString(
                GZipBase64.comprimir(XML).getBytes(StandardCharsets.US_ASCII));

        JsonNode resposta = json("""
                {"tipoAmbiente":2,"eventos":[{"chaveAcesso":"1","tipoEvento":101101,
                 "numeroPedidoRegistroEvento":1,"arquivoXml":"%s"}]}""".formatted(duplo));

        assertEquals(XML, NfseServiceImpl.xmlDoEventoConsultado(resposta));
    }

    @Test
    void arquivoXmlComBase64Simples_tambemELido() throws Exception {
        JsonNode resposta = json("""
                {"eventos":[{"arquivoXml":"%s"}]}""".formatted(GZipBase64.comprimir(XML)));

        assertEquals(XML, NfseServiceImpl.xmlDoEventoConsultado(resposta));
    }

    @Test
    void formaDaSwagger_continuaAceita() throws Exception {
        JsonNode resposta = json("""
                {"eventoXmlGZipB64":"%s"}""".formatted(GZipBase64.comprimir(XML)));

        assertEquals(XML, NfseServiceImpl.xmlDoEventoConsultado(resposta));
    }

    @Test
    void semEvento_devolveNull() throws Exception {
        assertNull(NfseServiceImpl.xmlDoEventoConsultado(json("{\"eventos\":[]}")));
    }
}
