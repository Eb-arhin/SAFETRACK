package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AppNotification
import com.example.ui.components.EmptyStateCard
import com.example.ui.theme.EmergencyRed600
import com.example.ui.theme.SafeBlue700
import com.example.ui.theme.SafeGreen600
import com.example.ui.theme.SafeTeal600

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NotificationsScreen(
    notifications: List<AppNotification>,
    onMarkNotificationRead: (String) -> Unit,
    onOpenNotificationPreferences: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFilter by remember { mutableStateOf("all") }

    val filtered = remember(notifications, selectedFilter) {
        when (selectedFilter) {
            "unread" -> notifications.filter { !it.isRead }
            "movement" -> notifications.filter {
                it.type in listOf("journey", "journey_started", "dismissed", "late_departure", "geofence_enter", "geofence_exit")
            }
            "attendance" -> notifications.filter { it.type == "attendance" }
            "safety" -> notifications.filter { it.type in listOf("emergency", "incident") }
            else -> notifications
        }
    }
    val unreadCount = notifications.count { !it.isRead }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        item {
            Column(modifier = Modifier.widthIn(max = 920.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Notifications Centre", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            "$unreadCount unread alert(s) • ${notifications.size} total",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (unreadCount > 0) {
                            OutlinedButton(
                                onClick = {
                                    notifications.filter { !it.isRead }.forEach { onMarkNotificationRead(it.id) }
                                },
                                modifier = Modifier.testTag("mark_all_notifications_read_button")
                            ) {
                                Icon(Icons.Filled.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.size(4.dp))
                                Text("Mark All Read")
                            }
                        }
                        OutlinedButton(
                            onClick = onOpenNotificationPreferences,
                            modifier = Modifier.testTag("open_notification_prefs_button")
                        ) {
                            Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(4.dp))
                            Text("Preferences")
                        }
                    }
                }

                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        "all" to "All (${notifications.size})",
                        "unread" to "Unread ($unreadCount)",
                        "movement" to "Movement & Geofence",
                        "attendance" to "Attendance",
                        "safety" to "Incidents & Safety"
                    ).forEach { (key, label) ->
                        FilterChip(
                            selected = selectedFilter == key,
                            onClick = { selectedFilter = key },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }

        if (filtered.isEmpty()) {
            item {
                Column(modifier = Modifier.widthIn(max = 920.dp)) {
                    EmptyStateCard(
                        "All Caught Up",
                        "Attendance confirmations, child arrivals/departures, geofence events, and incident alerts appear here."
                    )
                }
            }
        } else {
            items(filtered, key = { it.id }) { ntf ->
                val (icon, tint) = when {
                    ntf.type == "geofence_enter" || ntf.type == "geofence_exit" -> Icons.Filled.Radar to SafeTeal600
                    ntf.type == "emergency" || ntf.type == "incident" -> Icons.Filled.Warning to EmergencyRed600
                    ntf.type == "attendance" -> Icons.Filled.FactCheck to SafeGreen600
                    ntf.type in listOf("journey", "journey_started", "dismissed", "late_departure") -> Icons.AutoMirrored.Filled.DirectionsWalk to SafeBlue700
                    else -> Icons.Filled.Notifications to MaterialTheme.colorScheme.primary
                }
                Card(
                    modifier = Modifier.widthIn(max = 920.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (ntf.isRead) {
                            MaterialTheme.colorScheme.surface
                        } else {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
                        }
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = tint.copy(alpha = 0.14f)
                        ) {
                            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.padding(8.dp).size(20.dp))
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(ntf.title, style = MaterialTheme.typography.titleSmall, fontWeight = if (ntf.isRead) FontWeight.Medium else FontWeight.Bold)
                            Text(ntf.body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (!ntf.isRead) {
                            TextButton(onClick = { onMarkNotificationRead(ntf.id) }) {
                                Icon(Icons.Filled.MarkEmailRead, contentDescription = "Mark read", modifier = Modifier.size(16.dp))
                                Spacer(Modifier.size(4.dp))
                                Text("Read")
                            }
                        } else {
                            Icon(Icons.Filled.CheckCircle, contentDescription = "Read", tint = SafeGreen600, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}
