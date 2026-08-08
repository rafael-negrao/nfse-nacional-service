package br.com.adelfo.nfse.nacional.service;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Municípios do IBGE — o domínio de {@code cLocEmi}, {@code cLocPrestacao}, {@code cLocIncid} e
 * {@code cMun}, todos códigos de 7 dígitos.
 *
 * <p>Dados de {@code ANEXO_A-MUNICIPIO_IBGE-PAISES_ISO2-v1.00-SNNFSe-20251210}: 5.570 municípios
 * mais a <b>localidade geral</b> {@code 0000000} (Águas Marítimas), que a aba {@code TAB.LOC.GERAL}
 * traz à parte e que também é valor válido nesses campos — daí estar no mesmo catálogo, marcada
 * por {@link Municipio#ehLocalidadeGeral()}.
 *
 * <p><b>A sigla da UF é derivada dos 2 primeiros dígitos do código</b>, pelo padrão IBGE. A coluna
 * "Sigla UF" do anexo só vem preenchida em 450 das 5.570 linhas, então a derivação é o único jeito
 * de tê-la para todas. Ela foi conferida por três caminhos independentes na geração: contra essas
 * 450 linhas, contra o conjunto de prefixos presentes no anexo, e contra o enum {@code TSUF} do
 * XSD — que traz exatamente as mesmas 27 siglas. {@code MunicipiosDerivacaoUfTest} repete as
 * conferências sobre o arquivo publicado.
 *
 * <p>Como os demais catálogos, é dado versionado no jar e envelhece: municípios são criados e
 * renomeados. Ver {@link ListaServicoNacional} sobre regeneração.
 */
public final class TabelaMunicipios {

    public static final String VERSAO_ANEXO = "ANEXO_A v1.00 (20251210)";

    private static final Map<String, Municipio> POR_CODIGO = TabelaTsv.carregar(
            "/tabelas/municipios-ibge-v1.00.tsv",
            c -> c[0],
            c -> new Municipio(c[0], c[1], c[2]));

    private TabelaMunicipios() {
    }

    public static boolean existe(String codigoIbge) {
        return POR_CODIGO.containsKey(codigoIbge);
    }

    public static Optional<Municipio> buscar(String codigoIbge) {
        return Optional.ofNullable(POR_CODIGO.get(codigoIbge));
    }

    /**
     * Nome do município — é o que vai em {@code xLocEmi}, {@code xLocPrestacao} e
     * {@code xLocIncid} na emissão por decisão judicial.
     */
    public static Optional<String> nome(String codigoIbge) {
        return buscar(codigoIbge).map(Municipio::nome);
    }

    public static Collection<Municipio> todos() {
        return POR_CODIGO.values();
    }

    /**
     * @param codigoIbge código de 7 dígitos
     * @param uf         sigla da unidade da federação; vazia na localidade geral
     * @param nome       nome oficial do município
     */
    public record Municipio(String codigoIbge, String uf, String nome) {

        /** {@code true} para Águas Marítimas, que não pertence a UF alguma. */
        public boolean ehLocalidadeGeral() {
            return uf == null || uf.isBlank();
        }
    }
}
