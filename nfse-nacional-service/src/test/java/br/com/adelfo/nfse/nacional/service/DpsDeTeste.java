package br.com.adelfo.nfse.nacional.service;

import br.com.adelfo.nfse.nacional.client.TipoAmbiente;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;

/**
 * Monta a DPS usada pelos testes de integração e controla a numeração entre execuções.
 *
 * <p><b>Pré-requisito de negócio:</b> a Sefin só gera NFS-e se o município emissor for conveniado,
 * estiver ativo e permitir emissores públicos, e se o CNPJ do certificado estiver cadastrado e
 * autorizado nele. Informe o município com {@code -Dnfse.municipio=<código IBGE>} — o default
 * abaixo quase certamente não serve para o seu certificado, e a rejeição correspondente é o
 * primeiro erro esperado ao rodar estes testes pela primeira vez.
 */
final class DpsDeTeste {

    private DpsDeTeste() {
    }

    /** Código IBGE do município emissor e de prestação. */
    static String municipio() {
        return System.getProperty("nfse.municipio", "3550308");
    }

    /** Código de tributação nacional (LC 116/2003) — 6 dígitos: item + subitem + desdobro. */
    static String codigoServico() {
        return System.getProperty("nfse.servico", "010101");
    }

    static String serie() {
        return System.getProperty("nfse.serie", "1");
    }

    /**
     * Arquivo de sequência ao lado do módulo ({@code nfse-nacional-service/nfse-test-sequence.txt}).
     * Guarda o próximo nDPS; cada leitura incrementa e regrava, garantindo que execuções sucessivas
     * usem números distintos — DPS duplicada é rejeitada pela Sefin.
     *
     * <p>Faixa padrão: 900–999, reiniciando em 900.
     */
    private static final Path ARQUIVO_SEQUENCIA = Paths.get("nfse-test-sequence.txt").toAbsolutePath();

    static String proximoNumeroDps() throws Exception {
        int atual = Files.exists(ARQUIVO_SEQUENCIA)
                ? Integer.parseInt(Files.readString(ARQUIVO_SEQUENCIA).trim())
                : 900;
        int proximo = (atual >= 999) ? 900 : atual + 1;
        Files.writeString(ARQUIVO_SEQUENCIA, proximo + System.lineSeparator());
        System.out.println("[sequencia] nDPS desta execução: " + atual + " (próximo: " + proximo + ")");
        return String.valueOf(atual);
    }

    /**
     * DPS de serviço simples, sem tomador — o mínimo que o leiaute aceita, para que qualquer
     * rejeição venha de regra de negócio e não de campo faltando.
     */
    static DpsBuilder simples(TipoAmbiente ambiente, String cnpjPrestador, String numeroDps) {
        return DpsBuilder.novo()
                .ambiente(ambiente)
                .municipioEmissor(municipio())
                .identificacao(serie(), numeroDps, LocalDate.now())
                .emitidaPeloPrestador()
                .prestadorCnpj(cnpjPrestador)
                .naoOptanteSimplesNacional()
                .semRegimeEspecial()
                .servicoPrestadoNoMunicipio(municipio())
                .servico(codigoServico(), "Servico de teste de integracao - ambiente de homologacao")
                .valorServico(new BigDecimal("1.00"))
                .issqnTributavel()
                .issqnNaoRetido()
                .semTotalTributos();
    }
}
