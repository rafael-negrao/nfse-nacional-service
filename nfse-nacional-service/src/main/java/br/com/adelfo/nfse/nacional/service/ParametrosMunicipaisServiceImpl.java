package br.com.adelfo.nfse.nacional.service;

import br.com.adelfo.nfse.nacional.client.NfseHttpClient;
import br.com.adelfo.nfse.nacional.client.NfseHttpException;
import br.com.adelfo.nfse.nacional.client.TipoAmbiente;
import br.com.adelfo.nfse.nacional.service.dto.response.ConvenioResponse;
import br.com.adelfo.nfse.nacional.service.dto.response.ParametroMunicipalResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Implementação das consultas de parametrização municipal no ADN.
 *
 * <p>Nenhuma destas operações lança em caso de "não encontrado": são consultas, e a ausência de
 * parametrização é um estado legítimo — o ADN responde 404 com corpo explicando, como em
 * {@code {"retencoes":null,"mensagem":"Parâmetros de retenções … não encontrados para a
 * competência."}}.
 */
public class ParametrosMunicipaisServiceImpl implements ParametrosMunicipaisService {

    /** A competência viaja como data ISO; {@code AAAAMMDD} e {@code AAAAMM} são recusados. */
    private static final DateTimeFormatter FORMATO_COMPETENCIA = DateTimeFormatter.ISO_LOCAL_DATE;

    private static final String NO_CONVENIO = "parametrosConvenio";
    private static final String CAMPO_MENSAGEM = "mensagem";

    private final NfseHttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ParametrosMunicipaisServiceImpl(NfseHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public ConvenioResponse consultarConvenio(TipoAmbiente ambiente, String codigoMunicipio) {
        JsonNode raiz = obter(ambiente, codigoMunicipio + "/convenio");
        JsonNode convenio = raiz == null ? null : raiz.get(NO_CONVENIO);
        if (convenio == null || convenio.isNull()) {
            return new ConvenioResponse(false, false, false, null, false, mensagem(raiz));
        }
        return new ConvenioResponse(
                simNao(convenio, "aderenteAmbienteNacional"),
                simNao(convenio, "aderenteEmissorNacional"),
                simNao(convenio, "aderenteMAN"),
                inteiro(convenio, "situacaoEmissaoPadraoContribuintesRFB"),
                // O nome do campo está grafado assim na API — "aproveitameto", sem o "n".
                convenio.path("permiteAproveitametoDeCreditos").asBoolean(false),
                mensagem(raiz));
    }

    @Override
    public ParametroMunicipalResponse consultarAliquota(TipoAmbiente ambiente, String codigoMunicipio,
                                                        String codigoServico, LocalDate competencia) {
        return parametro(ambiente,
                codigoMunicipio + "/" + servico(codigoServico) + "/" + data(competencia) + "/aliquota",
                "aliquotas");
    }

    @Override
    public ParametroMunicipalResponse consultarHistoricoAliquotas(TipoAmbiente ambiente,
                                                                  String codigoMunicipio, String codigoServico) {
        return parametro(ambiente,
                codigoMunicipio + "/" + servico(codigoServico) + "/historicoaliquotas", "aliquotas");
    }

    @Override
    public ParametroMunicipalResponse consultarRegimesEspeciais(TipoAmbiente ambiente, String codigoMunicipio,
                                                                String codigoServico, LocalDate competencia) {
        return parametro(ambiente,
                codigoMunicipio + "/" + servico(codigoServico) + "/" + data(competencia) + "/regimes_especiais",
                "regimesEspeciais");
    }

    @Override
    public ParametroMunicipalResponse consultarBeneficio(TipoAmbiente ambiente, String codigoMunicipio,
                                                         String numeroBeneficio, LocalDate competencia) {
        return parametro(ambiente,
                codigoMunicipio + "/" + numeroBeneficio + "/" + data(competencia) + "/beneficio",
                "beneficio");
    }

    @Override
    public ParametroMunicipalResponse consultarRetencoes(TipoAmbiente ambiente, String codigoMunicipio,
                                                         LocalDate competencia) {
        return parametro(ambiente, codigoMunicipio + "/" + data(competencia) + "/retencoes", "retencoes");
    }

    // -------------------------------------------------------------------------

    private ParametroMunicipalResponse parametro(TipoAmbiente ambiente, String caminho, String noDeDados) {
        JsonNode raiz = obter(ambiente, caminho);
        JsonNode dados = raiz == null ? null : raiz.get(noDeDados);
        boolean encontrado = dados != null && !dados.isNull();
        return new ParametroMunicipalResponse(
                encontrado, mensagem(raiz), encontrado ? dados.toString() : null);
    }

    /**
     * GET na base de parametrização. O 404 desta API significa "não parametrizado" e vem com corpo
     * explicativo, então é tratado como resposta e não como erro; os demais status viram
     * {@code NfseException}.
     */
    private JsonNode obter(TipoAmbiente ambiente, String caminho) {
        String url = ambiente.getUrlAdn() + "/parametrizacao/" + caminho;
        try {
            return ler(httpClient.get(url));
        } catch (NfseHttpException e) {
            if (e.getStatus() == 404) {
                return ler(e.getCorpo());
            }
            throw RespostaDeErro.traduzir(e);
        }
    }

    private JsonNode ler(String corpo) {
        if (corpo == null || corpo.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(corpo);
        } catch (Exception naoEhJson) {
            return null;
        }
    }

    /**
     * O ADN exige o código de serviço com 9 dígitos — nacional (6) + municipal (3). Validar aqui
     * troca um 400 do servidor por uma mensagem que aponta o erro.
     */
    private static String servico(String codigoServico) {
        if (codigoServico == null || !codigoServico.matches("[0-9]{9}")) {
            throw new IllegalArgumentException(
                    "o código do serviço desta API tem 9 dígitos (cTribNac + cTribMun); recebido: "
                            + codigoServico + ". Use ParametrosMunicipaisService.codigoServicoCompleto(...)");
        }
        return codigoServico;
    }

    private static String data(LocalDate competencia) {
        if (competencia == null) {
            throw new IllegalArgumentException("competência é obrigatória");
        }
        return competencia.format(FORMATO_COMPETENCIA);
    }

    private static String mensagem(JsonNode raiz) {
        return raiz == null ? null : texto(raiz, CAMPO_MENSAGEM);
    }

    private static String texto(JsonNode no, String campo) {
        JsonNode valor = no.get(campo);
        return valor == null || valor.isNull() ? null : valor.asText();
    }

    private static Integer inteiro(JsonNode no, String campo) {
        JsonNode valor = no.get(campo);
        return valor == null || valor.isNull() ? null : valor.asInt();
    }

    /** Os campos TipoSimNao da API vêm como 1/0, não como booleanos. */
    private static boolean simNao(JsonNode no, String campo) {
        JsonNode valor = no.get(campo);
        return valor != null && !valor.isNull() && valor.asInt() == 1;
    }
}
