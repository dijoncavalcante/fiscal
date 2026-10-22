package com.bragadev.fiscal.data.filesystem

import com.bragadev.fiscal.data.AppDirectories
import com.bragadev.fiscal.data.logging.AppLog
import com.bragadev.fiscal.domain.model.FileOperationError
import com.bragadev.fiscal.domain.model.Outcome
import com.bragadev.fiscal.domain.model.SignatureRole
import com.bragadev.fiscal.domain.repository.SignatureImageStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import javax.imageio.ImageIO

/**
 * Imagens do selo de assinatura em `%APPDATA%\Fiscal\assinaturas\<conta ou "extratos">.png`. A imagem é relida e gravada como
 * PNG (o arquivo original nunca é alterado): primeiro num arquivo temporário, que só então substitui o anterior.
 */
class SignatureImageStorageImpl(private val directories: AppDirectories) : SignatureImageStorage {
    override suspend fun load(role: SignatureRole): ByteArray? = withContext(Dispatchers.IO) {
        runCatching { fileOf(role).takeIf(Files::exists)?.let(Files::readAllBytes) }.getOrNull()
    }

    override suspend fun save(role: SignatureRole, source: Path): Outcome<Unit> = withContext(Dispatchers.IO) {
        try {
            val image = Files.newInputStream(source).use(ImageIO::read)
                ?: return@withContext Outcome.Failure(FileOperationError.UnsupportedImage)
            Files.createDirectories(directories.signaturesDirectory)
            val target = fileOf(role)
            val partial = target.resolveSibling("${target.fileName}.parcial")
            if (!ImageIO.write(
                    image,
                    "png",
                    partial.toFile()
                )
            ) return@withContext Outcome.Failure(FileOperationError.UnsupportedImage)
            Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            AppLog.info("Imagem da assinatura (${role.name}) atualizada")
            Outcome.Success(Unit)
        } catch (error: FileSystemException) {
            Outcome.Failure(FileErrorMapper.map(error))
        } catch (_: IOException) {
            Outcome.Failure(FileOperationError.ReadError)
        }
    }

    override suspend fun remove(role: SignatureRole): Outcome<Unit> = withContext(Dispatchers.IO) {
        try {
            Files.deleteIfExists(fileOf(role))
            Outcome.Success(Unit)
        } catch (error: FileSystemException) {
            Outcome.Failure(FileErrorMapper.map(error))
        } catch (_: IOException) {
            Outcome.Failure(FileOperationError.ReadError)
        }
    }

    private fun fileOf(role: SignatureRole): Path =
        directories.signaturesDirectory.resolve("${role.imageName}.png")
}
