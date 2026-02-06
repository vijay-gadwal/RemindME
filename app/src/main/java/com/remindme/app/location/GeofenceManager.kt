package com.remindme.app.location

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.remindme.app.data.entity.SavedLocation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class GeofenceManager(private val context: Context) {

    private val geofencingClient: GeofencingClient =
        LocationServices.getGeofencingClient(context)

    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    companion object {
        const val DEFAULT_RADIUS_METERS = 200f
        const val LOITERING_DELAY_MS = 30_000 // 30 seconds
    }

    suspend fun addGeofence(savedLocation: SavedLocation): Boolean {
        if (!hasPermission()) return false

        val geofence = Geofence.Builder()
            .setRequestId(savedLocation.id.toString())
            .setCircularRegion(
                savedLocation.latitude,
                savedLocation.longitude,
                savedLocation.radius
            )
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(
                Geofence.GEOFENCE_TRANSITION_ENTER or
                Geofence.GEOFENCE_TRANSITION_EXIT or
                Geofence.GEOFENCE_TRANSITION_DWELL
            )
            .setLoiteringDelay(LOITERING_DELAY_MS)
            .build()

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER or GeofencingRequest.INITIAL_TRIGGER_DWELL)
            .addGeofence(geofence)
            .build()

        return suspendCancellableCoroutine { continuation ->
            try {
                geofencingClient.addGeofences(request, geofencePendingIntent)
                    .addOnSuccessListener { continuation.resume(true) }
                    .addOnFailureListener { continuation.resume(false) }
            } catch (e: SecurityException) {
                continuation.resume(false)
            }
        }
    }

    suspend fun addGeofences(locations: List<SavedLocation>): Boolean {
        if (!hasPermission() || locations.isEmpty()) return false

        val geofences = locations.map { loc ->
            Geofence.Builder()
                .setRequestId(loc.id.toString())
                .setCircularRegion(
                    loc.latitude,
                    loc.longitude,
                    loc.radius
                )
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(
                    Geofence.GEOFENCE_TRANSITION_ENTER or
                    Geofence.GEOFENCE_TRANSITION_EXIT or
                    Geofence.GEOFENCE_TRANSITION_DWELL
                )
                .setLoiteringDelay(LOITERING_DELAY_MS)
                .build()
        }

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER or GeofencingRequest.INITIAL_TRIGGER_DWELL)
            .addGeofences(geofences)
            .build()

        return suspendCancellableCoroutine { continuation ->
            try {
                geofencingClient.addGeofences(request, geofencePendingIntent)
                    .addOnSuccessListener { continuation.resume(true) }
                    .addOnFailureListener { continuation.resume(false) }
            } catch (e: SecurityException) {
                continuation.resume(false)
            }
        }
    }

    fun removeGeofence(locationId: Long) {
        geofencingClient.removeGeofences(listOf(locationId.toString()))
    }

    fun removeAllGeofences() {
        geofencingClient.removeGeofences(geofencePendingIntent)
    }

    private fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
}
