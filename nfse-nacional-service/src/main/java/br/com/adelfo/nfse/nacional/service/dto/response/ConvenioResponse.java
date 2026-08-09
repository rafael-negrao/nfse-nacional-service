package br.com.adelfo.nfse.nacional.service.dto.response;

/**
 * Parâmetros do convênio de um município com o Sistema Nacional.
 *
 * <p>É a consulta a fazer <b>antes</b> de tentar emitir — mas olhe
 * {@link #contribuintesEmitemPeloPadraoNacional()}, e <b>não</b> {@link #aderenteEmissorNacional()}.
 *
 * <p><b>{@code aderenteEmissorNacional} sozinho engana.</b> Ele diz que o <i>município</i> aderiu,
 * não que os <i>contribuintes</i> dele já possam emitir por essa via. São Paulo responde
 * {@code true} nos dois ambientes e mesmo assim recusa toda emissão com {@code E0084}, porque
 * manteve o emissor próprio e ainda não migrou seus contribuintes.
 *
 * @param aderenteAmbienteNacional município aderiu ao ambiente nacional
 * @param aderenteEmissorNacional  município aderiu ao emissor público nacional — <b>não basta</b>
 * @param aderenteMAN              município aderiu ao Módulo de Apuração Nacional
 * @param situacaoEmissaoPadraoContribuintesRFB situação da emissão pelo padrão nacional; ver
 *                                 {@link #contribuintesEmitemPeloPadraoNacional()}
 * @param permiteAproveitamentoCreditos permite aproveitamento de créditos
 * @param mensagem                 texto devolvido pelo ADN
 */
public record ConvenioResponse(boolean aderenteAmbienteNacional,
                               boolean aderenteEmissorNacional,
                               boolean aderenteMAN,
                               Integer situacaoEmissaoPadraoContribuintesRFB,
                               boolean permiteAproveitamentoCreditos,
                               String mensagem) {

    /** Valor de {@code situacaoEmissaoPadraoContribuintesRFB} em que a emissão é possível. */
    private static final int CONTRIBUINTES_MIGRADOS = 1;

    /**
     * Se os contribuintes deste município já emitem pelo padrão nacional — <b>a condição real</b>
     * para {@code NfseService.emitir} funcionar.
     *
     * <p>A Swagger declara o enum como {@code 0, 1, -1} e não documenta os valores. O significado
     * foi determinado comparando municípios em produção restrita:
     *
     * <table border="1">
     *   <caption>Observado em 09/08/2026</caption>
     *   <tr><th>Município</th><th>{@code aderenteEmissorNacional}</th><th>situação</th></tr>
     *   <tr><td>Juiz de Fora, Niterói, Montes Claros</td><td>true</td><td>1</td></tr>
     *   <tr><td>São Paulo</td><td>true</td><td>0</td></tr>
     *   <tr><td>Brasiléia, Imbé</td><td>false</td><td>ausente</td></tr>
     * </table>
     *
     * <p>Ou seja: {@code 1} os contribuintes migraram; {@code 0} o município aderiu mas eles ainda
     * não; ausente, o município não aderiu. O valor {@code -1} está no enum e nunca foi observado —
     * por isso qualquer coisa diferente de {@code 1} é tratada como "ainda não".
     */
    public boolean contribuintesEmitemPeloPadraoNacional() {
        return situacaoEmissaoPadraoContribuintesRFB != null
                && situacaoEmissaoPadraoContribuintesRFB == CONTRIBUINTES_MIGRADOS;
    }
}
