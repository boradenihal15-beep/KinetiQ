package com.kinetiQ.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.speech.tts.TextToSpeech
import android.util.Size
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.kinetiQ.app.core.SessionSummary
import com.kinetiQ.app.core.Skeleton
import com.kinetiQ.app.core.Status
import com.kinetiQ.app.pose.SquatSession
import com.kinetiQ.app.pose.UiState
import com.kinetiQ.app.ui.components.PrimaryButton
import com.kinetiQ.app.ui.theme.FormGood
import com.kinetiQ.app.ui.theme.FormIncorrect
import com.kinetiQ.app.ui.theme.FormUnknown
import com.kinetiQ.app.ui.theme.FormWarning
import java.util.concurrent.Executors

@Composable
fun LiveScreen(onFinish: (SessionSummary) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }

    if (granted) {
        LiveContent(onFinish, onBack)
    } else {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Camera permission needed", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "KinetiQ analyses your movement live on this device. Video is not recorded or uploaded.",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
            )
            Spacer(Modifier.height(20.dp))
            PrimaryButton("ALLOW CAMERA", { launcher.launch(Manifest.permission.CAMERA) })
            TextButton(onClick = onBack) { Text("Back") }
        }
    }
}

@Composable
private fun LiveContent(onFinish: (SessionSummary) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val session = remember { SquatSession(context.applicationContext) }
    DisposableEffect(Unit) { onDispose { session.release() } }

    val ui by session.state.collectAsState()
    var front by remember { mutableStateOf(true) }
    var voice by remember { mutableStateOf(true) }

    // ---- voice feedback with cooldown ----
    var ttsReady by remember { mutableStateOf(false) }
    val tts = remember { TextToSpeech(context) { status -> ttsReady = status == TextToSpeech.SUCCESS } }
    DisposableEffect(Unit) { onDispose { tts.stop(); tts.shutdown() } }
    var lastMsg by remember { mutableStateOf("") }
    var lastAt by remember { mutableStateOf(0L) }
    val frame = ui.frame
    LaunchedEffect(frame?.message) {
        val f = frame ?: return@LaunchedEffect
        val worth = f.status == Status.WARNING || f.status == Status.INCORRECT || f.message == "Good squat!"
        val now = System.currentTimeMillis()
        val gap = if (f.message == lastMsg) 6000 else 2500
        if (voice && ttsReady && worth && now - lastAt > gap) {
            tts.speak(f.message, TextToSpeech.QUEUE_FLUSH, null, "kq")
            lastMsg = f.message
            lastAt = now
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        CameraPreview(session, front, Modifier.fillMaxSize())
        SkeletonOverlay(ui, Modifier.fillMaxSize())

        // top bar
        Row(
            Modifier.fillMaxWidth().systemBarsPadding().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            HudButton("Back", onBack)
            Surface(shape = RoundedCornerShape(50), color = Color(0xCC0E1116)) {
                Text(
                    statusLabel(frame?.status),
                    color = statusColor(frame?.status),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HudButton(if (voice) "Voice on" else "Voice off") { voice = !voice }
                HudButton("Flip") { front = !front }
            }
        }

        // bottom panel
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().systemBarsPadding().padding(12.dp)) {
            Surface(shape = RoundedCornerShape(24.dp), color = Color(0xE60E1116)) {
                Column(Modifier.padding(18.dp)) {
                    if (!ui.modelReady) {
                        Text("Pose model failed to load. Check the model file in assets.", color = FormIncorrect)
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("REPS", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                                Text("${frame?.reps ?: 0}", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("LAST FORM SCORE", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                                Text(
                                    frame?.lastScore?.toString() ?: "--",
                                    color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        val msg = frame?.message ?: "Position yourself in front of the camera"
                        val prefix = when (frame?.status) {
                            Status.GOOD -> "✓ "
                            Status.WARNING, Status.INCORRECT -> "⚠ "
                            else -> ""
                        }
                        Text(
                            prefix + msg,
                            color = statusColor(frame?.status),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        // debug line, handy for tuning thresholds
                        Text(
                            "Knee angle: ${frame?.kneeAngle?.toInt() ?: "--"}   Phase: ${frame?.phase ?: "--"}",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 11.sp
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    PrimaryButton("FINISH WORKOUT", { onFinish(session.finish()) })
                }
            }
        }
    }
}

@Composable
private fun CameraPreview(session: SquatSession, front: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    val owner = context as ComponentActivity
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FIT_CENTER
        }
    }
    val executor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(front) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val provider = future.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val selector = ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                    )
                    .build()
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(selector)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                analysis.setAnalyzer(executor) { image -> session.detector.detect(image, front) }
                provider.unbindAll()
                provider.bindToLifecycle(
                    owner,
                    if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
            } catch (e: Exception) {
                android.util.Log.e("KinetiQ", "Camera bind failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            try { ProcessCameraProvider.getInstance(context).get().unbindAll() } catch (e: Exception) { }
        }
    }
    DisposableEffect(Unit) { onDispose { executor.shutdown() } }
    AndroidView(factory = { previewView }, modifier = modifier)
}

@Composable
private fun SkeletonOverlay(ui: UiState, modifier: Modifier) {
    Canvas(modifier) {
        val pts = ui.points ?: return@Canvas
        if (ui.imgW == 0 || ui.imgH == 0 || pts.size < 33) return@Canvas
        // same fit-center mapping as the preview
        val scale = minOf(size.width / ui.imgW, size.height / ui.imgH)
        val ox = (size.width - ui.imgW * scale) / 2f
        val oy = (size.height - ui.imgH * scale) / 2f
        fun p(i: Int) = Offset(ox + pts[i].x * ui.imgW * scale, oy + pts[i].y * ui.imgH * scale)

        val frame = ui.frame
        val base = if (frame?.status == Status.UNKNOWN) FormUnknown else FormGood
        Skeleton.CONNECTIONS.forEach { (a, b) ->
            if (pts[a].vis < 0.4f || pts[b].vis < 0.4f) return@forEach
            val color = when {
                frame?.redPairs?.contains(a to b) == true -> FormIncorrect
                frame?.amberPairs?.contains(a to b) == true -> FormWarning
                else -> base
            }
            drawLine(color, p(a), p(b), strokeWidth = 9f, cap = StrokeCap.Round)
        }
        Skeleton.JOINTS.forEach { i ->
            if (pts[i].vis >= 0.4f) drawCircle(Color.White, 7f, p(i))
        }
    }
}

@Composable
private fun HudButton(label: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(50),
        color = Color(0xCC0E1116)
    ) {
        Text(
            label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

private fun statusColor(s: Status?): Color = when (s) {
    Status.GOOD -> FormGood
    Status.WARNING -> FormWarning
    Status.INCORRECT -> FormIncorrect
    else -> FormUnknown
}

private fun statusLabel(s: Status?): String = when (s) {
    Status.GOOD -> "FORM: GOOD"
    Status.WARNING -> "FORM: CHECK"
    Status.INCORRECT -> "FORM: FIX"
    else -> "ADJUST POSITION"
}