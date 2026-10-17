package com.rabbi.scrollcounter.squat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import com.rabbi.scrollcounter.data.ScrollStore
import com.rabbi.scrollcounter.ui.I18n
import com.rabbi.scrollcounter.ui.theme.AppFonts
import com.rabbi.scrollcounter.ui.theme.Brand
import com.rabbi.scrollcounter.ui.theme.ScrollCounterTheme
import com.rabbi.scrollcounter.ui.tr
import kotlinx.coroutines.delay
import java.util.concurrent.Executors

/**
 * "Do 10 squats to unlock": the camera watches your body (on-device ML Kit pose detection, no
 * internet, no photos saved) and counts real squats. When 10 are done, the locked daily limit
 * can be changed once more today.
 */
class SquatUnlockActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = ScrollStore.get(this)
        setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalLayoutDirection provides
                    if (I18n.lang.rtl) androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr
            ) {
                ScrollCounterTheme(dark = true) {
                    SquatScreen(
                        onUnlocked = { store.grantSquatUnlock(); setResult(RESULT_OK); finish() },
                        onClose = { setResult(RESULT_CANCELED); finish() }
                    )
                }
            }
        }
    }
}

private enum class Phase { INTRO, COUNTDOWN, ACTIVE, DONE }

/** Body points for the on-screen skeleton (image coordinates). */
private class Skeleton(val pts: Map<Int, Offset>, val imgW: Int, val imgH: Int, val mirrored: Boolean)

private val BONES = listOf(
    PoseLandmark.LEFT_SHOULDER to PoseLandmark.RIGHT_SHOULDER,
    PoseLandmark.LEFT_SHOULDER to PoseLandmark.LEFT_ELBOW, PoseLandmark.LEFT_ELBOW to PoseLandmark.LEFT_WRIST,
    PoseLandmark.RIGHT_SHOULDER to PoseLandmark.RIGHT_ELBOW, PoseLandmark.RIGHT_ELBOW to PoseLandmark.RIGHT_WRIST,
    PoseLandmark.LEFT_SHOULDER to PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_SHOULDER to PoseLandmark.RIGHT_HIP,
    PoseLandmark.LEFT_HIP to PoseLandmark.RIGHT_HIP,
    PoseLandmark.LEFT_HIP to PoseLandmark.LEFT_KNEE, PoseLandmark.LEFT_KNEE to PoseLandmark.LEFT_ANKLE,
    PoseLandmark.RIGHT_HIP to PoseLandmark.RIGHT_KNEE, PoseLandmark.RIGHT_KNEE to PoseLandmark.RIGHT_ANKLE,
)
private val JOINTS = BONES.flatMap { listOf(it.first, it.second) }.toSet() + PoseLandmark.NOSE

@Composable
private fun SquatScreen(onUnlocked: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var hasCamera by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var asked by rememberSaveable { mutableStateOf(false) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasCamera = it; asked = true }
    LaunchedEffect(Unit) { if (!hasCamera) { delay(400); permLauncher.launch(Manifest.permission.CAMERA) } }

    var front by rememberSaveable { mutableStateOf(true) }
    var phase by rememberSaveable { mutableStateOf(Phase.INTRO) }
    var countdown by remember { mutableIntStateOf(3) }
    val counter = remember { SquatCounter(10) }
    var reps by remember { mutableIntStateOf(0) }
    var cue by remember { mutableStateOf(SquatCounter.Cue.NO_BODY) }
    var depth by remember { mutableFloatStateOf(0f) }
    var skeleton by remember { mutableStateOf<Skeleton?>(null) }
    var flash by remember { mutableIntStateOf(0) }
    val tone = remember { runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 80) }.getOrNull() }
    DisposableEffect(Unit) { onDispose { runCatching { tone?.release() } } }

    // Each frame's pose → counter (only while the workout runs).
    val onPose: (Pose, Int, Int, Boolean) -> Unit = { pose, w, h, mirrored ->
        fun lm(type: Int) = pose.getPoseLandmark(type)
        val pts = HashMap<Int, Offset>()
        for (t in JOINTS) lm(t)?.takeIf { it.inFrameLikelihood >= 0.5f }?.let { pts[t] = Offset(it.position.x, it.position.y) }
        skeleton = if (pts.isEmpty()) null else Skeleton(pts, w, h, mirrored)
        if (phase == Phase.ACTIVE) {
            fun p(type: Int): SquatCounter.Pt {
                val l = lm(type) ?: return SquatCounter.Pt(0f, 0f, false)
                return SquatCounter.Pt(l.position.x, l.position.y, l.inFrameLikelihood >= 0.6f)
            }
            val c = counter.update(
                SystemClock.uptimeMillis(),
                p(PoseLandmark.LEFT_SHOULDER), p(PoseLandmark.RIGHT_SHOULDER),
                p(PoseLandmark.LEFT_HIP), p(PoseLandmark.RIGHT_HIP),
                p(PoseLandmark.LEFT_KNEE), p(PoseLandmark.RIGHT_KNEE),
                p(PoseLandmark.LEFT_ANKLE), p(PoseLandmark.RIGHT_ANKLE),
            )
            depth = counter.depth
            if (counter.reps != reps) {
                reps = counter.reps
                flash++
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                runCatching { tone?.startTone(if (reps >= counter.target) ToneGenerator.TONE_PROP_ACK else ToneGenerator.TONE_PROP_BEEP, 160) }
            }
            cue = c
            if (c == SquatCounter.Cue.DONE) phase = Phase.DONE
        }
    }

    LaunchedEffect(phase) {
        if (phase == Phase.COUNTDOWN) {
            for (i in 3 downTo 1) { countdown = i; runCatching { tone?.startTone(ToneGenerator.TONE_PROP_BEEP2, 120) }; delay(900) }
            counter.reset(); reps = 0; cue = SquatCounter.Cue.STAND_TALL
            phase = Phase.ACTIVE
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (hasCamera) CameraWithPose(front, onPose)
        // Skeleton over the camera
        skeleton?.let { sk -> SkeletonOverlay(sk, down = counter.isDown && phase == Phase.ACTIVE, flash = flash) }

        // Top & bottom shading for readability
        Box(Modifier.fillMaxWidth().height(170.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent))))
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(260.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
        )

        // Top bar: close · title · switch camera
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RoundIcon(Icons.Rounded.Close, onClose)
            Text(
                tr("10 squats to unlock"), modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold
            )
            RoundIcon(Icons.Rounded.Cameraswitch) { front = !front }
        }

        // Live counter + depth meter
        if (phase == Phase.ACTIVE || phase == Phase.DONE) {
            RepRing(reps, counter.target, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 64.dp))
            DepthMeter(depth, counter.isDown, Modifier.align(Alignment.CenterEnd).padding(end = 14.dp))
            CuePill(cueText(cue), cue, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 28.dp, start = 20.dp, end = 20.dp))
        }

        // Countdown
        if (phase == Phase.COUNTDOWN) {
            Text(
                "$countdown", modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 140.sp, fontFamily = AppFonts.Numbers),
                color = Color.White
            )
        }

        // Intro / permission card
        AnimatedVisibility(phase == Phase.INTRO, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            if (!hasCamera) CameraNeededCard(asked,
                onAllow = { permLauncher.launch(Manifest.permission.CAMERA) },
                onSettings = {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                })
            else IntroCard(skeleton != null) { phase = Phase.COUNTDOWN }
        }

        // Success
        AnimatedVisibility(phase == Phase.DONE, enter = fadeIn(tween(400)), modifier = Modifier.align(Alignment.Center)) {
            DoneCard(onUnlocked)
        }
    }

    // Re-check permission when coming back from Settings
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME)
                hasCamera = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
}

private fun cueText(c: SquatCounter.Cue): String = when (c) {
    SquatCounter.Cue.NO_BODY -> tr("Step back so your whole body is in the frame")
    SquatCounter.Cue.SHOW_LEGS -> tr("Show your legs and feet too")
    SquatCounter.Cue.STAND_TALL -> tr("Stand up straight to start")
    SquatCounter.Cue.GO_DOWN -> tr("Squat down")
    SquatCounter.Cue.LOWER -> tr("Lower… a bit more")
    SquatCounter.Cue.STAND_UP -> tr("Good! Now stand up")
    SquatCounter.Cue.REP -> tr("Nice! Keep going")
    SquatCounter.Cue.DONE -> tr("Done!")
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
private fun CameraWithPose(front: Boolean, onPose: (Pose, Int, Int, Boolean) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val detector = remember {
        PoseDetection.getClient(PoseDetectorOptions.Builder().setDetectorMode(PoseDetectorOptions.STREAM_MODE).build())
    }
    val latest by androidx.compose.runtime.rememberUpdatedState(onPose)
    DisposableEffect(Unit) { onDispose { runCatching { detector.close() }; executor.shutdown() } }

    DisposableEffect(front) {
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        future.addListener({
            runCatching {
                val p = future.get(); provider = p
                val ratio = ResolutionSelector.Builder().setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY).build()
                val preview = Preview.Builder().setResolutionSelector(ratio).build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val analysis = ImageAnalysis.Builder().setResolutionSelector(ratio)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                analysis.setAnalyzer(executor) { proxy ->
                    val media = proxy.image
                    if (media == null) { proxy.close(); return@setAnalyzer }
                    val rot = proxy.imageInfo.rotationDegrees
                    val w = if (rot % 180 == 0) proxy.width else proxy.height
                    val h = if (rot % 180 == 0) proxy.height else proxy.width
                    detector.process(InputImage.fromMediaImage(media, rot))
                        .addOnSuccessListener(ContextCompat.getMainExecutor(context)) { pose -> latest(pose, w, h, front) }
                        .addOnCompleteListener { proxy.close() }
                }
                val selector = if (front && p.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) CameraSelector.DEFAULT_FRONT_CAMERA
                    else CameraSelector.DEFAULT_BACK_CAMERA
                p.unbindAll()
                p.bindToLifecycle(owner, selector, preview, analysis)
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose { runCatching { provider?.unbindAll() } }
    }
    AndroidView({ previewView }, Modifier.fillMaxSize())
}

@Composable
private fun SkeletonOverlay(sk: Skeleton, down: Boolean, flash: Int) {
    val pulse by animateFloatAsState(if (flash % 2 == 0) 0f else 1f, tween(250), label = "flash")
    val line = if (down) Brand.Gold else Color(0xFF5EEAD4)
    Canvas(Modifier.fillMaxSize()) {
        val scale = maxOf(size.width / sk.imgW, size.height / sk.imgH)
        val dx = (size.width - sk.imgW * scale) / 2f
        val dy = (size.height - sk.imgH * scale) / 2f
        fun map(o: Offset) = Offset((if (sk.mirrored) sk.imgW - o.x else o.x) * scale + dx, o.y * scale + dy)
        val glow = Color.White.copy(alpha = 0.25f + 0.35f * pulse)
        for ((a, b) in BONES) {
            val pa = sk.pts[a] ?: continue; val pb = sk.pts[b] ?: continue
            drawLine(glow, map(pa), map(pb), strokeWidth = 14f, cap = StrokeCap.Round)
            drawLine(line, map(pa), map(pb), strokeWidth = 7f, cap = StrokeCap.Round)
        }
        for ((t, o) in sk.pts) {
            if (t == PoseLandmark.NOSE) continue
            val c = map(o)
            drawCircle(Color.White, 9f, c)
            drawCircle(line, 6f, c)
        }
        sk.pts[PoseLandmark.NOSE]?.let { drawCircle(line, 34f * scale.coerceAtMost(2f), map(it), style = Stroke(5f)) }
    }
}

@Composable
private fun RepRing(reps: Int, target: Int, modifier: Modifier) {
    val prog by animateFloatAsState(reps.toFloat() / target, tween(350), label = "reps")
    Box(modifier.size(132.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val st = 12.dp.toPx()
            drawCircle(Color.Black.copy(alpha = 0.45f), size.minDimension / 2)
            drawArc(Color.White.copy(alpha = 0.18f), 0f, 360f, false, style = Stroke(st),
                topLeft = Offset(st / 2, st / 2), size = androidx.compose.ui.geometry.Size(size.width - st, size.height - st))
            drawArc(Brush.sweepGradient(listOf(Color(0xFFFFE6A3), Brand.Gold, Color(0xFFFF9F43), Color(0xFFFFE6A3))), -90f, 360f * prog, false,
                style = Stroke(st, cap = StrokeCap.Round),
                topLeft = Offset(st / 2, st / 2), size = androidx.compose.ui.geometry.Size(size.width - st, size.height - st))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$reps", style = MaterialTheme.typography.displayMedium.copy(fontFamily = AppFonts.Numbers), color = Color.White)
            Text(tr("of %1\$s", target), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun DepthMeter(depth: Float, down: Boolean, modifier: Modifier) {
    val d by animateFloatAsState(depth, tween(120), label = "depth")
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.width(16.dp).height(190.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.45f))
                .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(50)),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                Modifier.fillMaxWidth().fillMaxHeight(d.coerceIn(0.02f, 1f)).clip(RoundedCornerShape(50))
                    .background(Brush.verticalGradient(listOf(Color(0xFF5EEAD4), if (down) Brand.Gold else Color(0xFF38BDF8))))
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(tr("DEPTH"), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
    }
}

@Composable
private fun CuePill(text: String, cue: SquatCounter.Cue, modifier: Modifier) {
    val good = cue == SquatCounter.Cue.REP || cue == SquatCounter.Cue.STAND_UP || cue == SquatCounter.Cue.DONE
    val warn = cue == SquatCounter.Cue.NO_BODY || cue == SquatCounter.Cue.SHOW_LEGS
    val accent = when { good -> Color(0xFF4ADE80); warn -> Color(0xFFFBBF24); else -> Color(0xFF5EEAD4) }
    Row(
        modifier.clip(RoundedCornerShape(22.dp)).background(Color.Black.copy(alpha = 0.6f))
            .border(1.5.dp, accent.copy(alpha = 0.8f), RoundedCornerShape(22.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(accent))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RoundIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f))
            .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = Color.White) }
}

@Composable
private fun GlassCard(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(14.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.verticalGradient(listOf(Color(0xF20B2A2B), Color(0xF2071012))))
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(28.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) { content() }
}

@Composable
private fun GoldButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(18.dp))
            .background(
                if (enabled) Brush.linearGradient(listOf(Color(0xFFFFE6A3), Color(0xFFFFC857), Color(0xFFFF9F43)))
                else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.12f)))
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (enabled) Color(0xFF4A2A00) else Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun IntroCard(bodySeen: Boolean, onStart: () -> Unit) {
    GlassCard {
        SquatDemo(Modifier.size(110.dp))
        Spacer(Modifier.height(6.dp))
        Text(tr("Earn one more change"), style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(4.dp))
        Text(
            tr("Do 10 real squats and you can change today's limit once more."),
            style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.75f), textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(14.dp))
        Step(1, tr("Lean the phone against something at waist height, or ask a friend to hold it"))
        Step(2, tr("Step back 2–3 steps so your whole body — head to feet — is on screen"))
        Step(3, tr("Squat until your thighs are almost level with the floor, then stand up straight"))
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (bodySeen) Color(0xFF4ADE80) else Color(0xFFFBBF24)))
            Spacer(Modifier.width(8.dp))
            Text(
                if (bodySeen) tr("Body found — you're ready") else tr("Looking for you… step into view"),
                style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f)
            )
        }
        Spacer(Modifier.height(14.dp))
        GoldButton(tr("Start"), onClick = onStart)
        Spacer(Modifier.height(6.dp))
        Text(tr("Tap ⟲ at the top to switch between front and back camera."), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.55f), textAlign = TextAlign.Center)
    }
}

@Composable
private fun Step(n: Int, text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(26.dp).clip(CircleShape).background(Brand.Gold.copy(alpha = 0.18f)).border(1.dp, Brand.Gold.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center
        ) { Text("$n", color = Brand.Gold, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge) }
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Color.White)
    }
}

/** Little looping side-view figure doing squats — shows exactly what to do. */
@Composable
private fun SquatDemo(modifier: Modifier) {
    val inf = rememberInfiniteTransition(label = "demo")
    val t by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse), label = "t")
    val d = (0.5f - 0.5f * kotlin.math.cos(t * Math.PI.toFloat()))
    Canvas(modifier) {
        val s = size.minDimension
        val ankle = Offset(s * 0.42f, s * 0.92f)
        val knee = Offset(s * (0.42f + 0.16f * d), s * (0.70f + 0.04f * d))
        val hip = Offset(s * (0.42f - 0.14f * d), s * (0.50f + 0.22f * d))
        val sh = Offset(hip.x + s * (0.02f + 0.10f * d), hip.y - s * 0.24f)
        val head = Offset(sh.x + s * 0.02f, sh.y - s * 0.09f)
        val hand = Offset(sh.x + s * (0.10f + 0.12f * d), sh.y + s * 0.02f)
        val col = Brand.Gold
        val w = s * 0.055f
        drawLine(Color.White.copy(alpha = 0.18f), Offset(s * 0.12f, s * 0.94f), Offset(s * 0.88f, s * 0.94f), s * 0.02f, StrokeCap.Round)
        drawLine(col, ankle, knee, w, StrokeCap.Round)
        drawLine(col, knee, hip, w, StrokeCap.Round)
        drawLine(Color.White, hip, sh, w, StrokeCap.Round)
        drawLine(Color.White, sh, hand, w * 0.8f, StrokeCap.Round)
        drawCircle(Color.White, s * 0.07f, head)
        drawLine(col, ankle, Offset(ankle.x + s * 0.09f, ankle.y), w, StrokeCap.Round)
    }
}

@Composable
private fun CameraNeededCard(asked: Boolean, onAllow: () -> Unit, onSettings: () -> Unit) {
    GlassCard {
        Box(Modifier.size(64.dp).clip(CircleShape).background(Brand.Gold.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.PhotoCamera, null, tint = Brand.Gold, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(tr("Allow the camera"), style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(6.dp))
        Text(
            tr("The camera is used only to count your squats, right on your phone. Nothing is recorded, saved or sent."),
            style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.75f), textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        GoldButton(tr("Allow camera"), onClick = onAllow)
        if (asked) TextButton(onClick = onSettings) { Text(tr("Open settings"), color = Color.White.copy(alpha = 0.8f)) }
    }
}

@Composable
private fun DoneCard(onContinue: () -> Unit) {
    val pop by animateFloatAsState(1f, tween(500), label = "pop")
    Column(
        Modifier.padding(24.dp).clip(RoundedCornerShape(30.dp))
            .background(Brush.verticalGradient(listOf(Color(0xF2103D2E), Color(0xF2071012))))
            .border(1.dp, Color(0xFF4ADE80).copy(alpha = 0.5f), RoundedCornerShape(30.dp))
            .padding(26.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size((76 * pop).dp).clip(CircleShape).background(Color(0xFF22C55E)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(tr("10 squats done!"), style = MaterialTheme.typography.headlineSmall, color = Color.White, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            tr("Limit unlocked — you can change it once more today."),
            style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f), textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(18.dp))
        GoldButton(tr("Edit my limit"), onClick = onContinue)
    }
}
