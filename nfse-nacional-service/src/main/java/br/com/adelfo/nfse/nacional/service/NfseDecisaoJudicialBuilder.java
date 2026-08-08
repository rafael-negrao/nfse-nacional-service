package br.com.adelfo.nfse.nacional.service;

import br.com.adelfo.nfse.nacional.schemas.ObjectFactory;
import br.com.adelfo.nfse.nacional.schemas.TCDPS;
import br.com.adelfo.nfse.nacional.schemas.TCEmitente;
import br.com.adelfo.nfse.nacional.schemas.TCEnderecoEmitente;
import br.com.adelfo.nfse.nacional.schemas.TCInfNFSe;
import br.com.adelfo.nfse.nacional.schemas.TCNFSe;
import br.com.adelfo.nfse.nacional.schemas.TCValoresNFSe;
import br.com.adelfo.nfse.nacional.schemas.TSUF;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Monta a NFS-e completa do fluxo de <b>emissão por decisão administrativa ou judicial</b>, o
 * chamado "bypass" ({@code POST /decisao-judicial/nfse}).
 *
 * <p>No fluxo regular envia-se só a DPS e a plataforma calcula o resto — local de incidência,
 * alíquota, valores, numeração. Aqui não: o contribuinte envia a NFS-e inteira, e a Sefin aplica
 * apenas validações mínimas (dígitos de CPF/CNPJ e afins). <b>A responsabilidade pelo conteúdo é
 * inteiramente do contribuinte</b>, que responde por fiscalização posterior se extrapolar o que a
 * decisão autorizou.
 *
 * <p>Pré-requisito que não é técnico: o município precisa ter cadastrado a decisão na plataforma,
 * com o número do processo, e autorizado o contribuinte a usar este fluxo. Sem isso a emissão é
 * recusada.
 *
 * <p>Valores fixos que o manual determina para este fluxo, e que este builder aplica sozinho:
 * {@code cStat=102}, {@code ambGer=2} (Sefin nacional), {@code tpEmis=1} (emissão direta no modelo
 * nacional), {@code nDFSe=0} e {@code dhProc} igual a {@code dhEmi}.
 *
 * <p>Uso: monte a DPS como no fluxo normal e acrescente o que a plataforma geraria.
 * <pre>
 * String xml = NfseDecisaoJudicialBuilder.sobre(dpsBuilder)
 *         .numeroNfse("240")
 *         .codigoNumerico("123456789")
 *         .localEmissao("São Paulo")
 *         .localPrestacao("São Paulo")
 *         .incidencia("3550308", "São Paulo")
 *         .descricaoTributacaoNacional("Elaboração de programa de computador")
 *         .emitente("ADELFO …", "33029610", enderecoEmitente)
 *         .aliquota(new BigDecimal("2.00"))
 *         .valores(new BigDecimal("1000.00"), new BigDecimal("20.00"), new BigDecimal("980.00"))
 *         .build();
 * </pre>
 */
public final class NfseDecisaoJudicialBuilder {

    /** Situação que marca a nota como emitida por decisão administrativa ou judicial. */
    public static final String CSTAT_DECISAO = "102";

    /** Ambiente gerador: 2 = Sefin nacional. */
    private static final String AMB_GER_SEFIN = "2";

    /** Tipo de emissão: 1 = emissão direta no modelo da NFS-e Nacional. */
    private static final String TP_EMIS_DIRETA = "1";

    /**
     * Número de DF-e municipal. O manual deste fluxo manda preencher com {@code "0"} para indicar
     * ausência — mas o XSD tipa o campo como {@code TSNDFSe}, cujo pattern
     * {@code [1-9]{1}[0-9]{0,12}} <b>não admite zero</b>. Manual e schema se contradizem.
     *
     * <p>Prevalece o manual, que é específico deste fluxo e prescreve o valor de forma explícita.
     * Se a Sefin rejeitar por schema, use {@link #numeroDfeMunicipal(String)} para sobrescrever.
     */
    private static final String N_DFSE_AUSENTE = "0";

    /** Ver {@link DataHoraFiscal} — o offset precisa ser numérico e o fuso, o de Brasília. */
    private static final DateTimeFormatter FORMATO_DATA_HORA = DataHoraFiscal.FORMATO;
    private static final DateTimeFormatter FORMATO_ANO_MES = DateTimeFormatter.ofPattern("yyMM");

    private final DpsBuilder dps;

    private String nNFSe;
    private String codigoNumerico;
    private String xLocEmi;
    private String xLocPrestacao;
    private String cLocIncid;
    private String xLocIncid;
    private String xTribNac;
    private String xTribMun;
    private String xNBS;
    private String xOutInf;
    private String verAplic = DpsBuilder.VER_APLIC;
    private String nDFSe = N_DFSE_AUSENTE;

    private String emitenteNome;
    private String emitenteFantasia;
    private String emitenteInscricaoMunicipal;
    private String emitenteTelefone;
    private String emitenteEmail;
    private EnderecoEmitente emitenteEndereco;

    private BigDecimal vBC;
    private BigDecimal pAliqAplic;
    private BigDecimal vISSQN;
    private BigDecimal vTotalRet;
    private BigDecimal vLiq;

    private NfseDecisaoJudicialBuilder(DpsBuilder dps) {
        this.dps = dps;
    }

    /** Parte da DPS já montada; os campos abaixo completam o que a plataforma geraria. */
    public static NfseDecisaoJudicialBuilder sobre(DpsBuilder dps) {
        if (dps == null) {
            throw new IllegalArgumentException("a DPS é obrigatória — a NFS-e a carrega embutida");
        }
        return new NfseDecisaoJudicialBuilder(dps);
    }

    /**
     * Número da NFS-e. No fluxo regular a plataforma o gera; aqui a sequência é controlada pelo
     * contribuinte e <b>não pode colidir</b> com NFS-e existentes.
     */
    public NfseDecisaoJudicialBuilder numeroNfse(String nNFSe) {
        this.nNFSe = nNFSe;
        return this;
    }

    /**
     * Código numérico aleatório de 9 dígitos que compõe o {@code Id}, gerado pelo contribuinte.
     * Informe-o explicitamente para que o documento seja reproduzível.
     */
    public NfseDecisaoJudicialBuilder codigoNumerico(String cNum) {
        this.codigoNumerico = cNum;
        return this;
    }

    /** Nome do município emissor, conforme o ANEXO_A. */
    public NfseDecisaoJudicialBuilder localEmissao(String xLocEmi) {
        this.xLocEmi = xLocEmi;
        return this;
    }

    /** Nome do município de prestação, conforme o ANEXO_A. */
    public NfseDecisaoJudicialBuilder localPrestacao(String xLocPrestacao) {
        this.xLocPrestacao = xLocPrestacao;
        return this;
    }

    /**
     * Local de incidência do ISSQN. Deixe de fora nos casos sem destaque do imposto — imunidade,
     * exportação de serviço ou {@code cTribNac=990101}.
     *
     * @param codigoIbge código de 7 dígitos, conforme o ANEXO_A
     * @param nome       nome do município
     */
    public NfseDecisaoJudicialBuilder incidencia(String codigoIbge, String nome) {
        this.cLocIncid = Documentos.municipio(codigoIbge, "incidencia");
        this.xLocIncid = nome;
        return this;
    }

    /**
     * Descrição do código de tributação nacional. Obrigatória nesta emissão, mas <b>opcional
     * aqui</b>: sem ela, o texto sai da {@link ListaServicoNacional} pelo {@code cTribNac} da DPS.
     */
    public NfseDecisaoJudicialBuilder descricaoTributacaoNacional(String xTribNac) {
        this.xTribNac = xTribNac;
        return this;
    }

    /** Descrição do código de tributação municipal, definida pelo município de incidência. */
    public NfseDecisaoJudicialBuilder descricaoTributacaoMunicipal(String xTribMun) {
        this.xTribMun = xTribMun;
        return this;
    }

    /** Descrição do código NBS. */
    public NfseDecisaoJudicialBuilder descricaoNbs(String xNBS) {
        this.xNBS = xNBS;
        return this;
    }

    /** Informações de interesse do contribuinte, livres. */
    public NfseDecisaoJudicialBuilder outrasInformacoes(String xOutInf) {
        this.xOutInf = xOutInf;
        return this;
    }

    /**
     * Sobrescreve o {@code nDFSe}. Só é necessário se a Sefin recusar o {@code "0"} que o manual
     * determina — ver a nota em {@link #N_DFSE_AUSENTE}.
     */
    public NfseDecisaoJudicialBuilder numeroDfeMunicipal(String nDFSe) {
        this.nDFSe = nDFSe;
        return this;
    }

    public NfseDecisaoJudicialBuilder versaoAplicativo(String verAplic) {
        this.verAplic = verAplic;
        return this;
    }

    /**
     * Dados do emitente que aparecem no corpo da NFS-e — separados dos do prestador na DPS, embora
     * descrevam a mesma pessoa. O CNPJ/CPF vem do prestador da DPS.
     */
    public NfseDecisaoJudicialBuilder emitente(String nome, String inscricaoMunicipal,
                                               EnderecoEmitente endereco) {
        this.emitenteNome = nome;
        this.emitenteInscricaoMunicipal = inscricaoMunicipal;
        this.emitenteEndereco = endereco;
        return this;
    }

    public NfseDecisaoJudicialBuilder emitenteNomeFantasia(String xFant) {
        this.emitenteFantasia = xFant;
        return this;
    }

    public NfseDecisaoJudicialBuilder emitenteContato(String telefone, String email) {
        this.emitenteTelefone = telefone;
        this.emitenteEmail = email;
        return this;
    }

    /**
     * Alíquota aplicada do ISSQN. É <b>obrigatória neste fluxo</b> quando houver incidência,
     * embora seja opcional na DPS do fluxo regular — aqui a plataforma não a determina.
     */
    public NfseDecisaoJudicialBuilder aliquota(BigDecimal pAliqAplic) {
        this.pAliqAplic = pAliqAplic;
        return this;
    }

    /**
     * Valores apurados da nota, que no fluxo regular a plataforma calcularia.
     *
     * @param baseCalculo base de cálculo do ISSQN
     * @param issqn       valor do ISSQN
     * @param liquido     valor líquido da NFS-e — o único obrigatório no leiaute
     */
    public NfseDecisaoJudicialBuilder valores(BigDecimal baseCalculo, BigDecimal issqn, BigDecimal liquido) {
        this.vBC = baseCalculo;
        this.vISSQN = issqn;
        this.vLiq = liquido;
        return this;
    }

    /** Total retido da nota. */
    public NfseDecisaoJudicialBuilder totalRetido(BigDecimal vTotalRet) {
        this.vTotalRet = vTotalRet;
        return this;
    }

    // =========================================================================================

    /**
     * Chave de acesso da NFS-e — os 50 dígitos do {@code Id} sem o literal {@code "NFS"}.
     * Disponível antes do envio porque, neste fluxo, quem a monta é o contribuinte.
     */
    public String chaveAcesso() {
        return montarId().substring(3);
    }

    /** XML da NFS-e, não assinado, pronto para {@code NfseService.emitirPorDecisaoJudicial}. */
    public String build() {
        validar();

        TCInfNFSe info = new TCInfNFSe();
        info.setId(montarId());
        info.setXLocEmi(nomeMunicipio(xLocEmi, dps.municipioEmissorInformado(), "localEmissao"));
        info.setXLocPrestacao(nomeMunicipio(
                xLocPrestacao, dps.municipioPrestacaoInformado(), "localPrestacao"));
        info.setNNFSe(nNFSe);
        info.setCLocIncid(cLocIncid);
        info.setXLocIncid(cLocIncid == null ? null
                : nomeMunicipio(xLocIncid, cLocIncid, "incidencia"));
        info.setXTribNac(descricaoTributacaoNacionalResolvida());
        info.setXTribMun(xTribMun);
        info.setXNBS(xNBS);
        info.setVerAplic(verAplic);
        info.setAmbGer(AMB_GER_SEFIN);
        info.setTpEmis(TP_EMIS_DIRETA);
        info.setCStat(CSTAT_DECISAO);
        // O manual determina dhProc igual a dhEmi: a nota não passa por processamento da
        // plataforma, então não há um instante de processamento distinto do de emissão.
        info.setDhProc(dps.dataHoraEmissaoFormatada());
        info.setNDFSe(nDFSe);
        info.setEmit(montarEmitente());
        info.setValores(montarValores());
        info.setXOutInf(xOutInf);

        TCDPS documentoDps = dps.buildJaxb();
        info.setDPS(documentoDps);

        TCNFSe nfse = new TCNFSe();
        nfse.setVersao(DpsBuilder.VERSAO_LEIAUTE);
        nfse.setInfNFSe(info);

        return MarshallerNfse.paraXml(new ObjectFactory().createNFSe(nfse));
    }

    /**
     * Id da NFS-e: {@code "NFS"} + cMun (7) + ambGer (1) + tipo de inscrição (1) + inscrição
     * federal (14) + nNFSe (13) + AAMM da emissão (4) + código numérico (9) + DV (1) — 53 posições.
     *
     * <p>No fluxo regular a plataforma monta este identificador; aqui é responsabilidade do
     * contribuinte, <b>inclusive o dígito verificador</b>, calculado por módulo 11.
     */
    private String montarId() {
        String semDv = "NFS"
                + dps.municipioEmissorInformado()
                + AMB_GER_SEFIN
                + dps.tipoInscricaoFederal()
                + zeroEsquerda(dps.inscricaoFederalPrestador(), 14)
                + zeroEsquerda(exigir(nNFSe, "número da NFS-e (numeroNfse)"), 13)
                + dps.dataHoraEmissao().format(FORMATO_ANO_MES)
                + codigoNumericoValidado();
        return semDv + digitoVerificador(semDv.substring(3));
    }

    /** Delegação para {@link Documentos#digitoVerificadorChaveAcesso(String)}. */
    static int digitoVerificador(String digitos) {
        return Documentos.digitoVerificadorChaveAcesso(digitos);
    }

    private TCEmitente montarEmitente() {
        TCEmitente emit = new TCEmitente();
        if (dps.inscricaoFederalPrestador().length() == 14) {
            emit.setCNPJ(dps.inscricaoFederalPrestador());
        } else {
            emit.setCPF(dps.inscricaoFederalPrestador());
        }
        emit.setIM(emitenteInscricaoMunicipal);
        emit.setXNome(emitenteNome);
        emit.setXFant(emitenteFantasia);
        emit.setFone(emitenteTelefone);
        emit.setEmail(emitenteEmail);

        TCEnderecoEmitente end = new TCEnderecoEmitente();
        end.setXLgr(emitenteEndereco.logradouro());
        end.setNro(emitenteEndereco.numero());
        end.setXCpl(emitenteEndereco.complemento());
        end.setXBairro(emitenteEndereco.bairro());
        end.setCMun(emitenteEndereco.codigoMunicipio());
        // O XSD tipa a UF como enumeração; converter aqui rejeita sigla inválida com uma mensagem
        // melhor que a IllegalArgumentException crua do valueOf gerado pelo JAXB.
        try {
            end.setUF(TSUF.fromValue(emitenteEndereco.uf()));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("UF inválida: " + emitenteEndereco.uf(), e);
        }
        end.setCEP(emitenteEndereco.cep());
        emit.setEnderNac(end);

        return emit;
    }

    private TCValoresNFSe montarValores() {
        TCValoresNFSe valores = new TCValoresNFSe();
        valores.setVBC(decimal(vBC));
        valores.setPAliqAplic(decimal(pAliqAplic));
        valores.setVISSQN(decimal(vISSQN));
        valores.setVTotalRet(decimal(vTotalRet));
        valores.setVLiq(decimal(vLiq));
        return valores;
    }

    /**
     * Descrição do {@code cTribNac}. Se o chamador não a informou, sai da
     * {@link ListaServicoNacional} — no fluxo regular esse texto é gerado pela plataforma, e aqui
     * ele é o mesmo do anexo oficial, então não faz sentido exigir que seja redigitado.
     */
    private String descricaoTributacaoNacionalResolvida() {
        if (xTribNac != null) {
            return xTribNac;
        }
        return ListaServicoNacional.descricao(dps.codigoTributacaoNacionalInformado())
                .orElseThrow(() -> new IllegalStateException(
                        "informe a descrição da tributação nacional (descricaoTributacaoNacional): "
                                + "o código " + dps.codigoTributacaoNacionalInformado()
                                + " não consta em " + ListaServicoNacional.VERSAO_ANEXO));
    }

    /**
     * Nome do município. Se o chamador não o informou, sai da {@link TabelaMunicipios} pelo código
     * — mesmo raciocínio do {@code xTribNac}: no fluxo regular a plataforma gera este texto, e ele
     * é o do anexo oficial.
     */
    private static String nomeMunicipio(String informado, String codigoIbge, String metodo) {
        if (informado != null) {
            return informado;
        }
        if (codigoIbge == null) {
            return null;
        }
        return TabelaMunicipios.nome(codigoIbge).orElseThrow(() -> new IllegalStateException(
                "informe o nome do município (" + metodo + "): o código " + codigoIbge
                        + " não consta em " + TabelaMunicipios.VERSAO_ANEXO));
    }

    private void validar() {
        exigir(nNFSe, "número da NFS-e (numeroNfse)");
        exigir(emitenteNome, "nome do emitente (emitente)");
        if (emitenteEndereco == null) {
            throw new IllegalStateException("informe o endereço do emitente (emitente)");
        }
        if (vLiq == null) {
            throw new IllegalStateException("informe os valores da nota (valores)");
        }
        if (cLocIncid != null && pAliqAplic == null) {
            throw new IllegalStateException(
                    "havendo local de incidência, a alíquota é obrigatória neste fluxo (aliquota)");
        }
    }

    private String codigoNumericoValidado() {
        if (codigoNumerico == null || !codigoNumerico.matches("[0-9]{9}")) {
            throw new IllegalStateException(
                    "informe o código numérico de 9 dígitos que compõe o Id (codigoNumerico); recebido: "
                            + codigoNumerico);
        }
        return codigoNumerico;
    }

    private static String exigir(String valor, String oQue) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException("informe " + oQue);
        }
        return valor;
    }

    /** Mesmo formato dos TSDec* da DPS: xs:string com duas casas decimais. */
    private static String decimal(BigDecimal valor) {
        if (valor == null) {
            return null;
        }
        if (valor.signum() < 0) {
            throw new IllegalArgumentException("valor não pode ser negativo: " + valor);
        }
        return valor.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String zeroEsquerda(String valor, int tamanho) {
        if (valor.length() > tamanho) {
            throw new IllegalArgumentException(
                    "valor \"" + valor + "\" excede " + tamanho + " posições na composição do Id da NFS-e");
        }
        return "0".repeat(tamanho - valor.length()) + valor;
    }

    /**
     * Endereço do emitente no corpo da NFS-e. Difere do {@code DpsBuilder.Endereco}: é sempre
     * nacional e exige UF, que o endereço da DPS não tem.
     */
    public record EnderecoEmitente(String logradouro,
                                   String numero,
                                   String complemento,
                                   String bairro,
                                   String codigoMunicipio,
                                   String uf,
                                   String cep) {

        public EnderecoEmitente {
            codigoMunicipio = Documentos.municipio(codigoMunicipio, "EnderecoEmitente.codigoMunicipio");
            cep = Documentos.cep(cep, "EnderecoEmitente.cep");
        }
    }
}
