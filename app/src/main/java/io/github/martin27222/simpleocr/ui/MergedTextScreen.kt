package io.github.martin27222.simpleocr.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.OutlinedCard
import androidx.compose.ui.graphics.Color
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

/** Shows the text joined from several photos, one card per area: copy one area or all of them. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MergedTextScreen(
    /** The text of each area found, in reading order. */
    frames: List<String>,
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
    val allText = remember(frames) { frames.joinToString("\n\n") }

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
            // The largest area is the main text; it gets the amber edge of the photo view.
            val mainFrame = remember(frames) { frames.indices.maxByOrNull { frames[it].length } }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (frames.isEmpty()) {
                    Text(
                        text = stringResource(R.string.no_text_found),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = SELECTABLE_TEXT_MARGIN - 16.dp),
                    )
                }
                frames.forEachIndexed { index, frameText ->
                    FrameCard(
                        text = frameText,
                        main = frames.size > 1 && index == mainFrame,
                        onCopy = {
                            if (clipboardActions.copy(frameText)) {
                                scope.launch { snackbarHostState.showSnackbar(copiedMessage) }
                            }
                        },
                    )
                }
            }
            Button(
                onClick = {
                    if (clipboardActions.copy(allText)) {
                        scope.launch { snackbarHostState.showSnackbar(copiedMessage) }
                    }
                },
                enabled = allText.isNotBlank(),
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

/** One area of text with its own copy button; the main text has an amber edge. */
@Composable
private fun FrameCard(text: String, main: Boolean, onCopy: () -> Unit) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(
            width = if (main) 2.dp else 1.dp,
            color = if (main) MAIN_FRAME_COLOR else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            SelectionContainer {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    // Room for the copy button and for a finger at the start of a line.
                    modifier = Modifier.padding(start = SELECTABLE_TEXT_MARGIN - 16.dp, end = 48.dp, top = 12.dp, bottom = 12.dp),
                )
            }
            IconButton(onClick = onCopy, modifier = Modifier.align(Alignment.TopEnd)) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = stringResource(R.string.copy_frame))
            }
        }
    }
}

/** The amber of the main text's box on the photo. */
private val MAIN_FRAME_COLOR = Color(0xFFF59E0B)
