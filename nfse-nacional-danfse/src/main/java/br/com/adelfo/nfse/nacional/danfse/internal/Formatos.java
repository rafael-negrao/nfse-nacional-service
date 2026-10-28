package br.com.adelfo.nfse.nacional.danfse.internal;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Máscaras de impressão da NT 008, item 2.4.5. O XML traz os valores crus; o DANFSe os mostra
 * como o leitor espera.
 */
public final class Formatos {

    /** Nota 12: campo sem informação no XML sai como traço. */
    public static final String VAZIO = "-";

    private Formatos() {
    }

    public static String ouTraco(String valor) {
        return valor == null || valor.isBlank() ? VAZIO : valor;
    }

    /** {@code nn.nnn.nnn/nnnn-nn}; aceita o CNPJ alfanumérico. */
    public static String cnpj(String v) {
        if (v == null || v.length() != 14) {
            return v;
        }
        return v.substring(0, 2) + "." + v.substring(2, 5) + "." + v.substring(5, 8) + "/"
                + v.substring(8, 12) + "-" + v.substring(12);
    }

    /** {@code nnn.nnn.nnn-nn}. */
    public static String cpf(String v) {
        if (v == null || v.length() != 11) {
            return v;
        }
        return v.substring(0, 3) + "." + v.substring(3, 6) + "." + v.substring(6, 9) + "-" + v.substring(9);
    }

    /**
     * {@code nn.nnn-nnn}. O {@code emit/enderNac/CEP} da NFS-e vem <b>sem o zero à esquerda</b> —
     * {@code 4273200} numa nota real de São Paulo —, então o valor é completado antes da máscara.
     */
    public static String cep(String v) {
        if (v == null || !v.matches("[0-9]{7,8}")) {
            return v;
        }
        String c = v.length() == 7 ? "0" + v : v;
        return c.substring(0, 2) + "." + c.substring(2, 5) + "-" + c.substring(5);
    }

    /** {@code (nn) nnnn-nnnn} ou {@code (nn) nnnnn-nnnn}; outros tamanhos saem como vieram. */
    public static String telefone(String v) {
        if (v == null || !v.matches("[0-9]{10,11}")) {
            return v;
        }
        int corte = v.length() - 4;
        return "(" + v.substring(0, 2) + ") " + v.substring(2, corte) + "-" + v.substring(corte);
    }

    /** {@code AAAA-MM-DD} → {@code DD/MM/AAAA}. */
    public static String data(String v) {
        if (v == null || v.length() < 10) {
            return v;
        }
        return v.substring(8, 10) + "/" + v.substring(5, 7) + "/" + v.substring(0, 4);
    }

    /**
     * {@code AAAA-MM-DDThh:mm:ss±hh:mm} → {@code DD/MM/AAAA hh:mm:ss}, no horário que o próprio XML
     * registra — sem converter fuso, para o DANFSe mostrar o mesmo que a nota.
     */
    public static String dataHora(String v) {
        if (v == null || v.length() < 19) {
            return v;
        }
        return data(v) + " " + v.substring(11, 19);
    }

    /** {@code 1234.5} → {@code R$ 1.234,50}. */
    public static String moeda(String v) {
        BigDecimal n = numero(v);
        return n == null ? VAZIO : "R$ " + decimal().format(n);
    }

    /** {@code 2} → {@code 2,00%}. */
    public static String percentual(String v) {
        BigDecimal n = numero(v);
        return n == null ? VAZIO : decimal().format(n) + "%";
    }

    public static BigDecimal numero(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(v.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String moeda(BigDecimal n) {
        return n == null ? VAZIO : "R$ " + decimal().format(n);
    }

    /** {@code 010401} → {@code 01.04.01}. */
    public static String codigoTributacaoNacional(String v) {
        if (v == null || v.length() != 6) {
            return v;
        }
        return v.substring(0, 2) + "." + v.substring(2, 4) + "." + v.substring(4);
    }

    /** {@code 123456789} → {@code 1.2345.67.89}. */
    public static String nbs(String v) {
        if (v == null || v.length() != 9) {
            return v;
        }
        return v.charAt(0) + "." + v.substring(1, 5) + "." + v.substring(5, 7) + "." + v.substring(7);
    }

    /**
     * Corta o texto em {@code maximo} caracteres, terminando em reticências — a regra da NT 008
     * ("Preencher com reticências (...), caso a descrição supere 37 caracteres" num campo de 40).
     */
    public static String limitar(String v, int maximo) {
        if (v == null || v.length() <= maximo) {
            return v;
        }
        return v.substring(0, maximo - 3) + "...";
    }

    /** Une partes não vazias com o separador; traço se todas vierem vazias. */
    public static String juntar(String separador, String... partes) {
        StringBuilder sb = new StringBuilder();
        for (String p : partes) {
            if (p != null && !p.isBlank()) {
                if (!sb.isEmpty()) {
                    sb.append(separador);
                }
                sb.append(p.trim());
            }
        }
        return sb.isEmpty() ? VAZIO : sb.toString();
    }

    private static DecimalFormat decimal() {
        // DecimalFormat não é thread-safe: uma instância por chamada.
        return new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.of("pt", "BR")));
    }
}
