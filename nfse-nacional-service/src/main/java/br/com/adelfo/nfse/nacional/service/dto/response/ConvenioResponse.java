package br.com.adelfo.nfse.nacional.service.dto.response;

/**
 * Parâmetros do convênio de um município com o Sistema Nacional.
 *
 * <p>É a consulta a fazer <b>antes</b> de tentar emitir: sem {@link #aderenteEmissorNacional()} o
 * município não aceita DPS pelo emissor público, e a emissão será rejeitada.
 *
 * @param aderenteAmbienteNacional município aderiu ao ambiente nacional
 * @param aderenteEmissorNacional  município permite emissão pelo emissor público nacional
 * @param aderenteMAN              município aderiu ao Módulo de Apuração Nacional
 * @param situacaoEmissaoPadraoContribuintesRFB código de situação da emissão padrão
 * @param permiteAproveitamentoCreditos permite aproveitamento de créditos
 * @param mensagem                 texto devolvido pelo ADN
 */
public record ConvenioResponse(boolean aderenteAmbienteNacional,
                               boolean aderenteEmissorNacional,
                               boolean aderenteMAN,
                               Integer situacaoEmissaoPadraoContribuintesRFB,
                               boolean permiteAproveitamentoCreditos,
                               String mensagem) {
}
