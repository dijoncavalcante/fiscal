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
./gradlew packageMsi     # instalador em build/compose/binaries/main/msi
```

- JDK 17 (toolchain). Gradle 8.14.3 (wrapper). Kotlin 2.3.0, Compose Multiplatform 1.10.0, Material3 1.10.0-alpha05,
  Koin 4.1.1, PDFBox 3.0.5, sqlite-jdbc 3.50.3.0. Versões em `gradle/libs.versions.toml`.
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
  navigator/    Seletor de mês: pasta raiz das contas → conta → ano de serviço → trimestre → mês (MonthNavigatorViewModel)
  pdftools/     Menu "PDF": converter JPEG/PNG em PDF e juntar PDFs (PdfToolsViewModel, PdfToolsDialog)
  settings/     Tela de configurações (inclui CutoffMonthSection: mês de corte)
  closing/      "Concluir mês": conferência + relatório PDF (MonthClosingViewModel, MonthClosingDialogHost)
  history/      Tela "Histórico": todas as operações, busca e Desfazer em qualquer uma válida (HistoryViewModel)
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

- **Esquerda — pasta de origem:** qualquer pasta do PC, escolhida pelo lápis; botão de atualizar. Lista os PDFs e as
  imagens JPEG/PNG (ícone de imagem, `Document.isImage`, `FileRepository.listDocuments`) da
  própria pasta (sem subpastas; ignora `._*.pdf` do macOS), com busca por nome e ordem "Mais recentes" (padrão, como
  "Data de modificação" do Explorer) ou "Nome" (salva em `document_sort`). Itens podem ser arrastados.
- **Centro — preview** (PDFBox; imagens via `ImageThumbnail`). O PDF é lido para memória: o arquivo nunca fica bloqueado nem é alterado.
- **Direita — mês em edição:** mês em destaque ("Junho de 2026" + "Liberado para edição" / cadeado "Somente leitura"), conta detectada,
  caminho completo **somente leitura** (texto copiável, pasta do mês em negrito) e lápis para trocar. Abaixo, grupos
  recolhíveis por conta com as categorias, cada uma com o selo **Já existe** (e os arquivos encontrados) ou **Faltando**;
  "Outros" no fim de cada conta; grupo "Arquivos sem número de categoria" (recolhido por padrão).
- **Arquivos já no mês:** clicar no nome abre no preview central; o arquivo aberto no preview fica destacado
  (mesmo fundo da lista da esquerda, `MonthFileRow.isSelected`); o lápis renomeia. Despesas/Outros: modo "Número e
  descrição" (campo Número aceita `3`, `3.1`, `3.2`… via `DescribedSequenceNaming.parseIndex`; vazio = próximo livre;
  número repetido só gera aviso `sameNumberFiles`) ou "Nome completo" (livre, `PlanRenameUseCase`); demais categorias:
  nome completo. Nunca sobrescreve; menu ⋮ → "Retirar do mês" (volta para a pasta de origem, com
  Desfazer; nunca apaga) e "Marcar/Remover pendência" (nota com ícone de alerta; a categoria vira "Com pendência" e não
  conta no resumo). Diálogos de organizar/renomear/retirar/confirmar mostram o PDF ao lado (`PreviewDialog`).
- **Seletor de mês (navigator):** no topo da lista do mês em edição (recolhível). "Pasta raiz das contas" (`months_root`;
  se vazia, deduzida da pasta do mês via `MonthFolderParser.accountsRootOf`); botões de conta, ano de serviço (padrão: o
  do mês aberto ou o mais recente) e trimestres (mais recente primeiro) com os meses; um clique troca a pasta do mês
  (`ChangeMonthFolderUseCase`). Árvore montada por `BrowseMonthFoldersUseCase` (ignora pastas sem mês). O "Caminho
  completo da pasta do mês" (somente leitura + lápis) fica dentro desse bloco, logo abaixo da pasta raiz.
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
  com descrição) e retirar do mês (`RemoveFromMonthUseCase`) usam o mesmo caminho e podem ser desfeitos.
- **Desfazer (`UndoOperationUseCase`):** valida antes (operação existe, não desfeita, arquivo no destino, origem livre,
  backup presente, meses liberados); nada é alterado se não for seguro.
- **Renomear** mantém na pasta atual; **Renomear e Mover** leva para a pasta do mês. A proposta (com destino) é sempre
  mostrada; as opções "Confirmar antes de mover/renomear" só controlam o passo extra "Confirmar operação?".

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
  ligar** (`auto_clean_backups`, `backup_retention_days` 30/90/180/365) e "Apagar agora…" com confirmação.
- **Exportar/importar dados:** exporta com `VACUUM INTO` (nunca sobrescreve); importar confere se é banco do FISCAL,
  guarda como `fiscal-importado.db` e `DataMaintenanceRepositoryImpl.applyPendingImport` (no `main`, antes de abrir o
  banco) guarda o atual como `fiscal-antes-da-importacao-<data>.db` e coloca o importado no lugar.

## Dados locais

- `%APPDATA%\Fiscal\fiscal.db` (SQLite) e `%APPDATA%\Fiscal\backup\`.
- Tabelas: `documents`, `categories` (+ `file_base_name`, `optional`), `file_operations` (+ `backup_path`, `undone`), `settings`,
  `file_flags` (pendências por pasta + nome, em minúsculas).
- Colunas novas: adicionar em `Schema.statements` **e** em `Schema.addedColumns` (migração por `ALTER TABLE` se faltar).
- Settings: `source_folder`, `month_folder`, `months_root`, `duplicate_policy`, `confirm_before_move`,
  `confirm_before_rename`, `document_sort`, `auto_clean_backups`, `backup_retention_days`, `theme_mode`,
  `onboarding_done`, `window_bounds`, `first_editable_month` (`root_path` é chave legada, migrada para `source_folder`). Chave ausente ou valor
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
