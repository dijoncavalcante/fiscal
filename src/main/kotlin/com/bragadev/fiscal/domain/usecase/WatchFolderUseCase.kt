package com.bragadev.fiscal.domain.usecase

import com.bragadev.fiscal.domain.repository.FileRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import java.nio.file.Path

/**
 * Avisa quando arquivos da pasta mudam fora do app (ex.: renomeados no Windows Explorer),
 * agrupando várias mudanças seguidas num único aviso.
 */
class WatchFolderUseCase(private val fileRepository: FileRepository) {
    @OptIn(FlowPreview::class)
    operator fun invoke(folder: Path): Flow<Unit> = fileRepository.watch(folder).debounce(DEBOUNCE_MS)

    private companion object {
        const val DEBOUNCE_MS = 400L
    }
}
