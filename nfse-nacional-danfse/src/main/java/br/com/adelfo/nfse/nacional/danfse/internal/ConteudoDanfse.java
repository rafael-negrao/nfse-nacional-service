package br.com.adelfo.nfse.nacional.danfse.internal;

import br.com.adelfo.nfse.nacional.service.TabelaMunicipios;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static br.com.adelfo.nfse.nacional.danfse.internal.Formatos.VAZIO;
import static br.com.adelfo.nfse.nacional.danfse.internal.Formatos.juntar;
import static br.com.adelfo.nfse.nacional.danfse.internal.Formatos.limitar;
import static br.com.adelfo.nfse.nacional.danfse.internal.Formatos.ouTraco;

/**
 * O conteúdo de cada campo do DANFSe, já formatado — a coluna "Caminho no XML" da tabela do item
 * 2.4.5 da NT 008, transformada em código. Não desenha nada: o {@link DesenhoDanfse} posiciona.
 *
 * <p>Os tamanhos máximos e as reticências seguem a coluna "Tam. do Campo" da mesma tabela.
 */
public final class ConteudoDanfse {

    private static final String DPS = "DPS/infDPS/";

    /** Nota 6: a linha de PIS/COFINS próprios só vale para competências de 2026. */
    private static final LocalDate FIM_LINHA_PIS_COFINS = LocalDate.of(2026, 12, 31);

    // Descrições das opções do leiaute, copiadas da documentação dos tipos no XSD.
    private static final Map<String, String> EMITENTE = Map.of(
            "1", "Prestador", "2", "Tomador", "3", "Intermediário");
    private static final Map<String, String> SITUACAO = Map.of(
            "100", "NFS-e Gerada", "101", "NFS-e de Substituição Gerada",
            "102", "NFS-e de Decisão Judicial", "103", "NFS-e Avulsa", "107", "NFS-e MEI");
    private static final Map<String, String> AMBIENTE_GERADOR = Map.of(
            "1", "Prefeitura", "2", "Sistema Nacional da NFS-e");
    private static final Map<String, String> TIPO_AMBIENTE = Map.of(
            "1", "Produção", "2", "Homologação");
    private static final Map<String, String> FINALIDADE = Map.of("0", "NFS-e regular");
    private static final Map<String, String> SIMPLES = Map.of(
            "1", "Não Optante",
            "2", "Optante - Microempreendedor Individual (MEI)",
            "3", "Optante - Microempresa ou Empresa de Pequeno Porte (ME/EPP)");
    private static final Map<String, String> REGIME_APURACAO_SN = Map.of(
            "1", "Regime de apuração dos tributos federais e municipal pelo Simples Nacional",
            "2", "Regime de apuração dos tributos federais pelo SN e o ISSQN por fora do SN conforme "
                    + "respectiva legislação municipal do tributo",
            "3", "Regime de apuração dos tributos federais e municipal por fora do SN conforme "
                    + "respectivas legislações federal e municipal de cada tributo");
    private static final Map<String, String> REGIME_ESPECIAL = Map.of(
            "0", "Nenhum", "1", "Ato Cooperado (Cooperativa)", "2", "Estimativa",
            "3", "Microempresa Municipal", "4", "Notário ou Registrador", "5", "Profissional Autônomo",
            "6", "Sociedade de Profissionais", "9", "Outros");
    private static final Map<String, String> TRIBUTACAO_ISSQN = Map.of(
            "1", "Operação Tributável", "2", "Imunidade", "3", "Exportação de Serviço",
            "4", "Não Incidência");
    private static final Map<String, String> IMUNIDADE = Map.of(
            "0", "Imunidade (tipo não informado na nota de origem)",
            "1", "Patrimônio, renda ou serviços, uns dos outros",
            "2", "Templos de qualquer culto",
            "3", "Patrimônio, renda ou serviços dos partidos políticos, entidades sindicais, "
                    + "instituições de educação e de assistência social",
            "4", "Livros, jornais, periódicos e o papel destinado a sua impressão",
            "5", "Fonogramas e videofonogramas musicais produzidos no Brasil");
    private static final Map<String, String> SUSPENSAO = Map.of(
            "1", "Exigibilidade Suspensa por Decisão Judicial",
            "2", "Exigibilidade Suspensa por Processo Administrativo");
    private static final Map<String, String> BENEFICIO = Map.of(
            "1", "Isenção", "2", "Redução da BC em %", "3", "Redução da BC em R$",
            "4", "Alíquota Diferenciada");
    private static final Map<String, String> RETENCAO_ISSQN = Map.of(
            "1", "Não Retido", "2", "Retido pelo Tomador", "3", "Retido pelo Intermediário");
    private static final Map<String, String> RETENCAO_PIS_COFINS = Map.of(
            "0", "PIS/COFINS/CSLL Não Retidos", "1", "PIS/COFINS Retidos",
            "2", "PIS/COFINS Não Retidos", "3", "PIS/COFINS/CSLL Retidos",
            "4", "PIS/COFINS Retidos, CSLL Não Retido", "5", "PIS Retido, COFINS/CSLL Não Retido",
            "6", "COFINS Retido, PIS/CSLL Não Retido", "7", "PIS Não Retido, COFINS/CSLL Retidos",
            "8", "PIS/COFINS Não Retidos, CSLL Retido", "9", "COFINS Não Retido, PIS/CSLL Retidos");

    private final XmlNfse x;

    public ConteudoDanfse(XmlNfse xml) {
        this.x = xml;
    }

    // ===================================================================================
    // Cabeçalho e identificação
    // ===================================================================================

    /** Chave de acesso: o Id da NFS-e sem o prefixo "NFS". */
    public String chaveAcesso() {
        String id = x.id();
        return id != null && id.startsWith("NFS") ? id.substring(3) : ouTraco(id);
    }

    /** Ambiente de testes: obriga a expressão "NFS-e SEM VALIDADE JURÍDICA" no cabeçalho. */
    public boolean homologacao() {
        return "2".equals(x.texto(DPS + "tpAmb"));
    }

    /**
     * "Município: São Paulo / SP". Não exibir quando o item do código de tributação nacional for
     * 99 — serviços sem incidência de ISSQN, em que o município emissor não tem papel.
     */
    public String municipioEmissor() {
        String cTribNac = x.texto(DPS + "serv/cServ/cTribNac");
        if (cTribNac != null && cTribNac.startsWith("99")) {
            return "";
        }
        String uf = x.texto("emit/enderNac/UF");
        String nome = x.texto("xLocEmi") != null ? x.texto("xLocEmi")
                : buscarMunicipio(x.texto(DPS + "cLocEmi")).map(TabelaMunicipios.Municipio::nome).orElse(null);
        return limitar("Município: " + juntar(" / ", nome, uf), 47);
    }

    public String ambienteGerador() {
        return "Ambiente Gerador: " + descricao(AMBIENTE_GERADOR, x.texto("ambGer"));
    }

    public String tipoAmbiente() {
        return "Tipo de Ambiente: " + descricao(TIPO_AMBIENTE, x.texto(DPS + "tpAmb"));
    }

    public String numeroNfse() {
        return ouTraco(x.texto("nNFSe"));
    }

    public String competencia() {
        return ouTraco(Formatos.data(x.texto(DPS + "dCompet")));
    }

    public String dataHoraEmissaoNfse() {
        return ouTraco(Formatos.dataHora(x.texto("dhProc")));
    }

    public String numeroDps() {
        return ouTraco(x.texto(DPS + "nDPS"));
    }

    public String serieDps() {
        return ouTraco(x.texto(DPS + "serie"));
    }

    public String dataHoraEmissaoDps() {
        return ouTraco(Formatos.dataHora(x.texto(DPS + "dhEmi")));
    }

    public String emitente() {
        return descricao(EMITENTE, x.texto(DPS + "tpEmit"));
    }

    public String situacao() {
        return limitar(descricao(SITUACAO, x.texto("cStat")), 40);
    }

    /** Antes da NT 009 o {@code finNFSe} ficava no grupo IBS/CBS; agora, na raiz da DPS. */
    public String finalidade() {
        return limitar(descricao(FINALIDADE, x.primeiro(DPS + "finNFSe", DPS + "IBSCBS/finNFSe")), 40);
    }

    /** Endereço do QR Code — item 2.4.3. */
    public String urlConsultaPublica() {
        return "https://www.nfse.gov.br/ConsultaPublica/?tpc=1&chave=" + chaveAcesso();
    }

    // ===================================================================================
    // Pessoas
    // ===================================================================================

    /**
     * Prestador. Com {@code tpEmit = 1} a DPS <b>não pode</b> trazer nome nem endereço do prestador
     * ({@code E0121}, {@code E0128}): a plataforma os tira do cadastro e grava em
     * {@code infNFSe/emit}. A NT 008 aponta só para {@code DPS/prest}, o que deixaria esses campos
     * em branco em toda nota emitida pelo próprio prestador — por isso o {@code emit} entra como
     * segunda fonte.
     */
    public Pessoa prestador() {
        String p = DPS + "prest/";
        // O emit só descreve o prestador quando é ele quem emite; senão a fonte é a própria DPS.
        String e = "1".equals(x.texto(DPS + "tpEmit")) ? "emit/" : p;
        boolean enderecoNoEmit = !x.existe(p + "end") && e.equals("emit/");
        return new Pessoa(
                documento(p),
                ouTraco(x.primeiro(p + "IM", e + "IM")),
                telefone(x.primeiro(p + "fone", e + "fone")),
                limitar(ouTraco(x.primeiro(p + "xNome", e + "xNome")), 80),
                enderecoNoEmit ? municipioEmit() : municipio(p + "end/"),
                enderecoNoEmit ? codigoECepEmit() : codigoECep(p + "end/"),
                enderecoNoEmit ? endereco("emit/enderNac/") : endereco(p + "end/"),
                limitar(ouTraco(x.primeiro(p + "email", e + "email")), 80));
    }

    /** {@code null} quando a NFS-e não identifica o tomador — o bloco vira a mensagem da Nota 2. */
    public Pessoa tomador() {
        return pessoa(DPS + "toma/");
    }

    public Pessoa intermediario() {
        return pessoa(DPS + "interm/");
    }

    /**
     * Destinatário da operação, do grupo IBS/CBS. {@link #destinatarioEhOTomador()} cobre o
     * {@code indDest = 0}, que imprime a mensagem da Nota 3 em vez do bloco.
     */
    public Pessoa destinatario() {
        return pessoa(DPS + "IBSCBS/dest/");
    }

    public boolean destinatarioEhOTomador() {
        return "0".equals(x.primeiro(DPS + "indDest", DPS + "IBSCBS/indDest"));
    }

    public String simplesNacional() {
        return limitar(descricao(SIMPLES, x.texto(DPS + "prest/regTrib/opSimpNac")), 40);
    }

    public String regimeApuracaoSimples() {
        return limitar(descricao(REGIME_APURACAO_SN, x.texto(DPS + "prest/regTrib/regApTribSN")), 80);
    }

    private Pessoa pessoa(String base) {
        if (!x.existe(base.substring(0, base.length() - 1))) {
            return null;
        }
        return new Pessoa(documento(base), ouTraco(x.texto(base + "IM")), telefone(x.texto(base + "fone")),
                limitar(ouTraco(x.texto(base + "xNome")), 80), municipio(base + "end/"),
                codigoECep(base + "end/"), endereco(base + "end/"), limitar(ouTraco(x.texto(base + "email")), 80));
    }

    private String documento(String base) {
        String cnpj = x.texto(base + "CNPJ");
        if (cnpj != null) {
            return Formatos.cnpj(cnpj);
        }
        String cpf = x.texto(base + "CPF");
        if (cpf != null) {
            return Formatos.cpf(cpf);
        }
        return limitar(ouTraco(x.texto(base + "NIF")), 40);
    }

    private static String telefone(String fone) {
        return limitar(ouTraco(Formatos.telefone(fone)), 20);
    }

    /** Nacional: nome do município pelo código IBGE + UF. Exterior: {@code xCidade}. */
    private String municipio(String end) {
        String cMun = x.texto(end + "endNac/cMun");
        if (cMun != null) {
            return limitar(buscarMunicipio(cMun)
                    .map(m -> juntar(" / ", m.nome(), m.uf()))
                    .orElse(cMun), 37);
        }
        return limitar(ouTraco(x.texto(end + "endExt/xCidade")), 37);
    }

    private String municipioEmit() {
        String cMun = x.texto("emit/enderNac/cMun");
        String nome = buscarMunicipio(cMun).map(TabelaMunicipios.Municipio::nome).orElse(cMun);
        return limitar(juntar(" / ", nome, x.texto("emit/enderNac/UF")), 37);
    }

    private String codigoECep(String end) {
        String cMun = x.texto(end + "endNac/cMun");
        if (cMun != null) {
            return juntar(" / ", cMun, Formatos.cep(x.texto(end + "endNac/CEP")));
        }
        return limitar(ouTraco(x.texto(end + "endExt/cEndPost")), 21);
    }

    private String codigoECepEmit() {
        return juntar(" / ", x.texto("emit/enderNac/cMun"), Formatos.cep(x.texto("emit/enderNac/CEP")));
    }

    private String endereco(String end) {
        return limitar(juntar(", ", x.texto(end + "xLgr"), x.texto(end + "nro"),
                x.texto(end + "xCpl"), x.texto(end + "xBairro")), 80);
    }

    // ===================================================================================
    // Serviço
    // ===================================================================================

    /** {@code 01.04.01 / 001}. */
    public String codigoTributacao() {
        return juntar(" / ", Formatos.codigoTributacaoNacional(x.texto(DPS + "serv/cServ/cTribNac")),
                x.texto(DPS + "serv/cServ/cTribMun"));
    }

    public String codigoNbs() {
        return ouTraco(Formatos.nbs(x.texto(DPS + "serv/cServ/cNBS")));
    }

    /** "São Paulo / SP / BR": município da prestação, UF e país ISO2. */
    public String localPrestacao() {
        return limitar(localidade(x.texto("xLocPrestacao"), x.texto(DPS + "serv/locPrest/cLocPrestacao"),
                x.texto(DPS + "serv/locPrest/cPaisPrestacao")), 42);
    }

    /** "SE xTribMun <> '' ENTAO Descrição Municipal SENAO Descrição Nacional". */
    public String descricaoTributacao() {
        return limitar(ouTraco(x.primeiro("xTribMun", "xTribNac")), 170);
    }

    /**
     * A descrição inteira, sem o corte em 1.300 caracteres que a tabela da NT sugere. O XSD
     * admite 2.000, e mesmo com todos os blocos presentes a página comporta cerca de 2.600; a NT
     * declara os limites de caracteres não obrigatórios (item 2.1). Cortar em 1.300 escondia o fim
     * de descrições que cabiam — justamente onde costuma ficar o valor e as condições. Se um dia
     * não couber, o {@code DesenhoDanfse} corta pela altura, com reticências.
     */
    public String descricaoServico() {
        return ouTraco(x.texto(DPS + "serv/cServ/xDescServ"));
    }

    // ===================================================================================
    // Tributação municipal
    // ===================================================================================

    /**
     * Nota 4: sem incidência de ISSQN, o bloco inteiro vira a mensagem "OPERAÇÃO NÃO SUJEITA AO
     * ISSQN". Sem o grupo {@code tribMun}, ou no item 99 da lista de serviços, que é o dos
     * serviços sem ISSQN.
     */
    public boolean sujeitoAoIssqn() {
        String cTribNac = x.texto(DPS + "serv/cServ/cTribNac");
        return x.existe(DPS + "valores/trib/tribMun") && (cTribNac == null || !cTribNac.startsWith("99"));
    }

    public String tipoTributacaoIssqn() {
        return limitar(descricao(TRIBUTACAO_ISSQN, x.texto(DPS + "valores/trib/tribMun/tribISSQN")), 21);
    }

    public String localIncidenciaIssqn() {
        return limitar(localidade(x.texto("xLocIncid"), x.texto("cLocIncid"),
                x.texto(DPS + "valores/trib/tribMun/cPaisResult")), 42);
    }

    public String regimeEspecial() {
        return limitar(descricao(REGIME_ESPECIAL, x.texto(DPS + "prest/regTrib/regEspTrib")), 27);
    }

    public String imunidade() {
        return limitar(descricao(IMUNIDADE, x.texto(DPS + "valores/trib/tribMun/tpImunidade")), 40);
    }

    public String suspensao() {
        return limitar(descricao(SUSPENSAO, x.texto(DPS + "valores/trib/tribMun/exigSusp/tpSusp")), 40);
    }

    public String processoSuspensao() {
        return limitar(ouTraco(x.texto(DPS + "valores/trib/tribMun/exigSusp/nProcesso")), 30);
    }

    public String beneficioMunicipal() {
        return limitar(descricao(BENEFICIO, x.texto("valores/tpBM")), 40);
    }

    public String calculoBeneficio() {
        return Formatos.moeda(x.primeiro("valores/vCalcBM", DPS + "valores/trib/tribMun/BM/vRedBCBM"));
    }

    /**
     * Ajustes da BC do ISSQN. Os nomes mudaram na NT 009 ({@code vDR}/{@code vCalcDR} viraram
     * {@code vAjusteBCISSQN}/{@code vCalcAjusteBCISSQN}); somam-se os dois leiautes.
     */
    public String totalDeducoes() {
        return Formatos.moeda(soma(
                x.primeiro(DPS + "valores/vAjusteBC/vAjusteBCISSQN", DPS + "valores/vDedRed/vDR"),
                x.primeiro("valores/vCalcAjusteBCISSQN", "valores/vCalcDR"),
                x.primeiro("IBSCBS/valores/vCalcAjusteBCIBSCBS", "IBSCBS/valores/vCalcReeRepRes")));
    }

    public String descontoIncondicionado() {
        return Formatos.moeda(x.texto(DPS + "valores/vDescCondIncond/vDescIncond"));
    }

    public String descontoCondicionado() {
        return Formatos.moeda(x.texto(DPS + "valores/vDescCondIncond/vDescCond"));
    }

    public String baseCalculoIssqn() {
        return Formatos.moeda(x.texto("valores/vBC"));
    }

    public String aliquotaIssqn() {
        return Formatos.percentual(x.texto("valores/pAliqAplic"));
    }

    public String retencaoIssqn() {
        return limitar(descricao(RETENCAO_ISSQN, x.texto(DPS + "valores/trib/tribMun/tpRetISSQN")), 25);
    }

    public String issqnApurado() {
        return Formatos.moeda(x.texto("valores/vISSQN"));
    }

    // ===================================================================================
    // Tributação federal
    // ===================================================================================

    public String irrf() {
        return Formatos.moeda(x.texto(DPS + "valores/trib/tribFed/vRetIRRF"));
    }

    public String contribuicaoPrevidenciaria() {
        return Formatos.moeda(x.texto(DPS + "valores/trib/tribFed/vRetCP"));
    }

    /**
     * Com PIS/COFINS retidos ({@code tpRetPisCofins = 1}), soma CSLL, PIS e COFINS; nos demais
     * casos, só a CSLL — regra da v1.02 da NT.
     */
    public String contribuicoesSociaisRetidas() {
        String csll = x.texto(DPS + "valores/trib/tribFed/vRetCSLL");
        if (pisCofinsRetidos()) {
            return Formatos.moeda(soma(csll, x.texto(DPS + "valores/trib/tribFed/piscofins/vPis"),
                    x.texto(DPS + "valores/trib/tribFed/piscofins/vCofins")));
        }
        return Formatos.moeda(csll);
    }

    /** Retidos, o PIS sai zerado aqui — já entrou nas contribuições retidas. */
    public String pisApuracaoPropria() {
        return pisCofinsRetidos() ? Formatos.moeda(BigDecimal.ZERO)
                : Formatos.moeda(x.texto(DPS + "valores/trib/tribFed/piscofins/vPis"));
    }

    public String cofinsApuracaoPropria() {
        return pisCofinsRetidos() ? Formatos.moeda(BigDecimal.ZERO)
                : Formatos.moeda(x.texto(DPS + "valores/trib/tribFed/piscofins/vCofins"));
    }

    public String descricaoContribuicoesRetidas() {
        return limitar(descricao(RETENCAO_PIS_COFINS,
                x.texto(DPS + "valores/trib/tribFed/piscofins/tpRetPisCofins")), 35);
    }

    /** Nota 6: a linha de PIS/COFINS de apuração própria só para competências até 2026. */
    public boolean imprimeLinhaPisCofins() {
        String comp = x.texto(DPS + "dCompet");
        return comp == null || !LocalDate.parse(comp.substring(0, 10)).isAfter(FIM_LINHA_PIS_COFINS);
    }

    private boolean pisCofinsRetidos() {
        return "1".equals(x.texto(DPS + "valores/trib/tribFed/piscofins/tpRetPisCofins"));
    }

    // ===================================================================================
    // IBS/CBS
    // ===================================================================================

    /** O CST e o cClassTrib mudaram de lugar na NT 009; lê os dois. */
    public String cstEClassificacao() {
        return juntar(" / ",
                x.primeiro(DPS + "IBSCBS/valores/trib/CST", DPS + "IBSCBS/valores/trib/gIBSCBS/CST"),
                x.primeiro(DPS + "IBSCBS/valores/trib/cClassTrib", DPS + "IBSCBS/valores/trib/gIBSCBS/cClassTrib"));
    }

    public String indicadorOperacaoEIncidencia() {
        String cMun = x.texto("IBSCBS/cLocalidadeIncid");
        String uf = buscarMunicipio(cMun).map(TabelaMunicipios.Municipio::uf).orElse(null);
        return limitar(juntar(" / ", x.texto(DPS + "IBSCBS/cIndOp"), cMun,
                x.texto("IBSCBS/xLocalidadeIncid"), uf), 56);
    }

    public String exclusoesReducoesBc() {
        return Formatos.moeda(soma(
                x.texto(DPS + "valores/vDescCondIncond/vDescIncond"),
                x.primeiro("IBSCBS/valores/vCalcAjusteBCIBSCBS", "IBSCBS/valores/vCalcReeRepRes"),
                x.texto("IBSCBS/valores/vCalcAjusteBCLocImoveis"),
                x.texto("valores/vISSQN"),
                x.texto(DPS + "valores/trib/tribFed/piscofins/vPis"),
                x.texto(DPS + "valores/trib/tribFed/piscofins/vCofins")));
    }

    public String baseCalculoIbsCbs() {
        return Formatos.moeda(x.texto("IBSCBS/valores/vBC"));
    }

    public String reducoesAliquota() {
        return juntar(" / ", Formatos.percentual(x.texto("IBSCBS/valores/uf/pRedAliqUF")),
                Formatos.percentual(x.texto("IBSCBS/valores/mun/pRedAliqMun")),
                Formatos.percentual(x.texto("IBSCBS/valores/fed/pRedAliqCBS")));
    }

    public String aliquotasIbs() {
        return juntar(" / ", Formatos.percentual(x.texto("IBSCBS/valores/uf/pIBSUF")),
                Formatos.percentual(x.texto("IBSCBS/valores/mun/pIBSMun")));
    }

    public String aliquotaEfetivaIbsMunicipal() {
        return Formatos.percentual(x.texto("IBSCBS/valores/mun/pAliqEfetMun"));
    }

    public String ibsMunicipal() {
        return Formatos.moeda(x.texto("IBSCBS/totCIBS/gIBS/gIBSMunTot/vIBSMun"));
    }

    public String aliquotaEfetivaIbsEstadual() {
        return Formatos.percentual(x.texto("IBSCBS/valores/uf/pAliqEfetUF"));
    }

    public String ibsEstadual() {
        return Formatos.moeda(x.texto("IBSCBS/totCIBS/gIBS/gIBSUFTot/vIBSUF"));
    }

    public String ibsTotal() {
        return Formatos.moeda(x.texto("IBSCBS/totCIBS/gIBS/vIBSTot"));
    }

    public String aliquotaCbs() {
        return Formatos.percentual(x.texto("IBSCBS/valores/fed/pCBS"));
    }

    public String aliquotaEfetivaCbs() {
        return Formatos.percentual(x.texto("IBSCBS/valores/fed/pAliqEfetCBS"));
    }

    public String cbsTotal() {
        return Formatos.moeda(x.texto("IBSCBS/totCIBS/gCBS/vCBS"));
    }

    // ===================================================================================
    // Totais
    // ===================================================================================

    public String valorServico() {
        return Formatos.moeda(x.texto(DPS + "valores/vServPrest/vServ"));
    }

    public String totalRetencoes() {
        return Formatos.moeda(x.texto("valores/vTotalRet"));
    }

    public String valorLiquido() {
        return Formatos.moeda(x.texto("valores/vLiq"));
    }

    public String totalIbsCbs() {
        return Formatos.moeda(soma(x.texto("IBSCBS/totCIBS/gIBS/vIBSTot"), x.texto("IBSCBS/totCIBS/gCBS/vCBS")));
    }

    /** Sem grupo IBS/CBS na nota, o valor líquido com IBS/CBS é o próprio valor líquido. */
    public String valorLiquidoComIbsCbs() {
        String vTotNF = x.texto("IBSCBS/totCIBS/vTotNF");
        return Formatos.moeda(vTotNF != null ? vTotNF : x.texto("valores/vLiq"));
    }

    // ===================================================================================
    // Informações complementares
    // ===================================================================================

    /**
     * União das informações complementares, na ordem da NT, separadas por pipe. A linha dos
     * totais aproximados de tributos é obrigatória (Nota 10) e vem à parte, em
     * {@link #totaisAproximadosTributos()}, porque não pode ser cortada pelas reticências.
     */
    public String informacoesComplementares() {
        List<String> partes = new ArrayList<>();
        acrescentar(partes, "Inf. Cont.: ", x.texto(DPS + "serv/infoCompl/xInfComp"));
        acrescentar(partes, "NFS-e Subst.: ", x.texto(DPS + "subst/chSubstda"));
        acrescentar(partes, "Doc. Ref.: ", x.texto(DPS + "serv/infoCompl/docRef"));
        acrescentar(partes, "Cod. Obra: ", x.texto(DPS + "serv/obra/cObra"));
        acrescentar(partes, "Insc. Imob.: ", x.primeiro(DPS + "serv/obra/inscImobFisc",
                DPS + "IBSCBS/imovel/gUnidImob/inscImobFisc", DPS + "IBSCBS/imovel/inscImobFisc"));
        acrescentar(partes, "Cod. Evt.: ", x.texto(DPS + "serv/atvEvento/idAtvEvt"));
        acrescentar(partes, "Doc. Tec.: ", x.texto(DPS + "serv/infoCompl/idDocTec"));
        acrescentar(partes, "Núm. Ped.: ", x.texto(DPS + "serv/infoCompl/xPed"));
        List<String> itens = x.textos(DPS + "serv/infoCompl/gItemPed/xItemPed");
        acrescentar(partes, "Item Ped.: ", itens.isEmpty() ? null : String.join(", ", itens));
        acrescentar(partes, "Inf. A. T. Mun.: ", x.texto("xOutInf"));
        return limitar(String.join(" | ", partes), 2000);
    }

    /**
     * "Totais Aproximados dos Tributos cfe. Lei nº 12.741/2012: Federais: R$ ou % ; Estaduais:
     * R$ ou % ; Municipais: R$ ou %" — em valor ou em percentual, conforme a NFS-e informou.
     */
    public String totaisAproximadosTributos() {
        String base = DPS + "valores/trib/totTrib/";
        String fed, est, mun;
        if (x.existe(base + "vTotTrib")) {
            fed = Formatos.moeda(x.texto(base + "vTotTrib/vTotTribFed"));
            est = Formatos.moeda(x.texto(base + "vTotTrib/vTotTribEst"));
            mun = Formatos.moeda(x.texto(base + "vTotTrib/vTotTribMun"));
        } else if (x.existe(base + "pTotTrib")) {
            fed = Formatos.percentual(x.texto(base + "pTotTrib/pTotTribFed"));
            est = Formatos.percentual(x.texto(base + "pTotTrib/pTotTribEst"));
            mun = Formatos.percentual(x.texto(base + "pTotTrib/pTotTribMun"));
        } else if (x.texto(base + "pTotTribSN") != null) {
            return "Totais Aproximados dos Tributos cfe. Lei nº 12.741/2012: Simples Nacional: "
                    + Formatos.percentual(x.texto(base + "pTotTribSN"));
        } else {
            fed = est = mun = VAZIO;
        }
        return "Totais Aproximados dos Tributos cfe. Lei nº 12.741/2012: Federais: " + fed
                + " ; Estaduais: " + est + " ; Municipais: " + mun;
    }

    /** Nº NFS-e / chave, para o canhoto. */
    public String numeroEChave() {
        return numeroNfse() + " / " + chaveAcesso();
    }

    // ===================================================================================

    /**
     * "Município / UF / País". O nome vem do XML ({@code xLocIncid}, {@code xLocPrestacao}) e, na
     * falta dele, da tabela de municípios: notas geradas por sistemas municipais trazem só o código
     * — a nota de referência 236, de São Paulo, saía "SP / BR", sem a cidade.
     */
    private String localidade(String nome, String cMun, String cPais) {
        var municipio = buscarMunicipio(cMun);
        String cidade = nome != null ? nome : municipio.map(TabelaMunicipios.Municipio::nome).orElse(null);
        String pais = cPais != null ? cPais : (cMun != null ? "BR" : null);
        return juntar(" / ", cidade, municipio.map(TabelaMunicipios.Municipio::uf).orElse(null), pais);
    }

    /** {@code TabelaMunicipios.buscar} não aceita nulo, e vários códigos são opcionais na nota. */
    private static java.util.Optional<TabelaMunicipios.Municipio> buscarMunicipio(String codigo) {
        return codigo == null ? java.util.Optional.empty() : TabelaMunicipios.buscar(codigo);
    }

    private static void acrescentar(List<String> partes, String rotulo, String valor) {
        if (valor != null && !valor.isBlank()) {
            partes.add(rotulo + valor);
        }
    }

    private static String descricao(Map<String, String> opcoes, String codigo) {
        if (codigo == null) {
            return VAZIO;
        }
        return opcoes.getOrDefault(codigo, codigo);
    }

    private static BigDecimal soma(String... valores) {
        BigDecimal total = null;
        for (String v : valores) {
            BigDecimal n = Formatos.numero(v);
            if (n != null) {
                total = total == null ? n : total.add(n);
            }
        }
        return total;
    }

    /** Dados de uma pessoa da nota, já formatados para os blocos de 3 linhas do DANFSe. */
    public record Pessoa(String documento, String inscricaoMunicipal, String telefone, String nome,
                         String municipio, String codigoECep, String endereco, String email) {
    }
}
