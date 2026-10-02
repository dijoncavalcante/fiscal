# Escopo do MVP e decisões de regras

Organizador de documentos PDF **100% offline** para Windows. Sem IA, sem internet, sem serviços externos.

## Fases

### Fase 1 — MVP (implementada)

| # | Entrega | Onde |
|---|---------|------|
| 1 | **Esquerda:** escolher qualquer pasta do computador (lápis) e listar os PDFs dela | `ChangeSourceFolderUseCase`, `ScanDocumentsUseCase` |
| 2 | **Direita:** pasta do mês em edição, com mês em destaque e caminho completo protegido (só o lápis troca) | `MonthHeader`, `FolderPathField`, `ChangeMonthFolderUseCase` |
| 2.1 | Mês e conta identificados pelo caminho; meses anteriores a junho/2026 ficam somente leitura | `MonthFolderParser`, `EditablePeriodPolicy` |
| 3 | Preview com PDFBox: páginas, zoom +/−, ajustar à largura/página | `PdfPreviewViewModel`, `PdfRepositoryImpl` |
| 4 | Árvore de categorias hierárquica (Congregação, Manutenção, Outros) | `CategoryHierarchy`, `DefaultCategories` |
| 5 | Arrastar PDF do Windows Explorer ou da lista para uma categoria | `OrganizerScreen`, `DragPayload` |
| 6 | Proposta com nome atual, novo nome e destino: **Cancelar / Renomear / Renomear e Mover** | `PlanOrganizationUseCase`, `OrganizerDialogs` |
| 7 | Conflitos de nome (substituir / cópia numerada / cancelar) conforme configuração | `ConflictPolicy`, `OrganizeDocumentUseCase` |
| 8 | Numeração automática de "Outros" | `SequentialNaming` |
| 9 | Histórico em SQLite e **Desfazer** | `UndoOperationUseCase`, `FileOperationDao` |
| 10 | Tela de configurações (pasta, duplicados, confirmações) | `SettingsScreen` |
| 11 | Testes unitários das regras principais | `src/test` |

### Fase 2 — Robustez e uso diário

- Tela de histórico com todas as operações e desfazer de qualquer item válido (hoje: desfazer a última e o "Desfazer" do aviso).
- Soltar vários arquivos de uma vez (hoje: um por vez, com aviso).
- Filtro na lista de documentos ("somente não organizados", busca por nome).
- Limpeza automática de backups antigos de arquivos substituídos.
- Instalador MSI assinado (`./gradlew packageMsi` já gera um instalador básico).

### Fase 3 — Extensões

- Editar categorias pela interface (o modelo e a tabela `categories` já suportam).
- Novos tipos de conta.
- Ponto de extensão `DocumentClassifier` para sugerir categoria (**não implementado** — a arquitetura apenas permite incluí-lo depois, entre `PlanOrganizationUseCase` e a UI).

## Pasta do mês e proteção de meses fechados

Estrutura real do pendrive:

```
CONTAS CONGREGAÇÃO\ANO DE SERVIÇO 2025-2026\4. TRIMESTRE Jun-Jul-Ago\1. JUNHO\
├── 1. Folha de Contas.pdf
├── 5. Remessa Betel.pdf
├── 5.1 Comprovante Remessa.pdf
└── 8. Extrato Bancário.pdf
```

- **Destino = a pasta do mês, sem subpastas.** O arquivo recebe o rótulo da categoria: `8. Extrato Bancário.pdf`,
  `5.1 Comprovante Remessa.pdf`, `1. Outros.pdf`.
- **Despesas aceitam vários arquivos** com descrição digitada no diálogo: `3. Despesa - xxx.pdf`, `3.1 Despesa - yyy.pdf`,
  `3.2 Despesa - xyz.pdf`. O número é o maior existente + 1 (conta também nomes fora do padrão, como `3 Despesa - ...`).
  A descrição vem sugerida a partir do nome original. Uma despesa que já está na pasta do mês mantém o número e só
  troca a descrição.
- **Mês identificado pelo caminho.** Aceita `1. JUNHO`, `10.Outubro`, `AGOSTO`, `3. Março 2024`. O ano vem do nome do
  mês ou da pasta "ANO DE SERVIÇO": em `2025-2026`, setembro–dezembro são 2025 e janeiro–agosto 2026. Um
  `5. Trimestre` dentro de `2025-2026` já é o ano seguinte (setembro/2026). Conferido contra todas as pastas do pendrive.
- **Conta identificada pelo caminho** (`CONTAS CONGREGAÇÃO` / `CONTAS MANUTENÇÃO`): só as categorias daquela conta e
  "Outros" aparecem, e o app recusa categoria de outra conta.
- **Bloqueio:** nada anterior a **junho de 2026** pode ser alterado — nem como destino, nem como origem (renomear um
  arquivo que está numa pasta de mês fechado também é bloqueado). Junho/2026 em diante é editável. Pastas fora de um mês
  (ex.: Downloads) podem ser origem, mas nunca destino. A regra é verificada ao propor, ao executar e ao desfazer.
  O primeiro mês editável fica em `EditablePeriodPolicy.FIRST_EDITABLE_MONTH`.
- **Caminho protegido:** o caminho completo fica sempre visível, com a pasta do mês em negrito; o texto pode ser
  copiado, mas não editado. Só o ícone de lápis abre o seletor de pastas.

## Conflitos encontrados no prompt e como foram resolvidos

### Duplicidade

1. **"Nunca sobrescrever automaticamente" × opção "Substituir".**
   "Substituir" só acontece por escolha explícita do usuário no diálogo de conflito, e mesmo assim nada é perdido:
   o arquivo existente é **movido para a pasta de backup** (`%APPDATA%\Fiscal\backup`) e o "Desfazer" restaura os dois arquivos.
   No nível do sistema de arquivos, `Files.move` é sempre chamado **sem** `REPLACE_EXISTING`, então o NIO recusa qualquer sobrescrita.

2. **Configuração de duplicados × diálogo "Escolha uma opção".**
   O diálogo só aparece com **Perguntar sempre**. Com **Criar cópia automaticamente** a cópia numerada é criada sem perguntar e
   "Substituir" nunca é usado. Com **Não permitir duplicados** a operação é bloqueada com uma mensagem — nem substitui, nem cria cópia.
   Regra única em `ConflictPolicy`.

3. **Cópia numerada `(2)` × numeração de "Outros" `N. Outros.pdf`.**
   São regras diferentes que não se misturam: "Outros" usa sempre o **maior número existente + 1**, então nunca colide e nunca
   precisa de `(2)`. Lacunas não são reaproveitadas ("1." e "3." existem → próximo é "4."), cumprindo
   "nunca reutilizar automaticamente um número ocupado". A cópia `(2)`, `(3)`… vale para as demais categorias e começa em 2.

4. **Conflito entre a proposta e a execução.**
   O disco pode mudar entre a proposta e o clique em "Confirmar". O conflito é verificado de novo na execução; se surgiu
   um arquivo novo, o diálogo de conflito é mostrado em vez de seguir adiante. O nome da cópia numerada também é recalculado.

5. **Comparação de nomes.** O Windows não diferencia maiúsculas de minúsculas: `extrato bancário.PDF` conflita com
   `Extrato Bancário.pdf`. Todas as comparações de nome ignoram caixa.

### Outras regras

6. **Pasta raiz:** o prompt cita `D:\Modelo\jw-teste\pendriver` e `D:\Modelo\jw\pendriver`. Adotado `D:\Modelo\jw\pendriver`
   como pasta de origem sugerida, usada apenas se existir. Se a pasta salva sumir (pendrive desconectado),
   o app avisa e pede para reconectar ou escolher outra.

7. **"Confirmar antes de mover" desligado × "Nunca mover sem mostrar o destino".**
   A proposta com nome atual, novo nome e destino é **sempre** mostrada — é nela que o usuário escolhe Renomear ou
   Renomear e Mover. As caixas de confirmação controlam apenas o passo extra "Confirmar operação?".

8. **Renomear × Renomear e Mover.** "Renomear" mantém o arquivo na pasta atual com o nome da categoria;
   "Renomear e Mover" leva para a pasta do mês em edição. As duas opções seguem as mesmas regras de duplicidade e de bloqueio.

9. **Mesmo nome em contas diferentes** ("Folha de Contas" existe nas duas contas): os ids das categorias levam o prefixo
   da conta (`congregacao.folha_de_contas`, `manutencao.folha_de_contas`).

10. **Nome dos arquivos (substitui a regra 11 do prompt original):** número e nome são campos separados e o arquivo usa o rótulo
    completo, como nas pastas reais: `"8. Extrato Bancário.pdf"`, `"5.1 Comprovante Remessa.pdf"`. Não há subpastas por categoria.

11. **Documento já organizado:** se o arquivo já tem o nome e o local corretos, nenhuma proposta é feita
    (evita renomear `2. Outros.pdf` para `3. Outros.pdf`, por exemplo).

12. **Preview não bloqueia o arquivo:** o PDF é lido para a memória antes de abrir no PDFBox, então o arquivo
    em exibição pode ser movido/renomeado e o original nunca é alterado.
