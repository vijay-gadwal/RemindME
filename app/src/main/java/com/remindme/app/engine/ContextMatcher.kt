package com.remindme.app.engine

import com.remindme.app.data.entity.Task
import com.remindme.app.data.entity.Goal
import com.remindme.app.data.entity.Tag
import com.remindme.app.data.entity.TaskStatus
import com.remindme.app.data.entity.GoalStatus

data class MatchResult(
    val matchedTasks: List<TaskMatch> = emptyList(),
    val matchedGoals: List<GoalMatch> = emptyList(),
    val summary: String = "",
    val intent: UserIntent = UserIntent.UNKNOWN
)

data class TaskMatch(
    val task: Task,
    val confidence: Float,
    val matchReason: String
)

data class GoalMatch(
    val goal: Goal,
    val confidence: Float,
    val matchReason: String
)

enum class UserIntent {
    ADD_TASK,
    ADD_GOAL,
    QUERY_TASKS,
    QUERY_GOALS,
    GOING_SOMEWHERE,
    STATUS_UPDATE,
    COMPLETE_TASK,
    COMPLETE_GOAL,
    SNOOZE_TASK,
    GET_SUMMARY,
    CHECK_IN_GOAL,
    UNKNOWN
}

object ContextMatcher {

    // Intent detection patterns
    private val intentPatterns = mapOf(
        UserIntent.ADD_TASK to listOf(
            "remind me", "add task", "add reminder", "create task", "remember to",
            "don't forget", "i need to", "i have to", "i should", "note that",
            "remind about"
        ),
        UserIntent.ADD_GOAL to listOf(
            "start a", "begin a", "my goal", "i want to achieve", "plan for",
            "set goal", "new goal", "i aim to", "target to", "resolution"
        ),
        UserIntent.GOING_SOMEWHERE to listOf(
            "i am going to", "i'm going to", "going to", "heading to",
            "traveling to", "travelling to", "visiting", "on my way to",
            "i am at", "i'm at", "i am in", "i'm in", "reached"
        ),
        UserIntent.STATUS_UPDATE to listOf(
            "i have faced", "there is an issue", "problem with", "issue with",
            "noticed that", "something wrong", "update about", "regarding my",
            "about my"
        ),
        UserIntent.COMPLETE_TASK to listOf(
            "done with", "completed", "finished", "i did", "mark as done",
            "task done", "got it done", "bought", "purchased"
        ),
        UserIntent.GET_SUMMARY to listOf(
            "summary", "summarize", "what do i", "what should i", "what's pending",
            "show me", "list my", "what are my", "any reminders", "anything i need",
            "what tasks", "what goals", "how am i doing", "progress"
        ),
        UserIntent.CHECK_IN_GOAL to listOf(
            "check in", "update progress", "i worked on", "i exercised",
            "i studied", "i practiced", "goal update", "progress update"
        )
    )

    fun detectIntent(input: String): UserIntent {
        val lower = input.lowercase().trim()
        var bestIntent = UserIntent.UNKNOWN
        var bestScore = 0

        for ((intent, patterns) in intentPatterns) {
            var score = 0
            for (pattern in patterns) {
                if (lower.contains(pattern)) {
                    score += pattern.split(" ").size // Longer matches score higher
                }
            }
            if (score > bestScore) {
                bestScore = score
                bestIntent = intent
            }
        }

        return bestIntent
    }

    fun matchTasksToContext(
        input: String,
        activeTasks: List<Task>,
        taskTags: Map<Long, List<Tag>>
    ): List<TaskMatch> {
        val lower = input.lowercase().trim()
        val inputWords = lower.split("\\s+".toRegex()).filter { it.length > 2 }
        val matches = mutableListOf<TaskMatch>()

        for (task in activeTasks) {
            if (task.status == TaskStatus.COMPLETED || task.status == TaskStatus.CANCELLED) continue

            var confidence = 0f
            val reasons = mutableListOf<String>()

            // Match against task description
            val descWords = task.description.lowercase().split("\\s+".toRegex())
            val descOverlap = inputWords.count { word -> descWords.any { it.contains(word) || word.contains(it) } }
            if (descOverlap > 0) {
                confidence += descOverlap * 0.2f
                reasons.add("description match")
            }

            // Match against task category
            if (task.category != null && lower.contains(task.category.lowercase())) {
                confidence += 0.3f
                reasons.add("category: ${task.category}")
            }

            // Match against task location
            if (task.locationName != null && lower.contains(task.locationName.lowercase())) {
                confidence += 0.5f
                reasons.add("location: ${task.locationName}")
            }

            // Match against task tags
            val tags = taskTags[task.id] ?: emptyList()
            for (tag in tags) {
                if (lower.contains(tag.name.lowercase())) {
                    confidence += 0.4f
                    reasons.add("tag: ${tag.name}")
                }
            }

            // Match against task notes
            if (task.notes != null) {
                val noteWords = task.notes.lowercase().split("\\s+".toRegex())
                val noteOverlap = inputWords.count { word -> noteWords.any { it.contains(word) } }
                if (noteOverlap > 0) {
                    confidence += noteOverlap * 0.1f
                    reasons.add("notes match")
                }
            }

            // Cap confidence at 1.0
            confidence = confidence.coerceAtMost(1.0f)

            if (confidence > 0.1f) {
                matches.add(TaskMatch(task, confidence, reasons.joinToString(", ")))
            }
        }

        return matches.sortedByDescending { it.confidence }
    }

    fun matchGoalsToContext(
        input: String,
        activeGoals: List<Goal>
    ): List<GoalMatch> {
        val lower = input.lowercase().trim()
        val inputWords = lower.split("\\s+".toRegex()).filter { it.length > 2 }
        val matches = mutableListOf<GoalMatch>()

        for (goal in activeGoals) {
            if (goal.status == GoalStatus.COMPLETED || goal.status == GoalStatus.ABANDONED) continue

            var confidence = 0f
            val reasons = mutableListOf<String>()

            // Match against goal title
            val titleWords = goal.title.lowercase().split("\\s+".toRegex())
            val titleOverlap = inputWords.count { word -> titleWords.any { it.contains(word) || word.contains(it) } }
            if (titleOverlap > 0) {
                confidence += titleOverlap * 0.3f
                reasons.add("title match")
            }

            // Match against goal description
            if (goal.description != null) {
                val descWords = goal.description.lowercase().split("\\s+".toRegex())
                val descOverlap = inputWords.count { word -> descWords.any { it.contains(word) } }
                if (descOverlap > 0) {
                    confidence += descOverlap * 0.15f
                    reasons.add("description match")
                }
            }

            // Match against goal category name
            if (lower.contains(goal.category.name.lowercase())) {
                confidence += 0.3f
                reasons.add("category: ${goal.category.name}")
            }

            confidence = confidence.coerceAtMost(1.0f)

            if (confidence > 0.1f) {
                matches.add(GoalMatch(goal, confidence, reasons.joinToString(", ")))
            }
        }

        return matches.sortedByDescending { it.confidence }
    }

    fun generateResponse(
        input: String,
        intent: UserIntent,
        taskMatches: List<TaskMatch>,
        goalMatches: List<GoalMatch>
    ): String {
        return when (intent) {
            UserIntent.GOING_SOMEWHERE -> {
                val location = extractLocation(input)
                if (taskMatches.isEmpty() && goalMatches.isEmpty()) {
                    "No pending tasks or goals related to ${location ?: "that location"}. Have a good trip!"
                } else {
                    buildString {
                        if (location != null) {
                            append("Here's what you need to do for $location:\n\n")
                        } else {
                            append("Here are related reminders:\n\n")
                        }
                        if (taskMatches.isNotEmpty()) {
                            append("📋 Tasks:\n")
                            taskMatches.forEachIndexed { i, match ->
                                append("${i + 1}. ${match.task.description}")
                                if (match.confidence >= 0.5f) append(" ⭐")
                                append("\n")
                            }
                        }
                        if (goalMatches.isNotEmpty()) {
                            append("\n🎯 Related Goals:\n")
                            goalMatches.forEachIndexed { i, match ->
                                append("${i + 1}. ${match.goal.title} (${match.goal.progress.toInt()}% done)\n")
                            }
                        }
                    }
                }
            }
            UserIntent.GET_SUMMARY -> {
                if (taskMatches.isEmpty() && goalMatches.isEmpty()) {
                    "You're all caught up! No pending tasks or active goals."
                } else {
                    buildString {
                        append("📊 Your Summary:\n\n")
                        if (taskMatches.isNotEmpty()) {
                            append("📋 ${taskMatches.size} relevant task(s):\n")
                            taskMatches.take(5).forEachIndexed { i, match ->
                                append("${i + 1}. ${match.task.description}\n")
                            }
                            if (taskMatches.size > 5) {
                                append("...and ${taskMatches.size - 5} more\n")
                            }
                        }
                        if (goalMatches.isNotEmpty()) {
                            append("\n🎯 ${goalMatches.size} active goal(s):\n")
                            goalMatches.take(5).forEachIndexed { i, match ->
                                append("${i + 1}. ${match.goal.title} - ${match.goal.progress.toInt()}% done\n")
                            }
                        }
                    }
                }
            }
            UserIntent.STATUS_UPDATE -> {
                if (taskMatches.isNotEmpty()) {
                    val task = taskMatches.first().task
                    "Got it! I've noted this update related to \"${task.description}\". This will be included in the summary when relevant."
                } else {
                    "Noted! I'll remember this information and bring it up when relevant."
                }
            }
            UserIntent.ADD_TASK -> "✅ Task added! I'll remind you at the right time."
            UserIntent.ADD_GOAL -> "🎯 Goal created! I'll help you track your progress."
            UserIntent.COMPLETE_TASK -> {
                if (taskMatches.isNotEmpty()) {
                    "Great job! Marked \"${taskMatches.first().task.description}\" as completed! 🎉"
                } else {
                    "Which task did you complete? Please be more specific."
                }
            }
            UserIntent.CHECK_IN_GOAL -> {
                if (goalMatches.isNotEmpty()) {
                    val goal = goalMatches.first().goal
                    "Awesome! Checked in for \"${goal.title}\". Streak: ${goal.currentStreak + 1} days! 🔥"
                } else {
                    "Which goal are you checking in for?"
                }
            }
            else -> {
                if (taskMatches.isNotEmpty() || goalMatches.isNotEmpty()) {
                    generateResponse(input, UserIntent.GET_SUMMARY, taskMatches, goalMatches)
                } else {
                    "I understand. How can I help you with your tasks or goals?"
                }
            }
        }
    }

    private fun extractLocation(input: String): String? {
        val patterns = listOf(
            "(?i)(?:going|heading|traveling|travelling|visiting)\\s+(?:to\\s+)?(.+?)(?:\\s+now|\\s+today|\\s+tomorrow|\\.|\$)",
            "(?i)(?:i am|i'm)\\s+(?:in|at)\\s+(.+?)(?:\\s+now|\\s+today|\\.|\$)"
        )
        for (pattern in patterns) {
            val matcher = java.util.regex.Pattern.compile(pattern).matcher(input)
            if (matcher.find()) {
                return matcher.group(1)?.trim()
            }
        }
        return null
    }
}
