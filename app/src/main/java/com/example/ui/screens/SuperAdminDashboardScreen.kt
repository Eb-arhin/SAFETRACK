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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.model.AppDistributionConfig
import com.example.data.model.AuditLog
import com.example.data.model.DeviceViewLayoutMode
import com.example.data.model.School
import com.example.data.model.SubscriptionPlan
import com.example.data.model.UserRole
import com.example.ui.components.AppShareDownloadCard
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.KpiMetricCard
import com.example.ui.theme.SafeBlue700
import com.example.ui.theme.SafeGreen600

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SuperAdminDashboardScreen(
    schools: List<School>,
    plans: List<SubscriptionPlan>,
    auditLogs: List<AuditLog>,
    isBusy: Boolean,
    onSavePlan: (SubscriptionPlan) -> Unit,
    onUpdateSchoolStatus: (School, newStatus: String) -> Unit,
    onInspectSchool: (School) -> Unit,
    appDistributionConfig: AppDistributionConfig = AppDistributionConfig(),
    deviceViewLayoutMode: DeviceViewLayoutMode = DeviceViewLayoutMode.AUTO,
    onOpenAppAccessDialog: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var editingPlan by remember { mutableStateOf<SubscriptionPlan?>(null) }

    val activeSubs = schools.count { it.subscriptionStatus == "active" || it.subscriptionStatus == "trial" }
    val estimatedMrr = schools.filter { it.subscriptionStatus == "active" }.sumOf { it.monthlyPriceGhs }
    val totalPupils = schools.sumOf { it.studentCount }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        item {
            Column(modifier = Modifier.widthIn(max = 900.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Platform Super Admin Console", style = MaterialTheme.typography.headlineSmall)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    maxItemsInEachRow = 4
                ) {
                    KpiMetricCard("Schools", schools.size.toString(), Icons.Filled.Apartment, modifier = Modifier.weight(1f))
                    KpiMetricCard("Active / Trial", activeSubs.toString(), Icons.Filled.CheckCircle, accentColor = SafeGreen600, modifier = Modifier.weight(1f))
                    KpiMetricCard("Est. MRR", "GH₵${estimatedMrr.toInt()}", Icons.Filled.AttachMoney, accentColor = SafeBlue700, modifier = Modifier.weight(1f))
                    KpiMetricCard("Total Pupils", totalPupils.toString(), Icons.Filled.Groups, modifier = Modifier.weight(1f))
                }
                AppShareDownloadCard(
                    config = appDistributionConfig,
                    currentDeviceLayoutMode = deviceViewLayoutMode,
                    canEditAccessSettings = true,
                    onOpenFullDialog = onOpenAppAccessDialog
                )
            }
        }

        item {
            Column(modifier = Modifier.widthIn(max = 900.dp).fillMaxWidth()) {
                Text("Configurable Subscription Plans", style = MaterialTheme.typography.titleLarge)
            }
        }

        items(plans, key = { it.id }) { plan ->
            Card(
                modifier = Modifier.widthIn(max = 900.dp).fillMaxWidth(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${plan.name} — GH₵${plan.monthlyPriceGhs.toInt()}/mo", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Up to ${plan.pupilLimit} pupils • ${plan.annualDiscountPercent}% annual discount • ${plan.trialDays}-day trial",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(onClick = { editingPlan = plan }) {
                        Icon(Icons.Filled.Edit, contentDescription = null)
                        Spacer(Modifier.padding(3.dp))
                        Text("Edit")
                    }
                }
            }
        }

        item {
            Column(modifier = Modifier.widthIn(max = 900.dp).fillMaxWidth()) {
                Text("Registered Tenant Schools (${schools.size})", style = MaterialTheme.typography.titleLarge)
            }
        }

        if (schools.isEmpty()) {
            item {
                Column(modifier = Modifier.widthIn(max = 900.dp)) {
                    EmptyStateCard("No Schools Registered Yet", "New schools registering on SafeTrack appear here with strict tenant isolation.")
                }
            }
        } else {
            items(schools, key = { it.id }) { school ->
                Card(
                    modifier = Modifier.widthIn(max = 900.dp).fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${school.name} (${school.code})", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${school.city}, ${school.country} • ${school.planName} (GH₵${school.monthlyPriceGhs.toInt()}/mo) • ${school.studentCount}/${school.pupilLimit} pupils • ${school.subscriptionStatus.uppercase()}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            OutlinedButton(onClick = { onInspectSchool(school) }) {
                                Text("Open School")
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("trial", "active", "past_due", "suspended").forEach { st ->
                                FilterChip(
                                    selected = school.subscriptionStatus == st,
                                    onClick = { onUpdateSchoolStatus(school, st) },
                                    label = { Text(st.replace('_', ' ').uppercase()) }
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Column(modifier = Modifier.widthIn(max = 900.dp).fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Security, contentDescription = null, tint = SafeBlue700)
                    Text("Platform Audit Activity (${auditLogs.size})", style = MaterialTheme.typography.titleLarge)
                }
            }
        }
        items(auditLogs.take(20), key = { it.id }) { log ->
            Card(modifier = Modifier.widthIn(max = 900.dp).fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("${log.action} • ${log.actorName} (${log.schoolId})", style = MaterialTheme.typography.titleSmall)
                    Text(log.details, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }

    if (editingPlan != null) {
        val plan = editingPlan!!
        var name by remember(plan.id) { mutableStateOf(plan.name) }
        var priceText by remember(plan.id) { mutableStateOf(plan.monthlyPriceGhs.toInt().toString()) }
        var limit by remember(plan.id) { mutableStateOf(plan.pupilLimit.toString()) }
        var discountText by remember(plan.id) { mutableStateOf(plan.annualDiscountPercent.toString()) }
        var trialText by remember(plan.id) { mutableStateOf(plan.trialDays.toString()) }

        AlertDialog(
            onDismissRequest = { editingPlan = null },
            title = { Text("Configure Subscription Tier") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Tier Name") })
                    OutlinedTextField(value = priceText, onValueChange = { priceText = it }, label = { Text("Monthly Price (GH₵)") })
                    OutlinedTextField(value = limit, onValueChange = { limit = it }, label = { Text("Pupil Capacity") })
                    OutlinedTextField(value = discountText, onValueChange = { discountText = it }, label = { Text("Annual Discount (%)") })
                    OutlinedTextField(value = trialText, onValueChange = { trialText = it }, label = { Text("Free Trial Days") })
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSavePlan(
                            plan.copy(
                                name = name.trim(),
                                monthlyPriceGhs = (priceText.toDoubleOrNull() ?: plan.monthlyPriceGhs).coerceAtLeast(0.0),
                                pupilLimit = (limit.toIntOrNull() ?: plan.pupilLimit).coerceAtLeast(1),
                                annualDiscountPercent = (discountText.toIntOrNull() ?: plan.annualDiscountPercent).coerceIn(0, 80),
                                trialDays = (trialText.toIntOrNull() ?: plan.trialDays).coerceIn(0, 90)
                            )
                        )
                        editingPlan = null
                    },
                    enabled = !isBusy && name.trim().isNotEmpty()
                ) { Text("Save Tier") }
            },
            dismissButton = { TextButton(onClick = { editingPlan = null }) { Text("Cancel") } }
        )
    }
}
