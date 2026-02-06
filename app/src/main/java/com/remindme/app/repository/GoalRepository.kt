package com.remindme.app.repository

import com.remindme.app.data.dao.GoalDao
import com.remindme.app.data.dao.MilestoneDao
import com.remindme.app.data.entity.*
import kotlinx.coroutines.flow.Flow

class GoalRepository(
    private val goalDao: GoalDao,
    private val milestoneDao: MilestoneDao
) {
    fun getAllGoals(): Flow<List<Goal>> = goalDao.getAllGoals()
    fun getActiveGoals(): Flow<List<Goal>> = goalDao.getActiveGoals()
    suspend fun getActiveGoalsSync(): List<Goal> = goalDao.getActiveGoalsSync()
    suspend fun getGoalById(id: Long): Goal? = goalDao.getGoalById(id)
    fun getGoalByIdFlow(id: Long): Flow<Goal?> = goalDao.getGoalByIdFlow(id)
    fun getGoalsByCategory(category: GoalCategory): Flow<List<Goal>> = goalDao.getGoalsByCategory(category)
    fun getGoalsByStatus(status: GoalStatus): Flow<List<Goal>> = goalDao.getGoalsByStatus(status)
    fun getActiveGoalCount(): Flow<Int> = goalDao.getActiveGoalCount()

    suspend fun insertGoal(goal: Goal): Long = goalDao.insertGoal(goal)

    suspend fun updateGoal(goal: Goal) {
        goalDao.updateGoal(goal.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteGoal(goal: Goal) {
        milestoneDao.deleteMilestonesForGoal(goal.id)
        goalDao.deleteGoal(goal)
    }

    suspend fun updateGoalStatus(goalId: Long, status: GoalStatus) {
        goalDao.updateGoalStatus(goalId, status)
    }

    suspend fun checkInGoal(goalId: Long) {
        val goal = goalDao.getGoalById(goalId) ?: return
        val newStreak = goal.currentStreak + 1
        goalDao.checkInGoal(goalId, newStreak)
    }

    suspend fun updateGoalProgress(goalId: Long) {
        val total = milestoneDao.getMilestoneCount(goalId)
        if (total == 0) return
        val completed = milestoneDao.getCompletedMilestoneCount(goalId)
        val progress = (completed.toFloat() / total.toFloat()) * 100f
        goalDao.updateGoalProgress(goalId, progress)

        if (completed == total) {
            goalDao.updateGoalStatus(goalId, GoalStatus.COMPLETED)
        } else if (completed > 0) {
            goalDao.updateGoalStatus(goalId, GoalStatus.IN_PROGRESS)
        }
    }

    // Milestone operations
    fun getMilestonesForGoal(goalId: Long): Flow<List<Milestone>> = milestoneDao.getMilestonesForGoal(goalId)
    suspend fun getMilestonesForGoalSync(goalId: Long): List<Milestone> = milestoneDao.getMilestonesForGoalSync(goalId)
    suspend fun getNextMilestone(goalId: Long): Milestone? = milestoneDao.getNextMilestone(goalId)

    suspend fun insertMilestone(milestone: Milestone): Long = milestoneDao.insertMilestone(milestone)
    suspend fun insertMilestones(milestones: List<Milestone>) = milestoneDao.insertMilestones(milestones)

    suspend fun updateMilestone(milestone: Milestone) = milestoneDao.updateMilestone(milestone)
    suspend fun deleteMilestone(milestone: Milestone) {
        milestoneDao.deleteMilestone(milestone)
        updateGoalProgress(milestone.goalId)
    }

    suspend fun completeMilestone(milestoneId: Long, goalId: Long) {
        milestoneDao.completeMilestone(milestoneId)
        updateGoalProgress(goalId)
    }

    suspend fun getGoalsNeedingCheckIn(cutoffTime: Long): List<Goal> =
        goalDao.getGoalsNeedingCheckIn(cutoffTime)
}
