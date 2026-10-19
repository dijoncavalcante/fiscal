package com.bragadev.fiscal.data.ocr

import com.bragadev.fiscal.data.logging.AppLog
import net.sourceforge.tess4j.Tesseract
import net.sourceforge.tess4j.util.LoadLibs
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path

/** Reconhece o texto de uma imagem (página fotografada ou digitalizada). Lança exceção se não conseguir. */
fun interface OcrEngine {
    fun read(image: BufferedImage): String
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
    override fun read(image: BufferedImage): String = tesseract.doOCR(image).orEmpty()

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
