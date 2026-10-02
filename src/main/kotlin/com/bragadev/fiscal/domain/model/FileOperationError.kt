package com.bragadev.fiscal.domain.model

sealed class FileOperationError {
    data object FileNotFound : FileOperationError()
    data object PermissionDenied : FileOperationError()
    data object FileLocked : FileOperationError()
    data object DestinationAlreadyExists : FileOperationError()
    data object DuplicatesNotAllowed : FileOperationError()
    data object InvalidFileName : FileOperationError()
    data object InvalidPdf : FileOperationError()
    data object RootNotConfigured : FileOperationError()
    data object RootNotFound : FileOperationError()
    data object CategoryNotFound : FileOperationError()
    data object AlreadyInPlace : FileOperationError()
    data object ReadError : FileOperationError()
    data object MoveError : FileOperationError()
    data class UndoNotPossible(val reason: UndoBlockReason) : FileOperationError()
    data class Unknown(val cause: Throwable) : FileOperationError()
}

enum class UndoBlockReason {
    ALREADY_UNDONE,
    OPERATION_NOT_FOUND,
    CURRENT_FILE_MISSING,
    ORIGINAL_LOCATION_OCCUPIED,
    BACKUP_MISSING,
}
