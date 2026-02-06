package com.remindme.app.location

import android.content.Context
import com.remindme.app.data.database.AppDatabase
import com.remindme.app.data.entity.SavedLocation
import com.remindme.app.data.entity.Task
import com.remindme.app.data.entity.TriggerType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LocationTaskMatch(
    val task: Task,
    val matchType: LocationMatchType,
    val placeName: String? = null,
    val distanceMeters: Float = 0f,
    val confidence: Float = 0f
)

enum class LocationMatchType {
    GEOFENCE_ENTER,
    NEARBY_POI,
    CITY_MATCH,
    AREA_MATCH,
    ON_ROUTE
}

class LocationAwareTaskMatcher(private val context: Context) {

    private val database = AppDatabase.getDatabase(context)
    private val locationManager = LocationManager(context)

    suspend fun findTasksForCurrentLocation(): List<LocationTaskMatch> = withContext(Dispatchers.IO) {
        val location = locationManager.currentLocation.value ?: return@withContext emptyList()
        val activeTasks = database.taskDao().getActiveTasksSync()
        val matches = mutableListOf<LocationTaskMatch>()

        for (task in activeTasks) {
            if (task.triggerType != TriggerType.LOCATION) continue
            val taskLocation = task.locationName?.lowercase() ?: continue

            // 1. Check city match
            if (location.city != null && taskLocation.contains(location.city.lowercase())) {
                matches.add(
                    LocationTaskMatch(
                        task = task,
                        matchType = LocationMatchType.CITY_MATCH,
                        placeName = location.city,
                        confidence = 0.6f
                    )
                )
                continue
            }

            // 2. Check area/neighborhood match
            if (location.area != null && taskLocation.contains(location.area.lowercase())) {
                matches.add(
                    LocationTaskMatch(
                        task = task,
                        matchType = LocationMatchType.AREA_MATCH,
                        placeName = location.area,
                        confidence = 0.8f
                    )
                )
                continue
            }

            // 3. Check saved location geofence match
            val savedLocations = database.savedLocationDao().getActiveLocationsSync()
            for (savedLoc in savedLocations) {
                if (taskLocation.contains(savedLoc.name.lowercase())) {
                    val distance = locationManager.calculateDistance(
                        location.latitude, location.longitude,
                        savedLoc.latitude, savedLoc.longitude
                    )
                    val radius = savedLoc.radius
                    if (distance <= radius) {
                        matches.add(
                            LocationTaskMatch(
                                task = task,
                                matchType = LocationMatchType.GEOFENCE_ENTER,
                                placeName = savedLoc.name,
                                distanceMeters = distance,
                                confidence = 0.95f
                            )
                        )
                    }
                }
            }
        }

        // 4. Check nearby POIs via Overpass API
        val poiMatches = findNearbyPoiMatches(location, activeTasks)
        matches.addAll(poiMatches)

        matches.sortedByDescending { it.confidence }
    }

    private suspend fun findNearbyPoiMatches(
        location: LocationData,
        tasks: List<Task>
    ): List<LocationTaskMatch> {
        val matches = mutableListOf<LocationTaskMatch>()

        // Extract unique location keywords from tasks
        val locationKeywords = tasks
            .filter { it.triggerType == TriggerType.LOCATION }
            .mapNotNull { it.locationName?.lowercase() }
            .distinct()

        for (keyword in locationKeywords) {
            val category = OverpassApiClient.getCategoryForKeyword(keyword)
            if (category != null) {
                val nearbyPlaces = OverpassApiClient.findNearbyPlaces(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    radiusMeters = 500,
                    category = category
                )

                if (nearbyPlaces.isNotEmpty()) {
                    val nearestPlace = nearbyPlaces.first()
                    val matchingTasks = tasks.filter {
                        it.triggerType == TriggerType.LOCATION &&
                        it.locationName?.lowercase()?.contains(keyword) == true
                    }

                    for (task in matchingTasks) {
                        matches.add(
                            LocationTaskMatch(
                                task = task,
                                matchType = LocationMatchType.NEARBY_POI,
                                placeName = nearestPlace.name,
                                distanceMeters = nearestPlace.distance,
                                confidence = (1f - (nearestPlace.distance / 500f)).coerceIn(0.3f, 0.9f)
                            )
                        )
                    }
                }
            }
        }

        return matches
    }

    suspend fun findTasksAlongRoute(
        destLat: Double,
        destLon: Double,
        mode: OsrmRoutingClient.TravelMode = OsrmRoutingClient.TravelMode.DRIVING
    ): List<TaskOnRoute> {
        val location = locationManager.currentLocation.value ?: return emptyList()
        val activeTasks = database.taskDao().getActiveTasksSync()

        // Get tasks with known locations (from saved locations)
        val savedLocations = database.savedLocationDao().getActiveLocationsSync()
        val taskLocations = mutableListOf<Triple<Long, String, Pair<Double, Double>>>()

        for (task in activeTasks) {
            if (task.triggerType != TriggerType.LOCATION || task.locationName == null) continue

            // Find matching saved location coordinates
            val matchingLoc = savedLocations.find { savedLoc ->
                task.locationName.equals(savedLoc.name, ignoreCase = true) ||
                task.locationName.equals(savedLoc.address, ignoreCase = true)
            }

            if (matchingLoc != null) {
                taskLocations.add(
                    Triple(task.id, task.description, Pair(matchingLoc.latitude, matchingLoc.longitude))
                )
            }
        }

        if (taskLocations.isEmpty()) return emptyList()

        return OsrmRoutingClient.findTasksAlongRoute(
            originLat = location.latitude,
            originLon = location.longitude,
            destLat = destLat,
            destLon = destLon,
            taskLocations = taskLocations,
            mode = mode
        )
    }

    suspend fun saveCurrentLocationAs(name: String): SavedLocation? {
        val location = locationManager.currentLocation.value ?: return null
        val savedLocation = SavedLocation(
            name = name,
            latitude = location.latitude,
            longitude = location.longitude,
            address = location.address,
            radius = GeofenceManager.DEFAULT_RADIUS_METERS
        )
        val id = database.savedLocationDao().insertLocation(savedLocation)
        return savedLocation.copy(id = id)
    }
}
