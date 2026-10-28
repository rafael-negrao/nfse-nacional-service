package br.com.adelfo.nfse.nacional.danfse.internal;

import br.com.adelfo.nfse.nacional.danfse.DanfseException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Leitura do XML da NFS-e pelos caminhos que a NT 008 usa ({@code infNFSe/DPS/infDPS/prest/CNPJ}).
 *
 * <p>Navega por <b>nome local</b>, ignorando namespace, e não por JAXB: o DANFSe precisa
 * imprimir notas de qualquer versão do leiaute. As emitidas antes da NT 009/2026 trazem o
 * {@code finNFSe} dentro de {@code IBSCBS}; as novas, na raiz da {@code infDPS}. Com as classes
 * geradas de um único XSD, metade delas perderia campos em silêncio.
 */
public final class XmlNfse {

    private final Element infNfse;

    private XmlNfse(Element infNfse) {
        this.infNfse = infNfse;
    }

    public static XmlNfse ler(String xml) {
        if (xml == null || xml.isBlank()) {
            throw new DanfseException("informe o XML da NFS-e");
        }
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            // XML vindo de fora: sem DTD nem entidades externas.
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            Document doc = f.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
            Element raiz = doc.getDocumentElement();
            Element inf = "infNFSe".equals(raiz.getLocalName()) ? raiz : filho(raiz, "infNFSe");
            if (inf == null) {
                throw new DanfseException("o XML não é de uma NFS-e: falta o elemento infNFSe (raiz: "
                        + raiz.getLocalName() + ")");
            }
            return new XmlNfse(inf);
        } catch (DanfseException e) {
            throw e;
        } catch (Exception e) {
            throw new DanfseException("XML da NFS-e ilegível", e);
        }
    }

    /** {@code Id} da {@code infNFSe}: {@code "NFS"} + chave de acesso. */
    public String id() {
        String id = infNfse.getAttribute("Id");
        return id.isBlank() ? null : id;
    }

    /**
     * Texto do elemento no caminho, relativo a {@code infNFSe}; {@code null} se ausente ou vazio.
     * Em elementos repetidos, vale o primeiro.
     */
    public String texto(String caminho) {
        Element e = elemento(caminho);
        if (e == null) {
            return null;
        }
        String t = e.getTextContent();
        return t == null || t.isBlank() ? null : t.trim();
    }

    /** O primeiro caminho que tiver texto. */
    public String primeiro(String... caminhos) {
        for (String c : caminhos) {
            String t = texto(c);
            if (t != null) {
                return t;
            }
        }
        return null;
    }

    /** {@code true} quando o elemento existe, mesmo vazio — para grupos como {@code toma}. */
    public boolean existe(String caminho) {
        return elemento(caminho) != null;
    }

    /** Textos de todos os elementos com o último nome do caminho, sob o penúltimo. */
    public List<String> textos(String caminho) {
        int corte = caminho.lastIndexOf('/');
        Element pai = corte < 0 ? infNfse : elemento(caminho.substring(0, corte));
        String nome = caminho.substring(corte + 1);
        List<String> valores = new ArrayList<>();
        if (pai == null) {
            return valores;
        }
        for (Node n = pai.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && nome.equals(e.getLocalName())) {
                String t = e.getTextContent();
                if (t != null && !t.isBlank()) {
                    valores.add(t.trim());
                }
            }
        }
        return valores;
    }

    private Element elemento(String caminho) {
        Element atual = infNfse;
        for (String parte : caminho.split("/")) {
            atual = filho(atual, parte);
            if (atual == null) {
                return null;
            }
        }
        return atual;
    }

    private static Element filho(Element pai, String nome) {
        for (Node n = pai.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && nome.equals(e.getLocalName())) {
                return e;
            }
        }
        return null;
    }
}
