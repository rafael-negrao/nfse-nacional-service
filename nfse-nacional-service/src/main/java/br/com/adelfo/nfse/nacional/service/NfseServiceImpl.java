package br.com.adelfo.nfse.nacional.service;

import br.com.adelfo.nfse.nacional.client.CertificadoDigital;
import br.com.adelfo.nfse.nacional.client.GZipBase64;
import br.com.adelfo.nfse.nacional.client.NfseHttpClient;
import br.com.adelfo.nfse.nacional.client.NfseHttpException;
import br.com.adelfo.nfse.nacional.client.TipoAmbiente;
import br.com.adelfo.nfse.nacional.service.dto.request.CancelamentoRequest;
import br.com.adelfo.nfse.nacional.service.dto.request.ConsultaDFeRequest;
import br.com.adelfo.nfse.nacional.service.dto.request.ConsultaDpsRequest;
import br.com.adelfo.nfse.nacional.service.dto.request.ConsultaEventoRequest;
import br.com.adelfo.nfse.nacional.service.dto.request.ConsultaEventosRequest;
import br.com.adelfo.nfse.nacional.service.dto.request.ConsultaNfseRequest;
import br.com.adelfo.nfse.nacional.service.dto.request.EmissaoDecisaoJudicialRequest;
import br.com.adelfo.nfse.nacional.service.dto.request.EmissaoRequest;
import br.com.adelfo.nfse.nacional.service.dto.request.ManifestacaoRequest;
import br.com.adelfo.nfse.nacional.service.dto.response.CancelamentoResponse;
import br.com.adelfo.nfse.nacional.service.dto.response.ConsultaDFeResponse;
import br.com.adelfo.nfse.nacional.service.dto.response.ConsultaDpsResponse;
import br.com.adelfo.nfse.nacional.service.dto.response.DocumentoDistribuido;
import br.com.adelfo.nfse.nacional.service.dto.response.StatusDistribuicao;
import br.com.adelfo.nfse.nacional.service.dto.response.ConsultaEventoResponse;
import br.com.adelfo.nfse.nacional.service.dto.response.ConsultaEventosResponse;
import br.com.adelfo.nfse.nacional.service.dto.response.ConsultaNfseResponse;
import br.com.adelfo.nfse.nacional.service.dto.response.EmissaoResponse;
import br.com.adelfo.nfse.nacional.service.dto.response.ManifestacaoResponse;
import br.com.adelfo.nfse.nacional.service.exception.NfseException;
import br.com.adelfo.nfse.nacional.service.signing.XmlSigningService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementação da fachada sobre a API REST da Sefin Nacional.
 *
 * <p>Fluxo de escrita, idêntico para emissão e evento:
 * XML (JAXB ou fornecido) → assinatura XMLDSig → GZip → Base64 → campo string do JSON → POST.
 * A resposta faz o caminho inverso.
 *
 * <p><b>Contrato JSON:</b> os nomes dos campos concentram-se nas constantes
 * {@code CAMPO_*} logo abaixo. Eles vêm da documentação pública e de implementações de terceiros —
 * a Swagger oficial exige certificado para ser lida. Confirme-os na primeira integração em
 * produção restrita; qualquer divergência se corrige aqui, num único ponto.
 */
public class NfseServiceImpl implements NfseService {

    // --- Nomes dos campos JSON — ver nota no Javadoc da classe -------------------------------
    private static final String CAMPO_DPS_ENVIO = "dpsXmlGZipB64";
    private static final String CAMPO_NFSE_RETORNO = "nfseXmlGZipB64";
    /** O bypass usa outro nome de campo: xmlGZipB64, e não dpsXmlGZipB64. */
    private static final String CAMPO_NFSE_ENVIO = "xmlGZipB64";
    private static final String CAMPO_EVENTO_ENVIO = "pedidoRegistroEventoXmlGZipB64";
    private static final String CAMPO_EVENTO_RETORNO = "eventoXmlGZipB64";
    private static final String CAMPO_CHAVE_ACESSO = "chaveAcesso";

    // Campos do lote de eventos do ADN, que usa PascalCase — ao contrário da Sefin, em camelCase.
    private static final String CAMPO_LOTE_DFE = "LoteDFe";
    private static final String CAMPO_ADN_NSU = "NSU";
    private static final String CAMPO_ADN_CHAVE = "ChaveAcesso";
    private static final String CAMPO_ADN_TIPO_EVENTO = "TipoEvento";
    private static final String CAMPO_ADN_XML = "ArquivoXml";
    private static final String CAMPO_ADN_DATA = "DataHoraGeracao";
    private static final String CAMPO_ADN_STATUS = "StatusProcessamento";
    private static final String CAMPO_ADN_TIPO_DOC = "TipoDocumento";
    private static final String CAMPO_ADN_ALERTAS = "Alertas";
    private static final String CAMPO_ADN_ERROS = "Erros";

    private final NfseHttpClient httpClient;
    private final XmlSigningService signingService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final CertificadoDigital certificado;

    public NfseServiceImpl(CertificadoDigital certificado, NfseHttpClient httpClient) {
        this.certificado = certificado;
        this.httpClient = httpClient;
        this.signingService = new XmlSigningService(certificado);
    }

    // -------------------------------------------------------------------------
    // Emissão — POST /nfse
    // -------------------------------------------------------------------------

    @Override
    public EmissaoResponse emitir(EmissaoRequest request) {
        String xmlAssinado = assinar(request.xmlDps(), extrairId(request.xmlDps(), "infDPS"));

        ObjectNode corpo = objectMapper.createObjectNode();
        corpo.put(CAMPO_DPS_ENVIO, GZipBase64.comprimir(xmlAssinado));

        JsonNode resposta = postJson(url(request.ambiente(), "/nfse"), corpo);

        return new EmissaoResponse(
                texto(resposta, CAMPO_CHAVE_ACESSO),
                xmlDeCampoGZip(resposta, CAMPO_NFSE_RETORNO));
    }

    // -------------------------------------------------------------------------
    // Emissão por decisão judicial — POST /decisao-judicial/nfse
    // -------------------------------------------------------------------------

    @Override
    public EmissaoResponse emitirPorDecisaoJudicial(EmissaoDecisaoJudicialRequest request) {
        // Aqui o elemento assinado é o infNFSe da própria nota, não o infDPS embutido.
        String xmlAssinado = assinar(request.xmlNfse(), extrairId(request.xmlNfse(), "infNFSe"));

        ObjectNode corpo = objectMapper.createObjectNode();
        corpo.put(CAMPO_NFSE_ENVIO, GZipBase64.comprimir(xmlAssinado));

        JsonNode resposta = postJson(url(request.ambiente(), "/decisao-judicial/nfse"), corpo);

        return new EmissaoResponse(
                texto(resposta, CAMPO_CHAVE_ACESSO),
                xmlDeCampoGZip(resposta, CAMPO_NFSE_RETORNO));
    }

    // -------------------------------------------------------------------------
    // Consulta por chave de acesso — GET /nfse/{chaveAcesso}
    // -------------------------------------------------------------------------

    @Override
    public ConsultaNfseResponse consultarPorChave(ConsultaNfseRequest request) {
        String url = url(request.ambiente(), "/nfse/" + encode(request.chaveAcesso()));
        JsonNode resposta;
        try {
            resposta = lerJson(httpClient.get(url));
        } catch (NfseHttpException e) {
            // NfseHttpException é contrato do módulo client; nada acima dele deve conhecer HTTP.
            throw traduzir(e);
        }

        return new ConsultaNfseResponse(
                request.chaveAcesso(),
                xmlDeCampoGZip(resposta, CAMPO_NFSE_RETORNO));
    }

    // -------------------------------------------------------------------------
    // Consulta por identificador da DPS — GET /dps/{id}
    // -------------------------------------------------------------------------

    @Override
    public ConsultaDpsResponse consultarPorIdDps(ConsultaDpsRequest request) {
        String url = url(request.ambiente(), "/dps/" + encode(request.idDps()));
        try {
            JsonNode resposta = lerJson(httpClient.get(url));
            return new ConsultaDpsResponse(true, texto(resposta, CAMPO_CHAVE_ACESSO));
        } catch (NfseHttpException e) {
            // 404 é resposta legítima desta rota: não existe NFS-e para a DPS consultada. Consulta
            // devolve estado, não lança — quem lança são apenas as operações de escrita.
            if (e.getStatus() == 404) {
                return new ConsultaDpsResponse(false, null);
            }
            // 403 significa "existe, mas você não é ator da nota". O HEAD é liberado a qualquer
            // certificado válido e responde apenas se a NFS-e foi gerada.
            if (e.getStatus() != 403) {
                throw traduzir(e);
            }
            return new ConsultaDpsResponse(httpClient.head(url) < 400, null);
        }
    }

    // -------------------------------------------------------------------------
    // Consulta de evento — GET /nfse/{chave}/eventos/{tipo}/{nSeq}
    // -------------------------------------------------------------------------

    @Override
    public ConsultaEventoResponse consultarEvento(ConsultaEventoRequest request) {
        String url = url(request.ambiente(), "/nfse/" + encode(request.chaveAcesso())
                + "/eventos/" + encode(request.tipoEvento()) + "/" + request.sequencial());
        try {
            JsonNode resposta = lerJson(httpClient.get(url));
            return new ConsultaEventoResponse(request.chaveAcesso(), request.tipoEvento(),
                    request.sequencial(), xmlDeCampoGZip(resposta, CAMPO_EVENTO_RETORNO));
        } catch (NfseHttpException e) {
            // 404 é resposta prevista na Swagger ("Nenhum evento encontrado para a NFS-e"), não
            // uma falha: consulta devolve estado.
            if (e.getStatus() == 404) {
                return new ConsultaEventoResponse(request.chaveAcesso(), request.tipoEvento(),
                        request.sequencial(), null);
            }
            throw traduzir(e);
        }
    }

    // -------------------------------------------------------------------------
    // Consulta de eventos — GET <adn>/contribuintes/NFSe/{chave}/Eventos
    // -------------------------------------------------------------------------

    @Override
    public ConsultaEventosResponse consultarEventos(ConsultaEventosRequest request) {
        String url = request.ambiente().getUrlAdn()
                + "/contribuintes/NFSe/" + encode(request.chaveAcesso()) + "/Eventos";
        try {
            // Mesmo envelope da distribuição por NSU — a rota reaproveita LoteDistribuicaoNSUResponse.
            return new ConsultaEventosResponse(
                    request.chaveAcesso(), documentosDoLote(lerJson(httpClient.get(url))));
        } catch (NfseHttpException e) {
            if (e.getStatus() == 404) {
                return new ConsultaEventosResponse(request.chaveAcesso(), List.of());
            }
            throw traduzir(e);
        }
    }

    // -------------------------------------------------------------------------
    // Distribuição de DF-e — GET <adn>/contribuintes/DFe/{NSU}
    // -------------------------------------------------------------------------

    @Override
    public ConsultaDFeResponse consultarDFe(ConsultaDFeRequest request) {
        StringBuilder url = new StringBuilder(request.ambiente().getUrlAdn())
                .append("/contribuintes/DFe/").append(request.nsu());
        String separador = "?";
        if (request.lote()) {
            url.append(separador).append("lote=true");
            separador = "&";
        }
        if (request.cnpjConsulta() != null && !request.cnpjConsulta().isBlank()) {
            url.append(separador).append("cnpjConsulta=").append(encode(request.cnpjConsulta()));
        }

        JsonNode resposta;
        try {
            resposta = lerJson(httpClient.get(url.toString()));
        } catch (NfseHttpException e) {
            // 404 aqui é "não há documentos após este NSU" — fim da varredura, não falha.
            if (e.getStatus() == 404) {
                return new ConsultaDFeResponse(
                        StatusDistribuicao.NENHUM_DOCUMENTO_LOCALIZADO, List.of(), List.of(), List.of());
            }
            throw traduzir(e);
        }

        return new ConsultaDFeResponse(
                StatusDistribuicao.de(texto(resposta, CAMPO_ADN_STATUS)),
                documentosDoLote(resposta),
                mensagens(resposta, CAMPO_ADN_ALERTAS),
                mensagens(resposta, CAMPO_ADN_ERROS));
    }

    /**
     * Lê o {@code LoteDFe} do envelope do ADN, compartilhado pela distribuição por NSU e pela
     * consulta de eventos por chave. Os campos vêm em PascalCase, ao contrário da Sefin.
     */
    private static List<DocumentoDistribuido> documentosDoLote(JsonNode resposta) {
        JsonNode lote = resposta.get(CAMPO_LOTE_DFE);
        if (lote == null || !lote.isArray()) {
            return List.of();
        }
        List<DocumentoDistribuido> documentos = new ArrayList<>();
        for (JsonNode item : lote) {
            documentos.add(new DocumentoDistribuido(
                    item.path(CAMPO_ADN_NSU).asLong(),
                    texto(item, CAMPO_ADN_CHAVE),
                    texto(item, CAMPO_ADN_TIPO_DOC),
                    // TipoEvento só existe nos itens de evento; a NFS-e do lote não traz o campo.
                    texto(item, CAMPO_ADN_TIPO_EVENTO),
                    xmlDeCampoGZip(item, CAMPO_ADN_XML),
                    texto(item, CAMPO_ADN_DATA)));
        }
        return documentos;
    }

    /** Achata um array de MensagemProcessamento em textos legíveis. */
    private static List<String> mensagens(JsonNode raiz, String campo) {
        JsonNode array = raiz.get(campo);
        if (array == null || !array.isArray()) {
            return List.of();
        }
        List<String> textos = new ArrayList<>();
        for (JsonNode m : array) {
            String codigo = texto(m, "Codigo");
            String descricao = texto(m, "Descricao");
            textos.add(codigo == null ? String.valueOf(descricao) : "[" + codigo + "] " + descricao);
        }
        return textos;
    }

    // -------------------------------------------------------------------------
    // Manifestação — POST /nfse/{chaveAcesso}/eventos (eventos 2xxxxx)
    // -------------------------------------------------------------------------

    @Override
    public ManifestacaoResponse manifestar(ManifestacaoRequest request) {
        String autor = autorDoEvento(request.autor());
        String xmlPedido = EventoBuilder.manifestacao(request, autor);
        String idPedido = EventoBuilder.idPedidoRegistroEvento(
                request.chaveAcesso(), request.tipo().getCodigo());

        JsonNode resposta = enviarEvento(request.ambiente(), request.chaveAcesso(),
                assinar(xmlPedido, idPedido));

        return new ManifestacaoResponse(request.chaveAcesso(), request.tipo(),
                xmlDeCampoGZip(resposta, CAMPO_EVENTO_RETORNO));
    }

    // -------------------------------------------------------------------------
    // Cancelamento — POST /nfse/{chaveAcesso}/eventos (evento e101101)
    // -------------------------------------------------------------------------

    @Override
    public CancelamentoResponse cancelar(CancelamentoRequest request) {
        String xmlPedido = EventoBuilder.cancelamento(request, autorDoEvento(request.autor()));
        String idPedido = EventoBuilder.idPedidoRegistroEvento(
                request.chaveAcesso(), EventoBuilder.EVENTO_CANCELAMENTO);

        JsonNode resposta = enviarEvento(request.ambiente(), request.chaveAcesso(),
                assinar(xmlPedido, idPedido));

        return new CancelamentoResponse(
                request.chaveAcesso(),
                xmlDeCampoGZip(resposta, CAMPO_EVENTO_RETORNO));
    }

    /** Envio comum a todos os pedidos de registro de evento. */
    private JsonNode enviarEvento(TipoAmbiente ambiente, String chaveAcesso, String xmlAssinado) {
        ObjectNode corpo = objectMapper.createObjectNode();
        corpo.put(CAMPO_EVENTO_ENVIO, GZipBase64.comprimir(xmlAssinado));
        return postJson(url(ambiente, "/nfse/" + encode(chaveAcesso) + "/eventos"), corpo);
    }

    /**
     * Inscrição federal que vai em {@code CNPJAutor}/{@code CPFAutor}. Sem autor explícito, usa a
     * do certificado — que é o que a regra {@code E0812} exige na esmagadora maioria dos casos.
     */
    private String autorDoEvento(String autorInformado) {
        if (autorInformado != null) {
            return autorInformado;
        }
        try {
            return certificado.inscricaoFederal();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Não foi possível determinar o autor do evento a partir do certificado. "
                            + "Informe-o com CancelamentoRequest.comAutor(...).", e);
        }
    }

    // -------------------------------------------------------------------------
    // Infraestrutura compartilhada
    // -------------------------------------------------------------------------

    private static String url(TipoAmbiente ambiente, String caminho) {
        return ambiente.getUrlSefinNacional() + caminho;
    }

    private static String encode(String valor) {
        return URLEncoder.encode(valor, StandardCharsets.UTF_8);
    }

    private String assinar(String xml, String id) {
        try {
            byte[] assinado = signingService.assinar(xml.getBytes(StandardCharsets.UTF_8), "#" + id);
            return new String(assinado, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao assinar o XML (Id=" + id + ")", e);
        }
    }

    /** Lê o atributo {@code Id} do elemento indicado, no XML fornecido pelo consumidor. */
    private static String extrairId(String xml, String elemento) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            Document doc = dbf.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            Element alvo = (Element) doc.getElementsByTagNameNS("*", elemento).item(0);
            if (alvo == null || !alvo.hasAttribute("Id")) {
                throw new IllegalArgumentException(
                        "XML não possui " + elemento + " com atributo Id");
            }
            return alvo.getAttribute("Id");
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("XML inválido", e);
        }
    }

    private JsonNode postJson(String url, ObjectNode corpo) {
        String json;
        try {
            json = objectMapper.writeValueAsString(corpo);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao serializar o corpo JSON", e);
        }
        try {
            return lerJson(httpClient.post(url, json));
        } catch (NfseHttpException e) {
            throw traduzir(e);
        }
    }

    private JsonNode lerJson(String corpo) {
        try {
            return objectMapper.readTree(corpo);
        } catch (Exception e) {
            throw new IllegalStateException("Resposta não é JSON válido: " + corpo, e);
        }
    }

    private static String texto(JsonNode no, String campo) {
        JsonNode valor = no.get(campo);
        return valor == null || valor.isNull() ? null : valor.asText();
    }

    private static String xmlDeCampoGZip(JsonNode no, String campo) {
        String valor = texto(no, campo);
        return valor == null ? null : GZipBase64.descomprimir(valor);
    }

    private NfseException traduzir(NfseHttpException e) {
        return RespostaDeErro.traduzir(e);
    }
}
