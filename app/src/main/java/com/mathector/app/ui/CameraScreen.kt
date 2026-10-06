package com.mathector.app.ui

import android.net.Uri
import android.view.Surface
import androidx.activity.compose.BackHandler
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File

@Composable
fun CameraScreen(onClose: () -> Unit, onCaptured: (Uri) -> Unit, onError: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val capture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    var taking by remember { mutableStateOf(false) }
    var cameraReady by remember { mutableStateOf(false) }
    val providerFuture = remember { ProcessCameraProvider.getInstance(context) }
    val previewView = remember { PreviewView(context) }
    DisposableEffect(lifecycle) {
        var disposed = false
        providerFuture.addListener({
            if (!disposed) try {
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                provider.unbindAll()
                provider.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                cameraReady = true
            } catch (error: Exception) { onError("相机不可用，可以使用相册或文件录入"); onClose() }
        }, ContextCompat.getMainExecutor(context))
        onDispose { disposed = true; if (providerFuture.isDone) runCatching { providerFuture.get().unbindAll() } }
    }
    BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        Column(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "关闭相机", tint = Color.White) }
                Spacer(Modifier.weight(1f)); Text("拍摄数学题", color = Color.White); Spacer(Modifier.weight(1f)); Spacer(Modifier.size(48.dp))
            }
            Text("让题目完整入镜，保持光线均匀", color = Color.White.copy(alpha = .8f))
        }
        Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("拍摄后可对照原图校对", color = Color.White.copy(alpha = .8f)); Spacer(Modifier.height(20.dp))
            FilledIconButton(enabled = cameraReady && !taking, onClick = {
                taking = true
                val target = File(context.cacheDir, "capture-${System.currentTimeMillis()}.jpg")
                capture.targetRotation = previewView.display?.rotation ?: Surface.ROTATION_0
                capture.takePicture(ImageCapture.OutputFileOptions.Builder(target).build(), ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(result: ImageCapture.OutputFileResults) { taking = false; onCaptured(Uri.fromFile(target)) }
                    override fun onError(exception: ImageCaptureException) { taking = false; onError("拍摄失败，请重试") }
                })
            }, modifier = Modifier.size(78.dp), shape = CircleShape, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = Color.Black)) {
                if (taking) CircularProgressIndicator(Modifier.size(28.dp)) else Icon(Icons.Rounded.CameraAlt, "拍摄", Modifier.size(32.dp))
            }
        }
    }
}
