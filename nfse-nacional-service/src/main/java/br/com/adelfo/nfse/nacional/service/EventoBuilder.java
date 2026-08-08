package br.com.adelfo.nfse.nacional.service;

import br.com.adelfo.nfse.nacional.schemas.ObjectFactory;
import br.com.adelfo.nfse.nacional.schemas.TCInfPedReg;
import br.com.adelfo.nfse.nacional.schemas.TCPedRegEvt;
import br.com.adelfo.nfse.nacional.schemas.TE101101;
import br.com.adelfo.nfse.nacional.schemas.TE202201;
import br.com.adelfo.nfse.nacional.schemas.TE202205;
import br.com.adelfo.nfse.nacional.schemas.TE203202;
import br.com.adelfo.nfse.nacional.schemas.TE203206;
import br.com.adelfo.nfse.nacional.schemas.TE204203;
import br.com.adelfo.nfse.nacional.schemas.TE204207;
import br.com.adelfo.nfse.nacional.service.dto.request.CancelamentoRequest;
import br.com.adelfo.nfse.nacional.service.dto.request.ManifestacaoRequest;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Monta o XML dos pedidos de registro de evento de NFS-e.
 *
 * <p>Cobre o cancelamento ({@code e101101}) e as seis manifestações que um contribuinte pode
 * registrar. Os demais eventos do leiaute são de município ou gerados pelo próprio sistema; a
 * parte genérica {@code infPedReg} é comum a todos.
 */
public final class EventoBuilder {

    /** Versão do leiaute — TVerNFSe aceita apenas "1.00" ou "1.01". */
    public static final String VERSAO_LEIAUTE = "1.01";

    /** Identificação do aplicativo emissor, gravada em verAplic (máx. 20 caracteres). */
    public static final String VER_APLIC = "adelfo-nfse-1.0.0";

    /** Código do evento de cancelamento de NFS-e. */
    public static final String EVENTO_CANCELAMENTO = "101101";

    /** Valor fixo exigido pela enumeração do XSD em TE101101/xDesc. */
    private static final String XDESC_CANCELAMENTO = "Cancelamento de NFS-e";

    /**
     * Formato de dhEvento (TSDateTimeUTC): {@code AAAA-MM-DDThh:mm:ssTZD}.
     * O pattern do XSD <b>não</b> admite fração de segundos — por isso não se usa
     * {@code ISO_OFFSET_DATE_TIME}, que a emite e faria o XML ser rejeitado no schema.
     */
    private static final DateTimeFormatter FORMATO_DATA_HORA =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private EventoBuilder() {
    }

    /**
     * XML (não assinado) do pedido de registro do evento de cancelamento.
     *
     * @param autor inscrição federal já resolvida — CNPJ (14) ou CPF (11). Quem resolve é a
     *              fachada, que conhece o certificado; ver {@code E0812} em CancelamentoRequest
     */
    public static String cancelamento(CancelamentoRequest request, String autor) {
        TE101101 cancelamento = new TE101101();
        cancelamento.setXDesc(XDESC_CANCELAMENTO);
        cancelamento.setCMotivo(request.codigoMotivo());
        cancelamento.setXMotivo(request.descricaoMotivo());

        TCInfPedReg info = new TCInfPedReg();
        info.setId(idPedidoRegistroEvento(request.chaveAcesso(), EVENTO_CANCELAMENTO));
        info.setTpAmb(request.ambiente().getCodigo());
        info.setVerAplic(VER_APLIC);
        info.setDhEvento(OffsetDateTime.now().format(FORMATO_DATA_HORA));
        info.setChNFSe(request.chaveAcesso());
        aplicarAutor(info, autor);
        info.setE101101(cancelamento);

        TCPedRegEvt pedido = new TCPedRegEvt();
        pedido.setVersao(VERSAO_LEIAUTE);
        pedido.setInfPedReg(info);

        return serializar(pedido);
    }

    /**
     * XML (não assinado) do pedido de registro de uma manifestação.
     *
     * <p>Confirmações têm só {@code xDesc}; rejeições acrescentam {@code cMotivo} e, opcionalmente,
     * {@code xMotivo}. O {@code xDesc} é enumeração de valor único por tipo — daí vir do enum e não
     * do chamador.
     *
     * @param autor inscrição federal já resolvida; ver {@code E0812} e {@code E0813}
     */
    public static String manifestacao(ManifestacaoRequest request, String autor) {
        TCInfPedReg info = new TCInfPedReg();
        info.setId(idPedidoRegistroEvento(request.chaveAcesso(), request.tipo().getCodigo()));
        info.setTpAmb(request.ambiente().getCodigo());
        info.setVerAplic(VER_APLIC);
        info.setDhEvento(OffsetDateTime.now().format(FORMATO_DATA_HORA));
        info.setChNFSe(request.chaveAcesso());
        aplicarAutor(info, autor);
        aplicarManifestacao(info, request);

        TCPedRegEvt pedido = new TCPedRegEvt();
        pedido.setVersao(VERSAO_LEIAUTE);
        pedido.setInfPedReg(info);

        return serializar(pedido);
    }

    /**
     * Cada manifestação é um elemento próprio no leiaute — {@code e202201}, {@code e203202} etc. —
     * e não um campo com código. Por isso o switch: o tipo escolhe qual elemento preencher.
     */
    private static void aplicarManifestacao(TCInfPedReg info, ManifestacaoRequest request) {
        String xDesc = request.tipo().getDescricao();
        switch (request.tipo()) {
            case CONFIRMACAO_PRESTADOR -> {
                TE202201 e = new TE202201();
                e.setXDesc(xDesc);
                info.setE202201(e);
            }
            case CONFIRMACAO_TOMADOR -> {
                TE203202 e = new TE203202();
                e.setXDesc(xDesc);
                info.setE203202(e);
            }
            case CONFIRMACAO_INTERMEDIARIO -> {
                TE204203 e = new TE204203();
                e.setXDesc(xDesc);
                info.setE204203(e);
            }
            case REJEICAO_PRESTADOR -> {
                TE202205 e = new TE202205();
                e.setXDesc(xDesc);
                e.setCMotivo(request.codigoMotivo());
                e.setXMotivo(request.descricaoMotivo());
                info.setE202205(e);
            }
            case REJEICAO_TOMADOR -> {
                TE203206 e = new TE203206();
                e.setXDesc(xDesc);
                e.setCMotivo(request.codigoMotivo());
                e.setXMotivo(request.descricaoMotivo());
                info.setE203206(e);
            }
            case REJEICAO_INTERMEDIARIO -> {
                TE204207 e = new TE204207();
                e.setXDesc(xDesc);
                e.setCMotivo(request.codigoMotivo());
                e.setXMotivo(request.descricaoMotivo());
                info.setE204207(e);
            }
        }
    }

    /**
     * CNPJAutor e CPFAutor são exclusivos; o tamanho da inscrição decide qual preencher.
     *
     * <p>Passa por {@code Documentos} como os demais pontos de entrada: este método é alcançável
     * diretamente pela API pública do builder, e deixá-lo sem conferência de DV abriria um caminho
     * por onde documento inválido chegaria à Sefin.
     */
    private static void aplicarAutor(TCInfPedReg info, String autor) {
        if (autor == null || autor.isBlank()) {
            throw new IllegalArgumentException("o autor do evento é obrigatório");
        }
        autor = Documentos.cpfOuCnpj(autor, "autor do evento");
        if (autor.length() == 14) {
            info.setCNPJAutor(autor);
        } else {
            info.setCPFAutor(autor);
        }
    }

    /**
     * Identificador do pedido de registro de evento.
     * Conforme TSIdPedRegEvt: {@code "PRE" + chave de acesso (50) + tipo do evento (6)}.
     */
    public static String idPedidoRegistroEvento(String chaveAcesso, String tipoEvento) {
        return "PRE" + chaveAcesso + tipoEvento;
    }

    private static String serializar(TCPedRegEvt pedido) {
        return MarshallerNfse.paraXml(new ObjectFactory().createPedRegEvento(pedido));
    }
}
