package com.remindme.app.data.dao

import androidx.room.*
import com.remindme.app.data.entity.Tag
import com.remindme.app.data.entity.TagType
import com.remindme.app.data.entity.TaskTag
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY name ASC")
    fun getAllTags(): Flow<List<Tag>>

    @Query("SELECT * FROM tags ORDER BY name ASC")
    suspend fun getAllTagsSync(): List<Tag>

    @Query("SELECT * FROM task_tags")
    suspend fun getAllTaskTagsSync(): List<TaskTag>

    @Query("SELECT * FROM tags WHERE type = :type ORDER BY name ASC")
    fun getTagsByType(type: TagType): Flow<List<Tag>>

    @Query("SELECT * FROM tags WHERE id = :id")
    suspend fun getTagById(id: Long): Tag?

    @Query("SELECT * FROM tags WHERE name = :name LIMIT 1")
    suspend fun getTagByName(name: String): Tag?

    @Query("SELECT * FROM tags WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun getTagByNameIgnoreCase(name: String): Tag?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: Tag): Long

    @Update
    suspend fun updateTag(tag: Tag)

    @Delete
    suspend fun deleteTag(tag: Tag)

    // TaskTag cross-reference operations
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTaskTag(taskTag: TaskTag)

    @Delete
    suspend fun deleteTaskTag(taskTag: TaskTag)

    @Query("DELETE FROM task_tags WHERE taskId = :taskId")
    suspend fun deleteTagsForTask(taskId: Long)

    @Query("""
        SELECT t.* FROM tags t 
        INNER JOIN task_tags tt ON t.id = tt.tagId 
        WHERE tt.taskId = :taskId
    """)
    fun getTagsForTask(taskId: Long): Flow<List<Tag>>

    @Query("""
        SELECT t.* FROM tags t 
        INNER JOIN task_tags tt ON t.id = tt.tagId 
        WHERE tt.taskId = :taskId
    """)
    suspend fun getTagsForTaskSync(taskId: Long): List<Tag>

    @Query("""
        SELECT tk.* FROM tasks tk 
        INNER JOIN task_tags tt ON tk.id = tt.taskId 
        WHERE tt.tagId = :tagId AND tk.status IN ('PENDING', 'IN_PROGRESS')
    """)
    suspend fun getActiveTasksForTag(tagId: Long): List<com.remindme.app.data.entity.Task>
}
