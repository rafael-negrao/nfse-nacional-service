package br.com.adelfo.nfse.nacional.service.signing;

import br.com.adelfo.nfse.nacional.client.CertificadoDigital;
import org.apache.xml.security.Init;
import org.apache.xml.security.signature.XMLSignature;
import org.apache.xml.security.transforms.Transforms;
import org.apache.xml.security.utils.Constants;
import org.apache.xml.security.utils.ElementProxy;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

/**
 * Assinatura XMLDSig Enveloped da DPS e do pedido de registro de evento.
 *
 * <p>Algoritmo: RSA-SHA1, digest SHA-1, canonicalização C14N sem comentários — o padrão dos
 * documentos fiscais brasileiros. A referência aponta para o atributo {@code Id} do elemento a
 * assinar ({@code infDPS} na DPS, {@code infPedReg} no pedido de evento).
 *
 * <p>Os três tratamentos abaixo foram herdados de {@code nfe-sefaz-sp}, onde cada um resolve uma
 * rejeição concreta do fisco. O leiaute da NFS-e Nacional tem a mesma anatomia (JAXB + xmldsig
 * importado no XSD + atributo {@code Id} não tipado como {@code xsd:ID}), então os mesmos
 * problemas se aplicam.
 */
public class XmlSigningService {

    static {
        Init.init();
        // O elemento <Signature> deve sair sem prefixo (xmlns default), não como <ds:Signature>.
        // Por padrão o Santuario usa "ds"; forçar prefixo vazio para o namespace XMLDSig.
        try {
            ElementProxy.setDefaultPrefix(Constants.SignatureSpecNS, "");
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final CertificadoDigital certificado;

    public XmlSigningService(CertificadoDigital certificado) {
        this.certificado = certificado;
    }

    /**
     * Assina o elemento identificado por {@code referenceUri} no documento XML fornecido.
     *
     * @param xmlBytes     bytes do XML a assinar (UTF-8)
     * @param referenceUri valor do atributo {@code Id} precedido de "#" (ex.: {@code "#DPS35..."})
     * @return bytes do XML assinado
     */
    public byte[] assinar(byte[] xmlBytes, String referenceUri) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        Document doc = dbf.newDocumentBuilder().parse(new ByteArrayInputStream(xmlBytes));

        // O JAXB declara xmlns:ns2="...xmldsig#" no elemento raiz porque o XSD da NFS-e importa o
        // XMLDSig. O Santuario depois adiciona xmlns:ds="..." no <Signature>. Dois prefixos para o
        // mesmo namespace entram no contexto de C14N, produzindo canonicalização assimétrica entre
        // o elemento assinado e o <SignedInfo> — assinatura inválida. Remover antes de assinar.
        removerDeclaracoesXmldsig(doc.getDocumentElement());

        // O JAXB serializa "Id" como atributo comum; o resolvedor de fragmento do XMLDSig usa
        // document.getElementById(), que só enxerga atributos do tipo xsd:ID. Registrar
        // explicitamente evita "Cannot resolve element with ID" em runtime.
        registrarAtributosId(doc.getDocumentElement());

        KeyStore keyStore = certificado.toKeyStore();
        String alias = keyStore.aliases().nextElement();
        PrivateKey privateKey = (PrivateKey) keyStore.getKey(alias, certificado.getSenha());
        X509Certificate cert = (X509Certificate) keyStore.getCertificate(alias);

        XMLSignature sig = new XMLSignature(doc, "",
                XMLSignature.ALGO_ID_SIGNATURE_RSA_SHA1,
                Transforms.TRANSFORM_C14N_OMIT_COMMENTS);

        doc.getDocumentElement().appendChild(sig.getElement());

        Transforms transforms = new Transforms(doc);
        transforms.addTransform(Transforms.TRANSFORM_ENVELOPED_SIGNATURE);
        transforms.addTransform(Transforms.TRANSFORM_C14N_OMIT_COMMENTS);

        sig.addDocument(referenceUri, transforms, Constants.ALGO_ID_DIGEST_SHA1);
        sig.addKeyInfo(cert);

        // O Santuario formata os elementos que cria com quebras de linha. Espaço em branco entre
        // tags é rejeitado pelo fisco; remover ANTES de assinar para que o <SignedInfo>
        // canonicalizado (base da assinatura) também fique sem esses nós.
        removerEspacosEmBranco(sig.getElement());

        sig.sign(privateKey);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        TransformerFactory.newInstance().newTransformer()
                .transform(new DOMSource(doc), new StreamResult(out));
        return out.toByteArray();
    }

    /**
     * Remove do elemento raiz toda declaração de namespace que aponte para o XMLDSig — tanto as
     * prefixadas ({@code xmlns:ds=}) quanto a default ({@code xmlns=}).
     *
     * <p>A default importa: o XML pode chegar de um consumidor que o gerou com outro marshaller.
     * O {@link br.com.adelfo.nfse.nacional.service.MarshallerNfse} desta biblioteca fixa o
     * namespace da NFS-e como default justamente para que esse caso não apareça, mas a assinatura
     * não pode depender de quem montou o documento.
     */
    private static void removerDeclaracoesXmldsig(Element root) {
        NamedNodeMap attrs = root.getAttributes();
        List<String> remover = new ArrayList<>();
        for (int i = 0; i < attrs.getLength(); i++) {
            Attr attr = (Attr) attrs.item(i);
            boolean ehDeclaracaoDeNamespace =
                    attr.getName().startsWith("xmlns:") || "xmlns".equals(attr.getName());
            if (ehDeclaracaoDeNamespace && Constants.SignatureSpecNS.equals(attr.getValue())) {
                remover.add(attr.getName());
            }
        }
        for (String nome : remover) {
            root.removeAttribute(nome);
        }
    }

    /** Remove recursivamente nós de texto compostos apenas por espaço em branco. */
    private static void removerEspacosEmBranco(Node node) {
        NodeList filhos = node.getChildNodes();
        for (int i = filhos.getLength() - 1; i >= 0; i--) {
            Node filho = filhos.item(i);
            if (filho.getNodeType() == Node.TEXT_NODE && filho.getTextContent().trim().isEmpty()) {
                node.removeChild(filho);
            } else if (filho.getNodeType() == Node.ELEMENT_NODE) {
                removerEspacosEmBranco(filho);
            }
        }
    }

    /** Registra todo atributo chamado "Id" como atributo do tipo XML ID. */
    private static void registrarAtributosId(Element element) {
        if (element.hasAttribute("Id")) {
            element.setIdAttribute("Id", true);
        }
        NodeList filhos = element.getChildNodes();
        for (int i = 0; i < filhos.getLength(); i++) {
            Node filho = filhos.item(i);
            if (filho.getNodeType() == Node.ELEMENT_NODE) {
                registrarAtributosId((Element) filho);
            }
        }
    }
}
