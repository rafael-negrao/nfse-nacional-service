package br.com.adelfo.nfse.nacional.danfse;

import br.com.adelfo.nfse.nacional.service.dto.response.SituacaoNfse;
import com.lowagie.text.PageSize;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DANFSe gerado a partir de uma NFS-e real de produção restrita (27/10/2026), com nomes, endereços
 * e contatos trocados por dados fictícios.
 */
class DanfseServiceTest {

    static final String CHAVE = "35503082206169966000133000000000000226100127014831";

    static String xml;
    private static DanfseService danfse;

    @BeforeAll
    static void carregar() throws Exception {
        danfse = new DanfseService();
        try (InputStream in = DanfseServiceTest.class.getResourceAsStream("/nfse-producao-restrita.xml")) {
            xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static String texto(byte[] pdf) throws Exception {
        PdfReader reader = new PdfReader(pdf);
        try {
            return new PdfTextExtractor(reader).getTextFromPage(1);
        } finally {
            reader.close();
        }
    }

    @Test
    void geraUmaUnicaPaginaA4() throws Exception {
        // Item 2.2: o DANFSe é impresso obrigatoriamente em uma única página, retrato, A4.
        byte[] pdf = danfse.gerarPdf(xml);

        PdfReader reader = new PdfReader(pdf);
        try {
            assertEquals(1, reader.getNumberOfPages());
            Rectangle pagina = reader.getPageSize(1);
            assertEquals(PageSize.A4.getWidth(), pagina.getWidth(), 0.5);
            assertEquals(PageSize.A4.getHeight(), pagina.getHeight(), 0.5);
        } finally {
            reader.close();
        }
    }

    @Test
    void imprimeOsCamposDaNota() throws Exception {
        String t = texto(danfse.gerarPdf(xml));

        assertTrue(t.contains("DANFSe v2.0"), t);
        assertTrue(t.contains(CHAVE));
        assertTrue(t.contains("Município: São Paulo / SP"));
        assertTrue(t.contains("06.169.966/0001-33"), "CNPJ do prestador com máscara");
        assertTrue(t.contains("01.04.01 / 001"), "código de tributação nacional / municipal");
        assertTrue(t.contains("Elaboração de programa de computador (software). Valor Bruto R$ 1,00"));
        assertTrue(t.contains("Totais Aproximados dos Tributos cfe. Lei nº 12.741/2012"));
    }

    @Test
    void prestadorSaiDoEmitQuandoADpsNaoOTraz() throws Exception {
        // Com tpEmit = 1, E0121/E0128 proíbem nome e endereço do prestador na DPS. Só o emit os tem.
        String t = texto(danfse.gerarPdf(xml));

        assertTrue(t.contains("PRESTADORA DE SERVICOS DE TECNOLOGIA EXEMPLO LTDA"), t);
        assertTrue(t.contains("AVENIDA PAULISTA, 1000, BELA VISTA"));
        // O CEP do emit vem sem o zero à esquerda (1310100); o DANFSe o restaura.
        assertTrue(t.contains("3550308 / 01.310-100"), t);
    }

    @Test
    void homologacao_levaAExpressaoSemValidadeJuridica() throws Exception {
        assertTrue(texto(danfse.gerarPdf(xml)).contains("NFS-e SEM VALIDADE JURÍDICA"));

        String producao = xml.replace("<tpAmb>2</tpAmb>", "<tpAmb>1</tpAmb>");
        assertFalse(texto(danfse.gerarPdf(producao)).contains("SEM VALIDADE JURÍDICA"));
    }

    @Test
    void notaCancelada_levaMarcaDagua() throws Exception {
        // Item 2.5.1. A situação vem de fora: o XML segue com cStat 100 após o cancelamento.
        assertTrue(texto(danfse.gerarPdf(DanfseRequest.de(xml).comSituacao(SituacaoNfse.CANCELADA)))
                .contains("CANCELADA"));
        assertTrue(texto(danfse.gerarPdf(DanfseRequest.de(xml).comSituacao(SituacaoNfse.SUBSTITUIDA)))
                .contains("SUBSTITUÍDA"));

        String valida = texto(danfse.gerarPdf(xml));
        assertFalse(valida.contains("CANCELADA"));
        assertFalse(valida.contains("SUBSTITUÍDA"));
    }

    @Test
    void blocosAusentes_viramAMensagemDaNota2() throws Exception {
        String t = texto(danfse.gerarPdf(xml));

        assertTrue(t.contains("DESTINATÁRIO DA OPERAÇÃO NÃO IDENTIFICADO NA NFS-e"));
        assertTrue(t.contains("INTERMEDIÁRIO DA OPERAÇÃO NÃO IDENTIFICADO NA NFS-e"));
    }

    @Test
    void canhoto_eOpcional() throws Exception {
        assertTrue(texto(danfse.gerarPdf(xml)).contains("DATA CIENTIFICAÇÃO"));
        assertFalse(texto(danfse.gerarPdf(DanfseRequest.de(xml).semCanhoto())).contains("DATA CIENTIFICAÇÃO"));
    }

    @Test
    void descricaoLonga_naoPassaDeUmaPagina() throws Exception {
        String longa = xml.replace("Elaboração de programa de computador (software). Valor Bruto R$ 1,00",
                "Serviço extenso. ".repeat(80).trim());

        PdfReader reader = new PdfReader(danfse.gerarPdf(longa));
        try {
            assertEquals(1, reader.getNumberOfPages(), "a descrição não pode empurrar o DANFSe para outra página");
        } finally {
            reader.close();
        }
    }

    @Test
    void descricaoNoTamanhoMaximoDoXsd_saiInteiraMesmoComTodosOsBlocos() throws Exception {
        // 2.000 caracteres é o limite de xDescServ. Com tomador, destinatário e intermediário
        // presentes sobra o menor espaço possível — e ainda assim o texto não pode ser cortado.
        String descricao = ("Desenvolvimento e manutenção evolutiva do sistema de gestão, com integração ao ERP "
                + "corporativo, testes automatizados e implantação assistida. ").repeat(15);
        descricao = descricao.substring(0, 1980) + " FIM-DA-DESCRICAO";
        String completo = xml
                .replace("Elaboração de programa de computador (software). Valor Bruto R$ 1,00", descricao)
                .replace("</toma>", "</toma><interm><CNPJ>11222333000181</CNPJ><xNome>INTERMEDIARIA EXEMPLO</xNome>"
                        + "</interm>")
                .replace("</valores></infDPS>", "</valores><IBSCBS><indDest>1</indDest><dest><CNPJ>44555666000105"
                        + "</CNPJ><xNome>DESTINATARIA EXEMPLO</xNome></dest><valores><trib><CST>000</CST>"
                        + "<cClassTrib>000001</cClassTrib></trib></valores></IBSCBS></infDPS>");

        byte[] pdf = danfse.gerarPdf(completo);
        String t = texto(pdf).replaceAll("\\s+", " ");

        assertTrue(t.contains("INTERMEDIARIA EXEMPLO") && t.contains("DESTINATARIA EXEMPLO"), "todos os blocos presentes");
        assertTrue(t.contains("FIM-DA-DESCRICAO"), "o fim da descrição não pode ser cortado");
        assertEquals(1, new PdfReader(pdf).getNumberOfPages());
    }

    @Test
    void xmlQueNaoEDeNfse_eRecusadoComMensagem() {
        DanfseException e = assertThrows(DanfseException.class, () -> danfse.gerarPdf("<DPS><infDPS/></DPS>"));
        assertTrue(e.getMessage().contains("infNFSe"), e.getMessage());

        assertThrows(DanfseException.class, () -> danfse.gerarPdf("isto não é XML"));
    }

    @Test
    void xmlComDoctype_eRecusado() {
        // XML vindo de fora: nada de DTD nem entidade externa.
        String comDtd = "<?xml version=\"1.0\"?><!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]>" + xml
                .substring(xml.indexOf("<NFSe"));
        assertThrows(DanfseException.class, () -> danfse.gerarPdf(comDtd));
    }

    @Test
    void geracaoConcorrente_produzPdfsIndependentes() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Callable<String>> tarefas = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                tarefas.add(() -> texto(danfse.gerarPdf(xml)));
            }
            for (Future<String> f : pool.invokeAll(tarefas)) {
                assertTrue(f.get().contains(CHAVE));
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
