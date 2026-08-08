package br.com.adelfo.nfse.nacional.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GZipBase64Test {

    @Test
    void comprimirEDescomprimir_preservaOXmlOriginal() {
        String xml = "<DPS versao=\"1.01\"><infDPS Id=\"DPS123\"><tpAmb>2</tpAmb></infDPS></DPS>";

        String comprimido = GZipBase64.comprimir(xml);

        assertFalse(comprimido.contains("<"), "resultado deve estar em Base64");
        assertEquals(xml, GZipBase64.descomprimir(comprimido));
    }

    @Test
    void comprimir_preservaAcentuacaoEmUtf8() {
        // Descrições de serviço e motivos de cancelamento carregam acentos; um round-trip que
        // troque o charset corromperia o XML já assinado e invalidaria a assinatura.
        String xml = "<xMotivo>Serviço não prestado — cancelamento a pedido</xMotivo>";

        assertEquals(xml, GZipBase64.descomprimir(GZipBase64.comprimir(xml)));
    }

    @Test
    void comprimir_reduzOTamanhoDeXmlRepetitivo() {
        String xml = "<item>servico</item>".repeat(200);

        assertTrue(GZipBase64.comprimir(xml).length() < xml.length());
    }
}
