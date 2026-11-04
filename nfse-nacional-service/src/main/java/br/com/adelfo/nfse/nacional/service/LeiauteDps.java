package br.com.adelfo.nfse.nacional.service;

import br.com.adelfo.nfse.nacional.client.TipoAmbiente;

import java.time.LocalDate;

/**
 * Leiaute em que a DPS é gerada — escolhido em {@link DpsBuilder#build(LeiauteDps)}.
 *
 * <p><b>Os dois pacotes se chamam {@code v1.01}</b>, e a DPS declara {@code versao="1.01"} nos dois:
 * o número não distingue um do outro. O que distingue é a posição de elementos, e a Sefin recusa o
 * leiaute errado com {@code E1235}.
 *
 * <p>Existe por causa de novembro de 2026: os optantes do Simples de São Paulo passam a emitir pelo
 * padrão nacional em 01/11, e produção só aceita o leiaute da NT 009 em 01/12. Até lá, produção
 * espera o de fevereiro. Depois de 01/12 {@link #FEVEREIRO_2026} deixa de ter uso e pode sair.
 */
public enum LeiauteDps {

    /**
     * Pacote {@code NFSe-ESQUEMAS_XSD-v1.01-20260209} (NT 004 v2.0) — o de produção até 30/11/2026.
     * Gerado por conversão da DPS da NT 009; o que não tem equivalente é recusado no
     * {@code build}. Ver {@code ConversorLeiauteFevereiro2026}.
     */
    FEVEREIRO_2026,

    /**
     * Pacote {@code NFSe-ESQUEMAS_XSD-PRODREST-v1.01-20261020} (NT 009/2026) — o compilado pelo
     * JAXB. Produção restrita desde ~21/10/2026; produção a partir de 01/12/2026.
     */
    NT009_OUTUBRO_2026;

    /**
     * Primeiro dia em que produção aceita a NT 009, segundo o comunicado do CGNFS-e. Já houve uma
     * data anunciada (03/11/2026) que não valeu — se mudar de novo, é só aqui.
     */
    public static final LocalDate INICIO_NT009_EM_PRODUCAO = LocalDate.of(2026, 12, 1);

    /**
     * Leiaute que o ambiente aceita na data de emissão. Produção restrita já está na NT 009; produção
     * troca em {@link #INICIO_NT009_EM_PRODUCAO}.
     */
    public static LeiauteDps vigente(TipoAmbiente ambiente, LocalDate data) {
        if (ambiente == null) throw new IllegalArgumentException("ambiente é obrigatório");
        if (data == null) throw new IllegalArgumentException("data é obrigatória");
        if (ambiente == TipoAmbiente.PRODUCAO && data.isBefore(INICIO_NT009_EM_PRODUCAO)) {
            return FEVEREIRO_2026;
        }
        return NT009_OUTUBRO_2026;
    }
}
