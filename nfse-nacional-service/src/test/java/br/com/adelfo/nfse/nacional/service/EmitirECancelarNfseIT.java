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
import br.com.adelfo.nfse.nacional.service.dto.response.EmissaoResponse;
import br.com.adelfo.nfse.nacional.service.exception.NfseException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Ciclo de escrita completo em produção restrita: <b>emitir → imprimir o XML → cancelar</b>.
 *
 * <p>Diferente do {@link NfseServicosIT}, que exercita a fachada com a DPS mínima, aqui a DPS
 * <b>espelha uma NFS-e real</b> — a de número 236, emitida pela Adelfo em produção
 * ({@value #NFSE_REFERENCIA}). Todos os campos vêm da consulta a essa nota: prestador optante do
 * Simples Nacional, tomador identificado com endereço, {@code cTribNac 010401} + {@code cTribMun
 * 001}, {@code cNBS} genérico e alíquota zero. Só o valor muda: <b>R$ 1,00</b> no lugar dos
 * R$ 6.075,00 originais, no campo e no descritivo.
 *
 * <p>Espelhar uma nota aceita de verdade é o que dá valor ao teste: uma rejeição aqui aponta para
 * a biblioteca ou para o cadastro do CNPJ no ambiente, não para um leiaute inventado.
 *
 * <p><b>Estado em 08/08/2026:</b> o documento é aceito pelo schema e chega às regras de negócio —
 * assinatura, GZip, Base64 e transporte estão validados contra a Sefin. A emissão em si para em
 * {@code E0084}: <i>"CNPJ do emitente prestador não possui estabelecimento ou domicílio em um
 * município correspondente ao município emissor … conforme cadastros CNPJ e CNC NFS-e"</i>. São
 * Paulo é aderente na produção restrita ({@code aderenteEmissorNacional: 1}), e a rejeição se
 * repete com a competência de julho, então não é convênio nem data: o CNPJ não consta no cadastro
 * <b>daquele ambiente</b>. É pré-requisito administrativo, fora do alcance do código.
 *
 * <p><b>Os dados bancários do descritivo original foram omitidos</b> — este repositório é público
 * e eles não têm papel nenhum no que se está verificando.
 *
 * <p><b>Ambiente:</b> a NFS-e Nacional não tem "homologação"; o ambiente de teste é a produção
 * restrita ({@code tpAmb=2}), que é o default de {@link CertificadoDeTeste}. A nota de referência
 * é de produção ({@code ambGer=1}), mas nada dela é reaproveitado além do leiaute.
 *
 * <pre>
 * mvn test -pl nfse-nacional-service -DfailIfNoTests=false \
 *     -Dsurefire.failIfNoSpecifiedTests=false -Dtest=EmitirECancelarNfseIT \
 *     -Dnfse.cert.p12=/caminho/cert.pfx -Dnfse.cert.senha=senha
 * </pre>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EmitirECancelarNfseIT {

    /** NFS-e real que serve de molde; consultada em produção para levantar o leiaute. */
    static final String NFSE_REFERENCIA = "35503081206169966000133000000000023626071910332505";

    /** Valor do serviço, no campo e no descritivo. */
    private static final BigDecimal VALOR_SERVICO = new BigDecimal("1.00");

    private static final String DESCRICAO_SERVICO =
            "Elaboração de programa de computador (software). Valor Bruto R$ 1,00";

    /** {@code 1} = erro na emissão ({@code TSCodJustCanc}). */
    private static final String MOTIVO_CANCELAMENTO_ERRO_NA_EMISSAO = "1";

    /** {@code TSMotivo} exige de 15 a 255 caracteres — uma justificativa curta é recusada. */
    private static final String JUSTIFICATIVA_CANCELAMENTO =
            "Cancelamento do teste de integracao de emissao em producao restrita";

    /**
     * Competência da DPS. Parametrizável porque {@code E0084} avalia o cadastro do CNPJ
     * <b>na data de competência</b> — testar outra data é a forma de separar "o CNPJ não está no
     * cadastro" de "não estava naquela data".
     */
    private static LocalDate competencia() {
        String informada = System.getProperty("nfse.competencia");
        return informada == null || informada.isBlank()
                ? DataHoraFiscal.hoje()
                : LocalDate.parse(informada);
    }

    private static TipoAmbiente ambiente;
    private static NfseService service;
    private static String cnpjPrestador;

    /** Preenchidos pela emissão; nulos quando ela foi rejeitada. */
    private static String chaveAcesso;
    private static String xmlNfseEmitida;
    private static String idDps;

    /** Motivo da rejeição na emissão, usado nas mensagens de skip dos passos seguintes. */
    private static String motivoIndisponibilidade;

    /**
     * Rejeições que dependem de <b>cadastro no ambiente</b>, e não do documento enviado.
     *
     * <p>Nenhuma delas é corrigível pela biblioteca: o documento está certo, quem não está
     * habilitado é o CNPJ ou o município naquele ambiente. Elas <b>pulam</b> o teste com a
     * mensagem da Sefin, em vez de falharem — uma falha aqui sugeriria defeito de código e
     * mandaria o próximo a mexer procurar no lugar errado. Qualquer outro código continua
     * quebrando o teste, que é o que se quer de uma regressão de verdade.
     */
    private static final java.util.Set<String> PRE_REQUISITOS_CADASTRAIS = java.util.Set.of(
            "E0037",   // município emissor sem convênio ativo
            "E0084",   // CNPJ sem estabelecimento no município emissor, conforme CNPJ e CNC
            "E0120");  // IM informado sem dados complementares no CNC do município

    @BeforeAll
    static void setup() throws Exception {
        ambiente = CertificadoDeTeste.ambiente();
        CertificadoDigital certificado = CertificadoDeTeste.carregar(ambiente);
        cnpjPrestador = CertificadoDeTeste.inscricaoFederalDoCertificado(certificado);

        NfseHttpClient httpClient = new NfseHttpClient(
                certificado, Duration.ofSeconds(15), Duration.ofSeconds(60));
        service = new NfseServiceImpl(certificado, httpClient);

        System.out.println("[setup] Ambiente    : " + ambiente + " (tpAmb=" + ambiente.getCodigo() + ")");
        System.out.println("[setup] Prestador   : " + cnpjPrestador);
        System.out.println("[setup] Município   : " + DpsDeTeste.municipio());
        System.out.println("[setup] Referência  : NFS-e " + NFSE_REFERENCIA);
        System.out.println("[setup] Competência : " + competencia());
    }

    /**
     * DPS espelhando a nota de referência. Os valores literais foram extraídos do XML dela; estão
     * fixos no teste, e não em {@link DpsDeTeste}, justamente para que se leia lado a lado com a
     * nota real.
     */
    private static DpsBuilder dpsEspelhandoAReferencia(String numeroDps) {
        return DpsBuilder.novo()
                .ambiente(ambiente)
                .municipioEmissor(DpsDeTeste.municipio())
                .identificacao(DpsDeTeste.serie(), numeroDps, competencia())
                .emitidaPeloPrestador()

                // --- prestador: o titular do certificado ---------------------------------------
                .prestadorCnpj(cnpjPrestador)
                // Sem IM, ao contrário da nota real: E0120 — "IM do prestador não deve ser
                // informado, pois não existem informações complementares registradas no CNC
                // NFS-e do município emissor". São Paulo tem cadastro no CNC em produção, mas
                // não em produção restrita, então o mesmo documento é aceito lá e recusado aqui.
                .prestadorEndereco(DpsBuilder.Endereco.nacional(
                        "3550308", "04273200", "VERGUEIRO", "08787", null, "VL FIRMIANO PINTO"))
                .prestadorContato(null, "rafael.negrao@gmail.com")
                // opSimpNac=3 com regApTribSN=1, como na nota real
                .optanteSimplesNacionalMeEpp("1")
                .semRegimeEspecial()

                // --- tomador -------------------------------------------------------------------
                .tomadorCnpj("54559893000139", "COMAHO COMERCIO DE MATERIAIS HOSPITALARES LTDA - EPP")
                // Idem: o IM do tomador cai na mesma ausência de cadastro no CNC.
                .tomadorEndereco(DpsBuilder.Endereco.nacional(
                        "3550308", "04316070", "SAO VENCESLAU", "00324", null, "VILA GUARANI"))
                .tomadorContato(null, "administrativo.comaho@comaho.com.br")

                // --- serviço -------------------------------------------------------------------
                .servicoPrestadoNoMunicipio("3550308")
                .servico("010401", DESCRICAO_SERVICO)
                .codigoTributacaoMunicipal("001")
                .codigoNbs("999999999")

                // --- valores: alíquota zero, ISSQN recolhido pelo Simples ----------------------
                .valorServico(VALOR_SERVICO)
                .issqnTributavel()
                .issqnNaoRetido()
                .aliquota(new BigDecimal("0.00"))
                .totalTributos(new BigDecimal("0.00"), new BigDecimal("0.00"), new BigDecimal("0.00"));
    }

    private static void exigeNfseEmitida() {
        assumeTrue(chaveAcesso != null,
                "Passo ignorado: não há NFS-e emitida — " + motivoIndisponibilidade);
    }

    @Test
    @Order(1)
    void emitir_geraNfseComAChaveDeAcessoDe50Digitos() throws Exception {
        DpsBuilder builder = dpsEspelhandoAReferencia(DpsDeTeste.proximoNumeroDps());
        idDps = builder.id();
        String xmlDps = builder.build();

        System.out.println();
        System.out.println("=== 1. DPS enviada ===");
        System.out.println("Id : " + idDps);
        System.out.println(xmlDps);

        try {
            EmissaoResponse resposta = service.emitir(new EmissaoRequest(ambiente, xmlDps));
            chaveAcesso = resposta.chaveAcesso();
            xmlNfseEmitida = resposta.xmlNfse();
        } catch (NfseException e) {
            motivoIndisponibilidade = "[" + e.getCodigo() + "] " + e.getMensagem();
            System.out.println();
            System.out.println("=== EMISSÃO REJEITADA ===");
            System.out.println("Rejeição : " + motivoIndisponibilidade);
            System.out.println("corpo    : " + e.getCorpoResposta());

            assumeTrue(!PRE_REQUISITOS_CADASTRAIS.contains(e.getCodigo()),
                    "Pré-requisito de cadastro não atendido no ambiente, não defeito da "
                            + "biblioteca — " + motivoIndisponibilidade);
            throw e;
        }

        System.out.println();
        System.out.println("=== NFS-e emitida ===");
        System.out.println("chaveAcesso: " + chaveAcesso);

        assertNotNull(chaveAcesso, "a emissão síncrona deve devolver a chave de acesso");
        assertTrue(chaveAcesso.matches("[0-9]{50}"), "chave fora do formato: " + chaveAcesso);
        // Produção restrita gera com ambGer=2; a referência, de produção, tem 1 nessa posição.
        assertEquals("2", chaveAcesso.substring(7, 8),
                "a nota deve ter sido gerada no ambiente de produção restrita");
    }

    /**
     * O XML da nota, que é o objeto do pedido: impresso duas vezes de propósito — o que a emissão
     * devolveu e o que a Sefin guardou. Se divergissem, o problema estaria no envio, não no
     * armazenamento.
     */
    @Test
    @Order(2)
    void consultarPorChave_imprimeOXmlDaNotaFiscal() {
        exigeNfseEmitida();

        System.out.println();
        System.out.println("=== 2. XML devolvido pela emissão ===");
        System.out.println(xmlNfseEmitida);

        ConsultaNfseResponse resposta = service.consultarPorChave(
                new ConsultaNfseRequest(ambiente, chaveAcesso));

        System.out.println();
        System.out.println("=== 2. XML consultado por chave ===");
        System.out.println(resposta.xmlNfse());

        assertNotNull(resposta.xmlNfse(), "a consulta por chave deve devolver o XML da NFS-e");
        assertTrue(resposta.xmlNfse().contains("<nNFSe>"), "o XML devolvido deve ser uma NFS-e");
        assertTrue(resposta.xmlNfse().contains(chaveAcesso),
                "o XML deve trazer a chave da nota emitida");
        // O valor é o ponto do teste: R$ 1,00, e não os R$ 6.075,00 da nota de referência.
        assertTrue(resposta.xmlNfse().contains("<vServ>1.00</vServ>"),
                "o valor do serviço deveria ser 1.00");
    }

    @Test
    @Order(3)
    void cancelar_registraOEventoE101101() {
        exigeNfseEmitida();

        // Sem autor: a fachada o tira do certificado, como a regra E0812 exige.
        CancelamentoResponse resposta = service.cancelar(CancelamentoRequest.de(
                ambiente, chaveAcesso, MOTIVO_CANCELAMENTO_ERRO_NA_EMISSAO, JUSTIFICATIVA_CANCELAMENTO));

        System.out.println();
        System.out.println("=== 3. XML do evento de cancelamento ===");
        System.out.println(resposta.xmlEvento());

        assertEquals(chaveAcesso, resposta.chaveAcesso());
        assertNotNull(resposta.xmlEvento(), "o cancelamento síncrono deve devolver o XML do evento");
        assertTrue(resposta.xmlEvento().contains("101101"),
                "o evento registrado deve ser o e101101 (cancelamento)");
    }

    /**
     * Confirma que o cancelamento ficou registrado, em vez de confiar apenas na resposta síncrona
     * do POST. É a única forma de distinguir "a Sefin aceitou o pedido" de "a nota está cancelada".
     */
    @Test
    @Order(4)
    void consultarEvento_confirmaOCancelamentoRegistrado() {
        exigeNfseEmitida();

        ConsultaEventoResponse resposta = service.consultarEvento(
                ConsultaEventoRequest.cancelamento(ambiente, chaveAcesso));

        System.out.println();
        System.out.println("=== 4. evento de cancelamento consultado ===");
        System.out.println(resposta.xmlEvento());

        assertNotNull(resposta.xmlEvento(), "o evento de cancelamento deve constar na Sefin");
    }
}
