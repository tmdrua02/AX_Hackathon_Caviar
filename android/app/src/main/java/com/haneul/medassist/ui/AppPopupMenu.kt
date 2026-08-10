package com.haneul.medassist.ui

import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.haneul.medassist.ui.theme.Danger

@Composable
internal fun AppPopupMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = Modifier.widthIn(min = 184.dp),
        shape = RoundedCornerShape(22.dp),
        containerColor = Color.White,
        tonalElevation = 0.dp,
        shadowElevation = 6.dp,
        content = content,
    )
}

@Composable
internal fun AppPopupMenuItem(text: String, danger: Boolean = false, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Text(
                text,
                style = MaterialTheme.typography.bodyLarge,
                color = if (danger) Danger else MaterialTheme.colorScheme.onSurface,
            )
        },
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
    )
}
