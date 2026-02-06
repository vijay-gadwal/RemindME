package com.remindme.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.remindme.app.data.entity.*
import com.remindme.app.ui.theme.*
import com.remindme.app.viewmodel.GoalViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalDetailScreen(
    goalId: Long,
    viewModel: GoalViewModel,
    onNavigateBack: () -> Unit
) {
    val goalWithMilestones by viewModel.selectedGoal.collectAsState()
    var showAddMilestone by remember { mutableStateOf(false) }
    var newMilestoneTitle by remember { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(goalId) {
        viewModel.loadGoalDetail(goalId)
    }

    val gwm = goalWithMilestones
    if (gwm == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = PrimaryCyan)
        }
        return
    }

    val goal = gwm.goal
    val milestones = gwm.milestones
    val categoryColor = getCategoryColor(goal.category)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        TopAppBar(
            title = {
                Text(
                    text = "Goal Details",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }
            },
            actions = {
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = StatusRed)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Goal header card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Category & Status
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(categoryColor)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = goal.category.name.lowercase().replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = categoryColor
                                )
                            }

                            // Status dropdown
                            var statusExpanded by remember { mutableStateOf(false) }
                            Box {
                                val statusColor = when (goal.status) {
                                    GoalStatus.IN_PROGRESS -> StatusGreen
                                    GoalStatus.NOT_STARTED -> StatusOrange
                                    GoalStatus.ON_HOLD -> StatusPurple
                                    GoalStatus.COMPLETED -> StatusBlue
                                    GoalStatus.ABANDONED -> StatusRed
                                }
                                SuggestionChip(
                                    onClick = { statusExpanded = true },
                                    label = {
                                        Text(
                                            goal.status.name.replace("_", " "),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = statusColor.copy(alpha = 0.15f),
                                        labelColor = statusColor
                                    )
                                )
                                DropdownMenu(
                                    expanded = statusExpanded,
                                    onDismissRequest = { statusExpanded = false }
                                ) {
                                    GoalStatus.values().forEach { status ->
                                        DropdownMenuItem(
                                            text = { Text(status.name.replace("_", " ")) },
                                            onClick = {
                                                viewModel.updateGoalStatus(goalId, status)
                                                statusExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = goal.title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )

                        if (goal.description != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = goal.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Progress
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Progress", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                            Text(
                                "${goal.progress.toInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = PrimaryCyan,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = goal.progress / 100f,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = categoryColor,
                            trackColor = DarkSurface,
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Stats grid
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            StatItem(label = "Streak", value = "${goal.currentStreak}", icon = "\uD83D\uDD25")
                            StatItem(label = "Best", value = "${goal.bestStreak}", icon = "\uD83C\uDFC6")
                            StatItem(
                                label = "Check-in",
                                value = goal.checkInFrequency.name.lowercase().replaceFirstChar { it.uppercase() },
                                icon = "\uD83D\uDCC5"
                            )
                            if (goal.targetDate != null) {
                                val daysLeft = ((goal.targetDate - System.currentTimeMillis()) / (24 * 60 * 60 * 1000)).toInt()
                                StatItem(
                                    label = "Days Left",
                                    value = if (daysLeft > 0) "$daysLeft" else "Due!",
                                    icon = "\u23F0"
                                )
                            }
                        }

                        // Check-in button
                        if (goal.status == GoalStatus.IN_PROGRESS) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.checkInGoal(goalId) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PrimaryCyan,
                                    contentColor = DarkBackground
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Check In Today", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // Milestones section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Milestones",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    IconButton(onClick = { showAddMilestone = !showAddMilestone }) {
                        Icon(
                            if (showAddMilestone) Icons.Default.Close else Icons.Default.Add,
                            contentDescription = "Add milestone",
                            tint = PrimaryCyan
                        )
                    }
                }
            }

            // Add milestone input
            if (showAddMilestone) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newMilestoneTitle,
                            onValueChange = { newMilestoneTitle = it },
                            placeholder = { Text("Milestone title") },
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryCyan,
                                unfocusedBorderColor = DarkSurfaceVariant,
                                focusedContainerColor = DarkSurfaceVariant,
                                unfocusedContainerColor = DarkSurfaceVariant,
                                cursorColor = PrimaryCyan,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                        IconButton(
                            onClick = {
                                if (newMilestoneTitle.isNotBlank()) {
                                    viewModel.addMilestone(goalId, newMilestoneTitle.trim())
                                    newMilestoneTitle = ""
                                    showAddMilestone = false
                                }
                            }
                        ) {
                            Icon(Icons.Default.Check, contentDescription = "Save", tint = StatusGreen)
                        }
                    }
                }
            }

            // Milestone list
            if (milestones.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.ListAlt,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = TextTertiary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "No milestones yet",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                            Text(
                                "Break down your goal into achievable steps",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextTertiary
                            )
                        }
                    }
                }
            } else {
                items(milestones, key = { it.id }) { milestone ->
                    MilestoneItem(
                        milestone = milestone,
                        categoryColor = categoryColor,
                        onComplete = { viewModel.completeMilestone(milestone.id, goalId) },
                        onDelete = { viewModel.deleteMilestone(milestone) }
                    )
                }
            }

            // Notes section
            if (goal.notes != null) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Notes",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant)
                    ) {
                        Text(
                            text = goal.notes,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    // Delete confirmation dialog
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Goal?") },
            text = { Text("This will permanently delete this goal and all its milestones.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteGoal(goal)
                        showDeleteConfirm = false
                        onNavigateBack()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = StatusRed)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            },
            containerColor = DarkSurfaceVariant
        )
    }
}

@Composable
fun StatItem(label: String, value: String, icon: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = icon, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary
        )
    }
}

@Composable
fun MilestoneItem(
    milestone: Milestone,
    categoryColor: androidx.compose.ui.graphics.Color,
    onComplete: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (milestone.isCompleted) DarkSurface else DarkSurfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Completion checkbox
            IconButton(
                onClick = { if (!milestone.isCompleted) onComplete() },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    if (milestone.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = if (milestone.isCompleted) "Completed" else "Mark complete",
                    tint = if (milestone.isCompleted) categoryColor else TextTertiary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = milestone.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (milestone.isCompleted) TextTertiary else TextPrimary,
                    textDecoration = if (milestone.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (milestone.description != null) {
                    Text(
                        text = milestone.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Remove",
                    tint = TextTertiary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
