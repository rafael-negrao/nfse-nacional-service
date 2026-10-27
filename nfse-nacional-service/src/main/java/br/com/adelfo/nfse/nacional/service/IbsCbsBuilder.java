package br.com.adelfo.nfse.nacional.service;

import br.com.adelfo.nfse.nacional.schemas.TCEnderImovel;
import br.com.adelfo.nfse.nacional.schemas.TCInfoRefNFSe;
import br.com.adelfo.nfse.nacional.schemas.TCRTCInfoDest;
import br.com.adelfo.nfse.nacional.schemas.TCRTCInfoIBSCBS;
import br.com.adelfo.nfse.nacional.schemas.TCRTCInfoImovel;
import br.com.adelfo.nfse.nacional.schemas.TCRTCInfoTribIBSCBS;
import br.com.adelfo.nfse.nacional.schemas.TCRTCInfoTributosSitClas;
import br.com.adelfo.nfse.nacional.schemas.TCRTCInfoTributosTribRegular;
import br.com.adelfo.nfse.nacional.schemas.TCRTCInfoValoresIBSCBS;
import br.com.adelfo.nfse.nacional.schemas.TCRTCLocacaoImovel;
import br.com.adelfo.nfse.nacional.schemas.TCRTCUnidadesImobiliarias;
import br.com.adelfo.nfse.nacional.schemas.TCRTCValoresBCImoveis;

import java.util.ArrayList;
import java.util.List;

/**
 * Grupo IBS/CBS da DPS — as informações da Reforma Tributária que o emitente declara.
 *
 * <p><b>Este grupo é declaratório, não de cálculo.</b> Os valores apurados de IBS e CBS ficam no
 * nível da NFS-e ({@code NFSe/infNFSe/IBSCBS}), e é a plataforma que os calcula, batendo-os contra
 * a Calculadora oficial — as regras {@code E1530} em diante. Aqui vão indicadores, destinatário,
 * imóvel e a situação/classificação tributária; nenhum valor de imposto.
 *
 * <p>A consequência prática é boa: dá para preencher o grupo sem integrar com a Calculadora.
 *
 * <p><b>O que a NT 009/2026 tirou daqui:</b> o {@code finNFSe} subiu para a raiz da
 * {@code infDPS} — o {@link DpsBuilder} o preenche —, e os documentos de reembolso, repasse e
 * ressarcimento ({@code gReeRepRes}) foram fundidos ao ajuste de base de cálculo em
 * {@code valores/vAjusteBC}, que o {@link DpsBuilder} monta a partir de {@link DocumentoAjusteBC}.
 *
 * <pre>
 * dpsBuilder.ibsCbs(IbsCbsBuilder.novo()
 *         .codigoIndicadorOperacao("000001")
 *         .destinatarioEhOProprioTomador()
 *         .tributacao("000", "000001")
 *         .build());
 * </pre>
 */
public final class IbsCbsBuilder {

    private String indFinal;
    private String cIndOp;
    private String tpOper;
    private String tpEnteGov;
    private String indDest;

    private final List<String> nfseReferenciadas = new ArrayList<>();

    private Destinatario destinatario;

    private String cst;
    private String cClassTrib;
    private String cCredPres;
    private String cstRegular;
    private String cClassTribRegular;

    private ImovelIbsCbs imovel;

    private IbsCbsBuilder() {
    }

    public static IbsCbsBuilder novo() {
        return new IbsCbsBuilder();
    }

    /** indFinal = 1: operação de uso ou consumo pessoal (art. 57). */
    public IbsCbsBuilder usoOuConsumoPessoal() {
        this.indFinal = "1";
        return this;
    }

    /** indFinal = 0. */
    public IbsCbsBuilder naoEhUsoOuConsumoPessoal() {
        this.indFinal = "0";
        return this;
    }

    /**
     * Código indicador da operação de fornecimento — 6 dígitos, conforme o ANEXO_C
     * ({@code doc/anexos/ANEXO_C-IndOp_IBSCBS-v1.01.xlsx}).
     *
     * <p>Opcional desde a NT 009. {@code E0859} o <b>proíbe</b> quando a classificação tributária
     * indica que não há tributação de IBS/CBS ({@code ind_gIBSCBS = 0}), e {@code E0938} torna as
     * unidades imobiliárias obrigatórias para 020101, 020201 e 020301.
     */
    public IbsCbsBuilder codigoIndicadorOperacao(String cIndOp) {
        this.cIndOp = cIndOp;
        return this;
    }

    /**
     * Tipo de operação com entes governamentais ou sobre bens imóveis.
     *
     * <p>{@code E0903}: é obrigatório quando há ente governamental, ou quando o {@code cTribNac}
     * corresponde a serviços sobre bens imóveis.
     *
     * @param tpOper 1 fornecimento com pagamento posterior; 2 recebimento com fornecimento já
     *               realizado; 3 fornecimento com pagamento já realizado; 4 recebimento com
     *               fornecimento posterior; 5 fornecimento e recebimento concomitantes
     */
    public IbsCbsBuilder tipoOperacao(String tpOper) {
        this.tpOper = tpOper;
        return this;
    }

    /**
     * Ente governamental destinatário.
     *
     * @param tpEnteGov 1 União; 2 Estado; 3 Distrito Federal; 4 Município
     */
    public IbsCbsBuilder enteGovernamental(String tpEnteGov) {
        this.tpEnteGov = tpEnteGov;
        return this;
    }

    /**
     * NFS-e referenciada. {@code E0905} torna o grupo obrigatório quando {@code tpOper} é 2 ou 3 —
     * os casos em que fornecimento e pagamento ocorrem em documentos distintos. Até 99.
     */
    public IbsCbsBuilder referenciaNfse(String chaveAcesso) {
        nfseReferenciadas.add(Documentos.chaveAcesso(chaveAcesso, "referenciaNfse"));
        return this;
    }

    /** indDest = 0: o destinatário é o próprio tomador identificado na nota. */
    public IbsCbsBuilder destinatarioEhOProprioTomador() {
        this.indDest = "0";
        this.destinatario = null;
        return this;
    }

    /**
     * indDest = 1: o destinatário é outra pessoa, ou outro estabelecimento do tomador.
     *
     * <p>{@code E0910}: o grupo do destinatário só pode ser informado neste caso.
     */
    public IbsCbsBuilder destinatarioDistintoDoTomador(Destinatario destinatario) {
        this.indDest = "1";
        this.destinatario = destinatario;
        return this;
    }

    /**
     * Situação e classificação tributária do IBS/CBS.
     *
     * @param cst        Código de Situação Tributária — 3 dígitos
     * @param cClassTrib Código de Classificação Tributária — 6 dígitos. {@code E0958} rejeita
     *                   código não suportado para prestação de serviço
     */
    public IbsCbsBuilder tributacao(String cst, String cClassTrib) {
        this.cst = cst;
        this.cClassTrib = cClassTrib;
        return this;
    }

    /** Código de crédito presumido. */
    public IbsCbsBuilder creditoPresumido(String cCredPres) {
        this.cCredPres = cCredPres;
        return this;
    }

    /**
     * Grupo de tributação regular, usado quando a operação tem tributação diferenciada mas é
     * preciso declarar qual seria a regular.
     *
     * <p>{@code E0964}: não informe quando o indicador da classificação tributária não o exigir.
     */
    public IbsCbsBuilder tributacaoRegular(String cstRegular, String cClassTribRegular) {
        this.cstRegular = cstRegular;
        this.cClassTribRegular = cClassTribRegular;
        return this;
    }

    /** Bens imóveis a que a operação se refere; ver {@link ImovelIbsCbs}. */
    public IbsCbsBuilder imovel(ImovelIbsCbs imovel) {
        this.imovel = imovel;
        return this;
    }

    // =========================================================================================

    TCRTCInfoIBSCBS build() {
        validar();

        TCRTCInfoIBSCBS grupo = new TCRTCInfoIBSCBS();
        grupo.setIndFinal(indFinal);
        grupo.setCIndOp(cIndOp);
        grupo.setTpOper(tpOper);
        grupo.setTpEnteGov(tpEnteGov);
        grupo.setIndDest(indDest);

        if (!nfseReferenciadas.isEmpty()) {
            TCInfoRefNFSe ref = new TCInfoRefNFSe();
            ref.getRefNFSe().addAll(nfseReferenciadas);
            grupo.setGRefNFSe(ref);
        }
        if (destinatario != null) {
            grupo.setDest(montarDestinatario());
        }
        if (imovel != null) {
            grupo.setImovel(montarImovel());
        }
        grupo.setValores(montarValores());

        return grupo;
    }

    private TCRTCInfoDest montarDestinatario() {
        TCRTCInfoDest dest = new TCRTCInfoDest();
        dest.setCNPJ(destinatario.cnpj());
        dest.setCPF(destinatario.cpf());
        dest.setNIF(destinatario.nif());
        dest.setXNome(destinatario.nome());
        dest.setFone(destinatario.telefone());
        dest.setEmail(destinatario.email());
        return dest;
    }

    /**
     * {@code CST} e {@code cClassTrib} vão direto em {@code trib} desde a NT 009; o
     * {@code gIBSCBS}, que antes os carregava, ficou opcional e só sai quando há crédito presumido
     * ou tributação regular a declarar.
     */
    private TCRTCInfoValoresIBSCBS montarValores() {
        TCRTCInfoTribIBSCBS trib = new TCRTCInfoTribIBSCBS();
        trib.setCST(cst);
        trib.setCClassTrib(cClassTrib);

        if (cCredPres != null || cstRegular != null) {
            TCRTCInfoTributosSitClas sitClas = new TCRTCInfoTributosSitClas();
            sitClas.setCCredPres(cCredPres);
            if (cstRegular != null) {
                TCRTCInfoTributosTribRegular regular = new TCRTCInfoTributosTribRegular();
                regular.setCSTReg(cstRegular);
                regular.setCClassTribReg(cClassTribRegular);
                sitClas.setGTribRegular(regular);
            }
            trib.setGIBSCBS(sitClas);
        }

        TCRTCInfoValoresIBSCBS valores = new TCRTCInfoValoresIBSCBS();
        valores.setTrib(trib);
        return valores;
    }

    private TCRTCInfoImovel montarImovel() {
        TCRTCInfoImovel info = new TCRTCInfoImovel();
        info.setCMun(imovel.codigoMunicipio());

        if (imovel.locacao() != null) {
            ImovelIbsCbs.Locacao l = imovel.locacao();
            TCRTCLocacaoImovel locacao = new TCRTCLocacaoImovel();
            locacao.setPCopropriedade(DpsBuilder.decimal(l.percentualCopropriedade()));
            locacao.setVTotOper(DpsBuilder.decimal(l.valorTotalOperacao()));
            locacao.setVDescIncondTot(DpsBuilder.decimal(l.descontoIncondicionado()));
            locacao.setVDescCondTot(DpsBuilder.decimal(l.descontoCondicionado()));
            locacao.setDVencOrig(l.vencimentoOriginal() == null ? null : l.vencimentoOriginal().toString());
            info.setGLocacao(locacao);
        }

        for (ImovelIbsCbs.UnidadeImobiliaria u : imovel.unidades()) {
            TCRTCUnidadesImobiliarias unidade = new TCRTCUnidadesImobiliarias();
            unidade.setInscImobFisc(u.inscricaoImobiliariaFiscal());
            unidade.setCCIB(u.codigoCib());
            if (u.endereco() != null) {
                ImovelIbsCbs.EnderecoImovel e = u.endereco();
                TCEnderImovel end = new TCEnderImovel();
                end.setCEP(e.cep());
                end.setXLgr(e.logradouro());
                end.setNro(e.numero());
                end.setXCpl(e.complemento());
                end.setXBairro(e.bairro());
                unidade.setEnd(end);
            }
            for (ImovelIbsCbs.AjusteLocacao a : u.ajustes()) {
                TCRTCValoresBCImoveis ajuste = new TCRTCValoresBCImoveis();
                ajuste.setTpAjusteBCLocImoveis(a.tipo());
                ajuste.setXTpAjusteBCLocImoveis(a.descricao());
                ajuste.setVAjusteBCLocImoveis(DpsBuilder.decimal(a.valor()));
                unidade.getGAjusteBCLocImoveis().add(ajuste);
            }
            info.getGUnidImob().add(unidade);
        }
        return info;
    }

    private void validar() {
        if (cIndOp != null && !cIndOp.matches("[0-9]{6}")) {
            throw new IllegalStateException(
                    "o código indicador da operação tem 6 dígitos, conforme o ANEXO_C "
                            + "(codigoIndicadorOperacao); recebido: " + cIndOp);
        }
        // E0901: o código precisa constar na tabela do ANEXO_C.
        if (cIndOp != null && !TabelaIndicadoresOperacao.existe(cIndOp)) {
            throw new IllegalStateException(
                    "cIndOp " + cIndOp + " não consta na tabela de indicadores de operação ("
                            + TabelaIndicadoresOperacao.VERSAO_ANEXO + ") — E0901");
        }
        if (indDest == null) {
            throw new IllegalStateException(
                    "informe o indicador de destinatário (destinatarioEhOProprioTomador ou "
                            + "destinatarioDistintoDoTomador)");
        }
        if (cst == null || !cst.matches("[0-9]{3}")) {
            throw new IllegalStateException(
                    "informe o CST com 3 dígitos (tributacao); recebido: " + cst);
        }
        if (cClassTrib == null || !cClassTrib.matches("[0-9]{6}")) {
            throw new IllegalStateException(
                    "informe o cClassTrib com 6 dígitos (tributacao); recebido: " + cClassTrib);
        }
        // E0905: fornecimento e pagamento em documentos distintos exigem a referência.
        if (("2".equals(tpOper) || "3".equals(tpOper)) && nfseReferenciadas.isEmpty()) {
            throw new IllegalStateException(
                    "com tpOper " + tpOper + " é obrigatório referenciar a NFS-e (referenciaNfse) — E0905");
        }
        if (nfseReferenciadas.size() > 99) {
            throw new IllegalStateException("no máximo 99 NFS-e referenciadas; informadas: "
                    + nfseReferenciadas.size());
        }
        if (cstRegular != null && cClassTribRegular == null) {
            throw new IllegalStateException(
                    "o grupo de tributação regular exige CST e cClassTrib (tributacaoRegular)");
        }
        // E0938: operações com unidade imobiliária precisam identificá-la.
        if (cIndOp != null && cIndOp.matches("020101|020201|020301")
                && (imovel == null || imovel.unidades().isEmpty())) {
            throw new IllegalStateException("com cIndOp " + cIndOp
                    + " é obrigatório informar as unidades imobiliárias (imovel) — E0938");
        }
    }

    /**
     * Destinatário do serviço, quando não é o próprio tomador.
     *
     * <p>Informe exatamente um entre CNPJ, CPF e NIF.
     */
    public record Destinatario(String cnpj, String cpf, String nif,
                               String nome, String telefone, String email) {

        public Destinatario {
            cnpj = Documentos.cnpj(cnpj, "Destinatario.cnpj");
            cpf = Documentos.cpf(cpf, "Destinatario.cpf");
            telefone = Documentos.telefone(telefone, "Destinatario.telefone");
            long informados = java.util.stream.Stream.of(cnpj, cpf, nif)
                    .filter(v -> v != null && !v.isBlank()).count();
            if (informados != 1) {
                throw new IllegalArgumentException(
                        "informe exatamente um entre CNPJ, CPF e NIF do destinatário");
            }
            if (nome == null || nome.isBlank()) {
                throw new IllegalArgumentException("o nome do destinatário é obrigatório");
            }
        }

        public static Destinatario porCnpj(String cnpj, String nome) {
            return new Destinatario(cnpj, null, null, nome, null, null);
        }

        public static Destinatario porCpf(String cpf, String nome) {
            return new Destinatario(null, cpf, null, nome, null, null);
        }

        public static Destinatario porNif(String nif, String nome) {
            return new Destinatario(null, null, nif, nome, null, null);
        }
    }
}
