package com.bragadev.fiscal.domain.usecase

import com.bragadev.fiscal.domain.model.FileOperationError
import com.bragadev.fiscal.domain.model.ImagePage
import com.bragadev.fiscal.domain.model.Outcome
import com.bragadev.fiscal.domain.model.map
import com.bragadev.fiscal.domain.repository.FileRepository
import com.bragadev.fiscal.domain.repository.PdfToolsRepository
import com.bragadev.fiscal.domain.rules.DuplicateNameResolver
import com.bragadev.fiscal.domain.rules.EditablePeriodPolicy
import com.bragadev.fiscal.domain.rules.FileNameRules
import com.bragadev.fiscal.domain.rules.ImageFileRules
import java.nio.file.Path

/**
 * Onde o PDF novo será salvo: valida pasta e nome, respeita meses fechados
 * e nunca sobrescreve — se o nome já existir, usa "(2)", "(3)"...
 */
class PdfOutputResolver(
    private val fileRepository: FileRepository,
    private val periodPolicy: EditablePeriodPolicy,
) {
    suspend fun resolve(folder: Path, name: String): Outcome<Path> {
        if (!fileRepository.isDirectory(folder)) return Outcome.Failure(FileOperationError.FolderNotFound)
        periodPolicy.checkSource(folder)?.let { return Outcome.Failure(it) }
        val baseName = FileNameRules.baseName(name.trim()).trim()
        if (baseName.isEmpty()) return Outcome.Failure(FileOperationError.NameRequired)
        val fileName = FileNameRules.withPdfExtension(baseName)
        if (!FileNameRules.isValid(fileName)) return Outcome.Failure(FileOperationError.InvalidFileName)
        val existing = fileRepository.listFileNames(folder)
        val finalName = if (FileNameRules.containsIgnoringCase(existing, fileName)) {
            DuplicateNameResolver.nextNumberedCopy(fileName, existing)
        } else {
            fileName
        }
        return Outcome.Success(folder.resolve(finalName))
    }
}

/** Converte imagens (JPEG/PNG) em um PDF, uma página por imagem. Retorna o arquivo criado. */
class CreatePdfFromImagesUseCase(
    private val fileRepository: FileRepository,
    private val pdfTools: PdfToolsRepository,
    private val outputResolver: PdfOutputResolver,
) {
    suspend operator fun invoke(pages: List<ImagePage>, folder: Path, name: String): Outcome<Path> {
        if (pages.isEmpty()) return Outcome.Failure(FileOperationError.NoInputFiles)
        pages.forEach { page ->
            if (!fileRepository.exists(page.path)) return Outcome.Failure(FileOperationError.FileNotFound)
            if (!ImageFileRules.isSupported(page.path.fileName.toString())) return Outcome.Failure(FileOperationError.UnsupportedImage)
        }
        val target = when (val resolved = outputResolver.resolve(folder, name)) {
            is Outcome.Success -> resolved.value
            is Outcome.Failure -> return resolved
        }
        return pdfTools.imagesToPdf(pages, target).map { target }
    }
}

/** Junta vários PDFs em um PDF novo, na ordem escolhida. Os originais não são alterados. */
class MergePdfsUseCase(
    private val fileRepository: FileRepository,
    private val pdfTools: PdfToolsRepository,
    private val outputResolver: PdfOutputResolver,
) {
    suspend operator fun invoke(sources: List<Path>, folder: Path, name: String): Outcome<Path> {
        if (sources.size < MIN_SOURCES) return Outcome.Failure(FileOperationError.NoInputFiles)
        sources.forEach { source ->
            if (!fileRepository.exists(source)) return Outcome.Failure(FileOperationError.FileNotFound)
            if (!fileRepository.isPdf(source)) return Outcome.Failure(FileOperationError.InvalidPdf)
        }
        val target = when (val resolved = outputResolver.resolve(folder, name)) {
            is Outcome.Success -> resolved.value
            is Outcome.Failure -> return resolved
        }
        return pdfTools.mergePdfs(sources, target).map { target }
    }

    private companion object {
        const val MIN_SOURCES = 2
    }
}

/** Lê uma imagem para mostrar na janela de conversão. */
class ReadImageUseCase(private val fileRepository: FileRepository) {
    suspend operator fun invoke(file: Path): Outcome<ByteArray> = fileRepository.readBytes(file)
}
