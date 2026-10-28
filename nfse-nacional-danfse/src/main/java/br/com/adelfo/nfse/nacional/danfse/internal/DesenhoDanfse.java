package br.com.adelfo.nfse.nacional.danfse.internal;

import br.com.adelfo.nfse.nacional.danfse.DanfseException;
import br.com.adelfo.nfse.nacional.danfse.internal.ConteudoDanfse.Pessoa;
import br.com.adelfo.nfse.nacional.service.dto.response.SituacaoNfse;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.lowagie.text.Document;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfGState;
import com.lowagie.text.pdf.PdfWriter;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Desenha o DANFSe numa página A4, nas coordenadas da tabela do item 2.4.5 da NT 008 v1.02 e na
 * disposição do Anexo I. Todas as medidas estão em centímetros a partir do canto superior
 * esquerdo, como na NT; a conversão para o sistema do PDF (pontos, a partir do canto inferior)
 * fica em {@link #y(double)} e {@link #cm(double)}.
 *
 * <p><b>Fontes:</b> a NT pede Arial nos títulos e Microsoft Sans Serif no conteúdo, ambas
 * proprietárias. Usa-se a Helvetica das 14 fontes-padrão do PDF, que tem as mesmas métricas da
 * Arial e não precisa ser embutida.
 *
 * <p>Não é thread-safe: uma instância por documento.
 */
public final class DesenhoDanfse {

    private static final float PAGINA_ALTURA = PageSize.A4.getHeight();

    private static final double X0 = 0.30;
    private static final double LARGURA = 20.40;
    private static final double[] COLUNA = {0.30, 5.41, 10.51, 15.62};
    private static final double COL_ESTREITA = 5.09;
    private static final double COL_LARGA = 10.19;

    /** Altura mínima de um bloco reduzido à mensagem das Notas 2, 3 e 4. */
    private static final double BLOCO_REDUZIDO = 0.32;

    /** Alturas da tabela do item 2.4.5 — sugestões, não mínimos (item 2.1). */
    private static final double DESCRICAO_SERVICO_TABELA = 0.64;
    private static final double INFO_COMPLEMENTAR_TABELA = 5.42;

    /** Piso das Informações Complementares: título e duas linhas, uma delas a dos tributos. */
    private static final double INFO_COMPLEMENTAR_PISO = 1.10;

    private static final double ENTRELINHA = 0.30;

    /** Sombreamento de 5% exigido no item 2.2.3. */
    private static final Color CINZA_5 = new Color(242, 242, 242);
    /** Marca d'água: cinza K35 (item 2.5). */
    private static final Color CINZA_K35 = new Color(166, 166, 166);
    /** "NFS-e SEM VALIDADE JURÍDICA": vermelho M100/Y100. */
    private static final Color VERMELHO = new Color(237, 28, 36);

    private final BaseFont normal;
    private final BaseFont negrito;
    private PdfContentByte cb;

    public DesenhoDanfse() {
        try {
            normal = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
            negrito = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
        } catch (Exception e) {
            throw new DanfseException("fontes do PDF indisponíveis", e);
        }
    }

    public byte[] desenhar(ConteudoDanfse c, SituacaoNfse situacao, boolean comCanhoto, byte[] logoPng) {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 0, 0, 0, 0);
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, saida);
            doc.addTitle("DANFSe " + c.chaveAcesso());
            doc.addCreator("nfse-nacional-danfse");
            doc.open();
            cb = writer.getDirectContent();

            Alturas a = distribuirAlturas(c, comCanhoto);
            double yv = X0;
            yv = cabecalho(c, logoPng, yv);
            yv = identificacao(c, yv);
            yv = blocoPessoa("PRESTADOR / FORNECEDOR", c.prestador(), true, false, yv, c);
            yv = c.tomador() == null
                    ? reduzido("TOMADOR/ADQUIRENTE DA OPERAÇÃO NÃO IDENTIFICADO NA NFS-e", yv)
                    : blocoPessoa("TOMADOR / ADQUIRENTE", c.tomador(), false, false, yv, c);
            yv = destinatario(c, yv);
            yv = c.intermediario() == null
                    ? reduzido("INTERMEDIÁRIO DA OPERAÇÃO NÃO IDENTIFICADO NA NFS-e", yv)
                    : blocoPessoa("INTERMEDIÁRIO DA OPERAÇÃO", c.intermediario(), false, false, yv, c);
            yv = servico(c, a.descricaoServico, yv);
            yv = c.sujeitoAoIssqn()
                    ? tributacaoMunicipal(c, yv)
                    : reduzido("TRIBUTAÇÃO MUNICIPAL (ISSQN) - OPERAÇÃO NÃO SUJEITA AO ISSQN", yv);
            yv = tributacaoFederal(c, yv);
            yv = ibsCbs(c, yv);
            yv = valorTotal(c, yv);
            yv = informacoesComplementares(c, a.informacoesComplementares, yv);
            if (comCanhoto) {
                yv = canhoto(c, yv);
            }
            // Borda da página: 1 ponto (item 2.2.3).
            retangulo(X0, X0, LARGURA, yv - X0, null, 1f);
            marcaDagua(situacao);
            doc.close();
            return saida.toByteArray();
        } catch (DanfseException e) {
            throw e;
        } catch (Exception e) {
            throw new DanfseException("falha ao desenhar o DANFSe", e);
        }
    }

    // =====================================================================================
    // Distribuição vertical
    // =====================================================================================

    private record Alturas(double descricaoServico, double informacoesComplementares) {
    }

    /**
     * Divide entre a Descrição do Serviço e as Informações Complementares o espaço vertical que
     * sobra dos blocos fixos — inclusive o que os blocos reduzidos e o canhoto ausente liberam
     * (itens 2.3.1 a 2.3.3).
     *
     * <p>As Informações Complementares ficam com o que o conteúdo delas precisa; a descrição, com
     * o resto. Seguir as alturas da tabela da NT ao pé da letra cortava uma descrição de 1.300
     * caracteres — o máximo que a própria NT admite — em uma linha, deixando cinco centímetros em
     * branco nas Informações Complementares. A NT declara essas alturas como sugestão (item 2.1);
     * o obrigatório é o modelo do Anexo I e a página única, e ambos se mantêm.
     */
    private Alturas distribuirAlturas(ConteudoDanfse c, boolean comCanhoto) {
        double liberado = 0;
        if (c.tomador() == null) liberado += 1.94 - BLOCO_REDUZIDO;
        if (c.destinatario() == null) liberado += 1.94 - BLOCO_REDUZIDO;
        if (c.intermediario() == null) liberado += 1.94 - BLOCO_REDUZIDO;
        if (!c.sujeitoAoIssqn()) liberado += 2.59 - BLOCO_REDUZIDO;
        if (!c.imprimeLinhaPisCofins()) liberado += 0.65;
        if (!comCanhoto) liberado += 0.67;
        double flexivel = DESCRICAO_SERVICO_TABELA + INFO_COMPLEMENTAR_TABELA + liberado;

        double larguraTexto = cm(LARGURA - 0.2);
        int linhasInfo = linhas(c.informacoesComplementares(), normal, 7, larguraTexto).size()
                + linhas(c.totaisAproximadosTributos(), normal, 7, larguraTexto).size();
        double info = Math.max(INFO_COMPLEMENTAR_PISO, 0.45 + linhasInfo * ENTRELINHA);

        double precisaDescricao = 0.35 + linhas(c.descricaoServico(), normal, 7, larguraTexto).size() * ENTRELINHA;
        double descricao = Math.max(DESCRICAO_SERVICO_TABELA, Math.min(precisaDescricao, flexivel - info));
        return new Alturas(descricao, flexivel - descricao);
    }

    // =====================================================================================
    // Blocos
    // =====================================================================================

    private double cabecalho(ConteudoDanfse c, byte[] logoPng, double topo) throws Exception {
        retangulo(X0, topo, LARGURA, 1.16, CINZA_5, 0.5f);
        Image logo = Image.getInstance(logoPng);
        float largura = (float) cm(4.00);
        float altura = largura * logo.getHeight() / logo.getWidth();
        logo.scaleAbsolute(largura, altura);
        logo.setAbsolutePosition((float) cm(0.49), y(0.44) - altura);
        cb.addImage(logo);

        double centro = 5.41 + 10.19 / 2;
        texto("DANFSe v2.0", negrito, 9, centro, topo + 0.12, PdfContentByte.ALIGN_CENTER, null);
        texto("Documento Auxiliar da NFS-e", negrito, 9, centro, topo + 0.46, PdfContentByte.ALIGN_CENTER, null);
        if (c.homologacao()) {
            texto("NFS-e SEM VALIDADE JURÍDICA", negrito, 9, centro, topo + 0.80, PdfContentByte.ALIGN_CENTER, VERMELHO);
        }
        texto(caber(c.municipioEmissor(), normal, 8, 5.0), normal, 8, 15.70, topo + 0.12, PdfContentByte.ALIGN_LEFT, null);
        texto(c.ambienteGerador(), normal, 6, 15.70, 0.97, PdfContentByte.ALIGN_LEFT, null);
        texto(c.tipoAmbiente(), normal, 6, 15.70, 1.22, PdfContentByte.ALIGN_LEFT, null);
        return 1.48;
    }

    private double identificacao(ConteudoDanfse c, double topo) {
        linha(topo);
        campoDestaque("CHAVE DE ACESSO DA NFS-E", c.chaveAcesso(), X0, topo, 15.30, null);
        double[] linhas = {2.27, 2.96, 3.65};
        campoDestaque("NÚMERO DA NFS-E", c.numeroNfse(), COLUNA[0], linhas[0], COL_ESTREITA, null);
        campoDestaque("COMPETÊNCIA DA NFS-E", c.competencia(), COLUNA[1], linhas[0], COL_ESTREITA, null);
        campoDestaque("DATA E HORA DA EMISSÃO DA NFS-E", c.dataHoraEmissaoNfse(), COLUNA[2], linhas[0], COL_ESTREITA, null);
        campoDestaque("NÚMERO DA DPS", c.numeroDps(), COLUNA[0], linhas[1], COL_ESTREITA, null);
        campoDestaque("SÉRIE DA DPS", c.serieDps(), COLUNA[1], linhas[1], COL_ESTREITA, null);
        campoDestaque("DATA E HORA DA EMISSÃO DA DPS", c.dataHoraEmissaoDps(), COLUNA[2], linhas[1], COL_ESTREITA, null);
        // "Emitente da NFS-e" tem sombreamento próprio (item 2.2.3).
        campoDestaque("EMITENTE DA NFS-E", c.emitente(), COLUNA[0], linhas[2], COL_ESTREITA, CINZA_5);
        campoDestaque("SITUAÇÃO DA NFS-E", c.situacao(), COLUNA[1], linhas[2], COL_ESTREITA, null);
        campoDestaque("FINALIDADE", c.finalidade(), COLUNA[2], linhas[2], COL_ESTREITA, null);

        qrCode(c.urlConsultaPublica(), 17.48, 1.67, 1.52);
        String[] aviso = {"A autenticidade desta NFS-e pode ser verificada pela",
                "leitura deste código QR ou pela consulta da chave de",
                "acesso no portal nacional da NFS-e"};
        for (int i = 0; i < aviso.length; i++) {
            texto(aviso[i], normal, 6, 15.80 + 4.72 / 2, 3.36 + i * 0.23, PdfContentByte.ALIGN_CENTER, null);
        }
        return 4.34;
    }

    private double blocoPessoa(String titulo, Pessoa p, boolean prestador, boolean destinatario,
                               double topo, ConteudoDanfse c) {
        linha(topo);
        tituloBloco(titulo, topo);
        campo("CNPJ / CPF / NIF", p.documento(), COLUNA[1], topo, COL_ESTREITA);
        if (!destinatario) {
            campo("Indicador Municipal (Inscrição)", p.inscricaoMunicipal(), COLUNA[2], topo, COL_ESTREITA);
        }
        campo("Telefone", p.telefone(), COLUNA[3], topo, COL_ESTREITA);
        campo("Nome / Nome Empresarial", p.nome(), COLUNA[0], topo + 0.64, COL_LARGA);
        campo("Município / Sigla UF", p.municipio(), COLUNA[2], topo + 0.64, COL_ESTREITA);
        campo("Código IBGE / CEP", p.codigoECep(), COLUNA[3], topo + 0.64, COL_ESTREITA);
        campo("Endereço", p.endereco(), COLUNA[0], topo + 1.28, COL_LARGA);
        campo("E-mail", p.email(), COLUNA[2], topo + 1.28, COL_LARGA);
        if (prestador) {
            campo("Simples Nacional na Data de Competência", c.simplesNacional(), COLUNA[0], topo + 1.94, COL_ESTREITA);
            campo("Regime de Apuração Tributária pelo SN", c.regimeApuracaoSimples(), COLUNA[1], topo + 1.94, 15.30);
            return topo + 2.58;
        }
        return topo + 1.94;
    }

    /** Nota 3: com {@code indDest = 0}, o bloco vira uma linha dizendo que é o próprio tomador. */
    private double destinatario(ConteudoDanfse c, double topo) {
        Pessoa d = c.destinatario();
        if (d != null) {
            return blocoPessoa("DESTINATÁRIO DA OPERAÇÃO", d, false, true, topo, c);
        }
        return reduzido(c.destinatarioEhOTomador()
                ? "O DESTINATÁRIO É O PRÓPRIO TOMADOR/ADQUIRENTE DA OPERAÇÃO"
                : "DESTINATÁRIO DA OPERAÇÃO NÃO IDENTIFICADO NA NFS-e", topo);
    }

    private double servico(ConteudoDanfse c, double alturaDescricao, double topo) {
        linha(topo);
        tituloBloco("SERVIÇO PRESTADO", topo);
        campo("Código de Tributação Nacional / Municipal", c.codigoTributacao(), COLUNA[1], topo, COL_ESTREITA);
        campo("Código da NBS", c.codigoNbs(), COLUNA[2], topo, COL_ESTREITA);
        campo("Local da Prestação / Sigla UF / País", c.localPrestacao(), COLUNA[3], topo, COL_ESTREITA);
        // A descrição do código não tem título no DANFSe (tabela do item 2.4.5).
        texto(caber(c.descricaoTributacao(), normal, 7, LARGURA - 0.2), normal, 7, X0 + 0.08, topo + 0.70,
                PdfContentByte.ALIGN_LEFT, null);
        double desc = topo + 1.05;
        texto("Descrição do Serviço", negrito, 6, X0 + 0.08, desc + 0.05, PdfContentByte.ALIGN_LEFT, null);
        paragrafo(c.descricaoServico(), X0 + 0.08, desc + 0.30, LARGURA - 0.2, alturaDescricao - 0.30, List.of());
        return desc + alturaDescricao;
    }

    private double tributacaoMunicipal(ConteudoDanfse c, double topo) {
        linha(topo);
        tituloBloco("TRIBUTAÇÃO MUNICIPAL (ISSQN)", topo);
        campo("Tipo de Tributação do ISSQN", c.tipoTributacaoIssqn(), COLUNA[1], topo, COL_ESTREITA);
        campo("Município / Sigla UF / País de Incidência do ISSQN", c.localIncidenciaIssqn(), COLUNA[2], topo, COL_LARGA);
        double l1 = topo + 0.65, l2 = topo + 1.30, l3 = topo + 1.94;
        campo("Regime Especial de Tributação do ISSQN", c.regimeEspecial(), COLUNA[0], l1, COL_ESTREITA);
        campo("Tipo de Imunidade do ISSQN", c.imunidade(), COLUNA[1], l1, COL_ESTREITA);
        campo("Suspensão da Exigibilidade do ISSQN", c.suspensao(), COLUNA[2], l1, COL_ESTREITA);
        campo("Número Processo Suspensão", c.processoSuspensao(), COLUNA[3], l1, COL_ESTREITA);
        campo("Benefício Municipal", c.beneficioMunicipal(), COLUNA[0], l2, COL_ESTREITA);
        campo("Cálculo do BM", c.calculoBeneficio(), COLUNA[1], l2, COL_ESTREITA);
        campo("Total Deduções/Reduções", c.totalDeducoes(), COLUNA[2], l2, COL_ESTREITA);
        campo("Desconto Incondicionado", c.descontoIncondicionado(), COLUNA[3], l2, COL_ESTREITA);
        campo("BC ISSQN", c.baseCalculoIssqn(), COLUNA[0], l3, COL_ESTREITA);
        campo("Alíquota Aplicada", c.aliquotaIssqn(), COLUNA[1], l3, COL_ESTREITA);
        campo("Retenção do ISSQN", c.retencaoIssqn(), COLUNA[2], l3, COL_ESTREITA);
        campo("ISSQN Apurado", c.issqnApurado(), COLUNA[3], l3, COL_ESTREITA);
        return topo + 2.59;
    }

    private double tributacaoFederal(ConteudoDanfse c, double topo) {
        linha(topo);
        tituloBloco("TRIBUTAÇÃO FEDERAL (EXCETO CBS)", topo);
        campo("IRRF", c.irrf(), COLUNA[1], topo, COL_ESTREITA);
        campo("Contribuição Previdenciária - Retida", c.contribuicaoPrevidenciaria(), COLUNA[2], topo, COL_ESTREITA);
        campo("Contribuições Sociais - Retidas", c.contribuicoesSociaisRetidas(), COLUNA[3], topo, COL_ESTREITA);
        if (!c.imprimeLinhaPisCofins()) {
            return topo + 0.65;
        }
        double l1 = topo + 0.65;
        campo("PIS - Débito Apuração Própria", c.pisApuracaoPropria(), COLUNA[0], l1, COL_ESTREITA);
        campo("COFINS - Débito Apuração Própria", c.cofinsApuracaoPropria(), COLUNA[1], l1, COL_ESTREITA);
        campo("Descrição Contrib. Sociais - Retidas", c.descricaoContribuicoesRetidas(), COLUNA[2], l1, COL_LARGA);
        return topo + 1.30;
    }

    private double ibsCbs(ConteudoDanfse c, double topo) {
        linha(topo);
        tituloBloco("TRIBUTAÇÃO IBS / CBS", topo);
        campo("CST / cClassTrib", c.cstEClassificacao(), COLUNA[1], topo, COL_ESTREITA);
        campo("Indicador de Operação / Código IBGE Incidência / Município Incidência / Sigla UF",
                c.indicadorOperacaoEIncidencia(), COLUNA[2], topo, COL_LARGA);
        double l1 = topo + 0.64, l2 = topo + 1.29, l3 = topo + 1.94;
        campo("Exclusões e Reduções da Base de Cálculo", c.exclusoesReducoesBc(), COLUNA[0], l1, COL_ESTREITA);
        campo("Base de Cálculo Após Exclusões e Reduções", c.baseCalculoIbsCbs(), COLUNA[1], l1, COL_ESTREITA);
        campo("Red. Alíquota IBS / Red. Alíquota CBS", c.reducoesAliquota(), COLUNA[2], l1, COL_ESTREITA);
        campo("Alíquota - IBS UF / IBS Mun", c.aliquotasIbs(), COLUNA[3], l1, COL_ESTREITA);
        campo("Alíq. Efetiva Municipal - IBS", c.aliquotaEfetivaIbsMunicipal(), COLUNA[0], l2, COL_ESTREITA);
        campo("Valor Apurado Municipal - IBS", c.ibsMunicipal(), COLUNA[1], l2, COL_ESTREITA);
        campo("Alíq. Efetiva Estadual - IBS", c.aliquotaEfetivaIbsEstadual(), COLUNA[2], l2, COL_ESTREITA);
        campo("Valor Apurado Estadual - IBS", c.ibsEstadual(), COLUNA[3], l2, COL_ESTREITA);
        campo("Valor Total Apurado - IBS", c.ibsTotal(), COLUNA[0], l3, COL_ESTREITA);
        campo("Alíquota - CBS", c.aliquotaCbs(), COLUNA[1], l3, COL_ESTREITA);
        campo("Alíquota Efetiva - CBS", c.aliquotaEfetivaCbs(), COLUNA[2], l3, COL_ESTREITA);
        campo("Valor Total Apurado - CBS", c.cbsTotal(), COLUNA[3], l3, COL_ESTREITA);
        return topo + 2.58;
    }

    private double valorTotal(ConteudoDanfse c, double topo) {
        linha(topo);
        tituloBloco("VALOR TOTAL DA NFS-E", topo);
        campoDestaque("VALOR DA OPERAÇÃO / SERVIÇO", c.valorServico(), COLUNA[1], topo, COL_ESTREITA, null);
        campo("Desconto Incondicionado", c.descontoIncondicionado(), COLUNA[2], topo, COL_ESTREITA);
        campo("Desconto Condicionado", c.descontoCondicionado(), COLUNA[3], topo, COL_ESTREITA);
        double l1 = topo + 0.69;
        campo("Total das Retenções (ISSQN / Federais)", c.totalRetencoes(), COLUNA[0], l1, COL_ESTREITA);
        campoDestaque("VALOR LÍQUIDO DA NFS-e", c.valorLiquido(), COLUNA[1], l1, COL_ESTREITA, null);
        campo("Total do IBS/CBS", c.totalIbsCbs(), COLUNA[2], l1, COL_ESTREITA);
        // Sombreado por exigência do item 2.2.3.
        campoDestaque("VALOR LÍQUIDO DA NFS-e + IBS/CBS", c.valorLiquidoComIbsCbs(), COLUNA[3], l1, COL_ESTREITA - 0.03, CINZA_5);
        return topo + 1.37;
    }

    private double informacoesComplementares(ConteudoDanfse c, double altura, double topo) {
        linha(topo);
        retangulo(X0, topo, LARGURA, 0.39, CINZA_5, 0f);
        texto("INFORMAÇÕES COMPLEMENTARES", negrito, 7, X0 + 0.08, topo + 0.08, PdfContentByte.ALIGN_LEFT, null);
        double conteudo = topo + 0.41;
        // A linha dos totais aproximados é fixa: as reticências cortam só o resto (Nota 10).
        paragrafo(c.informacoesComplementares(), X0 + 0.08, conteudo + 0.05, LARGURA - 0.2, altura - 0.40,
                List.of(c.totaisAproximadosTributos()));
        return conteudo + altura;
    }

    private double canhoto(ConteudoDanfse c, double topo) {
        linha(topo);
        campoDestaque("DATA CIENTIFICAÇÃO", "", COLUNA[0], topo, COL_ESTREITA, null);
        campoDestaque("IDENTIFICAÇÃO E ASSINATURA", "", COLUNA[1], topo, COL_ESTREITA, null);
        campoDestaque("Nº NFS-E / CHAVE NFS-E", c.numeroEChave(), COLUNA[2], topo, COL_LARGA, null);
        return topo + 0.67;
    }

    /** Notas 2 a 4: o bloco inteiro vira uma linha centralizada. */
    private double reduzido(String mensagem, double topo) {
        linha(topo);
        texto(mensagem, normal, 7, X0 + LARGURA / 2, topo + 0.06, PdfContentByte.ALIGN_CENTER, null);
        return topo + BLOCO_REDUZIDO;
    }

    // =====================================================================================
    // Primitivas
    // =====================================================================================

    /** Título do bloco: 7 pt, negrito, caixa alta, fundo cinza (itens 2.2.3 e 2.4.1). */
    private void tituloBloco(String titulo, double topo) {
        retangulo(X0, topo, COL_ESTREITA, 0.40, CINZA_5, 0f);
        texto(titulo, negrito, 7, X0 + 0.08, topo + 0.10, PdfContentByte.ALIGN_LEFT, null);
    }

    /** Campo comum: título 6 pt negrito, conteúdo 7 pt (itens 2.4.2 e 2.4.4). */
    private void campo(String rotulo, String valor, double x, double topo, double largura) {
        texto(caber(rotulo, negrito, 6, largura - 0.1), negrito, 6, x + 0.08, topo + 0.05, PdfContentByte.ALIGN_LEFT, null);
        texto(caber(valor, normal, 7, largura - 0.1), normal, 7, x + 0.08, topo + 0.30, PdfContentByte.ALIGN_LEFT, null);
    }

    /** Campos de identificação: títulos em 7 pt caixa alta (item 2.4.2, segundo parágrafo). */
    private void campoDestaque(String rotulo, String valor, double x, double topo, double largura, Color fundo) {
        if (fundo != null) {
            retangulo(x, topo, largura, 0.63, fundo, 0f);
        }
        texto(caber(rotulo, negrito, 7, largura - 0.1), negrito, 7, x + 0.08, topo + 0.05, PdfContentByte.ALIGN_LEFT, null);
        texto(caber(valor, normal, 7, largura - 0.1), normal, 7, x + 0.08, topo + 0.33, PdfContentByte.ALIGN_LEFT, null);
    }

    /**
     * Texto em várias linhas dentro da altura disponível. As {@code fixas} saem sempre ao final;
     * se o texto principal não couber, a última linha visível termina em reticências.
     */
    private void paragrafo(String texto, double x, double topo, double largura, double altura, List<String> fixas) {
        double entrelinha = ENTRELINHA;
        List<String> linhasFixas = new ArrayList<>();
        for (String f : fixas) {
            linhasFixas.addAll(linhas(f, normal, 7, cm(largura)));
        }
        int capacidade = Math.max(0, (int) Math.floor(altura / entrelinha) - linhasFixas.size());
        List<String> corpo = linhas(texto, normal, 7, cm(largura));
        if (corpo.size() > capacidade) {
            corpo = new ArrayList<>(corpo.subList(0, capacidade));
            if (!corpo.isEmpty()) {
                int ultima = corpo.size() - 1;
                corpo.set(ultima, caber(corpo.get(ultima) + "...", normal, 7, largura, true));
            }
        }
        double yv = topo;
        for (String l : corpo) {
            texto(l, normal, 7, x, yv, PdfContentByte.ALIGN_LEFT, null);
            yv += entrelinha;
        }
        for (String l : linhasFixas) {
            texto(l, normal, 7, x, yv, PdfContentByte.ALIGN_LEFT, null);
            yv += entrelinha;
        }
    }

    /** Quebra por palavras na largura dada; palavras maiores que a linha são partidas. */
    private static List<String> linhas(String texto, BaseFont fonte, float tamanho, double larguraPt) {
        List<String> resultado = new ArrayList<>();
        if (texto == null || texto.isEmpty()) {
            return resultado;
        }
        for (String paragrafo : texto.split("\\R")) {
            StringBuilder atual = new StringBuilder();
            for (String palavra : paragrafo.split(" ")) {
                String tentativa = atual.isEmpty() ? palavra : atual + " " + palavra;
                if (fonte.getWidthPoint(tentativa, tamanho) <= larguraPt) {
                    atual = new StringBuilder(tentativa);
                    continue;
                }
                if (!atual.isEmpty()) {
                    resultado.add(atual.toString());
                }
                atual = new StringBuilder(palavra);
                while (fonte.getWidthPoint(atual.toString(), tamanho) > larguraPt && atual.length() > 1) {
                    int corte = atual.length() - 1;
                    while (corte > 1 && fonte.getWidthPoint(atual.substring(0, corte), tamanho) > larguraPt) {
                        corte--;
                    }
                    resultado.add(atual.substring(0, corte));
                    atual = new StringBuilder(atual.substring(corte));
                }
            }
            if (!atual.isEmpty()) {
                resultado.add(atual.toString());
            }
        }
        return resultado;
    }

    private String caber(String texto, BaseFont fonte, float tamanho, double larguraCm) {
        return caber(texto, fonte, tamanho, larguraCm, false);
    }

    /**
     * Corta com reticências o que não couber na largura. Os limites de caracteres da NT supõem
     * texto médio; um nome todo em maiúsculas largas pode estourar a célula mesmo dentro deles.
     */
    private static String caber(String texto, BaseFont fonte, float tamanho, double larguraCm, boolean jaTemReticencias) {
        if (texto == null) {
            return "";
        }
        double limite = cm(larguraCm);
        if (fonte.getWidthPoint(texto, tamanho) <= limite) {
            return texto;
        }
        String base = jaTemReticencias && texto.endsWith("...") ? texto.substring(0, texto.length() - 3) : texto;
        while (!base.isEmpty() && fonte.getWidthPoint(base + "...", tamanho) > limite) {
            base = base.substring(0, base.length() - 1);
        }
        return base.stripTrailing() + "...";
    }

    private void texto(String texto, BaseFont fonte, float tamanho, double xCm, double topoCm, int alinhamento, Color cor) {
        if (texto == null || texto.isEmpty()) {
            return;
        }
        cb.beginText();
        cb.setFontAndSize(fonte, tamanho);
        cb.setColorFill(cor == null ? Color.BLACK : cor);
        // A linha de base fica uma altura de ascendente abaixo do topo.
        float base = y(topoCm) - fonte.getFontDescriptor(BaseFont.ASCENT, tamanho);
        cb.showTextAligned(alinhamento, texto, (float) cm(xCm), base, 0);
        cb.endText();
        cb.setColorFill(Color.BLACK);
    }

    /** Linha divisória de bloco: 0,5 ponto (item 2.2.3). */
    private void linha(double topo) {
        cb.setLineWidth(0.5f);
        cb.moveTo((float) cm(X0), y(topo));
        cb.lineTo((float) cm(X0 + LARGURA), y(topo));
        cb.stroke();
    }

    private void retangulo(double x, double topo, double largura, double altura, Color fundo, float borda) {
        float rx = (float) cm(x), ry = y(topo + altura), rw = (float) cm(largura), rh = (float) cm(altura);
        if (fundo != null) {
            cb.setColorFill(fundo);
            cb.rectangle(rx, ry, rw, rh);
            cb.fill();
            cb.setColorFill(Color.BLACK);
        }
        if (borda > 0) {
            cb.setLineWidth(borda);
            cb.rectangle(rx, ry, rw, rh);
            cb.stroke();
        }
    }

    /** QR Code desenhado em vetor, módulo a módulo: nítido em qualquer impressora. */
    private void qrCode(String conteudo, double x, double topo, double lado) {
        try {
            BitMatrix m = new QRCodeWriter().encode(conteudo, BarcodeFormat.QR_CODE, 0, 0,
                    Map.of(EncodeHintType.MARGIN, 0, EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M));
            double modulo = cm(lado) / m.getWidth();
            float origemX = (float) cm(x), origemY = y(topo);
            cb.setColorFill(Color.BLACK);
            for (int i = 0; i < m.getWidth(); i++) {
                for (int j = 0; j < m.getHeight(); j++) {
                    if (m.get(i, j)) {
                        cb.rectangle((float) (origemX + i * modulo), (float) (origemY - (j + 1) * modulo),
                                (float) modulo, (float) modulo);
                    }
                }
            }
            cb.fill();
        } catch (Exception e) {
            throw new DanfseException("falha ao gerar o QR Code", e);
        }
    }

    /** Itens 2.5.1 e 2.5.2: marca d'água diagonal, 50 pt no mínimo, cinza K35. */
    private void marcaDagua(SituacaoNfse situacao) {
        String marca = switch (situacao) {
            case CANCELADA -> "CANCELADA";
            case SUBSTITUIDA -> "SUBSTITUÍDA";
            default -> null;
        };
        if (marca == null) {
            return;
        }
        PdfGState transparencia = new PdfGState();
        // Semitransparente para não esconder os valores que atravessa.
        transparencia.setFillOpacity(0.6f);
        cb.saveState();
        cb.setGState(transparencia);
        cb.beginText();
        cb.setFontAndSize(normal, 72);
        cb.setColorFill(CINZA_K35);
        cb.showTextAligned(PdfContentByte.ALIGN_CENTER, marca, PageSize.A4.getWidth() / 2,
                PAGINA_ALTURA / 2, 55);
        cb.endText();
        cb.restoreState();
    }

    private static double cm(double centimetros) {
        return centimetros * 72 / 2.54;
    }

    /** Converte "centímetros a partir do topo" na coordenada vertical do PDF. */
    private static float y(double topoCm) {
        return (float) (PAGINA_ALTURA - cm(topoCm));
    }
}
