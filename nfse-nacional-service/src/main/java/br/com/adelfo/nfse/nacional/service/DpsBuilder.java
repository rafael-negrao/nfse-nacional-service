package br.com.adelfo.nfse.nacional.service;

import br.com.adelfo.nfse.nacional.client.TipoAmbiente;
import br.com.adelfo.nfse.nacional.schemas.ObjectFactory;
import br.com.adelfo.nfse.nacional.schemas.TCCServ;
import br.com.adelfo.nfse.nacional.schemas.TCDPS;
import br.com.adelfo.nfse.nacional.schemas.TCEndereco;
import br.com.adelfo.nfse.nacional.schemas.TCEnderExt;
import br.com.adelfo.nfse.nacional.schemas.TCEnderNac;
import br.com.adelfo.nfse.nacional.schemas.TCExigSuspensa;
import br.com.adelfo.nfse.nacional.schemas.TCInfDPS;
import br.com.adelfo.nfse.nacional.schemas.TCInfoCompl;
import br.com.adelfo.nfse.nacional.schemas.TCInfoDedRed;
import br.com.adelfo.nfse.nacional.schemas.TCInfoPessoa;
import br.com.adelfo.nfse.nacional.schemas.TCInfoPrestador;
import br.com.adelfo.nfse.nacional.schemas.TCInfoTributacao;
import br.com.adelfo.nfse.nacional.schemas.TCInfoValores;
import br.com.adelfo.nfse.nacional.schemas.TCLocPrest;
import br.com.adelfo.nfse.nacional.schemas.TCRegTrib;
import br.com.adelfo.nfse.nacional.schemas.TCServ;
import br.com.adelfo.nfse.nacional.schemas.TCSubstituicao;
import br.com.adelfo.nfse.nacional.schemas.TCTribFederal;
import br.com.adelfo.nfse.nacional.schemas.TCTribMunicipal;
import br.com.adelfo.nfse.nacional.schemas.TCTribOutrosPisCofins;
import br.com.adelfo.nfse.nacional.schemas.TCTribTotal;
import br.com.adelfo.nfse.nacional.schemas.TCTribTotalMonet;
import br.com.adelfo.nfse.nacional.schemas.TCTribTotalPercent;
import br.com.adelfo.nfse.nacional.schemas.TCVDescCondIncond;
import br.com.adelfo.nfse.nacional.schemas.TCVServPrest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Builder fluente da DPS (Declaração de Prestação de Serviços) — equivalente ao {@code NfeBuilder}
 * do projeto de referência.
 *
 * <p>Produz o XML <b>não assinado</b> pronto para {@code NfseService.emitir}; a assinatura XMLDSig
 * é aplicada pela biblioteca no momento do envio.
 *
 * <p>Exemplo do mínimo que o XSD aceita:
 * <pre>
 * String xml = DpsBuilder.novo()
 *         .ambiente(TipoAmbiente.PRODUCAO_RESTRITA)
 *         .municipioEmissor("3550308")
 *         .identificacao("1", "123", LocalDate.now())
 *         .emitidaPeloPrestador()
 *         .prestadorCnpj("00000000000000")
 *         .naoOptanteSimplesNacional()
 *         .semRegimeEspecial()
 *         .servicoPrestadoNoMunicipio("3550308")
 *         .servico("010101", "Análise e desenvolvimento de sistemas")
 *         .valorServico(new BigDecimal("1000.00"))
 *         .issqnTributavel()
 *         .issqnNaoRetido()
 *         .semTotalTributos()
 *         .build();
 * </pre>
 *
 * <p><b>Armadilhas do leiaute que este builder encapsula</b> — todas descobertas lendo os XSDs, e
 * cada uma renderia uma rejeição de schema difícil de diagnosticar:
 * <ul>
 *   <li>Os campos monetários e percentuais são {@code xs:string} com pattern, não {@code decimal}.
 *       Exigem exatamente duas casas decimais e proíbem zero à esquerda e sinal negativo.</li>
 *   <li>O {@code Id} tem 45 posições com série e número zero-preenchidos, enquanto os elementos
 *       {@code serie} e {@code nDPS} <b>não</b> admitem esse preenchimento.</li>
 *   <li>{@code dhEmi} não admite fração de segundos.</li>
 *   <li>O grupo IBS/CBS da Reforma Tributária entra por {@link #ibsCbs(IbsCbsBuilder)}.</li>
 *   <li>Nenhum elemento pode sair com prefixo de namespace — {@code E1228} rejeita. Por isso todo
 *       marshalling passa por {@code MarshallerNfse}.</li>
 * </ul>
 */
public final class DpsBuilder {

    /** Versão do leiaute — TVerNFSe aceita apenas "1.00" ou "1.01". */
    public static final String VERSAO_LEIAUTE = "1.01";

    /** Identificação do aplicativo emissor, gravada em verAplic (máx. 20 caracteres). */
    public static final String VER_APLIC = "adelfo-nfse-1.0.0";

    /** Formato de dhEmi (TSDateTimeUTC) — ver {@link DataHoraFiscal} para as armadilhas de fuso. */
    private static final DateTimeFormatter FORMATO_DATA_HORA = DataHoraFiscal.FORMATO;

    /** Tipo de inscrição federal usado na composição do Id da DPS. */
    private static final String INSCRICAO_CPF = "1";
    private static final String INSCRICAO_CNPJ = "2";

    // --- identificação -----------------------------------------------------------------------
    private TipoAmbiente ambiente;
    private String serie;
    private String nDps;
    private LocalDate competencia;
    private OffsetDateTime dhEmissao = DataHoraFiscal.agora();
    private String municipioEmissor;
    private String tpEmit = "1";
    private String cMotivoEmisTI;
    private String chNFSeRejeitada;
    private String verAplic = VER_APLIC;

    // --- substituição ------------------------------------------------------------------------
    private String chaveSubstituida;
    private String motivoSubstituicaoCodigo;
    private String motivoSubstituicaoDescricao;

    // --- prestador ---------------------------------------------------------------------------
    private final Pessoa prestador = new Pessoa();
    private String opSimpNac;
    private String regApTribSN;
    private String regEspTrib;

    // --- tomador / intermediário -------------------------------------------------------------
    private Pessoa tomador;
    private Pessoa intermediario;

    // --- serviço -----------------------------------------------------------------------------
    private String municipioPrestacao;
    private String paisPrestacao;
    private String cTribNac;
    private String cTribMun;
    private String descricaoServico;
    private String cNBS;
    private String codigoInternoContribuinte;
    private boolean validarCatalogoDeServicos = true;
    private String docRef;
    private String infoComplementar;

    // --- IBS/CBS -----------------------------------------------------------------------------
    private IbsCbsBuilder ibsCbs;

    // --- valores -----------------------------------------------------------------------------
    private BigDecimal valorServico;
    private BigDecimal valorRecebidoIntermediario;
    private BigDecimal descontoIncondicionado;
    private BigDecimal descontoCondicionado;
    private BigDecimal percentualDeducao;
    private BigDecimal valorDeducao;

    // --- tributação municipal ----------------------------------------------------------------
    private String tribISSQN;
    private String cPaisResultado;
    private String tpImunidade;
    private String tpRetISSQN;
    private BigDecimal aliquota;
    private String tpSuspensao;
    private String nProcessoSuspensao;

    // --- tributação federal ------------------------------------------------------------------
    private String cstPisCofins;
    private BigDecimal bcPisCofins;
    private BigDecimal aliquotaPis;
    private BigDecimal aliquotaCofins;
    private BigDecimal valorPis;
    private BigDecimal valorCofins;
    private String tpRetPisCofins;
    private BigDecimal valorRetCP;
    private BigDecimal valorRetIRRF;
    private BigDecimal valorRetCSLL;

    // --- total de tributos -------------------------------------------------------------------
    private BigDecimal totTribFed;
    private BigDecimal totTribEst;
    private BigDecimal totTribMun;
    private boolean totTribPercentual;
    private BigDecimal pTotTribSN;
    private boolean semTotalTributos;

    private DpsBuilder() {
    }

    public static DpsBuilder novo() {
        return new DpsBuilder();
    }

    // =========================================================================================
    // Identificação
    // =========================================================================================

    public DpsBuilder ambiente(TipoAmbiente ambiente) {
        this.ambiente = ambiente;
        return this;
    }

    /**
     * Série e número da DPS e data de competência (início da prestação do serviço).
     *
     * @param serie       série da DPS, até 5 dígitos; {@code E0010} rejeita série fora da faixa
     *                    definida para o tipo de emissor
     * @param numero      número da DPS, até 15 dígitos, sem zeros à esquerda
     * @param competencia data em que se iniciou a prestação do serviço
     */
    public DpsBuilder identificacao(String serie, String numero, LocalDate competencia) {
        this.serie = serie;
        this.nDps = numero;
        this.competencia = competencia;
        return this;
    }

    /** Sobrescreve a data/hora de emissão; por padrão é o instante da construção. */
    public DpsBuilder dataHoraEmissao(OffsetDateTime dhEmi) {
        this.dhEmissao = dhEmi;
        return this;
    }

    /** Código IBGE (7 dígitos) do município em que o emitente está cadastrado. */
    public DpsBuilder municipioEmissor(String codigoIbge) {
        this.municipioEmissor = Documentos.municipio(codigoIbge, "municipioEmissor");
        return this;
    }

    /** Identificação do aplicativo emissor; sobrescreve o padrão {@link #VER_APLIC}. */
    public DpsBuilder versaoAplicativo(String verAplic) {
        this.verAplic = verAplic;
        return this;
    }

    /** tpEmit=1 — o caso normal. */
    public DpsBuilder emitidaPeloPrestador() {
        this.tpEmit = "1";
        this.cMotivoEmisTI = null;
        return this;
    }

    /**
     * tpEmit=2 — DPS emitida pelo tomador.
     *
     * <p><b>Hoje é sempre rejeitada.</b> O AnexoI, aba {@code RN DPS_NFS-e}, traz a regra
     * {@code E9996}: "Nesta versão da aplicação, não é permitida a emissão de NFS-e pelo tomador ou
     * intermediário". O leiaute prevê o campo, mas o Emissor Público recusa. O método fica porque a
     * restrição é da versão atual da aplicação, não do leiaute — mas não conte com ele.
     *
     * @param codigoMotivo motivo da emissão pelo tomador/intermediário: 1 importação de serviço;
     *                     2 obrigado por legislação municipal; 3 recusa de emissão pelo prestador;
     *                     4 rejeição da NFS-e emitida pelo prestador
     */
    public DpsBuilder emitidaPeloTomador(String codigoMotivo) {
        this.tpEmit = "2";
        this.cMotivoEmisTI = codigoMotivo;
        return this;
    }

    /**
     * tpEmit=3 — DPS emitida pelo intermediário. <b>Também rejeitada por {@code E9996}</b>; ver
     * {@link #emitidaPeloTomador(String)}.
     */
    public DpsBuilder emitidaPeloIntermediario(String codigoMotivo) {
        this.tpEmit = "3";
        this.cMotivoEmisTI = codigoMotivo;
        return this;
    }

    /** Chave da NFS-e rejeitada, obrigatória quando o motivo da emissão pelo tomador é 4. */
    public DpsBuilder chaveNfseRejeitada(String chaveAcesso) {
        this.chNFSeRejeitada = Documentos.chaveAcesso(chaveAcesso, "chaveNfseRejeitada");
        return this;
    }

    /**
     * Marca esta DPS como substituta de uma NFS-e existente. A Sefin cancela a original com o
     * evento {@code e105102} e devolve a substituta.
     *
     * @param codigoMotivo 01 desenquadramento do Simples; 02 enquadramento no Simples;
     *                     03 inclusão retroativa de imunidade/isenção; 04 exclusão retroativa;
     *                     05 rejeição pelo tomador/intermediário; 99 outros
     */
    public DpsBuilder substitui(String chaveSubstituida, String codigoMotivo, String descricaoMotivo) {
        this.chaveSubstituida = Documentos.chaveAcesso(chaveSubstituida, "substitui");
        this.motivoSubstituicaoCodigo = codigoMotivo;
        this.motivoSubstituicaoDescricao = descricaoMotivo;
        return this;
    }

    // =========================================================================================
    // Prestador
    // =========================================================================================

    public DpsBuilder prestadorCnpj(String cnpj) {
        prestador.cnpj = Documentos.cnpj(cnpj, "prestadorCnpj");
        return this;
    }

    public DpsBuilder prestadorCpf(String cpf) {
        prestador.cpf = Documentos.cpf(cpf, "prestadorCpf");
        return this;
    }

    /** Prestador no exterior identificado por NIF. */
    public DpsBuilder prestadorNif(String nif) {
        prestador.nif = nif;
        return this;
    }

    /**
     * Prestador no exterior sem NIF.
     *
     * @param codigo 0 não informado na nota de origem; 1 dispensado do NIF; 2 não exigência do NIF
     */
    public DpsBuilder prestadorSemNif(String codigo) {
        prestador.cNaoNif = codigo;
        return this;
    }

    public DpsBuilder prestadorNome(String nomeRazaoSocial) {
        prestador.nome = nomeRazaoSocial;
        return this;
    }

    public DpsBuilder prestadorInscricaoMunicipal(String im) {
        prestador.inscricaoMunicipal = im;
        return this;
    }

    /** Cadastro de Atividade Econômica da Pessoa Física. */
    public DpsBuilder prestadorCaepf(String caepf) {
        prestador.caepf = caepf;
        return this;
    }

    public DpsBuilder prestadorEndereco(Endereco endereco) {
        prestador.endereco = endereco;
        return this;
    }

    public DpsBuilder prestadorContato(String telefone, String email) {
        prestador.telefone = Documentos.telefone(telefone, "prestadorContato");
        prestador.email = email;
        return this;
    }

    /** opSimpNac=1. */
    public DpsBuilder naoOptanteSimplesNacional() {
        this.opSimpNac = "1";
        this.regApTribSN = null;
        return this;
    }

    /** opSimpNac=2 — Microempreendedor Individual. */
    public DpsBuilder optanteSimplesNacionalMei() {
        this.opSimpNac = "2";
        this.regApTribSN = null;
        return this;
    }

    /**
     * opSimpNac=3 — Microempresa ou Empresa de Pequeno Porte.
     *
     * @param regimeApuracao 1 tributos federais e municipal pelo SN; 2 federais pelo SN e ISSQN
     *                       por fora; 3 federais e municipal por fora do SN
     */
    public DpsBuilder optanteSimplesNacionalMeEpp(String regimeApuracao) {
        this.opSimpNac = "3";
        this.regApTribSN = regimeApuracao;
        return this;
    }

    /** regEspTrib=0. */
    public DpsBuilder semRegimeEspecial() {
        this.regEspTrib = "0";
        return this;
    }

    /**
     * @param codigo 1 ato cooperado; 2 estimativa; 3 microempresa municipal; 4 notário ou
     *               registrador; 5 profissional autônomo; 6 sociedade de profissionais; 9 outros
     */
    public DpsBuilder regimeEspecial(String codigo) {
        this.regEspTrib = codigo;
        return this;
    }

    // =========================================================================================
    // Tomador e intermediário
    // =========================================================================================

    public DpsBuilder tomadorCnpj(String cnpj, String nomeRazaoSocial) {
        tomador = new Pessoa();
        tomador.cnpj = Documentos.cnpj(cnpj, "tomadorCnpj");
        tomador.nome = nomeRazaoSocial;
        return this;
    }

    public DpsBuilder tomadorCpf(String cpf, String nome) {
        tomador = new Pessoa();
        tomador.cpf = Documentos.cpf(cpf, "tomadorCpf");
        tomador.nome = nome;
        return this;
    }

    public DpsBuilder tomadorNif(String nif, String nome) {
        tomador = new Pessoa();
        tomador.nif = nif;
        tomador.nome = nome;
        return this;
    }

    /** Ver {@link #prestadorSemNif(String)} para os códigos. */
    public DpsBuilder tomadorSemNif(String codigo, String nome) {
        tomador = new Pessoa();
        tomador.cNaoNif = codigo;
        tomador.nome = nome;
        return this;
    }

    public DpsBuilder tomadorEndereco(Endereco endereco) {
        exigeTomador().endereco = endereco;
        return this;
    }

    public DpsBuilder tomadorContato(String telefone, String email) {
        Pessoa p = exigeTomador();
        p.telefone = Documentos.telefone(telefone, "telefone");
        p.email = email;
        return this;
    }

    public DpsBuilder tomadorInscricaoMunicipal(String im) {
        exigeTomador().inscricaoMunicipal = im;
        return this;
    }

    public DpsBuilder intermediarioCnpj(String cnpj, String nomeRazaoSocial) {
        intermediario = new Pessoa();
        intermediario.cnpj = Documentos.cnpj(cnpj, "intermediarioCnpj");
        intermediario.nome = nomeRazaoSocial;
        return this;
    }

    public DpsBuilder intermediarioCpf(String cpf, String nome) {
        intermediario = new Pessoa();
        intermediario.cpf = Documentos.cpf(cpf, "intermediarioCpf");
        intermediario.nome = nome;
        return this;
    }

    public DpsBuilder intermediarioEndereco(Endereco endereco) {
        exigeIntermediario().endereco = endereco;
        return this;
    }

    public DpsBuilder intermediarioContato(String telefone, String email) {
        Pessoa p = exigeIntermediario();
        p.telefone = Documentos.telefone(telefone, "telefone");
        p.email = email;
        return this;
    }

    private Pessoa exigeTomador() {
        if (tomador == null) {
            throw new IllegalStateException("informe o tomador antes (tomadorCnpj/tomadorCpf/…)");
        }
        return tomador;
    }

    private Pessoa exigeIntermediario() {
        if (intermediario == null) {
            throw new IllegalStateException("informe o intermediário antes (intermediarioCnpj/…)");
        }
        return intermediario;
    }

    // =========================================================================================
    // Serviço
    // =========================================================================================

    /** Local da prestação por código IBGE do município. */
    public DpsBuilder servicoPrestadoNoMunicipio(String codigoIbge) {
        this.municipioPrestacao = Documentos.municipio(codigoIbge, "servicoPrestadoNoMunicipio");
        this.paisPrestacao = null;
        return this;
    }

    /** Local da prestação no exterior, por código ISO do país. */
    public DpsBuilder servicoPrestadoNoPais(String codigoPaisIso) {
        this.paisPrestacao = codigoPaisIso;
        this.municipioPrestacao = null;
        return this;
    }

    /**
     * @param codigoTributacaoNacional 6 dígitos: item (2) + subitem (2) + desdobro nacional (2).
     *                                 Conferido contra a {@link ListaServicoNacional}; use
     *                                 {@link #semValidacaoDeCatalogo()} se o código for novo
     * @param descricao                descrição completa do serviço prestado — texto livre do
     *                                 emitente, não a descrição oficial do código
     */
    public DpsBuilder servico(String codigoTributacaoNacional, String descricao) {
        this.cTribNac = codigoTributacaoNacional;
        this.descricaoServico = descricao;
        return this;
    }

    /**
     * Desliga a conferência do {@code cTribNac} contra a lista de serviços embutida.
     *
     * <p>A tabela é dado versionado no jar e envelhece: se a Receita publicar um anexo com códigos
     * novos, a validação passa a barrar código legítimo. Este método é a saída enquanto a tabela
     * não é regenerada — ver {@link ListaServicoNacional}.
     */
    public DpsBuilder semValidacaoDeCatalogo() {
        this.validarCatalogoDeServicos = false;
        return this;
    }

    /**
     * Código de tributação municipal do ISSQN. Atenção: são <b>3 dígitos</b> (TCCodTribMun), não os
     * 6 do código nacional — trocar um pelo outro derruba a validação de schema.
     */
    public DpsBuilder codigoTributacaoMunicipal(String cTribMun) {
        this.cTribMun = cTribMun;
        return this;
    }

    /**
     * Código NBS 2.0 do serviço — 9 dígitos, sem pontos. Conferido contra a {@link TabelaNbs};
     * os níveis intermediários da hierarquia não servem aqui.
     */
    public DpsBuilder codigoNbs(String cNBS) {
        this.cNBS = cNBS;
        return this;
    }

    public DpsBuilder codigoInterno(String codigo) {
        this.codigoInternoContribuinte = codigo;
        return this;
    }

    /**
     * Documento que subsidia a emissão — obrigatório quando a DPS é emitida pelo tomador ou pelo
     * intermediário.
     */
    public DpsBuilder documentoReferencia(String docRef) {
        this.docRef = docRef;
        return this;
    }

    public DpsBuilder informacoesComplementares(String texto) {
        this.infoComplementar = texto;
        return this;
    }

    // =========================================================================================
    // Valores
    // =========================================================================================

    public DpsBuilder valorServico(BigDecimal valor) {
        this.valorServico = valor;
        return this;
    }

    /** Valor recebido pelo intermediário do serviço. */
    public DpsBuilder valorRecebidoIntermediario(BigDecimal valor) {
        this.valorRecebidoIntermediario = valor;
        return this;
    }

    public DpsBuilder descontoIncondicionado(BigDecimal valor) {
        this.descontoIncondicionado = valor;
        return this;
    }

    public DpsBuilder descontoCondicionado(BigDecimal valor) {
        this.descontoCondicionado = valor;
        return this;
    }

    /** Dedução/redução da base de cálculo por percentual. Exclui {@link #deducaoValor}. */
    public DpsBuilder deducaoPercentual(BigDecimal percentual) {
        this.percentualDeducao = percentual;
        this.valorDeducao = null;
        return this;
    }

    /** Dedução/redução da base de cálculo por valor. Exclui {@link #deducaoPercentual}. */
    public DpsBuilder deducaoValor(BigDecimal valor) {
        this.valorDeducao = valor;
        this.percentualDeducao = null;
        return this;
    }

    // =========================================================================================
    // Tributação municipal (ISSQN)
    // =========================================================================================

    /** tribISSQN=1 — operação tributável. */
    public DpsBuilder issqnTributavel() {
        this.tribISSQN = "1";
        return this;
    }

    /**
     * tribISSQN=2 — imunidade.
     *
     * @param tipoImunidade 0 tipo não informado; 1 CF88 art. 150 VI a; 2 templos; 3 partidos,
     *                      sindicatos, educação e assistência social; 4 livros e periódicos;
     *                      5 fonogramas e videofonogramas musicais
     */
    public DpsBuilder issqnImunidade(String tipoImunidade) {
        this.tribISSQN = "2";
        this.tpImunidade = tipoImunidade;
        return this;
    }

    /**
     * tribISSQN=3 — exportação de serviço.
     *
     * @param codigoPaisResultado código ISO do país onde se verificou o resultado da prestação
     */
    public DpsBuilder issqnExportacao(String codigoPaisResultado) {
        this.tribISSQN = "3";
        this.cPaisResultado = codigoPaisResultado;
        return this;
    }

    /** tribISSQN=4 — não incidência. */
    public DpsBuilder issqnNaoIncidencia() {
        this.tribISSQN = "4";
        return this;
    }

    /** tpRetISSQN=1. */
    public DpsBuilder issqnNaoRetido() {
        this.tpRetISSQN = "1";
        return this;
    }

    /** tpRetISSQN=2. */
    public DpsBuilder issqnRetidoPeloTomador() {
        this.tpRetISSQN = "2";
        return this;
    }

    /** tpRetISSQN=3. */
    public DpsBuilder issqnRetidoPeloIntermediario() {
        this.tpRetISSQN = "3";
        return this;
    }

    /**
     * Alíquota do ISSQN em percentual. Só precisa ser informada quando o município de incidência
     * não pertence ao Sistema Nacional — do contrário o próprio sistema a fornece.
     *
     * <p>{@code E1300} rejeita alíquota acima de 5%, e {@code E1297} rejeita redução de base de
     * cálculo que leve a alíquota efetiva a menos de 2%.
     */
    public DpsBuilder aliquota(BigDecimal percentual) {
        this.aliquota = percentual;
        return this;
    }

    /**
     * Suspensão da exigibilidade do ISSQN.
     *
     * @param tipo     1 decisão judicial; 2 processo administrativo
     * @param processo número do processo
     */
    public DpsBuilder exigibilidadeSuspensa(String tipo, String processo) {
        this.tpSuspensao = tipo;
        this.nProcessoSuspensao = processo;
        return this;
    }

    // =========================================================================================
    // Tributação federal
    // =========================================================================================

    /**
     * Grupo PIS/COFINS.
     *
     * @param cst Código de Situação Tributária do PIS/COFINS (ver tabela em TSTipoCST)
     */
    public DpsBuilder pisCofins(String cst, BigDecimal baseCalculo,
                                BigDecimal aliquotaPis, BigDecimal aliquotaCofins,
                                BigDecimal valorPis, BigDecimal valorCofins) {
        this.cstPisCofins = cst;
        this.bcPisCofins = baseCalculo;
        this.aliquotaPis = aliquotaPis;
        this.aliquotaCofins = aliquotaCofins;
        this.valorPis = valorPis;
        this.valorCofins = valorCofins;
        return this;
    }

    /** CST do PIS/COFINS sem valores — para os casos de não incidência. */
    public DpsBuilder pisCofinsCst(String cst) {
        this.cstPisCofins = cst;
        return this;
    }

    /** Tipo de retenção do PIS/COFINS (0 a 9 — ver tabela em TSTipoRetPISCofins). */
    public DpsBuilder retencaoPisCofins(String tipo) {
        this.tpRetPisCofins = tipo;
        return this;
    }

    public DpsBuilder retencoesFederais(BigDecimal vRetCP, BigDecimal vRetIRRF, BigDecimal vRetCSLL) {
        this.valorRetCP = vRetCP;
        this.valorRetIRRF = vRetIRRF;
        this.valorRetCSLL = vRetCSLL;
        return this;
    }

    // =========================================================================================
    // Total aproximado dos tributos (Lei 12.741/2012)
    // =========================================================================================

    /** indTotTrib=0 — opta por não informar valor estimado de tributos (Decreto 8.264/2014). */
    public DpsBuilder semTotalTributos() {
        this.semTotalTributos = true;
        return this;
    }

    /** Total aproximado dos tributos em valores monetários. */
    public DpsBuilder totalTributos(BigDecimal federais, BigDecimal estaduais, BigDecimal municipais) {
        this.totTribFed = federais;
        this.totTribEst = estaduais;
        this.totTribMun = municipais;
        this.totTribPercentual = false;
        this.semTotalTributos = false;
        return this;
    }

    /** Total aproximado dos tributos em percentuais. */
    public DpsBuilder totalTributosPercentual(BigDecimal federais, BigDecimal estaduais, BigDecimal municipais) {
        this.totTribFed = federais;
        this.totTribEst = estaduais;
        this.totTribMun = municipais;
        this.totTribPercentual = true;
        this.semTotalTributos = false;
        return this;
    }

    /** Percentual aproximado do total de tributos da alíquota do Simples Nacional. */
    public DpsBuilder totalTributosSimplesNacional(BigDecimal percentual) {
        this.pTotTribSN = percentual;
        this.semTotalTributos = false;
        return this;
    }

    // =========================================================================================
    // IBS/CBS — Reforma Tributária
    // =========================================================================================

    /**
     * Grupo IBS/CBS da DPS. Opcional no leiaute, e declaratório: os valores apurados são calculados
     * pela plataforma no nível da NFS-e. Ver {@link IbsCbsBuilder}.
     */
    public DpsBuilder ibsCbs(IbsCbsBuilder ibsCbs) {
        this.ibsCbs = ibsCbs;
        return this;
    }

    // =========================================================================================
    // Construção
    // =========================================================================================

    /** Identificador de 45 posições da DPS, conforme TSIdDPS. Disponível após {@link #build()}. */
    public String id() {
        validarIdentificacao();
        return montarId();
    }

    /**
     * Objeto JAXB da DPS. Existe para o {@link NfseDecisaoJudicialBuilder}, que precisa embutir a
     * DPS dentro da NFS-e em vez de serializá-la sozinha.
     */
    TCDPS buildJaxb() {
        validar();
        return montarDps();
    }

    /** Código IBGE do município de prestação; nulo quando a prestação é no exterior. */
    String municipioPrestacaoInformado() {
        return municipioPrestacao;
    }

    /** Código de tributação nacional informado. */
    String codigoTributacaoNacionalInformado() {
        return cTribNac;
    }

    /** Código IBGE do município emissor, tal como informado. */
    String municipioEmissorInformado() {
        return municipioEmissor;
    }

    /** Inscrição federal do prestador — CNPJ ou CPF, o que tiver sido informado. */
    String inscricaoFederalPrestador() {
        return prestador.cpf != null ? prestador.cpf : prestador.cnpj;
    }

    /** {@code 1} para CPF, {@code 2} para CNPJ, como exigem os identificadores do leiaute. */
    String tipoInscricaoFederal() {
        return prestador.cpf != null ? INSCRICAO_CPF : INSCRICAO_CNPJ;
    }

    OffsetDateTime dataHoraEmissao() {
        return dhEmissao;
    }

    String dataHoraEmissaoFormatada() {
        return DataHoraFiscal.formatar(dhEmissao);
    }

    /** XML da DPS, não assinado, pronto para {@code NfseService.emitir}. */
    public String build() {
        return serializar(buildJaxb());
    }

    private TCDPS montarDps() {
        TCInfDPS info = new TCInfDPS();
        info.setId(montarId());
        info.setTpAmb(ambiente.getCodigo());
        info.setDhEmi(DataHoraFiscal.formatar(dhEmissao));
        info.setVerAplic(verAplic);
        info.setSerie(serie);
        info.setNDPS(nDps);
        info.setDCompet(competencia.format(DateTimeFormatter.ISO_LOCAL_DATE));
        info.setTpEmit(tpEmit);
        info.setCMotivoEmisTI(cMotivoEmisTI);
        info.setChNFSeRej(chNFSeRejeitada);
        info.setCLocEmi(municipioEmissor);
        info.setSubst(montarSubstituicao());
        info.setPrest(montarPrestador());
        info.setToma(montarPessoa(tomador));
        info.setInterm(montarPessoa(intermediario));
        info.setServ(montarServico());
        info.setValores(montarValores());
        if (ibsCbs != null) {
            // A conferência de E0953 precisa do valor do serviço, que vive aqui e não no grupo.
            ibsCbs.conferirReembolsoContra(valorServico);
            info.setIBSCBS(ibsCbs.build());
        }

        TCDPS dps = new TCDPS();
        dps.setVersao(VERSAO_LEIAUTE);
        dps.setInfDPS(info);
        return dps;
    }

    /**
     * Id da DPS conforme TSIdDPS: {@code "DPS" + cMun(7) + tipo de inscrição(1) +
     * inscrição federal(14) + série(5) + número(15)}, totalizando 45 posições.
     *
     * <p>Série e número entram <b>zero-preenchidos</b> aqui, embora os elementos {@code serie} e
     * {@code nDPS} do corpo não admitam zeros à esquerda.
     */
    private String montarId() {
        String tipoInscricao = prestador.cpf != null ? INSCRICAO_CPF : INSCRICAO_CNPJ;
        String inscricao = prestador.cpf != null ? prestador.cpf : prestador.cnpj;
        return "DPS"
                + municipioEmissor
                + tipoInscricao
                + zeroEsquerda(inscricao, 14)
                + zeroEsquerda(serie, 5)
                + zeroEsquerda(nDps, 15);
    }

    private TCSubstituicao montarSubstituicao() {
        if (chaveSubstituida == null) {
            return null;
        }
        TCSubstituicao subst = new TCSubstituicao();
        subst.setChSubstda(chaveSubstituida);
        subst.setCMotivo(motivoSubstituicaoCodigo);
        subst.setXMotivo(motivoSubstituicaoDescricao);
        return subst;
    }

    private TCInfoPrestador montarPrestador() {
        TCInfoPrestador prest = new TCInfoPrestador();
        prest.setCNPJ(prestador.cnpj);
        prest.setCPF(prestador.cpf);
        prest.setNIF(prestador.nif);
        prest.setCNaoNIF(prestador.cNaoNif);
        prest.setCAEPF(prestador.caepf);
        prest.setIM(prestador.inscricaoMunicipal);
        prest.setXNome(prestador.nome);
        prest.setEnd(montarEndereco(prestador.endereco));
        prest.setFone(prestador.telefone);
        prest.setEmail(prestador.email);

        TCRegTrib regTrib = new TCRegTrib();
        regTrib.setOpSimpNac(opSimpNac);
        regTrib.setRegApTribSN(regApTribSN);
        regTrib.setRegEspTrib(regEspTrib);
        prest.setRegTrib(regTrib);

        return prest;
    }

    private static TCInfoPessoa montarPessoa(Pessoa pessoa) {
        if (pessoa == null) {
            return null;
        }
        TCInfoPessoa info = new TCInfoPessoa();
        info.setCNPJ(pessoa.cnpj);
        info.setCPF(pessoa.cpf);
        info.setNIF(pessoa.nif);
        info.setCNaoNIF(pessoa.cNaoNif);
        info.setCAEPF(pessoa.caepf);
        info.setIM(pessoa.inscricaoMunicipal);
        info.setXNome(pessoa.nome);
        info.setEnd(montarEndereco(pessoa.endereco));
        info.setFone(pessoa.telefone);
        info.setEmail(pessoa.email);
        return info;
    }

    private static TCEndereco montarEndereco(Endereco endereco) {
        if (endereco == null) {
            return null;
        }
        TCEndereco end = new TCEndereco();
        if (endereco.codigoPais() == null) {
            TCEnderNac nac = new TCEnderNac();
            nac.setCMun(endereco.codigoMunicipio());
            nac.setCEP(endereco.cep());
            end.setEndNac(nac);
        } else {
            TCEnderExt ext = new TCEnderExt();
            ext.setCPais(endereco.codigoPais());
            ext.setCEndPost(endereco.cep());
            ext.setXCidade(endereco.cidade());
            ext.setXEstProvReg(endereco.estadoProvinciaRegiao());
            end.setEndExt(ext);
        }
        end.setXLgr(endereco.logradouro());
        end.setNro(endereco.numero());
        end.setXCpl(endereco.complemento());
        end.setXBairro(endereco.bairro());
        return end;
    }

    private TCServ montarServico() {
        TCLocPrest local = new TCLocPrest();
        local.setCLocPrestacao(municipioPrestacao);
        local.setCPaisPrestacao(paisPrestacao);

        TCCServ cServ = new TCCServ();
        cServ.setCTribNac(cTribNac);
        cServ.setCTribMun(cTribMun);
        cServ.setXDescServ(descricaoServico);
        cServ.setCNBS(cNBS);
        cServ.setCIntContrib(codigoInternoContribuinte);

        TCServ serv = new TCServ();
        serv.setLocPrest(local);
        serv.setCServ(cServ);

        if (docRef != null || infoComplementar != null) {
            TCInfoCompl compl = new TCInfoCompl();
            compl.setDocRef(docRef);
            compl.setXInfComp(infoComplementar);
            serv.setInfoCompl(compl);
        }

        return serv;
    }

    private TCInfoValores montarValores() {
        TCVServPrest vServPrest = new TCVServPrest();
        vServPrest.setVServ(decimal(valorServico));
        vServPrest.setVReceb(decimal(valorRecebidoIntermediario));

        TCInfoValores valores = new TCInfoValores();
        valores.setVServPrest(vServPrest);

        if (descontoIncondicionado != null || descontoCondicionado != null) {
            TCVDescCondIncond descontos = new TCVDescCondIncond();
            descontos.setVDescIncond(decimal(descontoIncondicionado));
            descontos.setVDescCond(decimal(descontoCondicionado));
            valores.setVDescCondIncond(descontos);
        }

        if (percentualDeducao != null || valorDeducao != null) {
            TCInfoDedRed dedRed = new TCInfoDedRed();
            dedRed.setPDR(decimal(percentualDeducao));
            dedRed.setVDR(decimal(valorDeducao));
            valores.setVDedRed(dedRed);
        }

        valores.setTrib(montarTributacao());
        return valores;
    }

    private TCInfoTributacao montarTributacao() {
        TCTribMunicipal tribMun = new TCTribMunicipal();
        tribMun.setTribISSQN(tribISSQN);
        tribMun.setCPaisResult(cPaisResultado);
        tribMun.setTpImunidade(tpImunidade);
        tribMun.setTpRetISSQN(tpRetISSQN);
        tribMun.setPAliq(decimal(aliquota));
        if (tpSuspensao != null) {
            TCExigSuspensa susp = new TCExigSuspensa();
            susp.setTpSusp(tpSuspensao);
            susp.setNProcesso(nProcessoSuspensao);
            tribMun.setExigSusp(susp);
        }

        TCInfoTributacao trib = new TCInfoTributacao();
        trib.setTribMun(tribMun);
        trib.setTribFed(montarTributacaoFederal());
        trib.setTotTrib(montarTotalTributos());
        return trib;
    }

    private TCTribFederal montarTributacaoFederal() {
        boolean temPisCofins = cstPisCofins != null;
        boolean temRetencoes = valorRetCP != null || valorRetIRRF != null || valorRetCSLL != null;
        if (!temPisCofins && !temRetencoes) {
            return null;
        }

        TCTribFederal fed = new TCTribFederal();
        if (temPisCofins) {
            TCTribOutrosPisCofins pisCofins = new TCTribOutrosPisCofins();
            pisCofins.setCST(cstPisCofins);
            pisCofins.setVBCPisCofins(decimal(bcPisCofins));
            pisCofins.setPAliqPis(decimal(aliquotaPis));
            pisCofins.setPAliqCofins(decimal(aliquotaCofins));
            pisCofins.setVPis(decimal(valorPis));
            pisCofins.setVCofins(decimal(valorCofins));
            pisCofins.setTpRetPisCofins(tpRetPisCofins);
            fed.setPiscofins(pisCofins);
        }
        fed.setVRetCP(decimal(valorRetCP));
        fed.setVRetIRRF(decimal(valorRetIRRF));
        fed.setVRetCSLL(decimal(valorRetCSLL));
        return fed;
    }

    private TCTribTotal montarTotalTributos() {
        TCTribTotal total = new TCTribTotal();
        if (semTotalTributos) {
            total.setIndTotTrib("0");
        } else if (pTotTribSN != null) {
            total.setPTotTribSN(decimal(pTotTribSN));
        } else if (totTribPercentual) {
            TCTribTotalPercent percent = new TCTribTotalPercent();
            percent.setPTotTribFed(decimal(totTribFed));
            percent.setPTotTribEst(decimal(totTribEst));
            percent.setPTotTribMun(decimal(totTribMun));
            total.setPTotTrib(percent);
        } else {
            TCTribTotalMonet monet = new TCTribTotalMonet();
            monet.setVTotTribFed(decimal(totTribFed));
            monet.setVTotTribEst(decimal(totTribEst));
            monet.setVTotTribMun(decimal(totTribMun));
            total.setVTotTrib(monet);
        }
        return total;
    }

    private static String serializar(TCDPS dps) {
        return MarshallerNfse.paraXml(new ObjectFactory().createDPS(dps));
    }

    // =========================================================================================
    // Validação e formatação
    // =========================================================================================

    private void validar() {
        validarIdentificacao();

        if (prestador.cnpj == null && prestador.cpf == null
                && prestador.nif == null && prestador.cNaoNif == null) {
            throw new IllegalStateException(
                    "informe a identificação do prestador (prestadorCnpj/prestadorCpf/prestadorNif/prestadorSemNif)");
        }
        exigir(opSimpNac, "situação perante o Simples Nacional (naoOptanteSimplesNacional/optanteSimplesNacional…)");
        exigir(regEspTrib, "regime especial de tributação (semRegimeEspecial/regimeEspecial)");

        if (municipioPrestacao == null && paisPrestacao == null) {
            throw new IllegalStateException(
                    "informe o local da prestação (servicoPrestadoNoMunicipio/servicoPrestadoNoPais)");
        }
        exigir(cTribNac, "código de tributação nacional e descrição do serviço (servico)");
        exigir(descricaoServico, "descrição do serviço (servico)");
        if (validarCatalogoDeServicos && cNBS != null && !TabelaNbs.existe(cNBS)) {
            throw new IllegalStateException(
                    "cNBS " + cNBS + " não consta na NBS 2.0 (" + TabelaNbs.VERSAO_ANEXO
                            + "). Níveis de agrupamento da hierarquia não valem como cNBS; "
                            + "use uma folha de 9 dígitos.");
        }
        if (validarCatalogoDeServicos && !ListaServicoNacional.existe(cTribNac)) {
            throw new IllegalStateException(
                    "cTribNac " + cTribNac + " não consta na lista de serviços nacional ("
                            + ListaServicoNacional.VERSAO_ANEXO + "). Confira o código; se ele for novo, "
                            + "regenere a tabela ou chame semValidacaoDeCatalogo().");
        }

        if (valorServico == null) {
            throw new IllegalStateException("informe o valor do serviço (valorServico)");
        }
        exigir(tribISSQN, "tributação do ISSQN (issqnTributavel/issqnImunidade/issqnExportacao/issqnNaoIncidencia)");
        exigir(tpRetISSQN, "tipo de retenção do ISSQN (issqnNaoRetido/issqnRetidoPelo…)");

        boolean temTotalTributos = semTotalTributos || pTotTribSN != null
                || (totTribFed != null && totTribEst != null && totTribMun != null);
        if (!temTotalTributos) {
            throw new IllegalStateException(
                    "informe o total aproximado de tributos (semTotalTributos/totalTributos/totalTributosPercentual/"
                            + "totalTributosSimplesNacional)");
        }
    }

    private void validarIdentificacao() {
        if (ambiente == null) {
            throw new IllegalStateException("informe o ambiente");
        }
        exigir(serie, "série, número e competência (identificacao)");
        exigir(nDps, "número da DPS (identificacao)");
        if (competencia == null) {
            throw new IllegalStateException("informe a data de competência (identificacao)");
        }
        exigir(municipioEmissor, "município emissor (municipioEmissor)");
        if (prestador.cnpj == null && prestador.cpf == null) {
            // O Id exige inscrição federal; NIF e cNaoNIF não a fornecem.
            throw new IllegalStateException(
                    "o Id da DPS exige CNPJ ou CPF do prestador — prestador identificado por NIF não pode emitir DPS");
        }
    }

    private static void exigir(String valor, String oQue) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException("informe " + oQue);
        }
    }

    /**
     * Converte para o formato dos campos {@code TSDec*}, que são {@code xs:string} com pattern —
     * não {@code xs:decimal}. O pattern exige exatamente duas casas decimais (ou nenhuma), proíbe
     * zero à esquerda e não admite sinal. Um {@code BigDecimal} de escala diferente de 2 passaria
     * batido em Java e só seria rejeitado pelo schema da Sefin.
     */
    private static String decimal(BigDecimal valor) {
        if (valor == null) {
            return null;
        }
        if (valor.signum() < 0) {
            throw new IllegalArgumentException("valor monetário/percentual não pode ser negativo: " + valor);
        }
        return valor.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String zeroEsquerda(String valor, int tamanho) {
        if (valor.length() > tamanho) {
            throw new IllegalArgumentException(
                    "valor \"" + valor + "\" excede " + tamanho + " posições na composição do Id da DPS");
        }
        return "0".repeat(tamanho - valor.length()) + valor;
    }

    // =========================================================================================
    // Tipos auxiliares
    // =========================================================================================

    /** Estado mutável de uma pessoa (prestador, tomador ou intermediário) durante a construção. */
    private static final class Pessoa {
        private String cnpj;
        private String cpf;
        private String nif;
        private String cNaoNif;
        private String caepf;
        private String inscricaoMunicipal;
        private String nome;
        private Endereco endereco;
        private String telefone;
        private String email;
    }

    /**
     * Endereço nacional ou no exterior. Use as factories {@link #nacional} e {@link #exterior} —
     * o XSD trata os dois como uma escolha exclusiva.
     *
     * @param codigoMunicipio      código IBGE; nulo em endereço no exterior
     * @param codigoPais           código ISO; nulo em endereço nacional
     * @param cep                  CEP nacional ou código postal estrangeiro
     * @param cidade               apenas no exterior
     * @param estadoProvinciaRegiao apenas no exterior
     */
    public record Endereco(String codigoMunicipio,
                           String codigoPais,
                           String cep,
                           String cidade,
                           String estadoProvinciaRegiao,
                           String logradouro,
                           String numero,
                           String complemento,
                           String bairro) {

        public Endereco {
            codigoMunicipio = Documentos.municipio(codigoMunicipio, "Endereco.codigoMunicipio");
            // No exterior o "cep" é o código postal estrangeiro, que pode ter letras.
            if (codigoPais == null) {
                cep = Documentos.cep(cep, "Endereco.cep");
            }
        }

        public static Endereco nacional(String codigoMunicipioIbge, String cep, String logradouro,
                                        String numero, String complemento, String bairro) {
            return new Endereco(codigoMunicipioIbge, null, cep, null, null,
                    logradouro, numero, complemento, bairro);
        }

        public static Endereco exterior(String codigoPaisIso, String codigoPostal, String cidade,
                                        String estadoProvinciaRegiao, String logradouro,
                                        String numero, String complemento, String bairro) {
            return new Endereco(null, codigoPaisIso, codigoPostal, cidade, estadoProvinciaRegiao,
                    logradouro, numero, complemento, bairro);
        }
    }
}
