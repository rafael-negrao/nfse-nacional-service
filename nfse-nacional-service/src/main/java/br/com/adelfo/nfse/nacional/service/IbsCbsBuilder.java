package br.com.adelfo.nfse.nacional.service;

import br.com.adelfo.nfse.nacional.schemas.TCEnderExtSimples;
import br.com.adelfo.nfse.nacional.schemas.TCEnderObraEvento;
import br.com.adelfo.nfse.nacional.schemas.TCInfoRefNFSe;
import br.com.adelfo.nfse.nacional.schemas.TCRTCInfoImovel;
import br.com.adelfo.nfse.nacional.schemas.TCRTCInfoReeRepRes;
import br.com.adelfo.nfse.nacional.schemas.TCRTCListaDoc;
import br.com.adelfo.nfse.nacional.schemas.TCRTCListaDocDFe;
import br.com.adelfo.nfse.nacional.schemas.TCRTCListaDocFiscalOutro;
import br.com.adelfo.nfse.nacional.schemas.TCRTCListaDocFornec;
import br.com.adelfo.nfse.nacional.schemas.TCRTCListaDocOutro;
import br.com.adelfo.nfse.nacional.schemas.TCRTCInfoDest;
import br.com.adelfo.nfse.nacional.schemas.TCRTCInfoIBSCBS;
import br.com.adelfo.nfse.nacional.schemas.TCRTCInfoTributosIBSCBS;
import br.com.adelfo.nfse.nacional.schemas.TCRTCInfoTributosSitClas;
import br.com.adelfo.nfse.nacional.schemas.TCRTCInfoTributosTribRegular;
import br.com.adelfo.nfse.nacional.schemas.TCRTCInfoValoresIBSCBS;

import java.math.BigDecimal;
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
 * <pre>
 * dpsBuilder.ibsCbs(IbsCbsBuilder.novo()
 *         .nfseRegular()
 *         .codigoIndicadorOperacao("000001")
 *         .destinatarioEhOProprioTomador()
 *         .tributacao("000", "000001")
 *         .build());
 * </pre>
 */
public final class IbsCbsBuilder {

    /** finNFSe = 0: NFS-e regular. É o único valor que a enumeração admite hoje. */
    private static final String FIN_NFSE_REGULAR = "0";

    private String finNFSe = FIN_NFSE_REGULAR;
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
    private final List<DocumentoReembolso> reembolsos = new ArrayList<>();
    private BigDecimal valorServicoParaConferencia;

    private IbsCbsBuilder() {
    }

    public static IbsCbsBuilder novo() {
        return new IbsCbsBuilder();
    }

    /** finNFSe = 0 — o padrão. */
    public IbsCbsBuilder nfseRegular() {
        this.finNFSe = FIN_NFSE_REGULAR;
        return this;
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
     * ({@code doc/anexos/ANEXO_C-IndOp_IBSCBS-v1.01.xlsx}). Obrigatório no grupo.
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

    /**
     * Bem imóvel a que a operação se refere. {@code E0931} torna este grupo obrigatório para os
     * subitens de serviços sobre imóveis; ver {@link ImovelIbsCbs}.
     */
    public IbsCbsBuilder imovel(ImovelIbsCbs imovel) {
        this.imovel = imovel;
        return this;
    }

    /**
     * Documento de reembolso, repasse ou ressarcimento. Até 1000 por nota; cada chamada acrescenta
     * um. Ver {@link DocumentoReembolso}.
     */
    public IbsCbsBuilder reembolso(DocumentoReembolso documento) {
        reembolsos.add(documento);
        return this;
    }

    // =========================================================================================

    /**
     * Valor do serviço, informado pelo {@code DpsBuilder} para conferir a regra {@code E0953} — o
     * reembolso não pode superá-lo. Fica fora da API pública porque o dado já está na DPS.
     */
    void conferirReembolsoContra(BigDecimal valorServico) {
        this.valorServicoParaConferencia = valorServico;
    }

    TCRTCInfoIBSCBS build() {
        validar();

        TCRTCInfoIBSCBS grupo = new TCRTCInfoIBSCBS();
        grupo.setFinNFSe(finNFSe);
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

    private TCRTCInfoValoresIBSCBS montarValores() {
        TCRTCInfoTributosSitClas sitClas = new TCRTCInfoTributosSitClas();
        sitClas.setCST(cst);
        sitClas.setCClassTrib(cClassTrib);
        sitClas.setCCredPres(cCredPres);
        if (cstRegular != null) {
            TCRTCInfoTributosTribRegular regular = new TCRTCInfoTributosTribRegular();
            regular.setCSTReg(cstRegular);
            regular.setCClassTribReg(cClassTribRegular);
            sitClas.setGTribRegular(regular);
        }

        TCRTCInfoTributosIBSCBS trib = new TCRTCInfoTributosIBSCBS();
        trib.setGIBSCBS(sitClas);

        TCRTCInfoValoresIBSCBS valores = new TCRTCInfoValoresIBSCBS();
        valores.setTrib(trib);
        if (!reembolsos.isEmpty()) {
            TCRTCInfoReeRepRes grupo = new TCRTCInfoReeRepRes();
            reembolsos.forEach(d -> grupo.getDocumentos().add(montarDocumento(d)));
            valores.setGReeRepRes(grupo);
        }
        return valores;
    }

    private TCRTCInfoImovel montarImovel() {
        TCRTCInfoImovel info = new TCRTCInfoImovel();
        info.setInscImobFisc(imovel.inscricaoImobiliariaFiscal());
        info.setCCIB(imovel.codigoCib());
        if (imovel.endereco() != null) {
            ImovelIbsCbs.EnderecoImovel e = imovel.endereco();
            TCEnderObraEvento end = new TCEnderObraEvento();
            if (e.cep() != null) {
                end.setCEP(e.cep());
            } else {
                TCEnderExtSimples ext = new TCEnderExtSimples();
                ext.setCEndPost(e.codigoPostalExterior());
                ext.setXCidade(e.cidadeExterior());
                ext.setXEstProvReg(e.regiaoExterior());
                end.setEndExt(ext);
            }
            end.setXLgr(e.logradouro());
            end.setNro(e.numero());
            end.setXCpl(e.complemento());
            end.setXBairro(e.bairro());
            info.setEnd(end);
        }
        return info;
    }

    private static TCRTCListaDoc montarDocumento(DocumentoReembolso d) {
        TCRTCListaDoc doc = new TCRTCListaDoc();
        switch (d.documento()) {
            case DocumentoReembolso.DFeNacional dfe -> {
                TCRTCListaDocDFe alvo = new TCRTCListaDocDFe();
                alvo.setTipoChaveDFe(dfe.tipoChave());
                alvo.setXTipoChaveDFe(dfe.descricaoTipoChave());
                alvo.setChaveDFe(dfe.chave());
                doc.setDFeNacional(alvo);
            }
            case DocumentoReembolso.OutroDocumentoFiscal outro -> {
                TCRTCListaDocFiscalOutro alvo = new TCRTCListaDocFiscalOutro();
                alvo.setCMunDocFiscal(outro.codigoMunicipio());
                alvo.setNDocFiscal(outro.numero());
                alvo.setXDocFiscal(outro.descricao());
                doc.setDocFiscalOutro(alvo);
            }
            case DocumentoReembolso.DocumentoNaoFiscal naoFiscal -> {
                TCRTCListaDocOutro alvo = new TCRTCListaDocOutro();
                alvo.setNDoc(naoFiscal.numero());
                alvo.setXDoc(naoFiscal.descricao());
                doc.setDocOutro(alvo);
            }
        }
        if (d.fornecedor() != null) {
            TCRTCListaDocFornec fornec = new TCRTCListaDocFornec();
            fornec.setCNPJ(d.fornecedor().cnpj());
            fornec.setCPF(d.fornecedor().cpf());
            fornec.setNIF(d.fornecedor().nif());
            fornec.setXNome(d.fornecedor().nome());
            doc.setFornec(fornec);
        }
        doc.setDtEmiDoc(d.dataEmissao().toString());
        doc.setDtCompDoc(d.dataCompetencia().toString());
        doc.setTpReeRepRes(d.tipo());
        doc.setXTpReeRepRes(d.descricaoTipo());
        doc.setVlrReeRepRes(d.valor().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
        return doc;
    }

    private void validar() {
        if (cIndOp == null || !cIndOp.matches("[0-9]{6}")) {
            throw new IllegalStateException(
                    "informe o código indicador da operação com 6 dígitos, conforme o ANEXO_C "
                            + "(codigoIndicadorOperacao); recebido: " + cIndOp);
        }
        // E0901: o código precisa constar na tabela do ANEXO_C.
        if (!TabelaIndicadoresOperacao.existe(cIndOp)) {
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
        if (reembolsos.size() > 1000) {
            throw new IllegalStateException(
                    "no máximo 1000 documentos de reembolso; informados: " + reembolsos.size());
        }
        // E0953: o reembolso é parte do que foi cobrado, então não pode ultrapassar o serviço.
        if (valorServicoParaConferencia != null) {
            for (DocumentoReembolso d : reembolsos) {
                if (d.valor().compareTo(valorServicoParaConferencia) > 0) {
                    throw new IllegalStateException(
                            "o valor de reembolso " + d.valor() + " supera o valor do serviço "
                                    + valorServicoParaConferencia + " (E0953)");
                }
            }
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
