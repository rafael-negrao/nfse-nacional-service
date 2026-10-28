# Guia de uso — biblioteca `nfse-nacional`

Guia completo para **consumir** esta biblioteca a partir de outro projeto (por exemplo, o
`cms-root`). Foi escrito para ser lido por um desenvolvedor ou por um assistente de código que
não conhece este repositório: tudo o que é preciso para emitir, consultar, cancelar e imprimir
NFS-e está aqui, inclusive as armadilhas já pagas contra o ambiente real.

> **Para o assistente do projeto consumidor:** leia este arquivo inteiro antes de escrever código
> que use a biblioteca. As seções 4 (conceitos), 7 (emissão), 14 (impressão), 15 (erros) e 16
> (tabelas de domínio) evitam os enganos mais caros. Em caso de dúvida sobre uma regra fiscal, o `CLAUDE.md` deste repositório tem o histórico
> de cada decisão; os XSDs e anexos oficiais estão em `doc/`.

Estado em **27/10/2026**.

---

## Sumário

1. [O que a biblioteca faz](#1-o-que-a-biblioteca-faz)
2. [O que funciona hoje, e onde](#2-o-que-funciona-hoje-e-onde)
3. [Instalação](#3-instalação)
4. [Conceitos que você precisa saber antes de codar](#4-conceitos-que-você-precisa-saber-antes-de-codar)
5. [Configuração — o certificado](#5-configuração--o-certificado)
6. [Uso sem Spring](#6-uso-sem-spring)
7. [Emitir uma NFS-e](#7-emitir-uma-nfs-e)
8. [Referência do `DpsBuilder`](#8-referência-do-dpsbuilder)
9. [IBS/CBS, ajuste de base de cálculo e imóveis](#9-ibscbs-ajuste-de-base-de-cálculo-e-imóveis)
10. [Consultar](#10-consultar)
11. [Cancelar e substituir](#11-cancelar-e-substituir)
12. [Manifestar](#12-manifestar)
13. [Parâmetros municipais](#13-parâmetros-municipais)
14. [Imprimir — DANFSe](#14-imprimir--danfse)
15. [Erros](#15-erros)
16. [Tabelas de domínio e utilitários](#16-tabelas-de-domínio-e-utilitários)
17. [Emissão por decisão judicial (bypass)](#17-emissão-por-decisão-judicial-bypass)
18. [Testando o projeto consumidor](#18-testando-o-projeto-consumidor)
19. [Exemplo completo de serviço Spring](#19-exemplo-completo-de-serviço-spring)
20. [Checklist e armadilhas](#20-checklist-e-armadilhas)

---

## 1. O que a biblioteca faz

Integra uma aplicação Java com o **Sistema Nacional da NFS-e** (gov.br/nfse), que tem dois
servidores:

- **Sefin Nacional** — recebe a DPS e devolve a NFS-e; registra eventos (cancelamento,
  manifestação); consulta por chave e por DPS;
- **ADN** (Ambiente de Dados Nacional) — distribui documentos por NSU, lista os eventos de uma
  nota e publica parâmetros municipais.

A biblioteca cuida de tudo que é técnico: montagem do XML no leiaute oficial, validação contra o
XSD e contra as regras dos anexos, assinatura XMLDSig, GZip+Base64, JSON, mTLS com o certificado
A1, tradução das rejeições e geração do PDF (DANFSe).

### Módulos

| Artefato | Para quê | Declarar? |
|---|---|---|
| `nfse-nacional-schemas` | Classes JAXB geradas dos XSDs oficiais | não — vem por transitividade |
| `nfse-nacional-client` | `CertificadoDigital`, `TipoAmbiente`, cliente HTTP com mTLS | não — vem por transitividade |
| `nfse-nacional-service` | `NfseService`, builders, DTOs, autoconfiguration Spring | **sim** |
| `nfse-nacional-danfse` | `DanfseService` — PDF da nota, sem rede | só se for imprimir |

GroupId `br.com.adelfo.nfse.nacional`, versão `1.0.0`. Pacote-raiz idem.

---

## 2. O que funciona hoje, e onde

| Operação | Produção restrita | Produção |
|---|---|---|
| Consultas (chave, DPS, eventos, situação, DF-e) | validado | **validado** |
| Emissão | **validado** (27/10/2026) | bloqueado — ver abaixo |
| Cancelamento + consulta do evento | **validado** | depende da emissão |
| Manifestação | implementado, sem resposta da Sefin | idem |
| DANFSe | validado com notas reais | **validado** com 11 notas reais da Comaho |

**Emissão em produção está bloqueada por dois motivos, nenhum deles de código:**

1. **Leiaute.** A biblioteca compila o leiaute da **NT 009/2026**, que produção restrita já exige
   e **produção só passa a exigir em 01/12/2026**. Até lá, produção recusa a DPS com `E1235`
   (falha de schema).
2. **Cadastro em São Paulo.** Os três CNPJs disponíveis (Adelfo, Comaho, MCamas) são de São
   Paulo, que ainda não migrou os contribuintes para o Emissor Nacional em produção — a emissão
   volta `E0084`/`E0120`. Hoje as notas dessas empresas são emitidas pelo sistema da Prefeitura e
   replicadas ao ADN, de onde a biblioteca as **consulta e imprime** normalmente.

Para saber se o município já aceita, consulte o convênio (seção 13).

---

## 3. Instalação

### Requisitos

| | |
|---|---|
| Java | **21** |
| Spring Boot | **3.x** (a biblioteca é construída com 3.3.5; usa `jakarta.*`) |
| Maven | 3.8+ |
| Certificado | A1, PKCS#12 (`.pfx`/`.p12`), com a chave privada |

### Build local

A biblioteca **não está em repositório Maven remoto**. Instale-a no `~/.m2`:

```bash
git clone git@github.com:rafael-negrao/nfse-nacional-service.git
cd nfse-nacional-service
mvn clean install            # 197 testes, ~10 s, sem rede
```

### Dependências no `pom.xml` do consumidor

```xml
<dependency>
    <groupId>br.com.adelfo.nfse.nacional</groupId>
    <artifactId>nfse-nacional-service</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- só se for gerar o PDF; traz OpenPDF e ZXing -->
<dependency>
    <groupId>br.com.adelfo.nfse.nacional</groupId>
    <artifactId>nfse-nacional-danfse</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Nos servidores

Os servidores (`prd_comaho`, `prd_mcamas`, `hml_comaho`) recebem a biblioteca pelo Ansible deste
repositório, que faz `git pull` + `mvn clean install` no `~/.m2` do usuário `ubuntu`:

```bash
cd infraestrutura/ansible/ubuntu
./03.ci.sh --ambiente prd_comaho prd_mcamas hml_comaho
```

**A biblioteca vai antes da aplicação.** Se o consumidor for construído primeiro, o build dele
usa a versão antiga do `~/.m2` (ou falha por dependência não resolvida). Como a versão é sempre
`1.0.0`, não há como o consumidor "pedir" a nova: ele usa o que estiver instalado.

---

## 4. Conceitos que você precisa saber antes de codar

**DPS × NFS-e.** O contribuinte envia uma **DPS** (Declaração de Prestação de Serviço), e a Sefin
devolve a **NFS-e**, que é a DPS embrulhada com o que a plataforma calculou (número da nota,
valores, alíquota, local de incidência) e assinada pela Receita. Você monta a DPS; a NFS-e você só
guarda.

**Síncrono.** Não existe recibo/lote como na NF-e: `emitir` responde na hora com a NFS-e ou com
a rejeição.

**Identificadores.**

| | Formato | Quem gera |
|---|---|---|
| **Chave de acesso** da NFS-e | 50 dígitos | Sefin; vem em `EmissaoResponse.chaveAcesso()` |
| **Id da DPS** | `"DPS"` + 42 dígitos = cMun(7) + tipo inscrição(1) + CNPJ/CPF(14) + série(5) + número(15) | você, via `DpsBuilder.id()` |
| **Número da NFS-e** (`nNFSe`) | sequencial por emitente | Sefin |
| **Série e número da DPS** | escolhidos por você | **você controla e não pode repetir** |

**Ambientes.** `TipoAmbiente.PRODUCAO` (tpAmb 1) e `TipoAmbiente.PRODUCAO_RESTRITA` (tpAmb 2).
**Não existe "homologação"** na NFS-e Nacional: o equivalente é a produção restrita, onde as
notas não têm validade jurídica. O ambiente do request precisa ser o mesmo do XML (`E0006`).

**Uma nota cancelada continua com `cStat 100`.** O XML da NFS-e não muda depois de emitido; o
cancelamento é um **evento** separado. Para saber se uma nota está cancelada, use
`consultarSituacao` — **nunca** o `cStat` da consulta por chave.

**O ADN atrasa ~30 s.** Notas e eventos chegam ao ADN cerca de 30 segundos depois do
processamento na Sefin. Logo após emitir ou cancelar, `consultarSituacao` pode responder
`NAO_ENCONTRADA` ou `VALIDA`. Para confirmar a operação que você acabou de fazer, use o retorno
dela.

**Não existe consulta por período.** Nenhuma rota aceita intervalo de datas. Para descobrir notas
sem conhecer a chave (por exemplo, notas recebidas de fornecedores), use a **distribuição por
NSU** (`consultarDFe`), guardando o último NSU processado — seção 10.

**Rate limit no ADN.** Distribuição, eventos e parametrização respondem **429** se chamados em
sequência rápida. Espace as chamadas (6–8 s funciona) e trate `isLimiteDeRequisicoes()`.

**Prestador emitente não informa nome nem endereço.** Com `emitidaPeloPrestador()` (o único modo
aceito hoje), a plataforma tira nome, endereço e NIF do cadastro. Informá-los é rejeição
(`E0121`, `E0128`, `E0112`); o builder já recusa.

**A biblioteca nunca lê o certificado de arquivo, banco ou variável de ambiente.** Quem decide de
onde vêm os bytes do `.pfx` e a senha é a aplicação.

---

## 5. Configuração — o certificado

A autoconfiguration (`NfseAutoConfiguration`, registrada em
`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`) **ativa-se ao
encontrar um bean `CertificadoDigital`**. Não há property de habilitação. Sem esse bean, nenhum
bean da biblioteca é criado.

```java
import br.com.adelfo.nfse.nacional.client.CertificadoDigital;

@Configuration
public class NfseConfig {

    @Bean
    public CertificadoDigital certificadoDigital(/* seu repositório/cofre */) throws Exception {
        byte[] pfx = /* bytes do .pfx — banco, Secrets Manager, cofre */;
        char[] senha = /* senha do .pfx */;

        // Opção 1: baixa as ACs-raiz ICP-Brasil do ITI, em memória
        return CertificadoDigital.comTruststoreIcpBrasil(pfx, senha);
    }
}
```

**Escolha do truststore** (a cadeia em que o cliente confia para validar os servidores):

| Fábrica | Quando usar |
|---|---|
| `comTruststoreIcpBrasil(pfx, senha)` | Padrão. Baixa do `acraiz.icpbrasil.gov.br` — **que nem sempre responde**, e **não cobre o ADN** (o ADN usa Let's Encrypt) |
| `comTruststoreCapturaDosServidores(pfx, senha, hostSefin, hostAdn)` | **Recomendado na prática.** Captura a cadeia direto dos dois servidores |
| `comTruststoreCapturaDoServidor(pfx, senha, host, 443)` | Só um host — **não use**: derruba as chamadas ao outro |
| `new CertificadoDigital(pfx, senha, truststoreBytes, senhaTruststore)` | Truststore próprio (JKS) |

```java
TipoAmbiente amb = TipoAmbiente.PRODUCAO;
String sefin = URI.create(amb.getUrlSefinNacional()).getHost();   // sefin.nfse.gov.br
String adn   = URI.create(amb.getUrlAdn()).getHost();             // adn.nfse.gov.br
return CertificadoDigital.comTruststoreCapturaDosServidores(pfx, senha, sefin, adn);
```

Padrão robusto: tentar o ITI e cair para a captura, como faz o `CertificadoDeTeste` deste
repositório:

```java
try {
    return CertificadoDigital.comTruststoreIcpBrasil(pfx, senha);
} catch (Exception e) {
    return CertificadoDigital.comTruststoreCapturaDosServidores(pfx, senha, sefin, adn);
}
```

> Se for operar nos dois ambientes com o mesmo bean, capture os quatro hosts:
> `sefin.nfse.gov.br`, `adn.nfse.gov.br`, `sefin.producaorestrita.nfse.gov.br`,
> `adn.producaorestrita.nfse.gov.br`.

**O que o certificado informa.** `certificado.inscricaoFederal()` devolve o CNPJ (ou CPF) do
titular, lido do certificado. A biblioteca usa isso como autor dos eventos; você pode usá-lo para
preencher o prestador da DPS.

**Certificado renovado.** O A1 vence em 1 ano. Ao renovar, troque os bytes que o bean entrega e
reinicie a aplicação (o bean é criado uma vez). Confira a validade com
`keytool -list -v -storetype pkcs12 -keystore cert.pfx`.

### Beans criados

| Bean | Tipo | Substituível? |
|---|---|---|
| `nfseHttpClient` | `NfseHttpClient` | sim, `@ConditionalOnMissingBean` |
| `nfseService` | `NfseService` | sim |
| `parametrosMunicipaisService` | `ParametrosMunicipaisService` | sim |

`DanfseService` **não** é bean automático (o módulo danfse não tem autoconfiguration). Declare-o
se quiser injetá-lo:

```java
@Bean
public DanfseService danfseService() { return new DanfseService(); }   // thread-safe
```

### Properties (opcionais)

```properties
nfse.nacional.connection-timeout-ms=10000   # padrão 10000
nfse.nacional.read-timeout-ms=30000         # padrão 30000
```

---

## 6. Uso sem Spring

```java
CertificadoDigital cert = CertificadoDigital.comTruststoreCapturaDosServidores(pfx, senha,
        "sefin.nfse.gov.br", "adn.nfse.gov.br");
NfseHttpClient http = new NfseHttpClient(cert, Duration.ofSeconds(10), Duration.ofSeconds(30));
NfseService nfse = new NfseServiceImpl(cert, http);
ParametrosMunicipaisService parametros = new ParametrosMunicipaisServiceImpl(http);
DanfseService danfse = new DanfseService();
```

Todos são thread-safe e devem ser criados uma vez.

---

## 7. Emitir uma NFS-e

### Exemplo validado em produção restrita

Este é o formato que **passou** na Sefin em 27/10/2026 — empresa de São Paulo, optante do Simples
(ME/EPP), serviço de software, ISSQN pelo Simples sem retenção:

```java
DpsBuilder dps = DpsBuilder.novo()
        .ambiente(TipoAmbiente.PRODUCAO_RESTRITA)
        .municipioEmissor("3550308")                         // IBGE do município do prestador
        .identificacao("1", "238", LocalDate.now())          // série, número da DPS, competência
        .emitidaPeloPrestador()

        // prestador: SÓ o documento (e contato). Nome/endereço/NIF vêm do cadastro.
        .prestadorCnpj(certificado.inscricaoFederal())
        .prestadorContato(null, "fiscal@empresa.com.br")
        .optanteSimplesNacionalMeEpp("1")                    // apura federais e municipal pelo SN
        .semRegimeEspecial()

        // tomador
        .tomadorCnpj("54.559.893/0001-39", "CLIENTE LTDA")   // pontuação é normalizada
        .tomadorEndereco(DpsBuilder.Endereco.nacional(
                "3550308", "04316-070", "RUA EXEMPLO", "324", null, "VILA GUARANI"))
        .tomadorContato(null, "financeiro@cliente.com.br")

        // serviço
        .servicoPrestadoNoMunicipio("3550308")
        .servico("010401", "Elaboração de programa de computador (software). Contrato 12/2026.")
        .codigoTributacaoMunicipal("001")                    // 3 dígitos, não 6
        .codigoNbs("115021000")                              // NBS 2.0, 9 dígitos

        // valores
        .valorServico(new BigDecimal("1500.00"))
        .issqnTributavel()
        .issqnNaoRetido()
        // SEM .aliquota(...): ME/EPP no Simples sem retenção não informa (E0625)
        .totalTributos(new BigDecimal("0.00"), new BigDecimal("0.00"), new BigDecimal("0.00"));

String idDps  = dps.id();       // guarde ANTES de enviar — é a chave de idempotência
String xmlDps = dps.build();    // XML não assinado; o builder já validou contra o XSD

EmissaoResponse resp = nfse.emitir(new EmissaoRequest(TipoAmbiente.PRODUCAO_RESTRITA, xmlDps));
String chave   = resp.chaveAcesso();   // 50 dígitos
String xmlNfse = resp.xmlNfse();       // GUARDE: é o documento fiscal
```

Não optante do Simples, com alíquota:

```java
.naoOptanteSimplesNacional()
.semRegimeEspecial()
...
.issqnTributavel()
.issqnNaoRetido()
.aliquota(new BigDecimal("2.00"))   // só quando o município de incidência não fornece
```

### O que fazer com a resposta

1. **Persista** `idDps`, `chaveAcesso` e `xmlNfse`. O XML é o documento fiscal; o PDF se gera dele
   a qualquer momento.
2. A NFS-e traz o número oficial em `<nNFSe>`, os valores calculados e o `<cStat>100</cStat>`.

### Numeração da DPS — sua responsabilidade

Série e número são do emitente e **não podem se repetir** para o mesmo CNPJ. Use uma sequência no
seu banco (com lock/transação), por série. Regras do leiaute:

- `serie`: 1 a 5 dígitos, sem zeros à esquerda (`"1"`, não `"00001"`);
- `nDPS`: 1 a 15 dígitos, sem zeros à esquerda;
- no **Id** eles entram zero-preenchidos — o builder faz isso.

### Timeout e idempotência

Se `emitir` lançar por timeout ou `IOException`, **a nota pode ter sido gerada**. Não reenvie às
cegas com outro número. Consulte pelo Id da DPS:

```java
ConsultaDpsResponse c = nfse.consultarPorIdDps(new ConsultaDpsRequest(ambiente, idDps));
if (c.gerada()) {
    // já existe: recupere pela chave (c.chaveAcesso()) e siga
} else {
    // não foi gerada: pode reenviar o MESMO xml (mesmo número de DPS)
}
```

`consultarPorIdDps` devolve `gerada=false` para DPS inexistente (não lança).
`chaveAcesso` pode vir `null` mesmo com `gerada=true` quando o certificado não é de um ator da
nota (sigilo fiscal).

### Datas

- `competencia` (em `identificacao`) é a data do fato gerador; **não pode ser posterior** à emissão
  (`E0015`);
- `dhEmi` é o instante da construção, no **horário de Brasília**, independente do fuso da JVM;
  sobrescreva com `.dataHoraEmissao(OffsetDateTime)` se precisar. Data futura é rejeitada
  (`E0008`).

### Valores

Passe `BigDecimal`. O builder arredonda para 2 casas (`HALF_UP`) e recusa negativos — o XSD exige
string com exatamente duas casas, e um `BigDecimal` de escala errada só seria recusado na Sefin.

---

## 8. Referência do `DpsBuilder`

`br.com.adelfo.nfse.nacional.service.DpsBuilder`. Os métodos devolvem o próprio builder. Erros de
preenchimento lançam `IllegalArgumentException`/`IllegalStateException` **em `build()` ou na
chamada**, com o nome do método e o código da regra.

### Identificação

| Método | Campo | Observação |
|---|---|---|
| `novo()` | — | ponto de entrada |
| `ambiente(TipoAmbiente)` | `tpAmb` | obrigatório |
| `municipioEmissor(ibge)` | `cLocEmi` | município **do cadastro do emitente**; compõe o Id (`E0004`) |
| `identificacao(serie, numero, competencia)` | `serie`, `nDPS`, `dCompet` | obrigatório |
| `dataHoraEmissao(OffsetDateTime)` | `dhEmi` | padrão: agora, em Brasília |
| `versaoAplicativo(String)` | `verAplic` | padrão `adelfo-nfse-1.0.0`; máx. 20 caracteres |
| `emitidaPeloPrestador()` | `tpEmit=1` | **o único aceito hoje** |
| `emitidaPeloTomador(motivo)` / `emitidaPeloIntermediario(motivo)` | `tpEmit=2/3` | existem no leiaute; **a Sefin recusa com `E9996`** |
| `substitui(chave, motivo, descricao)` | `subst` | ver seção 11 |
| `id()` | — | Id da DPS (45 posições); disponível antes de `build()` |
| `build()` | — | XML não assinado, validado |

`finNFSe` (finalidade) é preenchido automaticamente como `0` (NFS-e regular).

### Prestador

| Método | Observação |
|---|---|
| `prestadorCnpj(cnpj)` / `prestadorCpf(cpf)` | um dos dois; DV conferido; aceita pontuação e CNPJ alfanumérico |
| `prestadorInscricaoMunicipal(im)` | **não use em São Paulo** — `E0120` quando o CNC não tem dados complementares |
| `prestadorContato(telefone, email)` | permitido |
| `prestadorCaepf(caepf)` | pessoa física com CAEPF |
| `prestadorNome`, `prestadorEndereco`, `prestadorNif`, `prestadorSemNif` | **proibidos com `tpEmit=1`** (`E0121`, `E0128`, `E0112`) |

### Regime tributário (um de cada grupo)

| Método | Significado |
|---|---|
| `naoOptanteSimplesNacional()` | `opSimpNac=1` |
| `optanteSimplesNacionalMei()` | `opSimpNac=2` |
| `optanteSimplesNacionalMeEpp(regime)` | `opSimpNac=3`; regime `1` federais+municipal pelo SN, `2` federais pelo SN e ISSQN por fora, `3` tudo por fora |
| `semRegimeEspecial()` | `regEspTrib=0` |
| `regimeEspecial(codigo)` | `1` ato cooperado, `2` estimativa, `3` ME municipal, `4` notário, `5` autônomo, `6` sociedade de profissionais, `9` outros |

### Tomador e intermediário

| Método | Observação |
|---|---|
| `tomadorCnpj(cnpj, nome)` / `tomadorCpf(cpf, nome)` | nome opcional desde a NT 009 (passe `null`) |
| `tomadorNif(nif, nome)` / `tomadorSemNif(codigo, nome)` | tomador no exterior |
| `tomadorEndereco(Endereco)` | `Endereco.nacional(ibge, cep, logradouro, numero, complemento, bairro)` ou `Endereco.exterior(iso, codPostal, cidade, estado, logradouro, numero, complemento, bairro)` |
| `tomadorContato(telefone, email)` | |
| `tomadorInscricaoMunicipal(im)` | |
| `intermediarioCnpj/Cpf/Endereco/Contato` | só se houver intermediário |

Sem tomador, a nota sai sem tomador identificado (alguns municípios recusam o cancelamento dessas,
`E0824`).

### Serviço

| Método | Observação |
|---|---|
| `servicoPrestadoNoMunicipio(ibge)` / `servicoPrestadoNoPais(iso)` | local da prestação |
| `servico(cTribNac, descricao)` | `cTribNac` 6 dígitos, **conferido contra a lista nacional** (338 códigos); descrição livre até 2000 caracteres |
| `codigoTributacaoMunicipal(cTribMun)` | **3 dígitos** |
| `codigoNbs(cNBS)` | 9 dígitos, conferido contra a NBS 2.0 (nós de agrupamento são recusados) |
| `codigoInterno(codigo)` | código do seu sistema |
| `documentoReferencia(docRef)`, `informacoesComplementares(texto)` | |
| `semValidacaoDeCatalogo()` | desliga a conferência do `cTribNac` se a Receita publicar código novo antes de a tabela ser atualizada |

Descubra o código e a descrição oficial com `ListaServicoNacional.buscar("010401")`.

### Valores e tributos

| Método | Observação |
|---|---|
| `valorServico(v)` | obrigatório |
| `valorRecebidoIntermediario(v)` | |
| `descontoIncondicionado(v)` / `descontoCondicionado(v)` | |
| `deducaoPercentual(p)` / `deducaoValor(v)` | ajuste da BC do ISSQN (exclusivos entre si); `E1297` barra alíquota efetiva < 2% |
| `documentoAjusteBC(DocumentoAjusteBC)` | documentos que justificam o ajuste; seção 9 |
| `issqnTributavel()` / `issqnImunidade(tipo)` / `issqnExportacao(paisIso)` / `issqnNaoIncidencia()` | `tribISSQN` 1–4; um deles |
| `issqnNaoRetido()` / `issqnRetidoPeloTomador()` / `issqnRetidoPeloIntermediario()` | `tpRetISSQN` 1–3; um deles |
| `aliquota(p)` | só quando o município de incidência não fornece; máx. 5% (`E1300`); **não informe para ME/EPP no SN sem retenção** (`E0625`) |
| `exigibilidadeSuspensa(tipo, processo)` | `1` judicial, `2` administrativo |
| `pisCofins(cst, bc, aliqPis, aliqCofins, vPis, vCofins)` / `pisCofinsCst(cst)` / `retencaoPisCofins(tipo)` | |
| `retencoesFederais(vRetCP, vRetIRRF, vRetCSLL)` | |
| `semTotalTributos()` / `totalTributos(fed, est, mun)` / `totalTributosPercentual(fed, est, mun)` / `totalTributosSimplesNacional(p)` | Lei 12.741; **um deles é obrigatório** |
| `ibsCbs(IbsCbsBuilder)` | grupo da Reforma Tributária; opcional; seção 9 |

---

## 9. IBS/CBS, ajuste de base de cálculo e imóveis

### Grupo IBS/CBS (`IbsCbsBuilder`)

É **declaratório**: indicadores, destinatário e classificação tributária. Os valores de IBS/CBS
quem calcula é a plataforma, no nível da NFS-e — você não precisa da Calculadora oficial.

```java
dps.ibsCbs(IbsCbsBuilder.novo()
        .naoEhUsoOuConsumoPessoal()               // indFinal=0  (ou usoOuConsumoPessoal())
        .codigoIndicadorOperacao("100301")        // cIndOp, ANEXO_C; opcional desde a NT 009
        .destinatarioEhOProprioTomador()          // indDest=0
        .tributacao("000", "000001"));            // CST (3) + cClassTrib (6)
```

| Método | Observação |
|---|---|
| `codigoIndicadorOperacao(cIndOp)` | conferido contra o ANEXO_C; **proibido** quando não há tributação de IBS/CBS (`E0859`) |
| `tipoOperacao(tpOper)` | `1`–`5`; obrigatório com ente governamental ou serviço sobre imóvel |
| `enteGovernamental(tp)` | `1` União, `2` Estado, `3` DF, `4` Município |
| `referenciaNfse(chave)` | obrigatório com `tpOper` 2 ou 3; até 99 |
| `destinatarioEhOProprioTomador()` / `destinatarioDistintoDoTomador(Destinatario.porCnpj(cnpj, nome))` | `porCpf`, `porNif` também |
| `tributacao(cst, cClassTrib)` | obrigatório |
| `tributacaoRegular(cst, cClassTrib)`, `creditoPresumido(cod)` | só quando a classificação exigir |
| `imovel(ImovelIbsCbs)` | operações com imóveis |

> **Próxima mudança anunciada:** o release da NT 009 previsto para ~30/10/2026 move
> `indDest`/`dest` para a raiz da `infDPS`. Quando acontecer, a Sefin volta a responder `E1235`
> e a biblioteca precisa ser atualizada.

### Ajuste de base de cálculo (`DocumentoAjusteBC`)

Dedução do ISSQN e reembolso/repasse/ressarcimento do IBS/CBS, num só grupo:

```java
dps.deducaoValor(new BigDecimal("300.00"))
   .documentoAjusteBC(new DocumentoAjusteBC(
        "2", null,                                  // tipo 2 = materiais (só ISSQN)
        new BigDecimal("300.00"),                   // valor total do documento
        new BigDecimal("300.00"),                   // valor aplicado
        LocalDate.of(2026, 10, 1), null,
        new DocumentoAjusteBC.DFeNacional("2", null, "<chave NF-e de 44 dígitos>"),
        null));
```

Tipos: `2`, `5`–`9`, `99` ajustam só o ISSQN; `101`–`105`, `199` são reembolsos (ajustam ISSQN e
IBS/CBS). `descricaoTipo` só nos tipos `99`/`199`. Documento identificado por `DFeNacional`,
`OutroDocumentoFiscal` ou `DocumentoNaoFiscal`; fornecedor opcional (`Fornecedor`, com endereço).

### Imóveis (`ImovelIbsCbs`)

`ImovelIbsCbs.de(ibge, unidades...)` ou `ImovelIbsCbs.locacao(ibge, locacao, unidades...)`; cada
`UnidadeImobiliaria` tem CIB (8 caracteres) **ou** `EnderecoImovel`. Locação só no subitem 99.03.

---

## 10. Consultar

Todas as consultas **devolvem objetos**; quem lança são as operações de escrita. A exceção é
`consultarPorChave`, que lança `NfseException` com código `E2401` quando a chave não existe.

### Por chave — o XML da nota

```java
ConsultaNfseResponse r = nfse.consultarPorChave(new ConsultaNfseRequest(ambiente, chave));
String xml = r.xmlNfse();
```

Funciona para notas de qualquer origem que estejam no ambiente nacional — inclusive as emitidas
pelo sistema da Prefeitura de São Paulo.

### Por Id da DPS

```java
ConsultaDpsResponse r = nfse.consultarPorIdDps(new ConsultaDpsRequest(ambiente, idDps));
r.gerada();        // false se não existe NFS-e para a DPS
r.chaveAcesso();   // null se o certificado não for de um ator da nota
```

### Situação (válida, cancelada…)

```java
SituacaoNfse s = nfse.consultarSituacao(ambiente, chave);
```

| `SituacaoNfse` | Significado |
|---|---|
| `VALIDA` | sem evento de cancelamento |
| `CANCELADA` | cancelamento pelo emitente, por ofício ou deferido em análise fiscal |
| `SUBSTITUIDA` | cancelada por substituição (outra DPS a substituiu) |
| `CANCELAMENTO_EM_ANALISE` | pedido de análise fiscal pendente |
| `NAO_ENCONTRADA` | o ADN não conhece a chave (ou ainda não recebeu — ~30 s) |

`s.cancelada()` é `true` para `CANCELADA` e `SUBSTITUIDA`.

### Todos os documentos de uma nota

```java
ConsultaEventosResponse r = nfse.consultarEventos(new ConsultaEventosRequest(ambiente, chave));
r.nfse();                    // Optional<DocumentoDistribuido> — a própria NFS-e
r.eventos();                 // só os eventos
r.situacao();                // o mesmo que consultarSituacao
r.eventoDeCancelamento();    // Optional
```

Apesar do nome da rota, ela devolve **a NFS-e junto com os eventos**.

### Um evento específico

```java
ConsultaEventoResponse e = nfse.consultarEvento(ConsultaEventoRequest.cancelamento(ambiente, chave));
e.encontrado();   // false se não houver
e.xmlEvento();
// genérico: new ConsultaEventoRequest(ambiente, chave, "101101", 1)
```

### Distribuição por NSU — descobrir notas emitidas e recebidas

Devolve **todos** os documentos em que o CNPJ do certificado aparece — como prestador, tomador ou
intermediário —, NFS-e e eventos misturados, em ordem de NSU.

```java
long nsu = ultimoNsuSalvo();           // 0 na primeira vez
ConsultaDFeResponse lote;
do {
    lote = nfse.consultarDFe(ConsultaDFeRequest.aPartirDe(ambiente, nsu));
    for (DocumentoDistribuido d : lote.documentos()) {
        if (d.ehNfse()) {
            processarNota(d.chaveAcesso(), d.xml());
        } else {
            processarEvento(d.chaveAcesso(), d.tipoEvento(), d.xml());   // ex.: "CANCELAMENTO"
        }
    }
    nsu = lote.ultimoNsu().orElse(nsu);
    salvarUltimoNsu(nsu);                // persista a cada lote
    Thread.sleep(6_000);                 // rate limit do ADN
} while (lote.temMaisDocumentos());
```

- O ADN devolve os documentos **posteriores** ao NSU informado, sem sobreposição entre lotes;
- `lote.status()`: `DOCUMENTOS_LOCALIZADOS`, `NENHUM_DOCUMENTO_LOCALIZADO` (fim), `REJEICAO`;
- para separar emitidas de recebidas, compare o CNPJ do `<emit>` do XML com o do certificado;
- matriz consultando filial: `new ConsultaDFeRequest(ambiente, nsu, cnpjFilial, true)` — mesma
  raiz de CNPJ;
- referência: em 27/10/2026 a Comaho tinha ~800 NSUs em produção (341 NFS-e emitidas, 413
  recebidas), varridos em alguns minutos.

**Filtrar por data é com você**, sobre o que a varredura trouxer (`<dCompet>`, `<dhProc>`).

---

## 11. Cancelar e substituir

### Cancelar

```java
CancelamentoResponse r = nfse.cancelar(CancelamentoRequest.de(
        ambiente, chave,
        "1",                                        // 1 erro na emissão, 2 serviço não prestado, 9 outros
        "Erro na emissão da nota fiscal de serviço"));  // 15 a 255 caracteres — obrigatório
String xmlEvento = r.xmlEvento();
```

- O **autor** é o CNPJ do certificado. `CancelamentoRequest.comAutor(...)` só para outro
  estabelecimento do mesmo grupo (`E0812` compara só a raiz);
- vai ao ambiente que **gerou** a nota (`E0831`);
- rejeições prováveis, que dependem do município: prazo (`E0822`), valor (`E0823`), nota sem
  tomador (`E0824`), tributo já recolhido (`E0827`);
- notas emitidas pelo sistema da Prefeitura (ex.: São Paulo hoje) **se cancelam lá**, não por
  esta rota.

### Substituir

Emita uma nova DPS apontando a chave da nota a substituir. A Sefin cancela a original
(evento `e105102`) e devolve a substituta:

```java
DpsBuilder nova = DpsBuilder.novo()
        /* ... tudo como numa emissão normal, com NOVO número de DPS ... */
        .substitui(chaveOriginal, "99", "Correção do valor do serviço prestado");
nfse.emitir(new EmissaoRequest(ambiente, nova.build()));
```

Motivos: `01` desenquadramento do SN, `02` enquadramento no SN, `03` inclusão retroativa de
imunidade/isenção, `04` exclusão retroativa, `05` rejeição pelo tomador/intermediário, `99` outros.

---

## 12. Manifestar

Confirmação ou rejeição da nota pelo prestador, tomador ou intermediário. **Implementado e
validado contra o XSD, mas ainda sem uma manifestação aceita pela Sefin** — trate como
experimental.

```java
nfse.manifestar(ManifestacaoRequest.confirmacao(ambiente, chave, TipoManifestacao.CONFIRMACAO_TOMADOR));

nfse.manifestar(ManifestacaoRequest.rejeicao(ambiente, chave, TipoManifestacao.REJEICAO_TOMADOR,
        "5", "Valor do serviço diverge do contratado"));
// motivos: 1 duplicidade, 2 já emitida pelo tomador, 3 fato gerador não ocorreu,
//          4 responsabilidade tributária, 5 valor/dedução/serviço/data, 9 outros (descrição obrigatória)
```

Uma manifestação por autor e por nota (`E1833`).

---

## 13. Parâmetros municipais

`ParametrosMunicipaisService` (bean próprio; outra API, no ADN).

### Convênio — o município aceita emissão pelo padrão nacional?

```java
ConvenioResponse c = parametros.consultarConvenio(ambiente, "3550308");
c.contribuintesEmitemPeloPadraoNacional();   // USE ESTE
c.aderenteEmissorNacional();                 // NÃO basta: SP responde true e recusa toda emissão
```

`contribuintesEmitemPeloPadraoNacional()` é uma indicação barata (pré-voo). A confirmação
definitiva é a tentativa de emissão.

### Demais consultas

```java
String cod = ParametrosMunicipaisService.codigoServicoCompleto("010401", "001");   // 9 dígitos
parametros.consultarRetencoes(ambiente, "3550308", LocalDate.now());
parametros.consultarAliquota(ambiente, "3550308", cod, LocalDate.now());          // ver aviso
parametros.consultarHistoricoAliquotas(ambiente, "3550308", cod);                 // ver aviso
parametros.consultarRegimesEspeciais(ambiente, "3550308", cod, LocalDate.now());  // ver aviso
parametros.consultarBeneficio(ambiente, "3550308", nBM, LocalDate.now());
```

Retornam `ParametroMunicipalResponse(encontrado, mensagem, conteudo)` com o JSON bruto em
`conteudo`.

> **Aviso:** as três rotas com código de serviço (alíquota, histórico, regimes) respondem 400 para
> **qualquer** código nos dois ambientes — defeito do ADN. Não dependa delas.

---

## 14. Imprimir — DANFSe

O **DANFSe** (Documento Auxiliar da NFS-e) é o PDF que se entrega ao cliente. A API nacional que o
gerava foi **suspensa em 03/08/2026** pela NT 008/2026, e gerar o PDF passou a ser obrigação do
sistema emissor. O módulo `nfse-nacional-danfse` faz isso **localmente**, a partir do XML da
NFS-e, **sem rede e sem certificado**, no leiaute da NT 008 v1.02 (Anexo I).

### Dependência

```xml
<dependency>
    <groupId>br.com.adelfo.nfse.nacional</groupId>
    <artifactId>nfse-nacional-danfse</artifactId>
    <version>1.0.0</version>
</dependency>
```

Traz OpenPDF 1.3.43 e ZXing 3.5.3 (QR Code). **Não há autoconfiguration**: declare o bean se quiser
injetar.

```java
@Bean
public DanfseService danfseService() {
    return new DanfseService();   // carrega a logomarca uma vez; thread-safe
}
```

### API

| Classe | Membro | Para quê |
|---|---|---|
| `DanfseService` | `byte[] gerarPdf(DanfseRequest)` | PDF completo |
| | `byte[] gerarPdf(String xmlNfse)` | atalho: nota válida, com canhoto |
| | `void gerarPdf(DanfseRequest, Path)` | grava direto em arquivo |
| `DanfseRequest` | `de(String xmlNfse)` | request padrão: situação `VALIDA`, com canhoto |
| | `comSituacao(SituacaoNfse)` | marca d'água de cancelada/substituída |
| | `semCanhoto()` | omite o canhoto de recebimento (opcional na NT) |
| `DanfseException` | — | XML não é NFS-e, está malformado ou tem `DOCTYPE` |

O XML de entrada é **a NFS-e** (`<NFSe>…<infNFSe>…`), não a DPS. Ele vem de:

- `EmissaoResponse.xmlNfse()` — logo após emitir;
- `ConsultaNfseResponse.xmlNfse()` — consulta por chave;
- `DocumentoDistribuido.xml()` com `ehNfse()` — distribuição por NSU (notas emitidas **e**
  recebidas);
- o seu banco, se você guardou o XML (recomendado).

### Uso básico

```java
String xml = nfse.consultarPorChave(new ConsultaNfseRequest(ambiente, chave)).xmlNfse();
SituacaoNfse situacao = nfse.consultarSituacao(ambiente, chave);

byte[] pdf = danfse.gerarPdf(DanfseRequest.de(xml).comSituacao(situacao));
```

Variações:

```java
danfse.gerarPdf(xml);                                               // válida, com canhoto
danfse.gerarPdf(DanfseRequest.de(xml).semCanhoto());                // sem canhoto
danfse.gerarPdf(DanfseRequest.de(xml).comSituacao(SituacaoNfse.CANCELADA), Path.of("nota.pdf"));
```

### Logo após emitir

A nota acabou de nascer, então é válida — não consulte a situação (o ADN ainda nem a recebeu,
~30 s):

```java
EmissaoResponse r = nfse.emitir(new EmissaoRequest(ambiente, xmlDps));
byte[] pdf = danfse.gerarPdf(r.xmlNfse());
```

### Endpoint que devolve o PDF

```java
@GetMapping(value = "/notas/{chave}/danfse", produces = MediaType.APPLICATION_PDF_VALUE)
public ResponseEntity<byte[]> danfse(@PathVariable String chave) {
    Nota nota = repo.buscarPorChave(chave);                         // XML guardado na emissão
    SituacaoNfse situacao = nfse.consultarSituacao(ambiente, chave); // cancelada? substituída?
    byte[] pdf = danfse.gerarPdf(DanfseRequest.de(nota.xml()).comSituacao(situacao));
    return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                    "inline; filename=\"nfse-" + nota.numero() + ".pdf\"")
            .body(pdf);
}
```

Se a consulta de situação falhar (ADN fora do ar, 429), prefira mostrar o PDF com a situação que
você tem registrada no banco a não mostrar nada — mas registre o cancelamento no seu banco quando
**você** cancela, para não depender do ADN nesse caso.

### Imprimir notas descobertas pela distribuição (NSU)

A varredura traz notas e eventos misturados. Os eventos dizem quais notas foram canceladas, então
dá para imprimir sem uma consulta de situação por nota — agrupe os documentos por chave e deixe a
biblioteca derivar a situação, com a mesma regra de `consultarSituacao`:

```java
Map<String, List<DocumentoDistribuido>> porChave = todosOsDocumentos.stream()
        .collect(Collectors.groupingBy(DocumentoDistribuido::chaveAcesso));

porChave.forEach((chave, docs) -> {
    ConsultaEventosResponse daNota = new ConsultaEventosResponse(chave, docs);
    daNota.nfse().ifPresent(nota -> {
        byte[] pdf = danfse.gerarPdf(DanfseRequest.de(nota.xml()).comSituacao(daNota.situacao()));
        // salvar / enviar
    });
});
```

Não compare `tipoEvento` à mão: há eventos cujo nome começa com `CANCELAMENTO` e que **não**
cancelam a nota (`CANCELAMENTO_INDEFERIDO_ANALISE_FISCAL` é a recusa do pedido).

Isso só vale se você varreu **todos** os NSUs desde a emissão da nota; senão, use
`consultarSituacao` (respeitando o rate limit).

### Envio por e-mail

O e-mail do tomador está no XML, em `infDPS/toma/email`. Anexe o PDF **e o XML** — o XML é o
documento fiscal, o PDF é auxiliar.

### Regras do leiaute que a biblioteca já aplica

| Situação | O que sai no PDF |
|---|---|
| Nota de produção restrita (`tpAmb=2`) | "NFS-e SEM VALIDADE JURÍDICA" no cabeçalho |
| `comSituacao(CANCELADA)` | marca d'água diagonal "CANCELADA" |
| `comSituacao(SUBSTITUIDA)` | marca d'água "SUBSTITUÍDA" |
| `VALIDA`, `NAO_ENCONTRADA`, `CANCELAMENTO_EM_ANALISE` | sem marca d'água |
| Sem destinatário / sem intermediário | uma linha "… NÃO IDENTIFICADO NA NFS-e" no lugar do bloco |
| Destinatário = tomador | "O DESTINATÁRIO É O PRÓPRIO TOMADOR/ADQUIRENTE DA OPERAÇÃO" |
| Prestador emitente (`tpEmit=1`) | nome e endereço do prestador vêm de `infNFSe/emit`, já que a DPS não os traz |
| Campo vazio no XML | "-" (Nota 12) |
| Texto maior que o campo | cortado com "..." no limite da NT (ex.: Simples Nacional em 37 caracteres) |
| Totais aproximados dos tributos | linha sempre presente; "-" se o emitente não informou (Nota 10) |
| Competência após 2026 | some a linha de PIS/COFINS próprios (Nota 6) |
| Descrição do serviço longa | sai **inteira** até 2.000 caracteres (máximo do XSD), sempre em uma página |
| QR Code | `https://www.nfse.gov.br/ConsultaPublica/?tpc=1&chave=<chave>` |

### O que o DANFSe aceita

- **qualquer versão do leiaute** (v1.00, v1.01, NT 009) e **qualquer origem**: Emissor Nacional,
  sistema da Prefeitura de São Paulo, outros municípios. O XML é lido por nome de elemento, não
  por JAXB;
- validado em 27/10/2026 com 11 notas reais de produção da Comaho — emitidas por ela (pelo sistema
  da Prefeitura), canceladas e recebidas de fornecedores de São Paulo, Barueri (com IBS/CBS
  preenchido) e Belo Horizonte (Emissor Nacional).

### Boas práticas

- **Guarde o XML, não o PDF.** O PDF se regenera a qualquer momento, e com a situação atualizada:
  um PDF salvo antes do cancelamento continua dizendo que a nota é válida;
- gere sob demanda; é rápido (dezenas de milissegundos) e não usa rede;
- uma instância de `DanfseService` para a aplicação inteira.

### Limitações conhecidas

- fonte Helvetica no lugar de Arial/Microsoft Sans Serif (proprietárias; mesmas métricas);
- descrições formatadas com espaços para alinhar colunas (alguns sistemas fazem "tabela" de itens
  assim) perdem o alinhamento — sem quebras de linha no XML não há como reconstruí-lo. Quebras de
  linha reais são respeitadas;
- ainda não há leiaute para os novos fatos geradores do IBS/CBS — a NT 008 promete nota própria;
- XML inválido ou com `DOCTYPE` lança `DanfseException`, e o parser do JDK imprime uma linha
  `[Fatal Error]` no stderr — é ruído, não falha da geração.

---

## 15. Erros

### Tipos

| Exceção | Quando |
|---|---|
| `IllegalArgumentException` / `IllegalStateException` | validação local — builder ou construtor de DTO. **Nada foi enviado.** A mensagem diz o método e a regra |
| `NfseException` | rejeição do fisco nas operações de escrita, falha HTTP, 429, 5xx; também `consultarPorChave` com chave inexistente |
| `DanfseException` | XML não é NFS-e, está malformado ou tem `DOCTYPE` |

### `NfseException`

```java
try {
    nfse.emitir(req);
} catch (NfseException e) {
    e.getCodigo();               // "E0084", "E1235"… ou o status HTTP ("404") se não houver código fiscal
    e.getMensagem();             // descrição; com vários apontamentos, todos vêm juntos
    e.getCorpoResposta();        // corpo bruto, para log
    e.isLimiteDeRequisicoes();   // 429 → espere e repita
    e.isServicoIndisponivel();   // 5xx → repita depois; NÃO é rejeição do documento
}
```

Distinga **rejeição fiscal** (corrigir o documento) de **indisponibilidade** (repetir depois). A
Sefin valida tudo e devolve **todos** os apontamentos de uma vez — corrija todos antes de reenviar.

### Códigos já encontrados

| Código | Significado | O que fazer |
|---|---|---|
| `E1235` | XML fora do schema da Sefin | leiaute mudou (NT nova) ou produção ainda no leiaute antigo — atualizar a biblioteca |
| `E0084` | CNPJ sem estabelecimento no CNC do município | município não migrou o contribuinte; não é código |
| `E0120` | IM informada sem dados complementares no CNC | retire `prestadorInscricaoMunicipal` |
| `E0121`, `E0128`, `E0112` | nome/endereço/NIF do prestador com `tpEmit=1` | o builder já recusa |
| `E0625` | alíquota informada para ME/EPP no SN sem retenção | retire `aliquota` |
| `E9996` | emissão pelo tomador/intermediário | use `emitidaPeloPrestador()` |
| `E0006` | `tpAmb` diferente do ambiente | mesmo `TipoAmbiente` no builder e no request |
| `E0008` / `E0015` | `dhEmi` futura / competência posterior à emissão | |
| `E1300` / `E1297` | alíquota > 5% / alíquota efetiva < 2% | |
| `E0822`–`E0824`, `E0827` | prazo, valor, tomador ou tributo recolhido no cancelamento | regra do município |
| `E2401` | chave não encontrada | |
| `E2404` | DPS sem NFS-e gerada | |
| `E1228` | prefixo de namespace no XML | não acontece com o builder; só se você editar o XML |

As regras completas estão nos anexos (`doc/anexos/ANEXO_I-*.xlsx`, aba `RN DPS_NFS-e`;
`ANEXO_II-*`, aba `RN EVENTO_PED.REG.EVENTO`).

---

## 16. Tabelas de domínio e utilitários

### `Documentos` — normalização e dígito verificador

```java
Documentos.cnpj("54.559.893/0001-39", "cnpj");   // "54559893000139", ou IllegalArgumentException
Documentos.cpf(valor, "cpf");
Documentos.cpfOuCnpj(valor, "documento");
Documentos.cep("04316-070", "cep");
Documentos.municipio("3550308", "municipio");
Documentos.telefone(valor, "telefone");
Documentos.chaveAcesso(chave, "chave");          // confere o DV
Documentos.apenasDigitos(valor);
```

Recusa DV errado e todos-os-dígitos-iguais. CNPJ alfanumérico (julho/2026 em diante) é aceito.

### Tabelas de domínio (catálogos oficiais embutidos)

A biblioteca traz, **dentro do jar** do `nfse-nacional-service`, cinco tabelas geradas dos anexos
oficiais da NFS-e. Elas servem a dois propósitos: o próprio builder as usa para barrar código
inválido **antes** do envio, e o projeto consumidor pode usá-las para montar cadastros, combos e
validações sem baixar planilha nenhuma.

| Arquivo (`src/main/resources/tabelas/`) | Classe Java | Registros | Campo do leiaute | Origem |
|---|---|---|---|---|
| `servicos-nacionais-v1.01.tsv` | `ListaServicoNacional` | 338 | `cTribNac` | ANEXO_B v1.01 (22/01/2026), aba `LISTA.SERV.NAC.` |
| `nbs-v2.0.tsv` | `TabelaNbs` | 918 | `cNBS` | ANEXO_B v1.01, aba `LISTA.NBS_v2.0` |
| `municipios-ibge-v1.00.tsv` | `TabelaMunicipios` | 5.571 | `cLocEmi`, `cLocPrestacao`, `cLocIncid`, `cMun` | ANEXO_A v1.00 (10/12/2025), abas `TAB.MUN_IBGE` e `TAB.LOC.GERAL` |
| `paises-iso2-v1.00.tsv` | `TabelaPaises` | 250 | `cPais`, `cPaisPrestacao`, `cPaisResult` | ANEXO_A v1.00, aba `TAB.PAÍS_ISO2` |
| `indicadores-operacao-ibscbs-v1.01.tsv` | `TabelaIndicadoresOperacao` | 26 | `cIndOp` (IBS/CBS) | ANEXO_C v1.01 (22/01/2026) |

Cada classe expõe a versão do anexo em `VERSAO_ANEXO` (ex.: `ListaServicoNacional.VERSAO_ANEXO`
= `"ANEXO_B v1.01 (20260122)"`). As tabelas são carregadas uma vez, na primeira chamada, e são
imutáveis — pode chamar de qualquer thread.

#### Formato dos arquivos

Texto UTF-8, **separado por TAB**, uma linha por registro. Linhas iniciadas por `#` são
comentário (origem e regras de geração). Sem cabeçalho de colunas.

```text
# servicos-nacionais-v1.01.tsv — cTribNac<TAB>descrição
010101	Análise e desenvolvimento de sistemas.
010401	Elaboração de programas de computadores, inclusive de jogos eletrônicos, …

# nbs-v2.0.tsv — cNBS (9 dígitos, sem pontos)<TAB>descrição
115021000	Serviços de projeto, desenvolvimento e instalação de aplicativos e programas não personalizados …

# municipios-ibge-v1.00.tsv — código IBGE<TAB>UF<TAB>nome   (UF vazia = localidade geral)
0000000		ÁGUAS MARÍTIMAS
3550308	SP	São Paulo

# paises-iso2-v1.00.tsv — ISO 3166-1 alpha-2<TAB>nome
US	Estados Unidos

# indicadores-operacao-ibscbs-v1.01.tsv — cIndOp<TAB>característica<TAB>local a identificar
020101	Execução de operações com bem imóvel, …	Localidade do imóvel (1)
100301	Execução dos demais serviços …	…
```

**Prefira as classes aos arquivos.** Os arquivos estão no classpath (dá para lê-los com
`getResourceAsStream("/tabelas/…")`), mas o nome muda quando a versão do anexo muda; as classes
não.

#### API

```java
// Lista de serviços nacional (cTribNac — 6 dígitos: item + subitem LC 116 + desdobro nacional)
ListaServicoNacional.existe("010401");                    // true
ListaServicoNacional.buscar("010401");                    // Optional<ServicoNacional(codigo, descricao)>
ListaServicoNacional.descricao("010401");                 // Optional<String>
ListaServicoNacional.todos();                             // Collection<ServicoNacional>, 338

// NBS 2.0 (cNBS — 9 dígitos)
TabelaNbs.existe("115021000");
TabelaNbs.descricao("115021000");                         // Optional<String>; vazio para 999999999
TabelaNbs.codigos();                                      // Collection<String>, 918
TabelaNbs.formatar("115021000");                          // "1.1502.10.00" — só exibição

// Municípios IBGE
TabelaMunicipios.existe("3550308");
TabelaMunicipios.buscar("3550308");                       // Optional<Municipio(codigoIbge, uf, nome)>
TabelaMunicipios.nome("3550308");                         // Optional<"São Paulo">
TabelaMunicipios.todos();                                 // Collection<Municipio>, 5.571
municipio.ehLocalidadeGeral();                            // true para 0000000 (Águas Marítimas)

// Países ISO2
TabelaPaises.existe("US");
TabelaPaises.nome("US");                                  // Optional<String>
TabelaPaises.codigos();                                   // 250

// Indicadores de operação IBS/CBS (cIndOp — 6 dígitos)
TabelaIndicadoresOperacao.existe("100301");
TabelaIndicadoresOperacao.buscar("100301");               // Optional<IndicadorOperacao(codigo, caracteristica, localAIdentificar)>
TabelaIndicadoresOperacao.todos();                        // 26
```

`buscar`/`existe` recebem o código **só com dígitos** (sem pontos) e **não aceitam `null`** em
`TabelaMunicipios.buscar` — trate campos opcionais antes.

#### Onde a biblioteca as usa sozinha

| Tabela | Usada por | Efeito |
|---|---|---|
| `ListaServicoNacional` | `DpsBuilder.servico(...)` | `cTribNac` fora da lista → `IllegalArgumentException` no `build()` |
| `TabelaNbs` | `DpsBuilder.codigoNbs(...)` | código inexistente ou nó de agrupamento → exceção |
| `TabelaIndicadoresOperacao` | `IbsCbsBuilder.codigoIndicadorOperacao(...)` | `cIndOp` fora do ANEXO_C → exceção (`E0901`) |
| `ListaServicoNacional`, `TabelaMunicipios` | `NfseDecisaoJudicialBuilder` | preenche `xTribNac`, `xLocEmi`, `xLocPrestacao`, `xLocIncid` quando você não informa |
| `TabelaMunicipios` | `DanfseService` | completa nome do município e UF quando o XML só traz o código |

**`TabelaMunicipios` e `TabelaPaises` não são conferidas pelo `DpsBuilder`.** Ele só confere o
formato (7 dígitos para município). Se quiser barrar código IBGE inexistente antes do envio,
confira com `TabelaMunicipios.existe(...)` no seu código — a Sefin recusaria depois (`E0037` para o
local de emissão).

#### Usos típicos no projeto consumidor

```java
// Combo de serviços no cadastro do produto/contrato
List<ServicoNacional> servicos = ListaServicoNacional.todos().stream()
        .sorted(Comparator.comparing(ServicoNacional::codigo))
        .toList();

// Validação de cadastro antes de salvar
if (!ListaServicoNacional.existe(contrato.cTribNac())) {
    throw new ValidacaoException("Código de serviço inválido: " + contrato.cTribNac());
}

// Exibir "01.04.01 - Elaboração de programas…" na tela
String rotulo = ListaServicoNacional.descricao(c).map(d -> c + " - " + d).orElse(c);

// Município: nome e UF a partir do código IBGE salvo no cliente
TabelaMunicipios.buscar(cliente.codigoIbge())
        .map(m -> m.nome() + " / " + m.uf())
        .orElse("-");

// Autocomplete de município por nome
TabelaMunicipios.todos().stream()
        .filter(m -> m.nome().toLowerCase().startsWith(termo.toLowerCase()))
        .limit(20)
        .toList();
```

Se o consumidor precisar das tabelas no próprio banco (para joins ou relatórios), **importe a
partir das classes** (`todos()`), não copiando o `.tsv`: assim a carga acompanha a versão da
biblioteca instalada.

#### Armadilhas dos códigos

- **`cTribNac` tem 6 dígitos, `cTribMun` tem 3** — e a API de parametrização municipal quer os
  dois concatenados (9), via `ParametrosMunicipaisService.codigoServicoCompleto`;
- **o código da lista não é o "subitem da LC 116"**: o subitem 01.04 vira `010401` (com o
  desdobro `01`). O subitem 15.02 se abre em oito códigos (`150201`–`150208`);
- **a descrição oficial do `cTribNac` não é a descrição do serviço da nota**: em
  `servico(cTribNac, descricao)` a descrição é o texto livre do que foi prestado; a oficial a
  plataforma grava sozinha em `xTribNac`;
- **NBS só nos 9 dígitos.** Os níveis de agrupamento (5–8 dígitos) existem no anexo mas o XSD os
  recusa — por isso não estão na tabela. `999999999` é o genérico, aceito e sem descrição;
- **item 99 (`990101`)** é o único código sem incidência de ISSQN nem ICMS;
- **`0000000` (Águas Marítimas)** é uma "localidade geral", sem UF.

#### As tabelas envelhecem

São dado versionado no jar. Quando a Receita publicar anexo novo (código de serviço novo, NBS
nova, município criado), a biblioteca passa a recusar um código legítimo até ser atualizada. A
mensagem de erro cita a versão do anexo embutida, para tornar isso óbvio. Enquanto isso:

```java
DpsBuilder.novo() /* … */ .semValidacaoDeCatalogo()   // desliga a conferência de cTribNac e cNBS
```

A correção definitiva é regenerar o `.tsv` neste repositório a partir do anexo novo
(`doc/anexos/`) e reinstalar a biblioteca — ver "Tabelas de domínio geradas dos anexos" no
`CLAUDE.md`.

### `DataHoraFiscal`

```java
DataHoraFiscal.agora();   // OffsetDateTime em America/Sao_Paulo
DataHoraFiscal.formatar(instante);   // "2026-10-27T17:29:52-03:00" — sem fração, como o XSD exige
DataHoraFiscal.hoje();    // LocalDate em Brasília — use para a competência
```

Use em vez de `LocalDate.now()` se o servidor roda em UTC: perto da meia-noite, "hoje" em UTC já é
amanhã em Brasília, e a competência sairia no futuro (`E0015`).

---

## 17. Emissão por decisão judicial (bypass)

Só quando o município cadastrou uma decisão administrativa/judicial e autorizou o contribuinte.
Envia-se a **NFS-e inteira** — a responsabilidade pelo conteúdo é do contribuinte.

```java
DpsBuilder dps = /* monte como no fluxo normal */;
NfseDecisaoJudicialBuilder nj = NfseDecisaoJudicialBuilder.sobre(dps)
        .numeroNfse("240")
        .codigoNumerico("123456789")
        .incidencia("3550308", "São Paulo")
        .emitente("EMPRESA LTDA", "12345678",
                new NfseDecisaoJudicialBuilder.EnderecoEmitente(
                        "RUA X", "100", null, "CENTRO", "3550308", "SP", "01000000"))
        .aliquota(new BigDecimal("2.00"))
        .valores(new BigDecimal("1000.00"), new BigDecimal("20.00"), new BigDecimal("980.00"));
String chave = nj.chaveAcesso();   // conhecida antes do envio
nfse.emitirPorDecisaoJudicial(new EmissaoDecisaoJudicialRequest(ambiente, nj.build()));
```

Nunca exercitado contra a Sefin.

---

## 18. Testando o projeto consumidor

- **Testes unitários:** mocke `NfseService` (é interface). Use `DpsBuilder` de verdade — ele é
  puro, sem rede, e pegar erro de leiaute no teste é o ponto. `DanfseService` também roda sem
  rede: gere o PDF de um XML de fixture e verifique que não lança;
- **Testes de integração:** sempre `PRODUCAO_RESTRITA`. Notas lá não têm validade jurídica, mas
  **consomem a numeração da DPS** e ficam registradas — cancele as de teste;
- **nunca** emita em produção a partir de teste automatizado;
- guarde o `.pfx` fora do repositório (este repositório usa `certificado/`, no `.gitignore`).

---

## 19. Exemplo completo de serviço Spring

```java
@Service
@RequiredArgsConstructor
public class NotaFiscalServicoService {

    private final NfseService nfse;
    private final DanfseService danfse;
    private final CertificadoDigital certificado;
    private final NotaRepository repo;           // seu repositório
    private final SequenciaDps sequencia;        // sua sequência, transacional

    @Value("${app.nfse.ambiente:PRODUCAO_RESTRITA}")
    private TipoAmbiente ambiente;

    public Nota emitir(Pedido p) throws Exception {
        String numero = sequencia.proximo("1");

        DpsBuilder dps = DpsBuilder.novo()
                .ambiente(ambiente)
                .municipioEmissor("3550308")
                .identificacao("1", numero, DataHoraFiscal.hoje())
                .emitidaPeloPrestador()
                .prestadorCnpj(certificado.inscricaoFederal())
                .optanteSimplesNacionalMeEpp("1")
                .semRegimeEspecial()
                .tomadorCnpj(p.cnpjCliente(), p.nomeCliente())
                .servicoPrestadoNoMunicipio("3550308")
                .servico(p.codigoServico(), p.descricao())
                .valorServico(p.valor())
                .issqnTributavel()
                .issqnNaoRetido()
                .semTotalTributos();

        Nota nota = repo.save(Nota.pendente(dps.id(), numero));   // grava ANTES de enviar
        try {
            EmissaoResponse r = nfse.emitir(new EmissaoRequest(ambiente, dps.build()));
            return repo.save(nota.autorizada(r.chaveAcesso(), r.xmlNfse()));
        } catch (NfseException e) {
            if (e.isServicoIndisponivel() || e.isLimiteDeRequisicoes()) {
                return repo.save(nota.aReprocessar(e.getMensagem()));   // job confere por idDps
            }
            return repo.save(nota.rejeitada(e.getCodigo(), e.getMensagem()));
        }
    }

    public byte[] pdf(Nota nota) {
        SituacaoNfse situacao = nfse.consultarSituacao(ambiente, nota.chave());
        return danfse.gerarPdf(DanfseRequest.de(nota.xml()).comSituacao(situacao));
    }

    public void cancelar(Nota nota, String motivo) {
        CancelamentoResponse r = nfse.cancelar(CancelamentoRequest.de(ambiente, nota.chave(), "1", motivo));
        repo.save(nota.cancelada(r.xmlEvento()));
    }
}
```

Para o reprocessamento: `consultarPorIdDps(new ConsultaDpsRequest(ambiente, nota.idDps()))` — se
`gerada()`, recupere a nota pela chave; senão, reenvie o mesmo XML.

---

## 20. Checklist e armadilhas

Antes de emitir:

- [ ] Bean `CertificadoDigital` com truststore que cobre **Sefin e ADN**;
- [ ] `TipoAmbiente` igual no builder e no request;
- [ ] série/número de DPS de uma sequência própria, sem repetição, sem zeros à esquerda;
- [ ] `idDps` persistido **antes** do envio;
- [ ] prestador só com CNPJ/CPF e contato (sem nome, endereço, NIF, e sem IM em SP);
- [ ] `cTribNac` de 6 dígitos válido; `cTribMun` de **3**; NBS de 9;
- [ ] alíquota só quando cabe (nunca para ME/EPP no SN sem retenção);
- [ ] um dos `semTotalTributos`/`totalTributos*`;
- [ ] competência ≤ hoje em Brasília (`DataHoraFiscal.hoje()`);
- [ ] município aceita emissão (`consultarConvenio`) — São Paulo em produção ainda não.

Depois de emitir:

- [ ] guardar `chaveAcesso` e `xmlNfse`;
- [ ] situação por `consultarSituacao`, nunca pelo `cStat`;
- [ ] esperar ~30 s antes de confiar no ADN;
- [ ] PDF com `comSituacao(...)`.

Armadilhas que já custaram tempo:

- **HTTP/2:** a Sefin derruba conexões HTTP/2. O cliente da biblioteca fixa HTTP/1.1 — se
  substituir o `NfseHttpClient`, mantenha isso;
- **ADN de produção e o `.pfx` só com a folha:** o JDK não enviava o certificado, e o erro era
  `SSLException: Tag mismatch`. Já corrigido dentro da biblioteca (`KeyManagerDoEmitente`); se
  aparecer de novo, rode com `-Djavax.net.debug=ssl:handshake`;
- **`aderenteEmissorNacional = true` não significa que se pode emitir** — use
  `contribuintesEmitemPeloPadraoNacional()`;
- **leiaute muda sem mudar de versão:** os arquivos da NT 009 continuam chamando-se `v1.01`. O
  sinal de mudança é o `E1235`. Quando aparecer, atualize a biblioteca;
- **429 tem cara de erro de documento** se você logar só o corpo (vem HTML). Cheque
  `isLimiteDeRequisicoes()`;
- **fuso:** servidor em UTC gera competência "amanhã" depois das 21h de Brasília — use
  `DataHoraFiscal`.

### Datas a acompanhar

| Data | O quê |
|---|---|
| ~30/10/2026 | próximo release da NT 009 em produção restrita (move `indDest`/`dest`) |
| **01/12/2026** | produção passa a exigir o leiaute da NT 009 — a partir daí a biblioteca emite em produção (se o município aceitar) |
| 01/01/2027 | migração dos optantes do Simples de São Paulo para o Emissor Nacional, segundo a Prefeitura (a Resolução CGSN 189/2026 dizia 01/09/2026, sem efeito até agora) |
