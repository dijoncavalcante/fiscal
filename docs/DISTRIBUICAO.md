# Distribuição: instalador, assinatura e versão

## O que o instalador faz

`./gradlew packageMsi` gera `build/compose/binaries/main/msi/Fiscal-<versão>.msi`:

- **Java embutido**: o instalador leva o próprio Java (só os módulos usados: `java.sql`, `jdk.unsupported` além
  dos padrões). O usuário não instala nada. Ao adicionar bibliotecas, rode `./gradlew suggestRuntimeModules` e
  ajuste `modules(...)` no `build.gradle.kts`.
- Instala em `C:\Program Files\Fiscal` (o usuário pode trocar a pasta; pede permissão de administrador).
- **Atalhos**: pasta **FISCAL** no menu Iniciar e atalho na área de trabalho, com o ícone do app.
- **Atualização**: um instalador de versão maior substitui o instalado (mesmo `upgradeUuid`). Instalar uma versão
  **menor** que a instalada é recusado pelo Windows.
- **Desinstalação** (Configurações do Windows → Aplicativos → Fiscal → Desinstalar): remove tudo o que foi
  instalado — programa, Java embutido e atalhos. O app nunca grava na própria pasta (conferido: a pasta fica idêntica
  depois de abrir e fechar o app), então nada sobra lá.
- **Dados do usuário ficam**: `%APPDATA%\Fiscal` (configurações, histórico, pendências, backups de arquivos
  substituídos e logs) não é apagado ao desinstalar, para não perder nada numa reinstalação ou atualização.
  Para apagar de vez: depois de desinstalar, apague a pasta `%APPDATA%\Fiscal` (o botão "Abrir pasta de dados" em
  Configurações → Sobre o FISCAL mostra onde fica). Os PDFs do pendrive nunca são afetados.

## Versão

A versão fica **só** em `version = "..."` no `build.gradle.kts`. Ela vai para o MSI, a tela **Sobre** (com a data
da compilação e o commit, gerados em `fiscal-build.properties`), o log e o diagnóstico.

- Formato `MAIOR.MENOR.CORREÇÃO`, só números (MAIOR até 255) — exigência do MSI.
- **Aumente a versão a cada instalador entregue**; com a mesma versão o Windows não atualiza por cima.

## Assinatura de código ("Editor desconhecido")

Sem assinatura, o Windows mostra **"Editor desconhecido"** ao instalar. A assinatura prova que o instalador veio
da BragaDev e não foi alterado. O build já assina sozinho (task `signMsi`, que roda depois do `packageMsi`, usando o
[jsign](https://ebourg.github.io/jsign/)); falta só o **certificado**.

### 1. Conseguir um certificado de assinatura de código

Precisa ser emitido por uma autoridade reconhecida pelo Windows. **Certificado autoassinado não serve**: o
Windows continua mostrando "Editor desconhecido" (o build recusa esse caso com uma mensagem).

Hoje as autoridades entregam a chave num **token USB** ou num **serviço na nuvem** (não mais num arquivo `.pfx`
comum). Opções comuns, todas aceitas pelo jsign:

- **Azure Trusted Signing / Artifact Signing** (Microsoft, assinatura mensal, validação de identidade).
- **Certificado OV** de uma autoridade (Certum, Sectigo, DigiCert, SSL.com...) em token USB ou na nuvem do emissor.

Compare preço e exigências (pessoa física ou empresa, documentos) direto com o emissor. Mesmo assinado, o
SmartScreen pode avisar nas primeiras instalações até o certificado ganhar reputação.

### 2. Configurar no seu computador (nunca no repositório)

Em `%USERPROFILE%\.gradle\gradle.properties` (crie se não existir):

```properties
# Arquivo .pfx/.p12 (ou o que o tipo abaixo pedir: token, URL do serviço...)
fiscal.sign.keystore=C:\\Certificados\\bragadev.pfx
fiscal.sign.storepass=SENHA_OU_TOKEN
# Tipo de armazenamento do jsign: PKCS12 (arquivo, padrão), PKCS11, YUBIKEY, TRUSTEDSIGNING, AZUREKEYVAULT...
fiscal.sign.storetype=PKCS12
# Só se o arquivo tiver mais de um certificado:
# fiscal.sign.alias=bragadev
# Carimbo de tempo: mantém a assinatura válida depois que o certificado vencer (padrão: DigiCert)
# fiscal.sign.timestamp=http://timestamp.digicert.com
```

Para cada tipo de armazenamento, o que vai em `keystore`/`storepass` está na documentação do jsign.

### 3. Gerar e conferir

```bash
./gradlew packageMsi
```

A saída mostra `Assinado: Fiscal-<versão>.msi`. Sem configuração, mostra
`ATENÇÃO: instalador NÃO assinado` e o MSI sai sem assinatura (continua funcionando).

Conferir no PowerShell (deve dar `Valid` e o nome da BragaDev):

```powershell
Get-AuthenticodeSignature build\compose\binaries\main\msi\Fiscal-2.0.0.msi | Format-List Status, SignerCertificate
```

Ou: botão direito no `.msi` → Propriedades → aba **Assinaturas Digitais**.

O caminho completo foi testado com um certificado de teste emitido por uma autoridade de teste: a assinatura sai
correta e o Windows mostra o editor; só recusa a confiança porque a autoridade de teste não é reconhecida —
exatamente o que um certificado real resolve.

## Antes de entregar um instalador

1. Aumentar `version` no `build.gradle.kts`.
2. `./gradlew test`
3. `./gradlew packageMsi` (assina se configurado).
4. Instalar num computador de teste: atalho no menu Iniciar, abrir, Configurações → Sobre mostra a versão nova.
