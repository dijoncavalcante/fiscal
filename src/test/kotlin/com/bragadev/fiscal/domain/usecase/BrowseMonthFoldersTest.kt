package com.bragadev.fiscal.domain.usecase

import com.bragadev.fiscal.data.filesystem.FileRepositoryImpl
import com.bragadev.fiscal.domain.model.AccountType
import com.bragadev.fiscal.domain.model.AppSettings
import com.bragadev.fiscal.domain.model.MonthFolderStatus
import com.bragadev.fiscal.domain.model.Outcome
import com.bragadev.fiscal.domain.rules.EditablePeriodPolicy
import com.bragadev.fiscal.domain.rules.MonthFolderParser
import com.bragadev.fiscal.fakes.FakeSettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path
import java.time.YearMonth
import kotlin.test.assertEquals

/** Usa a estrutura real do pendrive: conta → ano de serviço → trimestre → mês. */
class BrowseMonthFoldersTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val root: Path by lazy { temp.newFolder("pendriver").toPath() }
    private val policy = EditablePeriodPolicy(YearMonth.of(2026, 6))
    private val browse by lazy { BrowseMonthFoldersUseCase(FileRepositoryImpl(), policy) }

    private fun mkdirs(vararg segments: String): Path =
        segments.fold(root) { path, segment -> path.resolve(segment) }.also { Files.createDirectories(it) }

    @Test
    fun `monta contas anos trimestres e meses em ordem`() = runTest {
        mkdirs("CONTAS CONGREGAÇÃO", "ANO DE SERVIÇO 2025-2026", "4. TRIMESTRE Jun-Jul-Ago", "3. AGOSTO")
        mkdirs("CONTAS CONGREGAÇÃO", "ANO DE SERVIÇO 2025-2026", "4. TRIMESTRE Jun-Jul-Ago", "1. JUNHO")
        mkdirs("CONTAS CONGREGAÇÃO", "ANO DE SERVIÇO 2025-2026", "3. TRIMESTRE Mar-Abr-Mai", "3. MAIO")
        mkdirs("CONTAS CONGREGAÇÃO", "ANO DE SERVIÇO 2024-2025", "1. TRIMESTRE - Set_Out_Nov", "10.Outubro")
        mkdirs("CONTAS MANUTENÇÃO", "ANO DE SERVIÇO 2025-2026", "5. Trimestre Set-Out-Nov", "1. Setembro")
        mkdirs("CONTAS MANUTENÇÃO", "PASTA DE APROVAÇÕES PERMANENTES")
        Files.createDirectories(root.resolve("Outra pasta qualquer"))

        val tree = (browse(root) as Outcome.Success).value

        assertEquals(listOf(AccountType.CONGREGACAO, AccountType.MANUTENCAO), tree.accounts.map { it.account })
        val congregacao = tree.accounts.first()
        assertEquals(listOf("ANO DE SERVIÇO 2024-2025", "ANO DE SERVIÇO 2025-2026"), congregacao.serviceYears.map { it.name })
        val year = congregacao.serviceYears.last()
        assertEquals(listOf("3. TRIMESTRE Mar-Abr-Mai", "4. TRIMESTRE Jun-Jul-Ago"), year.quarters.map { it.name })
        assertEquals(listOf("1. JUNHO", "3. AGOSTO"), year.quarters.last().months.map { it.path.fileName.toString() })
        assertEquals(MonthFolderStatus.LOCKED, year.quarters.first().months.single().status)

        val manutencao = tree.accounts.last()
        assertEquals(listOf("ANO DE SERVIÇO 2025-2026"), manutencao.serviceYears.map { it.name })
        assertEquals(YearMonth.of(2026, 9), manutencao.serviceYears.single().quarters.single().months.single().detectedMonth?.month)
    }

    @Test
    fun `pasta raiz e deduzida da pasta do mes`() {
        val month = root.resolve("CONTAS MANUTENÇÃO").resolve("ANO DE SERVIÇO 2025-2026").resolve("4. Trimestre").resolve("1. Junho")
        assertEquals(root, MonthFolderParser.accountsRootOf(month))
        assertEquals(null, MonthFolderParser.accountsRootOf(Path.of("C:", "Downloads")))
    }

    @Test
    fun `sem pasta raiz salva usa a deduzida da pasta do mes`() = runTest {
        val month = mkdirs("CONTAS CONGREGAÇÃO", "ANO DE SERVIÇO 2025-2026", "4. TRIMESTRE Jun-Jul-Ago", "1. JUNHO")
        val settings = FakeSettingsRepository(AppSettings(monthFolder = month))

        assertEquals(root, ResolveMonthsRootUseCase(settings, FileRepositoryImpl())())

        settings.save(settings.settings.value.copy(monthsRoot = temp.root.toPath()))
        assertEquals(temp.root.toPath(), ResolveMonthsRootUseCase(settings, FileRepositoryImpl())())
    }
}
