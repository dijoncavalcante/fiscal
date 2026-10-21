package com.bragadev.fiscal.data.filesystem

import com.bragadev.fiscal.data.AppDirectories
import com.bragadev.fiscal.data.logging.AppLog
import com.bragadev.fiscal.domain.model.AccountType
import com.bragadev.fiscal.domain.model.FileOperationError
import com.bragadev.fiscal.domain.model.Outcome
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
 * Imagens do selo de assinatura em `%APPDATA%\Fiscal\assinaturas\<conta>.png`. A imagem é relida e gravada como
 * PNG (o arquivo original nunca é alterado): primeiro num arquivo temporário, que só então substitui o anterior.
 */
class SignatureImageStorageImpl(private val directories: AppDirectories) : SignatureImageStorage {
    override suspend fun load(account: AccountType): ByteArray? = withContext(Dispatchers.IO) {
        runCatching { fileOf(account).takeIf(Files::exists)?.let(Files::readAllBytes) }.getOrNull()
    }

    override suspend fun save(account: AccountType, source: Path): Outcome<Unit> = withContext(Dispatchers.IO) {
        try {
            val image = Files.newInputStream(source).use(ImageIO::read)
                ?: return@withContext Outcome.Failure(FileOperationError.UnsupportedImage)
            Files.createDirectories(directories.signaturesDirectory)
            val target = fileOf(account)
            val partial = target.resolveSibling("${target.fileName}.parcial")
            if (!ImageIO.write(
                    image,
                    "png",
                    partial.toFile()
                )
            ) return@withContext Outcome.Failure(FileOperationError.UnsupportedImage)
            Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            AppLog.info("Imagem da assinatura da conta ${account.name} atualizada")
            Outcome.Success(Unit)
        } catch (error: FileSystemException) {
            Outcome.Failure(FileErrorMapper.map(error))
        } catch (_: IOException) {
            Outcome.Failure(FileOperationError.ReadError)
        }
    }

    override suspend fun remove(account: AccountType): Outcome<Unit> = withContext(Dispatchers.IO) {
        try {
            Files.deleteIfExists(fileOf(account))
            Outcome.Success(Unit)
        } catch (error: FileSystemException) {
            Outcome.Failure(FileErrorMapper.map(error))
        } catch (_: IOException) {
            Outcome.Failure(FileOperationError.ReadError)
        }
    }

    private fun fileOf(account: AccountType): Path =
        directories.signaturesDirectory.resolve("${account.name.lowercase()}.png")
}
