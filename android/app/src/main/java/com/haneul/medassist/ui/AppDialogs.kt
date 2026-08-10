package com.haneul.medassist.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.haneul.medassist.ui.theme.Danger
import com.haneul.medassist.ui.theme.Muted
import com.haneul.medassist.ui.theme.Primary

@Composable
internal fun AppConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    danger: Boolean = false,
    confirmWeight: Float = 1f,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = Color.White, shadowElevation = 10.dp) {
            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(message, color = Muted, style = MaterialTheme.typography.bodyLarge)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onDismiss, Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(15.dp)) {
                        Text("취소", maxLines = 1, softWrap = false)
                    }
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.weight(confirmWeight).height(52.dp),
                        shape = RoundedCornerShape(15.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (danger) Danger else Primary),
                    ) {
                        Text(confirmText, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    }
                }
            }
        }
    }
}
