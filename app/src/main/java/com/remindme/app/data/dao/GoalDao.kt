package com.remindme.app.data.dao

import androidx.room.*
import com.remindme.app.data.entity.Goal
import com.remindme.app.data.entity.GoalCategory
import com.remindme.app.data.entity.GoalStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals ORDER BY updatedAt DESC")
    fun getAllGoals(): Flow<List<Goal>>

    @Query("SELECT * FROM goals ORDER BY updatedAt DESC")
    suspend fun getAllGoalsSync(): List<Goal>

    @Query("SELECT * FROM goals WHERE status IN ('NOT_STARTED', 'IN_PROGRESS', 'ON_HOLD') ORDER BY updatedAt DESC")
    fun getActiveGoals(): Flow<List<Goal>>

    @Query("SELECT * FROM goals WHERE status IN ('NOT_STARTED', 'IN_PROGRESS', 'ON_HOLD') ORDER BY updatedAt DESC")
    suspend fun getActiveGoalsSync(): List<Goal>

    @Query("SELECT * FROM goals WHERE id = :id")
    suspend fun getGoalById(id: Long): Goal?

    @Query("SELECT * FROM goals WHERE id = :id")
    fun getGoalByIdFlow(id: Long): Flow<Goal?>

    @Query("SELECT * FROM goals WHERE category = :category ORDER BY updatedAt DESC")
    fun getGoalsByCategory(category: GoalCategory): Flow<List<Goal>>

    @Query("SELECT * FROM goals WHERE status = :status ORDER BY updatedAt DESC")
    fun getGoalsByStatus(status: GoalStatus): Flow<List<Goal>>

    @Query("""
        SELECT * FROM goals 
        WHERE status IN ('NOT_STARTED', 'IN_PROGRESS') 
        AND targetDate IS NOT NULL 
        AND targetDate BETWEEN :startTime AND :endTime
        ORDER BY targetDate ASC
    """)
    fun getGoalsDueBetween(startTime: Long, endTime: Long): Flow<List<Goal>>

    @Query("""
        SELECT * FROM goals 
        WHERE status IN ('IN_PROGRESS') 
        AND reminderEnabled = 1
        AND (lastCheckedIn IS NULL OR lastCheckedIn < :cutoffTime)
        ORDER BY updatedAt ASC
    """)
    suspend fun getGoalsNeedingCheckIn(cutoffTime: Long): List<Goal>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoal(goal: Goal): Long

    @Update
    suspend fun updateGoal(goal: Goal)

    @Delete
    suspend fun deleteGoal(goal: Goal)

    @Query("UPDATE goals SET status = :status, updatedAt = :now WHERE id = :goalId")
    suspend fun updateGoalStatus(goalId: Long, status: GoalStatus, now: Long = System.currentTimeMillis())

    @Query("UPDATE goals SET progress = :progress, updatedAt = :now WHERE id = :goalId")
    suspend fun updateGoalProgress(goalId: Long, progress: Float, now: Long = System.currentTimeMillis())

    @Query("""
        UPDATE goals SET 
        lastCheckedIn = :now, 
        currentStreak = :streak,
        bestStreak = CASE WHEN :streak > bestStreak THEN :streak ELSE bestStreak END,
        updatedAt = :now 
        WHERE id = :goalId
    """)
    suspend fun checkInGoal(goalId: Long, streak: Int, now: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM goals WHERE status IN ('NOT_STARTED', 'IN_PROGRESS', 'ON_HOLD')")
    fun getActiveGoalCount(): Flow<Int>
}
