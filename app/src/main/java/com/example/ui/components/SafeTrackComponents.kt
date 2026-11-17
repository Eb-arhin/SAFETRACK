package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.AttendanceStatus
import com.example.data.model.Journey
import com.example.data.model.MovementStatus
import com.example.data.model.School
import com.example.ui.theme.DelayAmber100
import com.example.ui.theme.DelayAmber700
import com.example.ui.theme.EmergencyRed100
import com.example.ui.theme.EmergencyRed600
import com.example.ui.theme.EmergencyRed700
import com.example.ui.theme.ReportGold
import com.example.ui.theme.ReportNavy
import com.example.ui.theme.SafeBlue100
import com.example.ui.theme.SafeBlue700
import com.example.ui.theme.SafeBlue900
import com.example.ui.theme.SafeGreen100
import com.example.ui.theme.SafeGreen700
import com.example.util.LocationTracker
import com.example.util.QrCodeGenerator

@Composable
fun SchoolBrandCrest(
    school: School?,
    size: Dp = 44.dp,
    modifier: Modifier = Modifier
) {
    val initials = remember(school?.name) {
        val parts = school?.name?.trim()?.split(Regex("\\s+"))?.filter { it.isNotBlank() }.orEmpty()
        when {
            parts.size >= 2 -> "${parts[0].first().uppercaseChar()}${parts[1].first().uppercaseChar()}"
            parts.size == 1 -> parts[0].take(2).uppercase()
            else -> "CA"
        }
    }
    Surface(
        modifier = modifier.size(size),
        shape = RoundedCornerShape(12.dp),
        color = ReportNavy,
        border = BorderStroke(1.5.dp, ReportGold)
    ) {
        if (!school?.logoUri.isNullOrBlank()) {
            AsyncImage(
                model = school!!.logoUri,
                contentDescription = "${school.name} logo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = initials,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = ReportGold
                )
            }
        }
    }
}

@Composable
fun QrCodePreviewBox(
    content: String,
    modifier: Modifier = Modifier,
    size: Dp = 180.dp
) {
    val matrix = remember(content) {
        runCatching { QrCodeGenerator.encode(content) }.getOrNull()
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        modifier = modifier.size(size)
    ) {
        if (matrix == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "QR Ready on Valid URL",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.DarkGray
                )
            }
        } else {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
                    .aspectRatio(1f)
            ) {
                val cell = this.size.width / matrix.size.toFloat()
                drawRect(color = Color.White, size = this.size)
                for (r in 0 until matrix.size) {
                    for (c in 0 until matrix.size) {
                        if (matrix.get(r, c)) {
                            drawRect(
                                color = Color(0xFF0A2540),
                                topLeft = Offset(c * cell, r * cell),
                                size = Size(cell + 0.4f, cell + 0.4f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun KpiMetricCard(
    label: String,
    value: String,
    icon: ImageVector,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = accentColor.copy(alpha = 0.14f)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = accentColor,
                        modifier = Modifier
                            .padding(7.dp)
                            .size(18.dp)
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun MovementStatusBadge(statusKey: String) {
    val status = MovementStatus.fromKey(statusKey)
    val (bg, fg, icon) = when (status) {
        MovementStatus.ARRIVED_AT_SCHOOL,
        MovementStatus.ARRIVED_HOME,
        MovementStatus.AT_APPROVED_ALTERNATIVE_DESTINATION -> Triple(SafeGreen100, SafeGreen700, Icons.Filled.CheckCircle)
        MovementStatus.ON_THE_WAY_TO_SCHOOL,
        MovementStatus.ON_THE_WAY_HOME,
        MovementStatus.PREPARING_FOR_SCHOOL -> Triple(SafeBlue100, SafeBlue900, Icons.Filled.DirectionsBus)
        MovementStatus.JOURNEY_DELAYED -> Triple(DelayAmber100, DelayAmber700, Icons.Filled.Schedule)
        MovementStatus.EMERGENCY_REPORTED -> Triple(EmergencyRed100, EmergencyRed700, Icons.Filled.Warning)
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = bg
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = status.label,
                style = MaterialTheme.typography.labelMedium,
                color = fg,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun AttendanceStatusBadge(statusKey: String) {
    val status = AttendanceStatus.fromKey(statusKey)
    val (bg, fg, icon) = when (status) {
        AttendanceStatus.PRESENT -> Triple(SafeGreen100, SafeGreen700, Icons.Filled.CheckCircle)
        AttendanceStatus.LATE, AttendanceStatus.PENDING_CONFIRMATION -> Triple(DelayAmber100, DelayAmber700, Icons.Filled.Schedule)
        AttendanceStatus.EXCUSED_ABSENCE, AttendanceStatus.EARLY_DEPARTURE -> Triple(SafeBlue100, SafeBlue900, Icons.Filled.Info)
        AttendanceStatus.ABSENT -> Triple(EmergencyRed100, EmergencyRed700, Icons.Filled.Cancel)
    }
    val displayLabel = if (status == AttendanceStatus.LATE) "Tardy (Late)" else status.label
    Surface(
        shape = RoundedCornerShape(50),
        color = bg
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = displayLabel,
                style = MaterialTheme.typography.labelMedium,
                color = fg,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun JourneyLocationCard(journey: Journey) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    imageVector = if (journey.isTrackingActive && journey.gpsAvailable) Icons.Filled.GpsFixed else Icons.Filled.GpsOff,
                    contentDescription = "GPS status",
                    tint = if (journey.isTrackingActive && journey.gpsAvailable) SafeGreen700 else DelayAmber700,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = when {
                        !journey.isTrackingActive -> "Tracking Stopped (Journey Completed/Inactive)"
                        journey.gpsAvailable -> "Live Device GPS Active"
                        else -> "GPS Unavailable — Manual Journey Updates Active"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (journey.isTrackingActive && journey.liveLatitude != null && journey.liveLongitude != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Text(
                        text = "Live Location: ${"%.5f".format(journey.liveLatitude)}, ${"%.5f".format(journey.liveLongitude)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else if (journey.lastKnownLatitude != null && journey.lastKnownLongitude != null) {
                Text(
                    text = "Last Known Location: ${"%.5f".format(journey.lastKnownLatitude)}, ${"%.5f".format(journey.lastKnownLongitude)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "No GPS coordinates recorded for this journey.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!journey.manualNote.isNullOrBlank()) {
                Text(
                    text = "Manual note: ${journey.manualNote}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                Text(
                    text = "GPS tracks the authorised device only and does not prove a child's physical presence or identity.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun EmptyStateCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_empty_journey),
                contentDescription = title,
                modifier = Modifier
                    .height(110.dp)
                    .clip(RoundedCornerShape(14.dp)),
                contentScale = ContentScale.Fit
            )
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyReportDialog(
    studentName: String,
    onDismiss: () -> Unit,
    onSubmit: (severity: String, title: String, description: String) -> Unit
) {
    var severity by remember { mutableStateOf("high") }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Warning, contentDescription = null, tint = EmergencyRed600) },
        title = { Text("Report Safety Incident — $studentName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    OutlinedTextField(
                        value = severity.uppercase(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Severity") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        listOf("low", "medium", "high", "critical").forEach { level ->
                            DropdownMenuItem(
                                text = { Text(level.uppercase()) },
                                onClick = {
                                    severity = level
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Incident Summary") },
                    placeholder = { Text("e.g., Bus delayed at Junction / Pickup verification needed") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Details & Action Taken") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(severity, title, description) },
                enabled = title.trim().length >= 2 && description.trim().length >= 2,
                colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed600)
            ) {
                Text("Send Alert")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun LocationConsentBanner(
    consentGranted: Boolean,
    onToggleConsent: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val hasOsPerm = LocationTracker.hasLocationPermission(context)
    val trulyActive = consentGranted && hasOsPerm

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                trulyActive -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                consentGranted && !hasOsPerm -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f)
                else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = when {
                        trulyActive -> Icons.Filled.GpsFixed
                        consentGranted && !hasOsPerm -> Icons.Filled.Warning
                        else -> Icons.Filled.Shield
                    },
                    contentDescription = null,
                    tint = when {
                        trulyActive -> SafeGreen700
                        consentGranted && !hasOsPerm -> DelayAmber700
                        else -> MaterialTheme.colorScheme.primary
                    }
                )
                Column {
                    Text(
                        text = when {
                            trulyActive -> "GPS Sharing Active (Consent & Permission Granted)"
                            consentGranted && !hasOsPerm -> "GPS Consent Enabled — Android Location Permission Missing"
                            else -> "GPS Consent Off — Manual Updates Active"
                        },
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = if (consentGranted && !hasOsPerm) {
                            "Grant device location permission before starting a tracked journey, otherwise SafeTrack uses manual status updates."
                        } else {
                            "Foreground location is shared strictly during active authorised journeys and stops immediately on arrival."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = consentGranted,
                onCheckedChange = onToggleConsent
            )
        }
    }
}
