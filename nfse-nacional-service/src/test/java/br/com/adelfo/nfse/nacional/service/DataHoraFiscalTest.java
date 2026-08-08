package br.com.adelfo.nfse.nacional.service;

import br.com.adelfo.nfse.nacional.client.TipoAmbiente;
import br.com.adelfo.nfse.nacional.service.dto.request.ManifestacaoRequest;
import br.com.adelfo.nfse.nacional.service.dto.request.TipoManifestacao;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * O fuso do servidor não pode vazar para o documento fiscal.
 *
 * <p>Estes testes existem por causa de uma rejeição real: o build passava na máquina de
 * desenvolvimento (São Paulo, {@code -03:00}) e falhava no servidor de produção (UTC), porque o
 * padrão {@code XXX} emite {@code Z} quando o offset é zero e o {@code TSDateTimeUTC} só admite
 * offset numérico. Vinte e cinco testes quebraram de uma vez, todos com
 * {@code cvc-pattern-valid ... 'TSDateTimeUTC'}.
 *
 * <p>Sem uma verificação que force o fuso, o defeito volta assim que alguém trocar o formatter.
 */
class DataHoraFiscalTest {

    private static final TimeZone ORIGINAL = TimeZone.getDefault();
    private static final String CHAVE = "1".repeat(49) + "9";

    @AfterEach
    void restaurarFuso() {
        TimeZone.setDefault(ORIGINAL);
    }

    private static void comFusoDaJvm(String zona, Runnable acao) {
        TimeZone.setDefault(TimeZone.getTimeZone(zona));
        acao.run();
    }

    private static String dpsEmitidaAgora() {
        return DpsBuilder.novo()
                .ambiente(TipoAmbiente.PRODUCAO_RESTRITA)
                .municipioEmissor("3550308")
                .identificacao("1", "1", LocalDate.of(2026, 3, 15))
                .emitidaPeloPrestador()
                .prestadorCnpj("06169966000133")
                .naoOptanteSimplesNacional()
                .semRegimeEspecial()
                .servicoPrestadoNoMunicipio("3550308")
                .servico("010401", "Elaboracao de programa de computador")
                .valorServico(new java.math.BigDecimal("100.00"))
                .issqnTributavel()
                .issqnNaoRetido()
                .aliquota(new java.math.BigDecimal("2.00"))
                .semTotalTributos()
                .build();
    }

    @Test
    void carimboNuncaSaiComZ_qualquerQueSejaOFusoDaJvm() {
        for (String zona : List.of("UTC", "America/Sao_Paulo", "Asia/Tokyo", "America/Los_Angeles")) {
            comFusoDaJvm(zona, () -> {
                String carimbo = DataHoraFiscal.agoraFormatado();

                assertFalse(carimbo.endsWith("Z"),
                        "TSDateTimeUTC não admite 'Z'; JVM em " + zona + " produziu " + carimbo);
                assertTrue(carimbo.endsWith("-03:00"),
                        "o carimbo é sempre o horário de Brasília; JVM em " + zona
                                + " produziu " + carimbo);
                assertFalse(carimbo.contains("."), "o pattern não admite fração de segundos");
            });
        }
    }

    @Test
    void dpsGeradaComJvmEmUtc_continuaValidaNoSchema() throws Exception {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        // Descontado o defeito conhecido do TSSerieDPS, que nenhuma série numérica satisfaz
        // (ver DpsBuilderTest.DEFEITO_TSSERIEDPS) e que nada tem a ver com fuso.
        List<String> erros = ValidacaoXsd.erros("DPS_v1.01.xsd", dpsEmitidaAgora()).stream()
                .filter(msg -> !msg.contains("TSSerieDPS") && !msg.contains("'serie'"))
                .toList();

        assertEquals(List.of(), erros,
                "foi exatamente assim que o build quebrou no servidor de produção");
    }

    @Test
    void eventoGeradoComJvmEmUtc_continuaValidoNoSchema() throws Exception {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        String xml = EventoBuilder.manifestacao(ManifestacaoRequest.confirmacao(
                TipoAmbiente.PRODUCAO_RESTRITA, CHAVE, TipoManifestacao.CONFIRMACAO_TOMADOR),
                "06169966000133");

        assertEquals(List.of(), ValidacaoXsd.erros("pedRegEvento_v1.01.xsd", xml));
    }

    @Test
    void dhEmiInformadoPeloChamador_eConvertidoParaBrasilia() {
        // Mesmo instante, fusos diferentes na entrada: o XML tem de sair idêntico.
        OffsetDateTime emUtc = OffsetDateTime.of(2026, 3, 15, 18, 30, 0, 0, ZoneOffset.UTC);
        OffsetDateTime emToquio = emUtc.withOffsetSameInstant(ZoneOffset.ofHours(9));

        assertEquals("2026-03-15T15:30:00-03:00", DataHoraFiscal.formatar(emUtc));
        assertEquals(DataHoraFiscal.formatar(emUtc), DataHoraFiscal.formatar(emToquio));
    }

    @Test
    void dataCorrente_naoViraODiaSeguinteNumaJvmEmUtc() {
        // 21h em Brasília já é o dia seguinte em UTC. A competência da DPS é uma data sem fuso:
        // se seguisse o relógio do servidor, cairia no mês errado na virada do mês.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        assertEquals(LocalDate.now(DataHoraFiscal.FUSO_BRASILIA), DataHoraFiscal.hoje());
    }
}
