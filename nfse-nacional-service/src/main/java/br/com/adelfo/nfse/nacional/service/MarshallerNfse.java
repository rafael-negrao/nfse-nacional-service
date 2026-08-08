package br.com.adelfo.nfse.nacional.service;

import br.com.adelfo.nfse.nacional.schemas.ObjectFactory;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import org.glassfish.jaxb.runtime.marshaller.NamespacePrefixMapper;

import java.io.StringWriter;

/**
 * Serializa os documentos fiscais em XML com prefixos de namespace <b>determinísticos</b>.
 *
 * <p>Sem isso, o JAXB escolhe os prefixos por conta própria e a escolha varia entre execuções: ora
 * o namespace da NFS-e sai como default e o XMLDSig como {@code ns2}, ora o inverso —
 * {@code <ns2:DPS xmlns="http://www.w3.org/2000/09/xmldsig#" …>}. O XMLDSig aparece no conjunto de
 * namespaces porque {@code TCDPS} e {@code TCPedRegEvt} declaram o elemento {@code ds:Signature},
 * mesmo quando o documento ainda não está assinado.
 *
 * <p>Duas consequências, ambas ruins:
 * <ul>
 *   <li>a assinatura é calculada sobre a forma canônica do XML, e prefixos instáveis produzem
 *       contextos de canonicalização diferentes a cada execução;</li>
 *   <li>o {@code XmlSigningService} remove as declarações XMLDSig do elemento raiz antes de
 *       assinar, e uma declaração <i>default</i> ({@code xmlns=}) apontando para o XMLDSig não é a
 *       mesma coisa que uma prefixada — tratar só uma delas deixa um buraco.</li>
 * </ul>
 *
 * <p>Aqui o namespace da NFS-e é fixado como default e o XMLDSig recebe o prefixo {@code ds}, que
 * é a forma esperada nos documentos fiscais brasileiros.
 */
final class MarshallerNfse {

    static final String NS_NFSE = "http://www.sped.fazenda.gov.br/nfse";
    static final String NS_XMLDSIG = "http://www.w3.org/2000/09/xmldsig#";

    /** JAXBContext é caro de criar e thread-safe — uma instância para todo o processo. */
    private static final JAXBContext CONTEXTO;

    static {
        try {
            CONTEXTO = JAXBContext.newInstance(ObjectFactory.class);
        } catch (JAXBException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static final NamespacePrefixMapper PREFIXOS = new NamespacePrefixMapper() {
        @Override
        public String getPreferredPrefix(String namespaceUri, String sugestao, boolean exigePrefixo) {
            if (NS_NFSE.equals(namespaceUri)) {
                return "";
            }
            if (NS_XMLDSIG.equals(namespaceUri)) {
                return "ds";
            }
            return sugestao;
        }
    };

    private MarshallerNfse() {
    }

    static String paraXml(JAXBElement<?> elemento) {
        try {
            Marshaller marshaller = CONTEXTO.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.TRUE);
            marshaller.setProperty(Marshaller.JAXB_ENCODING, "UTF-8");
            // Propriedade específica do runtime JAXB da Glassfish, que é o que roda aqui. Se um dia
            // o runtime mudar, esta chamada estoura em vez de degradar em silêncio para prefixos
            // instáveis — falhar alto é o comportamento certo num documento assinado.
            marshaller.setProperty("org.glassfish.jaxb.namespacePrefixMapper", PREFIXOS);

            StringWriter writer = new StringWriter();
            marshaller.marshal(elemento, writer);
            return writer.toString();
        } catch (JAXBException e) {
            throw new IllegalStateException(
                    "Falha ao serializar " + elemento.getName().getLocalPart(), e);
        }
    }
}
