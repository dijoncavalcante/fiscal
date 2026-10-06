package com.bragadev.fiscal.domain.model

import java.nio.file.Path

/** Imagem que vira uma página do PDF, com a rotação escolhida pelo usuário (0, 90, 180 ou 270 graus). */
data class ImagePage(val path: Path, val rotationDegrees: Int = 0) {
    fun rotatedClockwise(): ImagePage = copy(rotationDegrees = (rotationDegrees + 90) % 360)
}
