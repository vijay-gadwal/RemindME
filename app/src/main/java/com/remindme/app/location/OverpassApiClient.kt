package com.remindme.app.location

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class NearbyPlace(
    val id: Long,
    val name: String,
    val type: String,
    val latitude: Double,
    val longitude: Double,
    val distance: Float = 0f,
    val tags: Map<String, String> = emptyMap()
)

data class OverpassResponse(
    val elements: List<OverpassElement> = emptyList()
)

data class OverpassElement(
    val id: Long = 0,
    val type: String = "",
    val lat: Double? = null,
    val lon: Double? = null,
    val center: OverpassCenter? = null,
    val tags: Map<String, String>? = null
)

data class OverpassCenter(
    val lat: Double = 0.0,
    val lon: Double = 0.0
)

object OverpassApiClient {

    private const val OVERPASS_URL = "https://overpass-api.de/api/interpreter"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    // Common POI categories for task matching
    private val poiCategories = mapOf(
        "supermarket" to "[\"shop\"=\"supermarket\"]",
        "grocery" to "[\"shop\"~\"supermarket|convenience|grocery\"]",
        "pharmacy" to "[\"amenity\"=\"pharmacy\"]",
        "hospital" to "[\"amenity\"=\"hospital\"]",
        "bank" to "[\"amenity\"=\"bank\"]",
        "atm" to "[\"amenity\"=\"atm\"]",
        "restaurant" to "[\"amenity\"=\"restaurant\"]",
        "cafe" to "[\"amenity\"=\"cafe\"]",
        "gas_station" to "[\"amenity\"=\"fuel\"]",
        "petrol" to "[\"amenity\"=\"fuel\"]",
        "post_office" to "[\"amenity\"=\"post_office\"]",
        "gym" to "[\"leisure\"=\"fitness_centre\"]",
        "park" to "[\"leisure\"=\"park\"]",
        "school" to "[\"amenity\"=\"school\"]",
        "library" to "[\"amenity\"=\"library\"]",
        "temple" to "[\"amenity\"=\"place_of_worship\"]",
        "church" to "[\"amenity\"=\"place_of_worship\"][\"religion\"=\"christian\"]",
        "mosque" to "[\"amenity\"=\"place_of_worship\"][\"religion\"=\"muslim\"]",
        "mall" to "[\"shop\"=\"mall\"]",
        "shopping" to "[\"shop\"~\"mall|department_store|supermarket\"]",
        "hotel" to "[\"tourism\"=\"hotel\"]",
        "airport" to "[\"aeroway\"=\"aerodrome\"]",
        "bus_station" to "[\"amenity\"=\"bus_station\"]",
        "train_station" to "[\"railway\"=\"station\"]",
        "parking" to "[\"amenity\"=\"parking\"]",
        "doctor" to "[\"amenity\"~\"doctors|clinic\"]",
        "dentist" to "[\"amenity\"=\"dentist\"]",
        "car_service" to "[\"shop\"~\"car_repair|car\"]",
        "mechanic" to "[\"shop\"=\"car_repair\"]"
    )

    suspend fun findNearbyPlaces(
        latitude: Double,
        longitude: Double,
        radiusMeters: Int = 1000,
        category: String? = null
    ): List<NearbyPlace> = withContext(Dispatchers.IO) {
        try {
            val query = if (category != null) {
                buildCategoryQuery(latitude, longitude, radiusMeters, category)
            } else {
                buildGeneralQuery(latitude, longitude, radiusMeters)
            }

            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "$OVERPASS_URL?data=$encodedQuery"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "RemindME/1.0")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext emptyList()

            val overpassResponse = gson.fromJson(body, OverpassResponse::class.java)
            parseElements(overpassResponse.elements, latitude, longitude)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun searchPlaceByName(
        latitude: Double,
        longitude: Double,
        name: String,
        radiusMeters: Int = 5000
    ): List<NearbyPlace> = withContext(Dispatchers.IO) {
        try {
            val query = """
                [out:json][timeout:10];
                (
                  node["name"~"$name",i](around:$radiusMeters,$latitude,$longitude);
                  way["name"~"$name",i](around:$radiusMeters,$latitude,$longitude);
                );
                out center body;
            """.trimIndent()

            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "$OVERPASS_URL?data=$encodedQuery"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "RemindME/1.0")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext emptyList()

            val overpassResponse = gson.fromJson(body, OverpassResponse::class.java)
            parseElements(overpassResponse.elements, latitude, longitude)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getCategoryForKeyword(keyword: String): String? {
        val lower = keyword.lowercase()
        return poiCategories.keys.firstOrNull { lower.contains(it) || it.contains(lower) }
    }

    private fun buildCategoryQuery(
        lat: Double, lon: Double, radius: Int, category: String
    ): String {
        val filter = poiCategories[category.lowercase()] ?: return buildGeneralQuery(lat, lon, radius)
        return """
            [out:json][timeout:10];
            (
              node${filter}(around:$radius,$lat,$lon);
              way${filter}(around:$radius,$lat,$lon);
            );
            out center body;
        """.trimIndent()
    }

    private fun buildGeneralQuery(lat: Double, lon: Double, radius: Int): String {
        return """
            [out:json][timeout:10];
            (
              node["amenity"](around:$radius,$lat,$lon);
              node["shop"](around:$radius,$lat,$lon);
              way["amenity"](around:$radius,$lat,$lon);
              way["shop"](around:$radius,$lat,$lon);
            );
            out center body;
        """.trimIndent()
    }

    private fun parseElements(
        elements: List<OverpassElement>,
        refLat: Double,
        refLon: Double
    ): List<NearbyPlace> {
        return elements.mapNotNull { element ->
            val lat = element.lat ?: element.center?.lat ?: return@mapNotNull null
            val lon = element.lon ?: element.center?.lon ?: return@mapNotNull null
            val tags = element.tags ?: return@mapNotNull null
            val name = tags["name"] ?: return@mapNotNull null

            val type = tags["amenity"] ?: tags["shop"] ?: tags["leisure"]
                ?: tags["tourism"] ?: tags["railway"] ?: "place"

            val results = FloatArray(1)
            android.location.Location.distanceBetween(refLat, refLon, lat, lon, results)

            NearbyPlace(
                id = element.id,
                name = name,
                type = type,
                latitude = lat,
                longitude = lon,
                distance = results[0],
                tags = tags
            )
        }.sortedBy { it.distance }
    }
}
