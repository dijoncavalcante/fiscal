package com.bragadev.fiscal.data.pdf

import org.junit.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.imageio.ImageIO
import kotlin.test.assertEquals

/** Foto "deitada" com a orientação anotada no EXIF, como o celular grava. */
fun jpegWithOrientation(orientation: Int?, width: Int = 120, height: Int = 60, littleEndian: Boolean = true): ByteArray {
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val jpeg = ByteArrayOutputStream().also { ImageIO.write(image, "jpg", it) }.toByteArray()
    if (orientation == null) return jpeg

    val order = if (littleEndian) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN
    val tiff = ByteBuffer.allocate(26).order(order).apply {
        put(if (littleEndian) "II".toByteArray() else "MM".toByteArray())
        putShort(42)
        putInt(8) // IFD0 logo depois do cabeçalho
        putShort(1) // uma entrada
        putShort(0x0112).putShort(3).putInt(1).putShort(orientation.toShort()).putShort(0)
        putInt(0) // sem próximo IFD
    }.array()
    val payload = "Exif".toByteArray() + byteArrayOf(0, 0) + tiff
    val length = payload.size + 2
    val app1 = byteArrayOf(0xFF.toByte(), 0xE1.toByte(), (length shr 8).toByte(), length.toByte()) + payload
    return jpeg.copyOfRange(0, 2) + app1 + jpeg.copyOfRange(2, jpeg.size)
}

class ExifOrientationTest {
    @Test
    fun `orientacoes do celular viram graus no sentido horario`() {
        assertEquals(0, ExifOrientation.rotationDegrees(jpegWithOrientation(1)))
        assertEquals(90, ExifOrientation.rotationDegrees(jpegWithOrientation(6)))
        assertEquals(180, ExifOrientation.rotationDegrees(jpegWithOrientation(3)))
        assertEquals(270, ExifOrientation.rotationDegrees(jpegWithOrientation(8)))
    }

    @Test
    fun `funciona nas duas ordens de bytes do EXIF`() {
        assertEquals(90, ExifOrientation.rotationDegrees(jpegWithOrientation(6, littleEndian = false)))
        assertEquals(270, ExifOrientation.rotationDegrees(jpegWithOrientation(8, littleEndian = false)))
    }

    @Test
    fun `sem EXIF, PNG ou arquivo estranho fica como esta`() {
        assertEquals(0, ExifOrientation.rotationDegrees(jpegWithOrientation(null)))
        val png = ByteArrayOutputStream().also { ImageIO.write(BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB), "png", it) }.toByteArray()
        assertEquals(0, ExifOrientation.rotationDegrees(png))
        assertEquals(0, ExifOrientation.rotationDegrees(ByteArray(0)))
        assertEquals(0, ExifOrientation.rotationDegrees(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE1.toByte(), 0, 40)))
    }

    @Test
    fun `espelhadas usam a rotacao mais proxima`() {
        assertEquals(0, ExifOrientation.degreesFor(2))
        assertEquals(180, ExifOrientation.degreesFor(4))
        assertEquals(90, ExifOrientation.degreesFor(5))
        assertEquals(270, ExifOrientation.degreesFor(7))
    }

    /**
     * O preview (Skia) já mostra a foto em pé; por isso o PDF soma o EXIF e o botão Girar continua
     * relativo ao que o usuário vê. Se o Skia mudar esse comportamento, preview e PDF deixam de bater.
     */
    @Test
    fun `preview ja mostra a foto em pe`() {
        val image = org.jetbrains.skia.Image.makeFromEncoded(jpegWithOrientation(6, width = 120, height = 60))
        assertEquals(60 to 120, image.width to image.height)
    }
}
