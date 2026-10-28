package br.com.adelfo.nfse.nacional.danfse;

import br.com.adelfo.nfse.nacional.danfse.internal.ConteudoDanfse;
import br.com.adelfo.nfse.nacional.danfse.internal.XmlNfse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regras de conteúdo da tabela do item 2.4.5 da NT 008, sem gerar PDF. */
class ConteudoDanfseTest {

    private static String xml;

    @BeforeAll
    static void carregar() throws Exception {
        DanfseServiceTest.carregar();
        xml = DanfseServiceTest.xml;
    }

    private static ConteudoDanfse de(String x) {
        return new ConteudoDanfse(XmlNfse.ler(x));
    }

    /** Insere um trecho logo após a abertura de um elemento. */
    private static String inserirApos(String elemento, String trecho) {
        String abertura = "<" + elemento + ">";
        assertTrue(xml.contains(abertura), elemento);
        return xml.replaceFirst(abertura, abertura + trecho);
    }

    @Test
    void identificacao() {
        ConteudoDanfse c = de(xml);

        assertEquals(DanfseServiceTest.CHAVE, c.chaveAcesso(), "Id sem o prefixo NFS");
        assertEquals("2", c.numeroNfse());
        assertEquals("27/10/2026", c.competencia());
        assertEquals("27/10/2026 17:29:52", c.dataHoraEmissaoNfse(), "horário do XML, sem converter fuso");
        assertEquals("Prestador", c.emitente());
        assertEquals("NFS-e Gerada", c.situacao());
        assertEquals("NFS-e regular", c.finalidade());
        assertEquals("https://www.nfse.gov.br/ConsultaPublica/?tpc=1&chave=" + DanfseServiceTest.CHAVE,
                c.urlConsultaPublica());
    }

    @Test
    void finalidade_doLeiauteAnterior_tambemELida() {
        // Antes da NT 009 o finNFSe ficava em IBSCBS; notas antigas precisam imprimir igual.
        String antigo = xml.replace("<finNFSe>0</finNFSe>", "")
                .replace("<toma>", "<IBSCBS><finNFSe>0</finNFSe></IBSCBS><toma>");
        assertEquals("NFS-e regular", de(antigo).finalidade());
    }

    @Test
    void prestador_completaComOEmit() {
        ConteudoDanfse.Pessoa p = de(xml).prestador();

        assertEquals("06.169.966/0001-33", p.documento());
        assertEquals("PRESTADORA DE SERVICOS DE TECNOLOGIA EXEMPLO LTDA", p.nome());
        assertEquals("São Paulo / SP", p.municipio());
        assertEquals("3550308 / 01.310-100", p.codigoECep());
        assertEquals("(11) 3000-0000", p.telefone());
        // O e-mail informado na DPS prevalece sobre o do cadastro.
        assertEquals("contato@prestadora.exemplo", p.email());
    }

    @Test
    void camposVazios_saemComTraco() {
        // Nota 12.
        ConteudoDanfse c = de(xml);
        assertEquals("-", c.prestador().inscricaoMunicipal());
        assertEquals("-", c.baseCalculoIssqn());
        assertEquals("-", c.cstEClassificacao());
    }

    @Test
    void tomadorEDestinatario() {
        ConteudoDanfse c = de(xml);

        assertEquals("TOMADORA COMERCIO EXEMPLO LTDA - EPP", c.tomador().nome());
        assertEquals("RUA AUGUSTA, 500, CONSOLACAO", c.tomador().endereco());
        assertNull(c.destinatario());
        assertNull(c.intermediario());
        assertFalse(c.destinatarioEhOTomador());
    }

    @Test
    void contribuicoesSociais_somamPisCofinsQuandoRetidos() {
        // Regra da v1.02: com tpRetPisCofins = 1, CSLL + PIS + COFINS nas retidas, e os próprios zerados.
        String retido = inserirApos("trib", "<tribFed><piscofins><CST>01</CST><vBCPisCofins>1.00</vBCPisCofins>"
                + "<pAliqPis>1.00</pAliqPis><pAliqCofins>3.00</pAliqCofins><vPis>0.10</vPis><vCofins>0.30</vCofins>"
                + "<tpRetPisCofins>1</tpRetPisCofins></piscofins><vRetCSLL>0.05</vRetCSLL></tribFed>");
        ConteudoDanfse c = de(retido);

        assertEquals("R$ 0,45", c.contribuicoesSociaisRetidas());
        assertEquals("R$ 0,00", c.pisApuracaoPropria());
        assertEquals("R$ 0,00", c.cofinsApuracaoPropria());
        assertEquals("PIS/COFINS Retidos", c.descricaoContribuicoesRetidas());

        ConteudoDanfse naoRetido = de(retido.replace("<tpRetPisCofins>1<", "<tpRetPisCofins>2<"));
        assertEquals("R$ 0,05", naoRetido.contribuicoesSociaisRetidas());
        assertEquals("R$ 0,10", naoRetido.pisApuracaoPropria());
    }

    @Test
    void linhaDePisCofins_soAte2026() {
        // Nota 6.
        assertTrue(de(xml).imprimeLinhaPisCofins());
        assertFalse(de(xml.replace("<dCompet>2026-10-27</dCompet>", "<dCompet>2027-01-04</dCompet>"))
                .imprimeLinhaPisCofins());
    }

    @Test
    void informacoesComplementares_naOrdemDaNt_separadasPorPipe() {
        String completo = xml.replace("</cServ>", "</cServ><infoCompl><docRef>CONTRATO-9</docRef><xPed>PED-1</xPed>"
                        + "<gItemPed><xItemPed>1</xItemPed><xItemPed>2</xItemPed></gItemPed>"
                        + "<xInfComp>Observação livre</xInfComp></infoCompl>");

        assertEquals("Inf. Cont.: Observação livre | Doc. Ref.: CONTRATO-9 | Núm. Ped.: PED-1 | Item Ped.: 1, 2",
                de(completo).informacoesComplementares());
    }

    @Test
    void totaisAproximados_emValorOuPercentual() {
        assertEquals("Totais Aproximados dos Tributos cfe. Lei nº 12.741/2012: Federais: R$ 0,00 ; "
                + "Estaduais: R$ 0,00 ; Municipais: R$ 0,00", de(xml).totaisAproximadosTributos());

        String percentual = xml.replaceAll("<vTotTrib>.*</vTotTrib>",
                "<pTotTrib><pTotTribFed>13.45</pTotTribFed><pTotTribEst>0.00</pTotTribEst>"
                        + "<pTotTribMun>2.00</pTotTribMun></pTotTrib>");
        assertTrue(de(percentual).totaisAproximadosTributos().endsWith("Federais: 13,45% ; Estaduais: 0,00% ; "
                + "Municipais: 2,00%"));
    }

    @Test
    void issqn_semGrupoMunicipal_naoESujeito() {
        // Nota 4.
        assertTrue(de(xml).sujeitoAoIssqn());
        assertFalse(de(xml.replaceAll("<tribMun>.*?</tribMun>", "")).sujeitoAoIssqn());
        assertFalse(de(xml.replace("<cTribNac>010401</cTribNac>", "<cTribNac>990101</cTribNac>")).sujeitoAoIssqn());
    }

    @Test
    void localDeIncidencia_semNomeNoXml_vemDaTabela() {
        // Notas de sistema municipal trazem só o cLocIncid; a nota de referência 236 saía "SP / BR".
        assertEquals("São Paulo / SP / BR", de(xml).localIncidenciaIssqn());
        assertEquals("São Paulo / SP / BR", de(xml.replace("<xLocIncid>São Paulo</xLocIncid>", ""))
                .localIncidenciaIssqn());
    }

    @Test
    void campoLongo_terminaEmReticencias() {
        String nomeLongo = "N".repeat(150);
        String x = xml.replace("TOMADORA COMERCIO EXEMPLO LTDA - EPP", nomeLongo);

        String nome = de(x).tomador().nome();
        assertEquals(80, nome.length());
        assertTrue(nome.endsWith("..."));
    }
}
