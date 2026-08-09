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
     * <p><b>É indicação, não garantia.</b> A Swagger declara o enum como {@code 0, 1, -1} e não
     * documenta os valores. Confrontando a API de produção com a lista oficial de municípios
     * aderentes ({@code doc/municipios-aderentes-20260710.xlsx}, coluna
     * {@code AderenteEmissorNacional}), a leitura "{@code 1} = os contribuintes emitem pelo padrão
     * nacional" bateu em <b>quatro dos seis</b> municípios amostrados:
     *
     * <table border="1">
     *   <caption>Consultado em 09/08/2026 contra a lista de 10/07/2026</caption>
     *   <tr><th>Município</th><th>situação (API)</th><th>lista oficial</th><th>confere</th></tr>
     *   <tr><td>São Paulo</td><td>0</td><td>Não</td><td>sim</td></tr>
     *   <tr><td>Brasiléia</td><td>0</td><td>Não</td><td>sim</td></tr>
     *   <tr><td>Niterói</td><td>1</td><td>Sim</td><td>sim</td></tr>
     *   <tr><td>Imbé</td><td>1</td><td>Sim</td><td>sim</td></tr>
     *   <tr><td>Juiz de Fora</td><td>1</td><td>Não</td><td><b>não</b></td></tr>
     *   <tr><td>Montes Claros</td><td>0</td><td>Sim</td><td><b>não</b></td></tr>
     * </table>
     *
     * <p>As duas divergências apontam em direções opostas, então não são explicáveis só pelo mês
     * de diferença entre as fontes. Use este método como <b>pré-voo barato</b> — ele evita montar
     * uma DPS que seria recusada —, mas a fonte de verdade é a lista oficial, e a resposta
     * definitiva é a própria tentativa de emissão.
     *
     * <p>Onde as três fontes concordam é em São Paulo: {@code 0} na API dos dois ambientes,
     * {@code Não} na lista, e {@code E0084} em toda emissão tentada.
     *
     * <p>O valor {@code -1} está no enum e nunca foi observado — tudo que não for {@code 1} conta
     * como "ainda não".
     */
    public boolean contribuintesEmitemPeloPadraoNacional() {
        return situacaoEmissaoPadraoContribuintesRFB != null
                && situacaoEmissaoPadraoContribuintesRFB == CONTRIBUINTES_MIGRADOS;
    }
}
