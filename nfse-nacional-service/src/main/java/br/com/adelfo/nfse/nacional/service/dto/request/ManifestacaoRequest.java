package br.com.adelfo.nfse.nacional.service.dto.request;

import br.com.adelfo.nfse.nacional.client.TipoAmbiente;
import br.com.adelfo.nfse.nacional.service.Documentos;

/**
 * Manifestação do prestador, tomador ou intermediário sobre uma NFS-e.
 *
 * <p>Como no cancelamento, o autor sai do certificado — a regra {@code E0812} exige que ele
 * corresponda ao titular que assina, e {@code E0813} exige ainda que seja um dos atores previstos
 * para aquele evento na planilha "Tipo Eventos".
 *
 * @param ambiente        ambiente alvo
 * @param chaveAcesso     chave de acesso da NFS-e (50 dígitos)
 * @param tipo            qual manifestação registrar
 * @param codigoMotivo    só nas rejeições: {@code 1} duplicidade, {@code 2} nota já emitida pelo
 *                        tomador, {@code 3} não ocorrência do fato gerador, {@code 4} erro de
 *                        responsabilidade tributária, {@code 5} erro de valor, deduções, serviço ou
 *                        data, {@code 9} outros
 * @param descricaoMotivo descrição do motivo, de 15 a 255 caracteres. Opcional no leiaute das
 *                        rejeições, mas <b>obrigatória quando o código é 9</b> ({@code E1944})
 * @param autor           inscrição federal do autor, ou {@code null} para usar a do certificado
 */
public record ManifestacaoRequest(TipoAmbiente ambiente,
                                  String chaveAcesso,
                                  TipoManifestacao tipo,
                                  String codigoMotivo,
                                  String descricaoMotivo,
                                  String autor) {

    public ManifestacaoRequest {
        if (ambiente == null) throw new IllegalArgumentException("ambiente é obrigatório");
        if (chaveAcesso == null || chaveAcesso.isBlank())
            throw new IllegalArgumentException("chaveAcesso é obrigatória");
        if (tipo == null) throw new IllegalArgumentException("tipo de manifestação é obrigatório");
        chaveAcesso = Documentos.chaveAcesso(chaveAcesso, "chaveAcesso");
        autor = Documentos.cpfOuCnpj(autor, "autor");

        if (tipo.ehRejeicao()) {
            if (codigoMotivo == null || !codigoMotivo.matches("[12345 9]".replace(" ", ""))) {
                throw new IllegalArgumentException(
                        "codigoMotivo da rejeição deve ser 1, 2, 3, 4, 5 ou 9; recebido: " + codigoMotivo);
            }
            // E1944: com o código 9 - Outros, a descrição passa a ser obrigatória.
            if ("9".equals(codigoMotivo) && (descricaoMotivo == null || descricaoMotivo.isBlank())) {
                throw new IllegalArgumentException(
                        "com codigoMotivo 9 (Outros), descricaoMotivo é obrigatória (E1944)");
            }
        } else if (codigoMotivo != null || descricaoMotivo != null) {
            throw new IllegalArgumentException("confirmação não aceita motivo — o leiaute só tem xDesc");
        }

        if (descricaoMotivo != null
                && (descricaoMotivo.strip().length() < 15 || descricaoMotivo.strip().length() > 255)) {
            throw new IllegalArgumentException(
                    "descricaoMotivo deve ter de 15 a 255 caracteres; recebido: "
                            + descricaoMotivo.strip().length());
        }
    }

    /** Confirmação, com o autor tirado do certificado. */
    public static ManifestacaoRequest confirmacao(TipoAmbiente ambiente, String chaveAcesso,
                                                  TipoManifestacao tipo) {
        if (tipo.ehRejeicao()) {
            throw new IllegalArgumentException(tipo + " é rejeição; use ManifestacaoRequest.rejeicao(...)");
        }
        return new ManifestacaoRequest(ambiente, chaveAcesso, tipo, null, null, null);
    }

    /** Rejeição, com o autor tirado do certificado. */
    public static ManifestacaoRequest rejeicao(TipoAmbiente ambiente, String chaveAcesso,
                                               TipoManifestacao tipo, String codigoMotivo,
                                               String descricaoMotivo) {
        if (!tipo.ehRejeicao()) {
            throw new IllegalArgumentException(tipo + " é confirmação; use ManifestacaoRequest.confirmacao(...)");
        }
        return new ManifestacaoRequest(ambiente, chaveAcesso, tipo, codigoMotivo, descricaoMotivo, null);
    }
}
