package br.com.adelfo.nfse.nacional.service;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Documento de reembolso, repasse ou ressarcimento — o grupo {@code gReeRepRes} do IBS/CBS.
 *
 * <p>Registra valores que passaram pelo prestador mas se referem a operações de terceiros, já
 * tributadas: repasse a corretores, a fornecedores intermediados por agência de turismo,
 * ressarcimento de produção externa ou mídia em agência de publicidade, e outros.
 *
 * <p>Cada documento identifica-se de uma das três formas — DF-e do repositório nacional, outro
 * documento fiscal, ou documento não fiscal — e leva o fornecedor, as datas e o valor.
 *
 * @param tipo             tipo do valor: {@code 01} repasse a corretores de imóveis; {@code 02}
 *                         repasse a fornecedor intermediado por agência de turismo; {@code 03}
 *                         reembolso de produção externa em publicidade; {@code 04} reembolso de
 *                         mídia em publicidade; {@code 99} outros
 * @param descricaoTipo    descrição do tipo. {@code E0952}: <b>só</b> quando o tipo é {@code 99}
 * @param valor            valor do reembolso. {@code E0953}: não pode superar o valor do serviço
 * @param dataEmissao      data de emissão do documento; {@code E0950} exige que seja igual ou
 *                         posterior à competência
 * @param dataCompetencia  data de competência do documento
 * @param documento        identificação do documento — ver as factories
 * @param fornecedor       fornecedor a que o valor se refere; opcional
 */
public record DocumentoReembolso(String tipo,
                                 String descricaoTipo,
                                 BigDecimal valor,
                                 LocalDate dataEmissao,
                                 LocalDate dataCompetencia,
                                 Identificacao documento,
                                 Fornecedor fornecedor) {

    private static final LocalDate LIMITE_DOC_FISCAL_OUTRO = LocalDate.of(2025, 12, 31);

    public DocumentoReembolso {
        if (tipo == null || !tipo.matches("01|02|03|04|99")) {
            throw new IllegalArgumentException(
                    "tipo deve ser 01, 02, 03, 04 ou 99; recebido: " + tipo);
        }
        // E0952: a descrição livre só existe para "Outros".
        if ("99".equals(tipo)) {
            if (descricaoTipo == null || descricaoTipo.isBlank()) {
                throw new IllegalArgumentException("com tipo 99 a descrição é obrigatória");
            }
        } else if (descricaoTipo != null) {
            throw new IllegalArgumentException(
                    "a descrição do tipo só é aceita quando o tipo é 99 (E0952)");
        }
        if (valor == null || valor.signum() < 0) {
            throw new IllegalArgumentException("valor é obrigatório e não pode ser negativo");
        }
        if (dataEmissao == null || dataCompetencia == null) {
            throw new IllegalArgumentException("datas de emissão e competência são obrigatórias");
        }
        // E0950 / E0951 — as duas regras dizem a mesma coisa, de lados opostos.
        if (dataEmissao.isBefore(dataCompetencia)) {
            throw new IllegalArgumentException(
                    "a data de emissão deve ser igual ou posterior à de competência (E0950): "
                            + dataEmissao + " < " + dataCompetencia);
        }
        if (documento == null) {
            throw new IllegalArgumentException("identifique o documento");
        }
        // E0942: "outro documento fiscal" é resquício do regime anterior à reforma.
        if (documento instanceof OutroDocumentoFiscal
                && dataCompetencia.isAfter(LIMITE_DOC_FISCAL_OUTRO)) {
            throw new IllegalArgumentException(
                    "outro documento fiscal exige competência anterior a 31/12/2025 (E0942); recebida: "
                            + dataCompetencia);
        }
    }

    /** Forma de identificar o documento; exatamente uma das três. */
    public sealed interface Identificacao
            permits DFeNacional, OutroDocumentoFiscal, DocumentoNaoFiscal {
    }

    /**
     * Documento fiscal eletrônico do repositório nacional.
     *
     * <p>{@code E0940} valida o tamanho da chave conforme o tipo: NFS-e tem 50 dígitos, NF-e e
     * CT-e têm 44.
     *
     * @param tipoChave  {@code 1} NFS-e, {@code 2} NF-e, {@code 3} CT-e, {@code 9} outro
     * @param descricaoTipoChave descrição, quando o tipo é {@code 9}
     * @param chave      chave de acesso do documento
     */
    public record DFeNacional(String tipoChave, String descricaoTipoChave, String chave)
            implements Identificacao {

        public DFeNacional {
            if (tipoChave == null || !tipoChave.matches("1|2|3|9")) {
                throw new IllegalArgumentException("tipoChave deve ser 1, 2, 3 ou 9; recebido: " + tipoChave);
            }
            chave = Documentos.apenasDigitos(chave);
            if (chave == null) {
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
                                + " dígitos (E0940); recebido: " + chave.length());
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
     * Documento fiscal fora do repositório nacional — só admitido para competências anteriores a
     * 31/12/2025 ({@code E0942}).
     *
     * @param codigoMunicipio código IBGE de 7 dígitos do município do documento
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

    /** Fornecedor a que o valor reembolsado se refere. Informe uma única inscrição. */
    public record Fornecedor(String cnpj, String cpf, String nif, String nome) {

        public Fornecedor {
            cnpj = Documentos.cnpj(cnpj, "Fornecedor.cnpj");
            cpf = Documentos.cpf(cpf, "Fornecedor.cpf");
            long informados = java.util.stream.Stream.of(cnpj, cpf, nif)
                    .filter(v -> v != null && !v.isBlank()).count();
            if (informados != 1) {
                throw new IllegalArgumentException(
                        "informe exatamente um entre CNPJ, CPF e NIF do fornecedor");
            }
        }

        public static Fornecedor porCnpj(String cnpj, String nome) {
            return new Fornecedor(cnpj, null, null, nome);
        }

        public static Fornecedor porCpf(String cpf, String nome) {
            return new Fornecedor(null, cpf, null, nome);
        }
    }
}
