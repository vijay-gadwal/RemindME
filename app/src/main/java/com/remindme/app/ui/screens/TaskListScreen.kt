package com.remindme.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import com.remindme.app.viewmodel.TaskViewModel
import com.remindme.app.viewmodel.TaskWithTags
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(
    viewModel: TaskViewModel,
    onNavigateToAddTask: () -> Unit
) {
    val tasksWithTags by viewModel.tasksWithTags.collectAsState()
    val allTasks by viewModel.allTasks.collectAsState()
    val activeCount by viewModel.activeTaskCount.collectAsState()
    val completedCount by viewModel.completedTaskCount.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()

    val filteredTasks = when (selectedFilter) {
        null -> tasksWithTags
        TaskStatus.COMPLETED -> allTasks
            .filter { it.status == TaskStatus.COMPLETED }
            .map { TaskWithTags(it) }
        else -> tasksWithTags.filter { it.task.status == selectedFilter }
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
                        text = "Tasks",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$activeCount active, $completedCount completed",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            },
            actions = {
                IconButton(onClick = onNavigateToAddTask) {
                    Icon(Icons.Default.Add, contentDescription = "Add task", tint = PrimaryCyan)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
        )

        // Filter chips
        LazyRow(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = selectedFilter == null,
                    onClick = { viewModel.setFilter(null) },
                    label = { Text("All Active") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PrimaryCyan.copy(alpha = 0.2f),
                        selectedLabelColor = PrimaryCyan
                    )
                )
            }
            item {
                FilterChip(
                    selected = selectedFilter == TaskStatus.PENDING,
                    onClick = { viewModel.setFilter(TaskStatus.PENDING) },
                    label = { Text("Pending") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = StatusOrange.copy(alpha = 0.2f),
                        selectedLabelColor = StatusOrange
                    )
                )
            }
            item {
                FilterChip(
                    selected = selectedFilter == TaskStatus.SNOOZED,
                    onClick = { viewModel.setFilter(TaskStatus.SNOOZED) },
                    label = { Text("Snoozed") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = StatusPurple.copy(alpha = 0.2f),
                        selectedLabelColor = StatusPurple
                    )
                )
            }
            item {
                FilterChip(
                    selected = selectedFilter == TaskStatus.COMPLETED,
                    onClick = { viewModel.setFilter(TaskStatus.COMPLETED) },
                    label = { Text("Completed") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = StatusGreen.copy(alpha = 0.2f),
                        selectedLabelColor = StatusGreen
                    )
                )
            }
        }

        // Task list
        if (filteredTasks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = TextTertiary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No tasks yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextSecondary
                    )
                    Text(
                        text = "Add tasks via chat or tap + above",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredTasks, key = { it.task.id }) { taskWithTags ->
                    TaskCard(
                        taskWithTags = taskWithTags,
                        onComplete = { viewModel.completeTask(taskWithTags.task.id) },
                        onDelete = { viewModel.deleteTask(taskWithTags.task) },
                        onReactivate = { viewModel.reactivateTask(taskWithTags.task.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun TaskCard(
    taskWithTags: TaskWithTags,
    onComplete: () -> Unit,
    onDelete: () -> Unit,
    onReactivate: () -> Unit
) {
    val task = taskWithTags.task
    val tags = taskWithTags.tags
    val isCompleted = task.status == TaskStatus.COMPLETED
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    val priorityColor = when (task.priority) {
        Priority.URGENT -> PriorityUrgent
        Priority.HIGH -> PriorityHigh
        Priority.MEDIUM -> PriorityMedium
        Priority.LOW -> PriorityLow
    }

    val triggerIcon = when (task.triggerType) {
        TriggerType.TIME -> Icons.Default.Schedule
        TriggerType.LOCATION -> Icons.Default.LocationOn
        TriggerType.EVENT -> Icons.Default.Event
        TriggerType.CONTEXT -> Icons.Default.Psychology
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Priority indicator
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(48.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(priorityColor)
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Task content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isCompleted) TextTertiary else TextPrimary,
                    fontWeight = FontWeight.Medium,
                    textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Trigger info
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        triggerIcon,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = TextTertiary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = when {
                            task.locationName != null -> task.locationName
                            task.dueDate != null -> dateFormat.format(Date(task.dueDate))
                            task.category != null -> task.category
                            else -> task.triggerType.name.lowercase()
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary
                    )
                }

                // Tags
                if (tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        tags.take(3).forEach { tag ->
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = PrimaryCyan.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = tag.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PrimaryCyan,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        if (tags.size > 3) {
                            Text(
                                text = "+${tags.size - 3}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextTertiary
                            )
                        }
                    }
                }
            }

            // Action buttons
            Column(horizontalAlignment = Alignment.End) {
                if (!isCompleted) {
                    IconButton(
                        onClick = onComplete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Complete",
                            tint = StatusGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else {
                    IconButton(
                        onClick = onReactivate,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Replay,
                            contentDescription = "Reactivate",
                            tint = StatusBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = StatusRed.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
