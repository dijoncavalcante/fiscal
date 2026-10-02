package com.bragadev.fiscal.presentation.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/** Base dos ViewModels: o escopo roda na thread da UI e o trabalho pesado fica nos repositórios. */
abstract class ViewModel {
    protected val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    open fun clear() {
        scope.cancel()
    }
}
