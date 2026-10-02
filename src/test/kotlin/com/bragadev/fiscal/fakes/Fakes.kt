package com.bragadev.fiscal.fakes

import com.bragadev.fiscal.domain.model.AppSettings
import com.bragadev.fiscal.domain.model.DocumentCategory
import com.bragadev.fiscal.domain.model.FileOperation
import com.bragadev.fiscal.domain.repository.BackupStorage
import com.bragadev.fiscal.domain.repository.CategoryRepository
import com.bragadev.fiscal.domain.repository.DocumentRepository
import com.bragadev.fiscal.domain.repository.OperationHistoryRepository
import com.bragadev.fiscal.domain.repository.SettingsRepository
import com.bragadev.fiscal.domain.rules.DefaultCategories
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

class FakeSettingsRepository(initial: AppSettings) : SettingsRepository {
    private val state = MutableStateFlow(initial)
    override val settings: StateFlow<AppSettings> = state
    override val suggestedRootPath: Path = Path.of("nao-existe", "pendriver")

    override suspend fun load(): AppSettings = state.value

    override suspend fun save(settings: AppSettings) {
        state.value = settings
    }
}

class FakeCategoryRepository(
    private val categories: List<DocumentCategory> = DefaultCategories.all,
) : CategoryRepository {
    override suspend fun getCategories(): List<DocumentCategory> = categories
}

class InMemoryHistoryRepository : OperationHistoryRepository {
    val operations = mutableListOf<FileOperation>()

    override suspend fun save(operation: FileOperation) {
        operations += operation
    }

    override suspend fun find(id: UUID): FileOperation? = operations.firstOrNull { it.id == id }

    override suspend fun markUndone(id: UUID) {
        val index = operations.indexOfFirst { it.id == id }
        if (index >= 0) operations[index] = operations[index].copy(undone = true)
    }

    override suspend fun lastUndoable(): FileOperation? = operations.lastOrNull { !it.undone }
}

class NoOpDocumentRepository : DocumentRepository {
    override suspend fun recordOrganized(originalPath: Path, currentPath: Path, categoryId: String?) = Unit

    override suspend fun recordRestored(currentPath: Path, restoredPath: Path) = Unit
}

class TempBackupStorage(private val directory: Path) : BackupStorage {
    override fun newBackupPath(fileName: String): Path = directory.resolve("${UUID.randomUUID()}-$fileName")
}

/** Cria um arquivo com assinatura de PDF suficiente para a validação do repositório. */
fun Path.createFakePdf(name: String, content: String = name): Path {
    Files.createDirectories(this)
    return resolve(name).also { Files.writeString(it, "%PDF-1.4\n% $content\n%%EOF") }
}
