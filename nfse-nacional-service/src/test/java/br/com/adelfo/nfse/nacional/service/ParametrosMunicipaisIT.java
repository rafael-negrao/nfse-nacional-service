package br.com.adelfo.nfse.nacional.service;

import br.com.adelfo.nfse.nacional.client.CertificadoDigital;
import br.com.adelfo.nfse.nacional.client.NfseHttpClient;
import br.com.adelfo.nfse.nacional.client.TipoAmbiente;
import br.com.adelfo.nfse.nacional.service.dto.request.ConsultaEventoRequest;
import br.com.adelfo.nfse.nacional.service.dto.request.ConsultaEventosRequest;
import br.com.adelfo.nfse.nacional.service.dto.response.ConsultaEventoResponse;
import br.com.adelfo.nfse.nacional.service.dto.response.ConsultaEventosResponse;
import br.com.adelfo.nfse.nacional.service.dto.response.ConvenioResponse;
import br.com.adelfo.nfse.nacional.service.dto.response.DocumentoDistribuido;
import br.com.adelfo.nfse.nacional.service.dto.response.ParametroMunicipalResponse;
import br.com.adelfo.nfse.nacional.service.exception.NfseException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Exercita as consultas de parametrização municipal e de eventos contra o ambiente real. Só
 * leitura: nenhum documento é emitido.
 *
 * <pre>
 * mvn test -pl nfse-nacional-service -DfailIfNoTests=false \
 *     -Dsurefire.failIfNoSpecifiedTests=false -Dtest=ParametrosMunicipaisIT \
 *     -Dnfse.municipio=3550308
 * </pre>
 */
class ParametrosMunicipaisIT {

    private static TipoAmbiente ambiente;
    private static ParametrosMunicipaisService parametros;
    private static NfseService nfse;
    private static String municipio;
    /** 9 dígitos: cTribNac (6) + cTribMun (3). */
    private static String servicoCompleto;

    @BeforeAll
    static void setup() throws Exception {
        ambiente = CertificadoDeTeste.ambiente();
        CertificadoDigital certificado = CertificadoDeTeste.carregar(ambiente);
        NfseHttpClient httpClient = new NfseHttpClient(
                certificado, Duration.ofSeconds(15), Duration.ofSeconds(30));

        parametros = new ParametrosMunicipaisServiceImpl(httpClient);
        nfse = new NfseServiceImpl(certificado, httpClient);
        municipio = DpsDeTeste.municipio();
        servicoCompleto = ParametrosMunicipaisService.codigoServicoCompleto(
                DpsDeTeste.codigoServico(), System.getProperty("nfse.servico.municipal", "000"));

        System.out.println("[setup] Município : " + municipio);
    }

    /**
     * Compara o convênio de vários municípios para descobrir o que significa o
     * {@code situacaoEmissaoPadraoContribuintesRFB}.
     *
     * <p>A Swagger declara o enum como {@code 0, 1, -1} e <b>não documenta os valores</b>. São
     * Paulo responde {@code aderenteEmissorNacional: 1} — o município aderiu — mas
     * {@code situacaoEmissaoPadraoContribuintesRFB: 0}, e é lá que a emissão morre com
     * {@code E0084}. Comparar com municípios que comprovadamente emitem pelo Emissor Nacional é o
     * que permite ler esse campo como "os contribuintes deste município já podem emitir pelo
     * padrão nacional" em vez de adivinhar.
     *
     * <p>Só leitura. As chamadas são espaçadas porque o ADN aplica limite de ritmo e responde 429
     * em HTML depois de duas seguidas.
     */
    @Test
    void situacaoEmissaoPadraoRFB_comparadaEntreMunicipios() throws Exception {
        record Alvo(String codigo, String nome) {
        }
        List<Alvo> alvos = List.of(
                new Alvo("3550308", "São Paulo/SP — mantém emissor próprio"),
                new Alvo("1200104", "Brasiléia/AC"),
                new Alvo("4310330", "Imbé/RS"),
                new Alvo("3136702", "Juiz de Fora/MG"),
                new Alvo("3303302", "Niterói/RJ"),
                new Alvo("3143302", "Montes Claros/MG"));

        System.out.println();
        System.out.printf("%-9s %-38s %-9s %-8s %s%n",
                "código", "município", "emissor", "situação", "ambiente");
        for (Alvo alvo : alvos) {
            try {
                ConvenioResponse c = parametros.consultarConvenio(ambiente, alvo.codigo());
                System.out.printf("%-9s %-38s %-9s %-8s %s%n",
                        alvo.codigo(), alvo.nome(),
                        c.aderenteEmissorNacional(),
                        c.situacaoEmissaoPadraoContribuintesRFB(),
                        c.aderenteAmbienteNacional());
            } catch (Exception e) {
                System.out.printf("%-9s %-38s %s%n", alvo.codigo(), alvo.nome(), e.getMessage());
            }
            Thread.sleep(3000);
        }
    }

    @Test
    void consultarConvenio_devolveAAderenciaDoMunicipio() {
        ConvenioResponse c = parametros.consultarConvenio(ambiente, municipio);

        System.out.println("=== convênio de " + municipio + " ===");
        System.out.println("ambiente nacional : " + c.aderenteAmbienteNacional());
        System.out.println("emissor nacional  : " + c.aderenteEmissorNacional());
        System.out.println("MAN               : " + c.aderenteMAN());
        System.out.println("situação RFB      : " + c.situacaoEmissaoPadraoContribuintesRFB());
        System.out.println("aprov. créditos   : " + c.permiteAproveitamentoCreditos());
        System.out.println("mensagem          : " + c.mensagem());

        assertNotNull(c.mensagem(), "o ADN sempre devolve mensagem nesta rota");
    }

    @Test
    void consultarRetencoes_devolveEstadoSemLancar() {
        ParametroMunicipalResponse r =
                parametros.consultarRetencoes(ambiente, municipio, LocalDate.now());

        System.out.println("=== retenções ===");
        System.out.println("encontrado: " + r.encontrado());
        System.out.println("mensagem  : " + r.mensagem());
        System.out.println("conteúdo  : " + r.conteudo());

        // Ausência de parametrização é estado válido, não erro: o contrato é não lançar.
        assertNotNull(r);
    }

    /**
     * Defeito do lado do ADN: as três rotas que recebem código de serviço respondem 400 com
     * "O código do serviço deve ser composto por nove dígitos" <b>para qualquer valor</b> —
     * inclusive {@code 010101000} e {@code 000000000}, que têm exatamente nove dígitos. Foram
     * testados também 6, 7, 8 e 10 dígitos e a forma pontuada, todos com a mesma resposta.
     *
     * <p>Os caminhos vêm da Swagger oficial, então a implementação fica como está. Este teste fixa
     * o comportamento observado: se um dia passar a responder, ele falha e avisa que a rota
     * voltou — momento de trocar estas asserções por verificações de conteúdo.
     */
    private static void assertDefeitoConhecidoDoAdn(Runnable consulta) {
        NfseException e = assertThrows(NfseException.class, consulta::run);
        System.out.println("resposta do ADN: [" + e.getCodigo() + "] " + e.getMensagem());
        assertTrue(e.getMensagem().contains("nove dígitos"),
                "esperado o defeito conhecido do ADN; recebido: " + e.getMensagem());
    }

    @Test
    void consultarAliquota_hojeEsbarraNoDefeitoDoAdn() {
        System.out.println("=== alíquota do serviço " + servicoCompleto + " ===");
        assertDefeitoConhecidoDoAdn(() ->
                parametros.consultarAliquota(ambiente, municipio, servicoCompleto, LocalDate.now()));
    }

    @Test
    void consultarHistoricoAliquotas_hojeEsbarraNoDefeitoDoAdn() {
        System.out.println("=== histórico de alíquotas ===");
        assertDefeitoConhecidoDoAdn(() ->
                parametros.consultarHistoricoAliquotas(ambiente, municipio, servicoCompleto));
    }

    @Test
    void consultarRegimesEspeciais_hojeEsbarraNoDefeitoDoAdn() {
        System.out.println("=== regimes especiais ===");
        assertDefeitoConhecidoDoAdn(() ->
                parametros.consultarRegimesEspeciais(ambiente, municipio, servicoCompleto, LocalDate.now()));
    }

    @Test
    void codigoServicoForaDoFormato_falhaAntesDeChamarOAdn() {
        // Validação nossa: melhor uma mensagem que aponta o erro do que o 400 genérico do ADN.
        assertThrows(IllegalArgumentException.class,
                () -> parametros.consultarHistoricoAliquotas(ambiente, municipio, "010101"));
    }

    @Test
    void consultarEvento_deNotaInexistente_naoLancaEDizQueNaoAchou() {
        ConsultaEventoResponse r = nfse.consultarEvento(
                ConsultaEventoRequest.cancelamento(ambiente, "1".repeat(49) + "9"));

        System.out.println("=== evento 101101/1 de nota inexistente ===");
        System.out.println("encontrado: " + r.encontrado());

        assertFalse(r.encontrado(), "não há evento para uma chave inexistente");
        assertEquals("101101", r.tipoEvento());
    }

    @Test
    void consultarEventos_deNotaInexistente_devolveListaVazia() {
        ConsultaEventosResponse r = nfse.consultarEventos(
                new ConsultaEventosRequest(ambiente, "1".repeat(49) + "9"));

        System.out.println("=== eventos (ADN) de nota inexistente ===");
        System.out.println("documentos: " + r.documentos().size());

        assertTrue(r.documentos().isEmpty());
    }

    /**
     * Consulta os eventos de uma NFS-e <b>real</b>. Exige {@code -Dnfse.chave}; sem uma chave que
     * exista, a rota só devolve lista vazia e nada de útil é verificado.
     */
    @Test
    void consultarEventos_deNotaReal_trazNfseEEventos() {
        String chave = System.getProperty("nfse.chave");
        assumeTrue(chave != null && !chave.isBlank(),
                "Teste ignorado: informe -Dnfse.chave=<chave de 50 dígitos> de uma nota existente");

        ConsultaEventosResponse r = nfse.consultarEventos(new ConsultaEventosRequest(ambiente, chave));

        System.out.println("=== eventos (ADN) da nota " + chave + " ===");
        for (DocumentoDistribuido d : r.documentos()) {
            System.out.println("  NSU=" + d.nsu() + " tipo=" + d.tipoDocumento()
                    + " evento=" + d.tipoEvento() + " geracao=" + d.dataHoraGeracao()
                    + " xml=" + (d.xml() == null ? "null" : d.xml().length() + " bytes"));
        }
        System.out.println("  cancelada: " + r.cancelada());

        assertFalse(r.documentos().isEmpty(), "a nota informada deveria existir");
        // A rota chama-se /Eventos mas devolve a NFS-e junto — é o que distingue documentos() de eventos().
        assertTrue(r.nfse().isPresent(), "o lote deve trazer a própria NFS-e");
        for (DocumentoDistribuido d : r.documentos()) {
            assertNotNull(d.xml(), "todo documento do lote deve vir com XML descomprimido");
        }
    }
}
