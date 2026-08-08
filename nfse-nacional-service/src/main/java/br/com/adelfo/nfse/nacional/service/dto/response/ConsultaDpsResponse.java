package br.com.adelfo.nfse.nacional.service.dto.response;

/**
 * Resultado da consulta por identificador da DPS.
 *
 * @param gerada      indica se existe NFS-e gerada para a DPS consultada
 * @param chaveAcesso chave de acesso da NFS-e; {@code null} quando o solicitante não é ator da
 *                    nota e o sigilo fiscal impede a divulgação
 */
public record ConsultaDpsResponse(boolean gerada, String chaveAcesso) {
}
