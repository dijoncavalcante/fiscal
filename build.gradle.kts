import net.jsign.AuthenticodeSigner
import net.jsign.KeyStoreBuilder
import net.jsign.Signable
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.time.LocalDate

buildscript {
    repositories { mavenCentral() }
    dependencies {
        // Assinatura de código (Authenticode) do instalador, em Java puro: não precisa do Windows SDK.
        classpath(libs.jsign.core)
    }
}

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

group = "com.bragadev.fiscal"
// Versão única do app: vai para o instalador, a tela "Sobre", o log e o diagnóstico.
// Formato do MSI: MAIOR.MENOR.CORREÇÃO, só números (MAIOR até 255). Aumente a cada instalador novo,
// senão o Windows não atualiza por cima da versão instalada.
version = "2.0.0"

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(libs.compose.material3)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.koin.core)
    implementation(libs.koin.compose)
    implementation(libs.pdfbox)
    implementation(libs.sqlite.jdbc)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(compose.desktop.uiTestJUnit4)
}

// ---- Informações da versão (tela "Sobre") ----

val buildInfoDir = layout.buildDirectory.dir("generated/buildInfo")
val generateBuildInfo by tasks.registering {
    description = "Grava versão, data da compilação e commit em fiscal-build.properties."
    val appVersion = project.version.toString()
    val commit = providers.exec {
        commandLine("git", "rev-parse", "--short", "HEAD")
        isIgnoreExitValue = true
    }.standardOutput.asText.map { it.trim() }.orElse("")
    inputs.property("version", appVersion)
    inputs.property("commit", commit)
    // A data só muda de um dia para o outro: não força recompilar a cada build.
    inputs.property("date", LocalDate.now().toString())
    outputs.dir(buildInfoDir)
    doLast {
        val file = buildInfoDir.get().file("fiscal-build.properties").asFile
        file.parentFile.mkdirs()
        file.writeText(
            "version=$appVersion\nbuildDate=${LocalDate.now()}\ncommit=${commit.get().ifBlank { "desconhecido" }}\n",
        )
    }
}
sourceSets.main { resources.srcDir(generateBuildInfo) }

compose.desktop {
    application {
        mainClass = "com.bragadev.fiscal.app.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "Fiscal"
            packageVersion = project.version.toString()
            description = "FISCAL - Organizador de Documentos PDF"
            vendor = "BragaDev"
            copyright = "© ${LocalDate.now().year} BragaDev"
            // Java embutido: o instalador leva o próprio Java só com estes módulos (o usuário não instala nada).
            // Conferir com ./gradlew suggestRuntimeModules ao adicionar bibliotecas.
            modules("java.sql", "jdk.unsupported")

            windows {
                // Atalho no menu Iniciar (pasta "FISCAL") e na área de trabalho.
                menu = true
                menuGroup = "FISCAL"
                shortcut = true
                dirChooser = true
                // Mesmo código em todas as versões: o instalador novo substitui o antigo (atualização),
                // e "Desinstalar" no Windows remove tudo o que foi instalado.
                upgradeUuid = "6f3c1f0e-8a7d-4c1b-9b8e-2f6a1d4c9e21"
                // Gerado por tools/IconGenerator.java (ícone do instalador, do atalho e do Fiscal.exe).
                iconFile.set(project.file("packaging/fiscal.ico"))
            }
        }
    }
}

// ---- Assinatura de código ----
//
// Sem certificado, o Windows mostra "Editor desconhecido" ao instalar. Configure em
// %USERPROFILE%\.gradle\gradle.properties (nunca no repositório) — veja docs/DISTRIBUICAO.md:
//   fiscal.sign.keystore=C:\\caminho\\certificado.pfx
//   fiscal.sign.storepass=senha
//   fiscal.sign.alias=nome-do-certificado        (opcional se o arquivo tiver um só)
//   fiscal.sign.storetype=PKCS12                 (ou PKCS11, YUBIKEY, TRUSTEDSIGNING... aceitos pelo jsign)
//   fiscal.sign.timestamp=http://timestamp.digicert.com   (ou "nenhum" só para testes)

val signKeystore = providers.gradleProperty("fiscal.sign.keystore")
val signStorepass = providers.gradleProperty("fiscal.sign.storepass")
val signAlias = providers.gradleProperty("fiscal.sign.alias")
val signStoretype = providers.gradleProperty("fiscal.sign.storetype").orElse("PKCS12")
val signTimestamp = providers.gradleProperty("fiscal.sign.timestamp").orElse("http://timestamp.digicert.com")

fun registerSignTask(name: String, packageTask: String, folder: String, extension: String) {
    val sign = tasks.register(name) {
        group = "distribution"
        description = "Assina o instalador .$extension com o certificado de fiscal.sign.* (se configurado)."
        val outputDir = layout.buildDirectory.dir("compose/binaries/main/$folder")
        doLast {
            val installers = outputDir.get().asFile.listFiles { file -> file.extension == extension }.orEmpty()
            if (!signKeystore.isPresent) {
                logger.warn("ATENÇÃO: instalador NÃO assinado (fiscal.sign.keystore não configurado). Veja docs/DISTRIBUICAO.md.")
                return@doLast
            }
            val keystore = KeyStoreBuilder()
                .storetype(signStoretype.get())
                .keystore(signKeystore.get())
                .storepass(signStorepass.orNull)
                .build()
            val alias = signAlias.orNull ?: keystore.aliases().toList().single()
            // Certificado autoassinado não é embutido na assinatura (o jsign omite certificados raiz) e o Windows
            // não reconhece o editor: só serve um certificado emitido por uma autoridade (docs/DISTRIBUICAO.md).
            val certificate = keystore.getCertificate(alias) as java.security.cert.X509Certificate
            check(certificate.subjectX500Principal != certificate.issuerX500Principal) {
                "O certificado '${certificate.subjectX500Principal.name}' é autoassinado: o Windows continuaria mostrando " +
                    "\"Editor desconhecido\". Use um certificado de assinatura de código emitido por uma autoridade."
            }
            val timestamp = signTimestamp.get()
            val signer = AuthenticodeSigner(keystore, alias, signStorepass.orNull)
                .withProgramName("FISCAL - Organizador de Documentos PDF")
                .withTimestamping(timestamp != "nenhum")
                .apply { if (timestamp != "nenhum") withTimestampingAuthority(timestamp) }
            installers.forEach { installer ->
                Signable.of(installer).use { signer.sign(it) }
                logger.lifecycle("Assinado: ${installer.name}")
            }
        }
    }
    tasks.matching { it.name == packageTask }.configureEach { finalizedBy(sign) }
}
registerSignTask("signMsi", "packageMsi", "msi", "msi")
registerSignTask("signExe", "packageExe", "exe", "exe")
