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

    /** Serviço prestado sobre a pessoa, no estabelecimento do fornecedor — não envolve imóvel. */
    private static final String INDOP_SEM_IMOVEL = "030101";

    static void validar(String xml) throws Exception {
        assertEquals(List.of(), ValidacaoXsd.erros("DPS_v1.01.xsd", xml), "XML deve ser válido no XSD oficial");
    }

    static DpsBuilder dps() {
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
                .codigoIndicadorOperacao(INDOP_SEM_IMOVEL)
                .destinatarioEhOProprioTomador()
                .tributacao("000", "000001");
    }

    @Test
    void grupoMinimo_eValidoNoSchema() throws Exception {
        String xml = dps().ibsCbs(minimo()).build();

        validar(xml);
        assertTrue(xml.contains("<IBSCBS>"));
        assertTrue(xml.contains("<cIndOp>" + INDOP_SEM_IMOVEL + "</cIndOp>"));
        // NT 009: CST e cClassTrib vão direto em trib, e o gIBSCBS só sai quando há o que declarar.
        assertTrue(xml.contains("<trib><CST>000</CST><cClassTrib>000001</cClassTrib></trib>"), xml);
        assertFalse(xml.contains("<gIBSCBS>"), "sem crédito presumido nem tributação regular");
    }

    @Test
    void finNFSe_ficaNaRaizDaInfDps_eNaoNoGrupo() throws Exception {
        // NT 009: sem finNFSe na raiz a Sefin recusa com E1235, acusando o cLocEmi.
        String xml = dps().ibsCbs(minimo()).build();

        int finNfse = xml.indexOf("<finNFSe>0</finNFSe>");
        assertTrue(finNfse > 0, "finNFSe é obrigatório na infDPS");
        assertTrue(finNfse < xml.indexOf("<cLocEmi>"), "finNFSe vem antes do cLocEmi");
        assertTrue(finNfse < xml.indexOf("<IBSCBS>"), "finNFSe não fica mais dentro do grupo IBS/CBS");
        assertTrue(dps().build().contains("<finNFSe>0</finNFSe>"), "sai mesmo sem o grupo IBS/CBS");
    }

    @Test
    void indicadorDeOperacao_eOpcional() throws Exception {
        // NT 009: cIndOp virou 0-1; E0859 chega a proibi-lo quando não há tributação de IBS/CBS.
        String xml = dps().ibsCbs(IbsCbsBuilder.novo()
                .destinatarioEhOProprioTomador().tributacao("000", "000001")).build();

        validar(xml);
        assertFalse(xml.contains("cIndOp"));
    }

    @Test
    void grupoCompleto_eValidoNoSchema() throws Exception {
        String xml = dps().ibsCbs(IbsCbsBuilder.novo()
                        .naoEhUsoOuConsumoPessoal()
                        .codigoIndicadorOperacao(INDOP_SEM_IMOVEL)
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
        assertTrue(xml.contains("<gIBSCBS><cCredPres>00</cCredPres>"), xml);
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
        IllegalStateException semDest = assertThrows(IllegalStateException.class,
                () -> dps().ibsCbs(IbsCbsBuilder.novo()
                        .codigoIndicadorOperacao(INDOP_SEM_IMOVEL).tributacao("000", "000001")).build());
        assertTrue(semDest.getMessage().contains("destinatario"), semDest.getMessage());

        IllegalStateException semCst = assertThrows(IllegalStateException.class,
                () -> dps().ibsCbs(IbsCbsBuilder.novo()
                        .codigoIndicadorOperacao(INDOP_SEM_IMOVEL).destinatarioEhOProprioTomador()).build());
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
    // Subgrupo imóvel — reestruturado pela NT 009
    // ---------------------------------------------------------------------------------------

    @Test
    void imovelPorCib_eValidoNoSchema() throws Exception {
        String xml = dps().ibsCbs(minimo().imovel(ImovelIbsCbs.de(MUNICIPIO_SP,
                ImovelIbsCbs.UnidadeImobiliaria.porCib("12345678", "IM-001")))).build();

        validar(xml);
        assertTrue(xml.contains("<imovel><cMun>" + MUNICIPIO_SP + "</cMun>"), xml);
        assertTrue(xml.contains("<gUnidImob><inscImobFisc>IM-001</inscImobFisc><cCIB>12345678</cCIB>"), xml);
    }

    @Test
    void imovelPorEndereco_eValidoNoSchema() throws Exception {
        String xml = dps().ibsCbs(minimo().imovel(ImovelIbsCbs.de(MUNICIPIO_SP,
                ImovelIbsCbs.UnidadeImobiliaria.porEndereco(new ImovelIbsCbs.EnderecoImovel(
                        "01310-100", "Avenida Paulista", "1000", "Conj 51", "Bela Vista"))))).build();

        validar(xml);
        assertTrue(xml.contains("<CEP>01310100</CEP>"));
        assertFalse(xml.contains("cCIB"), "CIB e endereço são exclusivos");
    }

    @Test
    void variasUnidades_saemNaOrdem() throws Exception {
        String xml = dps().ibsCbs(minimo().imovel(ImovelIbsCbs.de(MUNICIPIO_SP,
                ImovelIbsCbs.UnidadeImobiliaria.porCib("11111111"),
                ImovelIbsCbs.UnidadeImobiliaria.porCib("22222222")))).build();

        validar(xml);
        assertTrue(xml.indexOf("11111111") < xml.indexOf("22222222"));
    }

    @Test
    void locacaoComAjustes_eValidaNoSchema() throws Exception {
        // gLocacao e gAjusteBCLocImoveis só valem no subitem 99.03 (E0936/E0937/E0939); o schema
        // não sabe disso, então aqui só se confere a forma.
        String xml = dps().ibsCbs(minimo().imovel(ImovelIbsCbs.locacao(MUNICIPIO_SP,
                ImovelIbsCbs.Locacao.integral(new BigDecimal("3000.00")),
                ImovelIbsCbs.UnidadeImobiliaria.porCib("12345678").comAjustes(
                        new ImovelIbsCbs.AjusteLocacao("01", null, new BigDecimal("200.00")),
                        new ImovelIbsCbs.AjusteLocacao("99", "Taxa de lixo", new BigDecimal("15.00"))))))
                .build();

        validar(xml);
        assertTrue(xml.contains("<gLocacao><pCopropriedade>100.00</pCopropriedade><vTotOper>3000.00</vTotOper>"), xml);
        assertTrue(xml.contains("<tpAjusteBCLocImoveis>01</tpAjusteBCLocImoveis>"));
        assertTrue(xml.contains("<xTpAjusteBCLocImoveis>Taxa de lixo</xTpAjusteBCLocImoveis>"));
    }

    @Test
    void ajusteDaLocacaoAcimaDoTotal_eRecusado() {
        // E0966
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> ImovelIbsCbs.locacao(MUNICIPIO_SP, ImovelIbsCbs.Locacao.integral(new BigDecimal("100.00")),
                        ImovelIbsCbs.UnidadeImobiliaria.porCib("12345678").comAjustes(
                                new ImovelIbsCbs.AjusteLocacao("03", null, new BigDecimal("100.01")))));
        assertTrue(e.getMessage().contains("E0966"), e.getMessage());
    }

    @Test
    void descricaoDoAjusteDaLocacao_soNoTipo99() {
        // E0949 / E0954
        assertThrows(IllegalArgumentException.class,
                () -> new ImovelIbsCbs.AjusteLocacao("99", null, BigDecimal.TEN));
        assertThrows(IllegalArgumentException.class,
                () -> new ImovelIbsCbs.AjusteLocacao("01", "não cabe", BigDecimal.TEN));
    }

    @Test
    void copropriedade_vaiDe0a100() {
        // E0940
        assertThrows(IllegalArgumentException.class, () -> new ImovelIbsCbs.Locacao(
                new BigDecimal("100.01"), BigDecimal.TEN, null, null, null));
    }

    @Test
    void indicadoresDeOperacaoComImovel_exigemUnidade() {
        // E0938: 020101, 020201 e 020301 identificam a unidade imobiliária.
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> dps().ibsCbs(minimo().codigoIndicadorOperacao("020201")).build());
        assertTrue(e.getMessage().contains("E0938"), e.getMessage());
    }

    @Test
    void unidade_exigeExatamenteUmaFormaDeIdentificacao() {
        // O leiaute é uma escolha: ou CIB, ou endereço.
        assertThrows(IllegalArgumentException.class,
                () -> new ImovelIbsCbs.UnidadeImobiliaria("IM-001", null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new ImovelIbsCbs.UnidadeImobiliaria(null, "12345678", new ImovelIbsCbs.EnderecoImovel(
                        "01310100", "Rua", "1", null, null), null));
    }

    @Test
    void codigoCib_temOitoCaracteres() {
        // E0933: 7 posições de código mais o dígito verificador.
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> ImovelIbsCbs.UnidadeImobiliaria.porCib("1234567"));
        assertTrue(e.getMessage().contains("E0933"), e.getMessage());
    }

    @Test
    void municipioDoImovel_eObrigatorio() {
        // E0929
        assertThrows(IllegalArgumentException.class, () -> ImovelIbsCbs.de(null));
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
