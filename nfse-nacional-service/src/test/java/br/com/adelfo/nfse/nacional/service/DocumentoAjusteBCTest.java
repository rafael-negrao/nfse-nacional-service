package br.com.adelfo.nfse.nacional.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static br.com.adelfo.nfse.nacional.service.IbsCbsBuilderTest.dps;
import static br.com.adelfo.nfse.nacional.service.IbsCbsBuilderTest.validar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ajuste de base de cálculo ({@code valores/vAjusteBC}), que a NT 009/2026 criou fundindo a
 * dedução do ISSQN e o reembolso do IBS/CBS. Regras do ANEXO_I de produção restrita (20/10/2026).
 */
class DocumentoAjusteBCTest {

    private static final String MUNICIPIO_SP = "3550308";

    private static final DpsBuilder.Endereco ENDERECO_FORNECEDOR = DpsBuilder.Endereco.nacional(
            MUNICIPIO_SP, "01310100", "Avenida Paulista", "1000", null, "Bela Vista");

    private static DocumentoAjusteBC reembolso(BigDecimal valor) {
        return new DocumentoAjusteBC("101", null, valor, valor,
                LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 1),
                DocumentoAjusteBC.DFeNacional.nfe("1".repeat(44)),
                DocumentoAjusteBC.Fornecedor.porCnpj("98765432000198", "Fornecedor Ltda", ENDERECO_FORNECEDOR));
    }

    private static DocumentoAjusteBC naoFiscal(String tipo, String descricao) {
        return new DocumentoAjusteBC(tipo, descricao, BigDecimal.TEN, BigDecimal.TEN, null, null,
                new DocumentoAjusteBC.DocumentoNaoFiscal("1", "x"), null);
    }

    @Test
    void reembolso_eValidoNoSchema() throws Exception {
        String xml = dps().documentoAjusteBC(reembolso(new BigDecimal("250.00"))).build();

        validar(xml);
        assertTrue(xml.contains("<vAjusteBC><documentos><docAjusteBC><tpAjusteBC>101</tpAjusteBC>"), xml);
        assertTrue(xml.contains("<vTotDoc>250.00</vTotDoc><vAjusteAplic>250.00</vAjusteAplic>"));
        assertTrue(xml.contains("<tipoChaveDFe>2</tipoChaveDFe>"));
        assertTrue(xml.contains("<fornec><CNPJ>98765432000198</CNPJ><xNome>Fornecedor Ltda</xNome><end>"), xml);
        assertFalse(xml.contains("gReeRepRes"), "o grupo antigo de reembolso deixou de existir");
    }

    @Test
    void deducaoPercentualDoIssqn_eValidaNoSchema() throws Exception {
        String xml = dps().deducaoPercentual(new BigDecimal("10")).build();

        validar(xml);
        assertTrue(xml.contains("<vAjusteBC><pAjusteBCISSQN>10.00</pAjusteBCISSQN></vAjusteBC>"), xml);
        assertFalse(xml.contains("vDedRed"));
    }

    @Test
    void deducaoComReembolso_convivemNoMesmoGrupo() throws Exception {
        // E0462 só barra os tipos exclusivos do ISSQN; reembolso acompanha o ajuste por valor.
        String xml = dps().deducaoValor(new BigDecimal("50"))
                .documentoAjusteBC(reembolso(new BigDecimal("100.00"))).build();

        validar(xml);
        assertTrue(xml.contains("<vAjusteBCISSQN>50.00</vAjusteBCISSQN><documentos>"), xml);
    }

    @Test
    void documentoExclusivoDoIssqn_naoAcompanhaAjustePorPercentual() {
        // E0462
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> dps().deducaoPercentual(BigDecimal.TEN).documentoAjusteBC(naoFiscal("2", null)).build());
        assertTrue(e.getMessage().contains("E0462"), e.getMessage());
    }

    @Test
    void percentualDeAjuste_vaiAte100() {
        // E0453
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> dps().deducaoPercentual(new BigDecimal("100.01")).build());
        assertTrue(e.getMessage().contains("E0453"), e.getMessage());
    }

    @Test
    void ajusteAcimaDoValorDoServico_eRecusado() {
        // E0467: o valor do serviço vive no DpsBuilder, então a conferência é na montagem da DPS.
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> dps().documentoAjusteBC(reembolso(new BigDecimal("1000.01"))).build());
        assertTrue(e.getMessage().contains("E0467"), e.getMessage());
    }

    @Test
    void valorAplicado_naoSuperaOTotalDoDocumento() {
        // E0469
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new DocumentoAjusteBC("7", null, BigDecimal.ONE, BigDecimal.TEN, null, null,
                        new DocumentoAjusteBC.DocumentoNaoFiscal("1", "x"), null));
        assertTrue(e.getMessage().contains("E0469"), e.getMessage());
    }

    @Test
    void codigosAntigosDeReembolso_saoRecusadosComOrientacao() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> naoFiscal("01", null));
        assertTrue(e.getMessage().contains("101–104 e 199"), e.getMessage());
    }

    @Test
    void descricaoDoTipo_soNos_99e199() {
        // E0464 / E0465
        assertThrows(IllegalArgumentException.class, () -> naoFiscal("7", "não cabe"));
        assertThrows(IllegalArgumentException.class, () -> naoFiscal("99", null));
        assertThrows(IllegalArgumentException.class, () -> naoFiscal("199", null));
    }

    @Test
    void tiposDeReembolsoComDocumentoFiscal_recusamDocumentoNaoFiscal() {
        // E0479: 101, 103, 104 e 199 exigem documento fiscal; 102 e 105 não.
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> naoFiscal("101", null));
        assertTrue(e.getMessage().contains("E0479"), e.getMessage());
        naoFiscal("102", null);
    }

    @Test
    void emissaoAnteriorACompetencia_eRecusada() {
        // E0472 / E0474
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new DocumentoAjusteBC("7", null, BigDecimal.TEN, BigDecimal.TEN,
                        LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 20),
                        new DocumentoAjusteBC.DocumentoNaoFiscal("1", "x"), null));
        assertTrue(e.getMessage().contains("E0472"), e.getMessage());
    }

    @Test
    void outroDocumentoFiscal_exigeCompetenciaAte2025() {
        // E0476: é resquício do regime anterior à reforma.
        DocumentoAjusteBC.OutroDocumentoFiscal nota =
                new DocumentoAjusteBC.OutroDocumentoFiscal(MUNICIPIO_SP, "1", "Nota antiga");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new DocumentoAjusteBC("7", null, BigDecimal.TEN, BigDecimal.TEN,
                        LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 1), nota, null));
        assertTrue(e.getMessage().contains("E0476"), e.getMessage());

        new DocumentoAjusteBC("7", null, BigDecimal.TEN, BigDecimal.TEN,
                LocalDate.of(2025, 12, 31), LocalDate.of(2025, 12, 31), nota, null);
    }

    @Test
    void chaveDFe_temTamanhoConformeOTipo() {
        // E0460: NFS-e tem 50 posições; NF-e e CT-e têm 44.
        assertThrows(IllegalArgumentException.class,
                () -> DocumentoAjusteBC.DFeNacional.nfe("1".repeat(49) + "9"));
        assertThrows(IllegalArgumentException.class,
                () -> DocumentoAjusteBC.DFeNacional.nfse("1".repeat(44)));
        assertEquals(50, DocumentoAjusteBC.DFeNacional.nfse("1".repeat(49) + "9").chave().length());
    }

    @Test
    void outroTipoDeDFe_exigeDescricao() {
        // E0470
        assertThrows(IllegalArgumentException.class,
                () -> new DocumentoAjusteBC.DFeNacional("9", null, "123"));
    }

    @Test
    void fornecedor_exigeEnderecoConformeAInscricao() {
        // E0492: CNPJ/CPF com endereço nacional. E0498: NIF com endereço no exterior.
        assertThrows(IllegalArgumentException.class,
                () -> DocumentoAjusteBC.Fornecedor.porCnpj("98765432000198", "F", null));
        assertThrows(IllegalArgumentException.class,
                () -> DocumentoAjusteBC.Fornecedor.porNif("ABC123", "F", ENDERECO_FORNECEDOR));
        DocumentoAjusteBC.Fornecedor.porNif("ABC123", "F", DpsBuilder.Endereco.exterior(
                "US", "10001", "New York", "NY", "5th Avenue", "100", null, "Manhattan"));
    }
}
