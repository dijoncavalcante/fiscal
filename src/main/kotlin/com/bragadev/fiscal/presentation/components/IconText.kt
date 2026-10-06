package com.bragadev.fiscal.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Ícone seguido de texto, na mesma cor. O ícone é decorativo: o texto já diz tudo para leitores de tela. */
@Composable
fun IconText(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    fontWeight: FontWeight? = null,
    iconSize: Dp = 16.dp,
    iconTint: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
) {
    val contentColor = color.takeOrElse { style.color.takeOrElse { LocalContentColor.current } }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, contentDescription = null, tint = iconTint.takeOrElse { contentColor }, modifier = Modifier.size(iconSize))
        Text(text, color = contentColor, style = style, fontWeight = fontWeight, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
    }
}

/** Setinha de grupo recolhível: para baixo quando aberto, para a direita quando fechado. */
@Composable
fun ExpandIcon(isExpanded: Boolean, modifier: Modifier = Modifier) {
    Icon(
        if (isExpanded) AppIcons.ExpandMore else AppIcons.ChevronRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = modifier.size(18.dp),
    )
}
