package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.DeviceViewLayoutMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "cefelix_safetrack_settings"
)

enum class AppThemeMode(val key: String, val label: String) {
    SYSTEM("system", "System Default"),
    LIGHT("light", "Light Mode"),
    DARK("dark", "Dark Mode");

    companion object {
        fun fromKey(key: String?): AppThemeMode =
            entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

enum class AccentPalette(val key: String, val label: String) {
    NAVY("navy", "Cefelix Navy"),
    COBALT("cobalt", "Royal Cobalt"),
    TEAL("teal", "Calm Teal"),
    EMERALD("emerald", "Campus Emerald");

    companion object {
        fun fromKey(key: String?): AccentPalette =
            entries.firstOrNull { it.key == key } ?: NAVY
    }
}

data class AppSettingsState(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val accentPalette: AccentPalette = AccentPalette.NAVY,
    val fontScale: Float = 1.0f,
    val compactCards: Boolean = false,
    val showDashboardActivities: Boolean = true,
    val showDashboardGeofence: Boolean = true,
    val deviceLayoutMode: DeviceViewLayoutMode = DeviceViewLayoutMode.AUTO,
    val notifyAttendance: Boolean = true,
    val notifyMovement: Boolean = true,
    val notifyGeofence: Boolean = true,
    val notifyIncidents: Boolean = true,
    val notifyAcademics: Boolean = true,
    val notifyComms: Boolean = true,
    val classAssessmentWeightPercent: Int = 50
)

class AppSettingsStore(context: Context) {
    private val dataStore = context.applicationContext.appSettingsDataStore

    val settingsFlow: Flow<AppSettingsState> = dataStore.data.map { prefs ->
        AppSettingsState(
            themeMode = AppThemeMode.fromKey(prefs[KEY_THEME_MODE]),
            accentPalette = AccentPalette.fromKey(prefs[KEY_ACCENT_PALETTE]),
            fontScale = (prefs[KEY_FONT_SCALE] ?: 1.0f).coerceIn(0.85f, 1.25f),
            compactCards = prefs[KEY_COMPACT_CARDS] ?: false,
            showDashboardActivities = prefs[KEY_SHOW_ACTIVITIES] ?: true,
            showDashboardGeofence = prefs[KEY_SHOW_GEOFENCE] ?: true,
            deviceLayoutMode = DeviceViewLayoutMode.fromKey(prefs[KEY_LAYOUT_MODE] ?: DeviceViewLayoutMode.AUTO.key),
            notifyAttendance = prefs[KEY_NOTIFY_ATTENDANCE] ?: true,
            notifyMovement = prefs[KEY_NOTIFY_MOVEMENT] ?: true,
            notifyGeofence = prefs[KEY_NOTIFY_GEOFENCE] ?: true,
            notifyIncidents = prefs[KEY_NOTIFY_INCIDENTS] ?: true,
            notifyAcademics = prefs[KEY_NOTIFY_ACADEMICS] ?: true,
            notifyComms = prefs[KEY_NOTIFY_COMMS] ?: true,
            classAssessmentWeightPercent = (prefs[KEY_CLASS_WEIGHT] ?: 50).coerceIn(20, 80)
        )
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        dataStore.edit { it[KEY_THEME_MODE] = mode.key }
    }

    suspend fun setAccentPalette(palette: AccentPalette) {
        dataStore.edit { it[KEY_ACCENT_PALETTE] = palette.key }
    }

    suspend fun setFontScale(scale: Float) {
        dataStore.edit { it[KEY_FONT_SCALE] = scale.coerceIn(0.85f, 1.25f) }
    }

    suspend fun setCompactCards(compact: Boolean) {
        dataStore.edit { it[KEY_COMPACT_CARDS] = compact }
    }

    suspend fun setShowDashboardActivities(show: Boolean) {
        dataStore.edit { it[KEY_SHOW_ACTIVITIES] = show }
    }

    suspend fun setShowDashboardGeofence(show: Boolean) {
        dataStore.edit { it[KEY_SHOW_GEOFENCE] = show }
    }

    suspend fun setDeviceLayoutMode(mode: DeviceViewLayoutMode) {
        dataStore.edit { it[KEY_LAYOUT_MODE] = mode.key }
    }

    suspend fun setNotificationToggle(category: String, enabled: Boolean) {
        val key = when (category) {
            "attendance" -> KEY_NOTIFY_ATTENDANCE
            "movement" -> KEY_NOTIFY_MOVEMENT
            "geofence" -> KEY_NOTIFY_GEOFENCE
            "incidents" -> KEY_NOTIFY_INCIDENTS
            "academics" -> KEY_NOTIFY_ACADEMICS
            else -> KEY_NOTIFY_COMMS
        }
        dataStore.edit { it[key] = enabled }
    }

    suspend fun setClassAssessmentWeightPercent(percent: Int) {
        dataStore.edit { it[KEY_CLASS_WEIGHT] = percent.coerceIn(20, 80) }
    }

    suspend fun restoreDefaults() {
        dataStore.edit { it.clear() }
    }

    companion object {
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_ACCENT_PALETTE = stringPreferencesKey("accent_palette")
        private val KEY_FONT_SCALE = floatPreferencesKey("font_scale")
        private val KEY_COMPACT_CARDS = booleanPreferencesKey("compact_cards")
        private val KEY_SHOW_ACTIVITIES = booleanPreferencesKey("show_dashboard_activities")
        private val KEY_SHOW_GEOFENCE = booleanPreferencesKey("show_dashboard_geofence")
        private val KEY_LAYOUT_MODE = stringPreferencesKey("device_layout_mode")
        private val KEY_NOTIFY_ATTENDANCE = booleanPreferencesKey("notify_attendance")
        private val KEY_NOTIFY_MOVEMENT = booleanPreferencesKey("notify_movement")
        private val KEY_NOTIFY_GEOFENCE = booleanPreferencesKey("notify_geofence")
        private val KEY_NOTIFY_INCIDENTS = booleanPreferencesKey("notify_incidents")
        private val KEY_NOTIFY_ACADEMICS = booleanPreferencesKey("notify_academics")
        private val KEY_NOTIFY_COMMS = booleanPreferencesKey("notify_comms")
        private val KEY_CLASS_WEIGHT = intPreferencesKey("class_assessment_weight")
    }
}
