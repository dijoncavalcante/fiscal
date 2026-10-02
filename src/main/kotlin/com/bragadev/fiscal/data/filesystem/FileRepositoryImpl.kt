package com.bragadev.fiscal.data.filesystem

import com.bragadev.fiscal.domain.model.Document
import com.bragadev.fiscal.domain.model.Outcome
import com.bragadev.fiscal.domain.repository.FileRepository
import com.bragadev.fiscal.domain.rules.FileNameRules
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

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

    override suspend fun listPdfFiles(root: Path): Outcome<List<Document>> = io {
        catching {
            val documents = mutableListOf<Document>()
            Files.walkFileTree(root, PdfCollector(documents))
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
            if (attributes.isRegularFile && FileNameRules.hasPdfExtension(file.fileName.toString())) {
                documents += Document(file, attributes.size(), attributes.lastModifiedTime().toInstant())
            }
            return FileVisitResult.CONTINUE
        }

        override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult = FileVisitResult.CONTINUE
    }

    private companion object {
        const val PDF_SIGNATURE = "%PDF-"
        const val PDF_HEADER_SEARCH_BYTES = 1024
    }
}
