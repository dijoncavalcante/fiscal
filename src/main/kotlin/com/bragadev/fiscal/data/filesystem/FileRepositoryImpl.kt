package com.bragadev.fiscal.data.filesystem

import com.bragadev.fiscal.domain.model.Document
import com.bragadev.fiscal.domain.model.Outcome
import com.bragadev.fiscal.domain.repository.FileRepository
import com.bragadev.fiscal.domain.rules.FileNameRules
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardWatchEventKinds.ENTRY_CREATE
import java.nio.file.StandardWatchEventKinds.ENTRY_DELETE
import java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.TimeUnit

class FileRepositoryImpl(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : FileRepository {

    override suspend fun exists(path: Path): Boolean = io { Files.exists(path) }

    override suspend fun isDirectory(path: Path): Boolean = io { Files.isDirectory(path) }

    override suspend fun isPdf(path: Path): Boolean = io {
        Files.isRegularFile(path) &&
            FileNameRules.hasPdfExtension(path.fileName.toString()) &&
            hasPdfSignature(path)
    }

    override suspend fun listFileNames(directory: Path): Set<String> = io {
        if (!Files.isDirectory(directory)) return@io emptySet()
        Files.list(directory).use { entries -> entries.map { it.fileName.toString() }.toList().toSet() }
    }

    override suspend fun readBytes(file: Path): Outcome<ByteArray> = io { catching { Files.readAllBytes(file) } }

    override suspend fun listSubfolders(folder: Path): List<Path> = io {
        if (!Files.isDirectory(folder)) return@io emptyList()
        runCatching {
            Files.list(folder).use { entries ->
                entries.filter { Files.isDirectory(it) && !it.fileName.toString().startsWith(".") && !isHidden(it) }
                    .toList()
                    .sortedBy { it.fileName.toString().lowercase() }
            }
        }.getOrDefault(emptyList())
    }

    override suspend fun listPdfFiles(folder: Path): Outcome<List<Document>> = io {
        catching {
            val documents = mutableListOf<Document>()
            Files.walkFileTree(folder, emptySet(), SINGLE_LEVEL, PdfCollector(documents))
            documents.toList()
        }
    }

    override suspend fun move(source: Path, target: Path): Outcome<Unit> = io {
        catching {
            target.parent?.let(Files::createDirectories)
            // Sem REPLACE_EXISTING: o NIO falha se o destino existir, impedindo sobrescrita.
            Files.move(source, target)
            Unit
        }
    }

    override fun watch(folder: Path): Flow<Unit> = callbackFlow {
        val watcher = try {
            folder.fileSystem.newWatchService().also {
                folder.register(it, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY)
            }
        } catch (_: IOException) {
            // Pasta inacessível (ex.: pendrive removido): sem avisos; o botão Atualizar continua funcionando.
            close()
            return@callbackFlow
        }
        val poller = launch(ioDispatcher) {
            while (isActive) {
                val key = runCatching { watcher.poll(WATCH_POLL_MS, TimeUnit.MILLISECONDS) }.getOrNull() ?: continue
                if (key.pollEvents().isNotEmpty()) trySend(Unit)
                if (!key.reset()) break
            }
        }
        awaitClose {
            poller.cancel()
            runCatching { watcher.close() }
        }
    }

    private fun isHidden(path: Path): Boolean = runCatching { Files.isHidden(path) }.getOrDefault(false)

    private fun hasPdfSignature(path: Path): Boolean = try {
        Files.newInputStream(path).use { input ->
            val header = input.readNBytes(PDF_HEADER_SEARCH_BYTES)
            String(header, Charsets.ISO_8859_1).contains(PDF_SIGNATURE)
        }
    } catch (_: IOException) {
        false
    }

    private suspend fun <T> io(block: suspend () -> T): T = withContext(ioDispatcher) { block() }

    private inline fun <T> catching(block: () -> T): Outcome<T> = try {
        Outcome.Success(block())
    } catch (error: Exception) {
        Outcome.Failure(FileErrorMapper.map(error))
    }

    /** Coleta PDFs e ignora pastas sem permissão de leitura em vez de interromper a busca. */
    private class PdfCollector(private val documents: MutableList<Document>) : SimpleFileVisitor<Path>() {
        override fun visitFile(file: Path, attributes: BasicFileAttributes): FileVisitResult {
            val name = file.fileName.toString()
            // "._arquivo.pdf" são metadados criados pelo macOS no pendrive, não PDFs de verdade.
            if (attributes.isRegularFile && FileNameRules.hasPdfExtension(name) && !name.startsWith(MAC_METADATA_PREFIX)) {
                documents += Document(file, attributes.size(), attributes.lastModifiedTime().toInstant())
            }
            return FileVisitResult.CONTINUE
        }

        override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult = FileVisitResult.CONTINUE
    }

    private companion object {
        const val PDF_SIGNATURE = "%PDF-"
        const val PDF_HEADER_SEARCH_BYTES = 1024
        const val SINGLE_LEVEL = 1
        const val MAC_METADATA_PREFIX = "._"
        const val WATCH_POLL_MS = 500L
    }
}
