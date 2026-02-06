package com.remindme.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.remindme.app.data.entity.*
import com.remindme.app.engine.AutoTagger
import com.remindme.app.ui.theme.*
import com.remindme.app.viewmodel.TaskViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskScreen(
    viewModel: TaskViewModel,
    onNavigateBack: () -> Unit
) {
    var description by remember { mutableStateOf("") }
    var selectedTrigger by remember { mutableStateOf(TriggerType.CONTEXT) }
    var selectedPriority by remember { mutableStateOf(Priority.MEDIUM) }
    var locationName by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var tagInput by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf(listOf<String>()) }
    var showAutoTags by remember { mutableStateOf(false) }
    var autoTags by remember { mutableStateOf(listOf<String>()) }

    // Auto-tag as user types
    LaunchedEffect(description) {
        if (description.length > 5) {
            val parsed = AutoTagger.parseInput(description)
            autoTags = parsed.tags.map { it.first }
            if (parsed.locationName != null && locationName.isEmpty()) {
                locationName = parsed.locationName
            }
            if (parsed.triggerType != TriggerType.CONTEXT) {
                selectedTrigger = parsed.triggerType
            }
            showAutoTags = autoTags.isNotEmpty()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        TopAppBar(
            title = {
                Text(
                    "Add Task",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Description
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("What do you need to remember?") },
                placeholder = { Text("e.g., Buy basil leaves at the supermarket") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryCyan,
                    unfocusedBorderColor = DarkSurfaceVariant,
                    focusedContainerColor = DarkSurfaceVariant,
                    unfocusedContainerColor = DarkSurfaceVariant,
                    cursorColor = PrimaryCyan,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedLabelColor = PrimaryCyan,
                    unfocusedLabelColor = TextSecondary
                ),
                shape = RoundedCornerShape(12.dp)
            )

            // Auto-detected tags
            if (showAutoTags && autoTags.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = PrimaryCyan.copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "Auto-detected tags:",
                            style = MaterialTheme.typography.labelMedium,
                            color = PrimaryCyan
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            autoTags.forEach { tag ->
                                SuggestionChip(
                                    onClick = {
                                        if (tag !in tags) tags = tags + tag
                                    },
                                    label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = PrimaryCyan.copy(alpha = 0.2f),
                                        labelColor = PrimaryCyan
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Trigger Type
            Text("Trigger Type", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TriggerType.values().forEach { trigger ->
                    FilterChip(
                        selected = selectedTrigger == trigger,
                        onClick = { selectedTrigger = trigger },
                        label = {
                            Text(
                                trigger.name.lowercase().replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        leadingIcon = {
                            Icon(
                                when (trigger) {
                                    TriggerType.TIME -> Icons.Default.Schedule
                                    TriggerType.LOCATION -> Icons.Default.LocationOn
                                    TriggerType.EVENT -> Icons.Default.Event
                                    TriggerType.CONTEXT -> Icons.Default.Psychology
                                },
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PrimaryCyan.copy(alpha = 0.2f),
                            selectedLabelColor = PrimaryCyan,
                            selectedLeadingIconColor = PrimaryCyan
                        )
                    )
                }
            }

            // Location (if location trigger)
            if (selectedTrigger == TriggerType.LOCATION) {
                OutlinedTextField(
                    value = locationName,
                    onValueChange = { locationName = it },
                    label = { Text("Location") },
                    placeholder = { Text("e.g., supermarket, bangalore") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = PrimaryCyan) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryCyan,
                        unfocusedBorderColor = DarkSurfaceVariant,
                        focusedContainerColor = DarkSurfaceVariant,
                        unfocusedContainerColor = DarkSurfaceVariant,
                        cursorColor = PrimaryCyan,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedLabelColor = PrimaryCyan,
                        unfocusedLabelColor = TextSecondary
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // Priority
            Text("Priority", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Priority.values().forEach { priority ->
                    val color = when (priority) {
                        Priority.URGENT -> PriorityUrgent
                        Priority.HIGH -> PriorityHigh
                        Priority.MEDIUM -> PriorityMedium
                        Priority.LOW -> PriorityLow
                    }
                    FilterChip(
                        selected = selectedPriority == priority,
                        onClick = { selectedPriority = priority },
                        label = {
                            Text(
                                priority.name.lowercase().replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = color.copy(alpha = 0.2f),
                            selectedLabelColor = color
                        )
                    )
                }
            }

            // Custom Tags
            OutlinedTextField(
                value = tagInput,
                onValueChange = { tagInput = it },
                label = { Text("Add tags") },
                placeholder = { Text("Type and press Enter") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    if (tagInput.isNotBlank()) {
                        IconButton(onClick = {
                            if (tagInput.isNotBlank() && tagInput !in tags) {
                                tags = tags + tagInput.trim()
                                tagInput = ""
                            }
                        }) {
                            Icon(Icons.Default.Add, contentDescription = "Add tag", tint = PrimaryCyan)
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryCyan,
                    unfocusedBorderColor = DarkSurfaceVariant,
                    focusedContainerColor = DarkSurfaceVariant,
                    unfocusedContainerColor = DarkSurfaceVariant,
                    cursorColor = PrimaryCyan,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedLabelColor = PrimaryCyan,
                    unfocusedLabelColor = TextSecondary
                ),
                shape = RoundedCornerShape(12.dp)
            )

            if (tags.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    tags.forEach { tag ->
                        InputChip(
                            selected = true,
                            onClick = { tags = tags - tag },
                            label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
                            trailingIcon = {
                                Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(14.dp))
                            },
                            colors = InputChipDefaults.inputChipColors(
                                selectedContainerColor = PrimaryCyan.copy(alpha = 0.2f),
                                selectedLabelColor = PrimaryCyan
                            )
                        )
                    }
                }
            }

            // Notes
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes (optional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryCyan,
                    unfocusedBorderColor = DarkSurfaceVariant,
                    focusedContainerColor = DarkSurfaceVariant,
                    unfocusedContainerColor = DarkSurfaceVariant,
                    cursorColor = PrimaryCyan,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedLabelColor = PrimaryCyan,
                    unfocusedLabelColor = TextSecondary
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Save button
            Button(
                onClick = {
                    if (description.isNotBlank()) {
                        val allTags = (tags + autoTags).distinct()
                        viewModel.addTask(
                            Task(
                                description = description.trim(),
                                triggerType = selectedTrigger,
                                priority = selectedPriority,
                                locationName = locationName.ifBlank { null },
                                notes = notes.ifBlank { null }
                            ),
                            allTags
                        )
                        onNavigateBack()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = description.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryCyan,
                    contentColor = DarkBackground,
                    disabledContainerColor = DarkSurfaceVariant,
                    disabledContentColor = TextTertiary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save Task", fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
