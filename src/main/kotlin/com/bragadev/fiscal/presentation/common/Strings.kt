package com.bragadev.fiscal.presentation.common

/** Textos da interface concentrados em um único lugar. */
object Strings {
    const val APP_TITLE = "Organizador de Documentos"

    // Barra superior e pasta raiz
    const val ROOT_LABEL = "Pasta raiz:"
    const val ROOT_CHANGE = "Alterar"
    const val ROOT_SELECT = "Selecionar pasta raiz"
    const val ROOT_NOT_CONFIGURED_TITLE = "Nenhuma pasta raiz selecionada"
    const val ROOT_NOT_CONFIGURED_BODY = "Escolha a pasta onde estão os documentos PDF para começar."
    const val ROOT_PICKER_TITLE = "Selecione a pasta raiz dos documentos"
    const val SETTINGS = "Configurações"
    const val UNDO_LAST = "Desfazer última"
    const val REFRESH = "Atualizar"

    fun rootMissing(path: String) = "A pasta \"$path\" não foi encontrada. Conecte o pendrive ou selecione outra pasta."

    // Documentos
    const val DOCUMENTS = "DOCUMENTOS"
    const val CATEGORIES = "CATEGORIAS"
    const val NO_DOCUMENTS = "Nenhum PDF encontrado nesta pasta."
    const val LOADING = "Carregando..."
    const val ROOT_FOLDER_LABEL = "(pasta raiz)"

    fun statusDocuments(count: Int) = when (count) {
        0 -> "Status: nenhum documento encontrado"
        1 -> "Status: 1 documento encontrado"
        else -> "Status: $count documentos encontrados"
    }

    // Categorias
    const val DROP_HINT = "Arraste um PDF para uma categoria, ou selecione um documento e clique na categoria."
    const val DROP_HERE = "Solte o arquivo aqui"
    const val SELECT_OR_DRAG = "Selecione um documento na lista ou arraste um PDF até a categoria."
    const val ONE_FILE_AT_A_TIME = "Arraste um arquivo por vez."

    // Preview
    const val PREVIEW = "PREVIEW"
    const val PREVIEW_EMPTY = "Selecione um documento para visualizar."
    const val FIT_WIDTH = "Ajustar à largura"
    const val FIT_PAGE = "Ajustar à página"
    const val PREVIOUS = "<"
    const val NEXT = ">"
    const val ZOOM_IN = "+"
    const val ZOOM_OUT = "-"

    fun pageOf(current: Int, total: Int) = "Página $current de $total"
    fun pageShort(current: Int, total: Int) = "Página $current / $total"
    fun zoomPercent(zoom: Float) = "${(zoom * 100).toInt()}%"

    // Diálogos de organização
    const val CURRENT_FILE = "Arquivo atual:"
    const val NEW_NAME = "Novo nome:"
    const val DESTINATION = "Destino:"
    const val CURRENT_FOLDER = "Pasta atual (somente renomear):"
    const val CANCEL = "Cancelar"
    const val RENAME = "Renomear"
    const val RENAME_AND_MOVE = "Renomear e Mover"
    const val CONFIRM = "Confirmar"
    const val CONTINUE = "Continuar"
    const val CONFIRM_TITLE = "Confirmar operação?"
    const val CONFLICT_TITLE = "Arquivo já existe"
    const val CONFLICT_CHOOSE = "Escolha uma opção:"
    const val REPLACE = "Substituir"
    const val CANCEL_OPERATION = "Cancelar"
    const val REPLACE_NOTE = "O arquivo existente será guardado em backup e pode ser restaurado com \"Desfazer\"."
    const val ALREADY_ORGANIZED = "Este documento já está com o nome e o local corretos."

    fun organizeTitle(category: String) = "Organizar em \"$category\""
    fun conflictMessage(fileName: String) = "O arquivo \"$fileName\" já existe."
    fun numberedCopy(fileName: String) = "Criar cópia numerada ($fileName)"

    // Resultado
    const val ORGANIZED = "✓ Documento organizado."
    const val UNDONE = "✓ Operação desfeita."
    const val UNDO = "Desfazer"

    // Configurações
    const val BACK = "← Voltar"
    const val SETTINGS_ROOT_SECTION = "Pasta raiz"
    const val SETTINGS_CURRENT_FOLDER = "Pasta atual:"
    const val SETTINGS_NO_FOLDER = "(nenhuma)"
    const val SETTINGS_CHANGE_FOLDER = "Alterar pasta"
    const val SETTINGS_DUPLICATES_SECTION = "Arquivos duplicados"
    const val SETTINGS_DUPLICATE_ASK = "Perguntar sempre"
    const val SETTINGS_DUPLICATE_AUTO = "Criar cópia numerada automaticamente"
    const val SETTINGS_DUPLICATE_FORBID = "Não permitir duplicados"
    const val SETTINGS_CONFIRM_SECTION = "Confirmação"
    const val SETTINGS_CONFIRM_MOVE = "Confirmar antes de mover arquivos"
    const val SETTINGS_CONFIRM_RENAME = "Confirmar antes de renomear arquivos"
    const val SETTINGS_CONFIRM_NOTE =
        "Mesmo sem esta confirmação, o destino e o novo nome são sempre mostrados antes de qualquer alteração."
}
