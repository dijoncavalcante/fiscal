package com.bragadev.fiscal.presentation.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bragadev.fiscal.presentation.common.Strings
import com.bragadev.fiscal.presentation.components.*

/**
 * Configurações → "Assinaturas das despesas": quem assina as despesas de cada conta (nome completo, como no
 * certificado digital), a imagem de referência do selo e, na Congregação, os termos que dispensam o cupom.
 */
@Composable
fun ExpenseSignaturesSection(viewModel: ExpenseSignaturesViewModel) {
    val state by viewModel.uiState.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            Strings.SIGNATURES_EXPLANATION,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.accounts.forEachIndexed { index, account ->
            if (index > 0) HorizontalDivider()
            AccountSignatureBlock(account, viewModel)
        }
        HorizontalDivider()
        ExemptTermsBlock(state, viewModel)
        state.message?.let {
            Text(it, color = if (state.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        }
    }
    state.pendingChange?.let { ChangeSignerDialog(it, viewModel) }
    state.certificateChoice?.let { CertificateChoiceDialog(it, viewModel) }
}

@Composable
private fun AccountSignatureBlock(state: AccountSignatureState, viewModel: ExpenseSignaturesViewModel) {
    val account = state.account
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(account.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        if (state.savedName.isEmpty()) {
            IconText(
                icon = AppIcons.Warning,
                text = Strings.SIGNER_NOT_CONFIGURED,
                color = StatusColors.Warning,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.draftName,
                onValueChange = { viewModel.onNameChanged(account, it) },
                label = { Text(Strings.SIGNER_NAME_LABEL) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { viewModel.onSaveName(account) }, enabled = state.hasUnsavedName) { Text(Strings.SAVE) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = {
                pickFiles(Strings.SIGNER_PDF_PICKER_TITLE, null, Strings.PDF_FILTER, listOf("pdf")).firstOrNull()
                    ?.let { viewModel.onReadFromPdf(account, it) }
            }) { Text(Strings.SIGNER_READ_FROM_PDF) }
            OutlinedButton(onClick = {
                pickFiles(
                    Strings.SIGNATURE_IMAGE_PICKER_TITLE,
                    null,
                    Strings.IMAGE_FILTER,
                    listOf("png", "jpg", "jpeg")
                )
                    .firstOrNull()?.let { viewModel.onImagePicked(account, it) }
            }) { Text(if (state.image == null) Strings.SIGNATURE_IMAGE_CHOOSE else Strings.SIGNATURE_IMAGE_REPLACE) }
            if (state.image != null) {
                TextButton(onClick = { viewModel.onRemoveImage(account) }) { Text(Strings.SIGNATURE_IMAGE_REMOVE) }
            }
        }
        state.image?.let { SignatureImage(it) }
    }
}

/** Imagem de referência do selo; só para o usuário reconhecer (a conferência usa o nome). */
@Composable
private fun SignatureImage(bytes: ByteArray) {
    val bitmap: ImageBitmap? = remember(bytes) {
        runCatching { org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()
    }
    bitmap ?: return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Image(
            bitmap = bitmap,
            contentDescription = Strings.SIGNATURE_IMAGE_DESCRIPTION,
            contentScale = ContentScale.Fit,
            modifier = Modifier.widthIn(max = 360.dp).heightIn(max = 110.dp)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small).padding(4.dp),
        )
        Text(
            Strings.SIGNATURE_IMAGE_NOTE,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ExemptTermsBlock(state: ExpenseSignaturesUiState, viewModel: ExpenseSignaturesViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(Strings.EXEMPT_TERMS_TITLE, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(
            Strings.EXEMPT_TERMS_EXPLANATION,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.exemptTerms.forEach { term ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(term, modifier = Modifier.weight(1f))
                IconButton(onClick = { viewModel.onRemoveTerm(term) }, modifier = Modifier.size(32.dp)) {
                    Icon(AppIcons.Close, contentDescription = Strings.removeTerm(term), modifier = Modifier.size(16.dp))
                }
            }
        }
        if (state.exemptTerms.isEmpty()) {
            Text(
                Strings.EXEMPT_TERMS_EMPTY,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.newTerm,
                onValueChange = viewModel::onNewTermChanged,
                label = { Text(Strings.EXEMPT_TERM_NEW) },
                singleLine = true,
                modifier = Modifier.weight(1f).onPreviewKeyEvent { event ->
                    // Enter adiciona o termo, como o botão.
                    (event.key == Key.Enter && event.type == KeyEventType.KeyDown).also { if (it) viewModel.onAddTerm() }
                },
            )
            OutlinedButton(onClick = viewModel::onAddTerm, enabled = state.newTerm.isNotBlank()) { Text(Strings.ADD) }
        }
        TextButton(onClick = viewModel::onRestoreDefaultTerms) { Text(Strings.EXEMPT_TERMS_RESTORE) }
    }
}

@Composable
private fun ChangeSignerDialog(change: SignerChange, viewModel: ExpenseSignaturesViewModel) {
    AlertDialog(
        onDismissRequest = viewModel::onDismissChange,
        modifier = Modifier.dialogKeys(viewModel::onConfirmChange, viewModel::onDismissChange),
        title = { Text(Strings.SIGNER_CHANGE_TITLE) },
        text = {
            Text(
                Strings.signerChangeBody(change.account.displayName, change.from, change.to),
                Modifier.widthIn(max = 460.dp).padding(top = 4.dp),
            )
        },
        dismissButton = { TextButton(onClick = viewModel::onDismissChange) { Text(Strings.CANCEL) } },
        confirmButton = { Button(onClick = viewModel::onConfirmChange) { Text(Strings.SIGNER_CHANGE_CONFIRM) } },
    )
}

@Composable
private fun CertificateChoiceDialog(choice: CertificateChoice, viewModel: ExpenseSignaturesViewModel) {
    AlertDialog(
        onDismissRequest = viewModel::onDismissCertificateChoice,
        modifier = Modifier.dialogKeys(null, viewModel::onDismissCertificateChoice),
        title = { Text(Strings.SIGNER_CHOICE_TITLE) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(Strings.signerChoiceBody(choice.account.displayName), Modifier.padding(bottom = 8.dp))
                choice.names.forEach { name ->
                    TextButton(onClick = { viewModel.onCertificateNameChosen(choice.account, name) }) { Text(name) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = viewModel::onDismissCertificateChoice) { Text(Strings.CANCEL) } },
    )
}
