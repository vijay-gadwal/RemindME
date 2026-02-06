package com.remindme.app.location

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class RouteResult(
    val distanceMeters: Double,
    val durationSeconds: Double,
    val geometry: String?,
    val waypoints: List<RouteWaypoint> = emptyList(),
    val steps: List<RouteStep> = emptyList()
)

data class RouteWaypoint(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val distanceFromRoute: Double = 0.0
)

data class RouteStep(
    val instruction: String,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val name: String
)

data class TaskOnRoute(
    val taskId: Long,
    val taskDescription: String,
    val locationName: String,
    val latitude: Double,
    val longitude: Double,
    val distanceFromRoute: Float,
    val estimatedArrivalSeconds: Double
)

// OSRM response models
data class OsrmResponse(
    val code: String = "",
    val routes: List<OsrmRoute> = emptyList(),
    val waypoints: List<OsrmWaypoint> = emptyList()
)

data class OsrmRoute(
    val distance: Double = 0.0,
    val duration: Double = 0.0,
    val geometry: String? = null,
    val legs: List<OsrmLeg> = emptyList()
)

data class OsrmLeg(
    val distance: Double = 0.0,
    val duration: Double = 0.0,
    val steps: List<OsrmStep> = emptyList()
)

data class OsrmStep(
    val distance: Double = 0.0,
    val duration: Double = 0.0,
    val name: String = "",
    val maneuver: OsrmManeuver? = null
)

data class OsrmManeuver(
    val type: String = "",
    val modifier: String? = null,
    val location: List<Double> = emptyList()
)

data class OsrmWaypoint(
    val name: String = "",
    val location: List<Double> = emptyList(),
    val distance: Double = 0.0
)

object OsrmRoutingClient {

    // Free public OSRM demo server — no API key required
    private const val OSRM_BASE_URL = "https://router.project-osrm.org"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    enum class TravelMode(val profile: String) {
        DRIVING("car"),
        CYCLING("bike"),
        WALKING("foot")
    }

    suspend fun getRoute(
        originLat: Double, originLon: Double,
        destLat: Double, destLon: Double,
        mode: TravelMode = TravelMode.DRIVING,
        includeSteps: Boolean = false
    ): RouteResult? = withContext(Dispatchers.IO) {
        try {
            val stepsParam = if (includeSteps) "true" else "false"
            val url = "$OSRM_BASE_URL/route/v1/${mode.profile}/" +
                    "$originLon,$originLat;$destLon,$destLat" +
                    "?overview=full&steps=$stepsParam&geometries=polyline"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "RemindME/1.0")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext null

            val osrmResponse = gson.fromJson(body, OsrmResponse::class.java)
            if (osrmResponse.code != "Ok" || osrmResponse.routes.isEmpty()) {
                return@withContext null
            }

            val route = osrmResponse.routes[0]
            val steps = if (includeSteps) {
                route.legs.flatMap { leg ->
                    leg.steps.map { step ->
                        val instruction = buildInstruction(step)
                        RouteStep(
                            instruction = instruction,
                            distanceMeters = step.distance,
                            durationSeconds = step.duration,
                            name = step.name
                        )
                    }
                }
            } else emptyList()

            val waypoints = osrmResponse.waypoints.map { wp ->
                RouteWaypoint(
                    name = wp.name,
                    latitude = wp.location.getOrElse(1) { 0.0 },
                    longitude = wp.location.getOrElse(0) { 0.0 },
                    distanceFromRoute = wp.distance
                )
            }

            RouteResult(
                distanceMeters = route.distance,
                durationSeconds = route.duration,
                geometry = route.geometry,
                waypoints = waypoints,
                steps = steps
            )
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getRouteWithWaypoints(
        points: List<Pair<Double, Double>>,
        mode: TravelMode = TravelMode.DRIVING
    ): RouteResult? = withContext(Dispatchers.IO) {
        if (points.size < 2) return@withContext null

        try {
            val coordinates = points.joinToString(";") { "${it.second},${it.first}" }
            val url = "$OSRM_BASE_URL/route/v1/${mode.profile}/$coordinates" +
                    "?overview=full&steps=true&geometries=polyline"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "RemindME/1.0")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext null

            val osrmResponse = gson.fromJson(body, OsrmResponse::class.java)
            if (osrmResponse.code != "Ok" || osrmResponse.routes.isEmpty()) {
                return@withContext null
            }

            val route = osrmResponse.routes[0]
            RouteResult(
                distanceMeters = route.distance,
                durationSeconds = route.duration,
                geometry = route.geometry
            )
        } catch (e: Exception) {
            null
        }
    }

    suspend fun findTasksAlongRoute(
        originLat: Double, originLon: Double,
        destLat: Double, destLon: Double,
        taskLocations: List<Triple<Long, String, Pair<Double, Double>>>,
        maxDeviationMeters: Float = 500f,
        mode: TravelMode = TravelMode.DRIVING
    ): List<TaskOnRoute> = withContext(Dispatchers.IO) {
        val route = getRoute(originLat, originLon, destLat, destLon, mode, includeSteps = true)
            ?: return@withContext emptyList()

        val routePoints = decodePolyline(route.geometry ?: return@withContext emptyList())

        taskLocations.mapNotNull { (taskId, description, location) ->
            val (taskLat, taskLon) = location

            // Find minimum distance from task location to any point on the route
            var minDistance = Float.MAX_VALUE
            var closestRouteIndex = 0

            routePoints.forEachIndexed { index, (lat, lon) ->
                val results = FloatArray(1)
                android.location.Location.distanceBetween(taskLat, taskLon, lat, lon, results)
                if (results[0] < minDistance) {
                    minDistance = results[0]
                    closestRouteIndex = index
                }
            }

            if (minDistance <= maxDeviationMeters) {
                // Estimate arrival time based on position along route
                val progress = closestRouteIndex.toDouble() / routePoints.size.coerceAtLeast(1)
                val estimatedArrival = route.durationSeconds * progress

                TaskOnRoute(
                    taskId = taskId,
                    taskDescription = description,
                    locationName = description,
                    latitude = taskLat,
                    longitude = taskLon,
                    distanceFromRoute = minDistance,
                    estimatedArrivalSeconds = estimatedArrival
                )
            } else null
        }.sortedBy { it.estimatedArrivalSeconds }
    }

    private fun buildInstruction(step: OsrmStep): String {
        val maneuver = step.maneuver ?: return "Continue on ${step.name}"
        val direction = maneuver.modifier?.let { " $it" } ?: ""
        return when (maneuver.type) {
            "turn" -> "Turn$direction onto ${step.name}"
            "new name" -> "Continue onto ${step.name}"
            "depart" -> "Head${direction} on ${step.name}"
            "arrive" -> "Arrive at destination"
            "merge" -> "Merge$direction onto ${step.name}"
            "roundabout" -> "At roundabout, take exit onto ${step.name}"
            "fork" -> "Keep$direction onto ${step.name}"
            else -> "${maneuver.type}$direction - ${step.name}"
        }
    }

    // Decode Google-encoded polyline
    private fun decodePolyline(encoded: String): List<Pair<Double, Double>> {
        val poly = mutableListOf<Pair<Double, Double>>()
        var index = 0
        var lat = 0
        var lng = 0

        while (index < encoded.length) {
            var result = 0
            var shift = 0
            var b: Int
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat

            result = 0
            shift = 0
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dlng

            poly.add(Pair(lat / 1E5, lng / 1E5))
        }
        return poly
    }
}
