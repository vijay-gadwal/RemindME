package com.remindme.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.remindme.app.data.entity.*
import com.remindme.app.ui.theme.*
import com.remindme.app.viewmodel.GoalViewModel
import com.remindme.app.viewmodel.GoalWithMilestones

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalListScreen(
    viewModel: GoalViewModel,
    onNavigateToGoalDetail: (Long) -> Unit,
    onNavigateToAddGoal: () -> Unit
) {
    val goalsWithMilestones by viewModel.goalsWithMilestones.collectAsState()
    val activeGoalCount by viewModel.activeGoalCount.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()

    val filteredGoals = if (selectedCategory != null) {
        goalsWithMilestones.filter { it.goal.category == selectedCategory }
    } else {
        goalsWithMilestones
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = "Goals",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$activeGoalCount active goals",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            },
            actions = {
                IconButton(onClick = onNavigateToAddGoal) {
                    Icon(Icons.Default.Add, contentDescription = "Add goal", tint = PrimaryCyan)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
        )

        // Category filter chips
        LazyRow(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { viewModel.setSelectedCategory(null) },
                    label = { Text("All") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PrimaryCyan.copy(alpha = 0.2f),
                        selectedLabelColor = PrimaryCyan
                    )
                )
            }
            items(GoalCategory.values().toList()) { category ->
                val color = getCategoryColor(category)
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = { viewModel.setSelectedCategory(category) },
                    label = {
                        Text(
                            category.name.lowercase().replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = color.copy(alpha = 0.2f),
                        selectedLabelColor = color
                    )
                )
            }
        }

        if (filteredGoals.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Flag,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = TextTertiary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No goals yet", style = MaterialTheme.typography.titleMedium, color = TextSecondary)
                    Text(
                        "Set long-term goals to track your progress",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredGoals, key = { it.goal.id }) { goalWithMilestones ->
                    GoalCard(
                        goalWithMilestones = goalWithMilestones,
                        onClick = { onNavigateToGoalDetail(goalWithMilestones.goal.id) },
                        onCheckIn = { viewModel.checkInGoal(goalWithMilestones.goal.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun GoalCard(
    goalWithMilestones: GoalWithMilestones,
    onClick: () -> Unit,
    onCheckIn: () -> Unit
) {
    val goal = goalWithMilestones.goal
    val milestones = goalWithMilestones.milestones
    val nextMilestone = goalWithMilestones.nextMilestone
    val categoryColor = getCategoryColor(goal.category)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
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

                // Status badge
                val statusColor = when (goal.status) {
                    GoalStatus.IN_PROGRESS -> StatusGreen
                    GoalStatus.NOT_STARTED -> StatusOrange
                    GoalStatus.ON_HOLD -> StatusPurple
                    GoalStatus.COMPLETED -> StatusBlue
                    GoalStatus.ABANDONED -> StatusRed
                }
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = statusColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = goal.status.name.replace("_", " "),
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Title
            Text(
                text = goal.title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (goal.description != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = goal.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Progress bar
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Progress",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    Text(
                        text = "${goal.progress.toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = PrimaryCyan,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = goal.progress / 100f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = categoryColor,
                    trackColor = DarkSurface,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Streak
                if (goal.currentStreak > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "\uD83D\uDD25", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${goal.currentStreak} day streak",
                            style = MaterialTheme.typography.labelMedium,
                            color = StatusOrange
                        )
                    }
                }

                // Milestones count
                if (milestones.isNotEmpty()) {
                    val completed = milestones.count { it.isCompleted }
                    Text(
                        text = "$completed/${milestones.size} milestones",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                }

                // Target date
                if (goal.targetDate != null) {
                    val daysLeft = ((goal.targetDate - System.currentTimeMillis()) / (24 * 60 * 60 * 1000)).toInt()
                    Text(
                        text = if (daysLeft > 0) "${daysLeft}d left" else "Overdue",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (daysLeft > 7) TextSecondary else StatusRed
                    )
                }
            }

            // Next milestone
            if (nextMilestone != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = DarkSurface
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.NavigateNext,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = PrimaryCyan
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Next: ${nextMilestone.title}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Check-in button
            if (goal.status == GoalStatus.IN_PROGRESS) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onCheckIn,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryCyan),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Check In", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

fun getCategoryColor(category: GoalCategory): Color {
    return when (category) {
        GoalCategory.FITNESS -> CategoryFitness
        GoalCategory.TRAVEL -> CategoryTravel
        GoalCategory.FINANCIAL -> CategoryFinancial
        GoalCategory.LEARNING -> CategoryLearning
        GoalCategory.CAREER -> CategoryCareer
        GoalCategory.PERSONAL -> CategoryPersonal
        GoalCategory.HEALTH -> CategoryHealth
    }
}
