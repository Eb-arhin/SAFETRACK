package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AppDistributionConfig
import com.example.data.model.DeviceViewLayoutMode
import com.example.data.model.InviteInspectionResult
import com.example.data.model.InviteInspectionStatus
import com.example.data.model.SchoolInvite
import com.example.data.model.SubscriptionPlan
import com.example.data.model.UserRole
import com.example.ui.components.AppShareDownloadCard
import com.example.ui.theme.DelayAmber100
import com.example.ui.theme.DelayAmber700
import com.example.ui.theme.EmergencyRed100
import com.example.ui.theme.EmergencyRed700
import com.example.ui.theme.SafeBlue100
import com.example.ui.theme.SafeBlue700
import com.example.ui.theme.SafeGreen100
import com.example.ui.theme.SafeGreen700

@Composable
fun OnboardingScreen(
    plans: List<SubscriptionPlan>,
    pendingInvites: List<SchoolInvite>,
    isSuperAdmin: Boolean,
    isBusy: Boolean,
    initialInviteCode: String = "",
    inviteInspectionResult: InviteInspectionResult? = null,
    appDistributionConfig: AppDistributionConfig = AppDistributionConfig(),
    deviceViewLayoutMode: DeviceViewLayoutMode = DeviceViewLayoutMode.AUTO,
    onOpenShareDialog: () -> Unit = {},
    onDismissInviteInspection: () -> Unit = {},
    onRegisterSchool: (
        name: String,
        city: String,
        country: String,
        address: String,
        phone: String,
        email: String,
        plan: SubscriptionPlan,
        billingCycle: String
    ) -> Unit,
    onClaimInvite: (String) -> Unit,
    onOpenSuperAdmin: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var inviteCode by remember { mutableStateOf(initialInviteCode) }

    LaunchedEffect(initialInviteCode) {
        if (initialInviteCode.isNotBlank()) {
            inviteCode = initialInviteCode.trim().uppercase()
            selectedTab = 0
        }
    }

    var schoolName by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("Accra") }
    var country by remember { mutableStateOf("Ghana") }
    var address by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var billingCycle by remember { mutableStateOf("monthly") }
    var selectedPlanId by remember(plans) { mutableStateOf(plans.firstOrNull()?.id ?: "starter") }
    val selectedPlan = plans.firstOrNull { it.id == selectedPlanId } ?: plans.first()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.widthIn(max = 680.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isSuperAdmin) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SafeBlue100.copy(alpha = 0.65f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Super Admin Account Verified", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Manage registered schools, subscription plans, and platform audit logs.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Button(
                            onClick = onOpenSuperAdmin,
                            modifier = Modifier.testTag("open_super_admin_button")
                        ) {
                            Icon(Icons.Filled.AdminPanelSettings, contentDescription = null)
                            Spacer(Modifier.padding(4.dp))
                            Text("Console")
                        }
                    }
                }
            }

            if (pendingInvites.isNotEmpty()) {
                Card(colors = CardDefaults.cardColors(containerColor = SafeGreen100.copy(alpha = 0.6f))) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Pending School Invitations", style = MaterialTheme.typography.titleMedium)
                        pendingInvites.forEach { inv ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(inv.schoolName, style = MaterialTheme.typography.titleSmall)
                                    Text("Role: ${UserRole.fromKey(inv.role).label} • Code: ${inv.inviteCode}", style = MaterialTheme.typography.bodySmall)
                                }
                                Button(onClick = { onClaimInvite(inv.inviteCode) }, enabled = !isBusy) {
                                    Text("Join")
                                }
                            }
                        }
                    }
                }
            }

            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Join via Invite") },
                    icon = { Icon(Icons.Filled.QrCode, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Register School") },
                    icon = { Icon(Icons.Filled.Apartment, contentDescription = null) }
                )
            }

            if (selectedTab == 0) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Join Your School Workspace", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Enter the invite code issued by your School Administrator (Teacher, Parent/Guardian, or Authorised Pickup).",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (inviteInspectionResult != null) {
                            val isValid = inviteInspectionResult.status == InviteInspectionStatus.VALID_PENDING
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isValid) SafeGreen100 else if (inviteInspectionResult.status == InviteInspectionStatus.EMAIL_MISMATCH_OR_RESTRICTED) DelayAmber100 else EmergencyRed100
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isValid) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                                            contentDescription = null,
                                            tint = if (isValid) SafeGreen700 else if (inviteInspectionResult.status == InviteInspectionStatus.EMAIL_MISMATCH_OR_RESTRICTED) DelayAmber700 else EmergencyRed700
                                        )
                                        Text(
                                            text = if (isValid) "Valid School Invite" else "Invite Issue — Action Required",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = if (isValid) SafeGreen700 else if (inviteInspectionResult.status == InviteInspectionStatus.EMAIL_MISMATCH_OR_RESTRICTED) DelayAmber700 else EmergencyRed700
                                        )
                                    }
                                    Text(
                                        text = inviteInspectionResult.message,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    if (!isValid) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            TextButton(
                                                onClick = {
                                                    inviteCode = ""
                                                    onDismissInviteInspection()
                                                }
                                            ) {
                                                Text("Clear Code & Request New Invite")
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = inviteCode,
                            onValueChange = { inviteCode = it.uppercase() },
                            label = { Text("School Invite Code") },
                            placeholder = { Text("e.g., INV-ST-A1B2C3") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("invite_code_input")
                        )
                        Button(
                            onClick = { onClaimInvite(inviteCode) },
                            enabled = inviteCode.trim().length >= 4 && !isBusy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("claim_invite_button")
                        ) {
                            Text("Join School")
                        }
                    }
                }
            } else {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Register Independent School", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Select a school subscription plan (includes a 14-day trial on standard tiers) with strict multi-school tenant isolation.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = schoolName,
                            onValueChange = { schoolName = it },
                            label = { Text("School Name *") },
                            placeholder = { Text("e.g., Accra Ridge Preparatory School") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("school_name_input")
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = city,
                                onValueChange = { city = it },
                                label = { Text("City *") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = country,
                                onValueChange = { country = it },
                                label = { Text("Country *") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        OutlinedTextField(
                            value = address,
                            onValueChange = { address = it },
                            label = { Text("Campus Address") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = phone,
                                onValueChange = { phone = it },
                                label = { Text("School Phone") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = email,
                                onValueChange = { email = it },
                                label = { Text("School Email") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Text("Billing Cycle", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = billingCycle == "monthly",
                                onClick = { billingCycle = "monthly" },
                                label = { Text("Monthly") }
                            )
                            FilterChip(
                                selected = billingCycle == "annual",
                                onClick = { billingCycle = "annual" },
                                label = { Text("Annual (Save ${selectedPlan.annualDiscountPercent}%)") }
                            )
                        }

                        Text("Select School Subscription Plan", style = MaterialTheme.typography.labelLarge)
                        plans.filter { it.isActive }.forEach { plan ->
                            val isSelected = plan.id == selectedPlan.id
                            val displayedMonthly = if (billingCycle == "annual" && plan.annualDiscountPercent > 0) {
                                plan.monthlyPriceGhs * (1.0 - plan.annualDiscountPercent / 100.0)
                            } else {
                                plan.monthlyPriceGhs
                            }
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedPlanId = plan.id },
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(
                                    if (isSelected) 2.dp else 1.dp,
                                    if (isSelected) SafeBlue700 else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                ),
                                color = if (isSelected) SafeBlue100.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surface
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(plan.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text(
                                            "GH₵${displayedMonthly.toInt()}/mo • Up to ${plan.pupilLimit} pupils",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = SafeBlue700,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    if (plan.trialDays > 0) {
                                        Text(
                                            "${plan.trialDays}-day free trial • ${plan.features.take(2).joinToString(" • ")}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = SafeGreen700
                                        )
                                    } else if (plan.features.isNotEmpty()) {
                                        Text(
                                            plan.features.take(2).joinToString(" • "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = {
                                onRegisterSchool(schoolName, city, country, address, phone, email, selectedPlan, billingCycle)
                            },
                            enabled = schoolName.trim().length >= 2 && !isBusy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("register_school_button")
                        ) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null)
                            Spacer(Modifier.padding(4.dp))
                            Text(
                                if (selectedPlan.trialDays > 0) {
                                    "Start ${selectedPlan.trialDays}-Day Trial & Register"
                                } else {
                                    "Register School & Open Workspace"
                                }
                            )
                        }
                    }
                }
            }

            AppShareDownloadCard(
                config = appDistributionConfig,
                currentDeviceLayoutMode = deviceViewLayoutMode,
                canEditAccessSettings = isSuperAdmin,
                onOpenFullDialog = onOpenShareDialog
            )
        }
    }
}
