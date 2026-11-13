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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.github.martin27222.simpleocr.camera.CameraScreen
import io.github.martin27222.simpleocr.privacy.PrivacyScreen
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
                var showPrivacy by remember { mutableStateOf(false) }

                // An image opened from another app closes back into that app.
                BackHandler(enabled = state.hasImage && !state.openedExternally) {
                    viewModel.closeImage()
                }

                if (state.hasImage || state.isLoadingImage) {
                    ScreenshotEditorScreen(
                        state = state,
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
                        onPhotoCaptured = viewModel::openCapturedPhoto,
                        onOpenPicker = { imagePicker.launch("image/*") },
                        onShowPrivacy = { showPrivacy = true },
                        onError = { message ->
                            Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                        },
                    )
                    if (showPrivacy) {
                        PrivacyScreen(onDismiss = { showPrivacy = false })
                    }
                }
            }
        }
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
