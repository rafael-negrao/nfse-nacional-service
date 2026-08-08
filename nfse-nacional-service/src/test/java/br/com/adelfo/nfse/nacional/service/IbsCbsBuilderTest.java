package br.com.adelfo.nfse.nacional.service;

import br.com.adelfo.nfse.nacional.client.TipoAmbiente;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Valida o grupo IBS/CBS dentro da DPS contra o XSD oficial.
 */
class IbsCbsBuilderTest {

    private static final String MUNICIPIO_SP = "3550308";
    private static final String CHAVE = "1".repeat(49) + "9";

    private static void validar(String xml) throws Exception {
        List<String> erros = ValidacaoXsd.erros("DPS_v1.01.xsd", xml).stream()
                // Mesmo defeito de TSSerieDPS descrito em DpsBuilderTest.
                .filter(m -> !m.contains("TSSerieDPS") && !m.contains("'serie'"))
                .toList();
        assertEquals(List.of(), erros, "XML deve ser válido no XSD oficial");
    }

    private static DpsBuilder dps() {
        return DpsBuilder.novo()
                .ambiente(TipoAmbiente.PRODUCAO_RESTRITA)
                .municipioEmissor(MUNICIPIO_SP)
                .identificacao("1", "123", LocalDate.of(2026, 8, 1))
                .prestadorCnpj("12345678000195")
                .naoOptanteSimplesNacional()
                .semRegimeEspecial()
                .servicoPrestadoNoMunicipio(MUNICIPIO_SP)
                .servico("010401", "Elaboracao de programa de computador")
                .valorServico(new BigDecimal("1000.00"))
                .issqnTributavel()
                .issqnNaoRetido()
                .semTotalTributos();
    }

    private static IbsCbsBuilder minimo() {
        return IbsCbsBuilder.novo()
                .nfseRegular()
                .codigoIndicadorOperacao("020201")
                .destinatarioEhOProprioTomador()
                .tributacao("000", "000001");
    }

    @Test
    void grupoMinimo_eValidoNoSchema() throws Exception {
        String xml = dps().ibsCbs(minimo()).build();

        validar(xml);
        assertTrue(xml.contains("<IBSCBS>"));
        assertTrue(xml.contains("<finNFSe>0</finNFSe>"));
        assertTrue(xml.contains("<cIndOp>020201</cIndOp>"));
        assertTrue(xml.contains("<CST>000</CST>"));
        assertTrue(xml.contains("<cClassTrib>000001</cClassTrib>"));
    }

    @Test
    void grupoCompleto_eValidoNoSchema() throws Exception {
        String xml = dps().ibsCbs(IbsCbsBuilder.novo()
                        .nfseRegular()
                        .naoEhUsoOuConsumoPessoal()
                        .codigoIndicadorOperacao("020201")
                        .tipoOperacao("2")
                        .referenciaNfse(CHAVE)
                        .enteGovernamental("4")
                        .destinatarioDistintoDoTomador(
                                IbsCbsBuilder.Destinatario.porCnpj("98765432000198", "Destinatario Ltda"))
                        .tributacao("000", "000001")
                        .creditoPresumido("00")
                        .tributacaoRegular("000", "000002"))
                .build();

        validar(xml);
        assertTrue(xml.contains("<indDest>1</indDest>"));
        assertTrue(xml.contains("<refNFSe>" + CHAVE + "</refNFSe>"));
        assertTrue(xml.contains("<CSTReg>000</CSTReg>"));
    }

    @Test
    void semGrupo_aDpsSegueValidaEOElementoNaoAparece() throws Exception {
        String xml = dps().build();

        validar(xml);
        assertFalse(xml.contains("IBSCBS"), "o grupo é opcional no leiaute");
    }

    @Test
    void destinatarioSoApareceQuandoIndDestE1() {
        // E0910: o grupo do destinatário só pode ser informado quando indDest = 1.
        String xml = dps().ibsCbs(minimo()).build();

        assertTrue(xml.contains("<indDest>0</indDest>"));
        assertFalse(xml.contains("<dest>"), "com indDest=0 não se identifica destinatário");
    }

    @Test
    void tipoOperacao2ou3_exigemNfseReferenciada() {
        // E0905: fornecimento e pagamento em documentos distintos exigem a referência.
        for (String tpOper : List.of("2", "3")) {
            IllegalStateException e = assertThrows(IllegalStateException.class,
                    () -> dps().ibsCbs(minimo().tipoOperacao(tpOper)).build());
            assertTrue(e.getMessage().contains("E0905"), e.getMessage());
        }
    }

    @Test
    void tipoOperacao1_naoExigeReferencia() throws Exception {
        validar(dps().ibsCbs(minimo().tipoOperacao("1")).build());
    }

    @Test
    void camposObrigatoriosDoGrupo_saoValidadosComMensagemQueApontaOMetodo() {
        IllegalStateException semIndOp = assertThrows(IllegalStateException.class,
                () -> dps().ibsCbs(IbsCbsBuilder.novo()
                        .destinatarioEhOProprioTomador().tributacao("000", "000001")).build());
        assertTrue(semIndOp.getMessage().contains("codigoIndicadorOperacao"), semIndOp.getMessage());

        IllegalStateException semDest = assertThrows(IllegalStateException.class,
                () -> dps().ibsCbs(IbsCbsBuilder.novo()
                        .codigoIndicadorOperacao("020201").tributacao("000", "000001")).build());
        assertTrue(semDest.getMessage().contains("destinatario"), semDest.getMessage());

        IllegalStateException semCst = assertThrows(IllegalStateException.class,
                () -> dps().ibsCbs(IbsCbsBuilder.novo()
                        .codigoIndicadorOperacao("020201").destinatarioEhOProprioTomador()).build());
        assertTrue(semCst.getMessage().contains("CST"), semCst.getMessage());
    }

    @Test
    void indicadorDeOperacaoForaDoAnexo_eRecusado() {
        // E0901: seis dígitos não bastam — o código precisa constar na tabela do ANEXO_C.
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> dps().ibsCbs(minimo().codigoIndicadorOperacao("999999")).build());

        assertTrue(e.getMessage().contains("E0901"), e.getMessage());
        assertTrue(e.getMessage().contains(TabelaIndicadoresOperacao.VERSAO_ANEXO), e.getMessage());
    }

    @Test
    void codigosForaDoTamanho_saoRecusados() {
        // cIndOp e cClassTrib têm 6 dígitos; CST tem 3.
        assertThrows(IllegalStateException.class,
                () -> dps().ibsCbs(minimo().codigoIndicadorOperacao("1")).build());
        assertThrows(IllegalStateException.class,
                () -> dps().ibsCbs(minimo().tributacao("0000", "000001")).build());
        assertThrows(IllegalStateException.class,
                () -> dps().ibsCbs(minimo().tributacao("000", "1")).build());
    }

    // ---------------------------------------------------------------------------------------
    // Subgrupo imóvel
    // ---------------------------------------------------------------------------------------

    @Test
    void imovelPorCib_eValidoNoSchema() throws Exception {
        String xml = dps().ibsCbs(minimo().imovel(ImovelIbsCbs.porCib("12345678", "IM-001"))).build();

        validar(xml);
        assertTrue(xml.contains("<cCIB>12345678</cCIB>"));
        assertTrue(xml.contains("<inscImobFisc>IM-001</inscImobFisc>"));
    }

    @Test
    void imovelPorEnderecoNacional_eValidoNoSchema() throws Exception {
        String xml = dps().ibsCbs(minimo().imovel(ImovelIbsCbs.porEndereco(
                ImovelIbsCbs.EnderecoImovel.nacional(
                        "01310100", "Avenida Paulista", "1000", "Conj 51", "Bela Vista")))).build();

        validar(xml);
        assertTrue(xml.contains("<CEP>01310100</CEP>"));
        assertFalse(xml.contains("cCIB"), "CIB e endereço são exclusivos");
    }

    @Test
    void imovelPorEnderecoNoExterior_eValidoNoSchema() throws Exception {
        String xml = dps().ibsCbs(minimo().imovel(ImovelIbsCbs.porEndereco(
                ImovelIbsCbs.EnderecoImovel.exterior(
                        "10001", "New York", "NY", "5th Avenue", "100", null, "Manhattan")))).build();

        validar(xml);
        assertTrue(xml.contains("<cEndPost>10001</cEndPost>"));
        assertTrue(xml.contains("<xCidade>New York</xCidade>"));
    }

    @Test
    void imovel_exigeExatamenteUmaFormaDeIdentificacao() {
        // O leiaute é uma escolha: ou CIB, ou endereço.
        assertThrows(IllegalArgumentException.class,
                () -> new ImovelIbsCbs("IM-001", null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new ImovelIbsCbs(null, "12345678", ImovelIbsCbs.EnderecoImovel.nacional(
                        "01310100", "Rua", "1", null, "Centro")));
    }

    @Test
    void codigoCib_temOitoCaracteres() {
        // E0933: 7 posições de código mais o dígito verificador.
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> ImovelIbsCbs.porCib("1234567"));
        assertTrue(e.getMessage().contains("E0933"), e.getMessage());
    }

    // ---------------------------------------------------------------------------------------
    // Subgrupo de reembolso, repasse e ressarcimento
    // ---------------------------------------------------------------------------------------

    private static DocumentoReembolso reembolso(BigDecimal valor) {
        return new DocumentoReembolso("01", null, valor,
                LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 1),
                DocumentoReembolso.DFeNacional.nfe("1".repeat(44)),
                DocumentoReembolso.Fornecedor.porCnpj("98765432000198", "Fornecedor Ltda"));
    }

    @Test
    void reembolso_eValidoNoSchema() throws Exception {
        String xml = dps().ibsCbs(minimo().reembolso(reembolso(new BigDecimal("250.00")))).build();

        validar(xml);
        assertTrue(xml.contains("<gReeRepRes>"));
        assertTrue(xml.contains("<tpReeRepRes>01</tpReeRepRes>"));
        assertTrue(xml.contains("<vlrReeRepRes>250.00</vlrReeRepRes>"));
        assertTrue(xml.contains("<tipoChaveDFe>2</tipoChaveDFe>"));
        assertTrue(xml.contains("<xNome>Fornecedor Ltda</xNome>"));
    }

    @Test
    void reembolsoComDocumentoNaoFiscal_eValidoNoSchema() throws Exception {
        String xml = dps().ibsCbs(minimo().reembolso(new DocumentoReembolso(
                "99", "Outro reembolso por conta e ordem", new BigDecimal("100.00"),
                LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 1),
                new DocumentoReembolso.DocumentoNaoFiscal("REC-42", "Recibo de despesa"), null))).build();

        validar(xml);
        assertTrue(xml.contains("<nDoc>REC-42</nDoc>"));
        assertTrue(xml.contains("<xTpReeRepRes>Outro reembolso por conta e ordem</xTpReeRepRes>"));
    }

    @Test
    void reembolsoAcimaDoValorDoServico_eRecusado() {
        // E0953: o reembolso é parte do que foi cobrado, não pode superá-lo. O valor do serviço
        // vive no DpsBuilder, então a conferência só acontece na montagem da DPS.
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> dps().ibsCbs(minimo().reembolso(reembolso(new BigDecimal("1000.01")))).build());
        assertTrue(e.getMessage().contains("E0953"), e.getMessage());
    }

    @Test
    void descricaoDoTipo_soExisteNoTipo99() {
        // E0952
        assertThrows(IllegalArgumentException.class, () -> new DocumentoReembolso(
                "01", "Descrição que não cabe", BigDecimal.TEN,
                LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 1),
                new DocumentoReembolso.DocumentoNaoFiscal("1", "x"), null));

        assertThrows(IllegalArgumentException.class, () -> new DocumentoReembolso(
                "99", null, BigDecimal.TEN,
                LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 1),
                new DocumentoReembolso.DocumentoNaoFiscal("1", "x"), null));
    }

    @Test
    void emissaoAnteriorACompetencia_eRecusada() {
        // E0950 / E0951
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new DocumentoReembolso("01", null, BigDecimal.TEN,
                        LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 20),
                        new DocumentoReembolso.DocumentoNaoFiscal("1", "x"), null));
        assertTrue(e.getMessage().contains("E0950"), e.getMessage());
    }

    @Test
    void outroDocumentoFiscal_exigeCompetenciaAte2025() {
        // E0942: é resquício do regime anterior à reforma.
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new DocumentoReembolso("01", null, BigDecimal.TEN,
                        LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 1),
                        new DocumentoReembolso.OutroDocumentoFiscal(MUNICIPIO_SP, "1", "Nota antiga"), null));
        assertTrue(e.getMessage().contains("E0942"), e.getMessage());
    }

    @Test
    void chaveDFe_temTamanhoConformeOTipo() {
        // E0940: NFS-e tem 50 dígitos; NF-e e CT-e têm 44.
        assertThrows(IllegalArgumentException.class,
                () -> DocumentoReembolso.DFeNacional.nfe("1".repeat(49) + "9"));
        assertThrows(IllegalArgumentException.class,
                () -> DocumentoReembolso.DFeNacional.nfse("1".repeat(44)));
        assertEquals(50, DocumentoReembolso.DFeNacional.nfse("1".repeat(49) + "9").chave().length());
    }

    @Test
    void destinatarioExigeExatamenteUmaInscricao() {
        assertThrows(IllegalArgumentException.class,
                () -> new IbsCbsBuilder.Destinatario(null, null, null, "Sem inscrição", null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new IbsCbsBuilder.Destinatario("98765432000198", "12345678909", null, "Dois", null, null));
        assertThrows(IllegalArgumentException.class,
                () -> IbsCbsBuilder.Destinatario.porCnpj("98765432000198", null));
    }
}
