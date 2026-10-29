package com.jarvis.ai

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.launch

private val Bg = Color(0xFF02040A)
private val Panel = Color(0xFF0C1626)
private val Cyan = Color(0xFF00E5FF)
private val Dim = Color(0xFF8A9BB5)
private val Amber = Color(0xFFFFB300)
private val Purple = Color(0xFF7C4DFF)
private val Blue = Color(0xFF2979FF)
private val Edge = Color(0xFF2F6BFF)
private val BtnGrad = listOf(Color(0xFF0A2A66), Color(0xFF0B3C94))

class MainActivity : ComponentActivity() {
    private val vm: JarvisViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(primary = Cyan, background = Bg, surface = Panel)
            ) {
                JarvisApp(vm)
            }
        }
    }

    // Privacy: the mic never keeps listening once the app leaves the screen.
    override fun onStop() {
        vm.stopAll()
        super.onStop()
    }
}

private fun glowColor(s: JarvisState): Color = when (s) {
    JarvisState.IDLE -> Blue
    JarvisState.LISTENING -> Cyan
    JarvisState.THINKING -> Purple
    JarvisState.SPEAKING -> Color(0xFF4FC3F7)
}

private fun stateLabel(s: JarvisState): String = when (s) {
    JarvisState.IDLE -> "TAP THE ORB TO TALK"
    JarvisState.LISTENING -> "LISTENING"
    JarvisState.THINKING -> "THINKING"
    JarvisState.SPEAKING -> "SPEAKING  (TAP TO STOP)"
}

@Composable
fun JarvisApp(vm: JarvisViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val chatStore = remember { ChatStore(context) }

    var showSettings by remember { mutableStateOf(false) }
    var showMemory by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var textMode by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    var userName by remember { mutableStateOf(vm.settings.userName) }
    var saved by remember { mutableStateOf(vm.messages.size) }

    // Save finished messages to local history (warnings are not saved).
    LaunchedEffect(vm.messages.size) {
        val n = vm.messages.size
        if (n < saved) saved = n
        while (saved < n) {
            val m = vm.messages[saved]
            if (!m.text.startsWith("⚠")) chatStore.add(m)
            saved++
        }
    }

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) vm.startListening()
        else vm.showNotice("Microphone permission was denied. You can still type to me.")
    }

    // Permissions for calls / SMS / contacts, asked only after you say yes.
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { vm.permissionsResult() }
    LaunchedEffect(vm.permissionRequest) {
        if (vm.permissionRequest.isNotEmpty()) {
            permLauncher.launch(vm.permissionRequest.toTypedArray())
        }
    }

    fun onMicClick() {
        if (vm.state == JarvisState.LISTENING || vm.state == JarvisState.SPEAKING) {
            vm.stopAll()
            return
        }
        if (vm.state == JarvisState.THINKING) return
        textMode = false
        val has = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (has) vm.startListening() else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    fun send() {
        if (input.isNotBlank()) {
            vm.submit(input)
            input = ""
        }
    }

    fun goTyping() {
        vm.stopAll()
        textMode = true
    }

    fun closeDrawer() {
        scope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = Panel) {
                Text(
                    vm.assistantName.uppercase(),
                    color = Cyan, fontSize = 18.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 5.sp,
                    modifier = Modifier.padding(start = 24.dp, top = 32.dp, bottom = 16.dp)
                )
                DrawerEntry("New chat") {
                    vm.newChat()
                    textMode = false
                    closeDrawer()
                }
                DrawerEntry("Chat history") {
                    showHistory = true
                    closeDrawer()
                }
                DrawerEntry("Notes") {
                    showMemory = true
                    closeDrawer()
                }
                DrawerEntry("Settings") {
                    showSettings = true
                    closeDrawer()
                }
                Text(
                    "History and notes are saved on this phone only.",
                    color = Dim, fontSize = 11.sp,
                    modifier = Modifier.padding(24.dp)
                )
            }
        }
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Bg, contentColor = Color.White) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF01030A), Color(0xFF020712), Color(0xFF05163D))
                        )
                    )
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .imePadding()
                ) {
                    TopBar(vm.assistantName) { scope.launch { drawerState.open() } }

                    if (textMode) {
                        // ---------- TEXT CHAT MODE ----------
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OrbImage(vm.state, vm.level, 64.dp) { onMicClick() }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    stateLabel(vm.state),
                                    color = glowColor(vm.state), fontSize = 11.sp, letterSpacing = 2.sp
                                )
                                if (vm.heardText.isNotBlank()) {
                                    Text("\"${vm.heardText}\"", fontSize = 14.sp, maxLines = 2)
                                }
                            }
                        }

                        val listState = rememberLazyListState()
                        LaunchedEffect(vm.messages.size) {
                            if (vm.messages.isNotEmpty()) listState.animateScrollToItem(vm.messages.size - 1)
                        }
                        if (vm.messages.isEmpty()) {
                            Box(
                                Modifier.weight(1f).fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Type below to chat with ${vm.assistantName}.", color = Dim)
                            }
                        } else {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(vm.messages) { m -> Bubble(m) }
                            }
                        }

                        if (vm.pendingConfirm != null) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(onClick = { vm.resolveConfirm(true) }, modifier = Modifier.weight(1f)) {
                                    Text("Yes")
                                }
                                OutlinedButton(onClick = { vm.resolveConfirm(false) }, modifier = Modifier.weight(1f)) {
                                    Text("No")
                                }
                            }
                        }

                        Composer(
                            name = vm.assistantName,
                            input = input,
                            onInput = { input = it },
                            state = vm.state,
                            level = vm.level,
                            onSend = { send() },
                            onMic = { onMicClick() }
                        )
                    } else {
                        // ---------- VOICE MODE (orb only) ----------
                        val last = vm.messages.lastOrNull()
                        val warn = last != null && last.text.startsWith("⚠") && vm.state == JarvisState.IDLE
                        val caption = last?.takeIf {
                            !it.fromUser && (warn || !vm.speakReplies || vm.pendingConfirm != null)
                        }

                        Column(
                            Modifier.weight(1f).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            OrbImage(vm.state, vm.level, 270.dp) { onMicClick() }
                            Spacer(Modifier.height(12.dp))
                            Box(
                                Modifier.fillMaxWidth().height(170.dp),
                                contentAlignment = Alignment.TopCenter
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    if (vm.messages.isEmpty()) {
                                        Text(
                                            if (userName.isBlank()) "Hello" else "Hello $userName",
                                            color = Color(0xFFCBD5E1), fontSize = 16.sp
                                        )
                                        Text(
                                            "How can I help\nyou today?",
                                            fontSize = 30.sp, lineHeight = 36.sp,
                                            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
                                        )
                                        Spacer(Modifier.height(8.dp))
                                    }
                                    Text(
                                        stateLabel(vm.state),
                                        color = glowColor(vm.state), fontSize = 11.sp, letterSpacing = 3.sp
                                    )
                                    if (vm.handsFree) Text("Hands-free ON", color = Dim, fontSize = 11.sp)
                                    if (vm.heardText.isNotBlank()) {
                                        Text(
                                            "\"${vm.heardText}\"",
                                            fontSize = 15.sp, textAlign = TextAlign.Center, maxLines = 2,
                                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
                                        )
                                    }
                                    if (caption != null) {
                                        Text(
                                            caption.text,
                                            color = if (warn) Amber else if (vm.pendingConfirm != null) Color.White else Dim,
                                            fontSize = 13.sp, textAlign = TextAlign.Center, maxLines = 4,
                                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }

                        if (vm.pendingConfirm != null) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(onClick = { vm.resolveConfirm(true) }, modifier = Modifier.weight(1f)) {
                                    Text("Yes")
                                }
                                OutlinedButton(onClick = { vm.resolveConfirm(false) }, modifier = Modifier.weight(1f)) {
                                    Text("No")
                                }
                            }
                        }

                        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
                            TypePill(Modifier.align(Alignment.CenterStart)) { goTyping() }
                            MicButton(vm.state, vm.level, Modifier.align(Alignment.Center)) { onMicClick() }
                        }
                    }
                }
            }
        }
    }

    if (showSettings) {
        SettingsDialog(vm, onDismiss = { showSettings = false }, onUserName = { userName = it })
    }
    if (showMemory) MemoryDialog(vm) { showMemory = false }
    if (showHistory) HistoryDialog(chatStore, vm.assistantName) { showHistory = false }
}

@Composable
private fun DrawerEntry(label: String, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(label) },
        selected = false,
        onClick = onClick,
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
    )
}

@Composable
fun TopBar(name: String, onMenu: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(Modifier.size(48.dp).clip(CircleShape).clickable { onMenu() }) {
            val w = size.width * 0.42f
            val x0 = (size.width - w) / 2f
            val gap = 7.dp.toPx()
            for (i in -1..1) {
                val y = center.y + i * gap
                drawLine(
                    color = Color(0xFFBBD4FF),
                    start = Offset(x0, y), end = Offset(x0 + w, y),
                    strokeWidth = 2.5f.dp.toPx(), cap = StrokeCap.Round
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        Text(
            name.uppercase(),
            color = Cyan, fontSize = 16.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 5.sp
        )
    }
}

@Composable
fun OrbImage(state: JarvisState, level: Float, diameter: Dp, onClick: () -> Unit) {
    val t = rememberInfiniteTransition(label = "orb")
    val spin by t.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(30000, easing = LinearEasing), RepeatMode.Restart),
        label = "spin"
    )
    val breathe by t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart),
        label = "breathe"
    )
    val glow = glowColor(state)
    val target = when (state) {
        JarvisState.IDLE -> 0f
        JarvisState.LISTENING -> level
        JarvisState.THINKING -> 0.5f
        JarvisState.SPEAKING -> 0.6f
    }
    val energy by animateFloatAsState(target, tween(120), label = "energy")
    val wob = sin(breathe * 2f * PI.toFloat())
    val breath = when (state) {
        JarvisState.IDLE -> 0.015f * wob
        JarvisState.LISTENING -> 0.01f * wob
        JarvisState.THINKING -> 0.05f * wob
        JarvisState.SPEAKING -> 0.05f * wob
    }
    val s = 1f + breath + 0.12f * energy

    Box(
        Modifier.size(diameter).clip(CircleShape).clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(glow.copy(alpha = 0.25f + 0.35f * energy), Color.Transparent),
                    center = center, radius = r
                ),
                radius = r, center = center
            )
        }
        Image(
            painter = painterResource(R.drawable.kabir_orb),
            contentDescription = "Orb",
            modifier = Modifier
                .fillMaxSize(0.88f)
                .graphicsLayer {
                    rotationZ = spin
                    scaleX = s
                    scaleY = s
                }
        )
    }
}

@Composable
fun TypePill(modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier
            .clip(shape)
            .background(Panel)
            .border(1.dp, Edge, shape)
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Text("Type", color = Color(0xFFBBD4FF), fontSize = 14.sp)
    }
}

@Composable
fun MicButton(state: JarvisState, level: Float, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .size(68.dp)
            .clip(CircleShape)
            .background(Brush.horizontalGradient(BtnGrad))
            .border(1.dp, Edge, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) { WaveGlyph(state, level, 40.dp) }
}

@Composable
fun WaveGlyph(state: JarvisState, level: Float, box: Dp) {
    val pattern = listOf(0.35f, 0.65f, 1f, 0.65f, 0.35f)
    val listening = state == JarvisState.LISTENING
    val color = when (state) {
        JarvisState.LISTENING -> Color(0xFF3DDC84)
        JarvisState.SPEAKING -> Color(0xFFB39DFF)
        else -> Color(0xFFBBD4FF)
    }
    Canvas(Modifier.size(box)) {
        val gap = size.width / 6f
        val maxH = size.height * 0.6f
        val k = if (listening) 0.3f + 0.7f * level else 0.75f
        pattern.forEachIndexed { i, p ->
            val h = maxH * p * k
            val x = gap * (i + 1)
            drawLine(
                color = color,
                start = Offset(x, center.y - h / 2f),
                end = Offset(x, center.y + h / 2f),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun SendGlyph(color: Color, box: Dp) {
    Canvas(Modifier.size(box)) {
        val cx = center.x
        val top = size.height * 0.2f
        val bottom = size.height * 0.8f
        val sw = 2.5f.dp.toPx()
        drawLine(color, Offset(cx, bottom), Offset(cx, top), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(
            color, Offset(cx, top), Offset(cx - size.width * 0.28f, top + size.height * 0.28f),
            strokeWidth = sw, cap = StrokeCap.Round
        )
        drawLine(
            color, Offset(cx, top), Offset(cx + size.width * 0.28f, top + size.height * 0.28f),
            strokeWidth = sw, cap = StrokeCap.Round
        )
    }
}

@Composable
fun Composer(
    name: String,
    input: String,
    onInput: (String) -> Unit,
    state: JarvisState,
    level: Float,
    onSend: () -> Unit,
    onMic: () -> Unit
) {
    val shape = RoundedCornerShape(28.dp)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try { focus.requestFocus() } catch (e: Exception) { }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .background(Panel, shape)
            .border(1.dp, Edge, shape)
            .padding(start = 18.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Box(Modifier.weight(1f).padding(vertical = 10.dp)) {
            if (input.isEmpty()) Text("Message $name", color = Dim, fontSize = 16.sp)
            BasicTextField(
                value = input,
                onValueChange = onInput,
                textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                cursorBrush = SolidColor(Cyan),
                maxLines = 6,
                modifier = Modifier.fillMaxWidth().focusRequester(focus)
            )
        }
        Spacer(Modifier.width(8.dp))
        if (input.isNotBlank()) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(Cyan).clickable { onSend() },
                contentAlignment = Alignment.Center
            ) { SendGlyph(Bg, 22.dp) }
        } else {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Brush.horizontalGradient(BtnGrad))
                    .border(1.dp, Edge, CircleShape)
                    .clickable { onMic() },
                contentAlignment = Alignment.Center
            ) { WaveGlyph(state, level, 28.dp) }
        }
    }
}

@Composable
fun Bubble(m: ChatMessage) {
    val warn = m.text.startsWith("⚠")
    if (!m.fromUser && !warn) {
        // Assistant replies: plain text, like ChatGPT
        Text(
            m.text,
            color = Color(0xFFE6EEF9), fontSize = 16.sp, lineHeight = 23.sp,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)
        )
        return
    }
    val shape = RoundedCornerShape(18.dp)
    val bg = if (m.fromUser) Color(0xFF0C3A7A) else Color(0xFF2A2110)
    val edge = if (m.fromUser) Edge else Amber.copy(alpha = 0.5f)
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (m.fromUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            Modifier
                .widthIn(max = 300.dp)
                .clip(shape)
                .background(bg)
                .border(1.dp, edge, shape)
                .padding(12.dp)
        ) {
            Text(m.text, color = Color.White)
        }
    }
}

@Composable
fun HistoryDialog(store: ChatStore, name: String, onDismiss: () -> Unit) {
    var list by remember { mutableStateOf(store.getAll()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chat history") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Saved only on this phone. Delete it anytime.", fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                if (list.isEmpty()) Text("No history yet.")
                list.forEach { m ->
                    Text(
                        (if (m.fromUser) "You: " else "$name: ") + m.text,
                        color = if (m.fromUser) Cyan else Color.White,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = {
            TextButton(onClick = {
                store.clear()
                list = emptyList()
            }) { Text("Delete all") }
        }
    )
}

@Composable
fun SettingsDialog(vm: JarvisViewModel, onDismiss: () -> Unit, onUserName: (String) -> Unit) {
    var key by remember { mutableStateOf(vm.settings.apiKey) }
    var model by remember { mutableStateOf(vm.settings.model) }
    var userNameField by remember { mutableStateOf(vm.settings.userName) }
    var name by remember { mutableStateOf(vm.settings.assistantName) }
    var personality by remember { mutableStateOf(vm.settings.personality) }
    var lang by remember { mutableStateOf(vm.voiceLanguage) }
    var speak by remember { mutableStateOf(vm.speakReplies) }
    var hands by remember { mutableStateOf(vm.handsFree) }
    var speed by remember { mutableStateOf(vm.speechSpeed) }
    var inKey by remember { mutableStateOf(vm.settings.inworldKey) }
    var inVoice by remember { mutableStateOf(vm.settings.inworldVoiceId) }
    var inModel by remember { mutableStateOf(vm.settings.inworldModel) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = key, onValueChange = { key = it },
                    label = { Text("Gemini API key") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Get a free key at aistudio.google.com (Get API key). " +
                        "It is stored encrypted on this phone only.",
                    fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp)
                )
                OutlinedTextField(
                    value = model, onValueChange = { model = it },
                    label = { Text("AI model name") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = userNameField, onValueChange = { userNameField = it },
                    label = { Text("Your name (for the greeting)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Assistant name") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = personality, onValueChange = { personality = it },
                    label = { Text("Personality") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text("Voice input language")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = lang == "en-IN", onClick = { lang = "en-IN" },
                        label = { Text("English") }
                    )
                    FilterChip(
                        selected = lang == "hi-IN", onClick = { lang = "hi-IN" },
                        label = { Text("Hindi") }
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text("Inworld voice (optional)")
                OutlinedTextField(
                    value = inKey, onValueChange = { inKey = it },
                    label = { Text("Inworld API key") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = inVoice, onValueChange = { inVoice = it },
                    label = { Text("Inworld voice ID") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = inModel, onValueChange = { inModel = it },
                    label = { Text("Inworld model name") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "If key and voice ID are filled, each spoken reply's text is sent to " +
                        "Inworld to make the voice. Leave them empty to use the phone's own " +
                        "voice. Speaking speed applies to the phone voice only.",
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(8.dp))
                Text("Speaking speed")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = speed == 0.85f, onClick = { speed = 0.85f },
                        label = { Text("Slow") }
                    )
                    FilterChip(
                        selected = speed == 1.0f, onClick = { speed = 1.0f },
                        label = { Text("Normal") }
                    )
                    FilterChip(
                        selected = speed == 1.2f, onClick = { speed = 1.2f },
                        label = { Text("Fast") }
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Speak replies aloud", modifier = Modifier.weight(1f))
                    Switch(checked = speak, onCheckedChange = { speak = it })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Hands-free conversation", modifier = Modifier.weight(1f))
                    Switch(checked = hands, onCheckedChange = { hands = it })
                }
                Text(
                    "Hands-free: after a reply is spoken, it listens again. It stops after a few " +
                        "seconds of silence, when you tap the orb, or when you leave the app. " +
                        "It never listens in the background.",
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "PRIVACY\n" +
                        "On this phone only: your keys (encrypted), settings, saved notes and chat history.\n" +
                        "Sent to Google Gemini: what you type or say (as text), recent chat, the " +
                        "personality text and your saved notes, but only for questions that are " +
                        "not local commands.\n" +
                        "Sent to Inworld (only if you set it up): the text of replies, to make the voice.\n" +
                        "Calls and SMS: only after you say yes. Contacts are searched on the phone " +
                        "for the name you say; the contact list is never sent anywhere.\n" +
                        "Local commands (open app, torch, time, dialer) never go online.\n" +
                        "Voice recognition is done by your phone's speech service and may use " +
                        "internet. On the free tier Google may use data to improve its products, " +
                        "so never say passwords or bank details. Free limits apply.",
                    fontSize = 11.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                vm.settings.userName = userNameField
                vm.settings.inworldKey = inKey
                vm.settings.inworldVoiceId = inVoice
                vm.settings.inworldModel = inModel.ifBlank { "inworld-tts-2" }
                onUserName(userNameField.trim())
                vm.saveSettings(key, model, name, personality, lang, speak, hands, speed)
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun MemoryDialog(vm: JarvisViewModel, onDismiss: () -> Unit) {
    var newNote by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Saved notes") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Only notes you approve are saved, on this phone. They are shared " +
                        "with the AI as context when you ask online questions.",
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(8.dp))
                if (vm.memories.isEmpty()) Text("No notes yet.")
                vm.memories.forEach { m ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(m.text, Modifier.weight(1f))
                        TextButton(onClick = { vm.deleteMemory(m.id) }) { Text("Delete") }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = newNote, onValueChange = { newNote = it },
                    label = { Text("New note") }, modifier = Modifier.fillMaxWidth()
                )
                Row {
                    TextButton(onClick = {
                        vm.addMemory(newNote)
                        newNote = ""
                    }) { Text("Add note") }
                    TextButton(onClick = { vm.clearMemories() }) { Text("Delete all") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
