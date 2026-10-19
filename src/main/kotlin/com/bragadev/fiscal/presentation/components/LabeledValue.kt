package com.bragadev.fiscal.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Par "rótulo / valor" usado nos diálogos de confirmação.
 *
 * @param copyDescription quando informado, mostra ao lado do valor o botão que o copia (ex.: "Copiar o nome").
 */
@Composable
fun LabeledValue(label: String, value: String, modifier: Modifier = Modifier, copyDescription: String? = null) {
    Column(modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (copyDescription != null) CopyButton(value, copyDescription, Modifier.size(32.dp), iconSize = 16.dp)
        }
    }
}
