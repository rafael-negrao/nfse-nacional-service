package br.com.adelfo.nfse.nacional.service;

import br.com.adelfo.nfse.nacional.client.TipoAmbiente;
import br.com.adelfo.nfse.nacional.service.dto.response.ConvenioResponse;
import br.com.adelfo.nfse.nacional.service.dto.response.ParametroMunicipalResponse;

import java.time.LocalDate;

/**
 * Consultas de parametrização municipal, servidas pelo <b>ADN</b> sob o prefixo
 * {@code /parametrizacao}.
 *
 * <p>Fachada à parte da {@link NfseService} de propósito: é outra API, em outro host, e a própria
 * Sefin sinaliza isso — a rota {@code GET /ParametrosMunicipais} dela responde apenas
 * "Este serviço foi movido".
 *
 * <p>O manual em prosa descreve estes caminhos como {@code /parametros_municipais/{cMun}/…} sob o
 * Emissor Público. Esse segmento não existe em host nenhum; os caminhos abaixo vieram da Swagger
 * oficial, baixada com certificado (ver {@code doc/openapi/adn-parametrizacao.json}).
 *
 * <p>Só as operações de leitura estão aqui. A Swagger também publica rotas POST de manutenção de
 * parâmetros, que são do município, não do contribuinte.
 */
public interface ParametrosMunicipaisService {

    /**
     * Convênio do município com o Sistema Nacional
     * ({@code GET /{codigoMunicipio}/convenio}).
     *
     * @param codigoMunicipio código IBGE de 7 dígitos
     */
    ConvenioResponse consultarConvenio(TipoAmbiente ambiente, String codigoMunicipio);

    /**
     * Compõe o código de serviço no formato que esta API exige: <b>9 dígitos</b>, resultado da
     * concatenação do código de tributação nacional com o municipal.
     *
     * <p>Passar aqui os 6 dígitos do {@code cTribNac} da DPS — o engano natural — faz o ADN
     * responder 400 com "O código do serviço deve ser composto por nove dígitos".
     *
     * @param cTribNac código de tributação nacional, 6 dígitos (LC 116/2003)
     * @param cTribMun código de tributação municipal, 3 dígitos ({@code TCCodTribMun})
     */
    static String codigoServicoCompleto(String cTribNac, String cTribMun) {
        if (cTribNac == null || !cTribNac.matches("[0-9]{6}")) {
            throw new IllegalArgumentException("cTribNac deve ter 6 dígitos: " + cTribNac);
        }
        if (cTribMun == null || !cTribMun.matches("[0-9]{3}")) {
            throw new IllegalArgumentException("cTribMun deve ter 3 dígitos: " + cTribMun);
        }
        return cTribNac + cTribMun;
    }

    /**
     * Alíquota do ISSQN vigente na competência
     * ({@code GET /{codigoMunicipio}/{codigoServico}/{competencia}/aliquota}).
     *
     * @param codigoServico código completo de <b>9 dígitos</b>; ver
     *                      {@link #codigoServicoCompleto(String, String)}
     * @param competencia   competência consultada; enviada como {@code AAAA-MM-DD}
     */
    ParametroMunicipalResponse consultarAliquota(TipoAmbiente ambiente, String codigoMunicipio,
                                                 String codigoServico, LocalDate competencia);

    /**
     * Histórico de alíquotas do serviço
     * ({@code GET /{codigoMunicipio}/{codigoServico}/historicoaliquotas}).
     */
    ParametroMunicipalResponse consultarHistoricoAliquotas(TipoAmbiente ambiente,
                                                           String codigoMunicipio, String codigoServico);

    /**
     * Regimes especiais de tributação do serviço na competência
     * ({@code GET /{codigoMunicipio}/{codigoServico}/{competencia}/regimes_especiais}).
     */
    ParametroMunicipalResponse consultarRegimesEspeciais(TipoAmbiente ambiente, String codigoMunicipio,
                                                         String codigoServico, LocalDate competencia);

    /**
     * Benefício municipal na competência
     * ({@code GET /{codigoMunicipio}/{numeroBeneficio}/{competencia}/beneficio}).
     *
     * @param numeroBeneficio identificador de 14 posições gerado pelo Sistema Nacional quando o
     *                        município cadastrou o benefício — é o mesmo valor que vai em
     *                        {@code nBM} na DPS, não o CNPJ do contribuinte
     */
    ParametroMunicipalResponse consultarBeneficio(TipoAmbiente ambiente, String codigoMunicipio,
                                                  String numeroBeneficio, LocalDate competencia);

    /**
     * Retenções que o município exige na competência
     * ({@code GET /{codigoMunicipio}/{competencia}/retencoes}).
     *
     * <p>O contribuinte não vai no caminho: o ADN o identifica pelo certificado da conexão.
     */
    ParametroMunicipalResponse consultarRetencoes(TipoAmbiente ambiente, String codigoMunicipio,
                                                  LocalDate competencia);
}
