package br.com.adelfo.nfse.nacional.service;

import br.com.adelfo.nfse.nacional.service.ListaServicoNacional.ServicoNacional;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A tabela é dado gerado do ANEXO_B; estes testes travam a geração contra o que o anexo diz.
 */
class ListaServicoNacionalTest {

    @Test
    void tabela_temOs338CodigosDoAnexo() {
        // 581 linhas no anexo, das quais 242 são cabeçalhos de item/subitem sem código.
        assertEquals(338, ListaServicoNacional.todos().size());
    }

    @Test
    void todoCodigo_temSeisDigitosEBateComItemSubitemDesdobro() {
        for (ServicoNacional s : ListaServicoNacional.todos()) {
            assertTrue(s.codigo().matches("[0-9]{6}"), "código fora do formato: " + s.codigo());
            assertEquals(s.codigo(),
                    String.format("%02d%02d%02d", s.item(), s.subitem(), s.desdobro()),
                    "código não é a concatenação de item, subitem e desdobro: " + s.codigo());
            assertFalse(s.descricao().isBlank(), "descrição vazia em " + s.codigo());
        }
    }

    @Test
    void codigoDaNotaRealDaAdelfo_estaNaTabela() {
        ServicoNacional s = ListaServicoNacional.buscar("010401").orElseThrow();

        assertEquals(1, s.item());
        assertEquals(4, s.subitem());
        assertEquals(1, s.desdobro());
        assertTrue(s.descricao().startsWith("Elaboração de programas de computadores"), s.descricao());
        assertFalse(s.semIncidencia());
    }

    @Test
    void item99_eOUnicoSemIncidencia() {
        Set<String> semIncidencia = ListaServicoNacional.todos().stream()
                .filter(ServicoNacional::semIncidencia)
                .map(ServicoNacional::codigo)
                .collect(Collectors.toSet());

        assertEquals(Set.of("990101"), semIncidencia);
    }

    @Test
    void subitem1502_temOitoDesdobros() {
        // É o caso que mostra para que serve o desdobro nacional: ele é mais fino que a LC 116.
        long desdobros = ListaServicoNacional.todos().stream()
                .filter(s -> s.item() == 15 && s.subitem() == 2)
                .count();

        assertEquals(8, desdobros);
        assertTrue(ListaServicoNacional.descricao("150201").orElseThrow().contains("no País"));
        assertTrue(ListaServicoNacional.descricao("150202").orElseThrow().contains("no exterior"));
    }

    @Test
    void codigoInexistente_naoEncontrado() {
        assertFalse(ListaServicoNacional.existe("999999"));
        assertTrue(ListaServicoNacional.buscar("999999").isEmpty());
        // 010400 é linha de agrupamento no anexo (desdobro 0) e não tem código utilizável.
        assertFalse(ListaServicoNacional.existe("010400"));
    }
}
