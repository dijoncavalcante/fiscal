package com.bragadev.fiscal.presentation

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import com.bragadev.fiscal.data.AppDirectories
import com.bragadev.fiscal.data.filesystem.SignatureImageStorageImpl
import com.bragadev.fiscal.domain.model.*
import com.bragadev.fiscal.domain.repository.DocumentContentReader
import com.bragadev.fiscal.domain.usecase.ObserveSettingsUseCase
import com.bragadev.fiscal.domain.usecase.ReadPdfSignersUseCase
import com.bragadev.fiscal.domain.usecase.SignatureImageUseCase
import com.bragadev.fiscal.domain.usecase.UpdateSettingsUseCase
import com.bragadev.fiscal.fakes.FakeSettingsRepository
import com.bragadev.fiscal.presentation.common.Strings
import com.bragadev.fiscal.presentation.settings.ExpenseSignaturesSection
import com.bragadev.fiscal.presentation.settings.ExpenseSignaturesViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.*

/** Configurações → Assinaturas das despesas: responsável de cada conta, imagem local e termos de isenção. */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class ExpenseSignaturesTest {
    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var directories: AppDirectories
    private lateinit var settings: FakeSettingsRepository
    private val pdfSigners = mutableListOf<String>()
    private lateinit var viewModel: ExpenseSignaturesViewModel

    private val reader = object : DocumentContentReader {
        override suspend fun read(file: Path, isEnough: (DocumentContent) -> Boolean) =
            Outcome.Success(DocumentContent(emptyList()))

        override suspend fun signerNames(file: Path): Outcome<List<String>> = Outcome.Success(pdfSigners.toList())
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        directories = AppDirectories(temp.newFolder("dados").toPath())
        settings = FakeSettingsRepository(AppSettings())
        viewModel = ExpenseSignaturesViewModel(
            ObserveSettingsUseCase(settings),
            UpdateSettingsUseCase(settings),
            ReadPdfSignersUseCase(reader),
            SignatureImageUseCase(SignatureImageStorageImpl(directories)),
        )
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun awaitSettings(condition: (AppSettings) -> Boolean): AppSettings =
        runBlocking { withTimeout(5_000) { settings.settings.first(condition) } }

    private fun image(name: String, format: String): Path {
        val file = temp.newFolder("fora-$format").toPath().resolve(name)
        val image = BufferedImage(120, 40, BufferedImage.TYPE_INT_RGB).apply { setRGB(5, 5, 0x1565C0) }
        ImageIO.write(image, format, file.toFile())
        return file
    }

    // ---- Imagem de referência ----

    @Test
    fun `imagem e copiada como PNG para a pasta de dados, o original nao muda`() = runTest {
        val storage = SignatureImageStorageImpl(directories)
        val original = image("selo andrey.jpg", "jpg")
        val before = Files.readAllBytes(original)

        assertIs<Outcome.Success<Unit>>(storage.save(AccountType.CONGREGACAO, original))

        val saved = directories.dataDirectory.resolve("assinaturas").resolve("congregacao.png")
        assertTrue(Files.exists(saved))
        assertNotNull(ImageIO.read(saved.toFile()))
        assertContentEquals(before, Files.readAllBytes(original))
        assertContentEquals(Files.readAllBytes(saved), storage.load(AccountType.CONGREGACAO))
        assertNull(storage.load(AccountType.MANUTENCAO))
        // Nada vai para a pasta do projeto.
        assertFalse(Files.exists(Path.of("assinaturas")))

        storage.remove(AccountType.CONGREGACAO)
        assertNull(storage.load(AccountType.CONGREGACAO))
    }

    @Test
    fun `arquivo que nao e imagem e recusado`() = runTest {
        val notImage = temp.newFile("selo.png").toPath().also { Files.writeString(it, "não é imagem") }

        val result = SignatureImageStorageImpl(directories).save(AccountType.MANUTENCAO, notImage)

        assertEquals(Outcome.Failure(FileOperationError.UnsupportedImage), result)
    }

    // ---- Responsável ----

    @Test
    fun `nome do certificado vira nome legivel`() {
        assertEquals("Andrey Alves da Costa", ExpenseSignaturesViewModel.displayName("ANDREY ALVES DA COSTA"))
        assertEquals(
            "Elves Lincoln Menezes de Lima",
            ExpenseSignaturesViewModel.displayName(" ELVES LINCOLN MENEZES DE LIMA ")
        )
    }

    @Test
    fun `ler de um PDF assinado preenche o campo, e salvar a primeira vez grava direto`() {
        pdfSigners += "ANDREY ALVES DA COSTA"

        viewModel.onReadFromPdf(AccountType.CONGREGACAO, Path.of("despesa.pdf"))
        val account = viewModel.uiState.value.accounts.first { it.account == AccountType.CONGREGACAO }
        assertEquals("Andrey Alves da Costa", account.draftName)
        assertNull(settings.settings.value.expenseSigners[AccountType.CONGREGACAO])

        viewModel.onSaveName(AccountType.CONGREGACAO)
        assertEquals(
            "Andrey Alves da Costa",
            awaitSettings { it.expenseSigners.isNotEmpty() }.expenseSigners[AccountType.CONGREGACAO]
        )
        assertNull(viewModel.uiState.value.pendingChange)
    }

    @Test
    fun `PDF sem assinatura avisa e varios nomes perguntam qual`() {
        viewModel.onReadFromPdf(AccountType.MANUTENCAO, Path.of("sem.pdf"))
        assertEquals(Strings.SIGNER_PDF_WITHOUT_SIGNATURE, viewModel.uiState.value.message)

        pdfSigners += listOf("ELVES LINCOLN MENEZES DE LIMA", "FULANO DE TAL")
        viewModel.onReadFromPdf(AccountType.MANUTENCAO, Path.of("dois.pdf"))
        assertEquals(
            listOf("Elves Lincoln Menezes de Lima", "Fulano de Tal"),
            viewModel.uiState.value.certificateChoice?.names
        )
    }

    @Test
    fun `trocar um responsavel ja gravado pede confirmacao`() {
        runBlocking { settings.save(AppSettings(expenseSigners = mapOf(AccountType.MANUTENCAO to "Elves Lincoln Menezes de Lima"))) }

        viewModel.onNameChanged(AccountType.MANUTENCAO, "Novo Responsável")
        viewModel.onSaveName(AccountType.MANUTENCAO)
        assertEquals("Elves Lincoln Menezes de Lima", settings.settings.value.expenseSigners[AccountType.MANUTENCAO])
        assertEquals("Novo Responsável", viewModel.uiState.value.pendingChange?.to)

        viewModel.onConfirmChange()
        assertEquals(
            "Novo Responsável",
            awaitSettings { it.expenseSigners[AccountType.MANUTENCAO] == "Novo Responsável" }
                .expenseSigners[AccountType.MANUTENCAO])
    }

    // ---- Tela com mouse e teclado simulados ----

    @Test
    fun `tela - digitar o nome, salvar, trocar com confirmacao e adicionar termo com Enter`() = runComposeUiTest {
        setContent { MaterialTheme { ExpenseSignaturesSection(viewModel) } }

        // Campos: [0] Congregação, [1] Manutenção, [2] novo termo.
        onAllNodes(hasSetTextAction())[0].performTextInput("Andrey Alves da Costa")
        onAllNodesWithText(Strings.SAVE)[0].performClick()
        waitForIdle()
        assertEquals("Andrey Alves da Costa", settings.settings.value.expenseSigners[AccountType.CONGREGACAO])

        onAllNodes(hasSetTextAction())[0].performTextReplacement("Outra Pessoa")
        onAllNodesWithText(Strings.SAVE)[0].performClick()
        onNodeWithText(Strings.SIGNER_CHANGE_TITLE).assertExists()
        onNodeWithText(Strings.SIGNER_CHANGE_CONFIRM).performClick()
        waitForIdle()
        assertEquals("Outra Pessoa", settings.settings.value.expenseSigners[AccountType.CONGREGACAO])

        onAllNodes(hasSetTextAction())[2].performTextInput("Aluguel")
        onAllNodes(hasSetTextAction())[2].performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        assertEquals("Aluguel", settings.settings.value.expenseExemptTerms.last())
        onNodeWithText("Aluguel").assertExists()
    }
}
