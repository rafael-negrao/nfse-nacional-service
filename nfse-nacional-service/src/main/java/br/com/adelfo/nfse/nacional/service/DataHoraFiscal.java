package br.com.adelfo.nfse.nacional.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Carimbos de data e hora no formato que o {@code TSDateTimeUTC} dos XSDs oficiais aceita.
 *
 * <p>Existe para que a biblioteca produza o mesmo documento independentemente do fuso do servidor.
 * Duas armadilhas justificam cada decisão daqui, e ambas só aparecem fora de uma máquina brasileira:
 *
 * <ul>
 *   <li><b>O padrão {@code XXX} emite {@code Z} quando o offset é zero.</b> O pattern do
 *       {@code TSDateTimeUTC} exige offset numérico ({@code -03:00}, {@code +00:00}) e não
 *       comporta o {@code Z}. Num servidor em UTC, todo XML era rejeitado no schema com
 *       {@code cvc-pattern-valid ... 'TSDateTimeUTC'}. O padrão {@code xxx} sempre emite a forma
 *       numérica;
 *   <li><b>o relógio do servidor não é o relógio do fato gerador.</b> Numa VM em UTC, tudo o que
 *       acontece depois das 21h no Brasil cai no dia seguinte — e a competência da DPS, que é uma
 *       data sem fuso, iria para o mês errado na virada do mês. Por isso o fuso é fixado em
 *       {@code America/Sao_Paulo}, o horário oficial de Brasília a que o fisco se refere, em vez
 *       do fuso padrão da JVM.
 * </ul>
 *
 * <p>Também não se usa {@code ISO_OFFSET_DATE_TIME}: ele emite fração de segundos, que o pattern
 * do XSD igualmente não admite.
 */
public final class DataHoraFiscal {

    /** Horário oficial de Brasília — a referência do fisco, independentemente de onde a JVM roda. */
    public static final ZoneId FUSO_BRASILIA = ZoneId.of("America/Sao_Paulo");

    /** {@code AAAA-MM-DDThh:mm:ssTZD}, sem fração de segundos e sempre com offset numérico. */
    public static final DateTimeFormatter FORMATO =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxxx");

    private DataHoraFiscal() {
    }

    /** Instante corrente no horário de Brasília. */
    public static OffsetDateTime agora() {
        return OffsetDateTime.now(FUSO_BRASILIA);
    }

    /** Data corrente no horário de Brasília — use no lugar de {@code LocalDate.now()}. */
    public static LocalDate hoje() {
        return LocalDate.now(FUSO_BRASILIA);
    }

    /**
     * Formata para o {@code TSDateTimeUTC}, convertendo para o horário de Brasília antes.
     *
     * <p>A conversão preserva o instante e torna o texto previsível: o offset sai sempre
     * {@code -03:00}, mesmo que o chamador tenha montado o {@link OffsetDateTime} em outro fuso.
     */
    public static String formatar(OffsetDateTime instante) {
        return instante.atZoneSameInstant(FUSO_BRASILIA).format(FORMATO);
    }

    /** Carimbo do instante corrente, pronto para ir ao XML. */
    public static String agoraFormatado() {
        return agora().format(FORMATO);
    }
}
