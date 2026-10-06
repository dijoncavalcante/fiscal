package com.bragadev.fiscal.data.filesystem

import com.bragadev.fiscal.data.logging.AppLog
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.UUID

/**
 * Move arquivos sem nunca sobrescrever e sem risco de perder o original.
 *
 * - Na mesma unidade: renomeação simples (`Files.move`), que é instantânea e não copia dados.
 * - Entre unidades (ex.: PC ↔ pendrive): copia para um arquivo temporário no destino, grava em disco,
 *   confere que a cópia é idêntica (tamanho e SHA-256), dá o nome final e só então apaga o original.
 *   Se algo falhar no caminho (pendrive removido, arquivo aberto), o original fica intacto e o
 *   temporário é descartado.
 *
 * O temporário começa com "~fiscal-" e termina em ".parcial", então nunca aparece nas listas de PDFs.
 */
class SafeFileMover(
    private val onDifferentVolumes: (Path, Path) -> Boolean = ::differentVolumes,
) {
    /** Lança exceção de NIO em caso de falha (mapeada para erro amigável por quem chama). */
    fun move(source: Path, target: Path) {
        val targetFolder = target.parent ?: throw IOException("Destino sem pasta: $target")
        Files.createDirectories(targetFolder)
        if (Files.exists(target)) throw java.nio.file.FileAlreadyExistsException(target.toString())

        if (!onDifferentVolumes(source, targetFolder)) {
            // Sem REPLACE_EXISTING: o NIO falha se o destino existir, impedindo sobrescrita.
            Files.move(source, target)
            AppLog.info("Movido: $source -> $target")
            return
        }
        copyVerifyAndRemoveOriginal(source, target, targetFolder)
    }

    private fun copyVerifyAndRemoveOriginal(source: Path, target: Path, targetFolder: Path) {
        val temporary = targetFolder.resolve("~fiscal-${UUID.randomUUID()}.parcial")
        try {
            Files.copy(source, temporary, StandardCopyOption.COPY_ATTRIBUTES)
            FileChannel.open(temporary, StandardOpenOption.WRITE).use { it.force(true) }
            if (Files.size(source) != Files.size(temporary) || !sha256(source).contentEquals(sha256(temporary))) {
                throw CopyVerificationException(source, target)
            }
            Files.move(temporary, target) // mesma pasta: renomeação atômica, sem sobrescrever
        } catch (error: Exception) {
            runCatching { Files.deleteIfExists(temporary) }
            AppLog.error("Falha ao copiar entre unidades: $source -> $target", error)
            throw error
        }
        try {
            Files.delete(source)
        } catch (error: IOException) {
            // Não conseguiu apagar o original (ex.: aberto em outro programa): desfaz a cópia para não duplicar.
            runCatching { Files.delete(target) }
            AppLog.error("Original não pôde ser apagado; cópia desfeita: $source", error)
            throw error
        }
        AppLog.info("Movido entre unidades com verificação: $source -> $target")
    }

    private fun sha256(file: Path): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(file).use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest()
    }

    private companion object {
        const val BUFFER_SIZE = 64 * 1024

        fun differentVolumes(source: Path, targetFolder: Path): Boolean =
            runCatching { Files.getFileStore(source) != Files.getFileStore(targetFolder) }.getOrDefault(true)
    }
}

/** A cópia entre unidades não ficou idêntica ao original. */
class CopyVerificationException(source: Path, target: Path) :
    IOException("A cópia de $source para $target não ficou idêntica ao original")
