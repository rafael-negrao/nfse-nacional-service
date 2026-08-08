package br.com.adelfo.nfse.nacional.service;

/**
 * Normalização dos campos numéricos do leiaute.
 *
 * <p>O XSD tipa CNPJ, CPF, código de município, CEP, telefone e chave de acesso como sequências de
 * dígitos — {@code [0-9]{14}}, {@code [0-9]{7}} e assim por diante. Mas quem consome a biblioteca
 * costuma ter esses dados formatados, vindos de cadastro ou de tela: {@code 06.169.966/0001-33},
 * {@code 01310-100}, {@code (11) 3000-0000}.
 *
 * <p>Sem tratamento, o comportamento era inconsistente e perigoso: o CNPJ do prestador estourava
 * com uma mensagem sobre o tamanho do Id, enquanto o do tomador <b>passava em silêncio</b> e ia
 * pontuado no XML, para ser recusado só na Sefin. Aqui a pontuação é removida e o que sobra é
 * conferido, com mensagem que nomeia o campo.
 *
 * <p>É pública: quem consome a biblioteca costuma precisar da mesma normalização em outros pontos
 * do próprio sistema.
 *
 * <p>CPF, CNPJ e chave de acesso têm o <b>dígito verificador conferido</b>, e valores com todos os
 * dígitos iguais são recusados — o módulo 11 os aceita por construção.
 *
 * <p>A conferência do DV é o que as regras {@code E0911}, {@code E0913}, {@code E0042} e
 * {@code E0907} exigem, e checar aqui evita uma ida à Sefin para descobrir um erro de digitação.
 *
 * <p>Ficam de fora, de propósito, os campos que o XSD <b>não</b> restringe a dígitos: a inscrição
 * municipal ({@code TSInscMun}, texto de 1 a 15) e o NIF ({@code TSNIF}, 1 a 40) admitem letras, e
 * limpá-los corromperia o dado.
 */
public final class Documentos {

    private Documentos() {
    }

    /**
     * CNPJ com 14 dígitos, dígito verificador conferido.
     *
     * <p><b>Sobre o CNPJ alfanumérico:</b> a IN RFB 2.229/2024 tornou as posições 1 a 12
     * alfanuméricas a partir de julho de 2026. O leiaute da NFS-e <b>ainda não acompanhou</b> — o
     * XSD v1.01, de fevereiro de 2026, tipa {@code TSCNPJ} como {@code [0-9]{14}}, e não há nota
     * técnica sobre o assunto nas atualizações do portal. Enviar letras produziria XML recusado no
     * schema, então aqui elas são barradas com essa explicação.
     *
     * <p>O cálculo do DV em {@link #digitoVerificadorCnpj(String)} já é o alfanumérico, que é
     * compatível com o numérico: um dígito vale {@code ASCII − 48}, que é o próprio valor. Quando o
     * leiaute mudar, basta afrouxar a checagem de formato.
     */
    public static String cnpj(String valor, String campo) {
        String bruto = valor == null ? null : valor.trim();
        if (bruto != null && bruto.matches(".*[A-Za-z].*")) {
            throw new IllegalArgumentException(campo + ": o leiaute da NFS-e ainda exige CNPJ "
                    + "somente numérico (TSCNPJ = [0-9]{14}); o CNPJ alfanumérico da IN RFB "
                    + "2.229/2024 não é aceito pelo schema v1.01. Recebido \"" + valor + "\"");
        }
        String digitos = comTamanhoExato(valor, 14, campo, "CNPJ");
        if (digitos == null) {
            return null;
        }
        recusarRepetidos(digitos, campo, "CNPJ");
        conferirDv(digitos, digitoVerificadorCnpj(digitos.substring(0, 12)), campo, "CNPJ");
        return digitos;
    }

    /** CPF com 11 dígitos, dígito verificador conferido ({@code E0913}). */
    public static String cpf(String valor, String campo) {
        String digitos = comTamanhoExato(valor, 11, campo, "CPF");
        if (digitos == null) {
            return null;
        }
        recusarRepetidos(digitos, campo, "CPF");
        conferirDv(digitos, digitoVerificadorCpf(digitos.substring(0, 9)), campo, "CPF");
        return digitos;
    }

    /**
     * Os dois dígitos verificadores de um CPF, a partir das 9 primeiras posições. Módulo 11 com
     * pesos decrescentes; resto menor que 2 resulta em dígito 0.
     */
    public static String digitoVerificadorCpf(String base) {
        if (base == null || !base.matches("[0-9]{9}")) {
            throw new IllegalArgumentException("a base do CPF tem 9 dígitos; recebido: " + base);
        }
        int d1 = moduloOnze(base, 10);
        int d2 = moduloOnze(base + d1, 11);
        return "" + d1 + d2;
    }

    /**
     * Os dois dígitos verificadores de um CNPJ, a partir das 12 primeiras posições.
     *
     * <p>Implementa o cálculo do <b>CNPJ alfanumérico</b> (IN RFB 2.229/2024): cada caractere vale
     * {@code ASCII − 48}, de modo que {@code '0'}–{@code '9'} valem 0–9 e {@code 'A'} vale 17,
     * {@code 'B'} 18 e assim por diante. Para um CNPJ numérico o resultado é idêntico ao do
     * cálculo antigo, então a mesma rotina serve aos dois formatos.
     */
    public static String digitoVerificadorCnpj(String base) {
        if (base == null || !base.matches("[0-9A-Z]{12}")) {
            throw new IllegalArgumentException(
                    "a base do CNPJ tem 12 posições alfanuméricas maiúsculas; recebido: " + base);
        }
        int[] pesos = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        int d1 = moduloOnzeCnpj(base, pesos);
        int[] pesos13 = new int[13];
        pesos13[0] = 6;
        System.arraycopy(pesos, 0, pesos13, 1, 12);
        int d2 = moduloOnzeCnpj(base + d1, pesos13);
        return "" + d1 + d2;
    }

    private static int moduloOnze(String digitos, int pesoInicial) {
        int soma = 0;
        for (int i = 0; i < digitos.length(); i++) {
            soma += (digitos.charAt(i) - '0') * (pesoInicial - i);
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }

    private static int moduloOnzeCnpj(String base, int[] pesos) {
        int soma = 0;
        for (int i = 0; i < base.length(); i++) {
            soma += (base.charAt(i) - 48) * pesos[i];
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }

    /**
     * Recusa valores com todos os dígitos iguais.
     *
     * <p>O módulo 11 não os pega: {@code 00000000000} e {@code 00000000000000} têm DV {@code 00} por
     * construção, e a chave de acesso só de zeros também passa. São valores notoriamente inválidos,
     * usados como preenchimento por engano, e chegariam à Sefin como documento aparentemente bem
     * formado.
     */
    private static void recusarRepetidos(String digitos, String campo, String oQue) {
        if (digitos.chars().distinct().count() == 1) {
            throw new IllegalArgumentException(campo + ": " + oQue
                    + " com todos os dígitos iguais é inválido — recebido \"" + digitos + "\"");
        }
    }

    private static void conferirDv(String valor, String esperado, String campo, String oQue) {
        String informado = valor.substring(valor.length() - 2);
        if (!esperado.equals(informado)) {
            throw new IllegalArgumentException(campo + ": " + oQue + " com dígito verificador "
                    + "inválido — informado " + informado + ", calculado " + esperado
                    + " para \"" + valor + "\"");
        }
    }

    /** CPF (11) ou CNPJ (14) — usado onde o leiaute aceita os dois. */
    public static String cpfOuCnpj(String valor, String campo) {
        String digitos = apenasDigitos(valor);
        if (digitos == null) {
            return null;
        }
        if (digitos.length() != 11 && digitos.length() != 14) {
            throw new IllegalArgumentException(campo + " deve ser um CPF (11 dígitos) ou CNPJ (14); "
                    + "recebido \"" + valor + "\" (" + digitos.length() + " dígitos)");
        }
        return digitos.length() == 11 ? cpf(digitos, campo) : cnpj(digitos, campo);
    }

    /** Código IBGE de município, 7 dígitos. */
    public static String municipio(String valor, String campo) {
        return comTamanhoExato(valor, 7, campo, "código IBGE de município");
    }

    /** CEP, 8 dígitos. */
    public static String cep(String valor, String campo) {
        return comTamanhoExato(valor, 8, campo, "CEP");
    }

    /**
     * Chave de acesso de NFS-e: 50 dígitos, com o último conferido.
     *
     * <p>{@code E0042} e {@code E0907} exigem a verificação do DV nas chaves referenciadas — de
     * NFS-e substituída e de NFS-e vinculada ao IBS/CBS. Conferir em toda chave que entra pega o
     * erro de digitação antes de virar rejeição.
     */
    public static String chaveAcesso(String valor, String campo) {
        String digitos = comTamanhoExato(valor, 50, campo, "chave de acesso");
        if (digitos == null) {
            return null;
        }
        recusarRepetidos(digitos, campo, "chave de acesso");
        String esperado = String.valueOf(digitoVerificadorChaveAcesso(digitos.substring(0, 49)));
        String informado = digitos.substring(49);
        if (!esperado.equals(informado)) {
            throw new IllegalArgumentException(campo + ": chave de acesso com dígito verificador "
                    + "inválido — informado " + informado + ", calculado " + esperado
                    + " para \"" + digitos + "\"");
        }
        return digitos;
    }

    /**
     * Dígito verificador da chave de acesso, a partir das 49 primeiras posições.
     *
     * <p>Módulo 11 com pesos de 2 a 9 da direita para a esquerda; resto 0 ou 1 resulta em dígito 0.
     * É o mesmo algoritmo da chave da NF-e, e foi conferido contra uma NFS-e real de produção.
     */
    public static int digitoVerificadorChaveAcesso(String base) {
        if (base == null || !base.matches("[0-9]{49}")) {
            throw new IllegalArgumentException(
                    "a base da chave de acesso tem 49 dígitos; recebido: " + base);
        }
        int soma = 0;
        int peso = 2;
        for (int i = base.length() - 1; i >= 0; i--) {
            soma += (base.charAt(i) - '0') * peso;
            peso = peso == 9 ? 2 : peso + 1;
        }
        int resto = soma % 11;
        return resto <= 1 ? 0 : 11 - resto;
    }

    /** Telefone, de 6 a 20 dígitos ({@code TSTelefone}). */
    public static String telefone(String valor, String campo) {
        String digitos = apenasDigitos(valor);
        if (digitos == null) {
            return null;
        }
        if (digitos.length() < 6 || digitos.length() > 20) {
            throw new IllegalArgumentException(campo + " deve ter de 6 a 20 dígitos; recebido \""
                    + valor + "\" (" + digitos.length() + " dígitos)");
        }
        return digitos;
    }

    /**
     * Remove tudo que não for dígito. {@code null} e vazio passam adiante como {@code null}, para
     * que campos opcionais continuem opcionais.
     */
    public static String apenasDigitos(String valor) {
        if (valor == null) {
            return null;
        }
        String limpo = valor.replaceAll("\\D", "");
        return limpo.isEmpty() ? null : limpo;
    }

    private static String comTamanhoExato(String valor, int tamanho, String campo, String oQue) {
        String digitos = apenasDigitos(valor);
        if (digitos == null) {
            return null;
        }
        if (digitos.length() != tamanho) {
            throw new IllegalArgumentException(campo + ": " + oQue + " deve ter " + tamanho
                    + " dígitos; recebido \"" + valor + "\" (" + digitos.length() + " dígitos)");
        }
        return digitos;
    }
}
