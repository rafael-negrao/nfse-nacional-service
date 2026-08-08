package br.com.adelfo.nfse.nacional.service;

import br.com.adelfo.nfse.nacional.client.CertificadoDigital;
import br.com.adelfo.nfse.nacional.client.NfseHttpClient;
import br.com.adelfo.nfse.nacional.client.NfseHttpException;
import br.com.adelfo.nfse.nacional.client.TipoAmbiente;
import br.com.adelfo.nfse.nacional.service.dto.request.ConsultaDpsRequest;
import br.com.adelfo.nfse.nacional.service.dto.request.ConsultaNfseRequest;
import br.com.adelfo.nfse.nacional.service.dto.response.ConsultaDpsResponse;
import br.com.adelfo.nfse.nacional.service.dto.response.ConsultaNfseResponse;
import br.com.adelfo.nfse.nacional.service.exception.NfseException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Varre as rotas de <b>consulta</b> da Sefin Nacional em produção restrita. Nenhum documento é
 * emitido: todas as chamadas são GET ou HEAD.
 *
 * <p>Serve tanto como teste de fumaça do transporte quanto como levantamento do que o ambiente
 * responde para o certificado em uso — em especial o convênio do município, que é o pré-requisito
 * de qualquer emissão.
 *
 * <p>As rotas de parâmetros municipais ainda não estão na fachada; aqui são chamadas direto pelo
 * {@link NfseHttpClient} e o corpo bruto é impresso.
 *
 * <pre>
 * mvn test -pl nfse-nacional-service -DfailIfNoTests=false \
 *     -Dsurefire.failIfNoSpecifiedTests=false -Dtest=ConsultasSefinIT \
 *     -Dnfse.municipio=3550308
 * </pre>
 */
class ConsultasSefinIT {

    private static TipoAmbiente ambiente;
    private static NfseService service;
    private static NfseHttpClient httpClient;
    private static String cnpjPrestador;

    @BeforeAll
    static void setup() throws Exception {
        ambiente = CertificadoDeTeste.ambiente();
        CertificadoDigital certificado = CertificadoDeTeste.carregar(ambiente);
        cnpjPrestador = CertificadoDeTeste.inscricaoFederalDoCertificado(certificado);

        httpClient = new NfseHttpClient(certificado, Duration.ofSeconds(15), Duration.ofSeconds(30));
        service = new NfseServiceImpl(certificado, httpClient);

        System.out.println("[setup] Prestador   : " + cnpjPrestador);
        System.out.println("[setup] Sefin       : " + ambiente.getUrlSefinNacional());
    }

    /**
     * Descobre em qual host/base cada rota de consulta realmente atende. O manual descreve
     * "Parâmetros Municipais" como parte do Emissor Público, mas a página de APIs publica uma base
     * própria no ADN — este teste resolve a divergência contra o ambiente em vez de por leitura.
     */
    @Test
    void mapearOndeCadaRotaAtende() {
        String municipio = DpsDeTeste.municipio();
        String adn = ambiente.getUrlAdn();
        String sefin = ambiente.getUrlSefinNacional();

        System.out.println();
        System.out.println("=== mapeamento das rotas de consulta ===");
        String chaveReal = System.getProperty("nfse.chave", "1".repeat(49) + "9");
        for (String url : new String[]{
                adn + "/danfse/swagger/docs/v1",
                adn + "/danfse/swagger/v1/swagger.json",
                adn + "/danfse/docs/v1/swagger.json",
                adn + "/danfse/swagger.json",
                adn + "/danfse/" + chaveReal,
                adn + "/danfse/danfse/" + chaveReal,
                sefin + "/DANFSe/" + chaveReal,
                sefin + "/DANFSe?chaveAcesso=" + chaveReal,
        }) {
            System.out.println(sondar(url));
        }
    }

    // -------------------------------------------------------------------------
    // Parâmetros municipais — ainda fora da fachada
    // -------------------------------------------------------------------------

    /**
     * Base dos parâmetros municipais, confirmada contra o ambiente: <b>ADN</b>, prefixo
     * {@code /parametrizacao}, e o código do município logo em seguida. O manual descreve o path
     * como {@code /parametros_municipais/{cMun}/…} sob o Emissor Público — esse segmento não
     * existe em host nenhum, e na Sefin a chamada volta como página HTML do ASP.NET.
     */
    private static String parametrizacao(String caminho) {
        return ambiente.getUrlAdn() + "/parametrizacao/" + caminho;
    }

    @Test
    void convenioDoMunicipio() {
        String municipio = DpsDeTeste.municipio();
        System.out.println();
        System.out.println("=== convênio do município " + municipio + " ===");
        System.out.println(sondar(parametrizacao(municipio + "/convenio")));
    }

    @Test
    void aliquotasDoServicoNoMunicipio() {
        String municipio = DpsDeTeste.municipio();
        System.out.println();
        System.out.println("=== alíquotas do serviço " + DpsDeTeste.codigoServico()
                + " em " + municipio + " ===");
        System.out.println(sondar(parametrizacao(municipio + "/" + DpsDeTeste.codigoServico())));
    }

    @Test
    void retencoesDoContribuinteNoMunicipio() {
        String municipio = DpsDeTeste.municipio();
        System.out.println();
        System.out.println("=== retenções do contribuinte " + cnpjPrestador + " em " + municipio + " ===");
        System.out.println(sondar(parametrizacao(municipio + "/" + cnpjPrestador)));
    }

    // -------------------------------------------------------------------------
    // Consultas já cobertas pela fachada
    // -------------------------------------------------------------------------

    @Test
    void consultarPorIdDps_deDpsInexistente() {
        String idDps = DpsBuilder.novo()
                .ambiente(ambiente)
                .municipioEmissor(DpsDeTeste.municipio())
                .identificacao("99999", "999999999999999", LocalDate.now())
                .prestadorCnpj(cnpjPrestador)
                .id();

        System.out.println();
        System.out.println("=== consultarPorIdDps (inexistente) ===");
        System.out.println("idDps : " + idDps);

        ConsultaDpsResponse resposta = service.consultarPorIdDps(new ConsultaDpsRequest(ambiente, idDps));

        System.out.println("gerada: " + resposta.gerada());
        System.out.println("chave : " + resposta.chaveAcesso());
        // gerada=false vem de um 404. Sem olhar o corpo não dá para saber se o 404 significa
        // "não há NFS-e para esta DPS" ou "esta rota não existe" — e a diferença é enorme.
        System.out.println(sondar(ambiente.getUrlSefinNacional() + "/dps/" + idDps));
        System.out.println(sondar(ambiente.getUrlSefinNacional() + "/nfse/" + "1".repeat(49) + "9"));
        assertNotNull(resposta);
    }

    /**
     * Consulta uma NFS-e real. Só roda com {@code -Dnfse.chave=<50 dígitos>}; sem isso não há
     * chave válida para consultar sem antes emitir, e este teste não emite.
     */
    @Test
    void consultarPorChave_quandoUmaChaveForInformada() {
        String chave = System.getProperty("nfse.chave");
        assumeTrue(chave != null && !chave.isBlank(),
                "Teste ignorado: informe -Dnfse.chave=<chave de 50 dígitos> para consultar uma NFS-e real");

        System.out.println();
        System.out.println("=== consultarPorChave ===");
        try {
            ConsultaNfseResponse resposta = service.consultarPorChave(new ConsultaNfseRequest(ambiente, chave));
            System.out.println(resposta.xmlNfse());
        } catch (NfseException e) {
            System.out.println("Rejeição [" + e.getCodigo() + "]: " + e.getMensagem());
            System.out.println("corpo: " + e.getCorpoResposta());
            throw e;
        }
    }

    /**
     * A consulta por chave de uma nota inexistente deve chegar como rejeição de negócio com o
     * código da Sefin (E2401), não como erro HTTP genérico.
     */
    @Test
    void consultarPorChave_inexistente_viraRejeicaoComCodigoDaSefin() {
        System.out.println();
        System.out.println("=== consultarPorChave (chave inexistente) ===");

        NfseException e = assertThrows(NfseException.class, () -> service.consultarPorChave(
                new ConsultaNfseRequest(ambiente, "1".repeat(49) + "9")));

        System.out.println("código  : " + e.getCodigo());
        System.out.println("mensagem: " + e.getMensagem());

        assertEquals("E2401", e.getCodigo(), "o código de negócio da Sefin deve ser extraído do envelope");
    }

    /** Rota de consulta de eventos, ainda fora da fachada. */
    @Test
    void consultarEventos_quandoUmaChaveForInformada() {
        String chave = System.getProperty("nfse.chave");
        assumeTrue(chave != null && !chave.isBlank(),
                "Teste ignorado: informe -Dnfse.chave=<chave de 50 dígitos>");

        imprimirGet("eventos da NFS-e " + chave, "/nfse/" + chave + "/eventos");
    }

    // -------------------------------------------------------------------------

    /**
     * GET cru que nunca falha o teste: o objetivo é registrar o que o ambiente responde, inclusive
     * quando responde erro. Um 404 aqui é informação, não defeito.
     */
    private static void imprimirGet(String descricao, String caminho) {
        System.out.println();
        System.out.println("=== " + descricao + " ===");
        System.out.println(sondar(ambiente.getUrlSefinNacional() + caminho));
    }

    /**
     * Resultado compacto de um GET: status e natureza do corpo.
     *
     * <p>A Sefin roda em ASP.NET e devolve uma página HTML de ~40 linhas para rota inexistente.
     * Despejar isso a cada chamada afoga a informação útil, e — mais importante — é como se
     * distingue "a rota não existe" de "o recurso não existe": a primeira vem como HTML do
     * servidor de aplicação, a segunda como JSON da API.
     */
    static String sondar(String url) {
        try {
            return "GET " + url + "\n  HTTP 200\n  " + resumo(httpClient.get(url));
        } catch (NfseHttpException e) {
            return "GET " + url + "\n  HTTP " + e.getStatus() + "\n  " + resumo(e.getCorpo());
        } catch (Exception e) {
            Throwable causa = e;
            while (causa.getCause() != null) {
                causa = causa.getCause();
            }
            return "GET " + url + "\n  falha de transporte: " + causa;
        }
    }

    private static String resumo(String corpo) {
        if (corpo == null || corpo.isBlank()) {
            return "(corpo vazio)";
        }
        String limpo = corpo.strip();
        if (limpo.startsWith("<!DOCTYPE html") || limpo.startsWith("<html")) {
            boolean rotaInexistente = limpo.contains("The resource cannot be found");
            return "página HTML do ASP.NET"
                    + (rotaInexistente ? " — ROTA NÃO EXISTE neste host/base" : "");
        }
        return "corpo da API: " + (limpo.length() > 3000 ? limpo.substring(0, 3000) + "…" : limpo);
    }
}
