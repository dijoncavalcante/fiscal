package com.bragadev.fiscal.app

import com.bragadev.fiscal.domain.model.WindowBounds
import org.junit.Test
import java.awt.Rectangle
import kotlin.test.assertEquals

class WindowRestoreTest {
    private val mainScreen = Rectangle(0, 0, 1920, 1040)
    private val rightScreen = Rectangle(1920, 0, 1920, 1040)

    @Test
    fun `primeira vez abre maximizada`() {
        assertEquals(WindowRestore.DEFAULT, WindowRestore.start(null, listOf(mainScreen)))
        assertEquals(true, WindowRestore.DEFAULT.maximized)
    }

    @Test
    fun `reabre na posicao e tamanho salvos`() {
        val saved = WindowBounds(x = 100, y = 80, width = 1200, height = 760, maximized = false)
        assertEquals(WindowStart(100, 80, 1200, 760, maximized = false), WindowRestore.start(saved, listOf(mainScreen)))
    }

    @Test
    fun `reabre maximizada lembrando o tamanho de quando restaurar`() {
        val saved = WindowBounds(x = 200, y = 100, width = 1000, height = 700, maximized = true)
        assertEquals(WindowStart(200, 100, 1000, 700, maximized = true), WindowRestore.start(saved, listOf(mainScreen)))
    }

    @Test
    fun `segundo monitor desligado abre centralizada no principal`() {
        val saved = WindowBounds(x = 2100, y = 50, width = 1200, height = 760, maximized = false)
        assertEquals(WindowStart(2100, 50, 1200, 760, false), WindowRestore.start(saved, listOf(mainScreen, rightScreen)))
        assertEquals(WindowStart(null, null, 1200, 760, false), WindowRestore.start(saved, listOf(mainScreen)))
    }

    @Test
    fun `janela com a barra de titulo fora da tela abre centralizada`() {
        val aboveTheScreen = WindowBounds(x = 100, y = -500, width = 1200, height = 760, maximized = false)
        assertEquals(WindowStart(null, null, 1200, 760, false), WindowRestore.start(aboveTheScreen, listOf(mainScreen)))
        // Só uma pontinha de 50 px aparecendo na esquerda também não dá para arrastar.
        val almostOff = WindowBounds(x = -1150, y = 100, width = 1200, height = 760, maximized = false)
        assertEquals(null, WindowRestore.start(almostOff, listOf(mainScreen)).x)
    }

    @Test
    fun `tamanho minusculo vira o minimo usavel`() {
        val tiny = WindowBounds(x = 10, y = 10, width = 200, height = 100, maximized = false)
        val start = WindowRestore.start(tiny, listOf(mainScreen))
        assertEquals(WindowRestore.MIN_WIDTH, start.width)
        assertEquals(WindowRestore.MIN_HEIGHT, start.height)
    }

    @Test
    fun `texto salvo invalido e ignorado`() {
        assertEquals(WindowBounds(1, -2, 3, 4, true), WindowBounds.decode(WindowBounds(1, -2, 3, 4, true).encode()))
        assertEquals(null, WindowBounds.decode("1,2,3"))
        assertEquals(null, WindowBounds.decode("a,b,c,d,true"))
        assertEquals(null, WindowBounds.decode("1,2,3,4,talvez"))
    }
}
