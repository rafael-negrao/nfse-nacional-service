package br.com.adelfo.nfse.nacional.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Bens imóveis a que a operação se refere, dentro do grupo IBS/CBS da DPS.
 *
 * <p><b>A NT 009/2026 reestruturou o grupo.</b> Antes era um imóvel só, identificado por CIB ou
 * endereço (nacional ou no exterior). Agora é o município, mais um grupo opcional de locação e
 * até 99 unidades imobiliárias — cada uma com o seu CIB ou endereço, e este só nacional.
 *
 * <p>Regras do ANEXO_I de produção restrita (20/10/2026):
 * <ul>
 *   <li>{@code E0929} — o município precisa existir na tabela IBGE;</li>
 *   <li>{@code E0936}/{@code E0937} — {@code gLocacao} existe <b>se e somente se</b> o
 *       {@code cTribNac} é do subitem 99.03; idem {@code E0939} para os ajustes de BC por unidade.
 *       A biblioteca não confere, porque o código de serviço vive em outro builder;</li>
 *   <li>{@code E0938} — as unidades são obrigatórias quando o {@code cIndOp} é 020101, 020201 ou
 *       020301;</li>
 *   <li>{@code E0940} — percentual de copropriedade entre 0 e 100%.</li>
 * </ul>
 *
 * @param codigoMunicipio código IBGE do município dos imóveis
 * @param locacao         dados da locação, cessão onerosa ou arrendamento; opcional
 * @param unidades        unidades imobiliárias objeto da operação; até 99
 */
public record ImovelIbsCbs(String codigoMunicipio, Locacao locacao, List<UnidadeImobiliaria> unidades) {

    public ImovelIbsCbs {
        codigoMunicipio = Documentos.municipio(codigoMunicipio, "ImovelIbsCbs.codigoMunicipio");
        if (codigoMunicipio == null) {
            throw new IllegalArgumentException("o código do município dos imóveis é obrigatório (E0929)");
        }
        unidades = unidades == null ? List.of() : List.copyOf(unidades);
        if (unidades.size() > 99) {
            throw new IllegalArgumentException("no máximo 99 unidades imobiliárias; informadas: " + unidades.size());
        }
        if (locacao != null) {
            for (UnidadeImobiliaria u : unidades) {
                for (AjusteLocacao a : u.ajustes()) {
                    if (a.valor().compareTo(locacao.valorTotalOperacao()) > 0) {
                        throw new IllegalArgumentException("o ajuste " + a.valor()
                                + " supera o valor total da operação " + locacao.valorTotalOperacao()
                                + " (E0966)");
                    }
                }
            }
        }
    }

    /** Imóveis do município, sem locação — o caso comum fora do subitem 99.03. */
    public static ImovelIbsCbs de(String codigoMunicipio, UnidadeImobiliaria... unidades) {
        return new ImovelIbsCbs(codigoMunicipio, null, List.of(unidades));
    }

    /** Locação, cessão onerosa ou arrendamento — subitem 99.03. */
    public static ImovelIbsCbs locacao(String codigoMunicipio, Locacao locacao, UnidadeImobiliaria... unidades) {
        return new ImovelIbsCbs(codigoMunicipio, locacao, List.of(unidades));
    }

    /**
     * Grupo {@code gLocacao}.
     *
     * @param percentualCopropriedade 100 quando há um só locador ({@code E0940})
     * @param valorTotalOperacao      aluguel, renda ou equivalente (art. 255 da LC 214/2025)
     * @param descontoIncondicionado  opcional
     * @param descontoCondicionado    opcional
     * @param vencimentoOriginal      opcional
     */
    public record Locacao(BigDecimal percentualCopropriedade,
                          BigDecimal valorTotalOperacao,
                          BigDecimal descontoIncondicionado,
                          BigDecimal descontoCondicionado,
                          LocalDate vencimentoOriginal) {

        public Locacao {
            if (percentualCopropriedade == null || percentualCopropriedade.signum() < 0
                    || percentualCopropriedade.compareTo(new BigDecimal("100")) > 0) {
                throw new IllegalArgumentException(
                        "o percentual de copropriedade vai de 0 a 100 (E0940); recebido: " + percentualCopropriedade);
            }
            if (valorTotalOperacao == null || valorTotalOperacao.signum() < 0) {
                throw new IllegalArgumentException("o valor total da operação é obrigatório e não pode ser negativo");
            }
        }

        /** Locador único: copropriedade de 100%. */
        public static Locacao integral(BigDecimal valorTotalOperacao) {
            return new Locacao(new BigDecimal("100"), valorTotalOperacao, null, null, null);
        }
    }

    /**
     * Unidade imobiliária ({@code gUnidImob}): exatamente um entre CIB e endereço.
     *
     * @param inscricaoImobiliariaFiscal inscrição no cadastro imobiliário do município, até 30
     *                                   caracteres; opcional
     * @param codigoCib                  Cadastro Imobiliário Brasileiro — <b>8 caracteres</b>: 7 do
     *                                   código mais o DV ({@code E0933})
     * @param endereco                   endereço, quando não há CIB
     * @param ajustes                    parcelas fora da BC do IBS/CBS na locação; até 1000
     */
    public record UnidadeImobiliaria(String inscricaoImobiliariaFiscal,
                                     String codigoCib,
                                     EnderecoImovel endereco,
                                     List<AjusteLocacao> ajustes) {

        public UnidadeImobiliaria {
            boolean temCib = codigoCib != null && !codigoCib.isBlank();
            if (temCib == (endereco != null)) {
                throw new IllegalArgumentException(
                        "informe exatamente um entre código CIB e endereço da unidade imobiliária");
            }
            if (temCib && codigoCib.length() != 8) {
                throw new IllegalArgumentException(
                        "o código CIB tem 8 caracteres — 7 do código mais o DV (E0933); recebido: "
                                + codigoCib.length());
            }
            if (inscricaoImobiliariaFiscal != null && inscricaoImobiliariaFiscal.length() > 30) {
                throw new IllegalArgumentException(
                        "a inscrição imobiliária fiscal tem no máximo 30 caracteres");
            }
            ajustes = ajustes == null ? List.of() : List.copyOf(ajustes);
            if (ajustes.size() > 1000) {
                throw new IllegalArgumentException("no máximo 1000 ajustes por unidade; informados: " + ajustes.size());
            }
        }

        public static UnidadeImobiliaria porCib(String codigoCib) {
            return new UnidadeImobiliaria(null, codigoCib, null, List.of());
        }

        public static UnidadeImobiliaria porCib(String codigoCib, String inscricaoImobiliariaFiscal) {
            return new UnidadeImobiliaria(inscricaoImobiliariaFiscal, codigoCib, null, List.of());
        }

        public static UnidadeImobiliaria porEndereco(EnderecoImovel endereco) {
            return new UnidadeImobiliaria(null, null, endereco, List.of());
        }

        /** A mesma unidade com ajustes de BC da locação — só no subitem 99.03 ({@code E0939}). */
        public UnidadeImobiliaria comAjustes(AjusteLocacao... ajustes) {
            return new UnidadeImobiliaria(inscricaoImobiliariaFiscal, codigoCib, endereco, List.of(ajustes));
        }
    }

    /**
     * Endereço da unidade imobiliária. Só nacional: a NT 009 removeu a forma no exterior.
     * Bairro e complemento são opcionais.
     */
    public record EnderecoImovel(String cep, String logradouro, String numero,
                                 String complemento, String bairro) {

        public EnderecoImovel {
            cep = Documentos.cep(cep, "EnderecoImovel.cep");
            if (cep == null || logradouro == null || numero == null) {
                throw new IllegalArgumentException("CEP, logradouro e número são obrigatórios");
            }
        }
    }

    /**
     * Parcela não integrante da BC do IBS/CBS na locação ({@code gAjusteBCLocImoveis}).
     *
     * @param tipo      {@code 01} tributos inclusos no aluguel (IPTU…), {@code 02} emolumentos,
     *                  {@code 03} condomínio, {@code 04} redutor social — só para locação
     *                  residencial ({@code E0944}) —, {@code 99} outras parcelas
     * @param descricao obrigatória no tipo {@code 99} e proibida nos demais ({@code E0949},
     *                  {@code E0954})
     * @param valor     não pode superar o valor total da operação ({@code E0966})
     */
    public record AjusteLocacao(String tipo, String descricao, BigDecimal valor) {

        public AjusteLocacao {
            if (tipo == null || !tipo.matches("01|02|03|04|99")) {
                throw new IllegalArgumentException("tipo deve ser 01, 02, 03, 04 ou 99; recebido: " + tipo);
            }
            boolean temDescricao = descricao != null && !descricao.isBlank();
            if ("99".equals(tipo) != temDescricao) {
                throw new IllegalArgumentException(
                        "a descrição é obrigatória no tipo 99 e proibida nos demais (E0949/E0954)");
            }
            if (valor == null || valor.signum() < 0) {
                throw new IllegalArgumentException("o valor do ajuste é obrigatório e não pode ser negativo");
            }
        }
    }
}
