# FISCAL - Organizador de Documentos PDF

Aplicação desktop para Windows, **100% offline**, para organizar PDFs em uma pasta local:
visualizar, arrastar para a categoria certa, renomear e mover com segurança — e desfazer.

- Kotlin + Compose Multiplatform Desktop, MVVM, Clean Architecture
- SQLite local (`%APPDATA%\Fiscal\fiscal.db`), Apache PDFBox, Java NIO, Coroutines, Koin
- Sem internet, sem IA, sem serviços externos

Escopo por fases e decisões sobre as regras: [docs/MVP.md](docs/MVP.md).

## Requisitos

- Windows 10/11
- JDK 17+ só para compilar; quem usa o instalador não precisa instalar Java (vem embutido)

## Comandos

```bash
./gradlew run            # executa o app
./gradlew test           # testes unitários
./gradlew packageMsi     # gera instalador em build/compose/binaries/main/msi (Java embutido; ver docs/DISTRIBUICAO.md)
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

7. Ao terminar o mês, clique em **Concluir mês** (no topo do mês em edição): o FISCAL mostra o que está faltando e as
   pendências e gera um **relatório em PDF** com o que existe em cada categoria (salvo na pasta de origem).

A versão em uso aparece na barra de status (clique para ver **Sobre**: versão, data e pastas de dados).

**Histórico** (barra superior) lista tudo o que foi renomeado ou movido, com busca e **Desfazer** em qualquer operação
que ainda possa ser desfeita. O **mês de corte** (meses anteriores ficam somente leitura) fica em **Configurações →
Proteção de meses**; ao entregar a prestação de contas do ano, avance-o ali, sem precisar de nova versão.
Fotos de celular convertidas em PDF já saem em pé (orientação gravada pelo celular).

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
