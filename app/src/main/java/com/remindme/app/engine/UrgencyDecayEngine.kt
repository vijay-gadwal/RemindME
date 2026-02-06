package com.remindme.app.engine

import com.remindme.app.data.entity.Priority
import com.remindme.app.data.entity.Task
import com.remindme.app.data.entity.TaskStatus
import java.util.concurrent.TimeUnit

object UrgencyDecayEngine {

    data class DecayResult(
        val taskId: Long,
        val originalPriority: Priority,
        val decayedPriority: Priority,
        val urgencyScore: Float,
        val reason: String
    )

    // Time thresholds for urgency escalation
    private val ESCALATION_THRESHOLDS = mapOf(
        Priority.LOW to TimeUnit.DAYS.toMillis(7),
        Priority.MEDIUM to TimeUnit.DAYS.toMillis(3),
        Priority.HIGH to TimeUnit.DAYS.toMillis(1),
        Priority.URGENT to 0L
    )

    fun calculateUrgencyScore(task: Task): Float {
        if (task.status != TaskStatus.PENDING && task.status != TaskStatus.SNOOZED) return 0f

        var score = when (task.priority) {
            Priority.URGENT -> 1.0f
            Priority.HIGH -> 0.75f
            Priority.MEDIUM -> 0.5f
            Priority.LOW -> 0.25f
        }

        // Factor 1: Time since creation (older tasks get more urgent)
        val ageDays = (System.currentTimeMillis() - task.createdAt).toFloat() / TimeUnit.DAYS.toMillis(1)
        val ageFactor = (ageDays / 14f).coerceIn(0f, 0.3f) // Max 0.3 boost over 14 days
        score += ageFactor

        // Factor 2: Due date proximity (closer = more urgent)
        if (task.dueDate != null) {
            val timeUntilDue = task.dueDate - System.currentTimeMillis()
            val hoursUntilDue = timeUntilDue.toFloat() / TimeUnit.HOURS.toMillis(1)

            score += when {
                hoursUntilDue <= 0 -> 0.4f   // Overdue
                hoursUntilDue <= 2 -> 0.35f   // Due within 2 hours
                hoursUntilDue <= 12 -> 0.25f  // Due today
                hoursUntilDue <= 24 -> 0.15f  // Due tomorrow
                hoursUntilDue <= 72 -> 0.05f  // Due within 3 days
                else -> 0f
            }
        }

        // Factor 3: Snooze count penalty (snoozed multiple times = user avoidance, reduce slightly)
        if (task.status == TaskStatus.SNOOZED) {
            score -= 0.05f
        }

        return score.coerceIn(0f, 1.0f)
    }

    fun calculateDecayedPriority(task: Task): DecayResult {
        val urgencyScore = calculateUrgencyScore(task)

        val decayedPriority = when {
            urgencyScore >= 0.85f -> Priority.URGENT
            urgencyScore >= 0.65f -> Priority.HIGH
            urgencyScore >= 0.40f -> Priority.MEDIUM
            else -> Priority.LOW
        }

        val reason = buildString {
            if (decayedPriority != task.priority) {
                if (decayedPriority.ordinal < task.priority.ordinal) {
                    append("Escalated: ")
                } else {
                    append("De-escalated: ")
                }
            }
            val ageDays = ((System.currentTimeMillis() - task.createdAt) / TimeUnit.DAYS.toMillis(1)).toInt()
            if (ageDays > 0) append("${ageDays}d old. ")
            if (task.dueDate != null) {
                val hoursLeft = ((task.dueDate - System.currentTimeMillis()) / TimeUnit.HOURS.toMillis(1)).toInt()
                when {
                    hoursLeft < 0 -> append("Overdue by ${-hoursLeft}h. ")
                    hoursLeft < 24 -> append("Due in ${hoursLeft}h. ")
                    else -> append("Due in ${hoursLeft / 24}d. ")
                }
            }
        }.trimEnd()

        return DecayResult(
            taskId = task.id,
            originalPriority = task.priority,
            decayedPriority = decayedPriority,
            urgencyScore = urgencyScore,
            reason = reason
        )
    }

    fun rankTasksByUrgency(tasks: List<Task>): List<Pair<Task, Float>> {
        return tasks
            .map { it to calculateUrgencyScore(it) }
            .sortedByDescending { it.second }
    }

    fun getOverdueTasks(tasks: List<Task>): List<Task> {
        val now = System.currentTimeMillis()
        return tasks.filter { task ->
            task.dueDate != null && task.dueDate < now &&
            task.status == TaskStatus.PENDING
        }
    }

    fun getTasksDueSoon(tasks: List<Task>, withinHours: Int = 24): List<Task> {
        val now = System.currentTimeMillis()
        val cutoff = now + TimeUnit.HOURS.toMillis(withinHours.toLong())
        return tasks.filter { task ->
            task.dueDate != null && task.dueDate in now..cutoff &&
            task.status == TaskStatus.PENDING
        }.sortedBy { it.dueDate }
    }
}
