package io.github.martin27222.simpleocr.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.martin27222.simpleocr.R
import io.github.martin27222.simpleocr.clipboard.ClipboardActions
import kotlinx.coroutines.launch

/** Shows the text joined from several photos: select parts of it or copy all of it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MergedTextScreen(
    text: String,
    /** The text was already copied automatically; the user is told once. */
    textCopied: Boolean,
    onTextCopiedShown: () -> Unit,
    onShowSettings: () -> Unit,
    onBack: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardActions = ClipboardActions(LocalClipboardManager.current)
    val scope = rememberCoroutineScope()
    val copiedMessage = stringResource(R.string.copied)

    LaunchedEffect(textCopied) {
        if (textCopied) {
            snackbarHostState.showSnackbar(copiedMessage)
            onTextCopiedShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.merged_text_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.back_to_camera),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onShowSettings) {
                        Icon(
                            Icons.Outlined.Settings,
                            contentDescription = stringResource(R.string.settings),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding(),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = SELECTABLE_TEXT_MARGIN, vertical = 12.dp),
            ) {
                if (text.isBlank()) {
                    Text(
                        text = stringResource(R.string.no_text_found),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    SelectionContainer {
                        Text(text = text, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            Button(
                onClick = {
                    if (clipboardActions.copy(text)) {
                        scope.launch { snackbarHostState.showSnackbar(copiedMessage) }
                    }
                },
                enabled = text.isNotBlank(),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 16.dp),
            ) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                Text(stringResource(R.string.copy_all), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
