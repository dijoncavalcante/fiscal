package com.bragadev.fiscal.app

import java.time.LocalDate
import java.util.Properties

/**
 * Identificação do app, usada no log, no diagnóstico e na tela "Sobre".
 *
 * Versão, data da compilação e commit vêm de `fiscal-build.properties`, gerado pelo Gradle a partir de
 * `version` no build.gradle.kts — a mesma versão do instalador. Sem o arquivo (ex.: rodando os testes), "dev".
 */
object AppInfo {
    const val NAME = "FISCAL - Organizador de Documentos PDF"

    private val properties: Properties = Properties().apply {
        AppInfo::class.java.getResourceAsStream("/fiscal-build.properties")?.use { load(it.reader(Charsets.UTF_8)) }
    }

    val VERSION: String = properties.getProperty("version") ?: "dev"
    val BUILD_DATE: LocalDate? = properties.getProperty("buildDate")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val COMMIT: String? = properties.getProperty("commit")?.takeIf { it.isNotBlank() && it != "desconhecido" }

    /** "2.0.0 (06/10/2026, 28b4a25)": o que aparece no log e no diagnóstico. */
    val FULL_VERSION: String
        get() = buildString {
            append(VERSION)
            val details = listOfNotNull(BUILD_DATE?.let { "%02d/%02d/%d".format(it.dayOfMonth, it.monthValue, it.year) }, COMMIT)
            if (details.isNotEmpty()) append(" (${details.joinToString(", ")})")
        }
}
