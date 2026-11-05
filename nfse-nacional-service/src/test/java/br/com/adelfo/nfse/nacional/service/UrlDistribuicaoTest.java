package br.com.adelfo.nfse.nacional.service;

import br.com.adelfo.nfse.nacional.client.TipoAmbiente;
import br.com.adelfo.nfse.nacional.service.dto.request.ConsultaDFeRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * URL da distribuição por NSU. Sem {@code cnpjConsulta} o ADN de produção recusou a MCamas com
 * {@code InscricaoCertificado}/{@code REJEICAO} e nenhum erro (04/11/2026) — por isso o CNPJ do
 * certificado vai sempre que o chamador não informar outro.
 */
class UrlDistribuicaoTest {

    private static final String BASE = TipoAmbiente.PRODUCAO.getUrlAdn() + "/contribuintes/DFe/";

    @Test
    void semCnpjNoPedido_usaODoCertificado() {
        assertEquals(BASE + "0?lote=true&cnpjConsulta=37641093000143",
                NfseServiceImpl.urlDistribuicao(ConsultaDFeRequest.doInicio(TipoAmbiente.PRODUCAO), "37641093000143"));
    }

    @Test
    void cnpjDoPedido_prevaleceSobreODoCertificado() {
        // a matriz varrendo uma filial: mesma raiz, outro estabelecimento
        ConsultaDFeRequest filial = new ConsultaDFeRequest(TipoAmbiente.PRODUCAO, 42, "37641093000224", true);
        assertEquals(BASE + "42?lote=true&cnpjConsulta=37641093000224",
                NfseServiceImpl.urlDistribuicao(filial, "37641093000143"));
    }

    @Test
    void semLote_oCnpjAbreAQueryString() {
        ConsultaDFeRequest umPorVez = new ConsultaDFeRequest(TipoAmbiente.PRODUCAO, 7, null, false);
        assertEquals(BASE + "7?cnpjConsulta=37641093000143",
                NfseServiceImpl.urlDistribuicao(umPorVez, "37641093000143"));
    }

    @Test
    void certificadoDePessoaFisica_naoMandaOParametro() {
        // cnpjConsulta é só de CNPJ; para CPF fica o comportamento antigo
        assertEquals(BASE + "0?lote=true",
                NfseServiceImpl.urlDistribuicao(ConsultaDFeRequest.doInicio(TipoAmbiente.PRODUCAO), null));
    }
}
