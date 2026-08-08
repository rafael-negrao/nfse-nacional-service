package br.com.adelfo.nfse.nacional.service;

import br.com.adelfo.nfse.nacional.service.TabelaIndicadoresOperacao.IndicadorOperacao;
import br.com.adelfo.nfse.nacional.service.TabelaMunicipios.Municipio;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tabelas geradas dos anexos A e C; estes testes travam a geração contra o que os anexos dizem.
 */
class TabelasDeDominioTest {

    // ---------------------------------------------------------------------------------------
    // Municípios — ANEXO_A, abas TAB.MUN_IBGE e TAB.LOC.GERAL
    // ---------------------------------------------------------------------------------------

    @Test
    void municipios_saoOs5570MaisALocalidadeGeral() {
        assertEquals(5571, TabelaMunicipios.todos().size());

        long gerais = TabelaMunicipios.todos().stream()
                .filter(Municipio::ehLocalidadeGeral).count();
        assertEquals(1, gerais, "só Águas Marítimas vem da aba TAB.LOC.GERAL");
    }

    @Test
    void todoCodigoIbge_temSeteDigitosENomeENaoRepete() {
        for (Municipio m : TabelaMunicipios.todos()) {
            assertTrue(m.codigoIbge().matches("[0-9]{7}"), "código fora do formato: " + m.codigoIbge());
            assertFalse(m.nome().isBlank(), "nome vazio em " + m.codigoIbge());
        }
    }

    @Test
    void saoPaulo_estaNaTabela() {
        Municipio sp = TabelaMunicipios.buscar("3550308").orElseThrow();

        assertEquals("SP", sp.uf());
        assertEquals("São Paulo", sp.nome());
        assertFalse(sp.ehLocalidadeGeral());
    }

    @Test
    void asUfs_saoAs27EBatemComOPrefixoDoCodigoIbge() {
        // A sigla é derivada do prefixo, então cada UF tem de ter exatamente um prefixo — e o
        // conjunto tem de bater com o enum TSUF do XSD.
        java.util.Map<String, java.util.Set<String>> prefixos = new java.util.HashMap<>();
        for (Municipio m : TabelaMunicipios.todos()) {
            if (m.ehLocalidadeGeral()) {
                continue;
            }
            prefixos.computeIfAbsent(m.uf(), k -> new java.util.HashSet<>())
                    .add(m.codigoIbge().substring(0, 2));
        }

        assertEquals(27, prefixos.size());
        prefixos.forEach((uf, p) -> assertEquals(1, p.size(),
                "UF " + uf + " com prefixos IBGE distintos: " + p));

        java.util.Set<String> doEnum = java.util.Arrays.stream(
                        br.com.adelfo.nfse.nacional.schemas.TSUF.values())
                .map(br.com.adelfo.nfse.nacional.schemas.TSUF::value)
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(doEnum, prefixos.keySet(),
                "as siglas derivadas devem ser exatamente as do enum TSUF do XSD");
    }

    @Test
    void siglasConhecidas_batemComOPrefixo() {
        assertEquals("SP", TabelaMunicipios.buscar("3550308").orElseThrow().uf());
        assertEquals("RJ", TabelaMunicipios.buscar("3304557").orElseThrow().uf());
        assertEquals("DF", TabelaMunicipios.buscar("5300108").orElseThrow().uf());
        assertEquals("RO", TabelaMunicipios.buscar("1100015").orElseThrow().uf());
    }

    @Test
    void aguasMaritimas_eLocalidadeGeralSemUf() {
        // Não é município, mas é valor válido nos campos de localidade — daí estar no catálogo.
        Municipio geral = TabelaMunicipios.buscar("0000000").orElseThrow();

        assertTrue(geral.ehLocalidadeGeral());
        assertTrue(geral.nome().toUpperCase().contains("MAR"), geral.nome());
    }

    @Test
    void codigoInexistente_naoEncontrado() {
        assertFalse(TabelaMunicipios.existe("9999999"));
    }

    // ---------------------------------------------------------------------------------------
    // Países — ANEXO_A, aba TAB.PAÍS_ISO2
    // ---------------------------------------------------------------------------------------

    @Test
    void paises_saoOs250DoAnexo() {
        assertEquals(250, TabelaPaises.codigos().size());
        assertTrue(TabelaPaises.codigos().stream().allMatch(c -> c.matches("[A-Z]{2}")));
    }

    @Test
    void brasilEEstadosUnidos_estaoNaTabela() {
        assertTrue(TabelaPaises.nome("BR").orElseThrow().contains("Brasil"));
        assertTrue(TabelaPaises.existe("US"));
        assertFalse(TabelaPaises.existe("XX"));
    }

    // ---------------------------------------------------------------------------------------
    // NBS 2.0 — ANEXO_B, aba LISTA.NBS_v2.0
    // ---------------------------------------------------------------------------------------

    @Test
    void nbs_saoAs918FolhasDoAnexo() {
        // 1210 linhas no anexo, das quais 292 são níveis de agrupamento (5, 6, 7 e 8 dígitos).
        assertEquals(918, TabelaNbs.codigos().size());
        assertTrue(TabelaNbs.codigos().stream().allMatch(c -> c.matches("[0-9]{9}")));
    }

    @Test
    void niveisDeAgrupamento_naoSaoCodigosValidos() {
        // 1.0401.11 e 1.0401.11.1 agrupam as folhas abaixo deles; só as folhas vão na DPS.
        assertFalse(TabelaNbs.existe("10401110"), "1.0401.11 tem 8 dígitos, não é folha");
        assertTrue(TabelaNbs.existe("104011111"));
        assertTrue(TabelaNbs.existe("104011119"));
        assertTrue(TabelaNbs.existe("104011120"));
    }

    @Test
    void codigoGenerico_existeMasNaoTemDescricaoNoAnexo() {
        // É o que a NFS-e real de produção usa; o anexo o publica sem texto.
        assertTrue(TabelaNbs.existe("999999999"));
        assertTrue(TabelaNbs.descricao("999999999").isEmpty());
    }

    @Test
    void formatar_reproduzAFormaPontuadaDoAnexo() {
        assertEquals("1.0101.11.00", TabelaNbs.formatar("101011100"));
        assertEquals("Serviços de construção de edificações residenciais de um e dois pavimentos",
                TabelaNbs.descricao("101011100").orElseThrow());
    }

    // ---------------------------------------------------------------------------------------
    // Indicadores de operação — ANEXO_C
    // ---------------------------------------------------------------------------------------

    @Test
    void indicadores_saoOs26DoAnexo() {
        assertEquals(26, TabelaIndicadoresOperacao.todos().size());

        for (IndicadorOperacao i : TabelaIndicadoresOperacao.todos()) {
            assertTrue(i.codigo().matches("[0-9]{6}"), "código fora do formato: " + i.codigo());
            assertFalse(i.caracteristica().isBlank(), "sem característica em " + i.codigo());
            assertFalse(i.localAIdentificar().isBlank(), "sem local em " + i.codigo());
        }
    }

    @Test
    void indicadorDeBemImovel_apontaALocalidadeDoImovel() {
        IndicadorOperacao i = TabelaIndicadoresOperacao.buscar("020201").orElseThrow();

        assertTrue(i.caracteristica().contains("bem imóvel"), i.caracteristica());
        assertTrue(i.localAIdentificar().contains("imóvel"), i.localAIdentificar());
    }

    @Test
    void codigoDeIndicadorInexistente_naoEncontrado() {
        assertFalse(TabelaIndicadoresOperacao.existe("999999"));
    }
}
