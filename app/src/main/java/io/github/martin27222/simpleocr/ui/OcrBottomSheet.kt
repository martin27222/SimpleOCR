// Derived from EasyOCR (https://github.com/MosesEllermann/EasyOCR), Apache License 2.0.
// Modified for SimpleOCR; see NOTICE and the git history for the changes.

package io.github.martin27222.simpleocr.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.martin27222.simpleocr.ocr.OcrLanguage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrBottomSheet(
    fullText: String,
    language: OcrLanguage,
    onLanguageSelected: (OcrLanguage) -> Unit,
    onCopyAll: () -> Unit,
    onRerunOcr: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Detected text")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onCopyAll, enabled = fullText.isNotBlank()) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                    Text("Copy all")
                }
                TextButton(onClick = onRerunOcr) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null)
                    Text("Re-run OCR")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OcrLanguage.entries.forEach { option ->
                    AssistChip(
                        onClick = { onLanguageSelected(option) },
                        label = { Text(option.label) },
                        enabled = option != language,
                    )
                }
            }
            SelectionContainer {
                Text(
                    text = fullText.ifBlank { "No text detected." },
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                )
            }
        }
    }
}
