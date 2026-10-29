# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Objetivo

Biblioteca Java para **emitir, consultar e cancelar NFS-e** no **Sistema Nacional da NFS-e**
(Sefin Nacional / ADN — gov.br), consumida por aplicações Spring Boot da Adelfo.

## Estado

Esqueleto montado e compilando: os três módulos existem, o JAXB gera 117 classes dos XSDs oficiais
v1.01, e o fluxo de assinatura/GZip/Base64/HTTP está implementado.

**Consultas validadas contra a Sefin em produção restrita** (`SefinNacional_1.6.0`): mTLS,
`GET /dps/{id}`, `GET /nfse/{chave}` e a tradução das rejeições. Ver `ConsultasSefinIT`.

**Os nomes dos campos JSON estão confirmados pela Swagger oficial** (`doc/openapi/`):
`dpsXmlGZipB64`, `nfseXmlGZipB64`, `pedidoRegistroEventoXmlGZipB64`, `eventoXmlGZipB64`,
`chaveAcesso`. As respostas de sucesso trazem ainda `idDps` e `alertas`, que a fachada hoje ignora.

**O ciclo de escrita está validado em produção restrita desde 27/10/2026**: `EmitirECancelarNfseIT`
emitiu, consultou por chave, cancelou e consultou o evento de cancelamento — 4 de 4 — com o
certificado da Adelfo em São Paulo. Duas notas de R$ 1,00 foram emitidas e canceladas
(`…0126105597989607` e `…0226100127014831`). **Manifestação segue sem resposta da Sefin.**

**Em 28/10/2026 a Comaho repetiu o ciclo** com o perfil tributário das notas que ela emite hoje pelo
sistema da Prefeitura (Simples, `cTribNac 140101`, `cTribMun 009`, tomador pessoa física sem
endereço): emitida na primeira tentativa (`…0126101339161070`), cancelada e lida como `CANCELADA`
no ADN. O perfil está em `GUIA-DE-USO.md`, seção 9.

Três coisas tiveram de mudar para chegar lá, nesta ordem — cada uma só apareceu depois que a
anterior foi resolvida:

1. **Schema da NT 009/2026** — a Sefin de produção restrita passou a validar contra o pacote de
   20/10/2026, e a DPS do leiaute de fevereiro voltava `E1235`. Ver "Leiaute da NT 009" abaixo.
2. **`E0128`** — com `tpEmit = 1`, a DPS não pode trazer o endereço do prestador (nem nome, `E0121`,
   nem NIF, `E0112`): a plataforma os tira do cadastro. O `DpsBuilder` agora barra os três.
3. **`E0625`** — ME/EPP que apura ISSQN pelo Simples, sem retenção, não informa alíquota. A nota
   de referência traz `0.00`, mas veio do sistema próprio da Prefeitura.

**Produção continua bloqueada pelo CNC de São Paulo** (situação `0` em 27/10/2026), e a partir de
01/12/2026 também exigirá o leiaute novo — que a biblioteca já usa. O histórico abaixo explica o
bloqueio; em produção restrita ele **caiu** entre 09/08 e 27/10/2026.

**A emissão esbarrava no CNC de São Paulo, e isso ainda vale para produção.** Duas regras, a
mesma causa: `E0120` ("IM do prestador não deve ser informado, pois **não existem informações
complementares registradas no CNC NFS-e do município emissor**") e `E0084` ("CNPJ do emitente
prestador não possui estabelecimento … conforme cadastros CNPJ e **CNC NFS-e**").

São Paulo **não povoou o CNC NFS-e**: a cidade opera o sistema próprio dela. É de lá que vem a nota
de referência 236 — `tpEmis=2` e certificado da *Secretaria Municipal da Fazenda* na assinatura.
Ela nasceu no município e foi replicada ao ADN; **não é exemplo do que o Emissor Público aceita**, e
o IM que ela carrega veio de quem a gerou.

O convênio engana: SP responde `aderenteEmissorNacional: 1` nos dois ambientes, o que diz que o
município aderiu — não que os contribuintes dele estejam cadastrados para emitir por esta rota.
Descartadas a data (`E0084` se repete com outra competência) e o ambiente (produção e produção
restrita recusam igual).

**Confirmado com um segundo CNPJ:** a Comaho (`54559893000139`), com certificado próprio, recebe o
mesmo `E0084` em São Paulo. Dois contribuintes distintos, mesma cidade, mesma rejeição — o
impedimento é do município, não do cadastro de cada um. Os três certificados disponíveis
(Adelfo, Comaho, MCamas) são de empresas de São Paulo, então nenhum permite exercitar outro
município.

#### Por que São Paulo ainda não emite pelo Emissor Nacional — e quando passa a emitir

Pesquisa em 08/08/2026. São Paulo **aderiu ao padrão nacional compartilhando dados com o ADN, mas
manteve o emissor próprio**. A migração dos contribuintes para o Emissor Nacional é escalonada, e é
ela que povoa o CNC:

| Data | Grupo | Fonte |
|---|---|---|
| 03/08/2026 | profissionais liberais e autônomos | Prefeitura de SP |
| ~~01/09/2026~~ | optantes do Simples Nacional | Resolução CGSN nº 189/2026 — adiada |
| **01/11/2026** | optantes do Simples Nacional | Resolução CGSN nº 191/2026, confirmada pela Prefeitura em 19/10/2026 |

**Resolvido em 28/10/2026:** a data dos optantes do Simples é **01/11/2026** (a de 01/01/2027, que
constava antes desta tabela, ficou superada). A Prefeitura mantém o sistema próprio só para
consulta e emissão retroativa de fatos geradores até 31/10/2026. Comaho e Adelfo, ambas do
Simples, passam a emitir pelo Emissor Nacional em novembro.

**Mas produção só aceita o leiaute da NT 009 em 01/12/2026**, e a biblioteca só gera esse. Em
28/10/2026 a DPS do perfil Comaho foi validada contra o pacote anterior (`Schemas/1.01`) e
recusada — `finNFSe` na raiz da `infDPS` e `cNBS` antes de `xDescServ`. De 01/11 a 30/11 a
biblioteca não emite em produção.

A Adelfo é **optante do Simples** (`opSimpNac=3` na nota de referência), então cai justamente na
faixa em disputa: hoje não está no CNC de São Paulo, e é isso que o `E0084` e o `E0120` relatam.
A rejeição some sozinha quando a migração acontecer — não há o que corrigir no código.

#### Como saber, antes de emitir, se o município aceita

`aderenteEmissorNacional` **não responde isso** — diz que o município aderiu, não que os
contribuintes dele já emitam por essa via. Quem responde é o
`situacaoEmissaoPadraoContribuintesRFB`, que a Swagger declara como `0, 1, -1` e **não documenta**.
O significado foi determinado comparando municípios em produção restrita (09/08/2026):

| Município | `aderenteEmissorNacional` | situação |
|---|---|---|
| Juiz de Fora, Niterói, Montes Claros | true | **1** — contribuintes migrados |
| São Paulo | true | **0** — município aderiu, contribuintes não migraram |
| Brasiléia, Imbé | false | ausente — município não aderente |

**Correção (09/08/2026):** essa leitura vale como indicação, não como regra. Confrontada com a
lista oficial de aderentes (`doc/municipios-aderentes-20260710.xlsx`, coluna
`AderenteEmissorNacional`), ela bate em 4 dos 6 municípios amostrados — Juiz de Fora responde
situação `1` mas consta como `Não`, e Montes Claros o inverso. As divergências apontam em direções
opostas, então não se explicam apenas pelo mês entre as fontes.

`ConvenioResponse.contribuintesEmitemPeloPadraoNacional()` encapsula a leitura e serve de pré-voo
barato — evita montar uma DPS fadada à recusa —, mas **a fonte de verdade é a lista oficial**, e a
resposta definitiva é a tentativa de emissão. Em São Paulo as três fontes concordam: `0` na API dos
dois ambientes, `Não` na lista, `E0084` em toda emissão.

#### O cronograma de adesão dos municípios já terminou

`doc/municipios-aderentes-20260710.xlsx`, baixado de gov.br/nfse. A LC 214/2025 obrigou todos a
aderir até 01/01/2026, e a lista mostra **5.571 de 5.571 aderentes**. Nenhum registro tem início de
vigência futuro: 4 em 2022, 308 em 2023, 107 em 2024, 2.561 em 2025 e 2.589 em 2026.

Aderir, porém, admite dois caminhos — e é aqui que mora a confusão:

| Caminho | Municípios |
|---|---|
| Contribuintes emitem pelo **Emissor Nacional** | 2.517 (45%) |
| Município mantém **emissor próprio** e compartilha com o ADN | 3.054 (55%) |

São Paulo está entre os 3.054. Portanto **não há cronograma municipal a esperar**: o que falta é a
migração dos contribuintes dentro de cada município, regida pela Resolução CGSN nº 189/2026 e pelos
calendários municipais.

`E0084` é rejeição comum e bem documentada por TecnoSpeed, TOTVS e pelo fórum do ACBr; a orientação
que todos repetem é conferir a habilitação do contribuinte na interface do Emissor Nacional, não
mexer no documento. O pré-requisito citado é **Cadastro Municipal de Contribuinte (CMC/IM) válido**
no município emissor.

Não existe API alternativa: o índice oficial de APIs lista Sefin Nacional, ADN, ADN Municípios, ADN
Contribuintes, CNC, Parametrização e DANFSE — e nenhuma do Emissor Nacional, que é só web e mobile.
O `swagger/contribuintesissqn` redireciona para esse mesmo índice. A biblioteca já aponta para os
endpoints certos.

#### A consequência para este projeto

**A Adelfo só tem estabelecimento em São Paulo**, e Comaho e MCamas — os outros dois certificados
disponíveis — também são paulistanas. Não há como escolher outro município: emitir de fato exigiria
um CNPJ estabelecido onde o Emissor Nacional já opera.

Por isso **a validação das rotas de escrita ficou bloqueada até São Paulo migrar**. Em produção
restrita a situação virou `1` entre 09/08 e 27/10/2026, e a emissão passou; em produção, em
27 e 28/10/2026, ainda era `0`. A data oficial para o Simples é 01/11/2026 (Resolução CGSN nº
191/2026); para emitir pela biblioteca, produção ainda precisa do leiaute da NT 009 (01/12/2026).

Em vez de apostar numa das datas, `ParametrosMunicipaisIT.saoPauloJaMigrouParaOEmissorNacional`
pergunta ao ambiente e imprime `LIBERADO` ou `AINDA NÃO`. Rode-o periodicamente — com
`-Dnfse.ambiente=producao` para produção. Em produção restrita ele virou para `LIBERADO`, e o
`EmitirECancelarNfseIT` passou depois dos três ajustes listados em "Estado".

Se aparecer um cliente estabelecido em município de Emissor Nacional, o ciclo roda hoje mesmo:
`-Dnfse.municipio=<IBGE>` e `-Dnfse.cert.p12=<certificado dele>`. A escolha de prestador e tomador
sai do certificado. O `EmitirECancelarNfseIT` escolhe o prestador pelo CNPJ do
certificado e usa a outra parte como tomador, então basta apontar `-Dnfse.cert.p12` para o
certificado desejado. **Cancelar já foi validado** (produção restrita, 27/10/2026); **manifestar
segue sem resposta da Sefin**.

Uma NFS-e real de produção já foi consultada e confirma decisões de implementação: a assinatura da
Receita usa `rsa-sha1` + C14N + enveloped, o `<Signature>` sai **sem prefixo** e o namespace da
NFS-e é o **default** — exatamente o que `XmlSigningService` e `MarshallerNfse` produzem.
**Correção (28/10/2026):** aquela assinatura era da Prefeitura de São Paulo (nota de `tpEmis 2`). A
Sefin assina a NFS-e que ela gera com `rsa-sha256` + `xml-exc-c14n#WithComments`; a DPS assinada pela
biblioteca com `rsa-sha1` é aceita, e a do Emissor Web vai sem assinatura.

Falta: os eventos de município (que o contribuinte não emite), a manifestação contra a Sefin, e a
emissão em produção (CNC de São Paulo).

## Guia do consumidor

`GUIA-DE-USO.md` ensina a usar a biblioteca a partir de outro projeto (ex.: `cms-root`) — é o
material para entregar ao assistente de lá. **Ao mudar a API pública, um comportamento observado no
ambiente ou uma regra de rejeição, atualize o guia junto**; ele afirma assinaturas e códigos
concretos, e um guia desatualizado ensina errado com confiança.

## Projeto de referência

`/Users/rafael_negrao/projects/adelfo/nfe-sefaz-sp` é a origem do padrão arquitetural. Consulte-o
ao criar algo novo.

**Diferença estrutural:** lá o `nfe-client` é gerado por `wsimport` porque a SEFAZ SP é SOAP. A
NFS-e Nacional é REST/JSON — não há WSDL nem `jaxws-maven-plugin`. O `nfse-nacional-client` é um
cliente `java.net.http.HttpClient` escrito à mão.

## Arquitetura

```
nfse-nacional (parent, packaging=pom, artifactId ≠ diretório)
├── nfse-nacional-schemas   ← JAXB gerado por xjc dos XSDs oficiais v1.01
├── nfse-nacional-client    ← CertificadoDigital, TipoAmbiente, NfseHttpClient (mTLS), GZipBase64
├── nfse-nacional-service   ← NfseService + autoconfiguration; único módulo que o consumidor declara
│                             DpsBuilder e EventoBuilder montam o XML; NfseServiceImpl só transporta
└── nfse-nacional-danfse    ← opcional: DanfseService gera o PDF da nota (NT 008), sem rede
```

GroupId: `br.com.adelfo.nfse.nacional`. Pacote-raiz idem. O artefato pai chama-se `nfse-nacional`
(não `nfse-nacional-service`) porque parent e módulo não podem compartilhar
`groupId:artifactId:version`.

### Princípios não negociáveis (herdados da referência)

- **A biblioteca nunca lê certificado de arquivo, banco ou env var.** A aplicação consumidora expõe
  um `@Bean CertificadoDigital` (bytes do `.pfx` + senha) e a autoconfiguration monta o resto.
- **Ativação pela presença do bean de certificado**, não por property.
- **`@ConditionalOnMissingBean`** em `NfseService` e `NfseHttpClient`, para o consumidor poder
  substituir qualquer um dos dois.
- Registro em `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.

### Fatos do transporte, confirmados contra o ambiente real

- **A Sefin recusa HTTP/2.** O servidor derruba o stream com `RST_STREAM: Use HTTP/1.1 for request`,
  que o `HttpClient` do JDK repassa como um `IOException` genérico. Como o padrão do JDK é negociar
  HTTP/2, a versão é fixada em `NfseHttpClient`. Não remova essa linha.
- O repositório do ITI (`acraiz.icpbrasil.gov.br`, HTTP simples) nem sempre é alcançável. O
  fallback `comTruststoreCapturaDoServidor` funciona e é o que roda hoje nos testes.
- `GET /dps/{id}` de uma DPS inexistente responde erro HTTP, não um corpo vazio — por isso
  `consultarPorIdDps` trata 404 como "não gerada" em vez de lançar. Consulta devolve estado; quem
  lança são as operações de escrita.
- **Sefin e ADN têm cadeias de certificação distintas.** Um truststore montado só com a cadeia de
  um deles derruba as chamadas ao outro no handshake, e o erro chega como `IOException` genérico
  com cara de indisponibilidade. Use `comTruststoreCapturaDosServidores` quando o ITI não estiver
  acessível. Em 27/10/2026 os dois ADN servem certificado **Let's Encrypt** (`YR2` em produção,
  `YR1` em produção restrita); a Sefin segue com SERPRO/GlobalSign. Ou seja: a cadeia ICP-Brasil
  do ITI **não** cobre o ADN.
- **O ADN de produção anuncia só as ACs raiz no `CertificateRequest`** (ICP-Brasil v5, v10, v11,
  v12); produção restrita anuncia 176, intermediárias inclusive. O `KeyManager` padrão do JDK só
  envia certificado cujo emissor, **dentro da cadeia guardada no `.pfx`**, esteja na lista — e o
  `.pfx` da Adelfo traz só a folha (emitida pela `AC Certisign RFB G5`). Resultado: o JDK enviava
  uma lista **vazia** e acusava `SSLException: Tag mismatch` ao decifrar o alerta do servidor, com
  qualquer cifra — cara de defeito de ChaCha20 ou de rede, e não era nenhum dos dois. O `curl`
  funcionava porque o OpenSSL envia o certificado de qualquer forma. `KeyManagerDoEmitente`
  oferece o certificado ignorando a lista; só o `-Djavax.net.debug=ssl:handshake` mostra a causa
  ("No X.509 cert selected"). Diagnosticado e corrigido em 27/10/2026.
- **A captura de cadeia precisa tolerar a recusa do mTLS.** Em TLS 1.2 o servidor pede o
  certificado do cliente durante o handshake, e o ADN de produção derruba a captura (que não
  apresenta certificado) com `handshake_failure` — depois de já ter enviado a própria cadeia.
  `capturarTruststoreDoServidor` aproveita a cadeia recebida; antes a descartava, e como
  `comTruststoreCapturaDosServidores` engole a falha por host, o truststore saía sem o ADN e a
  chamada real dava `PKIX path building failed`.
- **Envelope de erro da Sefin**, confirmado no ambiente:
  ```json
  { "tipoAmbiente": 2, "versaoAplicativo": "SefinNacional_1.6.0",
    "dataHoraProcessamento": "…",
    "erro": { "codigo": "E2401", "descricao": "Chave de acesso não encontrada." } }
  ```
  O código é `E####`, não numérico como o `cStat` da NF-e. `RespostaDeErro` extrai `erro` singular,
  `erros[]` plural e o formato sem envelope; para corpo não-JSON (rota inexistente devolve HTML do
  ASP.NET) o status HTTP vira o código, para não inventar significado fiscal.

  **As rotas não concordam na grafia dos campos.** As consultas mandam `erro` com chaves
  minúsculas; a **emissão** manda `erros[]` com as chaves em **PascalCase** (`Codigo`,
  `Descricao`) e ainda o `idDPS` recusado. Enquanto a busca era sensível a maiúsculas, a rejeição
  de negócio chegava ao chamador como código `"400"` com o JSON inteiro por mensagem —
  indistinguível de falha de transporte. Hoje os campos são procurados sem diferenciar caixa, e
  quando há mais de um apontamento **todos** entram na mensagem: a validação da DPS não para no
  primeiro, e reportar só ele faria corrigir um campo por reenvio.

  Códigos já vistos: `E2401` chave não encontrada, `E2404` DPS sem NFS-e gerada, `E0084` CNPJ sem
  estabelecimento no município emissor, `E0120` IM informado sem dados complementares no CNC —
  estes dois confirmados em produção **e** em produção restrita.

### Fluxo das operações de escrita

Idêntico para emissão e evento, e é o coração da biblioteca:

```
XML → assinatura XMLDSig (enveloped) → GZip → Base64 → campo string do JSON → POST
```

A resposta faz o caminho inverso. As rotas trafegam JSON; o documento fiscal em si é sempre XML.

| Operação | Rota Sefin Nacional | Observação |
|---|---|---|
| Emitir | `POST /nfse` | síncrono; DPS que referencia chave existente gera evento `e105102` (substituição) |
| Emitir por decisão judicial | `POST /decisao-judicial/nfse` | o "bypass"; ver abaixo |
| Consultar por chave | `GET /nfse/{chaveAcesso}` | chave tem 50 dígitos |
| Consultar por DPS | `GET /dps/{id}`, `HEAD /dps/{id}` | GET só devolve a chave se o certificado for de um ator da nota; HEAD é liberado |
| Cancelar | `POST /nfse/{chaveAcesso}/eventos` | evento `e101101`, síncrono |
| Manifestar | `POST /nfse/{chaveAcesso}/eventos` | mesma rota; confirmação/rejeição do prestador, tomador ou intermediário |
| Consultar um evento | `GET /nfse/{chaveAcesso}/eventos/{tipo}/{nSeq}` | **só esta forma existe**; sem o sequencial a rota responde 405 (aceita apenas POST). **A resposta não segue a Swagger** — ver abaixo |

**A consulta de evento diverge da Swagger**, confirmado em 27/10/2026. A especificação declara a
mesma resposta do POST (`eventoXmlGZipB64`); o servidor devolve `eventos[]`, com `chaveAcesso`,
`tipoEvento`, `numeroPedidoRegistroEvento`, `dataHoraRecebimento` e `arquivoXml` — e este com o
GZip codificado em **Base64 duas vezes** (`SDRzSUFB…` é o Base64 de `H4sIAAA…`). Lendo só o campo da
Swagger, o cancelamento recém-registrado voltava como "não encontrado".
`NfseServiceImpl.xmlDoEventoConsultado` lê as duas formas.

Fora da Sefin, no **ADN**, ficam os parâmetros municipais — ainda fora da fachada:

| Consulta | Rota | Estado |
|---|---|---|
| Documentos de uma nota | `GET <adn>/contribuintes/NFSe/{chave}/Eventos` | implementada e validada com nota real; apesar do nome, **devolve a NFS-e junto com os eventos** |
| Distribuição de DF-e por NSU | `GET <adn>/contribuintes/DFe/{NSU}?lote=true&cnpjConsulta=…` | implementada e validada em produção |
| Convênio do município | `GET <adn>/parametrizacao/{cMun}/convenio` | implementada, **responde 200** |
| Retenções | `GET <adn>/parametrizacao/{cMun}/{competencia}/retencoes` | implementada, responde; o contribuinte vem do certificado, não do caminho |
| Alíquota | `GET <adn>/parametrizacao/{cMun}/{cServico}/{competencia}/aliquota` | implementada; ver defeito abaixo |
| Histórico de alíquotas | `GET <adn>/parametrizacao/{cMun}/{cServico}/historicoaliquotas` | implementada; ver defeito abaixo |
| Regimes especiais | `GET <adn>/parametrizacao/{cMun}/{cServico}/{competencia}/regimes_especiais` | implementada; ver defeito abaixo |
| Benefício municipal | `GET <adn>/parametrizacao/{cMun}/{nBM}/{competencia}/beneficio` | implementada, **não exercitada** (exige um nBM real) |
| DANFSE (PDF) | `<adn>/danfse/…` | **indisponível**; ver abaixo |

Estão em `ParametrosMunicipaisService`, fachada separada da `NfseService` porque é outra API em
outro host — a própria Sefin responde "Este serviço foi movido" na rota antiga.

O manual descreve esses caminhos como `/parametros_municipais/{cMun}/…` sob o Emissor Público.
**Esse segmento não existe em host nenhum** — na Sefin a chamada volta como página HTML do ASP.NET.

- A **competência** viaja como `AAAA-MM-DD`; `AAAAMMDD` e `AAAAMM` são recusados.
- O **código de serviço** desta API tem **9 dígitos** = `cTribNac` (6) + `cTribMun` (3) — não os 6
  do `cTribNac` da DPS. Use `ParametrosMunicipaisService.codigoServicoCompleto(...)`.
- **Defeito do ADN:** as três rotas que recebem código de serviço respondem 400 com
  "O código do serviço deve ser composto por nove dígitos" **para qualquer valor**. Testados 6, 7,
  8, 9 e 10 dígitos e a forma pontuada; e `010401001` — código real extraído de uma NFS-e de São
  Paulo, `cTribNac 010401` + `cTribMun 001` — **em produção e em produção restrita**. Sempre a
  mesma resposta. Os caminhos vieram da Swagger oficial, então a implementação fica;
  `ParametrosMunicipaisIT` fixa o comportamento e avisa quando a rota voltar.

### Emissão por decisão administrativa ou judicial (bypass)

No fluxo regular envia-se a DPS e a plataforma calcula o resto. No bypass envia-se a **NFS-e
completa** e a Sefin só valida o mínimo — a responsabilidade pelo conteúdo passa a ser do
contribuinte. Pré-requisito não técnico: o município precisa ter cadastrado a decisão e autorizado
o contribuinte.

`NfseDecisaoJudicialBuilder.sobre(dpsBuilder)` monta o documento; `NfseService.emitirPorDecisaoJudicial`
envia. Diferenças em relação à emissão comum:

- o campo JSON é **`xmlGZipB64`**, não `dpsXmlGZipB64`;
- o elemento assinado é o `infNFSe`, não o `infDPS` embutido;
- o `Id` tem **53 posições**: `"NFS"` + cMun (7) + ambGer (1) + tipo de inscrição (1) + inscrição
  federal (14) + nNFSe (13) + AAMM (4) + código numérico (9) + **DV por módulo 11** (1). A chave de
  acesso é esse Id sem o `"NFS"`, e o contribuinte a conhece antes de enviar;
- o builder fixa `cStat=102`, `ambGer=2`, `tpEmis=1` e `dhProc = dhEmi`, conforme o manual;
- `nNFSe` é sequência controlada pelo contribuinte e não pode colidir com nota existente.

**Conflito entre manual e schema:** o manual manda `nDFSe="0"`, mas `TSNDFSe` tem pattern
`[1-9]{1}[0-9]{0,12}`, que não admite zero. Prevalece o manual, por ser específico deste fluxo;
`numeroDfeMunicipal(...)` sobrescreve se a Sefin recusar.

Note também que `ds:Signature` é **obrigatório** em `TCNFSe`, ao contrário de `TCDPS` e
`TCPedRegEvt`, onde é opcional. Por isso o documento não assinado nunca valida por completo no
XSD — `NfseDecisaoJudicialBuilderTest` fixa exatamente quais violações são esperadas.

### Não existe consulta por período

Varri as cinco specs: **nenhuma rota de NFS-e aceita intervalo de datas**. As consultas são todas
pontuais — por chave de acesso, por Id de DPS ou por tipo+sequencial de evento. Para descobrir
notas sem conhecer a chave, o mecanismo é a **distribuição incremental por NSU**, como na NF-e:
guarda-se o último NSU processado e pede-se o próximo lote. Filtrar por data é trabalho do
chamador, sobre o que a varredura trouxer. Ver `NfseService.consultarDFe` e o padrão em
`ConsultaDFeResponse`.

Comportamento confirmado em produção: o ADN devolve os documentos **posteriores** ao NSU informado,
em ordem crescente e sem sobreposição entre lotes; `StatusProcessamento` vira
`NENHUM_DOCUMENTO_LOCALIZADO` no fim da varredura. O lote traz NFS-e e eventos misturados,
distinguidos por `TipoDocumento`.

### As duas rotas do ADN compartilham o envelope

`GET /contribuintes/DFe/{NSU}` e `GET /contribuintes/NFSe/{chave}/Eventos` devolvem o mesmo
`LoteDistribuicaoNSUResponse`: `StatusProcessamento`, `LoteDFe[]`, `Alertas`, `Erros`. Os campos vêm
em **PascalCase**, ao contrário da Sefin, e o `ArquivoXml` é GZip+Base64. `NfseServiceImpl` tem um
único parser (`documentosDoLote`) para as duas, e ambas entregam `DocumentoDistribuido`.

**A situação da nota sai daqui, não da consulta por chave.** A NFS-e não muda depois de emitida —
o `cStat` continua `100 - NFS-e Gerada` após o cancelamento, e é isso que o portal mostra ao abrir a
nota. `ConsultaEventosResponse.situacao()` deriva `SituacaoNfse` dos eventos: as quatro formas de
cancelamento do ANEXO_II (`CANCELAMENTO`, `CANCELAMENTO_POR_SUBSTITUICAO`,
`CANCELAMENTO_DEFERIDO_ANALISE_FISCAL`, `CANCELAMENTO_POR_OFICIO`, nomes do enum `TipoEvento` da
Swagger do ADN) e a solicitação de análise fiscal pendente. `NfseService.consultarSituacao` é o
atalho. O ADN recebe nota e evento **~30 s depois** do processamento na Sefin (27/10/2026: `dhProc`
17:29:52, geração no ADN 17:30:24) — consultar a situação logo após a operação engana. **E nem
sempre são 30 s:** em 28/10/2026 o cancelamento da nota de teste `…0226108642141870` (item 99,
não incidência) constava na Sefin às 09:08:04, **ainda não tinha chegado ao ADN às 10:21** — a
rota `/Eventos` devolvia só a nota — e chegou até as 22:42; o da `…0126101339161070` (`140101`, ISSQN
em São Paulo), do mesmo dia, chegou em menos de 45 s, e dois cancelamentos `140101` da noite em
~35 s. Se o item 99 tem a ver com isso, um caso só não diz. A Sefin (`consultarEvento`) é a
confirmação imediata; o ADN é eventual, com atraso que pode passar de uma hora.

Duas armadilhas confirmadas em produção:

- a rota `/Eventos` **não devolve só eventos** — a NFS-e vem junto, distinguida por
  `TipoDocumento`. Por isso `ConsultaEventosResponse` expõe `documentos()`, e `eventos()`/`nfse()`
  como recortes;
- `TipoEvento` **só existe nos itens de evento**; no item da NFS-e o campo não vem.

### Rate limit do ADN

As rotas de distribuição, parametrização e DANFSE são limitadas por ritmo: duas chamadas seguidas
já podem render **429**. O 429 chega como página HTML, não como JSON da API — se despejado como
"mensagem de rejeição", faz procurar erro no documento quando o problema é cadência.
`RespostaDeErro` traduz 429 e 5xx em mensagens próprias, e `NfseException` expõe
`isLimiteDeRequisicoes()` e `isServicoIndisponivel()` para o chamador distinguir isso de rejeição
fiscal e reprocessar.

### DANFSE — gerado localmente (`nfse-nacional-danfse`)

**A API nacional de DANFSE foi suspensa em 03/08/2026** pela **NT 008/2026** (SE/CGNFS-e,
v1.02 de 14/07/2026, em `doc/nt-008-se-cgnfse-danfse-v1.02-20260714.pdf`).
Gerar o PDF passou a ser obrigação do sistema emissor. Em 27/10/2026 o ADN ainda respondia 404 em
`/danfse/*` (ver `DanfseIT`), e o Emissor Nacional web continua imprimindo pela própria tela.

`DanfseService.gerarPdf(DanfseRequest)` recebe o XML da NFS-e (de `emitir` ou `consultarPorChave`)
e devolve uma página A4. Não usa rede nem certificado. Decisões que valem saber:

- **OpenPDF, não JasperReports** — diverge do `nfe-danfe`. A NT especifica o DANFSe como tabela de
  coordenadas em cm (item 2.4.5) e um modelo (Anexo I); `DesenhoDanfse` desenha nessas
  coordenadas. Em Jasper seriam ~120 elementos posicionados num `.jrxml` escrito à mão — o do
  `nfe-danfe` veio pronto do Java_NFe, este não existe. O OpenPDF é o motor que o Jasper usa por
  baixo para PDF.
- **XML lido por nome local, não por JAXB** (`XmlNfse`): o DANFSe imprime notas de qualquer versão
  do leiaute. A nota de referência 236 (produção, leiaute antigo) e as da NT 009 saem pelo mesmo
  código; `finNFSe`, `CST`/`cClassTrib` e os nomes de ajuste de BC são lidos nos dois lugares.
- **Prestador vem do `infNFSe/emit` quando a DPS não o traz.** A NT aponta só `DPS/prest`, mas com
  `tpEmit = 1` nome e endereço do prestador são proibidos na DPS (`E0121`, `E0128`); seguir a NT ao
  pé da letra deixaria o bloco em branco em toda nota emitida pelo prestador.
- **O CEP do `emit` vem sem o zero à esquerda** (`4273200`) — `Formatos.cep` completa.
- **A situação vem de fora** (`DanfseRequest.comSituacao`): a nota fica com `cStat 100` depois de
  cancelada, então a marca d'água "CANCELADA"/"SUBSTITUÍDA" (item 2.5) depende de
  `NfseService.consultarSituacao`.
- **Fontes**: a NT pede Arial e Microsoft Sans Serif, proprietárias; usa-se Helvetica (base 14 do
  PDF, mesmas métricas da Arial, sem embutir). O poppler (`pdftoppm`) a renderiza sem negrito — o
  PDF referencia `Helvetica-Bold` e o Preview/Acrobat mostram certo.
- **Logomarca** oficial da NFS-e em `src/main/resources/danfse/`, baixada do link da NT (item 2.4.3).
- Blocos sem dados viram a linha das Notas 2–4 e cedem altura (item 2.3); a linha de PIS/COFINS
  próprios some após 2026 (Nota 6).
- **Descrição do Serviço e Informações Complementares dividem o espaço que sobra**: estas ficam
  com o que o conteúdo delas precisa, aquela com o resto. Com as alturas da tabela da NT ao pé da
  letra, uma descrição de 1.300 caracteres numa nota com todos os blocos saía em **uma linha**,
  com 5 cm em branco embaixo. A NT chama essas alturas e os limites de caracteres de sugestão
  (item 2.1); o obrigatório — modelo do Anexo I e página única — se mantém.
- **A descrição sai inteira, sem o corte em 1.300 caracteres** da tabela: o XSD admite 2.000, e a
  página comporta ~2.600 mesmo no pior caso. `DanfseServiceTest` fixa isso com 2.000 caracteres e
  todos os blocos presentes.

Conferido visualmente contra o Anexo I com a nota 236 (produção, cancelada) e a de produção
restrita de 27/10/2026. O fixture de teste é esta última com nomes, endereços e contatos trocados
por dados fictícios — o repositório é público.

### Regras de negócio da emissão (`ANEXO_I`, aba `RN DPS_NFS-e`)

A que muda a API: **`E9996` — "não é permitida a emissão de NFS-e pelo tomador ou intermediário"**.
O leiaute prevê `tpEmit` 2 e 3, e o `DpsBuilder` os expõe, mas o Emissor Público rejeita ambos
nesta versão. Só `tpEmit=1` (prestador) passa.

Demais regras que o builder documenta ou já respeita:

| Código | Regra |
|---|---|
| `E0112`, `E0121`, `E0128` | Com `tpEmit = 1` o prestador **não** leva NIF, nome nem endereço — a plataforma os tira do cadastro. `E0128` chegou na primeira emissão que passou pelo schema; o `DpsBuilder` agora barra os três |
| `E0625` | ME/EPP que apura ISSQN pelo Simples, sem retenção e sem benefício municipal, **não** informa alíquota. Não verificado no builder (depende do benefício municipal); o `EmitirECancelarNfseIT` deixou de mandar `0.00` |
| `E0004` | O `Id` é a concatenação exata de `"DPS"` + cód. município **do endereço do emitente** + tipo de inscrição (1=CPF, 2=CNPJ) + inscrição federal + série + número |
| `E0006` | `tpAmb` deve bater com o ambiente que recebeu a DPS |
| `E0008` | `dhEmi` anterior ou igual ao processamento — data futura é rejeitada |
| `E0010` | Série fora da faixa definida para o tipo de emissor |
| `E0015` | `dCompet` anterior ou igual a `dhEmi` |
| `E0037` | `cLocEmi` precisa existir no cadastro de convênio municipal |
| `E0042` | DV da chave em `chSubstda` — validado por `Documentos.chaveAcesso` |
| `E0714` | A assinatura da DPS deve ser válida |
| `E1297` | Redução de BC não pode levar a alíquota efetiva abaixo de 2% |
| `E1300` | Alíquota aplicada não pode passar de 5% |
| `E1289` | `vISSQN` deve ser igual a `vBC` × alíquota |
| `E1506`, `E1508` | Total de retenção e valor líquido não podem ser negativos |

O bloco IBS/CBS tem regras próprias (`E1530`+) que exigem valores batendo com os da Calculadora
oficial — mais um motivo para o `DpsBuilder` ainda não preencher esse grupo.

### CNPJ alfanumérico

A IN RFB 2.229/2024 tornou as posições 1 a 12 do CNPJ **alfanuméricas a partir de julho de 2026**.
O DV segue módulo 11, mas cada caractere passa a valer `ASCII − 48` — `A` = 17, `B` = 18 e assim
por diante.

**O schema da NT 009 (out/2026) acompanhou**: `TSCNPJ` é `[0-9A-Z]{14}`, e os Ids e chaves
admitem letras nas posições da inscrição federal. `Documentos.cnpj(...)` aceita letras (normaliza
para maiúsculas), confere o DV alfanumérico e exige os dois últimos caracteres numéricos.

Duas ressalvas:

- **`TSChaveNFSe` está errado no XSD.** Diz `[0-9]{6}([0-9A-Z]{14})[0-9]{30}` — letras nas posições
  7 a 20, que é o padrão da chave da **NF-e** (cUF 2 + AAMM 4 + CNPJ 14). Na chave da NFS-e o CNPJ
  ocupa as posições **10 a 23** (cMun 7 + ambGer 1 + tpInsc 1 vêm antes), como o próprio `TSIdNFSe`
  (`NFS[0-9]{9}[0-9A-Z]{14}[0-9]{27}`) confirma. `Documentos.chaveAcesso` segue o leiaute da chave.
- **O DV da chave com letras não está confirmado.** `digitoVerificadorChaveAcesso` usa
  `ASCII − 48`, por analogia com o CNPJ e com a regra que a NF-e adotou; nenhuma chave real com
  letras foi conferida.

### Tabelas de domínio geradas dos anexos

Quatro catálogos, em `nfse-nacional-service/src/main/resources/tabelas/*.tsv`, lidos por
`TabelaTsv`:

| Classe | Domínio | Registros | Origem |
|---|---|---|---|
| `ListaServicoNacional` | `cTribNac` | 338 | ANEXO_B, aba `LISTA.SERV.NAC.` |
| `TabelaMunicipios` | `cLocEmi`, `cLocPrestacao`, `cLocIncid`, `cMun` | 5.570 + 1 | ANEXO_A, abas `TAB.MUN_IBGE` e `TAB.LOC.GERAL` |
| `TabelaPaises` | `cPais`, `cPaisPrestacao`, `cPaisResult` | 250 | ANEXO_A, aba `TAB.PAÍS_ISO2` |
| `TabelaNbs` | `cNBS` | 918 | ANEXO_B, aba `LISTA.NBS_v2.0` |
| `TabelaIndicadoresOperacao` | `cIndOp` | 26 | ANEXO_C |

Duas coisas que a geração revelou e que valem para quem for regenerar:

- **Células mescladas deslocam colunas.** Um leitor de xlsx que percorre as células em ordem, sem
  honrar o atributo `r` de referência, lê valores na coluna errada — foi assim que a UF de São
  Paulo saiu vazia e que o ANEXO_C pareceu inconsistente. Honre a referência e propague o valor
  das células mescladas verticalmente.
- **Os anexos misturam níveis de hierarquia com valores utilizáveis.** Na lista de serviços, 242
  das 581 linhas são cabeçalhos de item e subitem sem código. Na NBS, 292 das 1.210 são nós de
  agrupamento com 5, 6, 7 ou 8 dígitos — `1.0401.11` e `1.0401.11.1` agrupam `1.0401.11.11`,
  `.19` e `.20`, e só estas três valem como `cNBS`, que o XSD tipa como `[0-9]{9}`.
- **A "Sigla UF" do ANEXO_A vem preenchida em só 450 das 5.570 linhas.** `TabelaMunicipios` a
  **deriva dos 2 primeiros dígitos do código**, pelo padrão IBGE. Como é regra que não está no
  anexo, a derivação é conferida por três caminhos independentes na geração e nos testes: contra as
  450 linhas em que o anexo dá a sigla, contra o conjunto de prefixos presentes no anexo, e contra
  o enum `TSUF` do XSD — que traz exatamente as mesmas 27 siglas.

A geração é conferida: cada código bate com a decomposição que o anexo declara (item+subitem+
desdobro nos serviços, prefixo+sequência nos indicadores), não há duplicatas, e todo município de
uma UF compartilha o prefixo IBGE. Os testes em `TabelasDeDominioTest` e `ListaServicoNacionalTest`
repetem essas conferências sobre o `.tsv` publicado.

### Domínio do `cTribNac` — lista de serviços nacional

O código tem 6 dígitos em três níveis de 2: **item** e **subitem** da LC 116/2003, mais o
**desdobro nacional**, que é mais fino que a lei (o subitem 15.02 se abre em oito desdobros,
`150201`–`150208`, separando conta no país e no exterior, investimento, poupança…).

`ListaServicoNacional` expõe os **338 códigos** do ANEXO_B, gerados para
`nfse-nacional-service/src/main/resources/tabelas/servicos-nacionais-v1.01.tsv`. Das 581 linhas do
anexo, 242 são cabeçalhos de item e subitem, sem código, e ficam de fora — é a parte que confunde
quem abre a planilha.

Consequências no código:

- `DpsBuilder.servico(...)` **confere o código contra o catálogo**; o XSD só garante 6 dígitos;
- `DpsBuilder.codigoNbs(...)` confere o código contra a NBS 2.0, rejeitando nível de agrupamento;
- `IbsCbsBuilder` confere o `cIndOp` contra o ANEXO_C (`E0901`);
- `NfseDecisaoJudicialBuilder` preenche `xLocEmi`, `xLocPrestacao` e `xLocIncid` a partir de
  `TabelaMunicipios` quando o chamador não os informa;
- `NfseDecisaoJudicialBuilder` preenche o `xTribNac` a partir da tabela quando o chamador não o
  informa — no fluxo regular esse texto é gerado pela plataforma, e é o mesmo do anexo;
- o item **99** (`990101`, "Serviços sem a incidência de ISSQN e ICMS") é o único sem incidência, e
  aparece nas regras como o caso em que não há destaque de imposto.

**A tabela é dado versionado no jar e envelhece.** Quando sair anexo novo, regenere o `.tsv` a
partir dele; enquanto isso, `DpsBuilder.semValidacaoDeCatalogo()` desliga a conferência para não
barrar código legítimo. A mensagem de erro cita a versão embutida justamente para tornar isso óbvio.

### Grupo IBS/CBS na DPS

`IbsCbsBuilder`, acoplado por `DpsBuilder.ibsCbs(...)`. **É declaratório, não de cálculo:** leva
indicadores, destinatário, imóvel e situação/classificação tributária — nenhum valor de imposto.

Os valores apurados de IBS/CBS ficam no nível da **NFS-e** (`NFSe/infNFSe/IBSCBS`), e as regras
`E1530`+ exigem que batam com a Calculadora oficial. Mas quem os calcula é a plataforma, no fluxo
regular. Por isso o grupo da DPS se preenche **sem integrar com a Calculadora** — foi a constatação
que destravou a implementação. (No fluxo de decisão judicial isso mudaria, já que lá o contribuinte
envia a NFS-e inteira.)

Regras que o builder aplica:

- `indDest`, `CST` (3) e `cClassTrib` (6) são obrigatórios; `cIndOp` (6 dígitos, ANEXO_C) virou
  **opcional** na NT 009, e `E0859` o proíbe quando não há tributação de IBS/CBS;
- `E0938` — `cIndOp` 020101, 020201 e 020301 exigem unidade imobiliária;
- `E0910` — o grupo do destinatário só existe quando `indDest = 1`;
- `E0905` — `tpOper` 2 ou 3 exige NFS-e referenciada (fornecimento e pagamento em documentos
  distintos); até 99 referências;
- `E0964` — o grupo de tributação regular só quando a classificação o exigir.

Subgrupos, em classes próprias porque têm regras densas:

- **`ImovelIbsCbs`** — reestruturado pela NT 009: **município** obrigatório (`E0929`), grupo de
  **locação** opcional (`gLocacao`, só no subitem 99.03 — `E0936`/`E0937`) e até 99 **unidades
  imobiliárias**, cada uma com **CIB** (8 caracteres: 7 + DV, `E0933`) **ou endereço** — agora só
  nacional — e, na locação, ajustes da BC do IBS/CBS (`E0944`, `E0949`/`E0954`, `E0966`). As regras
  que dependem do `cTribNac` não são verificadas, porque o código vive no `DpsBuilder`.

**Ajuste de base de cálculo — `DocumentoAjusteBC`, fora do grupo IBS/CBS.** A NT 009 fundiu a
dedução do ISSQN (`vDedRed`) e o reembolso/repasse/ressarcimento do IBS/CBS (`gReeRepRes`) num só
grupo, `infDPS/valores/vAjusteBC`. Por isso o documento saiu do `IbsCbsBuilder` e entra por
`DpsBuilder.documentoAjusteBC(...)`; `deducaoPercentual`/`deducaoValor` viraram `pAjusteBCISSQN`/
`vAjusteBCISSQN`. O tipo distingue os dois casos: `2`, `5`–`9` e `99` só ajustam o ISSQN;
`101`–`105` e `199` são reembolsos (os antigos `01`–`04` e `99`). Regras do ANEXO_I de 20/10/2026:
`E0453`, `E0460`, `E0462`, `E0464`/`E0465`, `E0467`, `E0469`, `E0470`, `E0472`, `E0476`, `E0479` e
`E0492`/`E0498` (o fornecedor agora exige endereço). **Os códigos de rejeição mudaram** — `E0940`,
que era o tamanho da chave do reembolso, hoje é o percentual de copropriedade.

### Eventos de manifestação

Dos 16 eventos do leiaute, **6 são do contribuinte** e estão implementados em `TipoManifestacao`:
confirmação e rejeição do prestador (`202201`/`202205`), do tomador (`203202`/`203206`) e do
intermediário (`204203`/`204207`).

Ficam de fora, deliberadamente, a **Confirmação Tácita** (`205204`) e a **Anulação da Rejeição**
(`205208`): o AnexoII atribui a autoria delas ao município de incidência (`MIncid`), e expô-las
como se o contribuinte pudesse emiti-las levaria a rejeição por `E0813`. Os demais são de
município ou gerados pelo próprio sistema.

Particularidades do leiaute:

- cada manifestação é um **elemento próprio** (`e202201`, `e203202`, …), não um campo com código;
- `xDesc` é enumeração de **valor único por tipo** — vem do enum, nunca do chamador;
- confirmações só têm `xDesc`; rejeições acrescentam `cMotivo` (1, 2, 3, 4, 5 ou 9) e `xMotivo`,
  este obrigatório quando o código é 9 (`E1944`);
- cada manifestação é **única por autor** (`E1833`).

**Rota:** vai pelo mesmo `POST /nfse/{chave}/eventos` do cancelamento. O AnexoII marca o ambiente
receptor como "2 - ADN", mas o ADN não publica rota de recepção para contribuinte, e o manual do
contribuinte descreve essa rota da Sefin como "modelo genérico que permite o registro de eventos
originados a partir de: Emitentes da NFS-e; **Não Emitentes da NFS-e**; …" — que são exatamente os
autores das manifestações. Confirme na primeira manifestação real.

### Regras de negócio do evento (`ANEXO_II`, aba `RN EVENTO_PED.REG.EVENTO`)

58 regras com código de erro. As que tocam o `cancelar`:

| Código | Regra |
|---|---|
| `E1827` | O `Id` do pedido é `"PRE"` + chave (50) + código do evento (6) — **confirma a leitura do pattern**, sem `nPedRegEvento` |
| `E1802` | O `Id` do evento gerado pela Sefin é `"EVT"` + id do pedido (56) + `nSeqEvento` (3) |
| `E0812` | `CNPJAutor` deve ser o mesmo CNPJ do certificado que assina — **compara só o CNPJ base**. Por isso a fachada preenche o autor a partir do certificado: `CancelamentoRequest.de(...)` não recebe autor, e `comAutor(...)` só serve para outro estabelecimento do mesmo grupo |
| `E0815` | Idem para `CPFAutor` |
| `E0813`, `E0816` | O autor precisa ser um dos previstos na planilha "Tipo Eventos" para aquele evento |
| `E1831` | A NFS-e indicada precisa existir no ADN |
| `E0822` | Cancelamento fora do prazo limite parametrizado pelo município |
| `E0823` | Cancelamento acima do valor permitido pelo município |
| `E0824` | Cancelamento de nota sem tomador identificado, se o município exigir |
| `E0827` | Não cancela nota com evento de Tributos Recolhidos vinculado |
| `E0831` | O pedido deve ir ao ambiente que **gerou** a NFS-e |
| `E1843` | `dhEvento` não pode ser posterior ao recebimento pelo Sistema Nacional |
| `E1980`, `E1989`, `E1991` | Assinatura obrigatória, válida, e feita com o certificado do **emitente do pedido** |

`E0822`–`E0824` dependem de parametrização municipal e não são verificáveis do lado do cliente —
são as rejeições prováveis quando o cancelamento for exercitado pela primeira vez.

Do leiaute (`LEIAUTE EVENTO_PED.REG.EVENTO`): no `e101101`, `xMotivo` é **1-1 com 15 a 255
caracteres** (`TSMotivo`). `CancelamentoRequest` valida isso — antes aceitava nulo e curto, e uma
justificativa como "Outros" teria sido recusada no schema.

Ainda **não lidas**: `LEIAUTE DPS_NFS-e`, `RN EVENTOSxEVENTOS`, e os manuais de município e de
decisão judicial.

## Ambientes

Em `TipoAmbiente` (módulo client) — o enum carrega o código `tpAmb` **e** as URLs base, para que
endpoint não se espalhe pelo código.

| Ambiente | `tpAmb` | Sefin Nacional | ADN |
|---|---|---|---|
| `PRODUCAO_RESTRITA` | 2 | `https://sefin.producaorestrita.nfse.gov.br/API/SefinNacional` | `https://adn.producaorestrita.nfse.gov.br` |
| `PRODUCAO` | 1 | `https://sefin.nfse.gov.br/SefinNacional` | `https://adn.nfse.gov.br` |

## Schemas

Pacote oficial em `doc/schemas-oficiais/` (zips originais preservados em `doc/`). Os XSDs
efetivamente compilados estão em `nfse-nacional-schemas/src/main/resources/xsd/`.

### Leiaute da NT 009/2026 — o que está compilado

Desde 27/10/2026 a biblioteca compila o pacote **`NFSe-ESQUEMAS_XSD-PRODREST-v1.01-20261020`**, da
página "Documentação Técnica (Homologação/testes)" do gov.br — em `doc/schemas-oficiais/Schemas/
1.01-prodrest-20261020/`. O de fevereiro continua em `Schemas/1.01/`.

**Os arquivos continuam se chamando `v1.01`, mas o conteúdo mudou** (`tiposComplexos` e
`tiposSimples`; os demais são idênticos). Não confie no número da versão para saber qual pacote a
Sefin usa: a resposta é o `E1235`. Foi assim que a mudança apareceu — a DPS de fevereiro voltava
`invalid child element 'cLocEmi' … expected: 'cMotivoEmisTI, finNFSe'`.

Mudanças que afetam a biblioteca: `finNFSe` sai de `IBSCBS` e vira **obrigatório na raiz da
`infDPS`**; `vDedRed` + `gReeRepRes` → `valores/vAjusteBC`; `CST`/`cClassTrib` sobem para
`IBSCBS/valores/trib`, com `gIBSCBS` opcional; `cIndOp` opcional; `imovel` reestruturado;
CNPJ alfanumérico; `xNome` do tomador opcional; `TSDesc*` recusam texto só de espaços.

**Cronograma:** produção restrita desde ~21/10/2026; **produção em 01/12/2026**, segundo o
comunicado do CGNFS-e (até lá, produção segue o leiaute da NT 004 v2.0). Uma issue no GitHub dizia
03/11/2026 — vale o comunicado. A NT 009 tem mais releases: a próxima, prevista para 30/10/2026,
move `indDest`/`dest` para a raiz da `infDPS`. **Este pacote ainda os tem em `IBSCBS`.** Quando o
`E1235` voltar, compare com o pacote mais recente da mesma página.

O `xjc` recebe **só os schemas raiz** (`DPS`, `NFSe`, `evento`, `pedRegEvento`); os `tipos*` entram
via `xs:include` (mesmo targetNamespace). Listá-los explicitamente causa erro de definição
duplicada. `CNC_v1.00.xsd` fica de fora: é o leiaute de upload usado por municípios.

O XSD é a fonte de verdade para formatos — e é rigoroso. Casos já encontrados:

- **Campos monetários e percentuais (`TSDec*`) são `xs:string` com pattern, não `xs:decimal`.**
  Exigem exatamente duas casas decimais, proíbem zero à esquerda e sinal. Um `BigDecimal` de escala
  diferente de 2 passa batido em Java e só é rejeitado pela Sefin. Ver `DpsBuilder.decimal`.
- `TSDateTimeUTC` **não admite fração de segundos**; `ISO_OFFSET_DATE_TIME` a emite. Use o
  formatter `yyyy-MM-dd'T'HH:mm:ssXXX`.
- `TSIdDPS` é `DPS[0-9]{42}` = `"DPS"` + cMun (7) + tipo de inscrição (1: `1`=CPF, `2`=CNPJ) +
  inscrição federal (14, CPF com zeros à esquerda) + série (5) + nDPS (15). Série e número entram
  **zero-preenchidos no Id**, mas os elementos `serie` e `nDPS` do corpo **não** admitem zeros à
  esquerda (`TSNumDPS` é `[1-9][0-9]{0,14}`).
- `TSIdPedRegEvt` é `PRE[0-9]{56}` = `"PRE"` + chave (50) + tipo do evento (6). A documentação em
  prosa do XSD menciona também o `nPedRegEvento`, mas o pattern não o comporta — **o pattern manda**.
- `TCCodTribMun` tem **3 dígitos**, contra os 6 de `TSCodTribNac`. Fácil de trocar um pelo outro.
- `TSCodJustCanc` admite apenas `1`, `2` e `9`.
- `xDesc` do `e101101` é enumeração de valor único: `"Cancelamento de NFS-e"`.

**Prefixo de namespace é rejeição, não estética.** O `ANEXO_I`, aba `RN_RECEPCAO_DPS`, rejeita com
**`E1228` — "Uso de prefixo de namespace não permitido na área de dados descompactada"**. Um XML em
que a NFS-e saísse como `ns2:` seria recusado antes de chegar às regras de negócio. A NFS-e real de
produção confirma a forma esperada: elementos sem prefixo e `<Signature>` sem prefixo.
`DpsBuilderTest.nenhumElementoUsaPrefixoDeNamespace` guarda isso varrendo todas as tags.

Outras rejeições da mesma aba que o pipeline já trata: `E1225` falha na base64, `E1226` estrutura
descompactada mal formada, `E1229` XML fora de UTF-8, `E1235` falha no esquema XML. E as de
certificado: `E1200`, `E1203`, `E1205`–`E1209`.

**Prefixos de namespace precisam ser determinísticos.** Sem isso o JAXB alterna entre execuções:
ora a NFS-e é o namespace default, ora o XMLDSig assume o default e a NFS-e vira `ns2:`. O XMLDSig
entra no conjunto porque `TCDPS` e `TCPedRegEvt` declaram `ds:Signature`, mesmo antes de assinar.
Como a assinatura é calculada sobre a forma canônica, prefixo instável é defeito, não estética.
Todo marshalling passa por `MarshallerNfse`, que fixa NFS-e como default e XMLDSig como `ds`.

**Uma divergência deliberada em relação ao pacote oficial** (a outra, o pattern quebrado do
`TSSerieDPS` — `^0{0,4}\d{1,5}$`, com `^`/`$` literais —, foi corrigida pelo pacote da NT 009 para
`[0-9]{1,4}|[0-8][0-9]{4}`, e o filtro dos testes saiu):

1. O `xmldsig-core-schema.xsd` compilado teve o `DOCTYPE` removido. O original referencia a DTD
   externa `http://www.w3.org/2001/XMLSchema.dtd`, e o W3C limita acessos automatizados — cada
   build e cada validação buscavam essa DTD na rede e falhavam de forma intermitente com
   `src-resolve: ds:Signature`, erro que não aponta para a causa. As entidades do subconjunto
   interno não eram usadas. O arquivo íntegro está em `doc/schemas-oficiais/`.

Ao mexer em leiaute, valide contra o XSD num teste (`ValidacaoXsd.erros`) em vez de descobrir a
rejeição na Sefin. O helper resolve os imports pelo classpath — a resolução relativa padrão do
Xerces falha de forma intermitente quando o módulo de schemas entra como jar.

## Comandos

Java 21 e Maven 3.8+ (o Maven local já roda em 21 por padrão).

```bash
mvn clean install              # build completo (197 testes), instala em ~/.m2
TZ=UTC mvn clean install       # o servidor roda em UTC; rode assim antes de um deploy
mvn clean install -DskipTests  # só empacota
mvn clean compile              # regenera o JAXB do nfse-nacional-schemas

mvn test -pl nfse-nacional-service --also-make          # um módulo + dependências internas
mvn test -pl nfse-nacional-service -Dtest=EventoBuilderTest   # um teste
```

### Testes de integração

Terminam em `IT`, então o surefire **não** os executa no build normal — rode-os por nome. Batem em
ambiente real e exigem certificado A1.

```bash
mvn test -pl nfse-nacional-service -DfailIfNoTests=false \
    -Dsurefire.failIfNoSpecifiedTests=false -Dtest=ConsultarDpsIT \
    -Dnfse.cert.p12=/caminho/cert.pfx -Dnfse.cert.senha=senha
```

| Teste | Para que serve |
|---|---|
| `ConsultarDpsIT` | Só leitura. Confirma mTLS e roteamento; rode primeiro quando algo quebrar |
| `ConsultasSefinIT` | Só leitura. Varre todas as rotas de consulta e mapeia em qual host cada uma atende |
| `ParametrosMunicipaisIT` | Só leitura. Parâmetros municipais e consultas de evento |
| `DistribuicaoDFeIT` | Só leitura. Varredura por NSU, com pausas por causa do rate limit |
| `DanfseIT` | Só leitura. Sonda a API nacional de DANFSE, suspensa pela NT 008 — o PDF hoje é local |
| `BaixarSwaggerIT` | Só leitura. Atualiza `doc/openapi/` com as especificações oficiais |
| `ContratoSefinIT` | Diagnóstico: POST cru em `/nfse` despejando corpo enviado e recebido |
| `EmitirNfseIT` | Emissão pela fachada, com a DPS montada pelo `DpsBuilder` |
| `EmitirECancelarNfseIT` | Emitir → imprimir o XML → cancelar, com a DPS espelhando uma NFS-e real |
| `NfseServicosIT` | Ciclo ordenado: emitir → consultar por chave → consultar por DPS → cancelar |

`CertificadoDeTeste` resolve a credencial: usa `-Dnfse.cert.p12`/`-Dnfse.cert.senha` quando
informados; senão varre `certificado/` (gitignored) e **abre cada candidato** com o `senha.txt`
irmão, ficando com o primeiro que funcionar. Sem certificado utilizável, os testes são pulados via
`assumeTrue`. Nenhuma senha aparece em código ou log.

As operações de escrita dependem de o município emissor ser conveniado e de o CNPJ do certificado
estar habilitado nele — informe com `-Dnfse.municipio=<código IBGE>`. Consulte antes o
`/parametrizacao/{cMun}/convenio`: São Paulo (3550308), o default, responde
`aderenteAmbienteNacional: 1` e `aderenteEmissorNacional: 1` em produção restrita. O `NfseServicosIT`
pula os passos seguintes com a mensagem da rejeição em vez de falhar em cascata. `DpsDeTeste`
controla a numeração via `nfse-test-sequence.txt` (gitignored) para não repetir DPS entre execuções.

## Convenções de código

- **Número formatado é normalizado na entrada.** O XSD tipa CNPJ, CPF, código de município, CEP,
  telefone e chave de acesso como dígitos puros, mas o consumidor costuma tê-los pontuados.
  `Documentos` limpa, confere o tamanho, **valida o dígito verificador** de CPF (`E0913`), CNPJ
  (`E0911`) e chave de acesso (`E0042`, `E0907`), e **recusa valores com todos os dígitos iguais** —
  que o módulo 11 aceita por construção. Sempre com o nome do campo na mensagem. Ficam de fora inscrição municipal (`TSInscMun`) e NIF
  (`TSNIF`), que admitem letras.
- **Português em tudo que é domínio**: classes, métodos, Javadoc, mensagens de erro, comentários.
  Termos técnicos e siglas fiscais (DPS, NFS-e, `chaveAcesso`, `tpAmb`) ficam na forma original.
- **DTOs são records**, em `dto/request` e `dto/response`, com validação no construtor canônico.
  A fachada nunca expõe classes JAXB geradas na assinatura pública.
- **Comentário explica o "porquê"**, com o sintoma concreto que ele evita. Veja `XmlSigningService`:
  cada workaround documenta a rejeição que motivou o hack. Erros de assinatura são opacos; sem essa
  disciplina o próximo a mexer remove o código achando que é supérfluo.
- **Rejeição do fisco vira `NfseException`** nas operações de escrita (emitir, cancelar). Consultas
  **retornam o objeto de resposta** para o chamador interpretar.
- Contrato HTTP isolado no client: `NfseHttpException` carrega status + corpo bruto; o service o
  traduz em `NfseException`. Nada acima do client conhece HTTP.
- **Dado que está no certificado não se pede ao chamador.** `CertificadoDigital.inscricaoFederal()`
  lê o CNPJ/CPF do titular do `subjectAltName` (OIDs ICP-Brasil `2.16.76.1.3.3` e `2.16.76.1.3.1`,
  os mesmos das regras `E1986`/`E2026`), com o CN como último recurso. Verificado contra os dois
  certificados reais disponíveis.
- Testes de integração terminam em `IT`, usam `assumeTrue` para pular sem certificado, e imprimem
  a resposta em `System.out` para inspeção manual.

## Documentação oficial

- **`doc/openapi/*.json` — as especificações OpenAPI oficiais, e a fonte de verdade dos contratos.**
  A Swagger responde 403 a um navegador, mas **200 com o certificado A1 na conexão**: é a mesma
  exigência de mTLS das demais rotas. Rode `BaixarSwaggerIT` para atualizá-las. Foi por não
  dispor delas que este projeto inferiu contratos do PDF e errou as rotas de parametrização.
  A da Sefin fica num caminho fora do padrão: `<base>/swagger/docs/v1`.
- Índice: https://www.gov.br/nfse/pt-br/biblioteca/documentacao-tecnica
- `doc/manual-contribuintes-emissor-publico-api-v1.2-out2025.pdf` — rotas e semântica das APIs
- `doc/nt-008-se-cgnfse-danfse-v1.02-20260714.pdf` — especificação do DANFSe: tabela de
  coordenadas (item 2.4.5), modelo (Anexo I) e regras de cancelamento/substituição
- `doc/guia-utilizacao-apis-adn.pdf` — cobre só duas rotas, ambas em `<adn>/contribuintes`:
  `GET /NFSe/{chave}/Eventos` (implementada) e `GET /DFe/{NSU}`, a distribuição de DF-e, **não
  implementada**. Esta última aceita os parâmetros de query `cnpjConsulta` e `lote`, e o
  certificado da conexão pode ser de qualquer CNPJ com a mesma **raiz** do consultado.
- Swagger (exige certificado): `<base>/docs/index` (Sefin), `<base>/docs/index.html` (ADN)
- `doc/anexos/*.xlsx` — os 8 anexos oficiais (5 de leiaute, 3 de domínio). **Carregam as regras
  que o XSD não expressa e os códigos de rejeição**; consulte-os antes de supor comportamento.
  Os que interessam ao contribuinte:
  - `ANEXO_I-DPS_NFSe` — abas `RN_RECEPCAO_DPS` (validações de transmissão), `LEIAUTE DPS_NFS-e`,
    `RN DPS_NFS-e` (**654 linhas, 429 com código de erro, 426 delas rejeição**),
    `MUN.INCID_INFO.SERV.`;
  - `ANEXO_II-PedRegEvt_Evt` — `TIPO EVENTOS DE NFSe` com os 16 eventos, seus autores e
    visibilidade; `RN EVENTOSxEVENTOS`; leiaute e regras do pedido de registro;
  - `ANEXO_A` (municípios IBGE, países ISO2), `ANEXO_B` (lista de serviços nacional e NBS 2.0),
    `ANEXO_C` (indicadores IBS/CBS). O ANEXO_B já está **materializado em código** — ver abaixo.
  Os anexos III, IV e V são de município (CNC, ADN, painel administrativo).
- Todos os manuais e guias da página "documentação atual" estão em `doc/` — inclusive os de
  município e o de emissão por decisão judicial, ainda não lidos.
