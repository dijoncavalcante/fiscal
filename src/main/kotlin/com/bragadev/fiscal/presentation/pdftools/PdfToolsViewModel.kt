package com.bragadev.fiscal.presentation.pdftools

import com.bragadev.fiscal.domain.model.ImagePage
import com.bragadev.fiscal.domain.model.Outcome
import com.bragadev.fiscal.domain.rules.FileNameRules
import com.bragadev.fiscal.domain.rules.ImageFileRules
import com.bragadev.fiscal.domain.usecase.CreatePdfFromImagesUseCase
import com.bragadev.fiscal.domain.usecase.MergePdfsUseCase
import com.bragadev.fiscal.domain.usecase.ObserveSettingsUseCase
import com.bragadev.fiscal.presentation.common.DocumentChangeNotifier
import com.bragadev.fiscal.presentation.common.Strings
import com.bragadev.fiscal.presentation.common.UserMessage
import com.bragadev.fiscal.presentation.common.ViewModel
import com.bragadev.fiscal.presentation.common.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.nio.file.Path

/** Menu PDF: converter imagens em PDF e juntar PDFs, salvando por padrão na pasta de origem. */
class PdfToolsViewModel(
    private val createPdfFromImages: CreatePdfFromImagesUseCase,
    private val mergePdfs: MergePdfsUseCase,
    private val observeSettings: ObserveSettingsUseCase,
    private val documentChanges: DocumentChangeNotifier,
) : ViewModel() {
    private val state = MutableStateFlow(PdfToolsUiState())
    val uiState: StateFlow<PdfToolsUiState> = state.asStateFlow()

    /** Pasta inicial dos seletores de arquivo. */
    val browseFolder: Path? get() = state.value.dialog?.outputFolder ?: observeSettings().value.sourceFolder

    fun onOpenImagesToPdf() = open(PdfTool.IMAGES_TO_PDF, initialFiles = emptyList())

    /** Abre "Juntar PDFs" já com o documento selecionado na lista, se houver. */
    fun onOpenMerge(selectedDocument: Path?) = open(PdfTool.MERGE_PDFS, listOfNotNull(selectedDocument))

    /** Arquivos vindos do seletor, da lista de documentos ou do Windows Explorer (arrastar e soltar). */
    fun onFilesAdded(paths: List<Path>) = editDialog { dialog ->
        val accepted = paths.filter { accepts(dialog.tool, it) }
        val newOnes = accepted.filter { path -> dialog.items.none { it.path == path } }.distinct()
        val items = dialog.items + newOnes.map(::ImagePage)
        val warning = when {
            accepted.size < paths.size ->
                if (dialog.tool == PdfTool.IMAGES_TO_PDF) Strings.ONLY_IMAGES_ACCEPTED else Strings.ONLY_PDFS_ACCEPTED
            newOnes.size < accepted.size -> Strings.ALREADY_IN_LIST
            else -> null
        }
        dialog.copy(
            items = items,
            selectedIndex = if (newOnes.isNotEmpty()) items.lastIndex else dialog.selectedIndex,
            outputName = dialog.outputName.ifBlank { defaultName(dialog.tool, items) },
            error = warning,
        )
    }

    fun onItemSelected(index: Int) = editDialog { it.copy(selectedIndex = index) }

    /** Reordenação por arrastar (alça ⠿) ou pelos botões ↑ ↓. */
    fun onMove(from: Int, to: Int) = editDialog { it.moved(from, to) }

    fun onMoveUp(index: Int) = onMove(index, index - 1)

    fun onMoveDown(index: Int) = onMove(index, index + 1)

    fun onRotate(index: Int) = editDialog { dialog ->
        dialog.copy(items = dialog.items.mapIndexed { i, item -> if (i == index) item.rotatedClockwise() else item })
    }

    fun onRemove(index: Int) = editDialog { dialog ->
        val items = dialog.items.filterIndexed { i, _ -> i != index }
        dialog.copy(items = items, selectedIndex = dialog.selectedIndex.coerceAtMost(items.lastIndex))
    }

    fun onOutputNameChanged(name: String) = editDialog { it.copy(outputName = name) }

    fun onOutputFolderSelected(folder: Path) = editDialog { it.copy(outputFolder = folder) }

    fun onConfirm() {
        val dialog = state.value.dialog ?: return
        val folder = dialog.outputFolder ?: return editDialog { it.copy(error = Strings.SOURCE_FOLDER_SELECT) }
        scope.launch {
            state.update { it.copy(isWorking = true) }
            val result = when (dialog.tool) {
                PdfTool.IMAGES_TO_PDF -> createPdfFromImages(dialog.items, folder, dialog.outputName)
                PdfTool.MERGE_PDFS -> mergePdfs(dialog.items.map { it.path }, folder, dialog.outputName)
            }
            when (result) {
                is Outcome.Success -> {
                    state.update { it.copy(dialog = null, message = UserMessage(Strings.pdfCreated(result.value.fileName.toString()))) }
                    // O PDF novo aparece na lista e abre no preview.
                    documentChanges.notifyChanged(selectPath = result.value)
                }
                is Outcome.Failure -> editDialog { it.copy(error = result.error.toUserMessage()) }
            }
            state.update { it.copy(isWorking = false) }
        }
    }

    fun dismissDialog() {
        state.update { it.copy(dialog = null) }
    }

    fun onMessageShown() {
        state.update { it.copy(message = null) }
    }

    private fun open(tool: PdfTool, initialFiles: List<Path>) {
        val items = initialFiles.filter { accepts(tool, it) }.map(::ImagePage)
        state.update {
            it.copy(
                dialog = PdfToolDialog(
                    tool = tool,
                    items = items,
                    selectedIndex = if (items.isEmpty()) -1 else 0,
                    outputFolder = observeSettings().value.sourceFolder,
                    outputName = defaultName(tool, items),
                ),
            )
        }
    }

    /** Qualquer edição limpa o erro anterior; só a própria edição pode definir um erro novo. */
    private fun editDialog(transform: (PdfToolDialog) -> PdfToolDialog) {
        state.update { current -> current.dialog?.let { current.copy(dialog = transform(it.copy(error = null))) } ?: current }
    }

    private fun PdfToolDialog.moved(from: Int, to: Int): PdfToolDialog {
        if (from !in items.indices || to !in items.indices) return this
        val reordered = items.toMutableList().apply { add(to, removeAt(from)) }
        return copy(items = reordered, selectedIndex = to)
    }

    private fun accepts(tool: PdfTool, path: Path): Boolean {
        val name = path.fileName.toString()
        return when (tool) {
            PdfTool.IMAGES_TO_PDF -> ImageFileRules.isSupported(name)
            PdfTool.MERGE_PDFS -> FileNameRules.hasPdfExtension(name)
        }
    }

    private fun defaultName(tool: PdfTool, items: List<ImagePage>): String {
        val first = items.firstOrNull()?.path?.fileName?.toString() ?: return ""
        val base = first.substringBeforeLast('.')
        return if (tool == PdfTool.MERGE_PDFS) base + Strings.MERGED_SUFFIX else base
    }
}
