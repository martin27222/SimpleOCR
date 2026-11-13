package io.github.martin27222.simpleocr.camera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FlashlightOff
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.martin27222.simpleocr.R
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Full-screen camera that hands back one upright photo per shutter press.
 * Photos stay in memory; nothing is written to storage here.
 */
@Composable
fun CameraScreen(
    onPhotoCaptured: (Bitmap) -> Unit,
    onOpenPicker: () -> Unit,
    onShowPrivacy: () -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (hasPermission) {
            CameraContent(
                onPhotoCaptured = onPhotoCaptured,
                onOpenPicker = onOpenPicker,
                onShowPrivacy = onShowPrivacy,
                onError = onError,
            )
        } else {
            PermissionHint(
                onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                onOpenPicker = onOpenPicker,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Composable
private fun CameraContent(
    onPhotoCaptured: (Bitmap) -> Unit,
    onOpenPicker: () -> Unit,
    onShowPrivacy: () -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val captureFailedMessage = stringResource(R.string.capture_failed)
    val cameraUnavailableMessage = stringResource(R.string.camera_unavailable)

    val previewView = remember {
        PreviewView(context).apply {
            // Show the whole sensor frame: what is visible is what gets recognized.
            scaleType = PreviewView.ScaleType.FIT_CENTER
        }
    }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torchOn by rememberSaveable { mutableStateOf(false) }
    var isCapturing by remember { mutableStateOf(false) }

    LaunchedEffect(lifecycleOwner) {
        runCatching {
            val provider = ProcessCameraProvider.awaitInstance(context)
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            provider.unbindAll()
            cameraProvider = provider
            camera = provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture,
            )
        }.onFailure { onError(cameraUnavailableMessage) }
    }

    DisposableEffect(Unit) {
        onDispose { cameraProvider?.unbindAll() }
    }

    LaunchedEffect(camera, torchOn) {
        camera?.cameraControl?.enableTorch(torchOn)
    }

    fun capture() {
        if (isCapturing || camera == null) return
        isCapturing = true
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    scope.launch {
                        val result = runCatching {
                            withContext(Dispatchers.Default) {
                                image.use(CapturedPhoto::toUprightBitmap)
                            }
                        }
                        isCapturing = false
                        result.onSuccess(onPhotoCaptured).onFailure { onError(captureFailedMessage) }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    isCapturing = false
                    onError(captureFailedMessage)
                }
            },
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(camera) {
                    detectTapGestures { offset ->
                        val activeCamera = camera ?: return@detectTapGestures
                        val point = previewView.meteringPointFactory.createPoint(offset.x, offset.y)
                        val action = FocusMeteringAction.Builder(point)
                            .setAutoCancelDuration(3, TimeUnit.SECONDS)
                            .build()
                        activeCamera.cameraControl.startFocusAndMetering(action)
                    }
                }
                .pointerInput(camera) {
                    detectTransformGestures { _, _, zoom, _ ->
                        val activeCamera = camera ?: return@detectTransformGestures
                        val zoomState = activeCamera.cameraInfo.zoomState.value
                            ?: return@detectTransformGestures
                        val ratio = (zoomState.zoomRatio * zoom)
                            .coerceIn(zoomState.minZoomRatio, zoomState.maxZoomRatio)
                        activeCamera.cameraControl.setZoomRatio(ratio)
                    }
                },
        )

        IconButton(
            onClick = onShowPrivacy,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(8.dp),
        ) {
            Icon(
                Icons.Outlined.Info,
                contentDescription = stringResource(R.string.privacy),
                tint = Color.White,
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onOpenPicker) {
                Icon(
                    Icons.Outlined.PhotoLibrary,
                    contentDescription = stringResource(R.string.choose_image),
                    tint = Color.White,
                )
            }

            ShutterButton(busy = isCapturing, onClick = ::capture)

            val hasTorch = camera?.cameraInfo?.hasFlashUnit() == true
            IconButton(onClick = { torchOn = !torchOn }, enabled = hasTorch) {
                Icon(
                    if (torchOn) Icons.Outlined.FlashlightOn else Icons.Outlined.FlashlightOff,
                    contentDescription = stringResource(R.string.toggle_light),
                    tint = if (hasTorch) Color.White else Color.Gray,
                )
            }
        }
    }
}

@Composable
private fun ShutterButton(
    busy: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(76.dp)
            .clip(CircleShape)
            .border(4.dp, Color.White, CircleShape)
            .clickable(
                enabled = !busy,
                role = Role.Button,
                onClickLabel = stringResource(R.string.take_photo),
                onClick = onClick,
            )
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp)
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
    }
}

@Composable
private fun PermissionHint(
    onRequestPermission: () -> Unit,
    onOpenPicker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.camera_permission_needed),
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onRequestPermission) {
            Text(stringResource(R.string.allow_camera))
        }
        Button(onClick = onOpenPicker) {
            Text(stringResource(R.string.choose_image))
        }
    }
}
