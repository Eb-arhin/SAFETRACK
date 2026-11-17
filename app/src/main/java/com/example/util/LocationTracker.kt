package com.example.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await

sealed interface GpsFetchResult {
    data class Available(
        val latitude: Double,
        val longitude: Double,
        val accuracyMeters: Float? = null
    ) : GpsFetchResult
    data class Unavailable(val reason: String) : GpsFetchResult
}

object LocationTracker {
    fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    @SuppressLint("MissingPermission")
    suspend fun fetchCurrentLocation(context: Context): GpsFetchResult {
        if (!hasLocationPermission(context)) {
            return GpsFetchResult.Unavailable("Location permission not granted")
        }
        return try {
            val client = LocationServices.getFusedLocationProviderClient(context)
            val cts = CancellationTokenSource()
            val location = client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token).await()
                ?: client.lastLocation.await()
            if (location != null) {
                val acc = if (location.hasAccuracy()) location.accuracy else null
                GpsFetchResult.Available(location.latitude, location.longitude, acc)
            } else {
                GpsFetchResult.Unavailable("GPS signal unavailable on device")
            }
        } catch (e: Exception) {
            GpsFetchResult.Unavailable(e.localizedMessage ?: "GPS unavailable")
        }
    }
}
