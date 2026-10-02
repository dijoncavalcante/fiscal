package com.bragadev.fiscal.presentation.common

import com.bragadev.fiscal.domain.model.FileOperationError
import com.bragadev.fiscal.domain.model.UndoBlockReason

/** Mensagens amigáveis para os erros de domínio. Nunca mostra detalhes técnicos. */
fun FileOperationError.toUserMessage(): String = when (this) {
    FileOperationError.FileNotFound -> "O arquivo não foi encontrado. Ele pode ter sido movido ou apagado."
    FileOperationError.PermissionDenied -> "Sem permissão para alterar este arquivo ou pasta."
    FileOperationError.FileLocked -> "O arquivo está aberto em outro programa. Feche-o e tente novamente."
    FileOperationError.DestinationAlreadyExists -> "Já existe um arquivo com este nome no destino."
    FileOperationError.DuplicatesNotAllowed ->
        "Já existe um arquivo com este nome no destino e as configurações não permitem duplicados."
    FileOperationError.InvalidFileName -> "O nome do arquivo não é válido."
    FileOperationError.InvalidPdf -> "Este arquivo não é um PDF válido."
    FileOperationError.RootNotConfigured -> "Selecione a pasta raiz antes de continuar."
    FileOperationError.RootNotFound -> "A pasta selecionada não foi encontrada."
    FileOperationError.CategoryNotFound -> "A categoria escolhida não existe mais."
    FileOperationError.AlreadyInPlace -> Strings.ALREADY_ORGANIZED
    FileOperationError.ReadError -> "Não foi possível ler o arquivo."
    FileOperationError.MoveError -> "Não foi possível mover o arquivo."
    is FileOperationError.UndoNotPossible -> reason.toUserMessage()
    is FileOperationError.Unknown -> "Ocorreu um erro inesperado. Nenhum arquivo foi alterado."
}

private fun UndoBlockReason.toUserMessage(): String = when (this) {
    UndoBlockReason.ALREADY_UNDONE -> "Esta operação já foi desfeita."
    UndoBlockReason.OPERATION_NOT_FOUND -> "A operação não foi encontrada no histórico."
    UndoBlockReason.CURRENT_FILE_MISSING -> "Não é possível desfazer: o arquivo não está mais no local organizado."
    UndoBlockReason.ORIGINAL_LOCATION_OCCUPIED ->
        "Não é possível desfazer: já existe outro arquivo com o nome original no local de origem."
    UndoBlockReason.BACKUP_MISSING -> "Não é possível desfazer: o backup do arquivo substituído não foi encontrado."
}
