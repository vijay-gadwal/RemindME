package com.remindme.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.remindme.app.data.database.AppDatabase
import com.remindme.app.data.entity.*
import com.remindme.app.repository.TaskRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class TaskWithTags(
    val task: Task,
    val tags: List<Tag> = emptyList()
)

class TaskViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository = TaskRepository(database.taskDao(), database.tagDao())

    val activeTasks: StateFlow<List<Task>> = repository.getActiveTasks()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val allTasks: StateFlow<List<Task>> = repository.getAllTasks()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val activeTaskCount: StateFlow<Int> = repository.getActiveTaskCount()
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val completedTaskCount: StateFlow<Int> = repository.getCompletedTaskCount()
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    private val _selectedFilter = MutableStateFlow<TaskStatus?>(null)
    val selectedFilter: StateFlow<TaskStatus?> = _selectedFilter.asStateFlow()

    private val _tasksWithTags = MutableStateFlow<List<TaskWithTags>>(emptyList())
    val tasksWithTags: StateFlow<List<TaskWithTags>> = _tasksWithTags.asStateFlow()

    init {
        // Load tasks with their tags
        viewModelScope.launch {
            activeTasks.collect { tasks ->
                val withTags = tasks.map { task ->
                    TaskWithTags(task, repository.getTagsForTask(task.id))
                }
                _tasksWithTags.value = withTags
            }
        }
    }

    fun setFilter(status: TaskStatus?) {
        _selectedFilter.value = status
    }

    fun addTask(task: Task, tags: List<String> = emptyList()) {
        viewModelScope.launch {
            val taskId = repository.insertTask(task)
            for (tagName in tags) {
                repository.addTagToTask(taskId, tagName)
            }
        }
    }

    fun updateTask(task: Task) {
        viewModelScope.launch {
            repository.updateTask(task)
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            repository.deleteTask(task)
        }
    }

    fun completeTask(taskId: Long) {
        viewModelScope.launch {
            repository.completeTask(taskId)
        }
    }

    fun snoozeTask(taskId: Long, untilMillis: Long) {
        viewModelScope.launch {
            repository.snoozeTask(taskId, untilMillis)
        }
    }

    fun reactivateTask(taskId: Long) {
        viewModelScope.launch {
            repository.reactivateTask(taskId)
        }
    }
}
