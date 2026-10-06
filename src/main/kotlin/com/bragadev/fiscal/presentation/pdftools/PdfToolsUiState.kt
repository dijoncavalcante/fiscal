package com.bragadev.fiscal.presentation.pdftools

import com.bragadev.fiscal.domain.model.ImagePage
import com.bragadev.fiscal.presentation.common.UserMessage
import java.nio.file.Path

enum class PdfTool { IMAGES_TO_PDF, MERGE_PDFS }

/** Menu PDF: converter imagens em PDF e juntar PDFs. */
data class PdfToolsUiState(
    val dialog: PdfToolDialog? = null,
    val isWorking: Boolean = false,
    val message: UserMessage? = null,
)

/**
 * Janela da ferramenta: a lista ordenada de arquivos de entrada (com rotação, para imagens),
 * o item mostrado na visualização e onde o PDF novo será salvo.
 */
data class PdfToolDialog(
    val tool: PdfTool,
    val items: List<ImagePage> = emptyList(),
    val selectedIndex: Int = -1,
    val outputFolder: Path? = null,
    val outputName: String = "",
    val error: String? = null,
) {
    val selected: ImagePage? get() = items.getOrNull(selectedIndex)
}
