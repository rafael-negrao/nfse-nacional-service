package br.com.adelfo.nfse.nacional.service;

import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Valida XML contra os XSDs oficiais empacotados em {@code nfse-nacional-schemas}.
 *
 * <p>Os XSDs se referenciam por caminho relativo ({@code schemaLocation="xmldsig-core-schema.xsd"}).
 * Resolver isso a partir de uma URL {@code jar:file:...!/} — o que acontece assim que o módulo de
 * schemas é empacotado no mesmo reator — falha de forma intermitente com
 * {@code src-resolve: ds:Signature}. Tentar contornar com um {@code LSResourceResolver} de
 * classpath não resolve: o Xerces indexa os documentos por systemId, e systemIds sintéticos
 * confundem a resolução dos imports aninhados.
 *
 * <p>A saída é copiar o conjunto de XSDs para um diretório temporário uma única vez e validar a
 * partir de arquivos reais. Assim a resolução relativa funciona exatamente como os autores do
 * schema previram, sem depender de como o módulo entrou no classpath.
 */
final class ValidacaoXsd {

    /** Conjunto completo de XSDs do pacote oficial v1.01 que o módulo de schemas publica. */
    private static final List<String> XSDS = List.of(
            "DPS_v1.01.xsd",
            "NFSe_v1.01.xsd",
            "evento_v1.01.xsd",
            "pedRegEvento_v1.01.xsd",
            "tiposComplexos_v1.01.xsd",
            "tiposEventos_v1.01.xsd",
            "tiposSimples_v1.01.xsd",
            "xmldsig-core-schema.xsd");

    private static Path diretorio;

    private ValidacaoXsd() {
    }

    /** Todos os erros de schema do XML; lista vazia quando o documento é válido. */
    static List<String> erros(String nomeXsd, String xml) throws Exception {
        List<String> erros = new ArrayList<>();

        Validator validator = validador(nomeXsd);
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
            erros.add(e.getMessage());
        }
        return erros;
    }

    private static Validator validador(String nomeXsd) throws Exception {
        Path xsd = diretorioDeSchemas().resolve(nomeXsd);
        if (!Files.exists(xsd)) {
            throw new IllegalArgumentException("XSD desconhecido: " + nomeXsd + " — acrescente-o a XSDS");
        }
        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        Schema schema = factory.newSchema(xsd.toFile());
        return schema.newValidator();
    }

    private static synchronized Path diretorioDeSchemas() throws Exception {
        if (diretorio != null) {
            return diretorio;
        }
        Path destino = Files.createTempDirectory("nfse-xsd");
        destino.toFile().deleteOnExit();
        for (String nome : XSDS) {
            try (InputStream in = ValidacaoXsd.class.getResourceAsStream("/xsd/" + nome)) {
                if (in == null) {
                    throw new IllegalStateException("XSD ausente no classpath: /xsd/" + nome);
                }
                Path arquivo = destino.resolve(nome);
                Files.copy(in, arquivo, StandardCopyOption.REPLACE_EXISTING);
                arquivo.toFile().deleteOnExit();
            }
        }
        diretorio = destino;
        return diretorio;
    }
}
