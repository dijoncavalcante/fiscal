package com.bragadev.fiscal.presentation.settings

import com.bragadev.fiscal.domain.model.AccountType
import com.bragadev.fiscal.domain.model.AppSettings
import com.bragadev.fiscal.domain.model.Outcome
import com.bragadev.fiscal.domain.usecase.ObserveSettingsUseCase
import com.bragadev.fiscal.domain.usecase.ReadPdfSignersUseCase
import com.bragadev.fiscal.domain.usecase.SignatureImageUseCase
import com.bragadev.fiscal.domain.usecase.UpdateSettingsUseCase
import com.bragadev.fiscal.presentation.common.Strings
import com.bragadev.fiscal.presentation.common.ViewModel
import com.bragadev.fiscal.presentation.common.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.nio.file.Path
import java.util.*

/** Responsável pela assinatura de uma conta, como aparece na tela. */
data class AccountSignatureState(
    val account: AccountType,
    /** Nome gravado (o que a conferência usa); vazio = não configurado. */
    val savedName: String = "",
    /** Nome no campo, ainda não salvo. */
    val draftName: String = "",
    /** Imagem de referência do selo (PNG), se o usuário escolheu uma. */
    val image: ByteArray? = null,
) {
    val hasUnsavedName: Boolean get() = draftName.trim() != savedName

    override fun equals(other: Any?): Boolean = other is AccountSignatureState && account == other.account &&
            savedName == other.savedName && draftName == other.draftName && image.contentEquals(other.image)

    override fun hashCode(): Int = Objects.hash(account, savedName, draftName, image?.contentHashCode())
}

/** Troca de responsável esperando confirmação (as despesas da conta serão conferidas de novo). */
data class SignerChange(val account: AccountType, val from: String, val to: String)

/** Vários nomes no PDF escolhido: o usuário diz qual é o responsável. */
data class CertificateChoice(val account: AccountType, val names: List<String>)

data class ExpenseSignaturesUiState(
    val accounts: List<AccountSignatureState> = AppSettings.EXPENSE_ACCOUNTS.map { AccountSignatureState(it) },
    val exemptTerms: List<String> = emptyList(),
    val newTerm: String = "",
    val pendingChange: SignerChange? = null,
    val certificateChoice: CertificateChoice? = null,
    val message: String? = null,
    val isError: Boolean = false,
)

/**
 * Configurações → "Assinaturas das despesas": quem assina as despesas de cada conta, a imagem de referência do
 * selo e os termos que dispensam o cupom na Congregação. Tudo fica na máquina (banco local e
 * `%APPDATA%\Fiscal\assinaturas`). Mudar o responsável ou os termos faz o mês aberto ser conferido de novo.
 */
class ExpenseSignaturesViewModel(
    private val observeSettings: ObserveSettingsUseCase,
    private val updateSettings: UpdateSettingsUseCase,
    private val readPdfSigners: ReadPdfSignersUseCase,
    private val signatureImages: SignatureImageUseCase,
) : ViewModel() {
    private val state = MutableStateFlow(ExpenseSignaturesUiState())
    val uiState: StateFlow<ExpenseSignaturesUiState> = state.asStateFlow()

    init {
        scope.launch {
            observeSettings().collect { settings ->
                state.update { current ->
                    current.copy(
                        accounts = current.accounts.map { account ->
                            val saved = settings.expenseSigners[account.account].orEmpty()
                            // Campo não mexido acompanha o valor gravado; o que o usuário está digitando fica.
                            val draft = if (account.hasUnsavedName) account.draftName else saved
                            account.copy(savedName = saved, draftName = draft)
                        },
                        exemptTerms = settings.expenseExemptTerms,
                    )
                }
            }
        }
        scope.launch { AppSettings.EXPENSE_ACCOUNTS.forEach { reloadImage(it) } }
    }

    fun onNameChanged(account: AccountType, text: String) = updateAccount(account) { it.copy(draftName = text) }

    /** Primeira vez grava direto; trocar um responsável já gravado pede confirmação. */
    fun onSaveName(account: AccountType) {
        val current = accountState(account)
        val name = current.draftName.trim()
        if (name == current.savedName) return
        if (current.savedName.isEmpty()) saveName(account, name)
        else state.update { it.copy(pendingChange = SignerChange(account, current.savedName, name)) }
    }

    fun onConfirmChange() {
        val change = state.value.pendingChange ?: return
        state.update { it.copy(pendingChange = null) }
        saveName(change.account, change.to)
    }

    fun onDismissChange() = state.update { it.copy(pendingChange = null) }

    /** Lê os nomes dos certificados do PDF escolhido e coloca no campo (ou pergunta qual, se houver vários). */
    fun onReadFromPdf(account: AccountType, file: Path) {
        scope.launch {
            when (val result = readPdfSigners(file)) {
                is Outcome.Failure -> showMessage(result.error.toUserMessage(), isError = true)
                is Outcome.Success -> {
                    val names = result.value.map(::displayName).distinct()
                    when (names.size) {
                        0 -> showMessage(Strings.SIGNER_PDF_WITHOUT_SIGNATURE, isError = true)
                        1 -> onCertificateNameChosen(account, names.single())
                        else -> state.update { it.copy(certificateChoice = CertificateChoice(account, names)) }
                    }
                }
            }
        }
    }

    fun onCertificateNameChosen(account: AccountType, name: String) {
        state.update { it.copy(certificateChoice = null) }
        updateAccount(account) { it.copy(draftName = name) }
        showMessage(Strings.signerReadFromPdf(name), isError = false)
    }

    fun onDismissCertificateChoice() = state.update { it.copy(certificateChoice = null) }

    fun onImagePicked(account: AccountType, file: Path) {
        scope.launch {
            when (val result = signatureImages.save(account, file)) {
                is Outcome.Failure -> showMessage(result.error.toUserMessage(), isError = true)
                is Outcome.Success -> {
                    reloadImage(account)
                    showMessage(Strings.SIGNATURE_IMAGE_SAVED, isError = false)
                }
            }
        }
    }

    fun onRemoveImage(account: AccountType) {
        scope.launch {
            when (val result = signatureImages.remove(account)) {
                is Outcome.Failure -> showMessage(result.error.toUserMessage(), isError = true)
                is Outcome.Success -> reloadImage(account)
            }
        }
    }

    fun onNewTermChanged(text: String) = state.update { it.copy(newTerm = text) }

    fun onAddTerm() {
        val term = state.value.newTerm.trim()
        if (term.isEmpty()) return
        state.update { it.copy(newTerm = "") }
        if (state.value.exemptTerms.none { it.equals(term, ignoreCase = true) }) {
            updateTerms { it + term }
        }
    }

    fun onRemoveTerm(term: String) = updateTerms { terms -> terms - term }

    fun onRestoreDefaultTerms() = updateTerms { AppSettings.DEFAULT_EXPENSE_EXEMPT_TERMS }

    fun onMessageShown() = state.update { it.copy(message = null) }

    private fun saveName(account: AccountType, name: String) {
        scope.launch {
            updateSettings { settings ->
                val signers =
                    if (name.isEmpty()) settings.expenseSigners - account else settings.expenseSigners + (account to name)
                settings.copy(expenseSigners = signers)
            }
            updateAccount(account) { it.copy(draftName = name) }
            showMessage(
                if (name.isEmpty()) Strings.signerCleared(account.displayName) else Strings.signerSaved(name),
                isError = false
            )
        }
    }

    private fun updateTerms(transform: (List<String>) -> List<String>) {
        scope.launch { updateSettings { it.copy(expenseExemptTerms = transform(it.expenseExemptTerms)) } }
    }

    private suspend fun reloadImage(account: AccountType) {
        val image = signatureImages.load(account)
        updateAccount(account) { it.copy(image = image) }
    }

    private fun accountState(account: AccountType) = state.value.accounts.first { it.account == account }

    private fun updateAccount(account: AccountType, transform: (AccountSignatureState) -> AccountSignatureState) =
        state.update { current ->
            current.copy(accounts = current.accounts.map { if (it.account == account) transform(it) else it })
        }

    private fun showMessage(message: String, isError: Boolean) =
        state.update { it.copy(message = message, isError = isError) }

    companion object {
        private val connectives = setOf("da", "das", "de", "do", "dos", "e")

        /** "ANDREY ALVES DA COSTA" (como no certificado) → "Andrey Alves da Costa". */
        fun displayName(certificateName: String): String =
            certificateName.trim().lowercase().split(Regex("""\s+""")).joinToString(" ") { word ->
                if (word in connectives) word else word.replaceFirstChar(Char::uppercase)
            }
    }
}
