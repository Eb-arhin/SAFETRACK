package com.example.ui.components

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.LocationSearching
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.School
import com.example.data.model.Student
import com.example.ui.theme.DelayAmber100
import com.example.ui.theme.DelayAmber700
import com.example.ui.theme.ReportGold
import com.example.ui.theme.SafeBlue100
import com.example.ui.theme.SafeBlue700
import com.example.ui.theme.SafeGreen100
import com.example.ui.theme.SafeGreen600
import com.example.ui.theme.SafeGreen700
import com.example.ui.theme.SafeTeal600
import com.example.util.GeofenceManager
import com.example.util.GeofenceMonitoringConfig
import com.example.util.NotificationHelper

@Composable
fun InteractiveGeofenceMapPreview(
    schoolName: String,
    latitude: Double?,
    longitude: Double?,
    radiusMeters: Double,
    enabled: Boolean,
    accuracyMeters: Float? = null,
    hasPermission: Boolean = true,
    modifier: Modifier = Modifier
) {
    val activeColor = if (enabled) SafeGreen600 else MaterialTheme.colorScheme.outline
    val ringFill = if (enabled) SafeTeal600.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
    val maxScaleMeters = 550.0
    val normalizedRatio = (radiusMeters.coerceIn(100.0, 500.0) / maxScaleMeters).toFloat()

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Text(
                        text = if (latitude != null && longitude != null) {
                            "${schoolName.ifBlank { "Campus" }} • ${"%.5f".format(latitude)}, ${"%.5f".format(longitude)}"
                        } else {
                            "Set coordinates or tap Use Current GPS"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (hasPermission) SafeGreen100 else DelayAmber100
                ) {
                    Text(
                        text = buildString {
                            append(if (hasPermission) "GPS Permitted" else "Permission Needed")
                            if (accuracyMeters != null) append(" • ±${accuracyMeters.toInt()}m")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (hasPermission) SafeGreen700 else DelayAmber700,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(175.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val center = Offset(w / 2f, h / 2f)
                    val maxRadiusPx = minOf(w, h) * 0.44f

                    // Subtle coordinate grid
                    val step = minOf(w, h) / 6f
                    var x = step
                    while (x < w) {
                        drawLine(gridColor, Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
                        x += step
                    }
                    var y = step
                    while (y < h) {
                        drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
                        y += step
                    }

                    // Reference concentric scale rings (100m, 250m, 500m)
                    listOf(100.0, 250.0, 500.0).forEach { refMeters ->
                        val refPx = maxRadiusPx * (refMeters / maxScaleMeters).toFloat()
                        drawCircle(
                            color = gridColor.copy(alpha = 0.35f),
                            radius = refPx,
                            center = center,
                            style = Stroke(
                                width = 1.5f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                            )
                        )
                    }

                    // Active circular geofence boundary
                    val activeRadiusPx = maxRadiusPx * normalizedRatio
                    drawCircle(color = ringFill, radius = activeRadiusPx, center = center)
                    drawCircle(
                        color = activeColor,
                        radius = activeRadiusPx,
                        center = center,
                        style = Stroke(width = 3.5f)
                    )

                    // Campus center marker
                    drawCircle(color = Color.White, radius = 14f, center = center)
                    drawCircle(color = SafeBlue700, radius = 10f, center = center)
                    drawCircle(color = ReportGold, radius = 4f, center = center)
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    border = BorderStroke(1.dp, activeColor.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                ) {
                    Text(
                        text = if (enabled) "Perimeter Radius: ${radiusMeters.toInt()}m (Active)" else "Perimeter Radius: ${radiusMeters.toInt()}m (Paused)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    Text(
                        text = "N ↑  100m–500m Scale",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SchoolGeofenceConfigCard(
    school: School,
    isBusy: Boolean,
    lastGpsAccuracyMeters: Float? = null,
    onCaptureCurrentGps: ((Double, Double) -> Unit) -> Unit,
    onSaveGeofence: (latitude: Double, longitude: Double, radiusMeters: Double, enabled: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var latText by remember(school.id, school.geofenceLatitude) {
        mutableStateOf(school.geofenceLatitude?.let { "%.6f".format(it) } ?: "")
    }
    var lngText by remember(school.id, school.geofenceLongitude) {
        mutableStateOf(school.geofenceLongitude?.let { "%.6f".format(it) } ?: "")
    }
    var radiusMeters by remember(school.id, school.geofenceRadiusMeters) {
        mutableDoubleStateOf(school.geofenceRadiusMeters.coerceIn(100.0, 2000.0))
    }
    var enabled by remember(school.id, school.geofenceEnabled) {
        mutableStateOf(school.geofenceEnabled)
    }

    val fgLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            onCaptureCurrentGps { lat, lng ->
                latText = "%.6f".format(lat)
                lngText = "%.6f".format(lng)
                enabled = true
            }
        }
    }

    val parsedLat = latText.trim().toDoubleOrNull()
    val parsedLng = lngText.trim().toDoubleOrNull()
    val validCoords = parsedLat != null && parsedLng != null && parsedLat in -90.0..90.0 && parsedLng in -180.0..180.0
    val hasPerm = GeofenceManager.hasForegroundLocationPermission(context)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.Radar, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text("School Premises Geofence", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Circular GPS boundary for automated parent entry & exit push alerts",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }

            InteractiveGeofenceMapPreview(
                schoolName = school.name,
                latitude = parsedLat,
                longitude = parsedLng,
                radiusMeters = radiusMeters,
                enabled = enabled,
                accuracyMeters = lastGpsAccuracyMeters,
                hasPermission = hasPerm
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = latText,
                    onValueChange = { latText = it },
                    label = { Text("Latitude") },
                    placeholder = { Text("5.603717") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("geofence_lat_input")
                )
                OutlinedTextField(
                    value = lngText,
                    onValueChange = { lngText = it },
                    label = { Text("Longitude") },
                    placeholder = { Text("-0.186964") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("geofence_lng_input")
                )
            }

            Text("Boundary Radius: ${radiusMeters.toInt()} meters", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(100.0, 150.0, 250.0, 500.0).forEach { r ->
                    FilterChip(
                        selected = radiusMeters.toInt() == r.toInt(),
                        onClick = { radiusMeters = r },
                        label = { Text("${r.toInt()}m") }
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = {
                        if (!GeofenceManager.hasForegroundLocationPermission(context)) {
                            fgLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        } else {
                            onCaptureCurrentGps { lat, lng ->
                                latText = "%.6f".format(lat)
                                lngText = "%.6f".format(lng)
                                enabled = true
                            }
                        }
                    },
                    enabled = !isBusy,
                    modifier = Modifier.weight(1f).testTag("capture_school_gps_button")
                ) {
                    Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Detect GPS")
                }

                Button(
                    onClick = {
                        if (parsedLat != null && parsedLng != null) {
                            onSaveGeofence(parsedLat, parsedLng, radiusMeters, enabled)
                        }
                    },
                    enabled = !isBusy && validCoords,
                    modifier = Modifier.weight(1f).testTag("save_school_geofence_button")
                ) {
                    Text("Save & Update")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeviceGeofenceMonitorCard(
    school: School?,
    students: List<Student>,
    geofenceStatus: GeofenceMonitoringConfig,
    perimeterCheckStatus: String?,
    isBusy: Boolean,
    lastGpsAccuracyMeters: Float? = null,
    onEnableMonitoring: (List<Student>) -> Unit,
    onDisableMonitoring: () -> Unit,
    onCheckPerimeterNow: (School) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedIds by remember(students) {
        mutableStateOf(
            geofenceStatus.trackedStudents.map { it.id }.toSet()
                .ifEmpty { students.firstOrNull()?.let { setOf(it.id) } ?: emptySet() }
        )
    }
    var showBackgroundDisclosure by remember { mutableStateOf(false) }

    val bgPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        val target = students.filter { it.id in selectedIds }.ifEmpty { students.take(1) }
        if (GeofenceManager.hasFineLocationPermission(context)) {
            onEnableMonitoring(target)
        }
    }

    val fgAndNotifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (GeofenceManager.hasFineLocationPermission(context)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !GeofenceManager.hasBackgroundLocationPermission(context)) {
                showBackgroundDisclosure = true
            } else {
                val target = students.filter { it.id in selectedIds }.ifEmpty { students.take(1) }
                onEnableMonitoring(target)
            }
        }
    }

    val schoolConfigured = school != null &&
        school.geofenceEnabled &&
        school.geofenceLatitude != null &&
        school.geofenceLongitude != null
    val hasFinePerm = GeofenceManager.hasFineLocationPermission(context)
    val hasBgPerm = GeofenceManager.hasBackgroundLocationPermission(context)
    val trulyActive = geofenceStatus.isActive && hasFinePerm && geofenceStatus.trackedStudents.isNotEmpty()
    val permissionRevoked = geofenceStatus.isActive && !hasFinePerm

    Card(
        modifier = modifier.fillMaxWidth().testTag("device_geofence_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (trulyActive) {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
            }
        ),
        border = BorderStroke(
            1.dp,
            if (trulyActive) SafeGreen600.copy(alpha = 0.4f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = when {
                            trulyActive -> Icons.Filled.GpsFixed
                            permissionRevoked -> Icons.Filled.Warning
                            else -> Icons.Filled.LocationSearching
                        },
                        contentDescription = null,
                        tint = when {
                            trulyActive -> SafeGreen700
                            permissionRevoked -> DelayAmber700
                            else -> MaterialTheme.colorScheme.primary
                        }
                    )
                    Column {
                        Text(
                            text = "Play Services School Geofencing",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = when {
                                permissionRevoked -> "Device location permission required to resume monitoring"
                                schoolConfigured -> "${school!!.name} • ${school.geofenceRadiusMeters.toInt()}m perimeter"
                                else -> "Awaiting School Admin campus coordinate setup"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (trulyActive) SafeGreen100 else DelayAmber100
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = when {
                                trulyActive -> Icons.Filled.GpsFixed
                                permissionRevoked -> Icons.Filled.Warning
                                else -> Icons.Filled.GpsOff
                            },
                            contentDescription = null,
                            tint = if (trulyActive) SafeGreen700 else DelayAmber700,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = when {
                                trulyActive && hasBgPerm -> "Active (BG)"
                                trulyActive -> "Foreground"
                                permissionRevoked -> "Permission"
                                else -> "Off"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (trulyActive) SafeGreen700 else DelayAmber700,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            if (school != null && school.geofenceLatitude != null && school.geofenceLongitude != null) {
                InteractiveGeofenceMapPreview(
                    schoolName = school.name,
                    latitude = school.geofenceLatitude,
                    longitude = school.geofenceLongitude,
                    radiusMeters = school.geofenceRadiusMeters,
                    enabled = trulyActive || school.geofenceEnabled,
                    accuracyMeters = lastGpsAccuracyMeters,
                    hasPermission = hasFinePerm
                )
            }

            Text(
                text = "Uses Google Play Services Location API to detect when the authorised carrying device enters or leaves the school premises and automatically sends push notifications to parents.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (students.isNotEmpty()) {
                Text("Monitored Pupil(s) on This Device", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    students.forEach { stu ->
                        val checked = stu.id in selectedIds
                        FilterChip(
                            selected = checked,
                            onClick = {
                                selectedIds = if (checked && selectedIds.size > 1) {
                                    selectedIds - stu.id
                                } else {
                                    selectedIds + stu.id
                                }
                            },
                            label = { Text(stu.fullName) }
                        )
                    }
                }
            }

            if (!geofenceStatus.lastEventSummary.isNullOrBlank()) {
                Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surface) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Text(
                            text = "Last Geofence Event: ${geofenceStatus.lastEventSummary}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            if (!perimeterCheckStatus.isNullOrBlank()) {
                Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surface) {
                    Text(
                        text = perimeterCheckStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth().padding(10.dp)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (trulyActive) {
                    OutlinedButton(
                        onClick = onDisableMonitoring,
                        enabled = !isBusy,
                        modifier = Modifier.weight(1f).testTag("disable_geofence_button")
                    ) {
                        Text("Stop Geofence")
                    }
                } else {
                    Button(
                        onClick = {
                            val target = students.filter { it.id in selectedIds }.ifEmpty { students.take(1) }
                            val needsNotif = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                !NotificationHelper.hasNotificationPermission(context)
                            if (!GeofenceManager.hasFineLocationPermission(context) || needsNotif) {
                                val perms = buildList {
                                    add(Manifest.permission.ACCESS_FINE_LOCATION)
                                    add(Manifest.permission.ACCESS_COARSE_LOCATION)
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        add(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                }.toTypedArray()
                                fgAndNotifLauncher.launch(perms)
                            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                                !GeofenceManager.hasBackgroundLocationPermission(context)
                            ) {
                                showBackgroundDisclosure = true
                            } else {
                                onEnableMonitoring(target)
                            }
                        },
                        enabled = !isBusy && schoolConfigured && students.isNotEmpty(),
                        modifier = Modifier.weight(1f).testTag("enable_geofence_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = SafeBlue700)
                    ) {
                        Icon(Icons.Filled.Shield, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Enable Geofence")
                    }
                }

                if (schoolConfigured && school != null) {
                    OutlinedButton(
                        onClick = { onCheckPerimeterNow(school) },
                        enabled = !isBusy,
                        modifier = Modifier.weight(1f).testTag("check_perimeter_button")
                    ) {
                        Text("Check Distance")
                    }
                }
            }
        }
    }

    if (showBackgroundDisclosure) {
        AlertDialog(
            onDismissRequest = { showBackgroundDisclosure = false },
            icon = { Icon(Icons.Filled.Shield, contentDescription = null, tint = SafeBlue700) },
            title = { Text("Background Location Disclosure") },
            text = {
                Text(
                    "SafeTrack uses background location access to monitor the configured school premises geofence so parents receive instant push notifications when a student's authorised device enters or leaves school, even when the screen is locked or the app is closed. Location monitoring stops immediately if you disable geofencing or sign out."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showBackgroundDisclosure = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            bgPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        } else {
                            val target = students.filter { it.id in selectedIds }.ifEmpty { students.take(1) }
                            onEnableMonitoring(target)
                        }
                    }
                ) {
                    Text("Allow All The Time")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showBackgroundDisclosure = false
                        val target = students.filter { it.id in selectedIds }.ifEmpty { students.take(1) }
                        onEnableMonitoring(target)
                    }
                ) {
                    Text("Foreground Only")
                }
            }
        )
    }
}
