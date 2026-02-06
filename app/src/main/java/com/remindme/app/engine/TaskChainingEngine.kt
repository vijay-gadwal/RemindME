package com.remindme.app.engine

import com.remindme.app.data.entity.Goal
import com.remindme.app.data.entity.GoalStatus
import com.remindme.app.data.entity.Milestone
import com.remindme.app.data.entity.Task
import com.remindme.app.data.entity.TaskStatus

data class TaskChain(
    val id: String,
    val name: String,
    val tasks: List<Task>,
    val currentStepIndex: Int = 0,
    val isComplete: Boolean = false
)

data class GoalDependency(
    val goalId: Long,
    val dependsOnGoalIds: List<Long>,
    val isBlocked: Boolean
)

object TaskChainingEngine {

    fun buildChainFromTasks(name: String, tasks: List<Task>): TaskChain {
        val sorted = tasks.sortedBy { it.createdAt }
        val currentIndex = sorted.indexOfFirst {
            it.status == TaskStatus.PENDING || it.status == TaskStatus.SNOOZED
        }.coerceAtLeast(0)

        return TaskChain(
            id = "chain_${System.currentTimeMillis()}",
            name = name,
            tasks = sorted,
            currentStepIndex = currentIndex,
            isComplete = sorted.all { it.status == TaskStatus.COMPLETED }
        )
    }

    fun getNextTask(chain: TaskChain): Task? {
        if (chain.isComplete) return null
        return chain.tasks.getOrNull(chain.currentStepIndex)
    }

    fun getCompletedCount(chain: TaskChain): Int {
        return chain.tasks.count { it.status == TaskStatus.COMPLETED }
    }

    fun getProgress(chain: TaskChain): Float {
        if (chain.tasks.isEmpty()) return 0f
        return getCompletedCount(chain).toFloat() / chain.tasks.size
    }

    // Goal dependencies
    fun checkGoalDependencies(
        goal: Goal,
        allGoals: List<Goal>,
        dependencyMap: Map<Long, List<Long>>
    ): GoalDependency {
        val dependencies = dependencyMap[goal.id] ?: emptyList()
        val isBlocked = dependencies.any { depId ->
            val depGoal = allGoals.find { it.id == depId }
            depGoal != null && depGoal.status != GoalStatus.COMPLETED
        }

        return GoalDependency(
            goalId = goal.id,
            dependsOnGoalIds = dependencies,
            isBlocked = isBlocked
        )
    }

    fun suggestNextMilestone(
        milestones: List<Milestone>,
        completedMilestones: List<Milestone>
    ): Milestone? {
        val completedIds = completedMilestones.map { it.id }.toSet()
        return milestones
            .filter { it.id !in completedIds && !it.isCompleted }
            .minByOrNull { it.orderIndex }
    }

    fun buildMilestoneChain(milestones: List<Milestone>): List<Pair<Milestone, Boolean>> {
        val sorted = milestones.sortedBy { it.orderIndex }
        var foundIncomplete = false
        return sorted.map { milestone ->
            val isNext = !milestone.isCompleted && !foundIncomplete
            if (isNext) foundIncomplete = true
            milestone to isNext
        }
    }

    fun calculateChainHealth(chain: TaskChain): String {
        val progress = getProgress(chain)
        val overdueCount = chain.tasks.count { task ->
            task.dueDate != null && task.dueDate < System.currentTimeMillis() &&
            task.status == TaskStatus.PENDING
        }

        return when {
            chain.isComplete -> "Complete"
            overdueCount > 0 -> "At Risk ($overdueCount overdue)"
            progress >= 0.75f -> "Almost Done"
            progress >= 0.5f -> "Halfway There"
            progress >= 0.25f -> "Making Progress"
            progress > 0f -> "Getting Started"
            else -> "Not Started"
        }
    }
}
