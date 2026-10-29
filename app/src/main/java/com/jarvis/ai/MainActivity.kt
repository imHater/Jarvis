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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import kotlin.math.PI
import kotlin.math.sin

private val Bg = Color(0xFF02040A)
private val Panel = Color(0xFF0C1626)
private val Cyan = Color(0xFF00E5FF)
private val Dim = Color(0xFF8A9BB5)
private val Green = Color(0xFF3DDC84)
private val Amber = Color(0xFFFFB300)
private val Purple = Color(0xFF7C4DFF)
private val Blue = Color(0xFF2979FF)

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
    var showSettings by remember { mutableStateOf(false) }
    var showMemory by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    var userName by remember { mutableStateOf(vm.settings.userName) }

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) vm.startListening()
        else vm.showNotice("Microphone permission was denied. You can still type to me.")
    }

    fun onMicClick() {
        if (vm.state == JarvisState.LISTENING || vm.state == JarvisState.SPEAKING) {
            vm.stopAll()
            return
        }
        if (vm.state == JarvisState.THINKING) return
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
                TopBar(
                    name = vm.assistantName,
                    onNotes = { showMemory = true },
                    onSettings = { showSettings = true }
                )

                if (vm.messages.isEmpty()) {
                    // ---------- HOME SCREEN ----------
                    Column(
                        Modifier.weight(1f).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        OrbImage(vm.state, vm.level, 280.dp) { onMicClick() }
                        Spacer(Modifier.height(20.dp))
                        Text(
                            if (userName.isBlank()) "Hello" else "Hello $userName",
                            color = Color(0xFFCBD5E1), fontSize = 16.sp
                        )
                        Text(
                            "How can I help\nyou today?",
                            fontSize = 30.sp, lineHeight = 36.sp,
                            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(10.dp))
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
                    }
                } else {
                    // ---------- CHAT SCREEN ----------
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OrbImage(vm.state, vm.level, 72.dp) { onMicClick() }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                stateLabel(vm.state),
                                color = glowColor(vm.state), fontSize = 11.sp, letterSpacing = 2.sp
                            )
                            if (vm.heardText.isNotBlank()) {
                                Text("\"${vm.heardText}\"", fontSize = 14.sp, maxLines = 2)
                            } else if (vm.handsFree) {
                                Text("Hands-free ON", color = Dim, fontSize = 11.sp)
                            }
                        }
                    }

                    val listState = rememberLazyListState()
                    LaunchedEffect(vm.messages.size) {
                        if (vm.messages.isNotEmpty()) listState.animateScrollToItem(vm.messages.size - 1)
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(vm.messages) { m -> Bubble(m) }
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
                }

                AskBar(
                    input = input,
                    onInput = { input = it },
                    state = vm.state,
                    level = vm.level,
                    onSend = { send() },
                    onMic = { onMicClick() }
                )
            }
        }
    }

    if (showSettings) {
        SettingsDialog(vm, onDismiss = { showSettings = false }, onUserName = { userName = it })
    }
    if (showMemory) MemoryDialog(vm) { showMemory = false }
}

@Composable
fun TopBar(name: String, onNotes: () -> Unit, onSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            name.uppercase(),
            color = Cyan, fontSize = 16.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 5.sp,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onNotes) { Text("Notes") }
        TextButton(onClick = onSettings) { Text("Settings") }
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
fun AskBar(
    input: String,
    onInput: (String) -> Unit,
    state: JarvisState,
    level: Float,
    onSend: () -> Unit,
    onMic: () -> Unit
) {
    val shape = RoundedCornerShape(32.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .background(
                Brush.horizontalGradient(listOf(Color(0xFF0A2A66), Color(0xFF0B3C94))),
                shape
            )
            .border(1.dp, Color(0xFF2F6BFF), shape)
            .padding(start = 8.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = onInput,
            modifier = Modifier.weight(1f),
            singleLine = true,
            placeholder = { Text("Ask anything...", color = Color(0xFF9DB7E8)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = Cyan,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent
            )
        )
        if (input.isNotBlank()) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(Cyan).clickable { onSend() },
                contentAlignment = Alignment.Center
            ) { Text("➤", color = Bg, fontSize = 18.sp) }
        } else {
            WaveIcon(state, level, onMic)
        }
    }
}

@Composable
fun WaveIcon(state: JarvisState, level: Float, onClick: () -> Unit) {
    val pattern = listOf(0.35f, 0.65f, 1f, 0.65f, 0.35f)
    val listening = state == JarvisState.LISTENING
    val color = when (state) {
        JarvisState.LISTENING -> Green
        JarvisState.SPEAKING -> Color(0xFFB39DFF)
        else -> Color(0xFFBBD4FF)
    }
    Canvas(Modifier.size(48.dp).clip(CircleShape).clickable { onClick() }) {
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
fun Bubble(m: ChatMessage) {
    val warn = m.text.startsWith("⚠")
    val shape = RoundedCornerShape(18.dp)
    val bg = when {
        m.fromUser -> Color(0xFF0C3A7A)
        warn -> Color(0xFF2A2110)
        else -> Panel
    }
    val edge = when {
        m.fromUser -> Color(0xFF2F6BFF)
        warn -> Amber.copy(alpha = 0.5f)
        else -> Color(0xFF1B2C4A)
    }
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
                        "On this phone only: your API key (encrypted), settings and saved notes.\n" +
                        "Sent to Google Gemini: what you type or say (as text), recent chat, the " +
                        "personality text and your saved notes, but only for questions that are " +
                        "not local commands.\n" +
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
