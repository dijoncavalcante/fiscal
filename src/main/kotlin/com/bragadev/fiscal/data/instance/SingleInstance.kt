package com.bragadev.fiscal.data.instance

import com.bragadev.fiscal.data.logging.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.StandardWatchEventKinds
import java.util.concurrent.TimeUnit

/**
 * Garante uma única cópia do app aberta, pois todas usariam o mesmo banco local.
 *
 * A primeira cópia trava [lockFile] enquanto estiver aberta. Uma segunda cópia não consegue a trava:
 * grava [signalFile] e fecha; a primeira percebe o sinal e traz a janela para a frente.
 * Tudo por arquivos locais — nada de rede.
 */
class SingleInstance(private val lockFile: Path, private val signalFile: Path) {
    private var channel: FileChannel? = null
    private var lock: FileLock? = null

    /** `true` se esta é a única cópia aberta (e passa a ser dona da trava). */
    fun tryAcquire(): Boolean {
        return try {
            val opened = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE)
            val acquired = try {
                opened.tryLock()
            } catch (_: OverlappingFileLockException) {
                null
            }
            if (acquired == null) {
                opened.close()
                false
            } else {
                channel = opened
                lock = acquired
                Files.deleteIfExists(signalFile)
                true
            }
        } catch (error: IOException) {
            // Sem conseguir criar a trava, não impede o uso do app.
            AppLog.warn("Não foi possível verificar se o app já está aberto", error)
            true
        }
    }

    /** Pede à cópia já aberta que apareça na frente. */
    fun requestActivation() {
        runCatching { Files.writeString(signalFile, System.currentTimeMillis().toString()) }
            .onFailure { AppLog.warn("Não foi possível avisar a cópia já aberta", it) }
    }

    /** Emite sempre que outra cópia pedir para mostrar a janela. */
    fun activationRequests(): Flow<Unit> = callbackFlow {
        val folder = signalFile.parent
        val watcher = folder.fileSystem.newWatchService()
        folder.register(watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY)
        val poller = launch {
            while (isActive) {
                val key = runCatching { watcher.poll(POLL_MS, TimeUnit.MILLISECONDS) }.getOrNull() ?: continue
                val signaled = key.pollEvents().any { (it.context() as? Path)?.fileName == signalFile.fileName }
                key.reset()
                if (signaled) {
                    runCatching { Files.deleteIfExists(signalFile) }
                    trySend(Unit)
                }
            }
        }
        awaitClose {
            poller.cancel()
            runCatching { watcher.close() }
        }
    }.flowOn(Dispatchers.IO)

    fun release() {
        runCatching { lock?.release() }
        runCatching { channel?.close() }
        lock = null
        channel = null
    }

    private companion object {
        const val POLL_MS = 300L
    }
}
