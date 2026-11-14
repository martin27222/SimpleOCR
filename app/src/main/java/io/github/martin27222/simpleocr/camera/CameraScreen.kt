package io.github.martin27222.simpleocr.camera

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.view.OrientationEventListener
import android.view.Surface
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
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FlashlightOff
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Panorama
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.martin27222.simpleocr.CaptureMode
import io.github.martin27222.simpleocr.R
import io.github.martin27222.simpleocr.settings.Quality
import io.github.martin27222.simpleocr.settings.QualityLevel
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
    mode: CaptureMode,
    multiPhotoCount: Int,
    isMergingPhotos: Boolean,
    lightOnAtStart: Boolean,
    /** Photos are always taken upright and the symbols do not turn with the phone. */
    lockOrientation: Boolean,
    defaultQuality: Quality,
    levelOf: (Quality) -> QualityLevel,
    onModeSelected: (CaptureMode) -> Unit,
    onPhotoCaptured: (Bitmap, QualityLevel) -> Unit,
    onMultiPhotoDone: () -> Unit,
    onMultiPhotoReset: () -> Unit,
    onOpenPicker: () -> Unit,
    onShowSettings: () -> Unit,
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
                mode = mode,
                multiPhotoCount = multiPhotoCount,
                isMergingPhotos = isMergingPhotos,
                lightOnAtStart = lightOnAtStart,
                lockOrientation = lockOrientation,
                defaultQuality = defaultQuality,
                levelOf = levelOf,
                onModeSelected = onModeSelected,
                onPhotoCaptured = onPhotoCaptured,
                onMultiPhotoDone = onMultiPhotoDone,
                onMultiPhotoReset = onMultiPhotoReset,
                onOpenPicker = onOpenPicker,
                onShowSettings = onShowSettings,
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
    mode: CaptureMode,
    multiPhotoCount: Int,
    isMergingPhotos: Boolean,
    lightOnAtStart: Boolean,
    /** Photos are always taken upright and the symbols do not turn with the phone. */
    lockOrientation: Boolean,
    defaultQuality: Quality,
    levelOf: (Quality) -> QualityLevel,
    onModeSelected: (CaptureMode) -> Unit,
    onPhotoCaptured: (Bitmap, QualityLevel) -> Unit,
    onMultiPhotoDone: () -> Unit,
    onMultiPhotoReset: () -> Unit,
    onOpenPicker: () -> Unit,
    onShowSettings: () -> Unit,
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
    var quality by rememberSaveable { mutableStateOf(defaultQuality) }
    val level = levelOf(quality)
    val carefulCapture = level.carefulCapture
    val imageCapture = remember(carefulCapture) {
        ImageCapture.Builder()
            .setCaptureMode(
                if (carefulCapture) {
                    ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
                } else {
                    ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                },
            )
            .build()
    }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torchOn by rememberSaveable { mutableStateOf(lightOnAtStart) }
    var isCapturing by remember { mutableStateOf(false) }

    // The way the phone is held, from the motion sensor: photos are taken that way round and
    // the symbols turn to stay upright.
    var rotation by remember { mutableIntStateOf(Surface.ROTATION_0) }
    DisposableEffect(lockOrientation) {
        if (lockOrientation) {
            rotation = Surface.ROTATION_0
            return@DisposableEffect onDispose {}
        }
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                rotation = DeviceRotation.fromOrientation(orientation, rotation)
            }
        }
        listener.enable()
        onDispose { listener.disable() }
    }
    // The screen itself stays upright, so the camera picture does not jump when the phone turns.
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        val before = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose { if (before != null) activity.requestedOrientation = before }
    }
    LaunchedEffect(rotation, imageCapture) { imageCapture.targetRotation = rotation }
    // Turned the short way round, so 270 degrees become -90.
    var symbolTarget by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(rotation) {
        val wanted = DeviceRotation.symbolAngle(rotation)
        val step = ((wanted - symbolTarget) % 360f + 540f) % 360f - 180f
        symbolTarget += step
    }
    val symbolAngle by animateFloatAsState(symbolTarget, label = "symbolAngle")
    val upright = Modifier.rotate(symbolAngle)

    LaunchedEffect(lifecycleOwner, imageCapture) {
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
        val shotLevel = level
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    scope.launch {
                        val result = runCatching {
                            withContext(Dispatchers.Default) {
                                image.use { CapturedPhoto.toUprightBitmap(it, shotLevel.maxSide) }
                            }
                        }
                        isCapturing = false
                        result.onSuccess { onPhotoCaptured(it, shotLevel) }.onFailure { onError(captureFailedMessage) }
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

        val qualityDescription = stringResource(R.string.capture_quality_level, quality.label)
        TextButton(
            onClick = { quality = quality.next() },
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(8.dp)
                .semantics { contentDescription = qualityDescription },
        ) {
            Text(text = quality.label, color = Color.White, fontWeight = FontWeight.Bold, modifier = upright)
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(8.dp),
        ) {
            IconButton(onClick = onShowSettings) {
                Icon(
                    Icons.Outlined.Settings,
                    contentDescription = stringResource(R.string.settings),
                    tint = Color.White,
                    modifier = upright,
                )
            }
            IconButton(onClick = onShowPrivacy) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = stringResource(R.string.privacy),
                    tint = Color.White,
                    modifier = upright,
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (mode == CaptureMode.MultiPhoto) {
                MultiPhotoStatus(
                    count = multiPhotoCount,
                    isMerging = isMergingPhotos,
                    onDone = onMultiPhotoDone,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (mode == CaptureMode.MultiPhoto) {
                    val canReset = multiPhotoCount > 0
                    IconButton(onClick = onMultiPhotoReset, enabled = canReset) {
                        Icon(
                            Icons.Outlined.RestartAlt,
                            contentDescription = stringResource(R.string.multi_photo_reset),
                            tint = if (canReset) Color.White else Color.Gray,
                            modifier = upright,
                        )
                    }
                } else {
                    IconButton(onClick = onOpenPicker) {
                        Icon(
                            Icons.Outlined.PhotoLibrary,
                            contentDescription = stringResource(R.string.choose_image),
                            tint = Color.White,
                            modifier = upright,
                        )
                    }
                }

                ShutterButton(busy = isCapturing || isMergingPhotos, onClick = ::capture)

                val hasTorch = camera?.cameraInfo?.hasFlashUnit() == true
                IconButton(onClick = { torchOn = !torchOn }, enabled = hasTorch) {
                    Icon(
                        if (torchOn) Icons.Outlined.FlashlightOn else Icons.Outlined.FlashlightOff,
                        contentDescription = stringResource(R.string.toggle_light),
                        tint = if (hasTorch) Color.White else Color.Gray,
                        modifier = upright,
                    )
                }
            }

            ModeBar(mode = mode, onModeSelected = onModeSelected, symbolModifier = upright)
        }
    }
}

/** Explains the mode for several photos and offers "Done" once there is a photo. */
@Composable
private fun MultiPhotoStatus(
    count: Int,
    isMerging: Boolean,
    onDone: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.multi_photo_hint),
            color = Color.White,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
        if (count > 0) {
            Button(onClick = onDone, enabled = !isMerging) {
                Text(pluralStringResource(R.plurals.multi_photo_done, count, count))
            }
        }
    }
}

/** The two things the shutter can do: one photo, or several photos of one object. */
@Composable
private fun ModeBar(
    mode: CaptureMode,
    onModeSelected: (CaptureMode) -> Unit,
    symbolModifier: Modifier,
) {
    Row(
        modifier = Modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ModeTab(
            icon = Icons.Outlined.PhotoCamera,
            label = stringResource(R.string.mode_photo),
            selected = mode == CaptureMode.Photo,
            symbolModifier = symbolModifier,
            onClick = { onModeSelected(CaptureMode.Photo) },
        )
        ModeTab(
            icon = Icons.Outlined.Panorama,
            label = stringResource(R.string.mode_multi_photo),
            selected = mode == CaptureMode.MultiPhoto,
            symbolModifier = symbolModifier,
            onClick = { onModeSelected(CaptureMode.MultiPhoto) },
        )
    }
}

@Composable
private fun ModeTab(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    symbolModifier: Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) Color.White else Color.Black.copy(alpha = 0.55f))
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (selected) Color.Black else Color.White,
            modifier = symbolModifier,
        )
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

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
