package br.com.adelfo.nfse.nacional.service;

/**
 * Bem imóvel a que a operação se refere, dentro do grupo IBS/CBS da DPS.
 *
 * <p>O leiaute exige <b>ou</b> o código CIB <b>ou</b> o endereço — nunca os dois. A inscrição
 * imobiliária fiscal é opcional e acompanha qualquer das duas formas.
 *
 * <p>{@code E0931} torna este grupo obrigatório quando o {@code cTribNac} pertence a subitens de
 * serviços sobre bens imóveis (07.02.01, 07.02.02, 07.04.01, 07.05.01, 07.05.02, 07.06.01,
 * 07.06.02, 07.07…). A lista completa está no ANEXO_I; a biblioteca não a verifica, porque
 * depende de tabela que muda por versão.
 *
 * @param inscricaoImobiliariaFiscal inscrição no cadastro imobiliário do município, até 30
 *                                   caracteres; opcional
 * @param codigoCib                  Cadastro Imobiliário Brasileiro — <b>8 caracteres</b>: 7 do
 *                                   código mais o dígito verificador ({@code E0933})
 * @param endereco                   endereço do imóvel, quando não há CIB
 */
public record ImovelIbsCbs(String inscricaoImobiliariaFiscal,
                           String codigoCib,
                           EnderecoImovel endereco) {

    public ImovelIbsCbs {
        boolean temCib = codigoCib != null && !codigoCib.isBlank();
        boolean temEndereco = endereco != null;
        if (temCib == temEndereco) {
            throw new IllegalArgumentException(
                    "informe exatamente um entre código CIB e endereço do imóvel");
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
    }

    /** Imóvel identificado pelo CIB. */
    public static ImovelIbsCbs porCib(String codigoCib) {
        return new ImovelIbsCbs(null, codigoCib, null);
    }

    /** Imóvel identificado pelo CIB, com a inscrição imobiliária do município. */
    public static ImovelIbsCbs porCib(String codigoCib, String inscricaoImobiliariaFiscal) {
        return new ImovelIbsCbs(inscricaoImobiliariaFiscal, codigoCib, null);
    }

    /** Imóvel identificado pelo endereço. */
    public static ImovelIbsCbs porEndereco(EnderecoImovel endereco) {
        return new ImovelIbsCbs(null, null, endereco);
    }

    /**
     * Endereço do imóvel. Nacional leva CEP; no exterior, código postal, cidade e região —
     * {@code E0934} exige a forma estrangeira quando o local da prestação é um país.
     *
     * @param cep                   CEP, em endereço nacional
     * @param codigoPostalExterior  código postal, em endereço no exterior
     * @param cidadeExterior        cidade, em endereço no exterior
     * @param regiaoExterior        estado, província ou região, em endereço no exterior
     */
    public record EnderecoImovel(String cep,
                                 String codigoPostalExterior,
                                 String cidadeExterior,
                                 String regiaoExterior,
                                 String logradouro,
                                 String numero,
                                 String complemento,
                                 String bairro) {

        public EnderecoImovel {
            cep = Documentos.cep(cep, "EnderecoImovel.cep");
            boolean nacional = cep != null && !cep.isBlank();
            boolean exterior = codigoPostalExterior != null && !codigoPostalExterior.isBlank();
            if (nacional == exterior) {
                throw new IllegalArgumentException(
                        "informe exatamente um entre CEP (nacional) e código postal (exterior)");
            }
            if (exterior && (cidadeExterior == null || regiaoExterior == null)) {
                throw new IllegalArgumentException(
                        "endereço no exterior exige cidade e região além do código postal");
            }
            if (logradouro == null || numero == null || bairro == null) {
                throw new IllegalArgumentException("logradouro, número e bairro são obrigatórios");
            }
        }

        public static EnderecoImovel nacional(String cep, String logradouro, String numero,
                                              String complemento, String bairro) {
            return new EnderecoImovel(cep, null, null, null, logradouro, numero, complemento, bairro);
        }

        public static EnderecoImovel exterior(String codigoPostal, String cidade, String regiao,
                                              String logradouro, String numero, String complemento,
                                              String bairro) {
            return new EnderecoImovel(null, codigoPostal, cidade, regiao,
                    logradouro, numero, complemento, bairro);
        }
    }
}
