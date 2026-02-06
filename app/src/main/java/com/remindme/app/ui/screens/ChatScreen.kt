package com.remindme.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.remindme.app.ui.theme.*
import com.remindme.app.viewmodel.ChatMessage
import com.remindme.app.viewmodel.ChatViewModel
import com.remindme.app.voice.ListeningState
import com.remindme.app.voice.TtsState
import com.remindme.app.voice.VoiceInteractionState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    isVoiceMode: Boolean,
    onToggleMode: () -> Unit
) {
    val messages by viewModel.messages.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current

    // Voice state
    val voiceManager = viewModel.voiceManager
    val listeningState by voiceManager.speechRecognizer.listeningState.collectAsState()
    val partialResult by voiceManager.speechRecognizer.partialResult.collectAsState()
    val rmsLevel by voiceManager.speechRecognizer.rmsLevel.collectAsState()
    val errorMessage by voiceManager.speechRecognizer.errorMessage.collectAsState()
    val isSpeaking by voiceManager.tts.isSpeaking.collectAsState()
    val lastResponse by viewModel.lastResponse.collectAsState()

    // Initialize voice when in voice mode
    LaunchedEffect(isVoiceMode) {
        if (isVoiceMode) {
            viewModel.initializeVoice()
        }
    }

    // Auto-read new responses in voice mode
    LaunchedEffect(lastResponse, isVoiceMode) {
        if (isVoiceMode && lastResponse != null && !isProcessing) {
            viewModel.speakLastResponse()
        }
    }

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Top bar
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = "RemindME",
                        style = MaterialTheme.typography.titleLarge,
                        color = PrimaryCyan,
                        fontWeight = FontWeight.Bold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isVoiceMode) "Voice Mode" else "Text Mode",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        if (isSpeaking) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                Icons.Default.VolumeUp,
                                contentDescription = "Speaking",
                                tint = PrimaryCyan,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            },
            actions = {
                // Stop TTS button (visible when speaking)
                if (isSpeaking) {
                    IconButton(onClick = { viewModel.stopSpeaking() }) {
                        Icon(
                            imageVector = Icons.Default.StopCircle,
                            contentDescription = "Stop speaking",
                            tint = StatusRed
                        )
                    }
                }
                IconButton(onClick = onToggleMode) {
                    Icon(
                        imageVector = if (isVoiceMode) Icons.Default.Keyboard else Icons.Default.Mic,
                        contentDescription = "Switch mode",
                        tint = PrimaryCyan
                    )
                }
                IconButton(onClick = { viewModel.clearHistory() }) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "Clear history",
                        tint = TextSecondary
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = DarkSurface
            )
        )

        // Messages list
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            items(messages, key = { it.id }) { message ->
                ChatBubble(
                    message = message,
                    isVoiceMode = isVoiceMode,
                    onSpeakMessage = { text ->
                        viewModel.voiceManager.tts.speak(text)
                    }
                )
            }

            if (isProcessing) {
                item {
                    TypingIndicator()
                }
            }
        }

        // Voice listening overlay
        AnimatedVisibility(
            visible = listeningState == ListeningState.LISTENING || listeningState == ListeningState.PROCESSING,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it }
        ) {
            VoiceListeningOverlay(
                listeningState = listeningState,
                partialResult = partialResult,
                rmsLevel = rmsLevel,
                onCancel = { viewModel.cancelVoiceInput() }
            )
        }

        // Error display
        errorMessage?.let { error ->
            Surface(
                color = StatusRed.copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusRed,
                    modifier = Modifier.padding(8.dp),
                    textAlign = TextAlign.Center
                )
            }
        }

        // Input area
        Surface(
            color = DarkSurface,
            tonalElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Text input field
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            text = if (isVoiceMode) "Or type here..." else "Type your message...",
                            color = TextTertiary
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryCyan,
                        unfocusedBorderColor = DarkSurfaceVariant,
                        focusedContainerColor = DarkSurfaceVariant,
                        unfocusedContainerColor = DarkSurfaceVariant,
                        cursorColor = PrimaryCyan,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (inputText.isNotBlank()) {
                                viewModel.processUserInput(inputText.trim())
                                inputText = ""
                                focusManager.clearFocus()
                            }
                        }
                    )
                )

                if (isVoiceMode) {
                    // Animated mic button for voice mode
                    VoiceMicButton(
                        isListening = listeningState == ListeningState.LISTENING,
                        rmsLevel = rmsLevel,
                        onClick = {
                            if (listeningState == ListeningState.LISTENING) {
                                viewModel.stopVoiceInput()
                            } else {
                                viewModel.startVoiceInput()
                            }
                        }
                    )
                }

                // Send button
                FloatingActionButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            viewModel.processUserInput(inputText.trim())
                            inputText = ""
                            focusManager.clearFocus()
                        }
                    },
                    containerColor = if (inputText.isNotBlank()) PrimaryCyan else DarkSurfaceVariant,
                    contentColor = if (inputText.isNotBlank()) DarkBackground else TextTertiary,
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send")
                }
            }
        }
    }
}

@Composable
fun VoiceMicButton(
    isListening: Boolean,
    rmsLevel: Float,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse")

    // Pulsing animation when listening
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // RMS-based dynamic scale
    val dynamicScale = if (isListening) {
        1f + (rmsLevel * 0.2f)
    } else {
        1f
    }

    val actualScale = if (isListening) pulseScale * dynamicScale else 1f
    val borderColor = if (isListening) PrimaryCyan else PrimaryCyan.copy(alpha = 0.3f)

    Box(
        modifier = Modifier
            .size(52.dp)
            .scale(actualScale)
            .clip(CircleShape)
            .background(if (isListening) PrimaryCyan else PrimaryCyan.copy(alpha = 0.15f))
            .border(2.dp, borderColor, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
            contentDescription = if (isListening) "Stop listening" else "Start voice input",
            tint = if (isListening) DarkBackground else PrimaryCyan,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun VoiceListeningOverlay(
    listeningState: ListeningState,
    partialResult: String,
    rmsLevel: Float,
    onCancel: () -> Unit
) {
    Surface(
        color = DarkSurfaceElevated,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Status indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // Animated sound bars
                SoundWaveIndicator(rmsLevel = rmsLevel, isActive = listeningState == ListeningState.LISTENING)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = when (listeningState) {
                        ListeningState.LISTENING -> "Listening..."
                        ListeningState.PROCESSING -> "Processing..."
                        else -> ""
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = PrimaryCyan,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Partial result display
            if (partialResult.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = partialResult,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Cancel button
            TextButton(onClick = onCancel) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Cancel",
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Cancel", color = TextSecondary, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
fun SoundWaveIndicator(rmsLevel: Float, isActive: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val barCount = 5

    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(24.dp)
    ) {
        repeat(barCount) { index ->
            val delay = index * 80
            val animatedHeight by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(400 + delay, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar_$index"
            )

            val height = if (isActive) {
                (animatedHeight * (0.4f + rmsLevel * 0.6f) * 24).dp
            } else {
                4.dp
            }

            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(height.coerceIn(4.dp, 24.dp))
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(PrimaryCyan)
            )
        }
    }
}

@Composable
fun ChatBubble(
    message: ChatMessage,
    isVoiceMode: Boolean = false,
    onSpeakMessage: (String) -> Unit = {}
) {
    val isUser = message.isFromUser
    val dateFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Card(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) UserBubble else AssistantBubble
            ),
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = dateFormat.format(Date(message.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary
                    )
                    // Speaker icon for TTS on assistant messages
                    if (!isUser && isVoiceMode) {
                        Icon(
                            Icons.Default.VolumeUp,
                            contentDescription = "Read aloud",
                            tint = TextTertiary,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { onSpeakMessage(message.text) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TypingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = AssistantBubble)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(3) { index ->
                    val delay = index * 200
                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(600, delayMillis = delay),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "dot_$index"
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(PrimaryCyan.copy(alpha = alpha))
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Thinking...",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }
}
