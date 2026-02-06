package com.remindme.app.data.dao

import androidx.room.*
import com.remindme.app.data.entity.SavedLocation
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedLocationDao {
    @Query("SELECT * FROM saved_locations WHERE isActive = 1 ORDER BY name ASC")
    fun getActiveLocations(): Flow<List<SavedLocation>>

    @Query("SELECT * FROM saved_locations WHERE isActive = 1 ORDER BY name ASC")
    suspend fun getActiveLocationsSync(): List<SavedLocation>

    @Query("SELECT * FROM saved_locations ORDER BY name ASC")
    fun getAllLocations(): Flow<List<SavedLocation>>

    @Query("SELECT * FROM saved_locations WHERE id = :id")
    suspend fun getLocationById(id: Long): SavedLocation?

    @Query("SELECT * FROM saved_locations WHERE LOWER(name) LIKE '%' || LOWER(:name) || '%'")
    suspend fun searchLocationsByName(name: String): List<SavedLocation>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocation(location: SavedLocation): Long

    @Update
    suspend fun updateLocation(location: SavedLocation)

    @Delete
    suspend fun deleteLocation(location: SavedLocation)
}
