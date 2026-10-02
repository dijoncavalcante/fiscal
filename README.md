# Fiscal — Organizador de Documentos PDF

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

1. Na primeira execução, o app usa `D:\Modelo\jw\pendriver` se a pasta existir; senão, pede para selecionar a pasta raiz.
2. Clique em um PDF da lista para ver o preview.
3. Arraste o PDF (da lista ou do Windows Explorer) até uma categoria — ou selecione o PDF e clique na categoria.
4. Confira o nome atual, o novo nome e o destino e escolha **Renomear** ou **Renomear e Mover**.
5. Use **Desfazer** no aviso ou no topo da tela para reverter a última operação.

## Estrutura

```
com.bragadev.fiscal
├── app            Main, App, módulo Koin
├── domain         modelos, regras puras, interfaces de repositório e casos de uso
├── data           SQLite (DAOs), sistema de arquivos (NIO), PDFBox, configurações
└── presentation   telas Compose e ViewModels (home, preview, organizer, settings)
```

A camada de apresentação não acessa `java.nio.file.Files`; toda operação em disco passa pelo `FileRepository`.
