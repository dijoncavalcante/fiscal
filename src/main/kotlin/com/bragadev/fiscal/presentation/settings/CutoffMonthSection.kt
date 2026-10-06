package com.bragadev.fiscal.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bragadev.fiscal.presentation.common.Strings
import com.bragadev.fiscal.presentation.components.AppIcons
import com.bragadev.fiscal.presentation.components.StatusColors
import com.bragadev.fiscal.presentation.components.dialogKeys
import java.time.Month
import java.time.YearMonth

/**
 * "Proteção de meses": mostra o mês de corte e deixa trocá-lo, sempre com confirmação,
 * dizendo quais meses vão travar ou destravar.
 */
@Composable
fun CutoffMonthSection(firstEditableMonth: YearMonth, onChange: (YearMonth) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    Text(Strings.lockedHint(firstEditableMonth))
    Text(
        Strings.CUTOFF_EXPLANATION,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
    OutlinedButton(onClick = { editing = true }, modifier = Modifier.padding(top = 8.dp)) {
        Icon(AppIcons.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
        Text(Strings.CHANGE_CUTOFF, Modifier.padding(start = 6.dp))
    }
    if (editing) {
        CutoffDialog(
            current = firstEditableMonth,
            onConfirm = { onChange(it); editing = false },
            onDismiss = { editing = false },
        )
    }
}

@Composable
private fun CutoffDialog(current: YearMonth, onConfirm: (YearMonth) -> Unit, onDismiss: () -> Unit) {
    var month by remember { mutableStateOf(current.month) }
    var year by remember { mutableStateOf(current.year) }
    val chosen = YearMonth.of(year, month)
    val confirm = if (chosen != current) ({ onConfirm(chosen) }) else null

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.dialogKeys(confirm, onDismiss),
        title = { Text(Strings.CUTOFF_DIALOG_TITLE) },
        text = {
            Column(Modifier.width(460.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(Strings.CUTOFF_DIALOG_BODY)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Picker(Strings.monthName(YearMonth.of(year, month)), Month.entries) { month = it }
                    Picker(year.toString(), yearOptions(current)) { year = it }
                }
                when {
                    chosen < current -> Text(Strings.cutoffUnlocks(chosen, current), color = StatusColors.Warning)
                    chosen > current -> Text(Strings.cutoffLocks(current, chosen), color = StatusColors.Warning)
                    else -> Text(Strings.CUTOFF_UNCHANGED, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.CANCEL) } },
        confirmButton = { Button(onClick = { confirm?.invoke() }, enabled = confirm != null) { Text(Strings.CONFIRM) } },
    )
}

@Composable
private fun <T> Picker(label: String, options: List<T>, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) {
            Text(label)
            Icon(AppIcons.ExpandMore, contentDescription = null, modifier = Modifier.padding(start = 4.dp).size(18.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                val text = if (option is Month) Strings.monthName(YearMonth.of(2000, option)) else option.toString()
                DropdownMenuItem(text = { Text(text) }, onClick = { open = false; onSelect(option) })
            }
        }
    }
}

/** Anos oferecidos: alguns antes e depois do atual, cobrindo a virada do ano de serviço. */
private fun yearOptions(current: YearMonth): List<Int> {
    val thisYear = YearMonth.now().year
    return (minOf(current.year, thisYear) - 3..maxOf(current.year, thisYear) + 2).toList()
}
