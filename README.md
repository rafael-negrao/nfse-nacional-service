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
| Emissão, cancelamento e manifestação | implementados e validados contra os XSDs oficiais, **nunca exercitados contra a Sefin** |
| DANFSE | não implementado — o serviço está fora do ar e sem documentação publicada |

As operações de escrita montam documentos que validam no schema, usam os nomes de campo confirmados
na Swagger oficial e seguem as regras de negócio dos anexos. Ainda assim, nenhuma recebeu resposta
da Sefin — trate-as como não verificadas até a primeira emissão real.

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

Três artefatos são instalados em `~/.m2`, todos sob o groupId `br.com.adelfo.nfse.nacional`:
`nfse-nacional-schemas`, `nfse-nacional-client` e `nfse-nacional-service`.

Declare apenas o último — os outros vêm por transitividade:

```xml
<dependency>
    <groupId>br.com.adelfo.nfse.nacional</groupId>
    <artifactId>nfse-nacional-service</artifactId>
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

### `ParametrosMunicipaisService`

Servido pelo ADN, sob `/parametrizacao`: `consultarConvenio`, `consultarAliquota`,
`consultarHistoricoAliquotas`, `consultarRegimesEspeciais`, `consultarBeneficio` e
`consultarRetencoes`.

Consulte o convênio **antes de emitir**: sem `aderenteEmissorNacional`, o município não aceita DPS
pelo emissor público.

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
mvn clean install        # 134 testes, sem rede nem certificado
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

- **CNPJ alfanumérico** (IN RFB 2.229/2024, em vigor desde julho de 2026) **não é aceito**: o XSD
  v1.01 ainda tipa `TSCNPJ` como `[0-9]{14}` e não há nota técnica da NFS-e sobre o assunto. O
  cálculo do DV alfanumérico já está implementado, à espera do schema novo;
- as três rotas de alíquota do ADN respondem 400 para qualquer código de serviço, inclusive códigos
  reais — defeito do lado deles, reproduzido nos dois ambientes;
- o DANFSE responde 503 em produção e 404 em produção restrita;
- eventos de município e o subgrupo IBS/CBS no nível da NFS-e não são implementados: o primeiro não
  cabe ao contribuinte, o segundo exige integração com a Calculadora oficial.
