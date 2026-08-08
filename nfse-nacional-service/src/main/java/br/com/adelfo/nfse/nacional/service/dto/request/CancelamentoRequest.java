package br.com.adelfo.nfse.nacional.service.dto.request;

import br.com.adelfo.nfse.nacional.client.TipoAmbiente;
import br.com.adelfo.nfse.nacional.service.Documentos;

/**
 * Cancelamento de NFS-e — evento {@code e101101}.
 *
 * <p>A biblioteca monta o XML do pedido de registro de evento a partir destes campos, assina e
 * envia para {@code POST /nfse/{chaveAcesso}/eventos}.
 *
 * <p><b>O autor do evento vem do certificado.</b> A regra {@code E0812} do AnexoII exige que o
 * {@code CNPJAutor} corresponda ao titular do certificado que assina o pedido — informá-lo à mão
 * só abre espaço para divergir. Use {@link #de}; a fachada preenche o autor a partir do
 * certificado configurado.
 *
 * <p>{@link #comAutor} existe para o caso legítimo em que o autor é <b>outro estabelecimento do
 * mesmo grupo</b>: a regra compara apenas o CNPJ base, então uma filial pode figurar como autora
 * de um evento assinado pela matriz.
 *
 * @param ambiente        ambiente alvo
 * @param chaveAcesso     chave de acesso da NFS-e a cancelar (50 dígitos)
 * @param codigoMotivo    código do motivo: {@code 1} erro na emissão, {@code 2} serviço não
 *                        prestado, {@code 9} outros ({@code TSCodJustCanc})
 * @param descricaoMotivo descrição do motivo. <b>Obrigatória, de 15 a 255 caracteres</b> — o
 *                        leiaute do {@code e101101} marca {@code xMotivo} como 1-1 e o tipo
 *                        {@code TSMotivo} impõe o comprimento mínimo
 * @param autor           inscrição federal do autor, ou {@code null} para usar a do certificado
 */
public record CancelamentoRequest(TipoAmbiente ambiente,
                                  String chaveAcesso,
                                  String codigoMotivo,
                                  String descricaoMotivo,
                                  String autor) {

    public CancelamentoRequest {
        if (ambiente == null) throw new IllegalArgumentException("ambiente é obrigatório");
        if (chaveAcesso == null || chaveAcesso.isBlank())
            throw new IllegalArgumentException("chaveAcesso é obrigatória");
        chaveAcesso = Documentos.chaveAcesso(chaveAcesso, "chaveAcesso");
        autor = Documentos.cpfOuCnpj(autor, "autor");
        if (codigoMotivo == null || codigoMotivo.isBlank())
            throw new IllegalArgumentException("codigoMotivo é obrigatório");
        // TSMotivo: 15 a 255 caracteres, e o leiaute marca xMotivo como obrigatório no e101101.
        // Validar aqui troca uma rejeição de schema opaca por uma mensagem que diz o que falta.
        if (descricaoMotivo == null || descricaoMotivo.strip().length() < 15
                || descricaoMotivo.strip().length() > 255) {
            throw new IllegalArgumentException(
                    "descricaoMotivo é obrigatória e deve ter de 15 a 255 caracteres; recebido: "
                            + (descricaoMotivo == null ? "null" : descricaoMotivo.strip().length() + " caracteres"));
        }
    }

    /** Cancelamento com o autor tirado do certificado — o caso normal. */
    public static CancelamentoRequest de(TipoAmbiente ambiente, String chaveAcesso,
                                         String codigoMotivo, String descricaoMotivo) {
        return new CancelamentoRequest(ambiente, chaveAcesso, codigoMotivo, descricaoMotivo, null);
    }

    /**
     * Cancelamento com autor explícito. Só use quando o autor for outro estabelecimento do mesmo
     * grupo do certificado; do contrário a Sefin rejeita por {@code E0812}.
     */
    public static CancelamentoRequest comAutor(TipoAmbiente ambiente, String chaveAcesso,
                                               String codigoMotivo, String descricaoMotivo, String autor) {
        return new CancelamentoRequest(ambiente, chaveAcesso, codigoMotivo, descricaoMotivo, autor);
    }

    /** {@code true} quando o autor é pessoa jurídica — decide entre CNPJAutor e CPFAutor. */
    public boolean autorEhPessoaJuridica() {
        return autor != null && autor.length() == 14;
    }
}
