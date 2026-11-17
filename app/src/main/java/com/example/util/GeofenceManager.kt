package com.example.util

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.data.model.School
import com.example.data.model.Student
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TrackedGeofenceStudent(
    val id: String,
    val fullName: String,
    val classId: String,
    val className: String,
    val guardianUids: List<String>,
    val teacherUids: List<String>,
    val adminUids: List<String>
)

data class GeofenceMonitoringConfig(
    val isActive: Boolean = false,
    val schoolId: String = "",
    val schoolName: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val radiusMeters: Double = 150.0,
    val trackedStudents: List<TrackedGeofenceStudent> = emptyList(),
    val lastEventSummary: String? = null
)

object GeofenceManager {
    private const val PREFS_NAME = "safetrack_geofence_prefs"
    private const val KEY_CONFIG_JSON = "geofence_config_json"
    private const val KEY_LAST_EVENT_SUMMARY = "geofence_last_event_summary"
    private const val GEOFENCE_PENDING_INTENT_REQ_CODE = 4102
    private const val NOTIFICATION_RESPONSIVENESS_MS = 30_000

    private val _monitoringStatus = MutableStateFlow(GeofenceMonitoringConfig())
    val monitoringStatus: StateFlow<GeofenceMonitoringConfig> = _monitoringStatus.asStateFlow()

    fun refreshStatus(context: Context): GeofenceMonitoringConfig {
        val loaded = loadConfig(context)
        val effective = if (loaded.isActive && (!hasFineLocationPermission(context) || loaded.trackedStudents.isEmpty())) {
            loaded.copy(isActive = false)
        } else {
            loaded
        }
        _monitoringStatus.value = effective
        return effective
    }

    fun hasForegroundLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    fun hasFineLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasBackgroundLocationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun geofenceRequestIdForSchool(schoolId: String): String = "safetrack_school_$schoolId"

    @SuppressLint("MissingPermission")
    suspend fun registerSchoolGeofence(
        context: Context,
        school: School,
        students: List<Student>
    ): Result<String> {
        val lat = school.geofenceLatitude
        val lng = school.geofenceLongitude
        if (!school.geofenceEnabled || lat == null || lng == null) {
            return Result.failure(
                IllegalStateException("School premises coordinates are not configured or geofencing is disabled by your School Admin.")
            )
        }
        if (!hasFineLocationPermission(context)) {
            return Result.failure(
                SecurityException("Precise location (ACCESS_FINE_LOCATION) is required to monitor the school geofence.")
            )
        }
        if (students.isEmpty()) {
            return Result.failure(
                IllegalArgumentException("Select at least one linked child to monitor on this device.")
            )
        }

        val radius = school.geofenceRadiusMeters.toFloat().coerceIn(100f, 2000f)
        val requestId = geofenceRequestIdForSchool(school.id)
        val geofence = Geofence.Builder()
            .setRequestId(requestId)
            .setCircularRegion(lat, lng, radius)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
            .setNotificationResponsiveness(NOTIFICATION_RESPONSIVENESS_MS)
            .build()

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()

        return try {
            val client = LocationServices.getGeofencingClient(context.applicationContext)
            val pendingIntent = geofencePendingIntent(context.applicationContext)
            runCatching { client.removeGeofences(pendingIntent).await() }
            client.addGeofences(request, pendingIntent).await()

            val tracked = students.map { s ->
                TrackedGeofenceStudent(
                    id = s.id,
                    fullName = s.fullName,
                    classId = s.classId,
                    className = s.className,
                    guardianUids = s.guardianUids,
                    teacherUids = s.teacherUids,
                    adminUids = s.adminUids
                )
            }
            val config = GeofenceMonitoringConfig(
                isActive = true,
                schoolId = school.id,
                schoolName = school.name,
                latitude = lat,
                longitude = lng,
                radiusMeters = radius.toDouble(),
                trackedStudents = tracked,
                lastEventSummary = loadConfig(context).lastEventSummary
            )
            saveConfig(context, config)
            _monitoringStatus.value = config

            val bgNote = if (hasBackgroundLocationPermission(context)) {
                "Background & foreground monitoring active"
            } else {
                "Foreground monitoring active (enable 'Allow all the time' for background detection)"
            }
            Result.success("School geofence registered (${radius.toInt()}m radius) — $bgNote.")
        } catch (e: Exception) {
            val msg = GeofenceStatusCodes.getStatusCodeString((e as? com.google.android.gms.common.api.ApiException)?.statusCode ?: -1)
            Result.failure(IllegalStateException("Could not register Play Services geofence ($msg): ${e.localizedMessage}"))
        }
    }

    suspend fun unregisterSchoolGeofence(context: Context): Result<Unit> {
        return try {
            val client = LocationServices.getGeofencingClient(context.applicationContext)
            client.removeGeofences(geofencePendingIntent(context.applicationContext)).await()
            val updated = loadConfig(context).copy(isActive = false, trackedStudents = emptyList())
            saveConfig(context, updated)
            _monitoringStatus.value = updated
            Result.success(Unit)
        } catch (e: Exception) {
            val updated = loadConfig(context).copy(isActive = false)
            saveConfig(context, updated)
            _monitoringStatus.value = updated
            Result.failure(e)
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun reregisterFromSavedConfig(context: Context) {
        val config = loadConfig(context)
        if (!config.isActive || config.schoolId.isBlank() || config.trackedStudents.isEmpty()) return
        if (!hasFineLocationPermission(context)) return

        val radius = config.radiusMeters.toFloat().coerceIn(100f, 2000f)
        val geofence = Geofence.Builder()
            .setRequestId(geofenceRequestIdForSchool(config.schoolId))
            .setCircularRegion(config.latitude, config.longitude, radius)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
            .setNotificationResponsiveness(NOTIFICATION_RESPONSIVENESS_MS)
            .build()

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()

        runCatching {
            val client = LocationServices.getGeofencingClient(context.applicationContext)
            client.addGeofences(request, geofencePendingIntent(context.applicationContext)).await()
        }
    }

    fun recordLastEventLocally(context: Context, summary: String) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LAST_EVENT_SUMMARY, summary).apply()
        val loaded = loadConfig(context)
        val effective = if (loaded.isActive && (!hasFineLocationPermission(context) || loaded.trackedStudents.isEmpty())) {
            loaded.copy(isActive = false, lastEventSummary = summary)
        } else {
            loaded.copy(lastEventSummary = summary)
        }
        _monitoringStatus.value = effective
    }

    fun loadConfig(context: Context): GeofenceMonitoringConfig {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_CONFIG_JSON, null)
        val lastSummary = prefs.getString(KEY_LAST_EVENT_SUMMARY, null)
        if (jsonStr.isNullOrBlank()) {
            return GeofenceMonitoringConfig(lastEventSummary = lastSummary)
        }
        return runCatching {
            val obj = JSONObject(jsonStr)
            val studentsArr = obj.optJSONArray("trackedStudents") ?: JSONArray()
            val tracked = mutableListOf<TrackedGeofenceStudent>()
            for (i in 0 until studentsArr.length()) {
                val item = studentsArr.getJSONObject(i)
                tracked += TrackedGeofenceStudent(
                    id = item.getString("id"),
                    fullName = item.getString("fullName"),
                    classId = item.optString("classId", ""),
                    className = item.optString("className", ""),
                    guardianUids = jsonArrayToList(item.optJSONArray("guardianUids")),
                    teacherUids = jsonArrayToList(item.optJSONArray("teacherUids")),
                    adminUids = jsonArrayToList(item.optJSONArray("adminUids"))
                )
            }
            GeofenceMonitoringConfig(
                isActive = obj.optBoolean("isActive", false),
                schoolId = obj.optString("schoolId", ""),
                schoolName = obj.optString("schoolName", ""),
                latitude = obj.optDouble("latitude", 0.0),
                longitude = obj.optDouble("longitude", 0.0),
                radiusMeters = obj.optDouble("radiusMeters", 150.0),
                trackedStudents = tracked,
                lastEventSummary = lastSummary
            )
        }.getOrDefault(GeofenceMonitoringConfig(lastEventSummary = lastSummary))
    }

    fun saveConfig(context: Context, config: GeofenceMonitoringConfig) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val studentsArr = JSONArray()
        config.trackedStudents.forEach { s ->
            studentsArr.put(
                JSONObject().apply {
                    put("id", s.id)
                    put("fullName", s.fullName)
                    put("classId", s.classId)
                    put("className", s.className)
                    put("guardianUids", JSONArray(s.guardianUids))
                    put("teacherUids", JSONArray(s.teacherUids))
                    put("adminUids", JSONArray(s.adminUids))
                }
            )
        }
        val obj = JSONObject().apply {
            put("isActive", config.isActive)
            put("schoolId", config.schoolId)
            put("schoolName", config.schoolName)
            put("latitude", config.latitude)
            put("longitude", config.longitude)
            put("radiusMeters", config.radiusMeters)
            put("trackedStudents", studentsArr)
        }
        prefs.edit().putString(KEY_CONFIG_JSON, obj.toString()).apply()
    }

    fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lng1, lat2, lng2, results)
        return results[0]
    }

    fun formatGeofenceAlert(
        studentName: String,
        schoolName: String,
        isEntering: Boolean,
        timeLabel: String = SimpleDateFormat("HH:mm", Locale.US).format(Date())
    ): Pair<String, String> {
        return if (isEntering) {
            "$studentName Entered School Premises" to
                "$studentName's authorised device entered $schoolName perimeter at $timeLabel. Teacher physical check-in confirms official attendance."
        } else {
            "$studentName Left School Premises" to
                "$studentName's authorised device exited $schoolName perimeter at $timeLabel."
        }
    }

    fun geofencePendingIntent(context: Context): PendingIntent {
        val intent = Intent(context.applicationContext, GeofenceBroadcastReceiver::class.java)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(
            context.applicationContext,
            GEOFENCE_PENDING_INTENT_REQ_CODE,
            intent,
            flags
        )
    }

    private fun jsonArrayToList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return List(arr.length()) { idx -> arr.optString(idx) }.filter { it.isNotBlank() }
    }
}
