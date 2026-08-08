package br.com.adelfo.nfse.nacional.service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Leitura das tabelas de domínio geradas dos anexos oficiais.
 *
 * <p>As três — serviços, municípios e indicadores de operação — têm o mesmo formato: TSV em UTF-8,
 * com linhas de comentário iniciadas por {@code #} e a chave na primeira coluna. Centralizar a
 * leitura evita repetir o mesmo laço em cada catálogo.
 */
final class TabelaTsv {

    private TabelaTsv() {
    }

    /**
     * Carrega um recurso TSV num mapa que preserva a ordem do arquivo.
     *
     * @param recurso caminho no classpath
     * @param chave   extrai a chave das colunas da linha
     * @param valor   monta o valor a partir das colunas da linha
     */
    static <V> Map<String, V> carregar(String recurso,
                                       Function<String[], String> chave,
                                       Function<String[], V> valor) {
        Map<String, V> mapa = new LinkedHashMap<>();
        try (InputStream in = TabelaTsv.class.getResourceAsStream(recurso)) {
            if (in == null) {
                throw new IllegalStateException("tabela ausente no jar: " + recurso);
            }
            BufferedReader leitor = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String linha;
            while ((linha = leitor.readLine()) != null) {
                if (linha.isBlank() || linha.startsWith("#")) {
                    continue;
                }
                String[] colunas = linha.split("\t", -1);
                mapa.put(chave.apply(colunas), valor.apply(colunas));
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("falha ao ler a tabela " + recurso, e);
        }
        return Map.copyOf(mapa);
    }
}
