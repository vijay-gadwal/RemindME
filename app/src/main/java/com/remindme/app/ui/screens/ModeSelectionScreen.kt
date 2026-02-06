package com.remindme.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.remindme.app.ui.theme.*

@Composable
fun ModeSelectionScreen(
    onSelectVoiceMode: () -> Unit,
    onSelectTextMode: () -> Unit,
    rememberPreference: Boolean,
    onRememberPreferenceChanged: (Boolean) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            // App title
            Text(
                text = "RemindME",
                style = MaterialTheme.typography.displayLarge,
                color = PrimaryCyan,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Your personal context-aware assistant",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            Text(
                text = "How would you like to interact?",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Mode selection cards
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Voice Mode Card
                ModeCard(
                    modifier = Modifier.weight(1f),
                    icon = { Icon(Icons.Default.Mic, contentDescription = "Voice", modifier = Modifier.size(48.dp), tint = PrimaryCyan) },
                    title = "Voice Mode",
                    description = "Tap & speak your reminders",
                    onClick = onSelectVoiceMode
                )

                // Text Mode Card
                ModeCard(
                    modifier = Modifier.weight(1f),
                    icon = { Icon(Icons.Default.Chat, contentDescription = "Text", modifier = Modifier.size(48.dp), tint = PrimaryCyan) },
                    title = "Text Mode",
                    description = "Type your reminders",
                    onClick = onSelectTextMode
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Remember preference checkbox
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable {
                    onRememberPreferenceChanged(!rememberPreference)
                }
            ) {
                Checkbox(
                    checked = rememberPreference,
                    onCheckedChange = onRememberPreferenceChanged,
                    colors = CheckboxDefaults.colors(
                        checkedColor = PrimaryCyan,
                        uncheckedColor = TextSecondary
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Remember my preference",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "You can switch modes anytime from the chat screen",
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun ModeCard(
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = DarkSurfaceVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            icon()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                fontSize = 11.sp
            )
        }
    }
}
