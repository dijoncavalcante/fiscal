package com.bragadev.fiscal.domain.usecase

import com.bragadev.fiscal.domain.repository.FileFlagRepository
import java.nio.file.Path

/** Marca ou desmarca uma pendência num arquivo (ex.: "arquivo errado, trocar"). Não altera o arquivo. */
class FileFlagUseCase(private val flagRepository: FileFlagRepository) {
    suspend fun mark(file: Path, note: String) {
        require(note.isNotBlank()) { "A pendência precisa de uma descrição." }
        flagRepository.set(file, note.trim())
    }

    suspend fun clear(file: Path) = flagRepository.clear(file)
}
