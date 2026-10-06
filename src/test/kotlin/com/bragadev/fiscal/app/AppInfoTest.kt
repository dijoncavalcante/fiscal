package com.bragadev.fiscal.app

import org.junit.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** A versão do build.gradle.kts chega ao app (tela "Sobre", log e diagnóstico) — a mesma do instalador. */
class AppInfoTest {
    @Test
    fun `versao e data da compilacao vem do build`() {
        assertTrue(Regex("""\d+\.\d+\.\d+""").matches(AppInfo.VERSION), "versão no formato do MSI, veio \"${AppInfo.VERSION}\"")
        assertNotNull(AppInfo.BUILD_DATE)
        assertTrue(AppInfo.FULL_VERSION.startsWith("${AppInfo.VERSION} ("))
    }
}
