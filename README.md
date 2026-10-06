# FISCAL - Organizador de Documentos PDF

Aplicação desktop para Windows, **100% offline**, para organizar PDFs em uma pasta local:
visualizar, arrastar para a categoria certa, renomear e mover com segurança — e desfazer.

- Kotlin + Compose Multiplatform Desktop, MVVM, Clean Architecture
- SQLite local (`%APPDATA%\Fiscal\fiscal.db`), Apache PDFBox, Java NIO, Coroutines, Koin
- Sem internet, sem IA, sem serviços externos

Escopo por fases e decisões sobre as regras: [docs/MVP.md](docs/MVP.md).

## Requisitos

- Windows 10/11
- JDK 17+

## Comandos

```bash
./gradlew run            # executa o app
./gradlew test           # testes unitários
./gradlew packageMsi     # gera instalador em build/compose/binaries/main/msi
```

## Como usar

Na primeira vez, o assistente pede três coisas: a pasta raiz das contas (a que tem CONTAS CONGREGAÇÃO e CONTAS
MANUTENÇÃO), a pasta de origem dos PDFs e o mês em edição. Dá para reabri-lo em **Configurações**.

1. **Esquerda:** clique no lápis e escolha a pasta do computador com os PDFs (sugestão inicial: `D:\Modelo\jw\pendriver`).
2. **Direita:** em "Escolher mês", clique no mês (ou no lápis para escolher a pasta, ex.: `...\4. TRIMESTRE Jun-Jul-Ago\1. JUNHO`).
   Confira o mês em destaque. Meses anteriores a junho de 2026 ficam **somente leitura** (com cadeado).
3. Clique em um PDF da lista para ver o preview.
4. Arraste o PDF (da lista ou do Windows Explorer) até uma categoria — ou selecione o PDF e clique na categoria.
5. Confira o nome atual, o novo nome, o mês e o destino e escolha **Renomear** ou **Renomear e Mover**.
6. Use **Desfazer** no aviso ou no topo da tela para reverter a última operação.

Atalhos: **Ctrl+Z** desfaz a última operação, **F5** atualiza; nas janelas, **Enter** confirma e **Esc** cancela.
Tema claro/escuro em **Configurações → Aparência** (padrão: igual ao Windows). A janela reabre no tamanho e na posição
em que foi fechada.

## Estrutura

```
com.bragadev.fiscal
├── app            Main, App, módulo Koin
├── domain         modelos, regras puras, interfaces de repositório e casos de uso
├── data           SQLite (DAOs), sistema de arquivos (NIO), PDFBox, configurações
└── presentation   telas Compose e ViewModels (home, preview, organizer, settings)
```

A camada de apresentação não acessa `java.nio.file.Files`; toda operação em disco passa pelo `FileRepository`.
