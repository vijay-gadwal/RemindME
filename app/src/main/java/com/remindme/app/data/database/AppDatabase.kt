package com.remindme.app.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.remindme.app.data.dao.*
import com.remindme.app.data.entity.*

@Database(
    entities = [
        Task::class,
        Tag::class,
        TaskTag::class,
        SavedLocation::class,
        Conversation::class,
        Goal::class,
        Milestone::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun tagDao(): TagDao
    abstract fun conversationDao(): ConversationDao
    abstract fun goalDao(): GoalDao
    abstract fun milestoneDao(): MilestoneDao
    abstract fun savedLocationDao(): SavedLocationDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "remindme_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
