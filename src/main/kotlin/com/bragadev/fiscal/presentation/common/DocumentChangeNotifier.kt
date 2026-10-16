package com.bragadev.fiscal.presentation.common

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.nio.file.Path

/** Avisa as telas de que arquivos mudaram em disco e qual documento deve ficar selecionado. */
class DocumentChangeNotifier {
    private val events = MutableSharedFlow<DocumentsChanged>(extraBufferCapacity = 8)
    val changes: SharedFlow<DocumentsChanged> = events.asSharedFlow()

    /**
     * @param selectPath documento que deve ficar selecionado.
     * @param removedPath documento que deixou de existir (excluído): se estava selecionado, a seleção vai para o vizinho.
     */
    fun notifyChanged(selectPath: Path? = null, removedPath: Path? = null) {
        events.tryEmit(DocumentsChanged(selectPath, removedPath))
    }
}

data class DocumentsChanged(val selectPath: Path?, val removedPath: Path? = null)
