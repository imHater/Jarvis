package com.jarvis.ai

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.cos
import kotlin.math.sin

private val Bg = Color(0xFF030711)
private val Panel = Color(0xFF0A1425)
private val Cyan = Color(0xFF00D9FF)
private val Blue = Color(0xFF176BFF)
private val Dim = Color(0xFF8299B8)
private val Green = Color(0xFF43F5B5)
private val Amber = Color(0xFFFFC857)
private val Red = Color(0xFFFF5263)
private val Purple = Color(0xFF9270FF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Cyan,
                    background = Bg,
                    surface = Panel
                )
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
    var showChat by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            vm.startListening()
        } else {
            vm.showNotice(
                "Microphone permission was denied. You can still type to me."
            )
        }
    }

    fun onMicClick() {
        if (vm.state == JarvisState.LISTENING) {
            vm.stopListening()
            return
        }

        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            vm.startListening()
        } else {
            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun sendMessage() {
        val message = input.trim()
        if (message.isNotEmpty()) {
            vm.submit(message)
            input = ""
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Bg,
        contentColor = Color.White
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF07152B),
                            Bg,
                            Color(0xFF030914)
                        )
                    )
                )
        ) {
            // Background ambient glow
            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF073C77).copy(alpha = 0.24f),
                            Color.Transparent
                        ),
                        center = Offset(
                            size.width * 0.5f,
                            size.height * 0.40f
                        ),
                        radius = size.width * 0.68f
                    ),
                    radius = size.width * 0.68f,
                    center = Offset(
                        size.width * 0.5f,
                        size.height * 0.40f
                    )
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 18.dp)
            ) {
                // TOP HEADER
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Cyan.copy(alpha = 0.08f))
                            .border(
                                1.dp,
                                Cyan.copy(alpha = 0.45f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "K",
                            color = Cyan,
                            fontSize = 23.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = vm.assistantName.uppercase(),
                            color = Color.White,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 4.sp
                        )

                        Text(
                            text = "PERSONAL AI ASSISTANT",
                            color = Cyan.copy(alpha = 0.75f),
                            fontSize = 9.sp,
                            letterSpacing = 2.sp
                        )
                    }

                    HeaderButton(
                        text = "NOTES",
                        onClick = { showMemory = true }
                    )

                    Spacer(Modifier.width(6.dp))

                    HeaderButton(
                        text = "⚙",
                        onClick = { showSettings = true }
                    )
                }

                Spacer(Modifier.height(8.dp))

                // MAIN ORB AREA
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = when (vm.state) {
                            JarvisState.IDLE -> "SYSTEM ONLINE"
                            JarvisState.LISTENING -> "LISTENING TO YOU"
                            JarvisState.THINKING -> "PROCESSING"
                            JarvisState.SPEAKING -> "SPEAKING"
                        },
                        color = when (vm.state) {
                            JarvisState.LISTENING -> Green
                            JarvisState.THINKING -> Amber
                            else -> Cyan
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 3.sp
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = when (vm.state) {
                            JarvisState.IDLE -> "How can I help you?"
                            JarvisState.LISTENING -> "I'm listening..."
                            JarvisState.THINKING -> "Give me a moment..."
                            JarvisState.SPEAKING -> "Here's your answer"
                        },
                        color = Color.White.copy(alpha = 0.82f),
                        fontSize = 15.sp
                    )

                    Spacer(Modifier.height(8.dp))

                    // Large animated blue orb
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 390.dp)
                            .aspectRatio(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        KabirOrb(
                            state = vm.state,
                            level = vm.level,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(Modifier.height(2.dp))

                    Text(
                        text = when (vm.state) {
                            JarvisState.IDLE ->
                                "TAP THE MICROPHONE TO TALK"
                            JarvisState.LISTENING ->
                                "SPEAK NOW"
                            JarvisState.THINKING ->
                                "ANALYZING YOUR REQUEST"
                            JarvisState.SPEAKING ->
                                "KABIR IS RESPONDING"
                        },
                        color = Dim,
                        fontSize = 9.sp,
                        letterSpacing = 2.sp
                    )

                    Spacer(Modifier.height(18.dp))

                    // Quick action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        QuickAction(
                            text = "CHAT",
                            selected = showChat,
                            onClick = { showChat = !showChat }
                        )

                        Spacer(Modifier.width(10.dp))

                        QuickAction(
                            text = "NOTES",
                            selected = false,
                            onClick = { showMemory = true }
                        )

                        Spacer(Modifier.width(10.dp))

                        QuickAction(
                            text = "SETTINGS",
                            selected = false,
                            onClick = { showSettings = true }
                        )
                    }
                }

                // Expandable chat area
                AnimatedVisibility(visible = showChat) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(145.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xCC081321))
                            .border(
                                1.dp,
                                Cyan.copy(alpha = 0.18f),
                                RoundedCornerShape(18.dp)
                            )
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "CONVERSATION",
                            color = Cyan,
                            fontSize = 10.sp,
                            letterSpacing = 2.sp
                        )

                        Spacer(Modifier.height(6.dp))

                        val listState = rememberLazyListState()

                        LaunchedEffect(vm.messages.size) {
                            if (vm.messages.isNotEmpty()) {
                                listState.animateScrollToItem(
                                    vm.messages.size - 1
                                )
                            }
                        }

                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (vm.messages.isEmpty()) {
                                item {
                                    Text(
                                        "Hello! I'm ${vm.assistantName}. What can I do for you?",
                                        color = Dim,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            items(vm.messages) { message ->
                                Bubble(message)
                            }
                        }
                    }
                }

                // Pending confirmation controls
                if (vm.pendingConfirm != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { vm.resolveConfirm(true) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Yes")
                        }

                        OutlinedButton(
                            onClick = { vm.resolveConfirm(false) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("No")
                        }
                    }
                }

                // INPUT BAR
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(
                                "Message Kabir...",
                                color = Dim,
                                fontSize = 13.sp
                            )
                        },
                        maxLines = 3,
                        shape = RoundedCornerShape(24.dp),
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Send
                        ),
                        keyboardActions = KeyboardActions(
                            onSend = { sendMessage() }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Cyan,
                            unfocusedBorderColor = Color(0xFF263D59),
                            focusedContainerColor = Color(0xFF0A1526),
                            unfocusedContainerColor = Color(0xFF0A1526),
                            cursorColor = Cyan
                        )
                    )

                    Spacer(Modifier.width(8.dp))

                    // Send button
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(Cyan, Blue)
                                )
                            )
                            .clickable { sendMessage() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "➤",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    // Microphone button
                    val isListening = vm.state == JarvisState.LISTENING

                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .scale(if (isListening) 1.08f else 1f)
                            .clip(CircleShape)
                            .background(
                                if (isListening) {
                                    Red.copy(alpha = 0.18f)
                                } else {
                                    Cyan.copy(alpha = 0.10f)
                                }
                            )
                            .border(
                                1.5.dp,
                                if (isListening) Red else Cyan,
                                CircleShape
                            )
                            .clickable { onMicClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isListening) "■" else "🎙",
                            color = if (isListening) Red else Cyan,
                            fontSize = 22.sp
                        )
                    }
                }
            }
        }
    }

    if (showSettings) {
        SettingsDialog(vm) { showSettings = false }
    }

    if (showMemory) {
        MemoryDialog(vm) { showMemory = false }
    }
}

@Composable
fun KabirOrb(
    state: JarvisState,
    level: Float,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "kabir_orb")

    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (state == JarvisState.THINKING) 2500 else 8000
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val reverseRotation by transition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 11000
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "reverse_rotation"
    )

    val breathing by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (state == JarvisState.SPEAKING) 400 else 1500
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing"
    )

    val micLevel = animateFloatAsState(
        targetValue = if (state == JarvisState.LISTENING) {
            level.coerceIn(0f, 1f)
        } else {
            0f
        },
        animationSpec = tween(100),
        label = "mic_level"
    )

    val scale = when (state) {
        JarvisState.IDLE -> breathing
        JarvisState.LISTENING -> 1f + micLevel.value * 0.16f
        JarvisState.THINKING -> breathing * 1.03f
        JarvisState.SPEAKING -> breathing * 1.08f
    }

    val accent = when (state) {
        JarvisState.IDLE -> Cyan
        JarvisState.LISTENING -> Green
        JarvisState.THINKING -> Purple
        JarvisState.SPEAKING -> Cyan
    }

    Canvas(
        modifier = modifier.scale(scale)
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val minSide = minOf(size.width, size.height)
        val radius = minSide * 0.29f

        // Outer ambient glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Blue.copy(alpha = 0.20f + micLevel.value * 0.18f),
                    Cyan.copy(alpha = 0.08f),
                    Color.Transparent
                ),
                center = center,
                radius = radius * 2.25f
            ),
            radius = radius * 2.25f,
            center = center
        )

        // Outer ring
        drawCircle(
            color = Cyan.copy(alpha = 0.22f),
            radius = radius * 1.72f,
            center = center,
            style = Stroke(width = 1.2.dp.toPx())
        )

        // Rotating segmented rings
        rotate(
            degrees = rotation,
            pivot = center
        ) {
            drawCircle(
                color = Cyan.copy(alpha = 0.85f),
                radius = radius * 1.52f,
                center = center,
                style = Stroke(
                    width = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = null
                )
            )

            drawCircle(
                color = Blue.copy(alpha = 0.9f),
                radius = radius * 1.62f,
                center = center,
                style = Stroke(
                    width = 4.dp.toPx(),
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                        floatArrayOf(18.dp.toPx(), 24.dp.toPx())
                    )
                )
            )

            // Small orbit markers
            for (i in 0 until 8) {
                val angle = Math.toRadians(i * 45.0)
                val orbitRadius = radius * 1.72f

                val x = center.x + cos(angle).toFloat() * orbitRadius
                val y = center.y + sin(angle).toFloat() * orbitRadius

                drawCircle(
                    color = if (i % 2 == 0) Cyan else Blue,
                    radius = if (i % 2 == 0) 4.dp.toPx() else 2.5.dp.toPx(),
                    center = Offset(x, y)
                )
            }
        }

        rotate(
            degrees = reverseRotation,
            pivot = center
        ) {
            drawCircle(
                color = Color(0xFF368DFF).copy(alpha = 0.85f),
                radius = radius * 1.34f,
                center = center,
                style = Stroke(width = 1.5.dp.toPx())
            )

            for (i in 0 until 36) {
                val angle = Math.toRadians(i * 10.0)
                val orbitRadius = radius * 1.34f

                val x = center.x + cos(angle).toFloat() * orbitRadius
                val y = center.y + sin(angle).toFloat() * orbitRadius

                drawCircle(
                    color = Cyan.copy(alpha = if (i % 3 == 0) 0.9f else 0.28f),
                    radius = if (i % 3 == 0) 2.dp.toPx() else 1.dp.toPx(),
                    center = Offset(x, y)
                )
            }
        }

        // Core outer glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFB9F8FF).copy(alpha = 0.95f),
                    Cyan.copy(alpha = 0.92f),
                    Blue.copy(alpha = 0.8f),
                    Color(0xFF061B51).copy(alpha = 0.95f)
                ),
                center = Offset(
                    center.x - radius * 0.22f,
                    center.y - radius * 0.24f
                ),
                radius = radius * 1.2f
            ),
            radius = radius,
            center = center
        )

        // Dark glass center
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF0D4B91),
                    Color(0xFF071A42),
                    Color(0xFF020918)
                ),
                center = Offset(
                    center.x - radius * 0.18f,
                    center.y - radius * 0.2f
                ),
                radius = radius * 0.92f
            ),
            radius = radius * 0.88f,
            center = center
        )

        // Inner luminous circle
        drawCircle(
            color = accent.copy(alpha = 0.62f + micLevel.value * 0.3f),
            radius = radius * 0.62f,
            center = center,
            style = Stroke(width = 1.5.dp.toPx())
        )

        // Inner particles
        for (i in 0 until 18) {
            val angle = Math.toRadians(i * 20.0 + rotation * 0.3)
            val orbitRadius = radius * 0.72f

            val x = center.x + cos(angle).toFloat() * orbitRadius
            val y = center.y + sin(angle).toFloat() * orbitRadius

            drawCircle(
                color = Cyan.copy(alpha = 0.5f),
                radius = 1.5.dp.toPx(),
                center = Offset(x, y)
            )
        }

        // Central highlight
        drawCircle(
            color = Color.White.copy(alpha = 0.8f),
            radius = radius * 0.055f,
            center = Offset(
                center.x - radius * 0.24f,
                center.y - radius * 0.27f
            )
        )
    }
}

@Composable
fun HeaderButton(
    text: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0B1B30))
            .border(
                1.dp,
                Cyan.copy(alpha = 0.22f),
                RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Cyan,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun QuickAction(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (selected) Cyan.copy(alpha = 0.14f)
                else Color(0xFF091527)
            )
            .border(
                1.dp,
                if (selected) Cyan.copy(alpha = 0.7f)
                else Color(0xFF263D59),
                RoundedCornerShape(20.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 17.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (selected) Cyan else Dim,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp
        )
    }
}

@Composable
fun Bubble(m: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (m.fromUser) {
            Arrangement.End
        } else {
            Arrangement.Start
        }
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (m.fromUser) Color(0xFF10344B)
                    else Color(0xFF102035)
                )
                .border(
                    1.dp,
                    if (m.fromUser) Cyan.copy(alpha = 0.20f)
                    else Color.White.copy(alpha = 0.06f),
                    RoundedCornerShape(14.dp)
                )
                .padding(10.dp)
        ) {
            Text(
                text = m.text,
                color = Color.White,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
fun SettingsDialog(
    vm: JarvisViewModel,
    onDismiss: () -> Unit
) {
    var key by remember { mutableStateOf(vm.settings.apiKey) }
    var model by remember { mutableStateOf(vm.settings.model) }
    var name by remember { mutableStateOf(vm.settings.assistantName) }
    var personality by remember { mutableStateOf(vm.settings.personality) }
    var lang by remember { mutableStateOf(vm.voiceLanguage) }
    var speak by remember { mutableStateOf(vm.speakReplies) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kabir Settings") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("Gemini API key") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    "Your API key is stored on this phone. Never share it publicly.",
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 5.dp)
                )

                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("AI model name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Assistant name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = personality,
                    onValueChange = { personality = it },
                    label = { Text("Personality") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                Text("Voice input language")

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = lang == "en-IN",
                        onClick = { lang = "en-IN" },
                        label = { Text("English") }
                    )

                    FilterChip(
                        selected = lang == "hi-IN",
                        onClick = { lang = "hi-IN" },
                        label = { Text("Hindi") }
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Speak replies aloud",
                        modifier = Modifier.weight(1f)
                    )

                    Switch(
                        checked = speak,
                        onCheckedChange = { speak = it }
                    )
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    "PRIVACY\n" +
                        "Your API key and settings are stored on this phone. " +
                        "Questions sent to Gemini may include recent chat and saved notes. " +
                        "Voice recognition is handled by your phone's speech service and " +
                        "may use the internet. Avoid speaking passwords or bank details.",
                    fontSize = 11.sp
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    vm.saveSettings(
                        key,
                        model,
                        name,
                        personality,
                        lang,
                        speak
                    )
                    onDismiss()
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun MemoryDialog(
    vm: JarvisViewModel,
    onDismiss: () -> Unit
) {
    var newNote by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Saved Notes") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    "Only notes you approve are saved. Notes may be sent to the AI " +
                        "as context when you ask online questions.",
                    fontSize = 12.sp
                )

                Spacer(Modifier.height(8.dp))

                if (vm.memories.isEmpty()) {
                    Text("No notes yet.")
                }

                vm.memories.forEach { memory ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = memory.text,
                            modifier = Modifier.weight(1f)
                        )

                        TextButton(
                            onClick = { vm.deleteMemory(memory.id) }
                        ) {
                            Text("Delete")
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = newNote,
                    onValueChange = { newNote = it },
                    label = { Text("New note") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row {
                    TextButton(
                        onClick = {
                            if (newNote.isNotBlank()) {
                                vm.addMemory(newNote.trim())
                                newNote = ""
                            }
                        }
                    ) {
                        Text("Add note")
                    }

                    TextButton(
                        onClick = { vm.clearMemories() }
                    ) {
                        Text("Delete all")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
