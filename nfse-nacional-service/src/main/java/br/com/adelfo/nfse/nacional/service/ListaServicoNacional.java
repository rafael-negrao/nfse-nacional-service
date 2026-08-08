package br.com.adelfo.nfse.nacional.service;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Lista de Serviços Nacional — o domínio do {@code cTribNac}.
 *
 * <p>O código tem 6 dígitos em três níveis de 2: <b>item</b> e <b>subitem</b> da LC 116/2003, mais
 * o <b>desdobro nacional</b>, que é mais fino que a lei. O subitem 15.02, por exemplo, se abre em
 * oito desdobros ({@code 150201}–{@code 150208}) separando conta-corrente no país e no exterior,
 * investimento, poupança e outras.
 *
 * <p>Os dados vêm de {@code ANEXO_B-NBS2-LISTA_SERVICO_NACIONAL-SNNFSe-v1.01-20260122}, aba
 * {@code LISTA.SERV.NAC.}. Só as linhas com desdobro têm código e entram aqui: das 581 linhas do
 * anexo, 242 são cabeçalhos de item e subitem, sem código, e ficam de fora. Restam <b>338</b>.
 *
 * <p><b>Este é dado versionado dentro do jar e envelhece.</b> Quando a Receita publicar um anexo
 * novo, regenere {@code tabelas/servicos-nacionais-v1.01.tsv} a partir dele. Por isso a validação
 * do catálogo tem escape: ver {@code DpsBuilder.semValidacaoDeCatalogo()}.
 */
public final class ListaServicoNacional {

    /** Versão do anexo que originou a tabela — aparece nas mensagens de erro. */
    public static final String VERSAO_ANEXO = "ANEXO_B v1.01 (20260122)";

    private static final String RECURSO = "/tabelas/servicos-nacionais-v1.01.tsv";

    private static final Map<String, ServicoNacional> POR_CODIGO = TabelaTsv.carregar(
            RECURSO, c -> c[0], c -> new ServicoNacional(c[0], c[1]));

    private ListaServicoNacional() {
    }

    /** {@code true} se o código consta na lista. */
    public static boolean existe(String cTribNac) {
        return POR_CODIGO.containsKey(cTribNac);
    }

    /** O serviço, se o código existir. */
    public static Optional<ServicoNacional> buscar(String cTribNac) {
        return Optional.ofNullable(POR_CODIGO.get(cTribNac));
    }

    /**
     * Descrição oficial do código — é o que vai em {@code xTribNac} na emissão por decisão
     * judicial, onde o preenchimento é do contribuinte.
     */
    public static Optional<String> descricao(String cTribNac) {
        return buscar(cTribNac).map(ServicoNacional::descricao);
    }

    /** Todos os serviços, na ordem do anexo. */
    public static Collection<ServicoNacional> todos() {
        return POR_CODIGO.values();
    }

    /**
     * Um serviço da lista nacional.
     *
     * @param codigo    {@code cTribNac} de 6 dígitos
     * @param descricao texto oficial, que alimenta o {@code xTribNac}
     */
    public record ServicoNacional(String codigo, String descricao) {

        /** Item da LC 116/2003 — os 2 primeiros dígitos. */
        public int item() {
            return Integer.parseInt(codigo.substring(0, 2));
        }

        /** Subitem da LC 116/2003 — os 2 dígitos do meio. */
        public int subitem() {
            return Integer.parseInt(codigo.substring(2, 4));
        }

        /** Desdobro nacional — os 2 últimos dígitos. */
        public int desdobro() {
            return Integer.parseInt(codigo.substring(4, 6));
        }

        /** {@code true} para o item 99, que reúne os serviços sem incidência de ISSQN e ICMS. */
        public boolean semIncidencia() {
            return item() == 99;
        }
    }
}
