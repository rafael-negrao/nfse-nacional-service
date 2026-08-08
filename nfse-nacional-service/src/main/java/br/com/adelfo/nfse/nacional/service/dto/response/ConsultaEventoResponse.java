package br.com.adelfo.nfse.nacional.service.dto.response;

/**
 * Evento recuperado da Sefin.
 *
 * @param chaveAcesso chave da NFS-e consultada
 * @param tipoEvento  código do evento consultado
 * @param sequencial  número sequencial consultado
 * @param xmlEvento   XML do evento, já decodificado de GZip+Base64; {@code null} se não houver
 */
public record ConsultaEventoResponse(String chaveAcesso,
                                     String tipoEvento,
                                     int sequencial,
                                     String xmlEvento) {

    /** {@code true} quando a Sefin devolveu o evento. */
    public boolean encontrado() {
        return xmlEvento != null;
    }
}
