package br.com.adelfo.nfse.nacional.service;

import br.com.adelfo.nfse.nacional.client.CertificadoDigital;
import br.com.adelfo.nfse.nacional.client.NfseHttpClient;
import br.com.adelfo.nfse.nacional.client.TipoAmbiente;
import br.com.adelfo.nfse.nacional.service.dto.request.CancelamentoRequest;
import br.com.adelfo.nfse.nacional.service.dto.request.ConsultaEventoRequest;
import br.com.adelfo.nfse.nacional.service.dto.request.ConsultaNfseRequest;
import br.com.adelfo.nfse.nacional.service.dto.request.EmissaoRequest;
import br.com.adelfo.nfse.nacional.service.dto.response.CancelamentoResponse;
import br.com.adelfo.nfse.nacional.service.dto.response.ConsultaEventoResponse;
import br.com.adelfo.nfse.nacional.service.dto.response.ConsultaNfseResponse;
import br.com.adelfo.nfse.nacional.service.dto.response.ConvenioResponse;
import br.com.adelfo.nfse.nacional.service.dto.response.EmissaoResponse;
import br.com.adelfo.nfse.nacional.service.dto.response.SituacaoNfse;
import br.com.adelfo.nfse.nacional.service.exception.NfseException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A Comaho emite uma NFS-e de <b>R$ 1,00</b> para a MCamas e a cancela — o ciclo que ela vai usar de
 * verdade, nos dois ambientes. Com {@code -Dnfse.prestador=mcamas} as partes se invertem: a MCamas
 * emite para a Comaho. A receita é a mesma porque o perfil é o mesmo — as 71 notas que a MCamas
 * emitiu pelo sistema da Prefeitura até 30/10/2026 são todas "manutenção em equipamento
 * hospitalar", {@code 140101}/{@code 009}, e desde 15/07/2026 (nNFSe 11) no Simples, sem retenção,
 * com 17,35% de tributos aproximados — a soma dos percentuais do IBPT abaixo:
 *
 * <ul>
 *   <li><b>produção restrita</b> (padrão): regressão. Leiaute da NT 009 e a receita do
 *       {@code GUIA-DE-USO}, seção 9, que passaram em 28/10/2026 — se algo se perdeu, é aqui que
 *       aparece;</li>
 *   <li><b>produção</b>: prova de que a biblioteca emite. Até 30/11/2026 sai no leiaute de
 *       fevereiro ({@link LeiauteDps#vigente}), que não tem outro ambiente onde ser testado.</li>
 * </ul>
 *
 * <p>Passos: emitir → consultar por chave → cancelar → consultar o evento na
 * Sefin → aguardar a situação {@code CANCELADA} no ADN.
 *
 * <p><b>Em produção a nota é um documento fiscal real</b>, por isso três travas:
 * <ol>
 *   <li>só roda com {@code -Dnfse.ambiente=producao} <b>e</b> {@code -Dnfse.producao.emitir=sim};</li>
 *   <li>o convênio de São Paulo é consultado e impresso, mas <b>não</b> barra o envio: em
 *       04/11/2026 a Comaho emitiu em produção pelo Emissor Web com o convênio respondendo situação
 *       {@code 0} — o indicador não acompanha a migração;</li>
 *   <li>se o passo de cancelamento não chegar a cancelar, o {@code @AfterAll} tenta de novo e, se
 *       ainda assim falhar, imprime a chave para cancelamento manual no Emissor Nacional.</li>
 * </ol>
 * Em produção a série é {@value #SERIE_PRODUCAO}, separada da que o sistema de verdade usar, e a
 * numeração tem arquivo próprio por prestador ({@code nfse-test-sequence-producao.txt} para a Comaho,
 * {@code nfse-test-sequence-producao-mcamas.txt} para a MCamas), que nunca reinicia: DPS repetida é
 * recusada, e em produção não há como "limpar" o histórico.
 *
 * <p>O cancelamento em produção depende de parametrização municipal que não se consulta antes
 * ({@code E0822} prazo, {@code E0823} valor, {@code E0824} tomador) — é o risco que sobra.
 *
 * <pre>
 * # produção restrita (regressão)
 * mvn test -pl nfse-nacional-service -DfailIfNoTests=false \
 *     -Dsurefire.failIfNoSpecifiedTests=false -Dtest=ComahoEmiteParaMcamasIT
 *
 * # produção — emite e cancela uma nota real de R$ 1,00
 * mvn test -pl nfse-nacional-service -DfailIfNoTests=false \
 *     -Dsurefire.failIfNoSpecifiedTests=false -Dtest=ComahoEmiteParaMcamasIT \
 *     -Dnfse.ambiente=producao -Dnfse.producao.emitir=sim
 *
 * # a MCamas emite para a Comaho (qualquer dos dois ambientes)
 *     ... -Dnfse.prestador=mcamas
 * </pre>
 *
 * <p>O certificado sai de {@code certificado/<prestador>/} (o vencido é descartado), ou de
 * {@code -Dnfse.cert.p12}. Se não for do prestador escolhido, o teste é pulado.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ComahoEmiteParaMcamasIT {

    /**
     * Uma das duas partes. Do tomador vão só CNPJ e razão social — endereço é opcional no leiaute; do
     * prestador, só o CNPJ ({@code E0121}/{@code E0128}). {@code pasta} é a de {@code certificado/}.
     */
    private record Parte(String pasta, String cnpj, String nome, String sequenciaProducao) {
    }

    private static final Parte COMAHO = new Parte("comaho", "54559893000139",
            "COMAHO COMERCIO DE MATERIAIS HOSPITALARES LTDA", "nfse-test-sequence-producao.txt");
    private static final Parte MCAMAS = new Parte("mcamas", "37641093000143",
            "MCAMAS LOCACAO DE EQUIPAMENTOS HOSPITALARES LTDA", "nfse-test-sequence-producao-mcamas.txt");

    private static final String MUNICIPIO_SP = "3550308";
    private static final BigDecimal VALOR = new BigDecimal("1.00");

    /** Percentuais do IBPT para o 14.01 em São Paulo (v26.2.B) — ver GUIA-DE-USO, seção 9. */
    private static final BigDecimal IBPT_FEDERAL = new BigDecimal("13.45");
    private static final BigDecimal IBPT_ESTADUAL = new BigDecimal("0.00");
    private static final BigDecimal IBPT_MUNICIPAL = new BigDecimal("3.90");

    private static final String DESCRICAO =
            "NFS-E DE TESTE DO SISTEMA EMISSOR, SERA CANCELADA EM SEGUIDA. VALOR R$ 1,00";

    /** {@code 2} = serviço não prestado ({@code TSCodJustCanc}) — o que é verdade numa nota de teste. */
    private static final String MOTIVO_SERVICO_NAO_PRESTADO = "2";
    private static final String JUSTIFICATIVA =
            "Nota de teste de integracao do sistema emissor; servico nao prestado";

    /** Série só dos testes em produção, para não disputar numeração com o emissor de verdade. */
    static final String SERIE_PRODUCAO = "999";

    /** Pré-requisitos de cadastro: o documento está certo, quem não está habilitado é o CNPJ ou o município. */
    private static final Set<String> PRE_REQUISITOS_CADASTRAIS = Set.of("E0037", "E0084", "E0120");

    /** O ADN recebe a nota ~30 s depois da Sefin, mas já levou mais de uma hora (CLAUDE.md). */
    private static final Duration ESPERA_ADN = Duration.ofMinutes(3);
    private static final Duration INTERVALO_ADN = Duration.ofSeconds(30);

    private static TipoAmbiente ambiente;
    private static Parte prestador;
    private static Parte tomador;
    private static LeiauteDps leiaute;
    private static NfseService service;
    private static NfseHttpClient httpClient;

    /** Motivo para não emitir; nulo quando o caminho está livre. */
    private static String impedimento;

    private static String chaveAcesso;
    private static String xmlNfse;
    private static boolean cancelada;

    @BeforeAll
    static void setup() throws Exception {
        ambiente = CertificadoDeTeste.ambiente();
        leiaute = LeiauteDps.vigente(ambiente, DataHoraFiscal.hoje());
        String escolhido = System.getProperty("nfse.prestador", COMAHO.pasta());
        if (MCAMAS.pasta().equalsIgnoreCase(escolhido)) {
            prestador = MCAMAS;
            tomador = COMAHO;
        } else if (COMAHO.pasta().equalsIgnoreCase(escolhido)) {
            prestador = COMAHO;
            tomador = MCAMAS;
        } else {
            throw new IllegalArgumentException("-Dnfse.prestador deve ser comaho ou mcamas: " + escolhido);
        }

        CertificadoDigital certificado = CertificadoDeTeste.carregarDaEmpresa(ambiente, prestador.pasta());
        String emitente = certificado.inscricaoFederal();
        assumeTrue(prestador.cnpj().equals(emitente),
                "Teste ignorado: o certificado é de " + emitente + ", e o prestador escolhido é " + prestador.nome());

        httpClient = new NfseHttpClient(certificado, Duration.ofSeconds(15), Duration.ofSeconds(60));
        service = new NfseServiceImpl(certificado, httpClient);

        System.out.println("[setup] Ambiente : " + ambiente + " (tpAmb=" + ambiente.getCodigo() + ")");
        System.out.println("[setup] Leiaute  : " + leiaute);
        System.out.println("[setup] Prestador: " + prestador.cnpj() + " (" + prestador.nome() + ")");
        System.out.println("[setup] Tomador  : " + tomador.cnpj() + " (" + tomador.nome() + ")");

        if (ambiente == TipoAmbiente.PRODUCAO) {
            System.out.println("[setup] *** PRODUÇÃO: a emissão gera um documento fiscal real ***");
            if (!"sim".equalsIgnoreCase(System.getProperty("nfse.producao.emitir"))) {
                impedimento = "produção exige -Dnfse.producao.emitir=sim — a nota é real";
                return;
            }
        }
        conferirConvenio();
    }

    /**
     * Só informa. Já barrou o envio quando a situação era {@code 0}, mas em 04/11/2026 a Comaho emitiu
     * em produção pelo Emissor Web com o convênio respondendo {@code 0} minutos depois — o pré-voo
     * impedia uma emissão que a Sefin aceitava. Fica impresso para explicar um {@code E0084}, se vier.
     */
    private static void conferirConvenio() {
        try {
            ConvenioResponse convenio = new ParametrosMunicipaisServiceImpl(httpClient)
                    .consultarConvenio(ambiente, MUNICIPIO_SP);
            System.out.println("[setup] Convênio : situação=" + convenio.situacaoEmissaoPadraoContribuintesRFB()
                    + " → emite pelo padrão nacional: " + convenio.contribuintesEmitemPeloPadraoNacional());
        } catch (Exception e) {
            // 429 do ADN não é motivo para abortar: o pré-voo é conveniência, a emissão é a verdade.
            System.out.println("[setup] Convênio : não consultado (" + e.getMessage() + "); seguindo");
        }
    }

    /** A receita do GUIA-DE-USO, seção 9 — a mesma para as duas empresas. */
    private static DpsBuilder dps(String serie, String numero) {
        return DpsBuilder.novo()
                .ambiente(ambiente)
                .municipioEmissor(MUNICIPIO_SP)
                .identificacao(serie, numero, DataHoraFiscal.hoje())
                .emitidaPeloPrestador()
                .prestadorCnpj(prestador.cnpj())
                .optanteSimplesNacionalMeEpp("1")
                .semRegimeEspecial()
                .tomadorCnpj(tomador.cnpj(), tomador.nome())
                .servicoPrestadoNoMunicipio(MUNICIPIO_SP)
                .servico("140101", DESCRICAO)
                .codigoTributacaoMunicipal("009")
                .codigoNbs("120018200")
                .valorServico(VALOR)
                .issqnTributavel()
                .issqnNaoRetido()
                // sem aliquota(...): E0625 recusa para ME/EPP no Simples sem retenção
                .totalTributosPercentual(IBPT_FEDERAL, IBPT_ESTADUAL, IBPT_MUNICIPAL);
    }

    private static String serie() {
        return ambiente == TipoAmbiente.PRODUCAO
                ? System.getProperty("nfse.serie", SERIE_PRODUCAO)
                : DpsDeTeste.serie();
    }

    /**
     * Produção: sequência própria do prestador, a partir de 1 e sem reinício — o Id da DPS leva o CNPJ,
     * então cada um numera a sua série 999. Produção restrita: a de sempre.
     */
    private static String proximoNumero() throws Exception {
        if (ambiente != TipoAmbiente.PRODUCAO) {
            return DpsDeTeste.proximoNumeroDps();
        }
        Path sequencia = Paths.get(prestador.sequenciaProducao()).toAbsolutePath();
        long atual = Files.exists(sequencia)
                ? Long.parseLong(Files.readString(sequencia).trim())
                : 1;
        Files.writeString(sequencia, (atual + 1) + System.lineSeparator());
        System.out.println("[sequencia] produção, série " + serie() + ", nDPS " + atual);
        return String.valueOf(atual);
    }

    private static void exigeNota() {
        assumeTrue(chaveAcesso != null, "Passo ignorado: não há NFS-e emitida — " + impedimento);
    }

    @Test
    @Order(1)
    void emitir() throws Exception {
        assumeTrue(impedimento == null, "Emissão não enviada — " + impedimento);

        DpsBuilder builder = dps(serie(), proximoNumero());
        String xmlDps = builder.build(leiaute);   // FEVEREIRO_2026 valida no XSD aqui, antes de enviar

        System.out.println();
        System.out.println("=== 1. DPS (" + leiaute + ") ===");
        System.out.println("Id: " + builder.id());
        System.out.println(xmlDps);

        try {
            EmissaoResponse resposta = service.emitir(new EmissaoRequest(ambiente, xmlDps));
            chaveAcesso = resposta.chaveAcesso();
            xmlNfse = resposta.xmlNfse();
        } catch (NfseException e) {
            impedimento = "[" + e.getCodigo() + "] " + e.getMensagem();
            System.out.println("=== EMISSÃO REJEITADA: " + impedimento);
            System.out.println("corpo: " + e.getCorpoResposta());
            assumeTrue(!PRE_REQUISITOS_CADASTRAIS.contains(e.getCodigo()),
                    "Pré-requisito de cadastro não atendido, não defeito da biblioteca — " + impedimento);
            throw e;
        }

        System.out.println();
        System.out.println("=== NFS-e emitida: " + chaveAcesso + " ===");
        System.out.println(xmlNfse);

        assertTrue(chaveAcesso.matches("[0-9]{50}"), "chave fora do formato: " + chaveAcesso);
        assertTrue(chaveAcesso.substring(9, 23).equals(prestador.cnpj()), "a chave deve trazer o CNPJ do prestador");
        assertTrue(xmlNfse.contains("<tpAmb>" + ambiente.getCodigo() + "</tpAmb>"),
                "a nota deve ter sido gerada em " + ambiente);
    }

    @Test
    @Order(2)
    void consultarPorChave() {
        exigeNota();

        ConsultaNfseResponse resposta = service.consultarPorChave(new ConsultaNfseRequest(ambiente, chaveAcesso));
        String xml = resposta.xmlNfse();

        assertNotNull(xml, "a consulta por chave deve devolver o XML da NFS-e");
        assertTrue(xml.contains(chaveAcesso), "o XML deve trazer a chave emitida");
        assertTrue(xml.contains("<vServ>1.00</vServ>"), "o valor do serviço deve ser 1.00");
        assertTrue(xml.contains("<CNPJ>" + tomador.cnpj() + "</CNPJ>"), tomador.nome() + " deve ser o tomador");
        assertTrue(xml.contains("<cTribNac>140101</cTribNac>"), "o serviço deve ser o 14.01.01");
        assertTrue(xml.contains("<pTotTribFed>13.45</pTotTribFed>"), "os percentuais do IBPT devem constar");
    }

    @Test
    @Order(3)
    void cancelar() {
        exigeNota();

        CancelamentoResponse resposta = cancelarNota();

        System.out.println();
        System.out.println("=== 3. evento de cancelamento ===");
        System.out.println(resposta.xmlEvento());

        assertEquals(chaveAcesso, resposta.chaveAcesso());
        assertTrue(resposta.xmlEvento().contains("101101"), "o evento deve ser o e101101");
    }

    /**
     * Com novas tentativas: em produção (04/11/2026) a consulta feita logo após o cancelamento
     * respondeu 404, e a mesma consulta, 4 min depois, devolveu o evento. Em produção restrita ele
     * constava na hora.
     */
    @Test
    @Order(4)
    void consultarEventoNaSefin() throws InterruptedException {
        exigeNota();
        assumeTrue(cancelada, "Passo ignorado: o cancelamento não foi registrado");

        ConsultaEventoResponse resposta = null;
        for (int tentativa = 1; tentativa <= 12; tentativa++) {
            resposta = service.consultarEvento(ConsultaEventoRequest.cancelamento(ambiente, chaveAcesso));
            System.out.println("[sefin] evento, tentativa " + tentativa + ": "
                    + (resposta.xmlEvento() != null ? "encontrado" : "ainda não"));
            if (resposta.xmlEvento() != null) {
                break;
            }
            Thread.sleep(10_000);
        }

        assertNotNull(resposta.xmlEvento(), "o evento de cancelamento deve constar na Sefin em 2 min");
    }

    /**
     * A situação sai do ADN, e o ADN é eventual. Não chegar no prazo pula em vez de falhar: atraso do
     * ADN não é defeito da biblioteca, e a Sefin (passo 4) já confirmou o cancelamento.
     */
    @Test
    @Order(5)
    void aguardarSituacaoCanceladaNoAdn() throws Exception {
        exigeNota();
        assumeTrue(cancelada, "Passo ignorado: o cancelamento não foi registrado");

        long limite = System.nanoTime() + ESPERA_ADN.toNanos();
        SituacaoNfse situacao = null;
        while (System.nanoTime() < limite) {
            try {
                situacao = service.consultarSituacao(ambiente, chaveAcesso);
                System.out.println("[adn] situação: " + situacao);
                if (situacao == SituacaoNfse.CANCELADA) {
                    return;
                }
            } catch (NfseException e) {
                // a nota pode ainda não estar no ADN, ou o rate limit respondeu 429
                System.out.println("[adn] " + e.getMessage());
            }
            Thread.sleep(INTERVALO_ADN.toMillis());
        }
        assumeTrue(false, "o ADN não mostrou CANCELADA em " + ESPERA_ADN.toMinutes()
                + " min (última leitura: " + situacao + ") — confira mais tarde; a Sefin já registrou o evento");
    }

    /** Rede de segurança: uma nota de produção não pode sobrar emitida porque um passo quebrou antes. */
    @AfterAll
    static void garantirCancelamento() {
        if (chaveAcesso == null || cancelada) {
            return;
        }
        try {
            cancelarNota();
            System.out.println("[afterAll] nota cancelada pela rede de segurança: " + chaveAcesso);
        } catch (Exception e) {
            System.out.println();
            System.out.println("************************************************************");
            System.out.println("*** NOTA EMITIDA E NÃO CANCELADA — cancele manualmente:");
            System.out.println("***   ambiente: " + ambiente);
            System.out.println("***   chave   : " + chaveAcesso);
            System.out.println("***   motivo  : " + e.getMessage());
            System.out.println("************************************************************");
        }
    }

    private static CancelamentoResponse cancelarNota() {
        CancelamentoResponse resposta = service.cancelar(CancelamentoRequest.de(
                ambiente, chaveAcesso, MOTIVO_SERVICO_NAO_PRESTADO, JUSTIFICATIVA));
        cancelada = true;
        return resposta;
    }
}
