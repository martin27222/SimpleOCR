// Derived from EasyOCR (https://github.com/MosesEllermann/EasyOCR), Apache License 2.0.
// Modified for SimpleOCR; see NOTICE and the git history for the changes.

package io.github.martin27222.simpleocr.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.RotateLeft
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Subject
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import io.github.martin27222.simpleocr.R
import androidx.compose.ui.unit.dp
import io.github.martin27222.simpleocr.EditorUiState
import io.github.martin27222.simpleocr.clipboard.ClipboardActions
import io.github.martin27222.simpleocr.geometry.ImageTransform
import io.github.martin27222.simpleocr.geometry.SizeF2
import io.github.martin27222.simpleocr.ocr.OcrLanguage
import io.github.martin27222.simpleocr.privacy.PrivacyScreen
import kotlinx.coroutines.launch
import kotlin.math.min

private val WorkbenchBackground = Color(0xFF0F172A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenshotEditorScreen(
    state: EditorUiState,
    onOpenPicker: () -> Unit,
    onToggleOverlays: () -> Unit,
    onRotateLeft: () -> Unit,
    onRotateRight: () -> Unit,
    onCrop: (CropState) -> Unit,
    onSaveCopy: () -> Unit,
    onBack: () -> Unit,
    onShowMessageConsumed: () -> Unit,
    onCopyAll: () -> Unit,
    onRerunOcr: () -> Unit,
    onLanguageSelected: (OcrLanguage) -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardActions = ClipboardActions(LocalClipboardManager.current)
    val scope = rememberCoroutineScope()
    val rotation = remember { Animatable(0f) }
    val previewScale = remember { Animatable(1f) }
    var showOcrSheet by remember { mutableStateOf(false) }
    var showPrivacySheet by remember { mutableStateOf(false) }
    var transform by remember { mutableStateOf(ImageTransform()) }
    var viewportSize by remember { mutableStateOf(SizeF2(0f, 0f)) }
    var activeTool by remember { mutableStateOf(EditorTool.View) }
    var cropState by remember { mutableStateOf<CropState?>(null) }
    var cropPreview by remember { mutableStateOf(false) }
    val copiedMessage = stringResource(R.string.copied)
    val savedMessage = stringResource(R.string.saved_to_gallery)

    LaunchedEffect(state.bitmap) {
        previewScale.snapTo(0.96f)
        previewScale.animateTo(1f, tween(durationMillis = 220, easing = FastOutSlowInEasing))
    }

    LaunchedEffect(state.errorMessage, state.lastSavedUri) {
        val message = state.errorMessage ?: state.lastSavedUri?.let { savedMessage }
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            onShowMessageConsumed()
        }
    }

    fun copyText(text: String) {
        if (clipboardActions.copy(text)) {
            scope.launch { snackbarHostState.showSnackbar(copiedMessage) }
        }
    }

    fun startCrop() {
        val bitmap = state.bitmap ?: return
        val imageSize = SizeF2(bitmap.width.toFloat(), bitmap.height.toFloat())
        cropState = cropState ?: CropState.initial(imageSize)
        activeTool = EditorTool.Crop
    }

    fun rotateAnimated(direction: Float, rotate: () -> Unit) {
        scope.launch {
            val currentBitmap = state.bitmap
            val targetScale = if (currentBitmap != null && !viewportSize.isEmpty) {
                val oldFit = min(
                    viewportSize.width / currentBitmap.width.toFloat(),
                    viewportSize.height / currentBitmap.height.toFloat(),
                )
                val newFit = min(
                    viewportSize.width / currentBitmap.height.toFloat(),
                    viewportSize.height / currentBitmap.width.toFloat(),
                )
                (newFit / oldFit).coerceIn(0.55f, 1.8f)
            } else {
                1f
            }
            rotation.snapTo(0f)
            previewScale.snapTo(1f)
            launch {
                previewScale.animateTo(targetScale, tween(durationMillis = 190, easing = FastOutSlowInEasing))
            }
            rotation.animateTo(direction * 90f, tween(durationMillis = 190, easing = FastOutSlowInEasing))
            rotate()
            rotation.snapTo(0f)
            previewScale.snapTo(1f)
        }
    }

    Scaffold(
        containerColor = WorkbenchBackground,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name), color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.back_to_camera),
                            tint = Color.White,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = WorkbenchBackground,
                    scrolledContainerColor = WorkbenchBackground,
                    actionIconContentColor = Color.White,
                    titleContentColor = Color.White,
                ),
                actions = {
                    IconButton(onClick = onToggleOverlays, enabled = state.hasImage) {
                        Icon(
                            imageVector = if (state.overlaysEnabled) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                            contentDescription = "Toggle text boxes",
                        )
                    }
                    IconButton(onClick = { showPrivacySheet = true }) {
                        Icon(Icons.Outlined.Info, contentDescription = "Privacy")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(WorkbenchBackground)
                .padding(padding),
        ) {
            val bitmap = state.bitmap
            val imageBottomPadding = when (activeTool) {
                EditorTool.View -> 172.dp
                EditorTool.Crop,
                EditorTool.Draw,
                -> 232.dp
            }
            when {
                bitmap != null -> ImageCanvasWithOcrOverlay(
                    bitmap = bitmap,
                    ocrResult = state.ocrResult,
                    overlaysEnabled = state.overlaysEnabled,
                    transform = transform,
                    viewportSize = viewportSize,
                    activeTool = activeTool,
                    cropState = cropState,
                    cropPreview = cropPreview,
                    drawingStrokes = emptyList(),
                    currentStroke = null,
                    penSettings = PenSettings(),
                    canvasRotation = rotation.value,
                    previewScale = previewScale.value,
                    onViewportChanged = { viewportSize = it },
                    onTransformChanged = { transform = it },
                    onCropChanged = { cropState = it },
                    onCropPreviewChanged = { cropPreview = it },
                    onDrawStart = {},
                    onDrawMove = {},
                    onDrawEnd = {},
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = imageBottomPadding),
                )

                state.isLoadingImage -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                )

                else -> EmptyState(
                    onOpenPicker = onOpenPicker,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            EditorFloatingControls(
                enabled = state.hasImage,
                activeTool = activeTool,
                cropState = cropState,
                canCopyAll = state.fullText.isNotBlank(),
                onCopyAll = {
                    copyText(state.fullText)
                    onCopyAll()
                },
                onCrop = ::startCrop,
                onRotateLeft = { rotateAnimated(-1f, onRotateLeft) },
                onRotateRight = { rotateAnimated(1f, onRotateRight) },
                onSaveCopy = onSaveCopy,
                onOcrText = { showOcrSheet = true },
                onCropPreset = { preset ->
                    val current = state.bitmap ?: return@EditorFloatingControls
                    cropState = cropRectForPreset(
                        imageSize = SizeF2(current.width.toFloat(), current.height.toFloat()),
                        preset = preset,
                    )
                },
                onApplyCrop = {
                    cropState?.let {
                        onCrop(it)
                        activeTool = EditorTool.View
                        cropState = null
                    }
                },
                onCancelCrop = {
                    activeTool = EditorTool.View
                    cropState = null
                    cropPreview = false
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )

            AnimatedVisibility(
                visible = state.isRunningOcr,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp),
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    tonalElevation = 4.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CircularProgressIndicator(strokeWidth = 2.dp)
                        Text("Running OCR")
                    }
                }
            }
        }
    }

    if (showOcrSheet) {
        OcrBottomSheet(
            fullText = state.fullText,
            language = state.language,
            onLanguageSelected = onLanguageSelected,
            onCopyAll = {
                copyText(state.fullText)
                onCopyAll()
            },
            onRerunOcr = onRerunOcr,
            onDismiss = { showOcrSheet = false },
        )
    }

    if (showPrivacySheet) {
        PrivacyScreen(onDismiss = { showPrivacySheet = false })
    }
}

@Composable
private fun EmptyState(
    onOpenPicker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.FileOpen,
            contentDescription = null,
            tint = Color.White,
        )
        Button(onClick = onOpenPicker) {
            Text("Choose image")
        }
    }
}

@Composable
private fun EditorFloatingControls(
    enabled: Boolean,
    activeTool: EditorTool,
    cropState: CropState?,
    canCopyAll: Boolean,
    onCopyAll: () -> Unit,
    onCrop: () -> Unit,
    onRotateLeft: () -> Unit,
    onRotateRight: () -> Unit,
    onSaveCopy: () -> Unit,
    onOcrText: () -> Unit,
    onCropPreset: (CropPreset) -> Unit,
    onApplyCrop: () -> Unit,
    onCancelCrop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AnimatedVisibility(
            visible = activeTool == EditorTool.Crop,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            FloatingPanel {
                CropControlPanel(
                    cropState = cropState,
                    onPreset = onCropPreset,
                    onApply = onApplyCrop,
                    onCancel = onCancelCrop,
                )
            }
        }

        AnimatedVisibility(
            visible = activeTool == EditorTool.View && canCopyAll,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Button(onClick = onCopyAll) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                Text(
                    text = stringResource(R.string.copy_all),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }

        Surface(
            shape = CircleShape,
            tonalElevation = 8.dp,
            shadowElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        ) {
            Row(
                modifier = Modifier
                    .widthIn(max = 344.dp)
                    .padding(horizontal = 7.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FloatingToolButton(selected = activeTool == EditorTool.Crop, enabled = enabled, onClick = onCrop) {
                    Icon(Icons.Outlined.Crop, contentDescription = "Crop")
                }
                CompactIconButton(onClick = onRotateLeft, enabled = enabled) {
                    Icon(Icons.Outlined.RotateLeft, contentDescription = "Rotate left")
                }
                CompactIconButton(onClick = onRotateRight, enabled = enabled) {
                    Icon(Icons.Outlined.RotateRight, contentDescription = "Rotate right")
                }
                CompactIconButton(onClick = onSaveCopy, enabled = enabled) {
                    Icon(Icons.Outlined.SaveAlt, contentDescription = stringResource(R.string.save_photo))
                }
                CompactIconButton(onClick = onOcrText, enabled = enabled) {
                    Icon(Icons.Outlined.Subject, contentDescription = "Full OCR text")
                }
            }
        }
    }
}

@Composable
private fun FloatingPanel(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 620.dp),
        shape = MaterialTheme.shapes.large,
        tonalElevation = 8.dp,
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
        content = { Box(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) { content() } },
    )
}

@Composable
private fun FloatingToolButton(
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
    ) {
        CompactIconButton(onClick = onClick, enabled = enabled, content = content)
    }
}

@Composable
private fun CompactIconButton(
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(42.dp),
        content = content,
    )
}

@Composable
private fun CropControlPanel(
    cropState: CropState?,
    onPreset: (CropPreset) -> Unit,
    onApply: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CropPreset.entries.forEach { preset ->
                FilterChip(
                    selected = cropState?.preset == preset,
                    onClick = { onPreset(preset) },
                    label = { Text(preset.label) },
                    leadingIcon = if (preset == CropPreset.Document) {
                        { Icon(Icons.Outlined.CropFree, contentDescription = null) }
                    } else {
                        null
                    },
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel) {
                Icon(Icons.Outlined.Close, contentDescription = null)
                Text("Cancel")
            }
            Button(onClick = onApply) {
                Icon(Icons.Outlined.Check, contentDescription = null)
                Text("Apply")
            }
        }
    }
}
