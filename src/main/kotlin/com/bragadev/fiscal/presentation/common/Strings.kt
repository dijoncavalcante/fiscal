package com.bragadev.fiscal.presentation.common

import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** Textos da interface concentrados em um único lugar. */
object Strings {
    const val APP_TITLE = "FISCAL - Organizador de Documentos PDF"
    private val ptBr: Locale = Locale.forLanguageTag("pt-BR")

    // Barra superior
    const val SETTINGS = "Configurações"
    const val UNDO_LAST = "Desfazer última"
    const val REFRESH = "Atualizar"
    const val UNDO_SHORTCUT = "Atalho: Ctrl+Z"
    const val REFRESH_SHORTCUT = "Atalho: F5"

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
    const val STATUS_EDITABLE = "Liberado para edição"
    const val STATUS_LOCKED = "Somente leitura"

    fun monthTitle(month: YearMonth): String =
        "${month.month.getDisplayName(TextStyle.FULL, ptBr).replaceFirstChar { it.titlecase(ptBr) }} de ${month.year}"

    fun lockedHint(firstEditable: YearMonth) =
        "Meses anteriores a ${monthTitle(firstEditable)} não podem ser alterados."

    fun accountLabel(account: String) = "Conta: $account"

    // Seletor de mês
    const val NAVIGATOR_TITLE = "Escolher mês"
    const val MONTHS_ROOT_LABEL = "Pasta raiz das contas"
    const val MONTHS_ROOT_EDIT = "Trocar a pasta raiz das contas"
    const val MONTHS_ROOT_PICKER_TITLE = "Escolha a pasta que contém CONTAS CONGREGAÇÃO e CONTAS MANUTENÇÃO"
    const val MONTHS_ROOT_EMPTY = "Nenhuma pasta raiz escolhida"
    const val MONTHS_ROOT_HINT = "Clique no lápis e escolha a pasta que contém CONTAS CONGREGAÇÃO e CONTAS MANUTENÇÃO."
    const val NO_MONTHS_FOUND = "Nenhuma pasta de mês encontrada nesta pasta raiz."
    const val SERVICE_YEAR = "Ano de serviço"

    fun monthName(month: YearMonth): String = month.month.getDisplayName(TextStyle.FULL, ptBr).replaceFirstChar { it.titlecase(ptBr) }

    // Documentos
    const val DOCUMENTS = "DOCUMENTOS"
    const val CATEGORIES = "CATEGORIAS"
    const val NO_DOCUMENTS = "Nenhum PDF ou imagem nesta pasta."
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
    const val FILE_MISSING = "Faltando"
    const val FILE_PRESENT = "Já existe"
    const val UNMATCHED_FILES = "Arquivos sem número de categoria"
    const val EXPAND = "Expandir"
    const val COLLAPSE = "Recolher"

    fun groupSummary(present: Int, total: Int) = "$present de $total com arquivo"
    fun sequentialCount(count: Int) = when (count) {
        0 -> "Nenhum arquivo"
        1 -> "1 arquivo"
        else -> "$count arquivos"
    }

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
    const val DESCRIPTION_HINT = "Ex.: Compra de cartazes para o display"

    const val DESCRIPTION_TITLE = "Descrição (vai no nome do arquivo)"

    fun organizeTitle(category: String) = "Organizar como \"$category\""
    fun conflictMessage(fileName: String) = "O arquivo \"$fileName\" já existe."
    fun numberedCopy(fileName: String) = "Criar cópia numerada ($fileName)"

    // Ações nos arquivos do mês
    const val FILE_ACTIONS = "Mais ações"
    const val RENAME_FILE = "Renomear arquivo"
    const val RENAME_FILE_TITLE = "Renomear arquivo"
    const val NEW_FILE_NAME = "Novo nome do arquivo"
    const val NAME_PREVIEW = "Vai ficar assim:"
    const val RENAME_MODE_NUMBERED = "Número e descrição"
    const val RENAME_MODE_FULL = "Nome completo"
    const val NUMBER_LABEL = "Número"
    fun numberHint(number: String) = "Ex.: $number, $number.1, $number.2. Vazio = próximo livre."
    const val FULL_NAME_HINT = "Nome inteiro do arquivo, sem \".pdf\"."

    fun sameNumberWarning(files: List<String>) =
        "Atenção: este número já é usado por ${files.joinToString()}. Renomeie o outro depois para não repetir."
    const val NAME_TAKEN = "Já existe um arquivo com este nome nesta pasta."
    const val REMOVE_FROM_MONTH = "Retirar do mês"
    const val REMOVE_FROM_MONTH_TITLE = "Retirar arquivo da pasta do mês"
    const val REMOVE_EXPLANATION =
        "O arquivo sai da pasta do mês e volta para a pasta de origem. Nada é apagado e você pode desfazer."
    const val MOVE_TO = "Vai para:"
    const val REMOVE = "Retirar"
    const val MARK_ISSUE = "Marcar pendência"
    const val CLEAR_ISSUE = "Remover pendência"
    const val ISSUE_TITLE = "Marcar pendência no arquivo"
    const val ISSUE_NOTE = "O que está errado?"
    const val ISSUE_DEFAULT_NOTE = "Arquivo errado — trocar"
    const val ISSUE_EXPLANATION = "A pendência aparece em destaque no mês até ser removida. O arquivo não é alterado."
    const val MARK = "Marcar"
    const val STATUS_ISSUE = "Com pendência"
    const val PREVIEW_FILE = "Ver no preview"
    const val FILE_RENAMED = "Arquivo renomeado."
    const val FILE_REMOVED = "Arquivo retirado do mês."
    const val ISSUE_MARKED = "Pendência marcada."
    const val ISSUE_CLEARED = "Pendência removida."

    fun issueLabel(note: String) = "Pendência: $note"

    // Documentos: ordenação e busca
    const val REFRESH_FOLDER = "Atualizar a pasta"
    const val SORT_NEWEST = "Mais recentes"
    const val SORT_NAME = "Nome"
    const val SEARCH = "Buscar nesta pasta"
    const val NO_SEARCH_RESULTS = "Nenhum arquivo com esse nome."

    fun modifiedAt(text: String) = "Modificado em $text"

    // Menu PDF
    const val PDF_MENU = "PDF"
    const val IMAGES_TO_PDF = "Converter JPEG para PDF"
    const val MERGE_PDFS = "Juntar PDFs"
    const val ADD_IMAGES = "Adicionar imagens"
    const val ADD_PDFS = "Adicionar PDFs"
    const val IMAGES_PICKER_TITLE = "Escolha as imagens (JPEG ou PNG)"
    const val PDFS_PICKER_TITLE = "Escolha os PDFs para juntar"
    const val IMAGES_FILTER = "Imagens JPEG ou PNG"
    const val PDFS_FILTER = "Arquivos PDF"
    const val OUTPUT_FOLDER_PICKER_TITLE = "Escolha onde salvar o PDF"
    const val OUTPUT_FOLDER = "Salvar na pasta"
    const val OUTPUT_FOLDER_EDIT = "Trocar a pasta onde o PDF será salvo"
    const val OUTPUT_NAME = "Nome do PDF"
    const val CREATE_PDF = "Criar PDF"
    const val MOVE_UP = "Subir"
    const val MOVE_DOWN = "Descer"
    const val ROTATE = "Girar"
    const val REMOVE_ITEM = "Tirar da lista"
    const val IMAGES_TO_PDF_HINT = "Clique numa imagem à esquerda para ver no preview e arraste para cá. Cada imagem vira uma página A4, na ordem da lista; use o botão Girar para fotos deitadas."
    const val MERGE_HINT = "Clique num PDF à esquerda para ver no preview e arraste para cá. As páginas ficam na ordem da lista; os originais não são alterados."
    const val EMPTY_TOOL_LIST = "Nenhum arquivo na lista."
    const val DROP_IMAGES_HERE = "Arraste aqui as imagens da lista de documentos ou do Windows Explorer"
    const val DROP_PDFS_HERE = "Arraste aqui os PDFs da lista de documentos ou do Windows Explorer"
    const val DRAG_TO_REORDER = "Segure um item e arraste para cima ou para baixo para mudar a ordem"
    const val CLOSE_TOOL = "Fechar"
    const val ONLY_IMAGES_ACCEPTED = "Alguns arquivos foram ignorados: aqui só entram imagens JPEG ou PNG."
    const val ONLY_PDFS_ACCEPTED = "Alguns arquivos foram ignorados: aqui só entram PDFs."
    const val ALREADY_IN_LIST = "Arquivo já está na lista."
    const val MERGED_SUFFIX = " - unido"

    fun pdfCreated(name: String) = "PDF criado: $name"
    fun pageCountLabel(count: Int) = if (count == 1) "1 arquivo" else "$count arquivos"

    // Erros inesperados e dados
    const val UNEXPECTED_ERROR_TITLE = "Algo deu errado"
    const val UNEXPECTED_ERROR_BODY =
        "O FISCAL encontrou um problema inesperado. Nenhum arquivo é apagado por causa de um erro; confira o " +
            "mês em edição antes de continuar. Se o problema se repetir, clique em \"Copiar diagnóstico\" e envie o texto."
    const val COPY_DIAGNOSTICS = "Copiar diagnóstico"
    const val DIAGNOSTICS_COPIED = "Diagnóstico copiado. Cole onde quiser enviar."
    const val OPEN_LOG_FOLDER = "Abrir pasta de logs"
    const val DATA_SECTION = "Dados e segurança"
    const val BACKUPS_TITLE = "Arquivos substituídos (backup)"
    const val BACKUPS_EXPLANATION =
        "Ao escolher \"Substituir\", o arquivo antigo é guardado para o \"Desfazer\" funcionar. Eles ficam em %APPDATA%\\Fiscal\\backup."
    const val AUTO_CLEAN = "Apagar automaticamente, ao abrir o app, backups com mais de"
    const val DAYS = "dias"
    const val CLEAN_NOW = "Apagar backups antigos agora…"
    const val CLEAN_CONFIRM_TITLE = "Apagar backups antigos?"
    const val EXPORT_IMPORT_TITLE = "Cópia de segurança dos dados"
    const val EXPORT_IMPORT_EXPLANATION =
        "Guarda configurações, histórico e pendências num arquivo, para levar a outro computador. Os PDFs não entram na cópia."
    const val EXPORT_DATA = "Exportar dados…"
    const val IMPORT_DATA = "Importar dados…"
    const val EXPORT_PICKER_TITLE = "Onde salvar a cópia de segurança"
    const val IMPORT_PICKER_TITLE = "Escolha a cópia de segurança do FISCAL"
    const val BACKUP_FILE_FILTER = "Cópia de segurança do FISCAL (.db)"
    const val IMPORT_CONFIRM_TITLE = "Importar dados?"
    const val IMPORT_CONFIRM_BODY =
        "As configurações, o histórico e as pendências atuais serão substituídos pelos da cópia ao reabrir o app. " +
            "Os dados atuais ficam guardados em %APPDATA%\\Fiscal como \"fiscal-antes-da-importacao\". Os PDFs não são alterados."
    const val IMPORT_READY = "Cópia pronta. Feche e abra o FISCAL para concluir a importação."
    const val DIAGNOSTICS_TITLE = "Diagnóstico"
    const val DIAGNOSTICS_EXPLANATION = "Use se algo não funcionar: copia versão, sistema e o final do log, sem enviar nada."

    fun backupStats(count: Int, bytes: Long) = "$count arquivo(s), ${"%.1f".format(bytes / 1_048_576.0)} MB"
    fun cleanConfirmBody(days: Int) =
        "Os arquivos substituídos há mais de $days dias serão apagados de vez, e substituições antigas não poderão mais ser desfeitas."
    fun backupsCleaned(count: Int) = "$count backup(s) antigo(s) apagado(s)."
    fun dataExported(name: String) = "Dados exportados para $name"
    fun exportFileName(date: String) = "fiscal-dados-$date.db"

    // Resultado
    const val ORGANIZED = "Documento organizado."
    const val UNDONE = "Operação desfeita."
    const val UNDO = "Desfazer"

    // Configurações
    const val BACK = "Voltar"
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
    const val APPEARANCE_SECTION = "Aparência"
    const val THEME_SYSTEM = "Igual ao Windows"
    const val THEME_LIGHT = "Claro"
    const val THEME_DARK = "Escuro"
    const val OPEN_ONBOARDING = "Abrir o assistente de configuração"
    const val OPEN_ONBOARDING_NOTE = "Passo a passo para escolher a pasta raiz das contas, a pasta de origem e o mês."

    // Assistente da primeira vez
    const val ONBOARDING_WELCOME = "Bem-vindo ao FISCAL"
    const val ONBOARDING_INTRO = "Três passos rápidos para deixar tudo pronto. Dá para mudar qualquer escolha depois, pelo lápis ao lado de cada pasta."
    const val ONBOARDING_ROOT_TITLE = "Pasta raiz das contas"
    const val ONBOARDING_ROOT_TEXT =
        "É a pasta que contém CONTAS CONGREGAÇÃO e CONTAS MANUTENÇÃO, geralmente no pendrive. " +
            "Com ela, o FISCAL mostra todos os meses para você escolher com um clique."
    const val ONBOARDING_SOURCE_TITLE = "Pasta de origem"
    const val ONBOARDING_SOURCE_TEXT =
        "É a pasta do computador onde chegam os PDFs e as fotos a organizar, por exemplo Downloads ou a pasta do scanner. " +
            "Ela aparece à esquerda da tela principal."
    const val ONBOARDING_MONTH_TITLE = "Mês em edição"
    const val ONBOARDING_MONTH_TEXT =
        "Escolha o mês que você vai organizar agora. Os documentos vão para a pasta deste mês."
    const val ONBOARDING_NEXT = "Próximo"
    const val ONBOARDING_BACK = "Voltar"
    const val ONBOARDING_FINISH = "Concluir"
    const val ONBOARDING_SKIP = "Pular assistente"

    fun onboardingStep(current: Int, total: Int) = "Passo $current de $total"
    fun accountsFound(names: List<String>) = "Encontradas: ${names.joinToString()}"
}
