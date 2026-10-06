package com.bragadev.fiscal.data.pdf

import com.bragadev.fiscal.domain.model.MonthReview
import com.bragadev.fiscal.domain.model.ReviewLine
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import java.awt.Color
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Desenha o relatório "Concluir mês" num PDF A4: cabeçalho com mês, conta e pasta; resumo (completo ou
 * o que falta); cada categoria com a situação e os arquivos; arquivos sem categoria; rodapé com páginas.
 *
 * Usa as fontes padrão do PDF (Helvetica), que cobrem o português. Caracteres fora delas (ex.: emoji
 * no nome de um arquivo) viram "?" no relatório — o arquivo em si não é tocado.
 */
internal class MonthReportLayout(private val document: PDDocument) {
    private val regular = PDType1Font(Standard14Fonts.FontName.HELVETICA)
    private val bold = PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
    private var content: PDPageContentStream? = null
    private var y = 0f

    fun render(review: MonthReview) {
        newPage()
        header(review)
        summary(review)
        section(SECTION_CATEGORIES)
        review.lines.forEach(::categoryLine)
        if (review.unmatchedFiles.isNotEmpty()) {
            section(SECTION_UNMATCHED)
            review.unmatchedFiles.forEach { name ->
                paragraph("•  $name", regular, BODY_SIZE, TEXT, indent = 12f)
                review.unmatchedIssues[name]?.let { paragraph("Pendência: $it", bold, SMALL_SIZE, RED, indent = 26f) }
            }
        }
        content?.close()
        footers()
    }

    private fun header(review: MonthReview) {
        paragraph("Relatório do mês — ${monthTitle(review)}", bold, TITLE_SIZE, TEXT, spacing = 6f)
        review.account?.let { paragraph(it.displayName, bold, SUBTITLE_SIZE, BLUE, spacing = 4f) }
        paragraph("Pasta: ${review.folder}", regular, SMALL_SIZE, GREY)
        paragraph("Gerado em ${DATE_TIME.format(review.generatedAt)} pelo FISCAL - Organizador de Documentos PDF", regular, SMALL_SIZE, GREY)
        y -= 10f
    }

    private fun summary(review: MonthReview) {
        if (review.isComplete) {
            paragraph("Situação: COMPLETO — todas as categorias têm arquivo e não há pendências.", bold, BODY_SIZE, GREEN)
        } else {
            val parts = buildList {
                if (review.missing.isNotEmpty()) add(plural(review.missing.size, "categoria faltando", "categorias faltando"))
                if (review.issueCount > 0) add(plural(review.issueCount, "pendência", "pendências"))
            }
            paragraph("Situação: INCOMPLETO — ${parts.joinToString(" e ")}.", bold, BODY_SIZE, RED)
        }
        paragraph("Categorias obrigatórias com arquivo: ${review.doneCount} de ${review.requiredCount}.", regular, BODY_SIZE, TEXT)
        review.missing.takeIf { it.isNotEmpty() }?.let { missing ->
            paragraph("Faltando: ${missing.joinToString { it.category.label }}.", regular, BODY_SIZE, AMBER)
        }
        y -= 6f
    }

    private fun categoryLine(line: ReviewLine) {
        val (tag, color) = when {
            line.issues.isNotEmpty() -> "PENDÊNCIA" to RED
            line.files.isNotEmpty() -> "OK" to GREEN
            line.category.optional -> "OPCIONAL" to GREY
            else -> "FALTANDO" to AMBER
        }
        val indent = line.depth * 16f
        ensureSpace(BODY_SIZE * LINE_FACTOR * 2)
        y -= 4f
        write(tag, bold, SMALL_SIZE, color, MARGIN + indent, y - BODY_SIZE * LINE_FACTOR)
        paragraph(line.category.label, bold, BODY_SIZE, TEXT, indent = indent + TAG_WIDTH)
        line.files.forEach { name ->
            paragraph("•  $name", regular, SMALL_SIZE, TEXT, indent = indent + TAG_WIDTH + 8f)
            line.issues[name]?.let { paragraph("Pendência: $it", bold, SMALL_SIZE, RED, indent = indent + TAG_WIDTH + 20f) }
        }
        if (line.files.isEmpty()) {
            val note = if (line.category.optional) "Nenhum arquivo (opcional)." else "Nenhum arquivo nesta categoria."
            paragraph(note, regular, SMALL_SIZE, GREY, indent = indent + TAG_WIDTH + 8f)
        }
    }

    private fun section(title: String) {
        ensureSpace(SUBTITLE_SIZE * 3)
        y -= 8f
        paragraph(title, bold, SUBTITLE_SIZE, BLUE, spacing = 2f)
        content!!.apply {
            setStrokingColor(LIGHT)
            setLineWidth(0.7f)
            moveTo(MARGIN, y)
            lineTo(PAGE.width - MARGIN, y)
            stroke()
        }
        y -= 6f
    }

    /** Texto com quebra de linha automática e nova página quando acaba o espaço. */
    private fun paragraph(text: String, font: PDFont, size: Float, color: Color, indent: Float = 0f, spacing: Float = 2f) {
        val width = PAGE.width - 2 * MARGIN - indent
        wrap(safe(text, font), font, size, width).forEach { line ->
            ensureSpace(size * LINE_FACTOR)
            y -= size * LINE_FACTOR
            write(line, font, size, color, MARGIN + indent, y)
        }
        y -= spacing
    }

    private fun write(text: String, font: PDFont, size: Float, color: Color, x: Float, baseline: Float) {
        content!!.apply {
            beginText()
            setFont(font, size)
            setNonStrokingColor(color)
            newLineAtOffset(x, baseline)
            showText(safe(text, font))
            endText()
        }
    }

    private fun ensureSpace(needed: Float) {
        if (y - needed < MARGIN + FOOTER_SPACE) newPage()
    }

    private fun newPage() {
        content?.close()
        val page = PDPage(PAGE)
        document.addPage(page)
        content = PDPageContentStream(document, page)
        y = PAGE.height - MARGIN
    }

    private fun footers() {
        val total = document.numberOfPages
        document.pages.forEachIndexed { index, page ->
            PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
                stream.beginText()
                stream.setFont(regular, FOOTER_SIZE)
                stream.setNonStrokingColor(GREY)
                stream.newLineAtOffset(MARGIN, MARGIN / 2)
                stream.showText("FISCAL - Organizador de Documentos PDF        Página ${index + 1} de $total")
                stream.endText()
            }
        }
    }

    private fun wrap(text: String, font: PDFont, size: Float, maxWidth: Float): List<String> {
        fun width(s: String) = font.getStringWidth(s) / 1000f * size
        val lines = mutableListOf<String>()
        var current = ""
        // Palavras compridas demais (caminhos de pasta) são quebradas no meio.
        val words = text.split(' ').flatMap { word -> if (width(word) <= maxWidth) listOf(word) else splitLong(word, ::width, maxWidth) }
        words.forEach { word ->
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (width(candidate) <= maxWidth || current.isEmpty()) {
                current = candidate
            } else {
                lines += current
                current = word
            }
        }
        if (current.isNotEmpty() || lines.isEmpty()) lines += current
        return lines
    }

    private fun splitLong(word: String, width: (String) -> Float, maxWidth: Float): List<String> {
        val parts = mutableListOf<String>()
        var part = ""
        word.forEach { char ->
            if (part.isNotEmpty() && width(part + char) > maxWidth) {
                parts += part
                part = ""
            }
            part += char
        }
        if (part.isNotEmpty()) parts += part
        return parts
    }

    /** Troca por "?" o que a fonte não consegue desenhar (o PDFBox recusaria o texto inteiro). */
    private fun safe(text: String, font: PDFont): String = buildString {
        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            val char = String(Character.toChars(codePoint))
            append(if (runCatching { font.encode(char) }.isSuccess) char else "?")
            index += Character.charCount(codePoint)
        }
    }

    private fun monthTitle(review: MonthReview): String {
        val name = review.month.month.getDisplayName(TextStyle.FULL, PT_BR).replaceFirstChar { it.titlecase(PT_BR) }
        return "$name de ${review.month.year}"
    }

    private fun plural(count: Int, singular: String, plural: String) = "$count ${if (count == 1) singular else plural}"

    private companion object {
        val PAGE: PDRectangle = PDRectangle.A4
        const val MARGIN = 50f
        const val FOOTER_SPACE = 20f
        const val TAG_WIDTH = 74f
        const val LINE_FACTOR = 1.35f
        const val TITLE_SIZE = 18f
        const val SUBTITLE_SIZE = 13f
        const val BODY_SIZE = 11f
        const val SMALL_SIZE = 9.5f
        const val FOOTER_SIZE = 8f
        const val SECTION_CATEGORIES = "Categorias"
        const val SECTION_UNMATCHED = "Arquivos sem número de categoria"
        val PT_BR: Locale = Locale.forLanguageTag("pt-BR")
        val DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm")
        val TEXT = Color(0x1F, 0x23, 0x28)
        val GREY = Color(0x6B, 0x72, 0x80)
        val LIGHT = Color(0xD0, 0xD5, 0xDD)
        val BLUE = Color(0x1F, 0x5F, 0xAD)
        val GREEN = Color(0x1B, 0x7F, 0x3B)
        val AMBER = Color(0x8A, 0x5A, 0x00)
        val RED = Color(0xB3, 0x26, 0x1E)
    }
}
