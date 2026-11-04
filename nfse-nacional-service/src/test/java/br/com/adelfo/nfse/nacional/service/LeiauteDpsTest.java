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
 * DPS no leiaute de fevereiro/2026, o de produção até 30/11/2026. Ver {@link LeiauteDps}.
 *
 * <p>Não há ambiente que aceite esse leiaute para teste — produção restrita já está na NT 009 —, então
 * a garantia vem do XSD oficial de fevereiro, validado nos dois sentidos: a DPS convertida passa
 * nele e a da NT 009 não.
 */
class LeiauteDpsTest {

    private static final String MUNICIPIO_SP = "3550308";

    /** A receita da Comaho do GUIA-DE-USO, seção 9, com tomador fictício. */
    private static DpsBuilder comaho() {
        return DpsBuilder.novo()
                .ambiente(TipoAmbiente.PRODUCAO)
                .municipioEmissor(MUNICIPIO_SP)
                .identificacao("1", "1", LocalDate.of(2026, 11, 4))
                .emitidaPeloPrestador()
                .prestadorCnpj("12345678000195")
                .prestadorContato(null, "fiscal@exemplo.com.br")
                .optanteSimplesNacionalMeEpp("1")
                .semRegimeEspecial()
                .tomadorCpf("12345678909", "Tomador de Teste")
                .servicoPrestadoNoMunicipio(MUNICIPIO_SP)
                .servico("140101", "MANUTENÇÃO EM EQUIPAMENTO")
                .codigoTributacaoMunicipal("009")
                .codigoNbs("120018200")
                .codigoInterno("OS1")
                .valorServico(new BigDecimal("150.00"))
                .issqnTributavel()
                .issqnNaoRetido()
                .totalTributosPercentual(new BigDecimal("13.45"), new BigDecimal("0.00"), new BigDecimal("3.90"));
    }

    private static IbsCbsBuilder ibsCbs() {
        return IbsCbsBuilder.novo()
                .codigoIndicadorOperacao("030101")
                .destinatarioEhOProprioTomador()
                .tributacao("000", "000001");
    }

    @Test
    void comaho_convertida_validaNoXsdDeFevereiro() {
        String xml = comaho().build(LeiauteDps.FEVEREIRO_2026);

        assertEquals(List.of(), ConversorLeiauteFevereiro2026.erros(xml));
        assertFalse(xml.contains("finNFSe"), "sem IBSCBS o finNFSe não tem onde ficar em fevereiro");
        assertTrue(xml.contains("<xDescServ>MANUTENÇÃO EM EQUIPAMENTO</xDescServ><cNBS>120018200</cNBS>"
                + "<cIntContrib>OS1</cIntContrib>"), xml);
    }

    @Test
    void osDoisLeiautesSeExcluem() throws Exception {
        // Garante que a validação discrimina: cada DPS só passa no XSD do próprio leiaute.
        String nt009 = comaho().build();
        String fevereiro = comaho().build(LeiauteDps.FEVEREIRO_2026);

        assertEquals(List.of(), ValidacaoXsd.erros("DPS_v1.01.xsd", nt009));
        assertFalse(ConversorLeiauteFevereiro2026.erros(nt009).isEmpty());
        assertFalse(ValidacaoXsd.erros("DPS_v1.01.xsd", fevereiro).isEmpty());
    }

    @Test
    void conversaoSoMudaPosicoes() {
        // Mesmos elementos, mesmos valores — muda a ordem, e o finNFSe 0 some sem o grupo IBS/CBS.
        // A mesma instância, para o dhEmi (tirado do relógio) ser igual nas duas.
        DpsBuilder dps = comaho();
        String nt009 = dps.build();
        String fevereiro = dps.build(LeiauteDps.FEVEREIRO_2026);

        assertEquals(ordenado(nt009.replace("<finNFSe>0</finNFSe>", "")), ordenado(fevereiro));
    }

    @Test
    void semPrefixoNemDeclaracaoRepetida() {
        // E1228: prefixo de namespace é rejeição. E o namespace da NFS-e declarado uma vez só, na raiz.
        String xml = comaho().ibsCbs(ibsCbs()).build(LeiauteDps.FEVEREIRO_2026);

        assertFalse(xml.matches("(?s).*<[A-Za-z]+:.*"), xml);
        assertEquals(1, xml.split("xmlns=", -1).length - 1, xml);
        assertFalse(xml.startsWith("<?xml"), xml);
    }

    @Test
    void comIbsCbs_finalidadeESituacaoTributariaDescemParaOGrupo() {
        String xml = comaho().ibsCbs(ibsCbs()).build(LeiauteDps.FEVEREIRO_2026);

        assertEquals(List.of(), ConversorLeiauteFevereiro2026.erros(xml));
        assertTrue(xml.contains("<IBSCBS><finNFSe>0</finNFSe>"), xml);
        assertTrue(xml.contains("<trib><gIBSCBS><CST>000</CST><cClassTrib>000001</cClassTrib></gIBSCBS></trib>"), xml);
    }

    @Test
    void ajusteDeBaseDeCalculo_eRecusado() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> comaho().deducaoPercentual(new BigDecimal("10.00")).build(LeiauteDps.FEVEREIRO_2026));
        assertTrue(e.getMessage().contains("vAjusteBC"), e.getMessage());
    }

    @Test
    void imovel_eRecusado() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> comaho().ibsCbs(ibsCbs().imovel(ImovelIbsCbs.de(MUNICIPIO_SP,
                        ImovelIbsCbs.UnidadeImobiliaria.porCib("12345678"))))
                        .build(LeiauteDps.FEVEREIRO_2026));
        assertTrue(e.getMessage().contains("imovel"), e.getMessage());
    }

    @Test
    void ibsCbsSemIndicadorDeOperacao_eRecusado() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> comaho().ibsCbs(IbsCbsBuilder.novo().destinatarioEhOProprioTomador().tributacao("000", "000001"))
                        .build(LeiauteDps.FEVEREIRO_2026));
        assertTrue(e.getMessage().contains("cIndOp"), e.getMessage());
    }

    @Test
    void tomadorSemNome_caiNaValidacaoDoXsd() {
        // xNome virou opcional na NT 009; em fevereiro é obrigatório. Não há regra própria para isso:
        // quem barra é o XSD, e a mensagem diz que a Sefin recusaria.
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> comaho().tomadorCpf("12345678909", null).build(LeiauteDps.FEVEREIRO_2026));
        assertTrue(e.getMessage().contains("E1235"), e.getMessage());
    }

    @Test
    void ntNove_eOPadrao() {
        DpsBuilder dps = comaho();
        assertEquals(dps.build(), dps.build(LeiauteDps.NT009_OUTUBRO_2026));
    }

    @Test
    void vigente_trocaEmProducaoEm1DeDezembro() {
        assertEquals(LeiauteDps.FEVEREIRO_2026,
                LeiauteDps.vigente(TipoAmbiente.PRODUCAO, LocalDate.of(2026, 11, 30)));
        assertEquals(LeiauteDps.NT009_OUTUBRO_2026,
                LeiauteDps.vigente(TipoAmbiente.PRODUCAO, LocalDate.of(2026, 12, 1)));
        assertEquals(LeiauteDps.NT009_OUTUBRO_2026,
                LeiauteDps.vigente(TipoAmbiente.PRODUCAO_RESTRITA, LocalDate.of(2026, 11, 4)));
    }

    /**
     * Tags e textos do XML em ordem alfabética — para comparar conteúdo ignorando posição. A raiz
     * fica de fora: a reserialização põe as declarações de namespace antes do {@code versao}.
     */
    private static List<String> ordenado(String xml) {
        return xml.replaceAll("<", "\n<").lines()
                .filter(l -> !l.isBlank() && !l.startsWith("<DPS "))
                .sorted().toList();
    }
}
