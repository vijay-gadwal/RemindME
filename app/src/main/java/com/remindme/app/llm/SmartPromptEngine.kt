package com.remindme.app.llm

import com.remindme.app.data.entity.*
import java.text.SimpleDateFormat
import java.util.*

object SmartPromptEngine {

    private const val SYSTEM_CONTEXT = """You are RemindME, a helpful personal reminder and goal tracking assistant. 
You help users manage tasks, track goals, and stay organized. 
Be concise, friendly, and actionable in your responses. 
Always respond in 2-3 sentences unless more detail is needed."""

    fun buildIntentParsingPrompt(userInput: String): String {
        return """$SYSTEM_CONTEXT

Classify the following user message into exactly one intent category.
Categories: ADD_TASK, ADD_GOAL, COMPLETE_TASK, CHECK_IN_GOAL, GET_SUMMARY, GOING_SOMEWHERE, STATUS_UPDATE, SNOOZE_TASK, LIST_TASKS, LIST_GOALS, GREETING, UNKNOWN

Also extract:
- description: the main task/goal description
- location: any mentioned location (or null)
- date: any mentioned date/time (or null)
- priority: urgent/high/medium/low (or null)
- category: fitness/travel/financial/learning/career/personal/health (or null)

User message: "$userInput"

Respond in this exact format:
INTENT: <intent>
DESCRIPTION: <description>
LOCATION: <location or null>
DATE: <date or null>
PRIORITY: <priority or null>
CATEGORY: <category or null>"""
    }

    fun buildTaskSummaryPrompt(tasks: List<Task>): String {
        val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())
        val taskList = tasks.take(15).joinToString("\n") { task ->
            val priority = when (task.priority) {
                Priority.URGENT -> "[URGENT]"
                Priority.HIGH -> "[HIGH]"
                Priority.MEDIUM -> "[MED]"
                Priority.LOW -> "[LOW]"
            }
            val location = task.locationName?.let { " @ $it" } ?: ""
            val due = task.dueDate?.let { " due ${dateFormat.format(Date(it))}" } ?: ""
            "- $priority ${task.description}$location$due"
        }

        return """$SYSTEM_CONTEXT

Summarize the following task list into a brief, organized daily briefing. 
Group by priority and mention any urgent items first. Be concise.

Tasks (${ tasks.size } total):
$taskList

Provide a 3-4 sentence summary highlighting what needs attention today."""
    }

    fun buildGoalProgressPrompt(goal: Goal, milestones: List<Milestone>): String {
        val completedMilestones = milestones.filter { it.isCompleted }
        val pendingMilestones = milestones.filter { !it.isCompleted }

        val milestoneText = buildString {
            if (completedMilestones.isNotEmpty()) {
                append("Completed: ${completedMilestones.joinToString(", ") { it.title }}\n")
            }
            if (pendingMilestones.isNotEmpty()) {
                append("Pending: ${pendingMilestones.joinToString(", ") { it.title }}")
            }
        }

        val daysInfo = if (goal.targetDate != null) {
            val daysLeft = ((goal.targetDate - System.currentTimeMillis()) / (24 * 60 * 60 * 1000)).toInt()
            if (daysLeft > 0) "$daysLeft days remaining" else "Past target date"
        } else "No target date"

        return """$SYSTEM_CONTEXT

Provide a brief motivational progress update for this goal:

Goal: ${goal.title}
Category: ${goal.category.name}
Status: ${goal.status.name}
Progress: ${goal.progress.toInt()}%
Streak: ${goal.currentStreak} days (best: ${goal.bestStreak})
Timeline: $daysInfo
Milestones:
$milestoneText

Give an encouraging 2-3 sentence update with a specific next-step suggestion."""
    }

    fun buildGoalSubTaskPrompt(goal: Goal): String {
        return """$SYSTEM_CONTEXT

Break down the following goal into 5-7 actionable milestones/steps.
Each step should be specific, measurable, and achievable.

Goal: ${goal.title}
Category: ${goal.category.name}
${goal.description?.let { "Description: $it" } ?: ""}

List the milestones in order, one per line, starting with the easiest/first step.
Format: just the milestone title, nothing else."""
    }

    fun buildSmartSnoozePrompt(task: Task, currentTime: Long): String {
        return """$SYSTEM_CONTEXT

Suggest the best snooze time for this reminder based on context:

Task: ${task.description}
Current time: ${SimpleDateFormat("h:mm a, EEEE", Locale.getDefault()).format(Date(currentTime))}
Priority: ${task.priority.name}
${task.locationName?.let { "Location: $it" } ?: ""}
${task.category?.let { "Category: $it" } ?: ""}

Suggest ONE specific snooze time (e.g., "tomorrow at 9 AM", "in 2 hours", "Monday morning").
Explain why in one sentence."""
    }

    fun buildReflectionPrompt(
        goal: Goal,
        recentCheckIns: Int,
        daysSinceStart: Long
    ): String {
        return """$SYSTEM_CONTEXT

Generate a brief reflection prompt for the user about their goal progress:

Goal: ${goal.title}
Days active: $daysSinceStart
Check-ins this week: $recentCheckIns
Current streak: ${goal.currentStreak}
Progress: ${goal.progress.toInt()}%

Ask ONE thoughtful reflection question that helps the user think about their progress and next steps. 
Keep it encouraging and specific to their goal."""
    }

    fun buildContextualResponsePrompt(
        userInput: String,
        activeTasks: List<Task>,
        activeGoals: List<Goal>,
        currentLocation: String? = null,
        recentHistory: List<Pair<String, Boolean>> = emptyList() // (message, isFromUser)
    ): String {
        val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())
        val taskContext = if (activeTasks.isNotEmpty()) {
            val taskLines = activeTasks.take(10).joinToString("\n") { task ->
                val priority = "[${task.priority.name}]"
                val loc = task.locationName?.let { " @ $it" } ?: ""
                val due = task.dueDate?.let { " due ${dateFormat.format(Date(it))}" } ?: ""
                val cat = task.category?.let { " ($it)" } ?: ""
                "- $priority ${task.description}$cat$loc$due"
            }
            "Active tasks (${activeTasks.size} total):\n$taskLines"
        } else "No active tasks."

        val goalContext = if (activeGoals.isNotEmpty()) {
            val goalLines = activeGoals.take(5).joinToString("\n") { goal ->
                val streak = if (goal.currentStreak > 0) " streak:${goal.currentStreak}d" else ""
                "- ${goal.title} [${goal.category.name}] ${goal.progress.toInt()}%$streak ${goal.status.name}"
            }
            "Active goals (${activeGoals.size} total):\n$goalLines"
        } else "No active goals."

        val locationContext = currentLocation?.let { "User is currently in: $it" } ?: ""
        val timeContext = "Current time: ${SimpleDateFormat("h:mm a, EEEE MMM d", Locale.getDefault()).format(Date())}"

        val historyContext = if (recentHistory.isNotEmpty()) {
            val historyLines = recentHistory.joinToString("\n") { (msg, isUser) ->
                val role = if (isUser) "User" else "Assistant"
                "$role: ${msg.take(200)}"
            }
            "Recent conversation:\n$historyLines"
        } else ""

        return """$SYSTEM_CONTEXT

User's current context:
$timeContext
$locationContext
$taskContext
$goalContext
$historyContext

User says: "$userInput"

Respond helpfully. Reference specific tasks or goals when relevant. If the user asks a general question, answer it using their task/goal context. Consider the recent conversation for continuity. Be conversational and concise (2-4 sentences)."""
    }

    fun buildActionEnhancementPrompt(
        userInput: String,
        actionResult: String,
        activeTasks: List<Task>,
        activeGoals: List<Goal>
    ): String {
        val taskSummary = if (activeTasks.isNotEmpty()) {
            "Other active tasks: ${activeTasks.take(5).joinToString(", ") { it.description }}"
        } else ""

        val goalSummary = if (activeGoals.isNotEmpty()) {
            "Active goals: ${activeGoals.take(3).joinToString(", ") { it.title }}"
        } else ""

        return """$SYSTEM_CONTEXT

The user said: "$userInput"
I performed this action: $actionResult
$taskSummary
$goalSummary

Add ONE brief helpful tip or observation (1 sentence max) related to this action. For example, relate it to their other tasks/goals, suggest a follow-up, or note timing. Be specific, not generic. If there's nothing useful to add, respond with just "ok"."""
    }

    fun buildDailyMotivationPrompt(
        activeGoalCount: Int,
        activeTaskCount: Int,
        longestStreak: Int,
        topGoal: Goal?
    ): String {
        return """$SYSTEM_CONTEXT

Generate a brief morning motivation message for a user with:
- $activeTaskCount pending tasks
- $activeGoalCount active goals
- Longest current streak: $longestStreak days
${topGoal?.let { "- Top goal: ${it.title} at ${it.progress.toInt()}%" } ?: ""}

Keep it to 1-2 sentences. Be specific and encouraging, not generic."""
    }

    fun buildChatResponsePrompt(
        userInput: String,
        activeTasks: List<Task>,
        activeGoals: List<Goal>
    ): String {
        return buildContextualResponsePrompt(userInput, activeTasks, activeGoals)
    }

    fun parseIntentResponse(response: String): ParsedIntent {
        val lines = response.lines().associate { line ->
            val parts = line.split(":", limit = 2)
            if (parts.size == 2) parts[0].trim().uppercase() to parts[1].trim()
            else "" to ""
        }

        return ParsedIntent(
            intent = lines["INTENT"] ?: "UNKNOWN",
            description = lines["DESCRIPTION"] ?: "",
            location = lines["LOCATION"]?.takeIf { it != "null" && it.isNotBlank() },
            date = lines["DATE"]?.takeIf { it != "null" && it.isNotBlank() },
            priority = lines["PRIORITY"]?.takeIf { it != "null" && it.isNotBlank() },
            category = lines["CATEGORY"]?.takeIf { it != "null" && it.isNotBlank() }
        )
    }
}

data class ParsedIntent(
    val intent: String,
    val description: String,
    val location: String? = null,
    val date: String? = null,
    val priority: String? = null,
    val category: String? = null
)
