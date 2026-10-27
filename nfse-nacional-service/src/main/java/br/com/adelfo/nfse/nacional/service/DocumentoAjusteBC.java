package br.com.adelfo.nfse.nacional.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

/**
 * Documento que ajusta a base de cálculo — o {@code docAjusteBC} de
 * {@code infDPS/valores/vAjusteBC/documentos}.
 *
 * <p>A NT 009/2026 fundiu aqui dois grupos que eram separados: a dedução/redução da BC do ISSQN
 * ({@code vDedRed/documentos}) e o reembolso, repasse ou ressarcimento do IBS/CBS
 * ({@code IBSCBS/valores/gReeRepRes}). O que distingue um do outro agora é só o {@code tipo}:
 *
 * <ul>
 *   <li><b>ajuste exclusivo do ISSQN</b> — {@code 2} materiais, {@code 5} repasse consorciado,
 *       {@code 6} repasse de plano de saúde, {@code 7} serviços, {@code 8} subempreitada de mão de
 *       obra, {@code 9} profissional parceiro, {@code 99} outras deduções;</li>
 *   <li><b>reembolso, repasse ou ressarcimento</b>, que ajusta ISSQN e IBS/CBS — {@code 101}
 *       corretagem de imóveis, {@code 102} fornecedor intermediado por agência de turismo,
 *       {@code 103} produção externa em publicidade, {@code 104} mídia em publicidade, {@code 105}
 *       glosa de serviços de saúde, {@code 199} outros.</li>
 * </ul>
 *
 * <p>Os códigos antigos de reembolso ({@code 01}–{@code 04} e {@code 99}) viraram
 * {@code 101}–{@code 104} e {@code 199}; enviar a numeração antiga é recusa no schema.
 *
 * <p>Regras do ANEXO_I de produção restrita (20/10/2026) conferidas aqui: {@code E0464},
 * {@code E0465}, {@code E0469}, {@code E0470}, {@code E0472}, {@code E0476}, {@code E0479}, além de
 * {@code E0460} (tamanho da chave) e {@code E0492}/{@code E0498} (endereço do fornecedor). As que
 * dependem do resto da DPS — {@code E0462} e {@code E0467} — são conferidas pelo
 * {@link DpsBuilder}, que é onde o valor do serviço e o ajuste por percentual existem.
 *
 * @param tipo                tipo do ajuste ({@code tpAjusteBC}) — ver a lista acima
 * @param descricaoTipo       descrição, obrigatória nos tipos {@code 99} e {@code 199} e proibida
 *                            nos demais ({@code E0464}, {@code E0465})
 * @param valorTotalDocumento valor total do documento ({@code vTotDoc})
 * @param valorAplicado       parte do documento usada no ajuste ({@code vAjusteAplic}); não pode
 *                            superar o total do documento ({@code E0469})
 * @param dataEmissao         data de emissão do documento; opcional
 * @param dataCompetencia     data de competência do documento; opcional, salvo em
 *                            {@link OutroDocumentoFiscal}
 * @param documento           identificação do documento — exatamente uma das três formas
 * @param fornecedor          fornecedor a que o valor se refere; opcional
 */
public record DocumentoAjusteBC(String tipo,
                                String descricaoTipo,
                                BigDecimal valorTotalDocumento,
                                BigDecimal valorAplicado,
                                LocalDate dataEmissao,
                                LocalDate dataCompetencia,
                                Identificacao documento,
                                Fornecedor fornecedor) {

    /** Tipos que só ajustam a BC do ISSQN. {@code E0462} os barra quando já há ajuste por percentual ou valor. */
    static final Set<String> TIPOS_EXCLUSIVOS_ISSQN = Set.of("2", "5", "6", "7", "8", "9", "99");

    /** Tipos de reembolso, repasse ou ressarcimento, que ajustam ISSQN e IBS/CBS. */
    static final Set<String> TIPOS_REEMBOLSO = Set.of("101", "102", "103", "104", "105", "199");

    /** {@code E0479}: estes tipos exigem documento fiscal — documento não fiscal é recusado. */
    private static final Set<String> TIPOS_QUE_EXIGEM_DOCUMENTO_FISCAL = Set.of("101", "103", "104", "199");

    private static final Set<String> TIPOS_COM_DESCRICAO = Set.of("99", "199");

    /** {@code E0476}: "outro documento fiscal" é resquício do regime anterior à reforma. */
    private static final LocalDate LIMITE_DOC_FISCAL_OUTRO = LocalDate.of(2025, 12, 31);

    public DocumentoAjusteBC {
        if (tipo == null || !(TIPOS_EXCLUSIVOS_ISSQN.contains(tipo) || TIPOS_REEMBOLSO.contains(tipo))) {
            throw new IllegalArgumentException("tipo de ajuste deve ser 2, 5, 6, 7, 8, 9, 99, "
                    + "101, 102, 103, 104, 105 ou 199 (TSRTCTpAjusteBC); recebido: " + tipo
                    + ". Os códigos de reembolso 01–04 e 99 do leiaute anterior viraram 101–104 e 199");
        }
        boolean temDescricao = descricaoTipo != null && !descricaoTipo.isBlank();
        if (TIPOS_COM_DESCRICAO.contains(tipo) && !temDescricao) {
            throw new IllegalArgumentException("com tipo " + tipo + " a descrição é obrigatória (E0465)");
        }
        if (!TIPOS_COM_DESCRICAO.contains(tipo) && descricaoTipo != null) {
            throw new IllegalArgumentException(
                    "a descrição do tipo só é aceita nos tipos 99 e 199 (E0464); tipo: " + tipo);
        }
        if (valorTotalDocumento == null || valorTotalDocumento.signum() < 0) {
            throw new IllegalArgumentException("o valor total do documento é obrigatório e não pode ser negativo");
        }
        if (valorAplicado == null || valorAplicado.signum() < 0) {
            throw new IllegalArgumentException("o valor aplicado é obrigatório e não pode ser negativo");
        }
        if (valorAplicado.compareTo(valorTotalDocumento) > 0) {
            throw new IllegalArgumentException("o valor aplicado " + valorAplicado
                    + " supera o total do documento " + valorTotalDocumento + " (E0469)");
        }
        // E0472 / E0474 — as duas regras dizem a mesma coisa, de lados opostos.
        if (dataEmissao != null && dataCompetencia != null && dataEmissao.isBefore(dataCompetencia)) {
            throw new IllegalArgumentException(
                    "a data de emissão deve ser igual ou posterior à de competência (E0472): "
                            + dataEmissao + " < " + dataCompetencia);
        }
        if (documento == null) {
            throw new IllegalArgumentException("identifique o documento");
        }
        if (documento instanceof OutroDocumentoFiscal
                && (dataCompetencia == null || dataCompetencia.isAfter(LIMITE_DOC_FISCAL_OUTRO))) {
            throw new IllegalArgumentException("outro documento fiscal exige competência até "
                    + "31/12/2025 (E0476); recebida: " + dataCompetencia);
        }
        if (documento instanceof DocumentoNaoFiscal && TIPOS_QUE_EXIGEM_DOCUMENTO_FISCAL.contains(tipo)) {
            throw new IllegalArgumentException(
                    "o tipo " + tipo + " exige documento fiscal, não documento não fiscal (E0479)");
        }
    }

    /** O tipo ajusta só o ISSQN, e não o IBS/CBS. */
    public boolean exclusivoDoIssqn() {
        return TIPOS_EXCLUSIVOS_ISSQN.contains(tipo);
    }

    /** Forma de identificar o documento; exatamente uma das três. */
    public sealed interface Identificacao
            permits DFeNacional, OutroDocumentoFiscal, DocumentoNaoFiscal {
    }

    /**
     * Documento fiscal eletrônico do repositório nacional.
     *
     * <p>{@code E0460} valida o tamanho da chave conforme o tipo: NFS-e tem 50 posições, NF-e e
     * CT-e têm 44. A chave não é reduzida a dígitos: com o CNPJ alfanumérico, as posições do CNPJ
     * podem trazer letras.
     *
     * @param tipoChave          {@code 1} NFS-e, {@code 2} NF-e, {@code 3} CT-e, {@code 9} outro
     * @param descricaoTipoChave descrição, obrigatória quando o tipo é {@code 9} ({@code E0470})
     * @param chave              chave de acesso do documento
     */
    public record DFeNacional(String tipoChave, String descricaoTipoChave, String chave)
            implements Identificacao {

        public DFeNacional {
            if (tipoChave == null || !tipoChave.matches("1|2|3|9")) {
                throw new IllegalArgumentException("tipoChave deve ser 1, 2, 3 ou 9; recebido: " + tipoChave);
            }
            if ("9".equals(tipoChave) && (descricaoTipoChave == null || descricaoTipoChave.isBlank())) {
                throw new IllegalArgumentException("com tipoChave 9 a descrição é obrigatória (E0470)");
            }
            chave = chave == null ? null : chave.replaceAll("[^0-9A-Za-z]", "").toUpperCase();
            if (chave == null || chave.isEmpty()) {
                throw new IllegalArgumentException("a chave do DF-e é obrigatória");
            }
            int esperado = switch (tipoChave) {
                case "1" -> 50;
                case "2", "3" -> 44;
                default -> -1;
            };
            if (esperado > 0 && chave.length() != esperado) {
                throw new IllegalArgumentException(
                        "chave de tipo " + tipoChave + " deve ter " + esperado
                                + " posições (E0460); recebido: " + chave.length());
            }
        }

        public static DFeNacional nfse(String chave) {
            return new DFeNacional("1", null, chave);
        }

        public static DFeNacional nfe(String chave) {
            return new DFeNacional("2", null, chave);
        }

        public static DFeNacional cte(String chave) {
            return new DFeNacional("3", null, chave);
        }
    }

    /**
     * Documento fiscal fora do repositório nacional — só admitido para competências até
     * 31/12/2025 ({@code E0476}).
     *
     * @param codigoMunicipio código IBGE de 7 dígitos do município emissor ({@code E0466})
     */
    public record OutroDocumentoFiscal(String codigoMunicipio, String numero, String descricao)
            implements Identificacao {

        public OutroDocumentoFiscal {
            codigoMunicipio = Documentos.municipio(codigoMunicipio, "OutroDocumentoFiscal.codigoMunicipio");
            if (codigoMunicipio == null) {
                throw new IllegalArgumentException("o código do município é obrigatório");
            }
        }
    }

    /** Documento não fiscal — recibo, contrato, o que a operação comportar. */
    public record DocumentoNaoFiscal(String numero, String descricao) implements Identificacao {
    }

    /**
     * Fornecedor a que o valor se refere. Informe uma única inscrição.
     *
     * <p>Mudou com a NT 009: o fornecedor passou a ser um {@code TCInfoPessoa} completo, e o
     * endereço virou obrigatório — nacional para CNPJ ou CPF ({@code E0492}), no exterior para
     * NIF ({@code E0498}).
     */
    public record Fornecedor(String cnpj, String cpf, String nif, String nome, DpsBuilder.Endereco endereco) {

        public Fornecedor {
            cnpj = Documentos.cnpj(cnpj, "Fornecedor.cnpj");
            cpf = Documentos.cpf(cpf, "Fornecedor.cpf");
            long informados = java.util.stream.Stream.of(cnpj, cpf, nif)
                    .filter(v -> v != null && !v.isBlank()).count();
            if (informados != 1) {
                throw new IllegalArgumentException(
                        "informe exatamente um entre CNPJ, CPF e NIF do fornecedor");
            }
            if (endereco == null) {
                throw new IllegalArgumentException("o endereço do fornecedor é obrigatório (E0492/E0498)");
            }
            boolean exterior = endereco.codigoPais() != null;
            if (nif != null && !exterior) {
                throw new IllegalArgumentException(
                        "fornecedor identificado por NIF exige endereço no exterior (E0498)");
            }
            if (nif == null && exterior) {
                throw new IllegalArgumentException(
                        "fornecedor identificado por CNPJ ou CPF exige endereço nacional (E0492)");
            }
        }

        public static Fornecedor porCnpj(String cnpj, String nome, DpsBuilder.Endereco endereco) {
            return new Fornecedor(cnpj, null, null, nome, endereco);
        }

        public static Fornecedor porCpf(String cpf, String nome, DpsBuilder.Endereco endereco) {
            return new Fornecedor(null, cpf, null, nome, endereco);
        }

        public static Fornecedor porNif(String nif, String nome, DpsBuilder.Endereco endereco) {
            return new Fornecedor(null, null, nif, nome, endereco);
        }
    }
}
