// Derived from EasyOCR (https://github.com/MosesEllermann/EasyOCR), Apache License 2.0.
// Modified for SimpleOCR; see NOTICE and the git history for the changes.

package io.github.martin27222.simpleocr

import android.Manifest
import android.graphics.Color
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.github.martin27222.simpleocr.camera.CameraScreen
import io.github.martin27222.simpleocr.ocr.textToCopy
import io.github.martin27222.simpleocr.privacy.PrivacyScreen
import io.github.martin27222.simpleocr.settings.SettingsSheet
import io.github.martin27222.simpleocr.ui.MergedTextScreen
import io.github.martin27222.simpleocr.ui.ScreenshotEditorScreen
import io.github.martin27222.simpleocr.ui.theme.SimpleOcrTheme

class MainActivity : ComponentActivity() {
    private val viewModel: EditorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        viewModel.openIntent(intent)

        setContent {
            SimpleOcrTheme {
                val state by viewModel.uiState.collectAsState()
                val settings by viewModel.settings.collectAsState()
                val imagePicker = rememberLauncherForActivityResult(
                    ActivityResultContracts.GetContent(),
                ) { uri: Uri? ->
                    uri?.let(viewModel::openUri)
                }
                val savePermission = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { granted ->
                    if (granted) viewModel.saveCopy()
                }
                // Only Android 9 and older need a permission to save photos.
                val autoSavePermission = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { granted ->
                    if (!granted) viewModel.updateSettings(settings.copy(autoSavePhotos = false))
                }
                var showPrivacy by remember { mutableStateOf(false) }
                var showSettings by remember { mutableStateOf(false) }
                val mergedFrames = state.mergedFrames

                // An image opened from another app closes back into that app.
                BackHandler(enabled = state.hasImage && !state.openedExternally) {
                    viewModel.closeImage()
                }
                // Back first discards the photos of a round object, then leaves the app.
                BackHandler(enabled = !state.hasImage && state.multiPhotoCount > 0) {
                    viewModel.resetMultiPhoto()
                }

                if (mergedFrames != null) {
                    MergedTextScreen(
                        frames = remember(mergedFrames, settings) {
                            mergedFrames.map { EditorViewModel.mergedText(it, settings) }.filter { it.isNotBlank() }
                        },
                        textCopied = state.textCopied,
                        onTextCopiedShown = viewModel::clearMessage,
                        onShowSettings = { showSettings = true },
                        onBack = viewModel::resetMultiPhoto,
                    )
                } else if (state.hasImage || state.isLoadingImage) {
                    ScreenshotEditorScreen(
                        state = state,
                        copyAllText = remember(state.ocrResult, settings) {
                            state.ocrResult?.textToCopy(settings).orEmpty()
                        },
                        frameText = { frame -> frame.textToCopy(settings) },
                        onShowSettings = { showSettings = true },
                        onOpenPicker = { imagePicker.launch("image/*") },
                        onToggleOverlays = viewModel::toggleOverlays,
                        onRotateLeft = viewModel::rotateLeft,
                        onRotateRight = viewModel::rotateRight,
                        onCrop = viewModel::cropTo,
                        onSaveCopy = {
                            if (needsLegacyWritePermission()) {
                                savePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            } else {
                                viewModel.saveCopy()
                            }
                        },
                        onBack = {
                            if (state.openedExternally) finish() else viewModel.closeImage()
                        },
                        onToggleDewarp = viewModel::toggleDewarp,
                        onShowMessageConsumed = viewModel::clearMessage,
                        onCopyAll = {},
                        onRerunOcr = viewModel::rerunOcr,
                        onLanguageSelected = viewModel::setLanguage,
                    )
                } else {
                    CameraScreen(
                        mode = state.captureMode,
                        multiPhotoCount = state.multiPhotoCount,
                        isMergingPhotos = state.isMergingPhotos,
                        lightOnAtStart = settings.lightOnAtStart,
                        lockOrientation = settings.lockOrientation,
                        defaultQuality = settings.quality,
                        levelOf = settings::level,
                        onModeSelected = viewModel::setCaptureMode,
                        onPhotoCaptured = { photo, level ->
                            if (state.captureMode == CaptureMode.MultiPhoto) {
                                viewModel.addMultiPhoto(photo, level.passes)
                            } else {
                                viewModel.openCapturedPhoto(photo, level.passes)
                            }
                        },
                        onMultiPhotoDone = viewModel::finishMultiPhoto,
                        onMultiPhotoReset = viewModel::resetMultiPhoto,
                        onOpenPicker = { imagePicker.launch("image/*") },
                        onShowSettings = { showSettings = true },
                        onShowPrivacy = { showPrivacy = true },
                        onError = ::showToast,
                    )
                    // The camera has no place for messages, so problems show up as a toast.
                    LaunchedEffect(state.errorMessage) {
                        state.errorMessage?.let { message ->
                            showToast(message)
                            viewModel.clearMessage()
                        }
                    }
                    if (showPrivacy) {
                        PrivacyScreen(onDismiss = { showPrivacy = false })
                    }
                }

                if (showSettings) {
                    SettingsSheet(
                        settings = settings,
                        onChange = { changed ->
                            viewModel.updateSettings(changed)
                            if (changed.autoSavePhotos && !settings.autoSavePhotos &&
                                needsLegacyWritePermission()
                            ) {
                                autoSavePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            }
                        },
                        onDismiss = { showSettings = false },
                    )
                }
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModel.openIntent(intent)
    }

    private fun needsLegacyWritePermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) != PackageManager.PERMISSION_GRANTED
}
