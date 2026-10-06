package com.bragadev.fiscal.app

import com.bragadev.fiscal.domain.model.WindowBounds
import java.awt.Rectangle

/** Como a janela abre: posição (`null` = centralizada), tamanho e se começa maximizada. */
data class WindowStart(val x: Int?, val y: Int?, val width: Int, val height: Int, val maximized: Boolean)

/**
 * Reabre a janela do jeito que o usuário deixou. A posição salva só é usada se a barra de título
 * ainda aparecer em alguma tela — por exemplo, se o segundo monitor foi desligado, a janela abre centralizada.
 */
object WindowRestore {
    const val DEFAULT_WIDTH = 1320
    const val DEFAULT_HEIGHT = 840
    const val MIN_WIDTH = 800
    const val MIN_HEIGHT = 500

    /** Altura da faixa da barra de título que precisa estar visível para o usuário conseguir arrastar a janela. */
    private const val TITLE_BAR_HEIGHT = 32
    private const val MIN_VISIBLE_TITLE_WIDTH = 120

    /** Primeira vez (nada salvo): maximizada, como antes. */
    val DEFAULT = WindowStart(x = null, y = null, width = DEFAULT_WIDTH, height = DEFAULT_HEIGHT, maximized = true)

    fun start(saved: WindowBounds?, screens: List<Rectangle>): WindowStart {
        saved ?: return DEFAULT
        val width = saved.width.coerceAtLeast(MIN_WIDTH)
        val height = saved.height.coerceAtLeast(MIN_HEIGHT)
        val titleBar = Rectangle(saved.x, saved.y, width, TITLE_BAR_HEIGHT)
        val visible = screens.any { screen ->
            val overlap = screen.intersection(titleBar)
            !overlap.isEmpty && overlap.width >= MIN_VISIBLE_TITLE_WIDTH && overlap.height >= TITLE_BAR_HEIGHT / 2
        }
        return if (visible) {
            WindowStart(saved.x, saved.y, width, height, saved.maximized)
        } else {
            WindowStart(x = null, y = null, width = width, height = height, maximized = saved.maximized)
        }
    }
}
