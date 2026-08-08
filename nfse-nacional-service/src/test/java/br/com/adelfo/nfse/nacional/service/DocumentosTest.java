package br.com.adelfo.nfse.nacional.service;

import br.com.adelfo.nfse.nacional.client.TipoAmbiente;
import br.com.adelfo.nfse.nacional.service.dto.request.CancelamentoRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Normalização dos campos numéricos. O XSD os tipa como dígitos puros, mas quem consome a
 * biblioteca costuma tê-los formatados.
 */
class DocumentosTest {

    @Test
    void pontuacaoUsual_eRemovida() {
        assertEquals("06169966000133", Documentos.cnpj("06.169.966/0001-33", "x"));
        assertEquals("12345678909", Documentos.cpf("123.456.789-09", "x"));
        assertEquals("01310100", Documentos.cep("01310-100", "x"));
        assertEquals("1130000000", Documentos.telefone("(11) 3000-0000", "x"));
        assertEquals("3550308", Documentos.municipio("3550308", "x"));
    }

    @Test
    void campoOpcionalVazio_continuaNulo() {
        assertNull(Documentos.cnpj(null, "x"));
        assertNull(Documentos.cnpj("", "x"));
        assertNull(Documentos.cnpj("   ", "x"));
        assertNull(Documentos.telefone("--", "x"), "só pontuação equivale a não informado");
    }

    @Test
    void tamanhoErrado_eRecusadoComOCampoNaMensagem() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> Documentos.cnpj("06.169.966/0001-3", "prestadorCnpj"));

        assertTrue(e.getMessage().contains("prestadorCnpj"), e.getMessage());
        assertTrue(e.getMessage().contains("13 dígitos"), e.getMessage());
    }

    @Test
    void cpfOuCnpj_aceitaOsDoisTamanhosERecusaOResto() {
        assertEquals("06169966000133", Documentos.cpfOuCnpj("06.169.966/0001-33", "autor"));
        assertEquals("12345678909", Documentos.cpfOuCnpj("123.456.789-09", "autor"));
        assertThrows(IllegalArgumentException.class, () -> Documentos.cpfOuCnpj("123456", "autor"));
    }

    // ---------------------------------------------------------------------------------------
    // Dígito verificador
    // ---------------------------------------------------------------------------------------

    @Test
    void cpfComDvErrado_eRecusado() {
        // E0913 manda a Sefin conferir o DV; conferir aqui evita a ida ao servidor.
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> Documentos.cpf("123.456.789-01", "tomadorCpf"));

        assertTrue(e.getMessage().contains("tomadorCpf"), e.getMessage());
        assertTrue(e.getMessage().contains("informado 01"), e.getMessage());
        assertTrue(e.getMessage().contains("calculado 09"), e.getMessage());
    }

    @Test
    void cnpjComDvErrado_eRecusado() {
        // E0911, o par do E0913 para pessoa jurídica.
        assertThrows(IllegalArgumentException.class,
                () -> Documentos.cnpj("06.169.966/0001-34", "prestadorCnpj"));
    }

    @Test
    void dvDeDocumentosReais_confere() {
        // CNPJ da Adelfo e da COMAHO, extraídos dos certificados usados nos testes de integração.
        assertEquals("06169966000133", Documentos.cnpj("06169966000133", "x"));
        assertEquals("54559893000139", Documentos.cnpj("54559893000139", "x"));

        assertEquals("33", Documentos.digitoVerificadorCnpj("061699660001"));
        assertEquals("09", Documentos.digitoVerificadorCpf("123456789"));
    }

    @Test
    void dvDoCnpj_usaOAlgoritmoAlfanumerico() {
        // IN RFB 2.229/2024: cada posição vale ASCII − 48, então 'A' = 17, 'B' = 18, 'C' = 19.
        // Para um CNPJ numérico o resultado é o mesmo do cálculo antigo — é o que garante que a
        // mesma rotina sirva aos dois formatos.
        assertEquals("35", Documentos.digitoVerificadorCnpj("12ABC34501DE"));
        assertEquals("95", Documentos.digitoVerificadorCnpj("123456780001"));
    }

    @Test
    void cnpjComLetras_eRecusadoEnquantoOLeiauteForNumerico() {
        // O XSD v1.01 (fev/2026) ainda tipa TSCNPJ como [0-9]{14}, embora o CNPJ alfanumérico
        // tenha entrado em vigor em julho/2026. Aceitar letras produziria XML inválido no schema.
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> Documentos.cnpj("12ABC34501DE35", "prestadorCnpj"));

        assertTrue(e.getMessage().contains("alfanumérico"), e.getMessage());
        assertTrue(e.getMessage().contains("TSCNPJ"), e.getMessage());
    }

    @Test
    void chaveDeAcessoComDvErrado_eRecusada() {
        // E0042 e E0907 exigem a verificação do DV nas chaves referenciadas. Aqui a chave real com
        // o último dígito trocado — não é de dígitos repetidos, então quem barra é o DV.
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> Documentos.chaveAcesso(
                        "35503081206169966000133000000000023726079719343784", "chaveAcesso"));

        assertTrue(e.getMessage().contains("informado 4"), e.getMessage());
        assertTrue(e.getMessage().contains("calculado 5"), e.getMessage());
    }

    @Test
    void valoresComTodosOsDigitosIguais_saoRecusados() {
        // O módulo 11 os aceita por construção: 00000000000 tem DV 00, e a chave só de zeros
        // também fecha. São valores de preenchimento, não documentos.
        IllegalArgumentException cpf = assertThrows(IllegalArgumentException.class,
                () -> Documentos.cpf("00000000000", "x"));
        IllegalArgumentException cnpj = assertThrows(IllegalArgumentException.class,
                () -> Documentos.cnpj("00000000000000", "x"));
        IllegalArgumentException chave = assertThrows(IllegalArgumentException.class,
                () -> Documentos.chaveAcesso("0".repeat(50), "x"));

        for (IllegalArgumentException e : List.of(cpf, cnpj, chave)) {
            assertTrue(e.getMessage().contains("dígitos iguais"), e.getMessage());
        }
        assertThrows(IllegalArgumentException.class, () -> Documentos.cpf("11111111111", "x"));
    }

    @Test
    void dvDaChaveReal_confere() {
        // Chave da NFS-e 237 da Adelfo, consultada em produção — o DV informado é 5.
        String real = "35503081206169966000133000000000023726079719343785";

        assertEquals(real, Documentos.chaveAcesso(real, "x"));
        assertEquals(5, Documentos.digitoVerificadorChaveAcesso(real.substring(0, 49)));
    }

    @Test
    void chaveInvalida_eBarradaNasReferenciasDoLeiaute() {
        // Os pontos em que o leiaute referencia outra nota: substituição (E0042) e IBS/CBS (E0907).
        assertThrows(IllegalArgumentException.class, () -> DpsBuilder.novo()
                .substitui("1".repeat(50), "99", "Correcao de valor"));

        assertThrows(IllegalArgumentException.class,
                () -> IbsCbsBuilder.novo().referenciaNfse("1".repeat(50)));
    }

    // ---------------------------------------------------------------------------------------
    // Integração com os builders e DTOs
    // ---------------------------------------------------------------------------------------

    @Test
    void dpsAceitaTodosOsNumerosFormatados() {
        String xml = DpsBuilder.novo()
                .ambiente(TipoAmbiente.PRODUCAO_RESTRITA)
                .municipioEmissor("3550308")
                .identificacao("1", "237", LocalDate.of(2026, 8, 1))
                .prestadorCnpj("06.169.966/0001-33")
                .prestadorContato("(11) 3000-0000", "fiscal@adelfo.com.br")
                .prestadorEndereco(DpsBuilder.Endereco.nacional(
                        "3550308", "01310-100", "Avenida Paulista", "1000", null, "Bela Vista"))
                .naoOptanteSimplesNacional().semRegimeEspecial()
                .tomadorCnpj("98.765.432/0001-98", "Cliente Exemplo")
                .servicoPrestadoNoMunicipio("3550308")
                .servico("010401", "Elaboracao de programa de computador")
                .valorServico(new BigDecimal("1000.00"))
                .issqnTributavel().issqnNaoRetido().semTotalTributos()
                .build();

        // Antes desta normalização, o CNPJ do tomador passava pontuado para o XML — em silêncio.
        assertTrue(xml.contains("<CNPJ>06169966000133</CNPJ>"));
        assertTrue(xml.contains("<CNPJ>98765432000198</CNPJ>"));
        assertTrue(xml.contains("<CEP>01310100</CEP>"));
        assertTrue(xml.contains("<fone>1130000000</fone>"));
        assertTrue(xml.contains("Id=\"DPS355030820616996600013300001000000000000237\""));
    }

    @Test
    void chaveDeAcessoFormatada_eNormalizadaNosDtos() {
        String chave = "1".repeat(49) + "9";
        String comEspacos = chave.substring(0, 10) + " " + chave.substring(10, 20) + " "
                + chave.substring(20);

        CancelamentoRequest req = CancelamentoRequest.de(
                TipoAmbiente.PRODUCAO, comEspacos, "1", "Erro na emissao da nota");

        assertEquals(chave, req.chaveAcesso());
    }

    @Test
    void autorFormatado_eNormalizado() {
        CancelamentoRequest req = CancelamentoRequest.comAutor(
                TipoAmbiente.PRODUCAO, "1".repeat(49) + "9", "1", "Erro na emissao da nota",
                "06.169.966/0001-33");

        assertEquals("06169966000133", req.autor());
    }

    @Test
    void inscricaoMunicipalENif_naoSaoLimpados() {
        // TSInscMun e TSNIF admitem letras; limpá-los corromperia o dado.
        String xml = DpsBuilder.novo()
                .ambiente(TipoAmbiente.PRODUCAO_RESTRITA)
                .municipioEmissor("3550308")
                .identificacao("1", "1", LocalDate.of(2026, 8, 1))
                .prestadorCnpj("06169966000133")
                .prestadorInscricaoMunicipal("IM-33029610")
                .naoOptanteSimplesNacional().semRegimeEspecial()
                .tomadorNif("US-123456789", "Foreign Client")
                .servicoPrestadoNoMunicipio("3550308")
                .servico("010401", "Servico")
                .valorServico(new BigDecimal("100.00"))
                .issqnTributavel().issqnNaoRetido().semTotalTributos()
                .build();

        assertTrue(xml.contains("<IM>IM-33029610</IM>"));
        assertTrue(xml.contains("<NIF>US-123456789</NIF>"));
    }
}
