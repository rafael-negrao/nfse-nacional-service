package br.com.adelfo.nfse.nacional.service;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Converte a DPS da NT 009 (a que o JAXB gera) para o leiaute de fevereiro/2026, o que produção
 * aceita até 30/11/2026. Ver {@link LeiauteDps}.
 *
 * <p>Converter em vez de gerar: o {@link DpsBuilder} é escrito sobre as classes JAXB da NT 009, e
 * duplicá-lo para um leiaute que sai de cena em 01/12/2026 custaria mais que a diferença entre os
 * dois. Na DPS sem ajuste de base de cálculo e sem imóvel, a diferença é de posição:
 * <ul>
 *   <li>{@code finNFSe} — na NT 009, obrigatório na raiz da {@code infDPS}; em fevereiro, primeiro
 *       filho do grupo {@code IBSCBS}, que é opcional. Sem o grupo, o campo some;</li>
 *   <li>{@code cNBS} e {@code cIntContrib} — na NT 009, antes de {@code xDescServ}; em fevereiro,
 *       depois;</li>
 *   <li>{@code CST} e {@code cClassTrib} — na NT 009, direto em {@code IBSCBS/valores/trib}; em
 *       fevereiro, dentro de {@code trib/gIBSCBS}, então obrigatório.</li>
 * </ul>
 *
 * <p>O que mudou de estrutura é recusado, com o nome do grupo: {@code vAjusteBC} (em fevereiro
 * eram {@code vDedRed} e {@code gReeRepRes}, com outros códigos de tipo), o {@code imovel}
 * reestruturado, {@code bensMoveis} (não existia) e {@code IBSCBS} sem {@code cIndOp} (era
 * obrigatório). O resto — tomador sem {@code xNome}, CNPJ com letras — é apanhado pela validação
 * contra o XSD de fevereiro, que roda em toda conversão. Antes a rejeição seria um {@code E1235} da
 * Sefin, numa emissão de produção.
 *
 * <p>A conversão acontece antes da assinatura, então a assinatura cobre o documento convertido.
 */
final class ConversorLeiauteFevereiro2026 {

    private static final String NS = MarshallerNfse.NS_NFSE;

    /** Pacote {@code NFSe-ESQUEMAS_XSD-v1.01-20260209}, publicado pelo módulo de schemas só para validação. */
    private static final String PASTA_XSD = "/xsd/fevereiro-2026/";
    private static final List<String> XSDS = List.of(
            "DPS_v1.01.xsd",
            "tiposComplexos_v1.01.xsd",
            "tiposSimples_v1.01.xsd",
            "xmldsig-core-schema.xsd");

    /** Thread-safe; carregado na primeira conversão. */
    private static Schema schema;

    private ConversorLeiauteFevereiro2026() {
    }

    /** DPS da NT 009, não assinada → mesma DPS no leiaute de fevereiro/2026, validada no XSD. */
    static String converter(String xmlNt009) {
        Document doc = ler(xmlNt009);
        Element infDps = filho(doc.getDocumentElement(), "infDPS");
        if (infDps == null) {
            throw new IllegalArgumentException("o XML não é uma DPS: falta infDPS");
        }
        Element ibsCbs = filho(infDps, "IBSCBS");

        recusarSemEquivalente(infDps, ibsCbs);
        moverFinalidade(infDps, ibsCbs);
        moverDescricaoDoServico(infDps);
        if (ibsCbs != null) {
            moverSituacaoTributaria(ibsCbs);
        }

        String xml = escrever(doc);
        validar(xml);
        return xml;
    }

    private static void recusarSemEquivalente(Element infDps, Element ibsCbs) {
        if (filho(filho(infDps, "valores"), "vAjusteBC") != null) {
            throw semEquivalente("ajuste de base de cálculo (vAjusteBC)",
                    "lá eram vDedRed (ISSQN) e gReeRepRes (IBS/CBS), com outros códigos de tipo");
        }
        if (ibsCbs == null) {
            return;
        }
        if (filho(ibsCbs, "imovel") != null) {
            throw semEquivalente("grupo imovel do IBS/CBS", "a estrutura foi refeita pela NT 009");
        }
        if (filho(ibsCbs, "bensMoveis") != null) {
            throw semEquivalente("grupo bensMoveis do IBS/CBS", "o grupo não existia");
        }
        if (filho(ibsCbs, "cIndOp") == null) {
            throw new IllegalStateException("o leiaute de fevereiro/2026 exige cIndOp no grupo IBS/CBS"
                    + " (a NT 009 o tornou opcional): informe IbsCbsBuilder.codigoIndicadorOperacao");
        }
    }

    private static IllegalStateException semEquivalente(String oQue, String porque) {
        return new IllegalStateException(oQue + " não tem equivalente no leiaute de fevereiro/2026 ("
                + porque + "). Emita no leiaute NT009_OUTUBRO_2026 a partir de "
                + LeiauteDps.INICIO_NT009_EM_PRODUCAO + ", ou pelo Emissor Nacional");
    }

    private static void moverFinalidade(Element infDps, Element ibsCbs) {
        Element finNfse = filho(infDps, "finNFSe");
        if (finNfse == null) {
            return;
        }
        infDps.removeChild(finNfse);
        if (ibsCbs != null) {
            ibsCbs.insertBefore(finNfse, ibsCbs.getFirstChild());
        } else if (!"0".equals(finNfse.getTextContent())) {
            // Em fevereiro o campo só existe dentro do IBSCBS; fora dele a nota é regular por omissão.
            throw new IllegalStateException("finNFSe " + finNfse.getTextContent()
                    + " exige o grupo IBS/CBS no leiaute de fevereiro/2026");
        }
    }

    private static void moverDescricaoDoServico(Element infDps) {
        Element cServ = filho(filho(infDps, "serv"), "cServ");
        if (cServ == null) {
            return;
        }
        Element descricao = filho(cServ, "xDescServ");
        Element primeiroDepois = filho(cServ, "cNBS") != null ? filho(cServ, "cNBS") : filho(cServ, "cIntContrib");
        if (descricao != null && primeiroDepois != null) {
            cServ.insertBefore(descricao, primeiroDepois);
        }
    }

    private static void moverSituacaoTributaria(Element ibsCbs) {
        Element trib = filho(filho(ibsCbs, "valores"), "trib");
        if (trib == null) {
            return;
        }
        Element cst = filho(trib, "CST");
        Element classificacao = filho(trib, "cClassTrib");
        Element grupo = filho(trib, "gIBSCBS");
        if (grupo == null) {
            grupo = trib.getOwnerDocument().createElementNS(NS, "gIBSCBS");
            trib.appendChild(grupo);
        }
        Node antes = grupo.getFirstChild();
        if (cst != null) {
            grupo.insertBefore(cst, antes);
        }
        if (classificacao != null) {
            grupo.insertBefore(classificacao, antes);
        }
    }

    /** Primeiro filho elemento com o nome local dado; {@code null} se não houver (ou se o pai for nulo). */
    private static Element filho(Element pai, String nome) {
        if (pai == null) {
            return null;
        }
        for (Node n = pai.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && nome.equals(e.getLocalName())) {
                return e;
            }
        }
        return null;
    }

    private static Document ler(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        } catch (Exception e) {
            throw new IllegalArgumentException("DPS mal formada: " + e.getMessage(), e);
        }
    }

    private static String escrever(Document doc) {
        try {
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            // Mesma forma que o MarshallerNfse entrega: sem declaração, sem indentação.
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(doc), new StreamResult(writer));
            return writer.toString();
        } catch (Exception e) {
            throw new IllegalStateException("falha ao serializar a DPS convertida", e);
        }
    }

    private static void validar(String xml) {
        List<String> erros = erros(xml);
        if (!erros.isEmpty()) {
            throw new IllegalStateException("a DPS não cabe no leiaute de fevereiro/2026 — a Sefin de"
                    + " produção a recusaria com E1235: " + String.join(" | ", erros));
        }
    }

    /** Erros de schema do XML contra o XSD de fevereiro/2026; lista vazia quando é válido. */
    static List<String> erros(String xml) {
        List<String> erros = new ArrayList<>();
        Validator validator = schema().newValidator();
        validator.setErrorHandler(new ErrorHandler() {
            public void warning(SAXParseException e) {
            }

            public void error(SAXParseException e) {
                erros.add(e.getMessage());
            }

            public void fatalError(SAXParseException e) {
                erros.add(e.getMessage());
            }
        });
        try {
            validator.validate(new StreamSource(new StringReader(xml)));
        } catch (SAXException e) {
            if (erros.isEmpty()) {
                erros.add(e.getMessage());
            }
        } catch (Exception e) {
            throw new IllegalStateException("falha ao validar a DPS no leiaute de fevereiro/2026", e);
        }
        return erros;
    }

    /**
     * Os XSDs se referenciam por caminho relativo, e resolver isso a partir de uma URL
     * {@code jar:file:…!/} falha de forma intermitente com {@code src-resolve: ds:Signature} — o
     * mesmo problema descrito no {@code ValidacaoXsd} dos testes. Copiá-los para arquivos reais uma
     * vez faz a resolução funcionar como os autores do schema previram.
     */
    private static synchronized Schema schema() {
        if (schema != null) {
            return schema;
        }
        try {
            Path pasta = Files.createTempDirectory("nfse-xsd-fevereiro-2026");
            pasta.toFile().deleteOnExit();
            for (String nome : XSDS) {
                try (InputStream in = ConversorLeiauteFevereiro2026.class.getResourceAsStream(PASTA_XSD + nome)) {
                    if (in == null) {
                        throw new IllegalStateException("XSD ausente do classpath: " + PASTA_XSD + nome);
                    }
                    Path arquivo = pasta.resolve(nome);
                    Files.copy(in, arquivo, StandardCopyOption.REPLACE_EXISTING);
                    arquivo.toFile().deleteOnExit();
                }
            }
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            schema = factory.newSchema(pasta.resolve("DPS_v1.01.xsd").toFile());
            return schema;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("falha ao carregar o XSD de fevereiro/2026", e);
        }
    }
}
