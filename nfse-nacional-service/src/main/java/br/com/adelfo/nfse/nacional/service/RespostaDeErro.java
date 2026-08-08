package br.com.adelfo.nfse.nacional.service;

import br.com.adelfo.nfse.nacional.client.NfseHttpException;
import br.com.adelfo.nfse.nacional.service.exception.NfseException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Traduz o corpo de erro da Sefin Nacional em {@link NfseException}.
 *
 * <p>O envelope, confirmado contra a produção restrita (SefinNacional_1.6.0), é:
 * <pre>
 * {
 *   "tipoAmbiente": 2,
 *   "versaoAplicativo": "SefinNacional_1.6.0",
 *   "dataHoraProcessamento": "2026-08-07T21:58:58-03:00",
 *   "erro": { "codigo": "E2401", "descricao": "Chave de acesso não encontrada." }
 * }
 * </pre>
 *
 * <p>O código vem no formato {@code E####} — não é numérico como o {@code cStat} da NF-e.
 *
 * <p><b>As rotas não concordam na grafia dos campos.</b> As consultas usam {@code erro} singular
 * com chaves minúsculas; a <b>emissão</b> devolve {@code erros[]} com as chaves em PascalCase e
 * ainda o {@code idDPS} recusado:
 * <pre>
 * {
 *   "tipoAmbiente": 2, "versaoAplicativo": "SefinNacional_1.6.0",
 *   "idDPS": "DPS3550308…900",
 *   "erros": [ { "Codigo": "E0120", "Descricao": "IM do prestador não deve ser informado…" } ]
 * }
 * </pre>
 * Por isso os campos são procurados <b>sem diferenciar maiúsculas</b>. Quando isso não era feito,
 * uma rejeição de negócio chegava ao chamador como código {@code "400"} e a mensagem vinha como o
 * JSON inteiro — indistinguível de uma falha de transporte.
 *
 * <p>Nem toda resposta segue esse formato: rota inexistente devolve a página HTML de erro do
 * ASP.NET em que a Sefin roda. Nesses casos o status HTTP vira o código, para não inventar
 * significado sobre um corpo que não é da API.
 */
final class RespostaDeErro {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RespostaDeErro() {
    }

    static NfseException traduzir(NfseHttpException e) {
        String codigo = null;
        String descricao = null;
        try {
            JsonNode raiz = MAPPER.readTree(e.getCorpo());
            JsonNode erro = primeiroErro(raiz);
            if (erro != null) {
                codigo = texto(erro, "codigo");
                descricao = coalesce(descricaoDeTodos(raiz), descricaoDe(erro));
            }
        } catch (Exception corpoNaoEhJson) {
            // Resposta HTML do servidor de aplicação: cai no retorno genérico abaixo.
        }

        return new NfseException(
                coalesce(codigo, String.valueOf(e.getStatus())),
                coalesce(descricao, mensagemDeInfraestrutura(e)),
                e.getCorpo());
    }

    /**
     * Mensagem para respostas que não vêm da API e sim da infraestrutura à frente dela. Sem isto o
     * chamador recebe uma página HTML inteira como "mensagem de rejeição", o que esconde a causa —
     * especialmente no 429, que não é erro de conteúdo e sim de ritmo, e se resolve espaçando as
     * chamadas em vez de mexer no documento.
     */
    private static String mensagemDeInfraestrutura(NfseHttpException e) {
        return switch (e.getStatus()) {
            case 429 -> "Limite de requisições excedido no Sistema Nacional NFS-e. "
                    + "Espace as chamadas e tente de novo.";
            case 502, 503, 504 -> "Serviço do Sistema Nacional NFS-e indisponível (HTTP "
                    + e.getStatus() + "). Não é rejeição do documento.";
            default -> e.getCorpo();
        };
    }

    /**
     * O nó que carrega código e descrição. A Sefin usa {@code erro} singular; a forma plural
     * {@code erros[]} aparece em respostas de validação com múltiplos apontamentos.
     */
    private static JsonNode primeiroErro(JsonNode raiz) {
        if (raiz == null || !raiz.isObject()) {
            return null;
        }
        JsonNode erro = raiz.get("erro");
        if (erro != null && erro.isObject()) {
            return erro;
        }
        JsonNode erros = raiz.get("erros");
        if (erros != null && erros.isArray() && !erros.isEmpty()) {
            return erros.get(0);
        }
        // Alguns endpoints devolvem código e descrição no próprio corpo, sem envelope.
        return texto(raiz, "codigo") != null ? raiz : null;
    }

    /**
     * Todas as rejeições do lote, numeradas, quando a Sefin aponta mais de uma.
     *
     * <p>A validação da DPS não para no primeiro problema. Reportar só o inicial faria o chamador
     * corrigir um campo, reenviar e receber o seguinte — um de cada vez.
     *
     * @return {@code null} quando há zero ou uma rejeição, caso em que a descrição simples serve
     */
    private static String descricaoDeTodos(JsonNode raiz) {
        JsonNode erros = raiz.get("erros");
        if (erros == null || !erros.isArray() || erros.size() < 2) {
            return null;
        }
        StringBuilder texto = new StringBuilder(erros.size() + " rejeições:");
        for (int i = 0; i < erros.size(); i++) {
            JsonNode erro = erros.get(i);
            texto.append(" (").append(i + 1).append(") [")
                    .append(coalesce(texto(erro, "codigo"), "?")).append("] ")
                    .append(coalesce(descricaoDe(erro), "sem descrição"));
        }
        return texto.toString();
    }

    private static String descricaoDe(JsonNode erro) {
        return coalesce(texto(erro, "descricao"), texto(erro, "mensagem"));
    }

    /**
     * Valor do campo, <b>ignorando maiúsculas e minúsculas</b> no nome.
     *
     * <p>A grafia varia por rota: {@code codigo} nas consultas, {@code Codigo} na emissão. Buscar
     * pelo nome exato faz a rejeição de negócio se perder e virar o status HTTP.
     */
    private static String texto(JsonNode no, String campo) {
        JsonNode valor = no.get(campo);
        if (valor == null) {
            var nomes = no.fieldNames();
            while (nomes.hasNext()) {
                String nome = nomes.next();
                if (nome.equalsIgnoreCase(campo)) {
                    valor = no.get(nome);
                    break;
                }
            }
        }
        return valor == null || valor.isNull() ? null : valor.asText();
    }

    private static String coalesce(String preferido, String alternativo) {
        return preferido != null && !preferido.isBlank() ? preferido : alternativo;
    }
}
