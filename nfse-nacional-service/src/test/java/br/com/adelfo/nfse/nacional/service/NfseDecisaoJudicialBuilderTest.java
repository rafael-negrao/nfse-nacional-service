package br.com.adelfo.nfse.nacional.service;

import br.com.adelfo.nfse.nacional.client.TipoAmbiente;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Valida a NFS-e do fluxo de decisão administrativa/judicial contra o XSD oficial
 * {@code NFSe_v1.01.xsd}.
 */
class NfseDecisaoJudicialBuilderTest {

    private static final String CNPJ = "12345678000195";
    private static final String MUNICIPIO_SP = "3550308";

    private static final NfseDecisaoJudicialBuilder.EnderecoEmitente ENDERECO =
            new NfseDecisaoJudicialBuilder.EnderecoEmitente(
                    "Avenida Paulista", "1000", "Conjunto 51", "Bela Vista",
                    MUNICIPIO_SP, "SP", "01310100");

    /**
     * Violações de schema <b>esperadas</b> neste fluxo, que não indicam defeito do builder:
     *
     * <ul>
     *   <li>{@code TSSerieDPS} — o pattern quebrado do XSD oficial, descrito em
     *       {@link DpsBuilderTest};</li>
     *   <li>{@code TSNDFSe} — o manual do bypass manda {@code nDFSe="0"}, e o pattern do XSD não
     *       admite zero. Manual e schema se contradizem; prevalece o manual;</li>
     *   <li>{@code Signature} ausente — ao contrário da DPS, o XSD da NFS-e exige a assinatura.
     *       O builder entrega o documento <b>não assinado</b>, e quem assina é a biblioteca no
     *       envio; validar o documento assinado exigiria certificado no teste unitário.</li>
     * </ul>
     */
    private static boolean ehViolacaoEsperada(String mensagem) {
        return mensagem.contains("TSSerieDPS") || mensagem.contains("'serie'")
                || mensagem.contains("TSNDFSe") || mensagem.contains("'nDFSe'")
                || mensagem.contains("Signature");
    }

    private static void validar(String xml) throws Exception {
        List<String> erros = ValidacaoXsd.erros("NFSe_v1.01.xsd", xml).stream()
                .filter(msg -> !ehViolacaoEsperada(msg))
                .toList();
        assertEquals(List.of(), erros, "XML deve ser válido no XSD oficial");
    }

    @Test
    void asUnicasViolacoesDeSchema_saoAsEsperadas() throws Exception {
        // Fixa exatamente quais violações o documento não assinado carrega. Se aparecer outra, o
        // filtro acima deixa de mascarar e este teste falha.
        List<String> todas = ValidacaoXsd.erros("NFSe_v1.01.xsd", completa().build());

        assertTrue(todas.stream().allMatch(NfseDecisaoJudicialBuilderTest::ehViolacaoEsperada),
                "violação inesperada: " + todas);
        assertTrue(todas.stream().anyMatch(m -> m.contains("Signature")),
                "o documento entregue é não assinado, então a assinatura deve faltar");
    }

    private static DpsBuilder dps() {
        return DpsBuilder.novo()
                .ambiente(TipoAmbiente.PRODUCAO_RESTRITA)
                .municipioEmissor(MUNICIPIO_SP)
                .identificacao("1", "240", LocalDate.of(2026, 8, 1))
                .dataHoraEmissao(OffsetDateTime.of(2026, 8, 1, 10, 0, 0, 0, ZoneOffset.ofHours(-3)))
                .emitidaPeloPrestador()
                .prestadorCnpj(CNPJ)
                .naoOptanteSimplesNacional()
                .semRegimeEspecial()
                .servicoPrestadoNoMunicipio(MUNICIPIO_SP)
                .servico("010401", "Elaboracao de programa de computador")
                .valorServico(new BigDecimal("1000.00"))
                .issqnTributavel()
                .issqnNaoRetido()
                .semTotalTributos();
    }

    private static NfseDecisaoJudicialBuilder completa() {
        return NfseDecisaoJudicialBuilder.sobre(dps())
                .numeroNfse("240")
                .codigoNumerico("123456789")
                .localEmissao("São Paulo")
                .localPrestacao("São Paulo")
                .incidencia(MUNICIPIO_SP, "São Paulo")
                .descricaoTributacaoNacional("Elaboracao de programa de computador (software)")
                .emitente("ADELFO SERVICOS ADMINISTRATIVOS", "33029610", ENDERECO)
                .aliquota(new BigDecimal("2.00"))
                .valores(new BigDecimal("1000.00"), new BigDecimal("20.00"), new BigDecimal("980.00"));
    }

    @Test
    void nfseCompleta_eValidaNoSchema() throws Exception {
        validar(completa().build());
    }

    @Test
    void descricaoDaTributacaoNacional_saiDaTabelaQuandoNaoInformada() {
        // No fluxo regular a plataforma gera o xTribNac; aqui é do contribuinte. Como o texto é o
        // do anexo oficial, não faz sentido exigir que seja redigitado.
        String xml = NfseDecisaoJudicialBuilder.sobre(dps())
                .numeroNfse("240").codigoNumerico("123456789")
                .localEmissao("São Paulo").localPrestacao("São Paulo")
                .incidencia(MUNICIPIO_SP, "São Paulo")
                .emitente("ADELFO SERVICOS ADMINISTRATIVOS", "33029610", ENDERECO)
                .aliquota(new BigDecimal("2.00"))
                .valores(new BigDecimal("1000.00"), new BigDecimal("20.00"), new BigDecimal("980.00"))
                .build();

        String esperado = ListaServicoNacional.descricao("010401").orElseThrow();
        assertTrue(xml.contains("<xTribNac>" + esperado + "</xTribNac>"), esperado);
    }

    @Test
    void camposFixosDoFluxo_saoAplicadosPeloBuilder() {
        String xml = completa().build();

        // O manual determina estes valores para o bypass; deixá-los a cargo do chamador só criaria
        // oportunidade de errar.
        assertTrue(xml.contains("<cStat>102</cStat>"), "cStat=102 marca a nota como de decisão");
        assertTrue(xml.contains("<ambGer>2</ambGer>"), "ambGer=2 = Sefin nacional");
        assertTrue(xml.contains("<tpEmis>1</tpEmis>"), "tpEmis=1 = emissão direta");
        assertTrue(xml.contains("<nDFSe>0</nDFSe>"), "nDFSe=0 = sem DF-e municipal");
    }

    @Test
    void dhProc_eIgualADhEmi() {
        // Regra do manual: a nota não passa por processamento da plataforma, então não há um
        // instante de processamento distinto do de emissão.
        String xml = completa().build();

        String dhProc = xml.replaceAll("(?s).*<dhProc>(.*?)</dhProc>.*", "$1");
        String dhEmi = xml.replaceAll("(?s).*<dhEmi>(.*?)</dhEmi>.*", "$1");
        assertEquals(dhEmi, dhProc);
    }

    @Test
    void id_tem53PosicoesEDigitoVerificadorValido() {
        String xml = completa().build();
        String id = xml.replaceAll("(?s).*<infNFSe Id=\"([^\"]+)\".*", "$1");

        assertEquals(53, id.length(), "Id: NFS + 50 dígitos");
        assertTrue(id.matches("NFS[0-9]{50}"), "Id fora do formato: " + id);

        // NFS + cMun(7) + ambGer(1) + tipoInscr(1) + inscrFederal(14) + nNFSe(13) + AAMM(4) + cNum(9) + DV(1)
        assertEquals("NFS" + MUNICIPIO_SP + "2" + "2" + CNPJ + "0000000000240" + "2608" + "123456789",
                id.substring(0, 52));

        String semDv = id.substring(3, 52);
        assertEquals(id.charAt(52) - '0', NfseDecisaoJudicialBuilder.digitoVerificador(semDv));
    }

    @Test
    void chaveAcesso_eOIdSemOLiteralNFS() {
        NfseDecisaoJudicialBuilder builder = completa();
        String xml = builder.build();
        String id = xml.replaceAll("(?s).*<infNFSe Id=\"([^\"]+)\".*", "$1");

        assertEquals(id.substring(3), builder.chaveAcesso());
        assertEquals(50, builder.chaveAcesso().length());
    }

    @Test
    void digitoVerificador_seguemModulo11() {
        // Conferido contra a chave real de produção 3550308120616996600013300000000002372607971934378|5
        assertEquals(5, NfseDecisaoJudicialBuilder.digitoVerificador(
                "3550308120616996600013300000000002372607971934378"));
    }

    @Test
    void aDpsFicaEmbutidaNaNfse() {
        String xml = completa().build();

        assertTrue(xml.contains("<DPS versao=\"1.01\">"), "a NFS-e carrega a DPS dentro dela");
        assertTrue(xml.contains("<infDPS Id=\"DPS"), "e a DPS mantém o próprio Id");
    }

    @Test
    void incidenciaSemAliquota_eRecusada() {
        // Neste fluxo a plataforma não determina alíquota; havendo incidência ela é obrigatória.
        assertThrows(IllegalStateException.class, () -> NfseDecisaoJudicialBuilder.sobre(dps())
                .numeroNfse("240").codigoNumerico("123456789")
                .localEmissao("São Paulo").localPrestacao("São Paulo")
                .incidencia(MUNICIPIO_SP, "São Paulo")
                .descricaoTributacaoNacional("Elaboracao de programa de computador")
                .emitente("ADELFO", "33029610", ENDERECO)
                .valores(new BigDecimal("1000.00"), BigDecimal.ZERO, new BigDecimal("1000.00"))
                .build());
    }

    @Test
    void codigoNumericoForaDoFormato_eRecusado() {
        assertThrows(IllegalStateException.class,
                () -> completa().codigoNumerico("123").build());
    }

    @Test
    void ufInvalida_eRecusadaComMensagemPropria() {
        var enderecoRuim = new NfseDecisaoJudicialBuilder.EnderecoEmitente(
                "Rua", "1", null, "Centro", MUNICIPIO_SP, "XX", "01310100");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> completa().emitente("ADELFO", "33029610", enderecoRuim).build());
        assertTrue(e.getMessage().contains("UF inválida"), e.getMessage());
    }
}
