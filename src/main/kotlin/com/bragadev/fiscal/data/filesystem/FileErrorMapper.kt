package com.bragadev.fiscal.data.filesystem

import com.bragadev.fiscal.data.logging.AppLog
import com.bragadev.fiscal.domain.model.FileOperationError
import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.FileSystemException
import java.nio.file.InvalidPathException
import java.nio.file.NoSuchFileException

/** Converte exceções do Java NIO em erros de domínio, sem expor detalhes técnicos à interface. */
object FileErrorMapper {
    private val lockedFileHints = listOf("another process", "outro processo", "being used", "sendo usado")

    fun map(error: Throwable): FileOperationError = classify(error).also { mapped ->
        if (mapped is FileOperationError.Unknown) {
            AppLog.error("Erro inesperado em operação de arquivo", error)
        } else {
            AppLog.warn("Operação de arquivo recusada (${mapped::class.simpleName}): ${error.message}")
        }
    }

    private fun classify(error: Throwable): FileOperationError = when (error) {
        is NoSuchFileException -> FileOperationError.FileNotFound
        is FileAlreadyExistsException -> FileOperationError.DestinationAlreadyExists
        is CopyVerificationException -> FileOperationError.CopyVerificationFailed
        is AccessDeniedException -> FileOperationError.PermissionDenied
        is FileSystemException -> if (isLocked(error)) FileOperationError.FileLocked else FileOperationError.MoveError
        is InvalidPathException -> FileOperationError.InvalidFileName
        is SecurityException -> FileOperationError.PermissionDenied
        is IOException -> FileOperationError.ReadError
        else -> FileOperationError.Unknown(error)
    }

    private fun isLocked(error: FileSystemException): Boolean {
        val reason = error.reason?.lowercase() ?: return false
        return lockedFileHints.any { it in reason }
    }
}
