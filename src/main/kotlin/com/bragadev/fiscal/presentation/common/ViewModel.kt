package com.bragadev.fiscal.presentation.common

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Base dos ViewModels: o escopo roda na thread da UI e o trabalho pesado fica nos repositórios.
 * Um erro inesperado numa tarefa não derruba o app: vai para [UnexpectedErrors].
 */
abstract class ViewModel {
    private val errorHandler = CoroutineExceptionHandler { _, error -> UnexpectedErrors.report(error) }
    protected val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + errorHandler)

    open fun clear() {
        scope.cancel()
    }
}
