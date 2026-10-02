package com.bragadev.fiscal.presentation.common

import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** Textos da interface concentrados em um único lugar. */
object Strings {
    const val APP_TITLE = "Organizador de Documentos"
    private val ptBr: Locale = Locale.forLanguageTag("pt-BR")

    // Barra superior
    const val SETTINGS = "Configurações"
    const val UNDO_LAST = "Desfazer última"
    const val REFRESH = "Atualizar"

    // Pasta de origem (esquerda)
    const val SOURCE_FOLDER_LABEL = "Pasta de origem"
    const val SOURCE_FOLDER_EDIT = "Escolher a pasta do computador para visualizar"
    const val SOURCE_FOLDER_PICKER_TITLE = "Escolha a pasta com os PDFs que deseja visualizar"
    const val SOURCE_FOLDER_EMPTY = "Nenhuma pasta selecionada"
    const val SOURCE_FOLDER_SELECT = "Clique no lápis para escolher uma pasta do computador."
    const val SOURCE_FOLDER_MISSING = "Pasta não encontrada. Conecte o pendrive ou escolha outra pasta."

    // Pasta do mês (direita)
    const val MONTH_PANEL = "MÊS EM EDIÇÃO"
    const val MONTH_FOLDER_LABEL = "Caminho completo da pasta do mês"
    const val MONTH_FOLDER_EDIT = "Trocar a pasta do mês em edição"
    const val MONTH_FOLDER_PICKER_TITLE = "Escolha a pasta do mês que deseja editar"
    const val MONTH_FOLDER_EMPTY = "Nenhuma pasta de mês selecionada"
    const val MONTH_NOT_SELECTED = "Selecione a pasta do mês"
    const val MONTH_NOT_SELECTED_HINT = "Clique no lápis e escolha a pasta de um mês, por exemplo \"1. JUNHO\"."
    const val MONTH_UNKNOWN = "Mês não identificado"
    const val MONTH_UNKNOWN_HINT = "Esta pasta não é a pasta de um mês. Escolha a pasta do mês (ex.: \"1. JUNHO\")."
    const val STATUS_EDITABLE = "✓ Liberado para edição"
    const val STATUS_LOCKED = "🔒 Somente leitura"

    fun monthTitle(month: YearMonth): String =
        "${month.month.getDisplayName(TextStyle.FULL, ptBr).replaceFirstChar { it.titlecase(ptBr) }} de ${month.year}"

    fun lockedHint(firstEditable: YearMonth) =
        "Meses anteriores a ${monthTitle(firstEditable)} não podem ser alterados."

    fun accountLabel(account: String) = "Conta: $account"

    // Documentos
    const val DOCUMENTS = "DOCUMENTOS"
    const val CATEGORIES = "CATEGORIAS"
    const val NO_DOCUMENTS = "Nenhum PDF nesta pasta."
    const val LOADING = "Carregando..."

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
    const val MONTH = "Mês:"
    const val CURRENT_FOLDER = "Somente renomear (fica na pasta atual):"
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

    fun organizeTitle(category: String) = "Organizar como \"$category\""
    fun conflictMessage(fileName: String) = "O arquivo \"$fileName\" já existe."
    fun numberedCopy(fileName: String) = "Criar cópia numerada ($fileName)"

    // Resultado
    const val ORGANIZED = "✓ Documento organizado."
    const val UNDONE = "✓ Operação desfeita."
    const val UNDO = "Desfazer"

    // Configurações
    const val BACK = "← Voltar"
    const val SETTINGS_FOLDERS_SECTION = "Pastas"
    const val SETTINGS_DUPLICATES_SECTION = "Arquivos duplicados"
    const val SETTINGS_DUPLICATE_ASK = "Perguntar sempre"
    const val SETTINGS_DUPLICATE_AUTO = "Criar cópia numerada automaticamente"
    const val SETTINGS_DUPLICATE_FORBID = "Não permitir duplicados"
    const val SETTINGS_CONFIRM_SECTION = "Confirmação"
    const val SETTINGS_CONFIRM_MOVE = "Confirmar antes de mover arquivos"
    const val SETTINGS_CONFIRM_RENAME = "Confirmar antes de renomear arquivos"
    const val SETTINGS_CONFIRM_NOTE =
        "Mesmo sem esta confirmação, o destino e o novo nome são sempre mostrados antes de qualquer alteração."
    const val SETTINGS_LOCK_SECTION = "Proteção de meses"
}
