# CLAUDE.md

Guia para trabalhar neste repositório. Leia antes de qualquer alteração.

## O que é

**Fiscal — Organizador de Documentos PDF.** App desktop **Windows, 100% offline**, para organizar os PDFs das contas
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
app/            Main.kt (janela, Koin), App.kt (tema, navegação Home/Settings), AppModule.kt (DI)
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
  components/   FolderPathField, PencilIcon, FolderPicker (JFileChooser), DragPayload, Panel, StatusColors...
  home/         Lado esquerdo (pasta de origem + lista) e HomeScreen (layout geral, snackbar, barra superior)
  preview/      Preview do PDF (zoom, páginas, ajustar)
  organizer/    Lado direito: mês em edição, árvore de categorias, diálogos de organizar
  monthfiles/   Ações sobre arquivos já no mês: renomear, retirar do mês, marcar pendência (MonthFilesViewModel)
  navigator/    Seletor de mês: pasta raiz das contas → conta → ano de serviço → trimestre → mês (MonthNavigatorViewModel)
  pdftools/     Menu "PDF ▾": converter JPEG/PNG em PDF e juntar PDFs (PdfToolsViewModel, PdfToolsDialog)
  settings/     Tela de configurações
```

Regras de camada:
- A apresentação **nunca** usa `java.nio.file.Files`; todo IO passa pelos repositórios.
- Regras de negócio ficam em `domain/rules` (objetos puros) e são chamadas pelos casos de uso, não pelos composables.
- Erros de domínio são `FileOperationError`; viram texto amigável em `presentation/common/ErrorMessages.kt`.
- Resultado de operações: `Outcome.Success` / `Outcome.Failure` (com `map`, `flatMap`, `getOrNull`).
- ViewModels são singletons Koin com `StateFlow` de estado imutável; IO sempre fora da thread da UI.
- Textos novos vão em `Strings.kt`; cores de status em `StatusColors`.

## Layout da tela

- **Esquerda — pasta de origem:** qualquer pasta do PC, escolhida pelo lápis; botão 🔄 atualiza. Lista os PDFs e as
  imagens JPEG/PNG (🖼, `Document.isImage`, `FileRepository.listDocuments`) da
  própria pasta (sem subpastas; ignora `._*.pdf` do macOS), com busca por nome e ordem "Mais recentes" (padrão, como
  "Data de modificação" do Explorer) ou "Nome" (salva em `document_sort`). Itens podem ser arrastados.
- **Centro — preview** (PDFBox; imagens via `ImageThumbnail`). O PDF é lido para memória: o arquivo nunca fica bloqueado nem é alterado.
- **Direita — mês em edição:** mês em destaque ("Junho de 2026 ✓ Liberado" / "🔒 Somente leitura"), conta detectada,
  caminho completo **somente leitura** (texto copiável, pasta do mês em negrito) e lápis para trocar. Abaixo, grupos
  recolhíveis por conta com as categorias, cada uma com **✓ Já existe** (e os arquivos encontrados) ou **○ Faltando**;
  "Outros" no fim de cada conta; grupo "Arquivos sem número de categoria" (recolhido por padrão).
- **Arquivos já no mês:** clicar no nome abre no preview central; o arquivo aberto no preview fica destacado
  (mesmo fundo da lista da esquerda, `MonthFileRow.isSelected`); ✏️ renomeia. Despesas/Outros: modo "Número e
  descrição" (campo Número aceita `3`, `3.1`, `3.2`… via `DescribedSequenceNaming.parseIndex`; vazio = próximo livre;
  número repetido só gera aviso `sameNumberFiles`) ou "Nome completo" (livre, `PlanRenameUseCase`); demais categorias:
  nome completo. Nunca sobrescreve; menu ⋮ → "Retirar do mês" (volta para a pasta de origem, com
  Desfazer; nunca apaga) e "Marcar/Remover pendência" (nota ⚠ no arquivo; a categoria vira "⚠ Com pendência" e não
  conta no resumo). Diálogos de organizar/renomear/retirar/confirmar mostram o PDF ao lado (`PreviewDialog`).
- **Seletor de mês (navigator):** no topo da lista do mês em edição (recolhível). "Pasta raiz das contas" (`months_root`;
  se vazia, deduzida da pasta do mês via `MonthFolderParser.accountsRootOf`); botões de conta, ano de serviço (padrão: o
  do mês aberto ou o mais recente) e trimestres (mais recente primeiro) com os meses; um clique troca a pasta do mês
  (`ChangeMonthFolderUseCase`). Árvore montada por `BrowseMonthFoldersUseCase` (ignora pastas sem mês). O "Caminho
  completo da pasta do mês" (somente leitura + lápis) fica dentro desse bloco, logo abaixo da pasta raiz.
- **Menu PDF ▾ (barra superior):** "Converter JPEG para PDF" (uma página A4 por imagem, orientação conforme a
  imagem, ↻ para girar) e "Juntar PDFs" (ordem da lista; já inclui o documento selecionado). Abre como **painel no
  lugar do mês em edição** (`PdfToolsPanel`, não modal): o usuário clica num arquivo à esquerda para ver no preview e
  arrasta para o painel (aceita também arquivos do Explorer; tipo errado e duplicados são avisados). Ordem por
  arrastar a alça ⠿ (`ReorderableColumn`, linhas de altura fixa) ou ↑ ↓; clicar no item mostra no preview; "Fechar"
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
- **Bloqueio (`EditablePeriodPolicy`, `FIRST_EDITABLE_MONTH = 2026-06`):** meses **anteriores a junho/2026** são somente
  leitura; junho/2026 em diante é editável. Destino precisa ser mês identificado e liberado; origem em mês bloqueado
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

## Dados locais

- `%APPDATA%\Fiscal\fiscal.db` (SQLite) e `%APPDATA%\Fiscal\backup\`.
- Tabelas: `documents`, `categories` (+ `file_base_name`, `optional`), `file_operations` (+ `backup_path`, `undone`), `settings`,
  `file_flags` (pendências por pasta + nome, em minúsculas).
- Colunas novas: adicionar em `Schema.statements` **e** em `Schema.addedColumns` (migração por `ALTER TABLE` se faltar).
- Settings: `source_folder`, `month_folder`, `duplicate_policy`, `confirm_before_move`, `confirm_before_rename`
  (`root_path` é chave legada, migrada para `source_folder`). Único caminho absoluto no código:
  `SettingsRepositoryImpl.DEFAULT_SUGGESTED_FOLDER = D:\Modelo\jw\pendriver` (sugestão inicial, usada só se existir).

## Testes

- `src/test/kotlin`: regras puras (`*RulesTest`, `MonthFolderParserTest`, `MonthChecklistBuilderTest`,
  `DescribedSequenceNamingTest`...), fluxo completo com arquivos reais em `TemporaryFolder` (`OrganizeAndUndoTest`,
  usando `FileRepositoryImpl` de verdade + fakes de `fakes/Fakes.kt`) e `FileRepositoryImplTest`.
- Use nomes reais das pastas do pendrive nos testes. Crie PDFs falsos com `Path.createFakePdf(...)` (assinatura `%PDF-`).
- Todo comportamento novo de regra precisa de teste. Rode `./gradlew test` antes de commitar.

## Cuidados importantes ao trabalhar aqui

- **O usuário costuma estar com o app aberto** e ele usa o **mesmo banco** (`%APPDATA%\Fiscal\fiscal.db`).
  - Nunca feche janelas "Organizador de Documentos" pelo título — feche só o processo que você mesmo iniciou (pelo PID).
  - Não altere as configurações dele no banco. Se precisar para um teste visual, anote o valor anterior e restaure.
- **Nunca altere, mova ou apague arquivos do pendrive** (`D:\Modelo\...`). Ler/listar para validar regras é ok.
- Para ver a tela: `./gradlew run` em segundo plano e capturar só a janela do seu processo (computer-use não consegue
  controlar a janela Java; não há como clicar/arrastar nela — diga isso ao relatar).
- Gradle não reexecuta testes cujo único input mudado é variável de ambiente: use `--rerun` nesses casos.
- `Database` usa uma thread única de IO; não abra conexões JDBC paralelas no app.
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
