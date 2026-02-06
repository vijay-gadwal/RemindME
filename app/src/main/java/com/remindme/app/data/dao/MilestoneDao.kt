package com.remindme.app.data.dao

import androidx.room.*
import com.remindme.app.data.entity.Milestone
import kotlinx.coroutines.flow.Flow

@Dao
interface MilestoneDao {
    @Query("SELECT * FROM milestones WHERE goalId = :goalId ORDER BY orderIndex ASC")
    fun getMilestonesForGoal(goalId: Long): Flow<List<Milestone>>

    @Query("SELECT * FROM milestones WHERE goalId = :goalId ORDER BY orderIndex ASC")
    suspend fun getMilestonesForGoalSync(goalId: Long): List<Milestone>

    @Query("SELECT * FROM milestones ORDER BY goalId, orderIndex ASC")
    suspend fun getAllMilestonesSync(): List<Milestone>

    @Query("SELECT * FROM milestones WHERE id = :id")
    suspend fun getMilestoneById(id: Long): Milestone?

    @Query("SELECT * FROM milestones WHERE goalId = :goalId AND isCompleted = 0 ORDER BY orderIndex ASC LIMIT 1")
    suspend fun getNextMilestone(goalId: Long): Milestone?

    @Query("""
        SELECT * FROM milestones 
        WHERE isCompleted = 0 
        AND targetDate IS NOT NULL 
        AND targetDate BETWEEN :startTime AND :endTime
        ORDER BY targetDate ASC
    """)
    fun getMilestonesDueBetween(startTime: Long, endTime: Long): Flow<List<Milestone>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMilestone(milestone: Milestone): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMilestones(milestones: List<Milestone>)

    @Update
    suspend fun updateMilestone(milestone: Milestone)

    @Delete
    suspend fun deleteMilestone(milestone: Milestone)

    @Query("DELETE FROM milestones WHERE goalId = :goalId")
    suspend fun deleteMilestonesForGoal(goalId: Long)

    @Query("UPDATE milestones SET isCompleted = 1, completedAt = :now WHERE id = :milestoneId")
    suspend fun completeMilestone(milestoneId: Long, now: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM milestones WHERE goalId = :goalId")
    suspend fun getMilestoneCount(goalId: Long): Int

    @Query("SELECT COUNT(*) FROM milestones WHERE goalId = :goalId AND isCompleted = 1")
    suspend fun getCompletedMilestoneCount(goalId: Long): Int
}
