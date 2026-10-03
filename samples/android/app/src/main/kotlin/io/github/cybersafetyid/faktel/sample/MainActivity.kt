package io.github.cybersafetyid.faktel.sample

import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.cybersafetyid.faktel.core.geometry.Point
import io.github.cybersafetyid.faktel.face.FaceAnalysis
import io.github.cybersafetyid.faktel.ktp.KtpScanResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val Bg = Color(0xFF0B0F1A)
private val Surface1 = Color(0xFF141B2D)
private val Surface2 = Color(0xFF1C2540)
private val Accent = Color(0xFF2DD4BF)
private val Indigo = Color(0xFF818CF8)
private val Good = Color(0xFF34D399)
private val Warn = Color(0xFFFBBF24)
private val Bad = Color(0xFFF87171)
private val Muted = Color(0xFF94A3B8)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val scheme = darkColorScheme(primary = Accent, background = Bg, surface = Surface1, onSurface = Color.White)
            MaterialTheme(colorScheme = scheme) { Surface(color = Bg, modifier = Modifier.fillMaxSize()) { App() } }
        }
    }
}

private enum class Tab(val label: String, val sample: String) {
    Selfie("Selfie check", "face_b.jpg"),
    Ktp("KTP scan", "ktp_sample.jpg"),
}

@Composable
private fun App() {
    val context = LocalContext.current
    // Loading models takes a moment: keep it off the main thread.
    var engine by remember { mutableStateOf<FaktelEngine?>(null) }
    LaunchedEffect(Unit) { engine = withContext(Dispatchers.Default) { FaktelEngine(context.applicationContext) } }
    var tab by remember { mutableStateOf(Tab.Selfie) }
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var face by remember { mutableStateOf<Pair<FaceAnalysis, Long>?>(null) }
    var ktp by remember { mutableStateOf<Pair<KtpScanResult, Long>?>(null) }
    var busy by remember { mutableStateOf(false) }

    // Auto-load the first sample so the screen is never empty.
    suspend fun run(bytes: ByteArray) {
        busy = true
        withContext(Dispatchers.Default) {
            val bmp = decodeUpright(bytes)
            val rgb = bmp.asRgb()
            if (tab == Tab.Selfie) {
                val r = engine!!.analyzeSelfie(rgb); face = r.value to r.millis; ktp = null
            } else {
                val r = engine!!.scanKtp(rgb); ktp = r.value to r.millis; face = null
            }
            photo = bmp
        }
        busy = false
    }

    var pending by remember { mutableStateOf<ByteArray?>(null) }
    LaunchedEffect(tab) { photo = null; face = null; ktp = null; pending = context.assets.open(tab.sample).readBytes() }
    LaunchedEffect(pending, engine) { val b = pending; if (b != null && engine != null) { run(b); pending = null } }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { pending = context.contentResolver.openInputStream(it)?.use { s -> s.readBytes() } }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Accent), contentAlignment = Alignment.Center) {
                    Text("F", color = Bg, fontWeight = FontWeight.Black, fontSize = 18.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Faktel", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("On-device face & e-KTP checks", fontSize = 12.sp, color = Muted)
                }
            }
            Spacer(Modifier.height(20.dp))
            PhotoCard(photo, face?.first, ktp?.first, busy)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill("Sample", Accent) { pending = context.assets.open(tab.sample).readBytes() }
                Pill("Pick photo", Muted) { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            }
            Spacer(Modifier.height(18.dp))
            face?.let { (a, ms) -> FaceResult(a, ms) }
            ktp?.let { (r, ms) -> KtpResult(r, ms) }
            Spacer(Modifier.height(24.dp))
        }
        Row(Modifier.fillMaxWidth().background(Surface1).padding(8.dp)) {
            Tab.entries.forEach { t ->
                val sel = t == tab
                Box(
                    Modifier.weight(1f).padding(4.dp).clip(RoundedCornerShape(14.dp))
                        .background(if (sel) Surface2 else Color.Transparent).clickable { tab = t }.padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(t.label, color = if (sel) Accent else Muted, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable
private fun Pill(text: String, color: Color, onClick: () -> Unit) {
    Text(
        text, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(CircleShape).border(1.dp, color.copy(alpha = .5f), CircleShape)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp),
    )
}

@Composable
private fun PhotoCard(photo: Bitmap?, face: FaceAnalysis?, ktp: KtpScanResult?, busy: Boolean) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Surface1), contentAlignment = Alignment.Center) {
        if (photo == null) {
            Box(Modifier.fillMaxWidth().height(320.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Accent) }
        } else {
            val ratio = (photo.width.toFloat() / photo.height).coerceAtLeast(0.6f)
            Box(Modifier.fillMaxWidth().aspectRatio(ratio).heightIn(max = 460.dp)) {
                Image(photo.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                Canvas(Modifier.fillMaxSize()) {
                    val k = size.width / photo.width
                    fun Point.o() = Offset((x * k).toFloat(), (y * k).toFloat())
                    face?.faces?.forEach { f ->
                        val b = f.box
                        val ok = face.isAcceptable
                        drawRoundRect(
                            if (ok) Good else Warn, Offset((b.left * k).toFloat(), (b.top * k).toFloat()),
                            Size((b.width * k).toFloat(), (b.height * k).toFloat()),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(14f), style = Stroke(5f),
                        )
                        f.landmarks.asList().forEach { drawCircle(Accent, 7f, it.o()) }
                    }
                    ktp?.detection?.quad?.let { q ->
                        val p = androidx.compose.ui.graphics.Path().apply {
                            moveTo(q.topLeft.o().x, q.topLeft.o().y); lineTo(q.topRight.o().x, q.topRight.o().y)
                            lineTo(q.bottomRight.o().x, q.bottomRight.o().y); lineTo(q.bottomLeft.o().x, q.bottomLeft.o().y); close()
                        }
                        drawPath(p, if (ktp.isAcceptable) Good else Warn, style = Stroke(6f))
                    }
                }
            }
        }
        if (busy) LinearProgressIndicator(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), color = Accent, trackColor = Color.Transparent)
    }
}

@Composable
private fun Verdict(ok: Boolean, title: String, ms: Long) {
    val c = if (ok) Good else Warn
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.copy(alpha = .12f)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(c))
        Spacer(Modifier.width(12.dp))
        Text(title, color = c, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Text("$ms ms", color = Muted, fontSize = 12.sp)
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Surface1).padding(14.dp)) {
        Text(label, color = Muted, fontSize = 11.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Metrics(vararg items: Pair<String, String>) {
    items.toList().chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { (l, v) -> Metric(l, v, Modifier.weight(1f)) }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun Issues(names: List<String>) {
    if (names.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        names.forEach {
            Text(it, color = Warn, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.clip(CircleShape).background(Warn.copy(alpha = .12f)).padding(horizontal = 12.dp, vertical = 6.dp))
        }
    }
}

@Composable
private fun FaceResult(a: FaceAnalysis, ms: Long) {
    val q = a.quality
    val live = a.liveness
    Verdict(a.isAcceptable, if (a.isAcceptable) "Selfie accepted" else if (a.face == null) "No face found" else "Needs another try", ms)
    Metrics(
        "Detector confidence" to (a.face?.let { "%.0f%%".format(it.score * 100) } ?: "-"),
        "Liveness (real)" to (live?.let { "%.0f%%".format(it.realScore * 100) + if (it.isLive) " ✓" else " ✗" } ?: "skipped"),
        "Sharpness" to (q?.let { "%.0f".format(it.blurScore) } ?: "-"),
        "Head pose" to (q?.let { "yaw %.0f° roll %.0f°".format(it.pose.yaw, it.pose.roll) } ?: "-"),
    )
    Issues(a.issues.map { it.name })
}

@Composable
private fun KtpResult(r: KtpScanResult, ms: Long) {
    Verdict(r.isAcceptable, if (r.isAcceptable) "KTP-like card accepted" else "Card needs a retake", ms)
    r.card?.let { card ->
        val bmp = remember(card) { card.toBitmap() }
        Text("Rectified card (1011×638)", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        Image(bmp.asImageBitmap(), null, Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).aspectRatio(bmp.width.toFloat() / bmp.height), contentScale = ContentScale.Fit)
        Spacer(Modifier.height(12.dp))
    }
    Metrics(
        "Sharpness" to (r.blurScore?.let { "%.0f".format(it) } ?: "-"),
        "Glare" to (r.glareRatio?.let { "%.1f%%".format(it * 100) } ?: "-"),
        "Card confidence" to (r.detection?.let { "%.0f%%".format(it.confidence * 100) } ?: "-"),
        "Portrait" to (if (r.portrait != null) "found" else "missing"),
    )
    Issues(r.issues.map { it.name })
}
