package br.com.adelfo.nfse.nacional.service;

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
import br.com.adelfo.nfse.nacional.service.dto.response.ConsultaEventoResponse;
import br.com.adelfo.nfse.nacional.service.dto.response.ConsultaEventosResponse;
import br.com.adelfo.nfse.nacional.service.dto.response.ConsultaNfseResponse;
import br.com.adelfo.nfse.nacional.service.dto.response.EmissaoResponse;
import br.com.adelfo.nfse.nacional.service.dto.response.ManifestacaoResponse;

/**
 * Fachada da API Sefin Nacional NFS-e.
 *
 * <p>Todas as operações usam mTLS com o {@code CertificadoDigital} fornecido via {@code @Bean} e
 * são <b>síncronas</b> — não existe o par autorização/recibo da NF-e.
 */
public interface NfseService {

    /**
     * Emite a NFS-e a partir da DPS ({@code POST /nfse}).
     *
     * <p>A DPS é assinada, comprimida em GZip+Base64 e enviada. Se a DPS referenciar a chave de
     * acesso de uma NFS-e existente, o sistema gera o evento {@code e105102} (Cancelamento por
     * Substituição), cancela a original e devolve a NFS-e substituta.
     *
     * @throws br.com.adelfo.nfse.nacional.service.exception.NfseException se a DPS for rejeitada
     */
    EmissaoResponse emitir(EmissaoRequest request);

    /**
     * Emite a NFS-e pelo fluxo de decisão administrativa ou judicial
     * ({@code POST /decisao-judicial/nfse}).
     *
     * <p>Só funciona se o município tiver cadastrado a decisão na plataforma e autorizado o
     * contribuinte a usar este fluxo. O documento enviado é a NFS-e completa, e a Sefin aplica
     * apenas validações mínimas — <b>a responsabilidade pelo conteúdo é do contribuinte</b>.
     *
     * @throws br.com.adelfo.nfse.nacional.service.exception.NfseException se for rejeitada
     */
    EmissaoResponse emitirPorDecisaoJudicial(EmissaoDecisaoJudicialRequest request);

    /**
     * Consulta a NFS-e pela chave de acesso ({@code GET /nfse/{chaveAcesso}}).
     */
    ConsultaNfseResponse consultarPorChave(ConsultaNfseRequest request);

    /**
     * Recupera a chave de acesso da NFS-e a partir do identificador da DPS
     * ({@code GET /dps/{id}}, com fallback para {@code HEAD /dps/{id}} quando o sigilo fiscal
     * impede a divulgação da chave).
     */
    ConsultaDpsResponse consultarPorIdDps(ConsultaDpsRequest request);

    /**
     * Consulta um evento específico pela chave, tipo e sequencial
     * ({@code GET /nfse/{chaveAcesso}/eventos/{tipoEvento}/{numSeqEvento}}).
     *
     * <p>Não lança quando o evento não existe: devolve
     * {@link ConsultaEventoResponse#encontrado()} falso.
     */
    ConsultaEventoResponse consultarEvento(ConsultaEventoRequest request);

    /**
     * Lista todos os eventos vinculados a uma NFS-e
     * ({@code GET <adn>/contribuintes/NFSe/{chaveAcesso}/Eventos}).
     *
     * <p>Única operação da fachada servida pelo ADN, e não pela Sefin.
     */
    ConsultaEventosResponse consultarEventos(ConsultaEventosRequest request);

    /**
     * Distribuição de DF-e a partir de um NSU
     * ({@code GET <adn>/contribuintes/DFe/{NSU}}).
     *
     * <p><b>É o único jeito de descobrir notas sem já conhecer a chave.</b> Não há consulta por
     * período no Sistema Nacional; varre-se a numeração sequencial e filtra-se por data no
     * chamador. Ver o padrão de varredura na Javadoc de {@link ConsultaDFeResponse}.
     */
    ConsultaDFeResponse consultarDFe(ConsultaDFeRequest request);

    /**
     * Registra uma manifestação sobre a NFS-e — confirmação ou rejeição pelo prestador, tomador ou
     * intermediário ({@code POST /nfse/{chaveAcesso}/eventos}).
     *
     * <p>Vai pela mesma rota do cancelamento: o manual descreve-a como "modelo genérico que
     * permite o registro de eventos originados a partir de: Emitentes da NFS-e; <b>Não Emitentes
     * da NFS-e</b>; …". Cada manifestação é única por autor ({@code E1833}).
     *
     * @throws br.com.adelfo.nfse.nacional.service.exception.NfseException se for rejeitada
     */
    ManifestacaoResponse manifestar(ManifestacaoRequest request);

    /**
     * Cancela a NFS-e via evento {@code e101101} ({@code POST /nfse/{chaveAcesso}/eventos}).
     *
     * @throws br.com.adelfo.nfse.nacional.service.exception.NfseException se o pedido for rejeitado
     */
    CancelamentoResponse cancelar(CancelamentoRequest request);
}
