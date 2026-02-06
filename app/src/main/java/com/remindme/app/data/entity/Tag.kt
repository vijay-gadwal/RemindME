package com.remindme.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TagType {
    LOCATION,   // bangalore, supermarket, nursery
    CATEGORY,   // car, shopping, food, travel
    CONTEXT,    // when-traveling, next-visit, on-the-way
    PERSON,     // related to a person
    CUSTOM      // user-defined
}

@Entity(tableName = "tags")
data class Tag(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: TagType = TagType.CUSTOM,
    val color: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
