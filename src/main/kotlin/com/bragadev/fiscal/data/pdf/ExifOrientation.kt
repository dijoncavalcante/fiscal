package com.bragadev.fiscal.data.pdf

/**
 * Lê a orientação que o celular grava na foto (EXIF, tag 0x0112) e devolve quantos graus, no sentido
 * horário, a imagem precisa girar para ficar em pé: 0, 90, 180 ou 270.
 *
 * O celular salva a foto "deitada" e só anota a orientação; o PDF ignora essa anotação, por isso o
 * app gira a página sozinho. Só JPEG tem EXIF na prática; qualquer outro formato, ou EXIF ausente ou
 * malformado, dá 0. Orientações espelhadas (raras em celular) são tratadas como a rotação mais próxima.
 */
object ExifOrientation {
    /** Basta o começo do arquivo: o EXIF fica num segmento APP1 de no máximo 64 KB, logo após o início. */
    const val HEADER_BYTES = 256 * 1024

    fun rotationDegrees(bytes: ByteArray): Int = runCatching { orientation(bytes) }.getOrNull()?.let(::degreesFor) ?: 0

    fun degreesFor(orientation: Int): Int = when (orientation) {
        3, 4 -> 180
        5, 6 -> 90
        7, 8 -> 270
        else -> 0
    }

    private fun orientation(bytes: ByteArray): Int? {
        if (bytes.size < 4 || bytes.u8(0) != 0xFF || bytes.u8(1) != 0xD8) return null
        var offset = 2
        while (offset + 4 <= bytes.size) {
            if (bytes.u8(offset) != 0xFF) return null
            val marker = bytes.u8(offset + 1)
            // Início da imagem em si (SOS) ou fim: não há mais metadados.
            if (marker == 0xDA || marker == 0xD9) return null
            val length = (bytes.u8(offset + 2) shl 8) or bytes.u8(offset + 3)
            if (length < 2) return null
            val data = offset + 4
            if (marker == 0xE1 && isExif(bytes, data)) return readTiffOrientation(bytes, data + 6, offset + 2 + length)
            offset += 2 + length
        }
        return null
    }

    private fun isExif(bytes: ByteArray, at: Int): Boolean =
        at + 6 <= bytes.size && String(bytes, at, 4, Charsets.US_ASCII) == "Exif" && bytes[at + 4].toInt() == 0 && bytes[at + 5].toInt() == 0

    private fun readTiffOrientation(bytes: ByteArray, tiff: Int, end: Int): Int? {
        val limit = minOf(end, bytes.size)
        if (tiff + 8 > limit) return null
        val little = when (String(bytes, tiff, 2, Charsets.US_ASCII)) {
            "II" -> true
            "MM" -> false
            else -> return null
        }
        fun u16(at: Int): Int {
            require(at + 2 <= limit)
            return if (little) bytes.u8(at) or (bytes.u8(at + 1) shl 8) else (bytes.u8(at) shl 8) or bytes.u8(at + 1)
        }
        fun u32(at: Int): Long {
            require(at + 4 <= limit)
            val a = u16(at).toLong()
            val b = u16(at + 2).toLong()
            return if (little) a or (b shl 16) else (a shl 16) or b
        }
        if (u16(tiff + 2) != 42) return null
        val ifd = tiff + u32(tiff + 4).toInt()
        val count = u16(ifd)
        for (i in 0 until count) {
            val entry = ifd + 2 + i * 12
            if (u16(entry) == ORIENTATION_TAG) return u16(entry + 8)
        }
        return null
    }

    private fun ByteArray.u8(at: Int): Int = this[at].toInt() and 0xFF

    private const val ORIENTATION_TAG = 0x0112
}
