package br.com.adelfo.nfse.nacional.service;

import br.com.adelfo.nfse.nacional.client.TipoAmbiente;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Valida o XML produzido pelo {@link DpsBuilder} contra o XSD oficial {@code DPS_v1.01.xsd},
 * empacotado em {@code nfse-nacional-schemas}.
 *
 * <p>Não exige certificado nem rede: {@code ds:Signature} é opcional em {@code TCDPS}, então a
 * DPS não assinada já é validável — que é exatamente o artefato que o builder entrega.
 */
class DpsBuilderTest {

    private static final String CNPJ = "12345678000195";
    private static final String MUNICIPIO_SP = "3550308";

    /**
     * Defeito do XSD oficial v1.01: {@code TSSerieDPS} usa o pattern {@code ^0{0,4}\d{1,5}$}, mas
     * em XML Schema os patterns já são implicitamente ancorados e {@code ^} / {@code $} valem como
     * caracteres literais. Nenhuma série numérica satisfaz esse pattern — só valores absurdos como
     * {@code "^1$"}. A v1.00 não tinha pattern algum neste tipo.
     *
     * <p>O builder emite a série numérica, que é o que a Sefin de fato aceita; aqui a violação é
     * filtrada para que o restante do schema continue guardando o leiaute. Quando o pacote oficial
     * for corrigido, o filtro deixa de casar e os testes seguem passando.
     */
    private static final String DEFEITO_TSSERIEDPS = "TSSerieDPS";

    /** Erros de schema do XML, descontado o defeito conhecido do {@code TSSerieDPS}. */
    private static List<String> errosDeSchema(String xml) throws Exception {
        return ValidacaoXsd.erros("DPS_v1.01.xsd", xml).stream()
                .filter(msg -> !ehDefeitoTSSerieDPS(msg))
                .toList();
    }

    /**
     * O pattern quebrado produz duas mensagens: a violação do facet, que nomeia o tipo, e o erro
     * de tipo em cascata, que nomeia o elemento.
     */
    private static boolean ehDefeitoTSSerieDPS(String mensagem) {
        return mensagem.contains(DEFEITO_TSSERIEDPS) || mensagem.contains("'serie'");
    }

    private static void validar(String xml) throws Exception {
        assertEquals(List.of(), errosDeSchema(xml), "XML deve ser válido no XSD oficial");
    }

    /** DPS mínima que o XSD aceita — a base dos demais testes. */
    private static DpsBuilder dpsMinima() {
        return DpsBuilder.novo()
                .ambiente(TipoAmbiente.PRODUCAO_RESTRITA)
                .municipioEmissor(MUNICIPIO_SP)
                .identificacao("1", "123", LocalDate.of(2026, 8, 1))
                .emitidaPeloPrestador()
                .prestadorCnpj(CNPJ)
                .naoOptanteSimplesNacional()
                .semRegimeEspecial()
                .servicoPrestadoNoMunicipio(MUNICIPIO_SP)
                .servico("010101", "Análise e desenvolvimento de sistemas")
                .valorServico(new BigDecimal("1000.00"))
                .issqnTributavel()
                .issqnNaoRetido()
                .semTotalTributos();
    }

    @Test
    void dpsMinima_eValidaNoSchema() {
        assertDoesNotThrow(() -> validar(dpsMinima().build()));
    }

    @Test
    void dpsCompleta_comTomadorEnderecoDescontosETributos_eValidaNoSchema() {
        String xml = dpsMinima()
                .prestadorNome("Adelfo Sistemas Ltda")
                .prestadorInscricaoMunicipal("123456")
                .prestadorEndereco(DpsBuilder.Endereco.nacional(
                        MUNICIPIO_SP, "01310100", "Avenida Paulista", "1000", "Conjunto 51", "Bela Vista"))
                .prestadorContato("1130000000", "fiscal@adelfo.com.br")
                .tomadorCnpj("98765432000198", "Cliente Exemplo S.A.")
                .tomadorEndereco(DpsBuilder.Endereco.nacional(
                        MUNICIPIO_SP, "04538133", "Avenida Brigadeiro Faria Lima", "3477", null, "Itaim Bibi"))
                .tomadorContato("1140000000", "contas@cliente.com.br")
                .codigoTributacaoMunicipal("101")
                .informacoesComplementares("Contrato 2026/001")
                .descontoIncondicionado(new BigDecimal("50.00"))
                .aliquota(new BigDecimal("2.00"))
                .pisCofins("01", new BigDecimal("1000.00"), new BigDecimal("0.65"),
                        new BigDecimal("3.00"), new BigDecimal("6.50"), new BigDecimal("30.00"))
                .retencoesFederais(null, new BigDecimal("15.00"), null)
                .totalTributos(new BigDecimal("100.00"), BigDecimal.ZERO, new BigDecimal("20.00"))
                .build();

        assertDoesNotThrow(() -> validar(xml));
        assertTrue(xml.contains("<xNome>Cliente Exemplo S.A.</xNome>"));
        assertTrue(xml.contains("<vDescIncond>50.00</vDescIncond>"));
    }

    @Test
    void enderecoNoExterior_eValidoNoSchema() {
        String xml = dpsMinima()
                .tomadorNif("US123456789", "Foreign Client Inc")
                .tomadorEndereco(DpsBuilder.Endereco.exterior(
                        "US", "10001", "New York", "NY", "5th Avenue", "100", null, "Manhattan"))
                .issqnExportacao("US")
                .build();

        assertDoesNotThrow(() -> validar(xml));
        assertTrue(xml.contains("<cPais>US</cPais>"));
    }

    @Test
    void substituicao_geraGrupoSubstEValidaNoSchema() {
        String chaveOriginal = "1".repeat(49) + "9";

        String xml = dpsMinima()
                .substitui(chaveOriginal, "99", "Correção de valor")
                .build();

        assertDoesNotThrow(() -> validar(xml));
        assertTrue(xml.contains("<chSubstda>" + chaveOriginal + "</chSubstda>"));
    }

    @Test
    void id_seguePadraoTSIdDPS() {
        String id = dpsMinima().id();

        // TSIdDPS: DPS + cMun(7) + tipo inscrição(1) + inscrição federal(14) + série(5) + nDPS(15)
        assertEquals(45, id.length());
        assertTrue(id.matches("DPS[0-9]{42}"), "Id fora do pattern TSIdDPS: " + id);
        assertEquals("DPS" + MUNICIPIO_SP + "2" + CNPJ + "00001" + "000000000000123", id);
    }

    @Test
    void id_usaTipoDeInscricao1ParaPrestadorPessoaFisica() {
        String id = DpsBuilder.novo()
                .ambiente(TipoAmbiente.PRODUCAO)
                .municipioEmissor(MUNICIPIO_SP)
                .identificacao("1", "7", LocalDate.of(2026, 8, 1))
                .prestadorCpf("12345678909")
                .id();

        // CPF é completado com 000 à esquerda até 14 posições
        assertEquals("DPS" + MUNICIPIO_SP + "1" + "00012345678909" + "00001" + "000000000000007", id);
    }

    @Test
    void serieENumero_naoRecebemZerosAEsquerdaNoCorpo() {
        // O zero-preenchimento vale só para o Id; os elementos serie e nDPS têm patterns que o
        // proíbem (TSNumDPS = [1-9][0-9]{0,14}). Confundir os dois derruba a validação de schema.
        String xml = dpsMinima().build();

        assertTrue(xml.contains("<serie>1</serie>"));
        assertTrue(xml.contains("<nDPS>123</nDPS>"));
        assertFalse(xml.contains("<nDPS>000000000000123</nDPS>"));
    }

    @Test
    void valoresMonetarios_saemComDuasCasasDecimais() {
        // Os campos TSDec* são xs:string com pattern; escala diferente de 2 é rejeitada no schema.
        String xml = dpsMinima()
                .valorServico(new BigDecimal("1500"))
                .descontoCondicionado(new BigDecimal("10.5"))
                .build();

        assertDoesNotThrow(() -> validar(xml));
        assertTrue(xml.contains("<vServ>1500.00</vServ>"));
        assertTrue(xml.contains("<vDescCond>10.50</vDescCond>"));
    }

    @Test
    void dhEmi_naoPodeTerFracaoDeSegundos() {
        String xml = dpsMinima().build();

        String dhEmi = xml.replaceAll("(?s).*<dhEmi>(.*?)</dhEmi>.*", "$1");
        assertTrue(dhEmi.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}[+-]\\d{2}:\\d{2}"),
                "dhEmi fora do formato AAAA-MM-DDThh:mm:ssTZD: " + dhEmi);
    }

    @Test
    void competencia_saiNoFormatoData() {
        String xml = dpsMinima().build();

        assertTrue(xml.contains("<dCompet>2026-08-01</dCompet>"));
    }

    @Test
    void valorNegativo_eRejeitadoAntesDeGerarXml() {
        // O pattern dos TSDec* não admite sinal; falhar aqui dá uma mensagem melhor que a rejeição
        // opaca da Sefin.
        assertThrows(IllegalArgumentException.class,
                () -> dpsMinima().valorServico(new BigDecimal("-1.00")).build());
    }

    @Test
    void faltandoCampoObrigatorio_falhaComMensagemQueApontaOMetodo() {
        IllegalStateException erro = assertThrows(IllegalStateException.class,
                () -> DpsBuilder.novo()
                        .ambiente(TipoAmbiente.PRODUCAO_RESTRITA)
                        .municipioEmissor(MUNICIPIO_SP)
                        .identificacao("1", "123", LocalDate.of(2026, 8, 1))
                        .prestadorCnpj(CNPJ)
                        .build());

        assertTrue(erro.getMessage().contains("Simples Nacional"), erro.getMessage());
    }

    @Test
    void prestadorSemCnpjNemCpf_naoPodeMontarId() {
        IllegalStateException erro = assertThrows(IllegalStateException.class,
                () -> DpsBuilder.novo()
                        .ambiente(TipoAmbiente.PRODUCAO_RESTRITA)
                        .municipioEmissor(MUNICIPIO_SP)
                        .identificacao("1", "123", LocalDate.of(2026, 8, 1))
                        .prestadorNif("US123")
                        .id());

        assertTrue(erro.getMessage().contains("CNPJ ou CPF"), erro.getMessage());
    }

    @Test
    void serieNumerica_violaOPatternQuebradoDoTSSerieDPS() throws Exception {
        // Fixa o defeito do XSD oficial descrito em DEFEITO_TSSERIEDPS. Se este teste passar a
        // falhar, o pacote de schemas foi corrigido e o filtro de errosDeSchema pode sair.
        List<String> todosOsErros = ValidacaoXsd.erros("DPS_v1.01.xsd", dpsMinima().build());

        assertFalse(todosOsErros.isEmpty(), "o pattern quebrado deveria acusar erro");
        assertTrue(todosOsErros.stream().allMatch(DpsBuilderTest::ehDefeitoTSSerieDPS),
                "só o defeito do TSSerieDPS era esperado: " + todosOsErros);
    }

    @Test
    void namespaces_saemComPrefixosFixos() {
        // Sem prefixos fixos o JAXB alterna entre execuções: ora a NFS-e é o namespace default,
        // ora o XMLDSig assume o default e a NFS-e vira "ns2:". Prefixo instável muda a forma
        // canônica do documento — e é sobre ela que a assinatura é calculada.
        //
        // Mais que isso: o AnexoI, aba RN_RECEPCAO_DPS, rejeita com E1228 o "uso de prefixo de
        // namespace não permitido na área de dados descompactada". Um XML com ns2: seria recusado
        // pela Sefin antes mesmo de chegar às regras de negócio da DPS.
        String xml = dpsMinima().build();

        assertTrue(xml.startsWith("<DPS "), "o elemento raiz deve sair sem prefixo: " + primeiraTag(xml));
        assertTrue(xml.contains("xmlns=\"http://www.sped.fazenda.gov.br/nfse\""),
                "o namespace da NFS-e deve ser o default: " + primeiraTag(xml));
        assertFalse(xml.contains("xmlns=\"http://www.w3.org/2000/09/xmldsig#\""),
                "o XMLDSig nunca deve ser o namespace default: " + primeiraTag(xml));
        assertTrue(xml.contains("<dCompet>"), "elementos filhos também sem prefixo");
    }

    @Test
    void nenhumElementoUsaPrefixoDeNamespace() {
        // Guarda direta da regra E1228: varre todas as tags e exige que nenhuma seja prefixada.
        String xml = dpsMinima().build();

        Matcher m = Pattern.compile("</?([A-Za-z_][\\w.-]*):").matcher(xml);
        List<String> prefixados = new ArrayList<>();
        while (m.find()) {
            prefixados.add(m.group(1));
        }
        assertEquals(List.of(), prefixados,
                "AnexoI/RN_RECEPCAO_DPS E1228 proíbe prefixo de namespace na área de dados");
    }

    private static String primeiraTag(String xml) {
        return xml.substring(0, Math.min(xml.indexOf('>') + 1, xml.length()));
    }

    @Test
    void codigoDeServicoForaDoCatalogo_eRecusadoComAVersaoDoAnexo() {
        // O XSD só exige 6 dígitos; quem sabe se o código existe é a lista de serviços nacional.
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> dpsMinima().servico("999999", "Servico inexistente").build());

        assertTrue(e.getMessage().contains("999999"), e.getMessage());
        assertTrue(e.getMessage().contains(ListaServicoNacional.VERSAO_ANEXO), e.getMessage());
    }

    @Test
    void semValidacaoDeCatalogo_permiteCodigoNovo() {
        // Escape para quando a Receita publicar códigos que a tabela embutida ainda não tem.
        assertDoesNotThrow(() -> dpsMinima()
                .servico("999999", "Servico de anexo mais novo")
                .semValidacaoDeCatalogo()
                .build());
    }

    @Test
    void codigoNbsForaDaHierarquiaFolha_eRecusado() {
        // 10401110 é o nível 1.0401.11, um agrupamento — não vale como cNBS.
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> dpsMinima().codigoNbs("10401110").build());
        assertTrue(e.getMessage().contains("agrupamento"), e.getMessage());

        assertDoesNotThrow(() -> dpsMinima().codigoNbs("104011111").build());
    }

    @Test
    void numeroDpsAcimaDoTamanhoDoId_falhaComMensagemClara() {
        assertThrows(IllegalArgumentException.class,
                () -> dpsMinima().identificacao("1", "1".repeat(16), LocalDate.of(2026, 8, 1)).id());
    }
}
