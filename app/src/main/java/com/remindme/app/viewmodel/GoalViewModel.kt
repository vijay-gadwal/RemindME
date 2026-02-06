package com.remindme.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.remindme.app.data.database.AppDatabase
import com.remindme.app.data.entity.*
import com.remindme.app.repository.GoalRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class GoalWithMilestones(
    val goal: Goal,
    val milestones: List<Milestone> = emptyList(),
    val nextMilestone: Milestone? = null
)

class GoalViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository = GoalRepository(database.goalDao(), database.milestoneDao())

    val activeGoals: StateFlow<List<Goal>> = repository.getActiveGoals()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val allGoals: StateFlow<List<Goal>> = repository.getAllGoals()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val activeGoalCount: StateFlow<Int> = repository.getActiveGoalCount()
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    private val _selectedGoal = MutableStateFlow<GoalWithMilestones?>(null)
    val selectedGoal: StateFlow<GoalWithMilestones?> = _selectedGoal.asStateFlow()

    private val _goalsWithMilestones = MutableStateFlow<List<GoalWithMilestones>>(emptyList())
    val goalsWithMilestones: StateFlow<List<GoalWithMilestones>> = _goalsWithMilestones.asStateFlow()

    private val _selectedCategory = MutableStateFlow<GoalCategory?>(null)
    val selectedCategory: StateFlow<GoalCategory?> = _selectedCategory.asStateFlow()

    init {
        viewModelScope.launch {
            activeGoals.collect { goals ->
                val withMilestones = goals.map { goal ->
                    val milestones = repository.getMilestonesForGoalSync(goal.id)
                    val next = repository.getNextMilestone(goal.id)
                    GoalWithMilestones(goal, milestones, next)
                }
                _goalsWithMilestones.value = withMilestones
            }
        }
    }

    fun setSelectedCategory(category: GoalCategory?) {
        _selectedCategory.value = category
    }

    fun loadGoalDetail(goalId: Long) {
        viewModelScope.launch {
            val goal = repository.getGoalById(goalId) ?: return@launch
            val milestones = repository.getMilestonesForGoalSync(goalId)
            val next = repository.getNextMilestone(goalId)
            _selectedGoal.value = GoalWithMilestones(goal, milestones, next)
        }
    }

    fun addGoal(goal: Goal, milestones: List<String> = emptyList()) {
        viewModelScope.launch {
            val goalId = repository.insertGoal(goal)
            if (milestones.isNotEmpty()) {
                val milestoneEntities = milestones.mapIndexed { index, title ->
                    Milestone(
                        goalId = goalId,
                        title = title,
                        orderIndex = index
                    )
                }
                repository.insertMilestones(milestoneEntities)
            }
        }
    }

    fun updateGoal(goal: Goal) {
        viewModelScope.launch {
            repository.updateGoal(goal)
            loadGoalDetail(goal.id)
        }
    }

    fun deleteGoal(goal: Goal) {
        viewModelScope.launch {
            repository.deleteGoal(goal)
            _selectedGoal.value = null
        }
    }

    fun updateGoalStatus(goalId: Long, status: GoalStatus) {
        viewModelScope.launch {
            repository.updateGoalStatus(goalId, status)
            loadGoalDetail(goalId)
        }
    }

    fun checkInGoal(goalId: Long) {
        viewModelScope.launch {
            repository.checkInGoal(goalId)
            loadGoalDetail(goalId)
        }
    }

    // Milestone operations
    fun addMilestone(goalId: Long, title: String, description: String? = null, targetDate: Long? = null) {
        viewModelScope.launch {
            val milestones = repository.getMilestonesForGoalSync(goalId)
            val nextOrder = (milestones.maxOfOrNull { it.orderIndex } ?: -1) + 1
            repository.insertMilestone(
                Milestone(
                    goalId = goalId,
                    title = title,
                    description = description,
                    targetDate = targetDate,
                    orderIndex = nextOrder
                )
            )
            repository.updateGoalProgress(goalId)
            loadGoalDetail(goalId)
        }
    }

    fun completeMilestone(milestoneId: Long, goalId: Long) {
        viewModelScope.launch {
            repository.completeMilestone(milestoneId, goalId)
            loadGoalDetail(goalId)
        }
    }

    fun deleteMilestone(milestone: Milestone) {
        viewModelScope.launch {
            repository.deleteMilestone(milestone)
            loadGoalDetail(milestone.goalId)
        }
    }
}
