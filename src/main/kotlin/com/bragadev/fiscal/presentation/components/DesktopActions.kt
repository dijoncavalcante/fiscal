package com.bragadev.fiscal.presentation.components

import com.bragadev.fiscal.data.logging.AppLog
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.File
import java.net.URI
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/** Onde o WhatsApp foi aberto para o usuário colar o arquivo. */
enum class WhatsAppTarget { APP, WEB, NONE }

/** Ações do Windows usadas pela interface (o FISCAL não acessa a rede): copiar, abrir arquivo/pasta e abrir o WhatsApp. */
object DesktopActions {
    fun copyToClipboard(text: String) {
        runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null) }
            .onFailure { AppLog.warn("Não foi possível copiar para a área de transferência", it) }
    }

    /**
     * Copia os arquivos como o Ctrl+C do Explorer: depois é só colar (Ctrl+V) numa pasta, no WhatsApp ou num e-mail.
     * Nada é gravado agora; quem cola é que cria a cópia. Retorna `false` se a área de transferência estiver ocupada.
     */
    fun copyFilesToClipboard(files: List<Path>): Boolean =
        runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(FileSelection(files.map(Path::toFile)), null) }
            .onFailure { AppLog.warn("Não foi possível copiar os arquivos para a área de transferência", it) }
            .isSuccess

    fun openFile(file: Path) {
        runCatching { Desktop.getDesktop().open(file.toFile()) }
            .onFailure { AppLog.warn("Não foi possível abrir o arquivo $file", it) }
    }

    fun openFolder(folder: Path) {
        runCatching { Desktop.getDesktop().open(folder.toFile()) }
            .onFailure { AppLog.warn("Não foi possível abrir a pasta $folder", it) }
    }

    /**
     * Abre o WhatsApp para o usuário colar o arquivo numa conversa: o app do Windows, se estiver instalado;
     * senão o WhatsApp Web no navegador. O FISCAL não acessa a internet: só pede ao Windows para abrir o app
     * ou o navegador. Demora um pouco (consulta o registro): chamar fora da thread da interface.
     */
    fun openWhatsApp(): WhatsAppTarget {
        if (isWhatsAppAppInstalled() && browse(URI(WHATSAPP_APP_URI))) return WhatsAppTarget.APP
        return if (browse(URI(WHATSAPP_WEB_URL))) WhatsAppTarget.WEB else WhatsAppTarget.NONE
    }

    /**
     * O app do WhatsApp registra o link "whatsapp:" no Windows (tanto o da Microsoft Store quanto o antigo
     * instalador). Só consulta o registro, nada é alterado.
     */
    private fun isWhatsAppAppInstalled(): Boolean = WHATSAPP_REGISTRY_KEYS.any { key ->
        runCatching {
            val process = ProcessBuilder("reg", "query", key).redirectErrorStream(true).start()
            process.inputStream.use { it.readAllBytes() }
            process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0
        }.getOrDefault(false)
    }

    private fun browse(uri: URI): Boolean =
        runCatching { Desktop.getDesktop().browse(uri) }
            .onFailure { AppLog.warn("Não foi possível abrir $uri", it) }
            .isSuccess

    private const val WHATSAPP_APP_URI = "whatsapp://"
    private const val WHATSAPP_WEB_URL = "https://web.whatsapp.com/"
    private val WHATSAPP_REGISTRY_KEYS = listOf(
        """HKCR\whatsapp""",
        """HKCU\Software\Microsoft\Windows\Shell\Associations\UrlAssociations\whatsapp""",
    )

    /** Lista de arquivos na área de transferência (no Windows vira o mesmo formato do Explorer). */
    private class FileSelection(private val files: List<File>) : Transferable {
        override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.javaFileListFlavor)

        override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor == DataFlavor.javaFileListFlavor

        override fun getTransferData(flavor: DataFlavor): Any {
            if (!isDataFlavorSupported(flavor)) throw UnsupportedFlavorException(flavor)
            return files
        }
    }
}
