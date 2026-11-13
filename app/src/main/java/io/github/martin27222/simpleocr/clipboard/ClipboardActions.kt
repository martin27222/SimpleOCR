// Derived from EasyOCR (https://github.com/MosesEllermann/EasyOCR), Apache License 2.0.
// Modified for SimpleOCR; see NOTICE and the git history for the changes.

package io.github.martin27222.simpleocr.clipboard

import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.text.AnnotatedString

class ClipboardActions(
    private val clipboardManager: ClipboardManager,
) {
    fun copy(text: String): Boolean {
        val normalized = text.trim()
        if (normalized.isBlank()) return false
        clipboardManager.setText(AnnotatedString(normalized))
        return true
    }
}
