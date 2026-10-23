package app.efa

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.ByteArrayOutputStream

/**
 * The in-app camera (A7). There is no gallery picker anywhere, so a photo can only be one taken now.
 * The picture goes straight to memory, not to a file, and the caller drops it after processing (ST1).
 * Failure behaviour (X7): permission refused or camera unavailable shows a plain message and a way back, never a blank screen.
 */
@Composable
fun CameraCapture(onJpeg: (ByteArray) -> Unit, onCancel: () -> Unit) {
    val c = LocalColors.current
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var denied by remember { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> granted = ok; denied = !ok }

    if (!granted) {
        Column(Modifier.fillMaxSize().background(c.bg).windowInsetsPadding(WindowInsets.systemBars).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            BasicText("Camera", style = TextStyle(c.text, 28.sp, FontWeight.SemiBold))
            // A9: every permission is asked in the app with a plain reason.
            BasicText("The camera is used only to take proof photos inside this app. Photos are processed on this phone. Only a small thumbnail is kept, for 14 days, and nothing is sent anywhere.", style = TextStyle(c.text, 16.sp))
            if (denied) BasicText("Camera permission is off. Allow it in Android settings, then try again.", style = TextStyle(c.text, 16.sp, FontWeight.SemiBold))
            Btn("Allow camera", { ask.launch(Manifest.permission.CAMERA) }, primary = true, modifier = Modifier.fillMaxWidth())
            Btn("Back", onCancel, modifier = Modifier.fillMaxWidth())
        }
        return
    }

    val owner = LocalLifecycleOwner.current
    val capture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }     // the capture button works only once the camera is bound

    Column(Modifier.fillMaxSize().background(c.bg).windowInsetsPadding(WindowInsets.systemBars).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AndroidView(
            factory = { context ->
                PreviewView(context).also { view ->
                    val future = ProcessCameraProvider.getInstance(context)
                    future.addListener({
                        try {
                            val provider = future.get()
                            val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
                            provider.unbindAll()
                            // Back camera first; a phone or emulator without one falls back to the front camera (X7).
                            val sel = when {
                                provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
                                provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
                                else -> { error = "This phone has no camera, so proof photos cannot be taken."; null }
                            }
                            if (sel != null) { provider.bindToLifecycle(owner, sel, preview, capture); ready = true }
                        } catch (e: Exception) { error = "The camera is not available. Try again or go back." }
                    }, ContextCompat.getMainExecutor(context))
                }
            },
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
        error?.let { BasicText(it, style = TextStyle(c.text, 16.sp, FontWeight.SemiBold)) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Btn(if (!ready) "Starting camera" else if (busy) "Saving" else "Take photo", {
                if (ready && !busy) {
                    busy = true
                    val out = ByteArrayOutputStream()
                    capture.takePicture(ImageCapture.OutputFileOptions.Builder(out).build(), ContextCompat.getMainExecutor(ctx),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(r: ImageCapture.OutputFileResults) { busy = false; onJpeg(out.toByteArray()) }
                            override fun onError(e: ImageCaptureException) {
                                android.util.Log.w("efa-camera", "capture failed: code=${e.imageCaptureError} ${e.message}", e)
                                busy = false; error = "The photo failed. Take it again."
                            }
                        })
                }
            }, primary = true, modifier = Modifier.weight(1f), description = "Take proof photo")
            Btn("Back", onCancel, modifier = Modifier.weight(1f))
        }
    }
}
