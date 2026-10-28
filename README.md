# nfse-nacional

Biblioteca Java para **emitir, consultar e cancelar NFS-e** no [Sistema Nacional da NFS-e](https://www.gov.br/nfse)
(Sefin Nacional / ADN), para uso em aplicações Spring Boot.

A aplicação consumidora expõe um `@Bean CertificadoDigital` e recebe o `NfseService` pronto. Nenhuma
outra configuração é obrigatória.

---

## Estado

| | |
|---|---|
| Consultas | **validadas contra o ambiente real**, produção e produção restrita |
| Emissão | **validada em produção restrita** (27/10/2026); em produção, ainda para no CNC de São Paulo |
| Cancelamento | **validado em produção restrita**, inclusive a consulta do evento |
| Manifestação | implementada e validada contra os XSDs oficiais, **ainda sem resposta da Sefin** |
| Leiaute | **NT 009/2026** (pacote de 20/10/2026), que produção restrita já exige e produção passa a exigir em 01/12/2026 |
| DANFSE | **gerado localmente** pelo módulo opcional `nfse-nacional-danfse`, conforme a NT 008/2026 — a API nacional foi suspensa |

O ciclo de escrita — emitir, consultar, cancelar e consultar o evento — está validado de ponta a
ponta contra a Sefin de produção restrita. Em produção falta um passo administrativo: o
**município emissor precisa ter o CNC NFS-e povoado** e o CNPJ precisa ter estabelecimento nele
(`E0120`, `E0084`). Em 27/10/2026 São Paulo já atendia a isso em produção restrita, mas não em
produção.

---

## Pré-requisitos

| Requisito | Versão |
|---|---|
| Java | 21 |
| Maven | 3.8+ |
| Certificado digital | A1 — PKCS#12 (`.pfx` / `.p12`) |

---

## Instalação

```bash
git clone git@github.com:rafael-negrao/nfse-nacional-service.git
cd nfse-nacional-service
mvn clean install -DskipTests
```

Quatro artefatos são instalados em `~/.m2`, todos sob o groupId `br.com.adelfo.nfse.nacional`:
`nfse-nacional-schemas`, `nfse-nacional-client`, `nfse-nacional-service` e
`nfse-nacional-danfse`.

Declare o `nfse-nacional-service` — schemas e client vêm por transitividade:

```xml
<dependency>
    <groupId>br.com.adelfo.nfse.nacional</groupId>
    <artifactId>nfse-nacional-service</artifactId>
    <version>1.0.0</version>
</dependency>
```

E o `nfse-nacional-danfse` só se for gerar o PDF da nota (traz OpenPDF e ZXing):

```xml
<dependency>
    <groupId>br.com.adelfo.nfse.nacional</groupId>
    <artifactId>nfse-nacional-danfse</artifactId>
    <version>1.0.0</version>
</dependency>
```

---

## Uso

### 1. Fornecer o certificado

A autoconfiguration ativa-se ao encontrar um `@Bean CertificadoDigital`. **A biblioteca nunca lê o
certificado de arquivo, banco ou variável de ambiente** — os bytes vêm de onde a sua aplicação
decidir.

```java
@Bean
public CertificadoDigital certificadoDigital() throws Exception {
    byte[] pfx = /* bytes do .pfx — banco, Secrets Manager, cofre */;
    char[] senha = /* senha */;

    // baixa as ACs Raiz ICP-Brasil do repositório ITI, em memória
    return CertificadoDigital.comTruststoreIcpBrasil(pfx, senha);
}
```

Se o ITI não estiver acessível, capture a cadeia direto dos servidores. Note que **Sefin e ADN têm
cadeias distintas** — informe os dois, ou as chamadas ao ADN falham no handshake:

```java
return CertificadoDigital.comTruststoreCapturaDosServidores(
        pfx, senha, "sefin.nfse.gov.br", "adn.nfse.gov.br");
```

### 2. Emitir

```java
@Service
public class EmissaoService {

    private final NfseService nfse;

    public String emitir() {
        String xmlDps = DpsBuilder.novo()
                .ambiente(TipoAmbiente.PRODUCAO)
                .municipioEmissor("3550308")
                .identificacao("1", "238", LocalDate.now())
                .emitidaPeloPrestador()
                .prestadorCnpj("06.169.966/0001-33")     // pontuação é normalizada
                .naoOptanteSimplesNacional()
                .semRegimeEspecial()
                .tomadorCnpj("54.559.893/0001-39", "Cliente Ltda")
                .servicoPrestadoNoMunicipio("3550308")
                .servico("010401", "Elaboração de programa de computador")
                .valorServico(new BigDecimal("6075.00"))
                .issqnTributavel()
                .issqnNaoRetido()
                .aliquota(new BigDecimal("2.00"))
                .semTotalTributos()
                .build();

        EmissaoResponse resposta = nfse.emitir(new EmissaoRequest(TipoAmbiente.PRODUCAO, xmlDps));
        return resposta.chaveAcesso();
    }
}
```

O XML sai **não assinado** do builder; a assinatura XMLDSig, o GZip e o Base64 são aplicados no
envio.

### 3. Consultar e cancelar

```java
// pela chave de acesso
ConsultaNfseResponse nota = nfse.consultarPorChave(
        new ConsultaNfseRequest(ambiente, chaveAcesso));

// varredura incremental — não existe consulta por período
long nsu = 0;
ConsultaDFeResponse lote;
do {
    lote = nfse.consultarDFe(ConsultaDFeRequest.aPartirDe(ambiente, nsu));
    lote.documentos().forEach(this::processar);
    nsu = lote.ultimoNsu().orElse(nsu);
} while (lote.temMaisDocumentos());

// cancelar — o autor sai do certificado
nfse.cancelar(CancelamentoRequest.de(
        ambiente, chaveAcesso, "1", "Erro na emissão da nota fiscal"));
```

### 4. Imprimir (DANFSe)

A API nacional de DANFSE foi suspensa em 03/08/2026 (NT 008/2026); o PDF é gerado localmente, a
partir do XML da nota, sem rede nem certificado:

```java
DanfseService danfse = new DanfseService();            // thread-safe, crie uma vez

String xml = nfse.consultarPorChave(new ConsultaNfseRequest(ambiente, chave)).xmlNfse();
SituacaoNfse situacao = nfse.consultarSituacao(ambiente, chave);   // para a marca d'água

byte[] pdf = danfse.gerarPdf(DanfseRequest.de(xml).comSituacao(situacao));
```

A situação vai à parte porque a nota continua com `cStat 100` depois de cancelada; sem ela, o
DANFSe de uma nota cancelada sairia sem a marca "CANCELADA" que a NT exige. Notas de produção
restrita saem com "NFS-e SEM VALIDADE JURÍDICA" no cabeçalho.

---

## Operações

### `NfseService`

| Método | Rota |
|---|---|
| `emitir` | `POST <sefin>/nfse` |
| `emitirPorDecisaoJudicial` | `POST <sefin>/decisao-judicial/nfse` |
| `consultarPorChave` | `GET <sefin>/nfse/{chave}` |
| `consultarPorIdDps` | `GET`/`HEAD <sefin>/dps/{id}` |
| `consultarEvento` | `GET <sefin>/nfse/{chave}/eventos/{tipo}/{nSeq}` |
| `cancelar` | `POST <sefin>/nfse/{chave}/eventos` — evento `e101101` |
| `manifestar` | `POST <sefin>/nfse/{chave}/eventos` — 6 manifestações |
| `consultarEventos` | `GET <adn>/contribuintes/NFSe/{chave}/Eventos` |
| `consultarDFe` | `GET <adn>/contribuintes/DFe/{NSU}` |
| `consultarSituacao` | mesma rota de `consultarEventos`; devolve `SituacaoNfse` |

**Para saber se uma nota está cancelada, use `consultarSituacao`, não `consultarPorChave`.** A NFS-e
não muda depois de emitida: o `cStat` continua `100 - NFS-e Gerada` após o cancelamento, que é um
evento separado. A situação considera as quatro formas de cancelamento (emitente, substituição,
deferimento de análise fiscal e ofício). O ADN recebe os documentos cerca de 30 s depois da Sefin,
então logo após a operação a resposta ainda pode ser `NAO_ENCONTRADA` ou `VALIDA`.

### `ParametrosMunicipaisService`

Servido pelo ADN, sob `/parametrizacao`: `consultarConvenio`, `consultarAliquota`,
`consultarHistoricoAliquotas`, `consultarRegimesEspeciais`, `consultarBeneficio` e
`consultarRetencoes`.

Consulte o convênio **antes de emitir** — e olhe
`ConvenioResponse.contribuintesEmitemPeloPadraoNacional()`, não `aderenteEmissorNacional`. Este
último diz que o *município* aderiu, não que os *contribuintes* dele já possam emitir: São Paulo
responde `true` e ainda assim recusa toda emissão, porque manteve o emissor próprio.

### Builders

`DpsBuilder`, `NfseDecisaoJudicialBuilder`, `EventoBuilder` e `IbsCbsBuilder`. Todos produzem XML
validado contra os XSDs oficiais nos testes.

---

## Ambientes

O enum `TipoAmbiente` carrega o código `tpAmb` e as URLs dos dois hosts.

| Constante | `tpAmb` | Sefin Nacional | ADN |
|---|---|---|---|
| `PRODUCAO` | 1 | `sefin.nfse.gov.br/SefinNacional` | `adn.nfse.gov.br` |
| `PRODUCAO_RESTRITA` | 2 | `sefin.producaorestrita.nfse.gov.br/API/SefinNacional` | `adn.producaorestrita.nfse.gov.br` |

Não existe ambiente chamado "homologação" na NFS-e Nacional: o equivalente é a produção restrita.

---

## Configuração

Tudo opcional, prefixo `nfse.nacional`:

```properties
nfse.nacional.connection-timeout-ms=10000
nfse.nacional.read-timeout-ms=30000
```

Para substituir qualquer implementação, declare o seu bean — `NfseService`, `NfseHttpClient` e
`ParametrosMunicipaisService` têm `@ConditionalOnMissingBean`.

---

## Validações locais

Antes de qualquer ida à rede, a biblioteca confere o que dá para conferir:

- **Normalização** — CNPJ, CPF, código de município, CEP, telefone e chave de acesso aceitam
  entrada formatada e são limpos. Inscrição municipal e NIF não, porque admitem letras;
- **Dígito verificador** — CPF (`E0913`), CNPJ (`E0911`) e chave de acesso (`E0042`, `E0907`);
  valores com todos os dígitos iguais são recusados;
- **Catálogos oficiais** — `cTribNac`, `cNBS` e `cIndOp` são conferidos contra tabelas geradas dos
  anexos: 338 serviços, 918 códigos NBS, 5.571 municípios, 250 países, 26 indicadores. Expostos em
  `ListaServicoNacional`, `TabelaNbs`, `TabelaMunicipios`, `TabelaPaises` e
  `TabelaIndicadoresOperacao`;
- **Regras de negócio** dos anexos, onde verificáveis do lado do cliente — alíquota máxima,
  obrigatoriedade condicional, formatos de identificador.

Os erros nomeiam o método do builder que falta e citam o código da regra, para encurtar o
diagnóstico.

---

## Testes

```bash
mvn clean install        # 197 testes, sem rede nem certificado
```

Os testes de integração terminam em `IT` e **não rodam no build** — batem em ambiente real e
exigem certificado A1:

```bash
mvn test -pl nfse-nacional-service -DfailIfNoTests=false \
    -Dsurefire.failIfNoSpecifiedTests=false -Dtest=ConsultarDpsIT \
    -Dnfse.cert.p12=/caminho/cert.pfx -Dnfse.cert.senha=senha
```

| Teste | Escrita? |
|---|---|
| `ConsultarDpsIT` | não — confirma mTLS e roteamento; rode primeiro quando algo quebrar |
| `ConsultasSefinIT` | não — varre as consultas e mapeia em qual host cada rota atende |
| `ParametrosMunicipaisIT` | não — parâmetros municipais e eventos |
| `DistribuicaoDFeIT` | não — varredura por NSU |
| `BaixarSwaggerIT` | não — atualiza `doc/openapi/` |
| `DanfseIT` | não — sonda o DANFSE |
| `EmitirNfseIT`, `NfseServicosIT`, `ContratoSefinIT` | **sim — emitem documento fiscal** |
| `EmitirECancelarNfseIT` | **sim** — emite espelhando uma NFS-e real, imprime o XML e cancela |
| `ParametrosMunicipaisIT#saoPauloJaMigrouParaOEmissorNacional` | não — vigia a virada de São Paulo; já virou em produção restrita, use `-Dnfse.ambiente=producao` |

Sem certificado, os testes são pulados via `assumeTrue`. Nenhuma senha aparece em código ou log.

Os carimbos de data e hora são fixados no horário de Brasília, então o fuso da máquina não altera o
documento gerado — `DataHoraFiscalTest` verifica isso forçando a JVM para UTC, Tóquio e Los Angeles.

---

## Deploy

A biblioteca não tem serviço: o deploy instala os três artefatos no `~/.m2` do servidor, de onde a
aplicação consumidora os resolve.

```shell
cd infraestrutura/ansible/ubuntu
cp comaho_prd_vars_file.yml.example comaho_prd_vars_file.yml   # o repo é público: nada a preencher
./11.setup.java21.sh --ambiente prd_comaho                     # uma vez por servidor
./03.ci.sh           --ambiente prd_comaho
```

**A biblioteca vai antes da aplicação consumidora** — invertida, o build dela falha por dependência
não resolvida. Detalhes em [`infraestrutura/ansible/ubuntu/00.instrucoes.md`](infraestrutura/ansible/ubuntu/00.instrucoes.md).

---

## Documentação

O diretório `doc/` guarda tudo o que a biblioteca consome, para que o repositório se baste:

- `openapi/` — as 5 especificações oficiais. **A Swagger responde 403 a um navegador, mas 200 com o
  certificado na conexão**; foi assim que os contratos foram confirmados;
- `anexos/` — os 8 anexos de leiaute, regras de negócio e domínio;
- `schemas-oficiais/` e `NFSe-*.zip` — o pacote de XSDs v1.01;
- os 8 manuais e guias em PDF.

[`CLAUDE.md`](CLAUDE.md) registra as decisões de arquitetura e as armadilhas encontradas contra o
ambiente real — leitura recomendada antes de mexer no leiaute ou na assinatura.

---

## Limitações conhecidas

- **o leiaute compilado é o da NT 009/2026** (produção restrita, 20/10/2026). Produção só o exige a
  partir de 01/12/2026; até lá, uma DPS desta versão seria recusada no schema em produção;
- **CNPJ alfanumérico** é aceito, como manda o schema da NT 009. O DV da *chave de acesso* com letras
  segue a regra da NF-e por analogia e não foi conferido contra uma chave real;
- as três rotas de alíquota do ADN respondem 400 para qualquer código de serviço, inclusive códigos
  reais — defeito do lado deles, reproduzido nos dois ambientes;
- o DANFSe usa Helvetica no lugar de Arial e Microsoft Sans Serif, que são proprietárias (mesmas
  métricas da Arial). Ainda não há leiaute para os novos fatos geradores do IBS/CBS — a NT 008
  promete nota técnica própria para eles;
- eventos de município e o subgrupo IBS/CBS no nível da NFS-e não são implementados: o primeiro não
  cabe ao contribuinte, o segundo exige integração com a Calculadora oficial.
