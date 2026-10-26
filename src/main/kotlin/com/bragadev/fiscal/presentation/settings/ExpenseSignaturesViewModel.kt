package com.bragadev.fiscal.presentation.settings

import com.bragadev.fiscal.domain.model.AppSettings
import com.bragadev.fiscal.domain.model.Outcome
import com.bragadev.fiscal.domain.model.SignatureRole
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

/** Quem assina (as despesas de uma conta ou os extratos bancários), como aparece na tela. */
data class AccountSignatureState(
    val role: SignatureRole,
    /** Nome gravado (o que a conferência usa); vazio = não configurado. */
    val savedName: String = "",
    /** Nome no campo, ainda não salvo. */
    val draftName: String = "",
    /** Imagem de referência do selo (PNG), se o usuário escolheu uma. */
    val image: ByteArray? = null,
) {
    val hasUnsavedName: Boolean get() = draftName.trim() != savedName

    override fun equals(other: Any?): Boolean = other is AccountSignatureState && role == other.role &&
            savedName == other.savedName && draftName == other.draftName && image.contentEquals(other.image)

    override fun hashCode(): Int = Objects.hash(role, savedName, draftName, image?.contentHashCode())
}

/** Troca de responsável esperando confirmação (os documentos serão conferidos de novo). */
data class SignerChange(val role: SignatureRole, val from: String, val to: String)

/** Vários nomes no PDF escolhido: o usuário diz qual é o responsável. */
data class CertificateChoice(val role: SignatureRole, val names: List<String>)

data class ExpenseSignaturesUiState(
    val accounts: List<AccountSignatureState> = SignatureRole.entries.map { AccountSignatureState(it) },
    val exemptTerms: List<String> = emptyList(),
    val newTerm: String = "",
    val pendingChange: SignerChange? = null,
    val certificateChoice: CertificateChoice? = null,
    val message: String? = null,
    val isError: Boolean = false,
)

/**
 * Configurações → "Assinaturas das despesas e extratos": quem assina as despesas de cada conta e os extratos
 * bancários das duas contas, a imagem de referência do selo e os termos que dispensam o cupom na Congregação. Tudo fica na máquina (banco local e
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
                            val saved = settings.signerFor(account.role).orEmpty()
                            // Campo não mexido acompanha o valor gravado; o que o usuário está digitando fica.
                            val draft = if (account.hasUnsavedName) account.draftName else saved
                            account.copy(savedName = saved, draftName = draft)
                        },
                        exemptTerms = settings.expenseExemptTerms,
                    )
                }
            }
        }
        scope.launch { SignatureRole.entries.forEach { reloadImage(it) } }
    }

    fun onNameChanged(role: SignatureRole, text: String) = updateAccount(role) { it.copy(draftName = text) }

    /** Primeira vez grava direto; trocar um responsável já gravado pede confirmação. */
    fun onSaveName(role: SignatureRole) {
        val current = accountState(role)
        val name = current.draftName.trim()
        if (name == current.savedName) return
        if (current.savedName.isEmpty()) saveName(role, name)
        else state.update { it.copy(pendingChange = SignerChange(role, current.savedName, name)) }
    }

    fun onConfirmChange() {
        val change = state.value.pendingChange ?: return
        state.update { it.copy(pendingChange = null) }
        saveName(change.role, change.to)
    }

    fun onDismissChange() = state.update { it.copy(pendingChange = null) }

    /** Lê os nomes dos certificados do PDF escolhido e coloca no campo (ou pergunta qual, se houver vários). */
    fun onReadFromPdf(role: SignatureRole, file: Path) {
        scope.launch {
            when (val result = readPdfSigners(file)) {
                is Outcome.Failure -> showMessage(result.error.toUserMessage(), isError = true)
                is Outcome.Success -> {
                    val names = result.value.map(::displayName).distinct()
                    when (names.size) {
                        0 -> showMessage(Strings.SIGNER_PDF_WITHOUT_SIGNATURE, isError = true)
                        1 -> onCertificateNameChosen(role, names.single())
                        else -> state.update { it.copy(certificateChoice = CertificateChoice(role, names)) }
                    }
                }
            }
        }
    }

    fun onCertificateNameChosen(role: SignatureRole, name: String) {
        state.update { it.copy(certificateChoice = null) }
        updateAccount(role) { it.copy(draftName = name) }
        showMessage(Strings.signerReadFromPdf(name), isError = false)
    }

    fun onDismissCertificateChoice() = state.update { it.copy(certificateChoice = null) }

    fun onImagePicked(role: SignatureRole, file: Path) {
        scope.launch {
            when (val result = signatureImages.save(role, file)) {
                is Outcome.Failure -> showMessage(result.error.toUserMessage(), isError = true)
                is Outcome.Success -> {
                    reloadImage(role)
                    showMessage(Strings.SIGNATURE_IMAGE_SAVED, isError = false)
                }
            }
        }
    }

    fun onRemoveImage(role: SignatureRole) {
        scope.launch {
            when (val result = signatureImages.remove(role)) {
                is Outcome.Failure -> showMessage(result.error.toUserMessage(), isError = true)
                is Outcome.Success -> reloadImage(role)
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

    private fun saveName(role: SignatureRole, name: String) {
        scope.launch {
            updateSettings { it.withSigner(role, name) }
            updateAccount(role) { it.copy(draftName = name) }
            val account = role.account
            val message = when {
                role == SignatureRole.ACCOUNTS_SERVANT && name.isEmpty() -> Strings.ACCOUNTS_SERVANT_CLEARED
                role == SignatureRole.ACCOUNTS_SERVANT -> Strings.accountsServantSaved(name)
                account == null && name.isEmpty() -> Strings.STATEMENT_SIGNER_CLEARED
                account == null -> Strings.statementSignerSaved(name)
                name.isEmpty() -> Strings.signerCleared(account.displayName)
                else -> Strings.signerSaved(name)
            }
            showMessage(message, isError = false)
        }
    }

    private fun updateTerms(transform: (List<String>) -> List<String>) {
        scope.launch { updateSettings { it.copy(expenseExemptTerms = transform(it.expenseExemptTerms)) } }
    }

    private suspend fun reloadImage(role: SignatureRole) {
        val image = signatureImages.load(role)
        updateAccount(role) { it.copy(image = image) }
    }

    private fun accountState(role: SignatureRole) = state.value.accounts.first { it.role == role }

    private fun updateAccount(role: SignatureRole, transform: (AccountSignatureState) -> AccountSignatureState) =
        state.update { current ->
            current.copy(accounts = current.accounts.map { if (it.role == role) transform(it) else it })
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
