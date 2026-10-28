package com.jarvis.ai

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel

private val Bg = Color(0xFF05080F)
private val Panel = Color(0xFF0F1724)
private val Cyan = Color(0xFF00E5FF)
private val Dim = Color(0xFF7C8DA6)
private val Green = Color(0xFF3DDC84)
private val Amber = Color(0xFFFFB300)
private val Red = Color(0xFFFF5252)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(primary = Cyan, background = Bg, surface = Panel)
            ) {
                val vm: JarvisViewModel = viewModel()
                JarvisApp(vm)
            }
        }
    }
}

@Composable
fun JarvisApp(vm: JarvisViewModel) {
    val context = LocalContext.current
    var showSettings by remember { mutableStateOf(false) }
    var showMemory by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) vm.startListening()
        else vm.showNotice("Microphone permission was denied. You can still type to me.")
    }

    fun onMicClick() {
        if (vm.state == JarvisState.LISTENING) {
            vm.stopListening()
            return
        }
        val has = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (has) vm.startListening() else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Bg, contentColor = Color.White) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            // Header
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AnimatedOrb(
    state = vm.state,
    level = vm.level,
    modifier = Modifier.width(52.dp)
)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        vm.assistantName.uppercase(),
                        color = Cyan, fontSize = 22.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 4.sp
                    )
                    Text(
                        when (vm.state) {
                            JarvisState.IDLE -> "Ready"
                            JarvisState.LISTENING -> "Listening..."
                            JarvisState.THINKING -> "Thinking..."
                            JarvisState.SPEAKING -> "Speaking..."
                        },
                        color = Dim, fontSize = 12.sp
                    )
                }
                TextButton(onClick = { showMemory = true }) { Text("Notes") }
                TextButton(onClick = { showSettings = true }) { Text("Settings") }
            }

            // Conversation
            val listState = rememberLazyListState()
            LaunchedEffect(vm.messages.size) {
                if (vm.messages.isNotEmpty()) listState.animateScrollToItem(vm.messages.size - 1)
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (vm.messages.isEmpty()) {
                    item {
                        Text(
                            "Namaste! Type or tap the mic. Say \"help\" to see what I can do " +
                                "offline. For AI answers, add your Gemini key in Settings.",
                            color = Dim, modifier = Modifier.padding(8.dp)
                        )
                    }
                }
                items(vm.messages) { m -> Bubble(m) }
            }

            // Confirmation buttons
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

            // Input row
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Type or tap the mic...", color = Dim) },
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Cyan,
                        unfocusedBorderColor = Dim
                    )
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    vm.submit(input)
                    input = ""
                }) { Text("Send") }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(if (vm.state == JarvisState.LISTENING) Red else Cyan)
                        .clickable { onMicClick() },
                    contentAlignment = Alignment.Center
                ) { Text("🎤", fontSize = 22.sp) }
            }
        }
    }

    if (showSettings) SettingsDialog(vm) { showSettings = false }
    if (showMemory) MemoryDialog(vm) { showMemory = false }
}

@Composable
fun Orb(state: JarvisState) {
    val transition = rememberInfiniteTransition(label = "orb")
    val pulse by transition.animateFloat(
        initialValue = 0.9f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse"
    )
    val color = when (state) {
        JarvisState.IDLE -> Cyan
        JarvisState.LISTENING -> Green
        JarvisState.THINKING -> Amber
        JarvisState.SPEAKING -> Color(0xFF7C4DFF)
    }
    val s = if (state == JarvisState.IDLE) 1f else pulse
    Box(
        Modifier
            .size(44.dp)
            .scale(s)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.25f))
            .border(2.dp, color, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.size(16.dp).clip(CircleShape).background(color))
    }
}

@Composable
fun Bubble(m: ChatMessage) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (m.fromUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (m.fromUser) Color(0xFF12394A) else Panel)
                .padding(12.dp)
        ) {
            Text(m.text, color = Color.White)
        }
    }
}

@Composable
fun SettingsDialog(vm: JarvisViewModel, onDismiss: () -> Unit) {
    var key by remember { mutableStateOf(vm.settings.apiKey) }
    var model by remember { mutableStateOf(vm.settings.model) }
    var name by remember { mutableStateOf(vm.settings.assistantName) }
    var personality by remember { mutableStateOf(vm.settings.personality) }
    var lang by remember { mutableStateOf(vm.voiceLanguage) }
    var speak by remember { mutableStateOf(vm.speakReplies) }

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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Speak replies aloud", modifier = Modifier.weight(1f))
                    Switch(checked = speak, onCheckedChange = { speak = it })
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "PRIVACY\n" +
                        "On this phone only: your API key (encrypted), settings and saved notes.\n" +
                        "Sent to Google Gemini: what you type or say (as text), recent chat and your " +
                        "saved notes, but only for questions that are not local commands.\n" +
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
                vm.saveSettings(key, model, name, personality, lang, speak)
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
