package com.bragadev.fiscal.data

import com.bragadev.fiscal.data.database.Database
import com.bragadev.fiscal.data.database.SettingsDao
import com.bragadev.fiscal.data.settings.SettingsRepositoryImpl
import com.bragadev.fiscal.domain.model.AppSettings
import com.bragadev.fiscal.domain.model.ThemeMode
import com.bragadev.fiscal.domain.model.WindowBounds
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Tema, assistente e janela ficam salvos entre aberturas do app. */
class SettingsPersistenceTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `tema assistente e janela sobrevivem a reabertura`() = runTest {
        val file = temp.root.toPath().resolve("fiscal.db")
        val database = Database(file)
        val bounds = WindowBounds(x = 120, y = 40, width = 1280, height = 800, maximized = true)
        SettingsRepositoryImpl(SettingsDao(database)).save(
            AppSettings(themeMode = ThemeMode.DARK, onboardingDone = true, windowBounds = bounds),
        )
        database.close()

        val reopened = Database(file)
        val loaded = SettingsRepositoryImpl(SettingsDao(reopened)).load()
        assertEquals(ThemeMode.DARK, loaded.themeMode)
        assertTrue(loaded.onboardingDone)
        assertEquals(bounds, loaded.windowBounds)
        reopened.close()
    }

    @Test
    fun `banco antigo sem as chaves novas usa os padroes`() = runTest {
        val database = Database(temp.root.toPath().resolve("antigo.db"))
        SettingsDao(database).putAll(mapOf("month_folder" to "D:\\pendriver\\1. JUNHO", "theme_mode" to "ROXO"))

        val loaded = SettingsRepositoryImpl(SettingsDao(database)).load()
        assertEquals(ThemeMode.SYSTEM, loaded.themeMode)
        assertFalse(loaded.onboardingDone)
        assertEquals(null, loaded.windowBounds)
        database.close()
    }

    @Test
    fun `assistente so abre sozinho para quem ainda nao tem as pastas`() {
        val configured = AppSettings(sourceFolder = Path.of("origem"), monthFolder = Path.of("1. JUNHO"))
        assertFalse(configured.needsOnboarding)
        assertTrue(AppSettings().needsOnboarding)
        assertTrue(AppSettings(sourceFolder = Path.of("origem")).needsOnboarding)
        assertFalse(AppSettings(onboardingDone = true).needsOnboarding)
    }
}
