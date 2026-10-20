package com.bragadev.fiscal.data.ocr

import com.bragadev.fiscal.data.logging.AppLog
import com.bragadev.fiscal.domain.model.PageWord
import net.sourceforge.tess4j.ITessAPI
import net.sourceforge.tess4j.Tesseract
import net.sourceforge.tess4j.util.LoadLibs
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path

/** Reconhece o texto de uma imagem (página fotografada ou digitalizada). Lança exceção se não conseguir. */
fun interface OcrEngine {
    fun read(image: BufferedImage): String

    /**
     * Palavras com a posição na imagem (pixels) e a confiança de cada uma. [dpi] é a resolução em que a página
     * foi desenhada. Vazio quando o motor não sabe dar posições.
     */
    fun readWords(image: BufferedImage, dpi: Int, mode: OcrMode): List<PageWord> = emptyList()
}

/** Como o OCR procura o texto na página. */
enum class OcrMode(val pageSegMode: Int, val description: String) {
    /** Um bloco de texto só: o padrão do Tesseract usado pelo tess4j, o da leitura normal ([OcrEngine.read]). */
    SINGLE_BLOCK(6, "bloco único"),

    /** Divide a página em blocos sozinho (padrão do programa de linha de comando do Tesseract). */
    AUTO(3, "automático"),

    /** Uma coluna de texto: bom para cupom estreito. */
    SINGLE_COLUMN(4, "uma coluna"),

    /** Palavras soltas, sem ordem: bom para tabelas (o título longe do valor). */
    SPARSE(11, "palavras soltas"),
}

/**
 * OCR com Tesseract, 100% local: as DLLs e o modelo de idioma vêm dentro do tess4j (nada é baixado).
 * Usa o modelo em inglês, que lê bem os termos procurados (CUPOM, NFC-e, DANFE, PIX...) já que os acentos
 * são ignorados na conferência. Uma imagem por vez: o Tesseract não pode ser usado por duas threads.
 */
class TesseractOcrEngine : OcrEngine {
    private val tesseract by lazy {
        loadVisualCppRuntime()
        Tesseract().apply {
            setDatapath(LoadLibs.extractTessResources("tessdata").absolutePath)
            setLanguage("eng")
            setVariable("user_defined_dpi", OCR_DPI.toString())
        }.also { AppLog.info("OCR (Tesseract) iniciado") }
    }

    @Synchronized
    override fun read(image: BufferedImage): String {
        // Mesmo modo de sempre: as regras foram calibradas com este texto. readWords troca o modo.
        tesseract.setPageSegMode(OcrMode.SINGLE_BLOCK.pageSegMode)
        tesseract.setVariable("user_defined_dpi", OCR_DPI.toString())
        return tesseract.doOCR(image).orEmpty()
    }

    @Synchronized
    override fun readWords(image: BufferedImage, dpi: Int, mode: OcrMode): List<PageWord> {
        tesseract.setPageSegMode(mode.pageSegMode)
        tesseract.setVariable("user_defined_dpi", dpi.toString())
        return tesseract.getWords(image, ITessAPI.TessPageIteratorLevel.RIL_WORD).orEmpty()
            .filter { it.text.isNotBlank() }
            .map { word ->
                val box = word.boundingBox
                PageWord(
                    word.text.trim(), box.x.toFloat(), box.y.toFloat(), box.width.toFloat(), box.height.toFloat(),
                    word.confidence,
                )
            }
    }

    /**
     * As DLLs do Tesseract precisam do runtime do Visual C++ (msvcp140, vcruntime140). O Java embutido no
     * instalador já traz esses arquivos: carregá-los antes evita exigir o "Visual C++ Redistributable" no PC.
     */
    private fun loadVisualCppRuntime() {
        val bin = Path.of(System.getProperty("java.home"), "bin")
        listOf("vcruntime140.dll", "vcruntime140_1.dll", "msvcp140.dll")
            .map(bin::resolve)
            .filter(Files::exists)
            .forEach { dll -> runCatching { System.load(dll.toString()) } }
    }

    companion object {
        /** Resolução usada para desenhar a página antes do OCR: boa para cupom e rápida (~1 s por página). */
        const val OCR_DPI = 200
    }
}
