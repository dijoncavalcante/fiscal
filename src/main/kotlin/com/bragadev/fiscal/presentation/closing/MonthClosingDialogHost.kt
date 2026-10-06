package com.bragadev.fiscal.presentation.closing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bragadev.fiscal.domain.model.MonthReview
import com.bragadev.fiscal.domain.model.ReviewLine
import com.bragadev.fiscal.presentation.common.Strings
import com.bragadev.fiscal.presentation.components.AppIcons
import com.bragadev.fiscal.presentation.components.DesktopActions
import com.bragadev.fiscal.presentation.components.FolderPathField
import com.bragadev.fiscal.presentation.components.IconText
import com.bragadev.fiscal.presentation.components.StatusColors
import com.bragadev.fiscal.presentation.components.dialogKeys
import com.bragadev.fiscal.presentation.components.pickFolder

/** "Concluir mês": conferência (faltando, pendências), onde salvar e "Gerar relatório PDF". Enter gera; Esc fecha. */
@Composable
fun MonthClosingDialogHost(state: MonthClosingUiState, viewModel: MonthClosingViewModel) {
    val dialog = state.dialog ?: return
    val review = dialog.review
    val canGenerate = !dialog.isWorking && dialog.created == null
    val onEnter: (() -> Unit)? = when {
        dialog.created != null -> viewModel::onDismiss
        canGenerate -> viewModel::onGenerate
        else -> null
    }

    Dialog(onDismissRequest = viewModel::onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp,
            modifier = Modifier.width(760.dp).dialogKeys(onEnter, viewModel::onDismiss),
        ) {
            Column(Modifier.padding(24.dp)) {
                IconText(
                    AppIcons.TaskDone,
                    Strings.concludeTitle(review.month, review.account?.displayName),
                    style = MaterialTheme.typography.headlineSmall,
                    iconSize = 28.dp,
                    iconTint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    review.folder.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                )

                Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState())) {
                    ReviewSummary(review)
                    HorizontalDivider(Modifier.padding(vertical = 10.dp))
                    review.lines.forEach { CategoryStatus(it) }
                }

                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                FolderPathField(
                    label = Strings.REPORT_FOLDER,
                    path = dialog.outputFolder?.toString().orEmpty(),
                    emptyText = Strings.REPORT_CHOOSE_FOLDER,
                    editDescription = Strings.REPORT_FOLDER_EDIT,
                    onEdit = { pickFolder(Strings.REPORT_FOLDER_PICKER_TITLE, dialog.outputFolder)?.let(viewModel::onOutputFolderChanged) },
                )
                OutlinedTextField(
                    value = dialog.outputName,
                    onValueChange = viewModel::onOutputNameChanged,
                    label = { Text(Strings.REPORT_NAME) },
                    singleLine = true,
                    enabled = dialog.created == null,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                if (dialog.isWorking) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                dialog.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
                dialog.created?.let { created ->
                    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconText(
                            AppIcons.CheckCircle,
                            Strings.reportCreated(created.fileName.toString()),
                            color = StatusColors.Positive,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(onClick = { created.parent?.let(DesktopActions::openFolder) }) { Text(Strings.OPEN_REPORT_FOLDER) }
                        Button(onClick = viewModel::onOpenReport, modifier = Modifier.padding(start = 8.dp)) { Text(Strings.OPEN_REPORT) }
                    }
                }

                Row(
                    Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    TextButton(onClick = viewModel::onDismiss) { Text(if (dialog.created != null) Strings.CLOSE_TOOL else Strings.CANCEL) }
                    if (dialog.created == null) {
                        Button(onClick = viewModel::onGenerate, enabled = canGenerate) {
                            Icon(AppIcons.Document, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(Strings.GENERATE_REPORT, Modifier.padding(start = 6.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewSummary(review: MonthReview) {
    if (review.isComplete) {
        IconText(AppIcons.CheckCircle, Strings.REVIEW_COMPLETE, color = StatusColors.Positive, fontWeight = FontWeight.SemiBold)
        return
    }
    Text(Strings.reviewSummary(review.doneCount, review.requiredCount), fontWeight = FontWeight.SemiBold)
    if (review.missing.isNotEmpty()) {
        IconText(
            AppIcons.CircleOutline,
            "${Strings.REVIEW_MISSING}: ${review.missing.joinToString { it.category.label }}",
            color = StatusColors.Warning,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
    val issues = review.lines.flatMap { line -> line.issues.map { (file, note) -> "$file — $note" } } +
        review.unmatchedIssues.map { (file, note) -> "$file — $note" }
    if (issues.isNotEmpty()) {
        IconText(AppIcons.Warning, "${Strings.REVIEW_ISSUES}:", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp))
        issues.forEach { Text("•  $it", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 22.dp)) }
    }
    Text(
        Strings.REVIEW_INCOMPLETE_NOTE,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/** Uma linha por categoria: situação e quantos arquivos — o detalhe completo vai no PDF. */
@Composable
private fun CategoryStatus(line: ReviewLine) {
    val (icon, color) = when {
        line.issues.isNotEmpty() -> AppIcons.Warning to MaterialTheme.colorScheme.error
        line.files.isNotEmpty() -> AppIcons.CheckCircle to StatusColors.Positive
        line.category.optional -> AppIcons.CircleOutline to MaterialTheme.colorScheme.onSurfaceVariant
        else -> AppIcons.CircleOutline to StatusColors.Warning
    }
    Row(Modifier.fillMaxWidth().padding(start = (line.depth * 16).dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(line.category.label, Modifier.weight(1f).padding(start = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(Strings.sequentialCount(line.files.size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
