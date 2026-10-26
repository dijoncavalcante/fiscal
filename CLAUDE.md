# CLAUDE.md

Guia para trabalhar neste repositório. Leia antes de qualquer alteração.

## O que é

**FISCAL - Organizador de Documentos PDF** (nome exibido na janela e no topo; `Strings.APP_TITLE`). Abre maximizado. App desktop **Windows, 100% offline**, para organizar os PDFs das contas
(Congregação e Manutenção) num pendrive: o usuário vê o PDF, arrasta para a categoria certa e o app renomeia/move para
a pasta do mês, com confirmação e **Desfazer**. Usuário final não é técnico: textos claros, nada de stack trace na UI.

Proibido no projeto: internet, APIs externas, nuvem, IA, autenticação, backend. Tudo fica na máquina.
O usuário conversa em **português (pt-BR)**; UI, comentários do código e mensagens de commit em português.

## Comandos

```bash
./gradlew run            # abre o app
./gradlew test           # testes (JUnit 4 + kotlin-test)
./gradlew compileKotlin  # checagem rápida de compilação
./gradlew packageMsi     # instalador em build/compose/binaries/main/msi (assina se fiscal.sign.* configurado)
```

- JDK 17 (toolchain). Gradle 8.14.3 (wrapper). Kotlin 2.3.0, Compose Multiplatform 1.10.0, Material3 1.10.0-alpha05,
  Koin 4.1.1, PDFBox 3.0.5, sqlite-jdbc 3.50.3.0, tess4j 5.13.0 (OCR). Versões em `gradle/libs.versions.toml`.
- Projeto de módulo único (`kotlin("jvm")` + plugin Compose), `mainClass = com.bragadev.fiscal.app.MainKt`.
- Ambiente: Windows, shell Git Bash e PowerShell. Não há Python nem `gh` CLI instalados.

## Arquitetura (Clean Architecture + MVVM)

Pacote `com.bragadev.fiscal`:

```
app/            Main.kt (janela, ícone, atalhos, Koin), App.kt (tema, navegação Assistente/Home/Settings), AppModule.kt (DI),
                WindowRestore (onde reabrir a janela)
domain/
  model/        Document, DocumentCategory, AccountType, NamingRule, AppSettings, FileOperation,
                OrganizationPlan, MonthFolderInfo/DetectedMonth, MonthChecklist, FileOperationError, Outcome
  rules/        Regras puras (sem IO) — onde mora a lógica de negócio, toda testada
  repository/   Interfaces (FileRepository, SettingsRepository, CategoryRepository, PdfRepository, ...)
  usecase/      Casos de uso (Plan/Organize/Undo, Scan, pastas, checklist, preview)
data/
  database/     SQLite via JDBC puro (Database + DAOs). Uma thread de IO para todo acesso.
  filesystem/   FileRepositoryImpl (Java NIO), FileErrorMapper, BackupStorageImpl
  pdf/          PdfRepositoryImpl (PDFBox)
  repository/   Implementações de Category/Document/OperationHistory
  settings/     SettingsRepositoryImpl
presentation/
  common/       ViewModel base, Strings (TODOS os textos da UI), ErrorMessages, DocumentChangeNotifier, UserMessage
  components/   AppIcons (ícones vetoriais), IconText/ExpandIcon, DialogKeys (Enter/Esc), FolderPathField, FolderPicker
                (JFileChooser), DragPayload, Panel, StatusColors...
  theme/        FiscalTheme: esquemas claro/escuro + FiscalColors (cores de situação) via LocalFiscalColors
  onboarding/   Assistente da primeira vez (OnboardingViewModel + OnboardingScreen)
  home/         Lado esquerdo (pasta de origem + lista) e HomeScreen (layout geral, snackbar, barra superior)
  preview/      Preview do PDF (zoom, páginas, ajustar)
  organizer/    Lado direito: mês em edição, árvore de categorias, diálogos de organizar
  monthfiles/   Ações sobre arquivos já no mês: renomear, retirar do mês, marcar pendência (MonthFilesViewModel)
  documentactions/ Botão direito na lista de documentos: renomear, copiar, excluir para a lixeira (DocumentActionsViewModel)
  navigator/    Seletor de mês: pasta raiz das contas → conta → ano de serviço → trimestre → mês (MonthNavigatorViewModel)
  pdftools/     Menu "PDF": converter JPEG/PNG em PDF e juntar PDFs (PdfToolsViewModel, PdfToolsDialog)
  settings/     Tela de configurações (inclui CutoffMonthSection: mês de corte, ExpenseSignaturesSection: assinaturas das despesas)
  closing/      "Concluir mês": conferência + relatório PDF (MonthClosingViewModel, MonthClosingDialogHost)
  history/      Tela "Histórico": todas as operações, busca e Desfazer em qualquer uma válida (HistoryViewModel)
  about/        Tela "Sobre": versão, data da compilação, commit, Java embutido, pastas (AboutInfo montado no AppModule)
```

Regras de camada:
- A apresentação **nunca** usa `java.nio.file.Files`; todo IO passa pelos repositórios.
- Regras de negócio ficam em `domain/rules` (objetos puros) e são chamadas pelos casos de uso, não pelos composables.
- Erros de domínio são `FileOperationError`; viram texto amigável em `presentation/common/ErrorMessages.kt`.
- Resultado de operações: `Outcome.Success` / `Outcome.Failure` (com `map`, `flatMap`, `getOrNull`).
- ViewModels são singletons Koin com `StateFlow` de estado imutável; IO sempre fora da thread da UI.
- Textos novos vão em `Strings.kt`; cores de situação em `StatusColors` (acompanha o tema; nunca cor fixa na tela).
- **Nunca use emoji/símbolos como ícone** (📄 ⚠ 🔒 ▾…): use `AppIcons` (paths do Material Icons) com `Icon`/`IconText`.
  Ícone novo = copiar o `pathData` do Material Icons para `AppIcons`.

## Layout da tela

- **Esquerda — pasta de origem:** qualquer pasta do PC, escolhida pelo lápis; botões "Abrir a pasta no Explorer" (
  `DesktopActions.openFolder`, só com a pasta encontrada) e atualizar. Lista os PDFs e as
  imagens JPEG/PNG (ícone de imagem, `Document.isImage`, `FileRepository.listDocuments`) da
  própria pasta (sem subpastas; ignora `._*.pdf` do macOS), com busca por nome e ordem "Mais recentes" (padrão, como
  "Data de modificação" do Explorer) ou "Nome" (salva em `document_sort`). Itens podem ser arrastados.
  **Botão direito** (ou o ⋮, visível com o mouse em cima ou no selecionado) → Renomear / Copiar / Excluir
  (`DocumentActionsViewModel`, pacote `presentation/documentactions/`); com a lista focada, F2 / Ctrl+C / Delete
  (`handleDocumentKey`, só para documento desta lista — não para um arquivo do mês aberto no preview).
  Renomear (`RenameDocumentUseCase`) fica na pasta, **mantém a extensão** (`FileNameRules.keepingExtension`) e nunca
  substitui. Copiar põe o arquivo na área de transferência como o Explorer (`DesktopActions.copyFilesToClipboard`,
  `javaFileListFlavor`): cola em pasta, WhatsApp, e-mail. **Enviar pelo WhatsApp** (também no menu do mês em
  edição, inclusive em mês fechado): o WhatsApp não aceita anexo vindo de outro programa, então copia o arquivo e
  abre o WhatsApp (`DesktopActions.openWhatsApp`: app do Windows se o link `whatsapp:` estiver registrado — consulta
  `reg query`, só leitura —, senão `web.whatsapp.com` no navegador, que é o que o usuário usa hoje); a mensagem
  diz para colar com Ctrl+V na conversa. Exceção combinada com o usuário à regra "sem internet": o FISCAL só pede
  ao Windows para abrir o app/navegador, nunca acessa a rede. **Excluir nunca apaga** (`DeleteDocumentUseCase`): move
  para
  a lixeira do FISCAL (`%APPDATA%\Fiscal\lixeira`, `BackupStorage.newTrashPath`), grava `OperationType.DELETE` e o
  Desfazer devolve (a Lixeira do Windows não serve: em pendrive ela apaga de vez). Depois de excluir, a seleção vai
  para o vizinho (`HomeViewModel.selectionAfterRemoving`, calculado antes, porque o WatchService pode atualizar antes).
- **Centro — preview** (PDFBox; imagens via `ImageThumbnail`). O PDF é lido para memória: o arquivo nunca fica bloqueado nem é alterado.
  **Rolagem contínua:** todas as páginas numa `LazyColumn` (uma abaixo da outra), roda do mouse / `ListScrollbar`
  percorrem o PDF inteiro; tamanho de cada página calculado antes (`PreviewPage`), imagem desenhada só para as
  visíveis ± 1 (`onVisiblePagesChanged`) e descartada além de ± 2 (memória). "Página X de N" = página que ocupa mais
  espaço na tela; `<`/`>` só rolam até a página. Nos modos de ajuste cada página tem o seu zoom. Zoom maior que o
  painel: barra horizontal embaixo (`HorizontalListScrollbar`, sempre reservando o espaço para o viewport não mudar),
  Shift + roda ou arrastar a página com o mouse (mãozinha). Teste com PDF real e
  roda do mouse simulada: `PdfPreviewScrollTest`.
- **Direita — mês em edição:** mês em destaque ("Junho de 2026" + "Liberado para edição" / cadeado "Somente leitura"), conta detectada,
  caminho completo **somente leitura** (texto copiável, pasta do mês em negrito) e lápis para trocar. Abaixo, grupos
  recolhíveis por conta com as categorias, cada uma com o selo **Já existe** (e os arquivos encontrados) ou **Faltando**;
  "Outros" no fim de cada conta; grupo "Arquivos sem número de categoria" (recolhido por padrão).
- **Arquivos já no mês:** clicar no nome abre no preview central; o arquivo aberto no preview fica destacado
  (mesmo fundo da lista da esquerda, `MonthFileRow.isSelected`); o lápis renomeia. Despesas/Outros: modo "Número e
  descrição" (campo Número aceita `3`, `3.1`, `3.2`… via `DescribedSequenceNaming.parseIndex`; vazio = próximo livre;
  número repetido só gera aviso `sameNumberFiles`) ou "Nome completo" (livre, `PlanRenameUseCase`); demais categorias:
  nome completo. Nunca sobrescreve. **Botão direito** (abre onde o mouse está e mostra no preview) ou ⋮ → mesmo
  menu da lista de documentos (Renomear / Copiar / Excluir, via `DocumentActionsViewModel`; Renomear de arquivo com
  categoria usa o diálogo do mês, sem categoria usa `RenameDocumentUseCase`; mês fechado: só Copiar) + "Retirar do
  mês" (volta para a pasta de origem, com
  Desfazer; nunca apaga) e "Marcar/Remover pendência" (nota com ícone de alerta; a categoria vira "Com pendência" e não
  conta no resumo). Diálogos de organizar/renomear/retirar/confirmar mostram o PDF ao lado (`PreviewDialog`).
- **Seletor de mês (navigator):** no topo da lista do mês em edição (recolhível). "Pasta raiz das contas" (`months_root`;
  se vazia, deduzida da pasta do mês via `MonthFolderParser.accountsRootOf`); botões de conta, ano de serviço (padrão: o
  do mês aberto ou o mais recente) e trimestres (mais recente primeiro) com os meses; um clique troca a pasta do mês
  (`ChangeMonthFolderUseCase`). Árvore montada por `BrowseMonthFoldersUseCase` (ignora pastas sem mês). O "Caminho
  completo da pasta do mês" (somente leitura + lápis) fica dentro desse bloco, logo abaixo da pasta raiz. Os dois
  campos têm o ícone de pasta que abre o caminho no Explorer (`FolderPathField(onOpenInExplorer = …)`).
- **Menu PDF (barra superior):** "Converter JPEG para PDF" (uma página A4 por imagem, orientação conforme a
  imagem; **fotos de celular giram sozinhas** pela orientação EXIF — `ExifOrientation`, somada ao botão Girar no
  `PdfToolsRepositoryImpl`; o preview (Skia) já mostra a foto em pé, por isso Girar é relativo ao que se vê) e "Juntar PDFs" (ordem da lista; já inclui o documento selecionado). Abre como **painel no
  lugar do mês em edição** (`PdfToolsPanel`, não modal): o usuário clica num arquivo à esquerda para ver no preview e
  arrasta para o painel (aceita também arquivos do Explorer; tipo errado e duplicados são avisados). Ordem por
  segurar a linha e arrastar (`ReorderableColumn`: gesto medido na lista a partir do ponto do clique, linhas com
  `key` e altura fixa; testado com mouse simulado em `ReorderableColumnTest`) ou setas Subir/Descer; clicar no item mostra no preview; "Fechar"
  volta ao mês. Salva por padrão na pasta de origem (`PdfOutputResolver`: valida nome, recusa mês fechado,
  nunca sobrescreve — usa "(2)"). `PdfToolsRepositoryImpl` monta o PDF na memória e grava com `CREATE_NEW` no fim;
  originais só são lidos. O PDF criado é selecionado e aparece na lista.
- **Atualização automática:** as duas pastas são observadas (`FileRepository.watch`, WatchService) e a tela se
  atualiza quando algo muda no Explorer.
- Arrastar um PDF (da lista ou do Windows Explorer) para uma categoria, ou selecionar e clicar na categoria, abre a
  proposta: nome atual, novo nome, mês, destino → **Cancelar / Renomear / Renomear e Mover**.

## Estrutura real do pendrive (essencial)

```
D:\Modelo\jw\pendriver\                      (também existe D:\Modelo\jw-teste\pendriver para testes do usuário)
  CONTAS CONGREGAÇÃO\ANO DE SERVIÇO 2025-2026\4. TRIMESTRE Jun-Jul-Ago\1. JUNHO\
      1. Folha de Contas (S-26).pdf
      3. Despesa - xxx.pdf / 3.1 Despesa - yyy.pdf
      5. Remessa Betel.pdf / 5.1 Comprovante Remessa.pdf
      8. Extrato Bancário.pdf ...
  CONTAS MANUTENÇÃO\ANO DE SERVIÇO 2025-2026\...\1. Junho\
```

Os PDFs ficam **soltos dentro da pasta do mês**, com o número no começo do nome. Não há subpastas por categoria.
Os nomes de pasta variam muito (`1. JUNHO`, `10.Outubro`, `2.  Outubro`, `AGOSTO`, `3. Março 2024`,
`2.TRIMESTRE - Dez_Jan_Fev`). Antes de mudar o parser, rode-o contra as pastas reais (somente leitura).

## Regras de negócio (domain/rules)

- **Categorias** (`DefaultCategories`): Congregação 1–9 (5.1 filha de 5) e **10. Outros** (`congregacao.outros`);
  Manutenção 1–5 e **6. Outros** (`manutencao.outros`). O "Outros" geral (`AccountType.OUTROS`, `isCatchAll`,
  `1. Outros.pdf`) só vale onde a conta não tem categoria própria com o mesmo nome (`CategoryHierarchy.catchAllFor`) —
  hoje nenhuma das duas contas o usa; fica para pastas sem conta identificada. `optional = true` (Outros) mostra quantidade em vez de
  "Faltando" e não conta no resumo. Ids com prefixo da conta (`congregacao.despesas`, `manutencao.despesas`).
  `DocumentCategory.label` = `"8. Extrato Bancário"` / `"5.1 Comprovante Remessa"` (número composto sem ponto extra).
  São gravadas no SQLite com **upsert** a cada início (mudanças no catálogo chegam a bancos existentes).
- **Nomes (`CategoryNaming`, por `NamingRule`):**
  - `CATEGORY_NAME` → `label + ".pdf"` (`8. Extrato Bancário.pdf`).
  - `SEQUENTIAL` ("Outros") → `N. Outros.pdf`, N = maior existente + 1 (`SequentialNaming`). Nunca reaproveita lacunas.
  - `DESCRIBED_SEQUENCE` também nos Outros das contas: `10. Outros - xxx.pdf`, `10.1 Outros - yyy.pdf`... (Congregação)
    e `6. Outros - xxx.pdf`, `6.1 Outros - yyy.pdf`... (Manutenção).
  - `DESCRIBED_SEQUENCE` ("3. Despesas", nas duas contas) → `3. Despesa - <descrição>.pdf`, depois `3.1 …`, `3.2 …`
    (`DescribedSequenceNaming`). Número = maior existente + 1, contando qualquer arquivo que comece com `3`/`3.x`
    (inclusive `3 Despesa - …` e `3. Despesas.pdf`). Descrição digitada no diálogo (sugerida do nome original, sem
    repetir "Despesa"); vazia → `DescriptionRequired`. Despesa já na pasta de destino **mantém o número**.
    A palavra no arquivo vem de `fileBaseName` ("Despesa").
- **Mês e conta pelo caminho (`MonthFolderParser`):** acha o segmento de mês mais profundo; ano no próprio nome ou na
  pasta-mãe com ano. "ANO DE SERVIÇO 2025-2026": set–dez = 2025, jan–ago = 2026; **"5. Trimestre" dentro de 2025-2026
  pertence ao ano seguinte** (set/2026). Conta: segmento contendo CONGREGACAO / MANUTENCAO (sem acentos).
- **Donativos da Manutenção** ("2. Donativos das Congregações") também são `DESCRIBED_SEQUENCE` com `fileBaseName`
  "Donativo": `2. Donativo - Japiim.pdf`, `2.1 Donativo - Trinta e Um de Março.pdf`... Arquivos numerados à mão
  (`2.1 Donativo Japiim.pdf`, sem hífen) contam na sequência.
- **Bloqueio (`EditablePeriodPolicy`):** meses **anteriores ao mês de corte** são somente leitura. O corte fica nas
  Configurações (`first_editable_month`, padrão `AppSettings.DEFAULT_FIRST_EDITABLE_MONTH = 2026-06`) e a política o lê
  **a cada verificação** (`EditablePeriodPolicy { settings.value.firstEditableMonth }` no Koin), então mudar lá vale na
  hora; organizer e navigator observam o valor para atualizar cadeados. Trocar exige confirmação que diz quais meses
  travam/destravam (`CutoffMonthSection`). Nos testes, `EditablePeriodPolicy(YearMonth)` fixa o mês. Destino precisa ser mês identificado e liberado; origem em mês bloqueado
  também é recusada (até para "Renomear"). Pastas fora de qualquer mês (ex.: Downloads) podem ser origem, nunca destino.
  Verificado ao **propor, executar e desfazer**.
- **Categoria × conta:** só categorias da conta da pasta do mês (+ Outros) aparecem e são aceitas
  (`CategoryNotInMonthAccount`).
- **Checklist do mês (`MonthChecklistBuilder`):** associa PDFs da pasta do mês às categorias pelo número inicial do nome.
  `5.1` → 5.1; `3.1` (sem categoria 3.1) → 3; `N. Outros.pdf` é testado antes do número; sem número → `unmatchedFiles`.
  Usar só categorias da conta (números se repetem entre contas).
- **Duplicidade (`ConflictPolicy`):** `ASK` pergunta (Substituir / Cópia numerada / Cancelar); `AUTO_NUMBERED_COPY`
  sempre cria `Nome (2).pdf` e nunca substitui; `FORBID` bloqueia. Conflito é rechecado na execução.
- **Nunca sobrescrever:** `Files.move` sempre **sem** `REPLACE_EXISTING`. "Substituir" move o arquivo existente para
  `%APPDATA%\Fiscal\backup` e o Desfazer restaura os dois. Nomes comparados sem diferenciar maiúsculas (Windows).
- **Histórico:** toda movimentação/renomeação passa por `RecordedFileMover` (grava `file_operations` e leva a
  pendência junto). Organizar (`OrganizeDocumentUseCase`), renomear (`PlanRenameUseCase` / `PlanOrganizationUseCase`
  com descrição), retirar do mês (`RemoveFromMonthUseCase`) e renomear/excluir da lista de documentos
  (`RenameDocumentUseCase`, `DeleteDocumentUseCase`) usam o mesmo caminho e podem ser desfeitos.
- **Desfazer (`UndoOperationUseCase`):** valida antes (operação existe, não desfeita, arquivo no destino, origem livre,
  backup presente, meses liberados); nada é alterado se não for seguro.
- **Renomear** mantém na pasta atual; **Renomear e Mover** leva para a pasta do mês. A proposta (com destino) é sempre
  mostrada; as opções "Confirmar antes de mover/renomear" só controlam o passo extra "Confirmar operação?".

## Conferência das despesas (Congregação e Manutenção)

- `congregacao.despesas` e `manutencao.despesas` (`ExpenseDocumentRules.categoryFor`). Regras puras em
  `ExpenseDocumentRules.check(content, ExpenseRequirements)`; pedidas pelo usuário em 20/10/2026. Sempre: **comprovante
  Pix/TED** e **assinatura do responsável da conta**. Manutenção: sempre **cupom, nota fiscal (DANFE) ou recibo** com
  o **mesmo valor do total**. Congregação: **Caso A** — o **nome do arquivo** tem um termo de isenção
  (`ExpenseExemptionRules`; nos PDFs reais os termos nunca estão no texto) → sem cupom e sem conferir valor, o "certo"
  diz "Sem cupom: despesa isenta ("Resolução" no nome do arquivo)"; **Caso B** — sem termo → igual à Manutenção.
- **Termos de isenção** (`AppSettings.DEFAULT_EXPENSE_EXEMPT_TERMS`, editáveis em Configurações, chave
  `expense_exempt_terms`, um por linha): Ônibus para o congresso/a assembleia, Resolução, Objetivos específicos,
  Donativo, Reembolso, Depósito de publicações. Vale se **todas as palavras principais** do termo estão no nome, em
  qualquer ordem, sem acento, singular/plural ("PUBLICAÇÕES" = "publicação"), ignorando "de/para/a…"; comparação
  exata (sem tolerância de OCR): "Impressão convite do congresso" **não** é isenta. Nas 28 despesas reais de
  2025-2026: 21 isentas, 7 com cupom (`ExpenseExemptionRulesTest`).
- **Responsáveis** (Configurações → Assinaturas das despesas, `ExpenseSignaturesViewModel`/`ExpenseSignaturesSection`):
  nome completo por conta em `expense_signer_congregacao` / `expense_signer_manutencao` (**nenhum nome no código**;
  sem nome = pendência "Falta assinatura do responsável (configure …)"). "Ler de um PDF assinado…" usa
  `DocumentContentReader.signerNames` (CN do certificado, sem o ":CPF"; "ANDREY ALVES DA COSTA" → "Andrey Alves da
  Costa"). Trocar um nome já gravado pede confirmação. Imagem do selo **só de referência**: copiada como PNG para
  `%APPDATA%\Fiscal\assinaturas\<congregacao|manutencao|extratos|servo-de-contas>.png` (`SignatureImageStorage`, por
  `SignatureRole`), nunca no repositório nem usada na conferência. A mesma seção tem o bloco "Extratos bancários (as
  duas contas)" (`SignatureRole.BANK_STATEMENTS`, chave `statement_signer`; ver "Conferência dos extratos") e o
  bloco "Servo de contas" (`SignatureRole.ACCOUNTS_SERVANT`, chave `accounts_servant_signer`, pedido em 26/10/2026):
  nome + imagem guardados, ainda sem nenhuma conferência que exija essa assinatura.
- `ExpenseRequirements.key` (responsável + termo de isenção) é gravado em `expense_checks.requirements_key`: mudar
  responsável ou termos invalida o resultado guardado (o `OrganizerViewModel` observa essas Configurações e mostra
  os documentos como "não conferido" até o próximo "Checar pendências"). A chave leva também o mês em edição.
- **Data da transferência (pedida em 26/10/2026, `EXPENSE_RULES_VERSION` 6):** todo comprovante Pix/TED do arquivo
  precisa ter a data no mês em edição (`ExpenseRequirements.month`, `ExpenseRequirement.PAYMENT_MONTH`,
  `ExpenseCheck.paymentDate` — este não é gravado no banco, só o item em `missing`). Data lida por `TransferDates`:
  "Data da operação: 18/09/2026" (next; OCR "Date da operaeha: 18072026"), "Data do pagamento …", "15 SET 2026 -
  16:56:42" (Nubank) e, sem nenhuma, o identificador do Pix (E2E, aaaaMMddHHmm em UTC, −3 h). **Não valem** a data
  do selo gov.br ("Data: 12/08/2026 07:38:13-0300", dia da assinatura) nem a expiração do QR Code. PDF com texto e
  data de outro mês = "confira se a despesa é deste mês"; foto ou E2E = "confira à mão (foto)"; não lida = "confira
  à mão se é de <mês>". Nas 123 despesas reais de 2025-2026: 1 de outro mês (Manutenção set/2025 "3.6 Compra de
  CABO HDMI", paga em 01/10) e 7 sem data legível (fotos ruins; de jun/2026 em diante só a "3. Despesa - Compra de
  argamassa" de jul/2026, "Data di opersedat (7007/2026") (`PaymentDateRulesTest`).
- Leitura (`PdfContentReaderImpl`): texto do PDFBox por página; página sem texto (cupom fotografado) passa pelo **OCR
  local** (`TesseractOcrEngine`, tess4j com o modelo `eng` que vem no jar — nada é baixado; carrega antes
  `msvcp140`/`vcruntime140` do Java embutido). Páginas com texto + imagem só vão ao OCR enquanto faltar algo
  (`hasEnoughText` = cupom + comprovante achados **e valor resolvido**). Exceção combinada com o usuário à regra
  "sem IA": OCR offline. O texto normal do OCR usa o modo 6 (bloco único, padrão do tess4j): as regras foram
  calibradas com ele — não trocar (trocar para o 3 fez sumir cupons e assinaturas nas despesas reais).
- **Digitalização com texto embutido** (app de scanner: "Ualor a Pagar", "valor Tota) k$!"): página com texto e uma
  imagem ≥ 1 milhão de pixels (`PHOTO_PIXELS`) entra em `ocrPages` — o texto dela também é de OCR, então valor que
  não bate vira "confira à mão" e nunca "troque o documento" (caso real: "3.2 Despesa da visita - Sidney Anselmo",
  vários cupons na mesma página; a regra não soma cupons dentro de uma página).
- **Palavras com posição** (`DocumentContent.layouts`, `PageLayout`/`PageWord`): páginas de texto trazem as palavras
  do PDFBox (`WordCollector`); fotos ganham **novas leituras** do OCR só enquanto o valor não fechou, nesta ordem:
  200 DPI modo 3, 300 DPI modo 11 (palavras soltas), 300 DPI modo 4 (no máximo 3 por foto; `readWords` devolve
  posição + confiança). Uma despesa difícil leva ~5–10 s na primeira vez (resultado fica guardado).
- **Total na tabela (`TableTotalFinder`):** título (TOTAL, VALOR TOTAL, VALOR TOTAL DA NOTA, VALOR A PAGAR) com o valor
  à direita na linha ou **embaixo, na mesma coluna** (NFC-e: "TOTAL (R$)" em cima, "50,00" embaixo, com o "1" de Itens
  no caminho). Não valem: SubTotal, "Total dos produtos/de itens/tributos" e a coluna TOTAL da lista de itens
  (linha com Código/Unitário/Qtd, ou logo abaixo dela). `confirmedBySum`: SubTotal − Desconto + Frete + Acréscimo
  = Total na mesma linha. Caso real: "3.5 Despesa - Compra de refil para bebedouro" (`TableTotalFinderTest`).
- Assinatura = nome do responsável no **certificado** da assinatura digital (`CertificateFactory` lê o PKCS#7; gov.br
  usa tamanho indefinido `30 80`, não cortar) ou o **selo gov.br completo** no texto/OCR: "Documento assinado
  digitalmente" + nome (até 12 palavras depois) + "validar" (de validar.iti.gov.br, até 40 palavras depois); a data é
  ignorada. O nome solto **não** vale: ele aparece como recebedor do Pix. A integridade criptográfica não é verificada.
- Termos procurados palavra a palavra com tolerância a erros do OCR (Levenshtein). Recibo vale, exceto em página de
  orçamento ou no próprio comprovante do banco ("Recibo de transferência"). Orçamento não conta como cupom.
- **Valor (`AmountProblem`):** o "Valor:" do comprovante (soma, se vários; aceita `1.234,56`, `125.00` e `1,051.49`)
  precisa ser igual a um **total** do cupom/nota (Total, Valor total, Valor a pagar — tolerante a OCR: "VALOR & PAGAR",
  "Valor a Pag" —, Valor do pedido/da venda/do documento) ou à soma dos totais de cupons diferentes. Preço de item
  e "Valor bruto" não contam. Resultados: `MISMATCH` (texto: "diferente do cupom/nota … troque o documento errado");
  `UNCERTAIN_READING` (não bate, mas veio de foto: o OCR troca dígitos e perde totais de tabela — "confira à mão");
  `PAYMENT_UNREADABLE` / `TOTAL_UNREADABLE` (valor não lido — "confira à mão"). Todos viram pendência. Cupom e Pix
  na mesma página: o "Valor:" do Pix é tirado antes de procurar o total (e a posição não é usada). "R$ 166 92"
  (vírgula perdida) vira 166,92. `DocumentContent.ocrPages` diz quais páginas vieram do OCR.
- **Segurança:** total de foto vale para **bater** com confiança do OCR ≥ 40 (ou conta da tabela fechando) e para
  **afirmar** diferença só ≥ 70; abaixo disso = "Total não foi lido". Valor do Pix ilegível no texto é procurado nas
  novas leituras só se a página for só comprovante. Quando bate, `ExpenseCheck.amountEvidence` diz de onde veio
  ("R$ 50,00 abaixo de "TOTAL (R$)", página 1 (foto, conta da tabela confere)"): gravado em `amount_evidence` e
  mostrado no "certo" verde. Teste nas despesas reais: trocar o "Valor:" do Pix por +7,13 nunca pode dar OK.
- `isAutomaticNote` reconhece "Falta … assinatura de <qualquer nome>" e os textos de versões anteriores (ex.: "Falta
  cupom ou nota fiscal", "assinatura de Elves Lincoln"), para serem trocados quando regras ou responsável mudam.
- Mudou a regra? Aumente `EXPENSE_RULES_VERSION`: resultados antigos em `expense_checks` (`rules_version`) são
  conferidos de novo.
- `CheckExpensesUseCase` (no `OrganizerViewModel`, em segundo plano): incompleta → pendência "Falta …" (texto de
  `issueNote`); corrigida → a pendência automática sai (`isAutomaticNote`). Pendência escrita pelo usuário nunca é
  mexida. Resultado guardado em `expense_checks` por tamanho + data: não repete OCR, e pendência automática removida
  pelo usuário não volta enquanto o arquivo não mudar. Falha passageira (arquivo em uso, OCR) = `NOT_CHECKED`, sem
  pendência. Tela: "certo" verde ao lado do nome (`MonthFileRow.expenseStatus`) ou indicador enquanto confere.
- **Quando confere (pedido em 26/10/2026):** só (1) o documento que acabou de ser organizado no mês (arrastado ou
  clicado na categoria; `OrganizerViewModel.pendingChecks`, parâmetro `only` do caso de uso) e (2) o botão
  **"Checar pendências"** no cabeçalho do mês (`onCheckIssuesRequested`, `only = null`: lê só o que não tem
  resultado guardado válido). Abrir o app, trocar de mês, mudanças no Explorer ou nas Configurações **não leem PDF**:
  mostram o resultado guardado ou `UNCHECKED` (círculo vazio, "Ainda não conferido"). O botão aparece só em conta com
  folha/despesas/extrato (`OrganizerUiState.canCheckIssues`) e, ao terminar, avisa quantos ficaram com pendência.

## Conferência dos extratos bancários (pedida em 22/10/2026)

- "8. Extrato Bancário" (`congregacao.extrato_bancario`) e "5. Extrato Bancário" (`manutencao.extrato_bancario`);
  "9. Extrato Betel" não é conferido. Regras puras em `BankStatementRules.check(content, StatementRequirements)`,
  pelo mesmo `CheckExpensesUseCase` (resultado em `expense_checks` com `STATEMENT_RULES_VERSION`, pendência
  automática, "certo" verde com conta + período + quem assinou; `isBankStatement` muda os textos da linha).
- **Conta certa** (`BankStatementRules.accountFor`, dados passados pelo usuário): Manutenção = next (237 - Banco
  Bradesco), Agência 3943, Conta 286256-5, DANILO CAVALCANTE BRAGA, Conta Poupança; Congregação = Nubank (Nu
  Pagamentos/Nu Financeira, CNPJs 18.236.120/0001-58 e 30.680.829/0001-43), Agência 0001, Conta 15077760-0, Filipe
  da Silva Costa. Agência, conta, titular e tipo são procurados **sozinhos na 1ª página** (o next embaralha:
  "Agência: | Conta: | Tipo: 3943 286256-5"); o banco em qualquer página (o CNPJ do Nubank só está na 4ª). O
  Nubank da Congregação cita a conta do next numa transferência: sem "next" no texto, não vale como extrato dele.
- **Mês**: período "Movimentação entre: 01/08/2026 e 31/08/2026" (next) ou "02 DE AGOSTO DE 2026 a 31 DE AGOSTO DE
  2026" (Nubank; no texto do PDF o "a" vem antes). Vale o mês com **mais dias** no período
  (`StatementPeriod.mainMonth`: o Nubank vai de 01/03 a 01/04, de 09/01 a 31/01, de 01/12 a 08/01). Nome do mês
  exato ou o único mais parecido (JULHO≠JUNHO, DEZEMBRO≠SETEMBRO); OCR: "O1 DE MARGO" vale.
- **Assinatura**: quem assina os extratos (Configurações, `statement_signer`, um nome para as duas contas),
  certificado ou selo gov.br completo (`ExpenseDocumentRules.isSignedBy`, data ignorada).
- Digitalizado (OCR) nunca diz "troque": conta não confirmada / período não lido / mês diferente = "confira à mão".
  Validado nos extratos reais de 2025-2026: todos reconhecem a conta; os anteriores a ago/2026 não têm a assinatura
  do Filipe (jul/2026 da Manutenção está assinado pelo Elves) e ficam com a pendência.

## Conferência da Folha de Contas (pedida em 22/10/2026)

- "1. Folha de Contas" (`congregacao.folha_de_contas`, `manutencao.folha_de_contas`), regras puras em
  `AccountSheetRules.check(content, account, month)`, pelo mesmo `CheckExpensesUseCase` (`SHEET_RULES_VERSION`,
  pendência automática, "certo" verde com nome da conta + mês; `CheckedDocument.ACCOUNT_SHEET` muda os textos).
- Na 1ª página: "FOLHA DE CONTAS (S-26)", o nome da conta — Congregação "Trinta e Um de Março - Manaus AM (68361)",
  Manutenção "Comissão de Funcionamento do Salão do Reino" (**completo**: a folha da Congregação cita "COMISSÃO DE
  FUNCIONAMENTO" numa saída) — e o "<mês> de <ano>" logo depois do nome (impressão do JW Hub: "... (68361) agosto de
  2026"), igual ao mês em edição. Nomes de mês lidos por `Months.number` (o mesmo dos extratos).
- Folha da outra conta = "Folha de Contas da conta da Manutenção (…), não da conta da Congregação: troque pela folha
  certa" (caso real: a pasta de junho/2026 da Congregação tem a folha da Manutenção). Digitalizada (OCR) = "confira à
  mão". Nas 24 folhas reais de 2025-2026 (6 digitalizadas) só essa fica com pendência (`AccountSheetRulesTest`).

## Conferência do Relatório Mensal (pedida em 26/10/2026)

- "7. Relatório Mensal" (`congregacao.relatorio_mensal`) e "4. Relatório Mensal" (`manutencao.relatorio_mensal`),
  regras puras em `MonthlyReportRules.check(content, account, month)`, pelo mesmo `CheckExpensesUseCase`
  (`REPORT_RULES_VERSION`, pendência automática, "certo" verde com título + mês; `CheckedDocument.MONTHLY_REPORT`).
- Na 1ª página: o título do formulário — Congregação "RELATÓRIO MENSAL DE CONTAS DA CONGREGAÇÃO (S-30)", Manutenção
  "RELATÓRIO MENSAL DE CONTAS DA COMISSÃO DE FUNCIONAMENTO DO SALÃO DO REINO (S-44)" (no PDF quebra a linha antes de
  "REINO") — e o primeiro "<mês> de <ano>" da página igual ao mês em edição (S-30: depois do nome da congregação;
  S-44: sozinho, depois das instruções; a data da impressão do navegador é só números).
- Relatório da outra conta = "Relatório Mensal da conta da Manutenção (S-44), não da conta da Congregação (S-30):
  troque pelo relatório certo". Digitalizado (OCR) = "confira à mão". Os 24 relatórios reais de 2025-2026 (5
  digitalizados) passam sem pendência (`MonthlyReportRulesTest`).

## Conferência dos Donativos das Congregações (pedida em 26/10/2026)

- "2. Donativos das Congregações" da Manutenção (`manutencao.donativos_congregacoes`), regras puras em
  `DonationRules`. Diferente das outras, confere **a categoria inteira junta** (`CheckExpensesUseCase.checkDonations`):
  chave do resultado guardado = mês + nome/tamanho/data de **todos** os donativos, então mudar ou renomear um confere
  os dois de novo (`DONATION_RULES_VERSION`, `CheckedDocument.DONATION`).
- Precisa de **dois** comprovantes de transferência, um de cada congregação, e a ordem segue a **data e hora** da
  transferência: a primeira é o "2.", a segunda o "2.1" (`subNumber`; "2 Donativo" = "2."). Cada um: data no mês em
  edição ("10 SET 2026 - 10:36:07"), valor `DonationRules.AMOUNT` (R$ 800,00), destino Danilo Cavalcante Braga
  (CPF ***.638.182-**, Bradesco, Agência 3943, Conta 286256-5, Conta poupança) e origem da congregação: Japiim =
  Walter Santana Mota do Rosário (Nu Pagamentos, 0001, 18938180-7, CPF ***.250.412-**); Trinta e Um de Março =
  Filipe da Silva Costa (Nu Pagamentos, 0001, 15077760-0, CPF ***.369.732-**). A congregação é reconhecida pelo nome
  **ou** pela conta da origem; o nome do arquivo citando a outra congregação pede para renomear.
- Os comprovantes reais são **todos digitalizados** (OCR): números com um dígito de tolerância e letras no lugar de
  dígitos ("1B2", "O0oL", "Tl MAI"); "Agência" só vale com o número logo depois (o "0001" está também no CNPJ). Na
  foto o CPF não gera pendência se nome e conta da mesma pessoa foram lidos; o que não foi lido = "confira à mão".
- Nos reais de 2025-2026: março a agosto/2026 passam; setembro/2026 Japiim (foto borrada, "Valor Ag 600,00") fica
  com "confira à mão"; meses antigos acusam a numeração 2.1/2.2 e o valor de R$ 700,00 daquela época.

## Fechamento do mês e histórico

- **Concluir mês** (botão no cabeçalho do mês em edição, aparece com mês identificado — também em meses fechados):
  `ReviewMonthUseCase` (só lê) monta `MonthReview` com `MonthReviewBuilder` (árvore da conta + checklist + pendências):
  cada categoria com arquivos, `missing` (obrigatória sem arquivo; "Outros" é opcional), `withIssues`, `isComplete`.
  A janela mostra o resumo e gera o PDF (`GenerateMonthReportUseCase` → `PdfToolsRepository.monthReport` →
  `data/pdf/MonthReportLayout`: Helvetica, quebra de linha e de página, rodapé "Página X de N"; caracteres que a fonte
  não tem viram "?"). Salva por padrão na **pasta de origem** (não na do mês, para não virar "arquivo sem número");
  `PdfOutputResolver` valida, nunca sobrescreve e recusa mês fechado. Depois: "Abrir relatório" / "Abrir pasta".
  Gerar com coisas faltando é permitido (o relatório mostra o que falta).
- **Histórico** (botão na barra superior): `ListHistoryUseCase` = todas as operações (mais recente primeiro) +
  `UndoOperationUseCase.check` em cada uma (motivo se não puder: já desfeita, arquivo mexido depois, nome antigo
  ocupado, backup sumido, mês fechado). Desfazer qualquer operação válida, com confirmação; se o arquivo foi
  renomeado/movido de novo depois, só a operação mais nova pode ser desfeita (o caminho não bate). Busca sem
  maiúsculas/acentos por nomes, pastas e data (`HistoryViewModel.filter`).

## Acabamento visual

- **Ícone do app:** gerado por `tools/IconGenerator.java` (`java tools/IconGenerator.java` na raiz). Saída:
  `src/main/resources/icons/fiscal-<16..256>.png` (janela, barra de tarefas e Alt+Tab: `window.iconImages` no `Main`) e
  `packaging/fiscal.ico` (instalador, atalho e `Fiscal.exe`: `nativeDistributions.windows.iconFile`).
- **Ícones da interface:** só `AppIcons` (vetores do Material Icons), nunca emoji. `IconText` = ícone + texto na mesma
  cor; `ExpandIcon` = setinha dos grupos recolhíveis.
- **Copiar caminho/nome:** `CopyButton` (ícone vira "certo" verde por 1,5 s). Todo "Abrir no Explorer" tem ao lado o
  copiar caminho (`FolderPathField` com `onOpenInExplorer`, cabeçalho de Documentos, relatório do mês, pasta de dados
  no Sobre); nomes de arquivo nos diálogos usam `LabeledValue(copyDescription = Strings.COPY_NAME)` e os campos de
  nome têm `trailingIcon = { CopyButton(...) }` (copia o texto do campo, sem a extensão do sufixo).
- **Recolher painéis:** botão « / » no título (`Panel(collapse = PanelCollapse(side) {…})`): Documentos recolhe para a
  esquerda, Mês em edição para a direita, virando uma faixa de 40 dp com o título na vertical (`CollapsedPanel`;
  clicar nela expande) e o preview ganha o espaço. Estado em `rememberSaveable` no `Workspace` (não é gravado no
  banco). Só no layout largo; com as ferramentas de PDF abertas o painel da direita fica sempre aberto.
  Teste: `CollapsiblePanelTest`.
- **Barras de rolagem:** Documentos e Mês em edição usam `ListScrollbar` (ao lado da lista, não por cima, para não
  cobrir o ⋮); testadas arrastando com mouse simulado em `CopyAndScrollTest`.
- **Tema:** Configurações → Aparência: "Igual ao Windows" (padrão, `isSystemInDarkTheme`), "Claro" ou "Escuro"
  (`theme_mode`). `FiscalTheme` troca o esquema do Material3 e as `FiscalColors` (`StatusColors.*` lê do tema). As páginas
  do PDF continuam brancas no tema escuro; só o fundo atrás delas (`PreviewBackdrop`) muda.
- **Atalhos:** `KeyboardShortcuts` recebe, pelo `onKeyEvent` da janela, só as teclas que o elemento focado não usou
  (Ctrl+Z num campo de texto desfaz a digitação). A `HomeScreen` registra Ctrl+Z (desfazer última) e F5 (atualizar)
  com `RegisterShortcuts`; com diálogo aberto ficam parados. Diálogos usam `Modifier.dialogKeys(onConfirm, onDismiss)`:
  Enter = botão principal (`null` quando desabilitado), Esc = cancelar; o diálogo pega o foco ao abrir. Exceção:
  "Apagar backups antigos" não confirma com Enter (não tem volta). `PreviewDialog` exige `onConfirm`.
- **Assistente da primeira vez** (`onboarding/`): 3 passos — pasta raiz das contas, pasta de origem, mês (usa
  `MonthTreePicker`, a mesma árvore do seletor de mês). Abre sozinho só se `AppSettings.needsOnboarding` (falta pasta de
  origem ou mês e `onboarding_done` = false); decisão tomada uma vez ao abrir. "Concluir" e "Pular assistente" gravam
  `onboarding_done`. Reabre em Configurações → Pastas → "Abrir o assistente de configuração". As pastas são escolhidas
  pelos ViewModels da tela principal (nada duplicado).
- **Janela:** ao fechar, grava `window_bounds` (`x,y,largura,altura,maximizada`, em dp) com o último tamanho/posição
  "normal" (não maximizada). `WindowRestore.start` só reaproveita a posição se a barra de título aparecer em algum
  monitor; senão centraliza. Primeira vez = maximizada. As configurações são carregadas no `main` antes da janela; se o
  banco falhar, abre com os padrões e mostra "Algo deu errado".

## Distribuição (detalhes em docs/DISTRIBUICAO.md)

- **Versão única** em `version` do `build.gradle.kts` (o usuário definiu 2.0.0). A task `generateBuildInfo` grava
  `fiscal-build.properties` (versão, data, commit) nos resources; `AppInfo.VERSION/BUILD_DATE/COMMIT/FULL_VERSION`
  leem dali ("dev" sem o arquivo). Vai para o MSI (`packageVersion`), tela Sobre, log e diagnóstico. Aumentar a cada
  instalador entregue (formato MSI: números, MAIOR ≤ 255).
- **MSI**: Java embutido (`modules("java.sql", "jdk.unsupported")`), pasta "FISCAL" no menu Iniciar + atalho na área de
  trabalho, `upgradeUuid` fixo (atualiza por cima; versão menor é recusada). O app nunca grava na própria pasta
  (testado), então desinstalar remove tudo; `%APPDATA%\Fiscal` (dados do usuário) é mantido de propósito.
- **Assinatura**: `signMsi`/`signExe` rodam depois de `packageMsi`/`packageExe` (jsign no classpath do buildscript),
  com `fiscal.sign.*` do `gradle.properties` do usuário. Sem configuração: aviso e MSI sem assinatura. Certificado
  autoassinado é recusado (o jsign omite certificados raiz da assinatura → Windows não acha o editor).
- Para testar a assinatura sem certificado real: CA de teste + certificado emitido por ela, só em arquivos `.p12`
  (keytool `-gencert`), passados com `-Pfiscal.sign.*` na linha de comando; nunca instalar na loja de certificados
  do Windows. `Get-AuthenticodeSignature` deve mostrar o assinante e "raiz não confiável".
- `Fiscal.exe` (launcher do jpackage) abre um processo filho, que é o dono da janela. A pasta do app gerada é
  somente leitura.
- **Sobre** (Configurações → Sobre o FISCAL, ou clicar em "Versão x" na barra de status): versão, data, commit, Java
  embutido, Windows, pasta de dados; copiar informações (diagnóstico) e abrir pastas.

## Confiabilidade

- **Uma cópia só** (`data/instance/SingleInstance`): trava `%APPDATA%\Fiscal\fiscal.lock`; a segunda cópia grava
  `abrir-janela.sinal` e fecha; a primeira (WatchService) traz a janela para a frente. O log só é iniciado depois da trava.
- **Log** (`data/logging/AppLog`): `%APPDATA%\Fiscal\logs\fiscal-N.log` (5 × 1 MB, `java.util.logging`). Registra
  início/fim, movimentações (`SafeFileMover`), erros de arquivo (`FileErrorMapper`) e erros inesperados.
- **Erros inesperados:** `UnexpectedErrors.report` (do `CoroutineExceptionHandler` do `ViewModel`, do
  `LocalWindowExceptionHandlerFactory` e do `Thread.setDefaultUncaughtExceptionHandler`) abre `UnexpectedErrorDialog`
  ("Algo deu errado" + copiar diagnóstico / abrir pasta de logs). `DiagnosticsRepositoryImpl` monta o texto (versão
  `AppInfo`, sistema, erro, final do log). Nada é enviado.
- **Mover entre unidades** (`SafeFileMover`): mesma unidade = `Files.move`; unidades diferentes = copia para
  `~fiscal-<uuid>.parcial` no destino, `force`, confere tamanho + SHA-256, renomeia e só então apaga o original; se não
  conseguir apagar o original, desfaz a cópia. Erro de verificação = `CopyVerificationFailed`.
- **Backups** (Configurações → Dados e segurança, `DataSafetyViewModel`): limpeza automática ao abrir **só se o usuário
  ligar** (`auto_clean_backups`, `backup_retention_days` 30/90/180/365) e "Apagar agora…" com confirmação. Vale para
  `backup\` e `lixeira\`; a idade vem da data no começo do nome (`yyyyMMdd-HHmmss-…`, `BackupStorageImpl`), não da
  data de modificação (mover mantém a do PDF original).
- **Exportar/importar dados:** exporta com `VACUUM INTO` (nunca sobrescreve); importar confere se é banco do FISCAL,
  guarda como `fiscal-importado.db` e `DataMaintenanceRepositoryImpl.applyPendingImport` (no `main`, antes de abrir o
  banco) guarda o atual como `fiscal-antes-da-importacao-<data>.db` e coloca o importado no lugar.

## Dados locais

- `%APPDATA%\Fiscal\fiscal.db` (SQLite), `%APPDATA%\Fiscal\backup\`, `%APPDATA%\Fiscal\lixeira\` (excluídos) e
  `%APPDATA%\Fiscal\assinaturas\` (imagens de referência do selo, opcionais).
- Tabelas: `documents`, `categories` (+ `file_base_name`, `optional`), `file_operations` (+ `backup_path`, `undone`), `settings`,
  `file_flags` (pendências por pasta + nome, em minúsculas), `expense_checks` (conferência das despesas).
- Colunas novas: adicionar em `Schema.statements` **e** em `Schema.addedColumns` (migração por `ALTER TABLE` se faltar).
- Settings: `source_folder`, `month_folder`, `months_root`, `duplicate_policy`, `confirm_before_move`,
  `confirm_before_rename`, `document_sort`, `auto_clean_backups`, `backup_retention_days`, `theme_mode`,
  `onboarding_done`, `window_bounds`, `first_editable_month`, `expense_signer_congregacao`, `expense_signer_manutencao`,
  `expense_exempt_terms`, `statement_signer`, `accounts_servant_signer` (`root_path` é chave legada, migrada para
  `source_folder`). Chave ausente
  ou valor
  inválido = padrão. Único caminho absoluto no código: `SettingsRepositoryImpl.DEFAULT_SUGGESTED_FOLDER =
  D:\Modelo\jw\pendriver` (sugestão inicial, usada só se existir).

## Testes

- `src/test/kotlin`: regras puras (`*RulesTest`, `MonthFolderParserTest`, `MonthChecklistBuilderTest`,
  `DescribedSequenceNamingTest`...), fluxo completo com arquivos reais em `TemporaryFolder` (`OrganizeAndUndoTest`,
  usando `FileRepositoryImpl` de verdade + fakes de `fakes/Fakes.kt`) e `FileRepositoryImplTest`.
- Interface com teclado/mouse simulados (`runComposeUiTest`): `ReorderableColumnTest` (arrastar) e `KeyboardTest`
  (Enter/Esc nos diálogos, Ctrl+Z/F5). Configurações salvas: `SettingsPersistenceTest`; janela: `WindowRestoreTest`.
- Concluir mês com PDF real lido de volta (`PDFTextStripper`): `MonthClosingTest`; histórico: `HistoryTest`; mês de
  corte: `EditablePeriodPolicyTest`; EXIF: `ExifOrientationTest` (`jpegWithOrientation` cria foto "de celular").
- Despesas da Congregação: `CongregacaoExpenseRulesTest` (Caso A/B, selo completo, responsável),
  `ExpenseExemptionRulesTest`
  (os 28 nomes reais) e `ExpenseSignaturesTest` (imagem local, responsável, tela com teclado/mouse simulados).
- Despesas: `ExpenseDocumentRulesTest` (textos reais de PDF e OCR), `CheckExpensesUseCaseTest`,
  `PdfContentReaderImplTest`
  (OCR de verdade numa página-foto gerada) e `ExpenseCheckDaoTest`. Extratos: `BankStatementRulesTest` (textos reais
  do next e do Nubank, PDF e OCR) e os casos de extrato em `CheckExpensesUseCaseTest`. Folha de Contas:
  `AccountSheetRulesTest` e o caso da folha em `CheckExpensesUseCaseTest`. Relatório Mensal: `MonthlyReportRulesTest`
  e o caso do relatório em `CheckExpensesUseCaseTest`. Donativos: `DonationRulesTest` (OCR real dos comprovantes) e
  o caso dos donativos (conferidos juntos, renomear confere de novo) em `CheckExpensesUseCaseTest`. Data da
  transferência das despesas: `PaymentDateRulesTest` e o caso da despesa paga em outro mês em
  `CheckExpensesUseCaseTest`.
- Telas que não dá para clicar no app (a janela Java não aceita automação): renderize num `runComposeUiTest`
  descartável e salve `onAllNodes(isRoot())[i].captureToImage()` em PNG para conferir (diálogos são outra raiz).
- Teste visual sem mexer nos dados do usuário: rode com `APPDATA` apontando para uma pasta temporária **de caminho
  curto** (ex.: `%TEMP%\claude\fad`; caminhos longos passam do limite de 260 caracteres e o SQLite não abre).
- Use nomes reais das pastas do pendrive nos testes. Crie PDFs falsos com `Path.createFakePdf(...)` (assinatura `%PDF-`).
- Todo comportamento novo de regra precisa de teste. Rode `./gradlew test` antes de commitar.

## Cuidados importantes ao trabalhar aqui

- **O usuário costuma estar com o app aberto** e ele usa o **mesmo banco** (`%APPDATA%\Fiscal\fiscal.db`).
  - Nunca feche janelas do app pelo título ("FISCAL - Organizador de Documentos PDF") — feche só o processo que você
    mesmo iniciou (pelo PID).
  - Não altere as configurações dele no banco. Se precisar para um teste visual, anote o valor anterior e restaure.
- **Nunca altere, mova ou apague arquivos do pendrive** (`D:\Modelo\...`). Ler/listar para validar regras é ok.
- Para ver a tela: `./gradlew run` em segundo plano e capturar só a janela do seu processo (computer-use não consegue
  controlar a janela Java; não há como clicar/arrastar nela — diga isso ao relatar).
- Gradle não reexecuta testes cujo único input mudado é variável de ambiente: use `--rerun` nesses casos.
- `Database` usa uma thread única de IO; não abra conexões JDBC paralelas no app.
- Edições por script: no perl com delimitador `|`, um `\|` no padrão vira "ou" vazio e o texto é inserido no
  **início do arquivo**. Prefira a ferramenta de edição; depois de scripts, confira que todo `.kt` começa com `package`.
- APIs experimentais do Compose (drag and drop, tooltip) exigem `@OptIn`. DnD: `dragAndDropTarget` / `dragAndDropSource`
  e `event.awtTransferable`; arraste interno usa texto com prefixo `fiscal-document:` (não arrastar como arquivo).

## Git / GitHub

- Branch `main`. Remote `origin = git@github.com:dijoncavalcante/fiscal.git` (SSH funciona).
- O repositório no GitHub ainda não foi criado. **Não lembrar o usuário disso** nem pedir para criá-lo; só fazer
  `git push -u origin main` se ele pedir explicitamente.
- Commits em português, descrevendo o porquê; terminar com a linha de coautoria indicada na sessão.

## Documentos

- `README.md` — como usar e comandos.
- `docs/MVP.md` — fases do MVP, regras da pasta do mês e decisões sobre conflitos de regras do prompt original.
- `docs/DISTRIBUICAO.md` — o que o instalador faz, versão, assinatura de código (certificado) e checklist de entrega.
