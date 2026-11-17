package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.BuildConfig
import com.example.data.local.AccentPalette
import com.example.data.local.AppSettingsState
import com.example.data.local.AppThemeMode
import com.example.data.model.AppDistributionConfig
import com.example.data.model.AppViewAccessMode
import com.example.data.model.DeviceViewLayoutMode
import com.example.data.model.ExamGrading
import com.example.data.model.School
import com.example.data.model.UserDeviceSession
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.theme.EmergencyRed600
import com.example.ui.theme.SafeBlue700
import com.example.ui.theme.SafeGreen600
import com.example.util.BackupSummary
import com.example.util.ReportType
import com.example.util.SchoolBackupExporter
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsManagementScreen(
    initialCategoryIndex: Int = 0,
    role: UserRole,
    profile: UserProfile?,
    school: School?,
    appSettings: AppSettingsState,
    appDistributionConfig: AppDistributionConfig,
    availableInviteCodes: List<String>,
    deviceSessions: List<UserDeviceSession>,
    lastBackupInspection: BackupSummary?,
    recordSummaryCounts: List<Pair<String, Int>>,
    isBusy: Boolean,
    onUpdateThemeMode: (AppThemeMode) -> Unit,
    onUpdateAccentPalette: (AccentPalette) -> Unit,
    onUpdateFontScale: (Float) -> Unit,
    onUpdateCompactCards: (Boolean) -> Unit,
    onUpdateDashboardWidgets: (showActivities: Boolean, showGeofence: Boolean) -> Unit,
    onSelectDeviceLayoutMode: (DeviceViewLayoutMode) -> Unit,
    onRestoreDefaults: () -> Unit,
    onUpdateSchoolInfo: (name: String, city: String, address: String, phone: String, email: String, logoUri: String?, motto: String, academicYear: String, currentTerm: String, operatingHours: String) -> Unit,
    onUpdateMyProfileDetails: (displayName: String, phone: String) -> Unit,
    onToggleLocationConsent: (Boolean) -> Unit,
    onRevokeDeviceSession: (String) -> Unit,
    onUpdateNotificationSetting: (category: String, enabled: Boolean) -> Unit,
    onUpdateAssessmentWeight: (Int) -> Unit,
    onNavigateAcademics: () -> Unit,
    onNavigateFinance: () -> Unit,
    onExportBackupJson: () -> Unit,
    onInspectBackupJson: (String) -> Unit,
    onExportPdf: (ReportType) -> Unit,
    onExportCsv: (ReportType) -> Unit,
    onClearExportCache: () -> Unit,
    onSaveDistributionConfig: (String, AppViewAccessMode, List<String>, String, String, String, String) -> Unit,
    onOpenGuide: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isAdmin = role == UserRole.SCHOOL_ADMIN || role == UserRole.SUPER_ADMIN
    val categories = listOf(
        "Appearance",
        "School Info",
        "Account & Security",
        "Notifications",
        "Academics",
        "Data & Backup",
        "Plan & Support",
        "Share App"
    )
    var selectedCategory by remember { mutableIntStateOf(initialCategoryIndex.coerceIn(0, categories.lastIndex)) }
    LaunchedEffect(initialCategoryIndex) {
        selectedCategory = initialCategoryIndex.coerceIn(0, categories.lastIndex)
    }

    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var showBackupInspectDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        ScrollableTabRow(selectedTabIndex = selectedCategory, edgePadding = 12.dp) {
            categories.forEachIndexed { idx, label ->
                Tab(
                    selected = selectedCategory == idx,
                    onClick = { selectedCategory = idx },
                    text = { Text(label) },
                    modifier = Modifier.testTag("settings_tab_$idx")
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            item {
                Column(modifier = Modifier.widthIn(max = 920.dp).fillMaxWidth()) {
                    when (selectedCategory) {
                        0 -> AppearanceSection(
                            appSettings = appSettings,
                            onUpdateThemeMode = onUpdateThemeMode,
                            onUpdateAccentPalette = onUpdateAccentPalette,
                            onUpdateFontScale = onUpdateFontScale,
                            onUpdateCompactCards = onUpdateCompactCards,
                            onUpdateDashboardWidgets = onUpdateDashboardWidgets,
                            onSelectDeviceLayoutMode = onSelectDeviceLayoutMode,
                            onRestoreDefaults = onRestoreDefaults
                        )
                        1 -> SchoolInfoSection(
                            school = school,
                            isAdmin = isAdmin,
                            isBusy = isBusy,
                            onUpdateSchoolInfo = onUpdateSchoolInfo
                        )
                        2 -> AccountSecuritySection(
                            role = role,
                            profile = profile,
                            deviceSessions = deviceSessions,
                            isBusy = isBusy,
                            onUpdateMyProfileDetails = onUpdateMyProfileDetails,
                            onToggleLocationConsent = onToggleLocationConsent,
                            onRevokeDeviceSession = onRevokeDeviceSession,
                            onSignOut = onSignOut,
                            onRequestDeleteAccount = { showDeleteAccountDialog = true }
                        )
                        3 -> NotificationPreferencesSection(
                            appSettings = appSettings,
                            onUpdateNotificationSetting = onUpdateNotificationSetting
                        )
                        4 -> AcademicConfigSection(
                            classWeightPercent = appSettings.classAssessmentWeightPercent,
                            onUpdateAssessmentWeight = onUpdateAssessmentWeight,
                            onNavigateAcademics = onNavigateAcademics
                        )
                        5 -> DataManagementSection(
                            recordSummaryCounts = recordSummaryCounts,
                            lastBackupInspection = lastBackupInspection,
                            onExportBackupJson = onExportBackupJson,
                            onOpenInspectDialog = { showBackupInspectDialog = true },
                            onExportPdf = onExportPdf,
                            onExportCsv = onExportCsv,
                            onClearExportCache = onClearExportCache
                        )
                        6 -> SubscriptionSupportSection(
                            school = school,
                            onNavigateFinance = onNavigateFinance,
                            onOpenGuide = onOpenGuide,
                            onContactSupport = {
                                val mail = Intent(Intent.ACTION_SENDTO).apply {
                                    data = Uri.parse("mailto:info.cefelixacademy.com")
                                    putExtra(Intent.EXTRA_SUBJECT, "Cefelix SafeTrack Support Request")
                                }
                                runCatching { context.startActivity(mail) }
                            }
                        )
                        7 -> SettingsShareAppSection(
                            config = appDistributionConfig,
                            schoolName = school?.name ?: "Cefelix Academy",
                            availableInviteCodes = availableInviteCodes,
                            canEditAccessSettings = isAdmin,
                            isBusy = isBusy,
                            onSaveDistributionConfig = onSaveDistributionConfig
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (showDeleteAccountDialog) {
        ConfirmDeleteDialog(
            title = "Delete Your SafeTrack Account?",
            message = "This removes your profile, memberships, and device GPS sessions across schools. Audit records preserved by schools are kept anonymized where required.",
            onDismiss = { showDeleteAccountDialog = false },
            onConfirmDelete = onDeleteAccount
        )
    }

    if (showBackupInspectDialog) {
        var jsonInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showBackupInspectDialog = false },
            title = { Text("Verify School JSON Backup") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Paste exported JSON backup text below to validate structure & record counts.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = jsonInput,
                        onValueChange = { jsonInput = it },
                        label = { Text("Backup JSON") },
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onInspectBackupJson(jsonInput)
                        showBackupInspectDialog = false
                    },
                    enabled = jsonInput.trim().startsWith("{")
                ) { Text("Inspect Backup") }
            },
            dismissButton = { TextButton(onClick = { showBackupInspectDialog = false }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppearanceSection(
    appSettings: AppSettingsState,
    onUpdateThemeMode: (AppThemeMode) -> Unit,
    onUpdateAccentPalette: (AccentPalette) -> Unit,
    onUpdateFontScale: (Float) -> Unit,
    onUpdateCompactCards: (Boolean) -> Unit,
    onUpdateDashboardWidgets: (Boolean, Boolean) -> Unit,
    onSelectDeviceLayoutMode: (DeviceViewLayoutMode) -> Unit,
    onRestoreDefaults: () -> Unit
) {
    var sliderScale by remember(appSettings.fontScale) { mutableFloatStateOf(appSettings.fontScale) }

    Card(shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("A. Appearance & Personalisation", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }

            Text("Theme Mode", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = appSettings.themeMode == mode,
                        onClick = { onUpdateThemeMode(mode) },
                        label = { Text(mode.label) },
                        modifier = Modifier.testTag("theme_mode_${mode.key}")
                    )
                }
            }

            Text("Accent Colour Palette", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AccentPalette.entries.forEach { pal ->
                    FilterChip(
                        selected = appSettings.accentPalette == pal,
                        onClick = { onUpdateAccentPalette(pal) },
                        label = { Text(pal.label) },
                        modifier = Modifier.testTag("accent_${pal.key}")
                    )
                }
            }

            Text("Display Font Scale: ${(sliderScale * 100).roundToInt()}%", style = MaterialTheme.typography.labelLarge)
            Slider(
                value = sliderScale,
                onValueChange = { sliderScale = it },
                onValueChangeFinished = { onUpdateFontScale(sliderScale) },
                valueRange = 0.85f..1.25f,
                steps = 7
            )

            Text("Device View Layout", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DeviceViewLayoutMode.entries.forEach { m ->
                    FilterChip(
                        selected = appSettings.deviceLayoutMode == m,
                        onClick = { onSelectDeviceLayoutMode(m) },
                        label = { Text(m.label) }
                    )
                }
            }

            SettingSwitchRow("Compact Dashboard Cards", "Tighter vertical padding for dense monitoring", appSettings.compactCards, onUpdateCompactCards)
            SettingSwitchRow("Show Recent Activity Feed", "Display school audit activities on Dashboard", appSettings.showDashboardActivities) {
                onUpdateDashboardWidgets(it, appSettings.showDashboardGeofence)
            }
            SettingSwitchRow("Show Geofencing Overview", "Display campus map preview on Dashboard", appSettings.showDashboardGeofence) {
                onUpdateDashboardWidgets(appSettings.showDashboardActivities, it)
            }

            OutlinedButton(onClick = onRestoreDefaults, modifier = Modifier.fillMaxWidth().testTag("restore_default_settings_button")) {
                Text("Restore Default Settings")
            }
        }
    }
}

@Composable
private fun SchoolInfoSection(
    school: School?,
    isAdmin: Boolean,
    isBusy: Boolean,
    onUpdateSchoolInfo: (String, String, String, String, String, String?, String, String, String, String) -> Unit
) {
    var name by remember(school?.id, school?.name) { mutableStateOf(school?.name.orEmpty()) }
    var city by remember(school?.id, school?.city) { mutableStateOf(school?.city.orEmpty()) }
    var address by remember(school?.id, school?.address) { mutableStateOf(school?.address.orEmpty()) }
    var phone by remember(school?.id, school?.phone) { mutableStateOf(school?.phone.orEmpty()) }
    var email by remember(school?.id, school?.email) { mutableStateOf(school?.email.orEmpty()) }
    var logoUri by remember(school?.id, school?.logoUri) { mutableStateOf(school?.logoUri) }
    var motto by remember(school?.id, school?.motto) { mutableStateOf(school?.motto.orEmpty()) }
    var year by remember(school?.id, school?.academicYear) { mutableStateOf(school?.academicYear ?: "2026/2027") }
    var term by remember(school?.id, school?.currentTerm) { mutableStateOf(school?.currentTerm ?: "Term 1") }
    var hours by remember(school?.id, school?.operatingHours) { mutableStateOf(school?.operatingHours ?: "07:00 - 15:30") }

    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) logoUri = uri.toString()
    }

    Card(shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Apartment, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("B. School Information & Identity", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            OutlinedTextField(value = name, onValueChange = { if (isAdmin) name = it }, readOnly = !isAdmin, label = { Text("School Name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = motto, onValueChange = { if (isAdmin) motto = it }, readOnly = !isAdmin, label = { Text("School Motto") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = city, onValueChange = { if (isAdmin) city = it }, readOnly = !isAdmin, label = { Text("City") }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = phone, onValueChange = { if (isAdmin) phone = it }, readOnly = !isAdmin, label = { Text("Phone") }, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(value = address, onValueChange = { if (isAdmin) address = it }, readOnly = !isAdmin, label = { Text("Campus Address") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = email, onValueChange = { if (isAdmin) email = it }, readOnly = !isAdmin, label = { Text("Official Email") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = year, onValueChange = { if (isAdmin) year = it }, readOnly = !isAdmin, label = { Text("Academic Year") }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = term, onValueChange = { if (isAdmin) term = it }, readOnly = !isAdmin, label = { Text("Active Term") }, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(value = hours, onValueChange = { if (isAdmin) hours = it }, readOnly = !isAdmin, label = { Text("Operating Hours") }, modifier = Modifier.fillMaxWidth())

            if (isAdmin) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { logoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text(if (logoUri.isNullOrBlank()) "Upload Logo" else "Change Logo")
                    }
                    Button(
                        onClick = { onUpdateSchoolInfo(name, city, address, phone, email, logoUri, motto, year, term, hours) },
                        enabled = !isBusy && name.trim().isNotEmpty(),
                        modifier = Modifier.weight(1f).testTag("save_school_info_button")
                    ) { Text("Save School Info") }
                }
            }
        }
    }
}

@Composable
private fun AccountSecuritySection(
    role: UserRole,
    profile: UserProfile?,
    deviceSessions: List<UserDeviceSession>,
    isBusy: Boolean,
    onUpdateMyProfileDetails: (String, String) -> Unit,
    onToggleLocationConsent: (Boolean) -> Unit,
    onRevokeDeviceSession: (String) -> Unit,
    onSignOut: () -> Unit,
    onRequestDeleteAccount: () -> Unit
) {
    val context = LocalContext.current
    var displayName by remember(profile?.displayName) { mutableStateOf(profile?.displayName.orEmpty()) }
    var phone by remember(profile?.phone) { mutableStateOf(profile?.phone.orEmpty()) }

    Card(shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("C. Account & Security", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Text("Signed in as: ${profile?.email.orEmpty()} • Role: ${role.label}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            OutlinedTextField(value = displayName, onValueChange = { displayName = it }, label = { Text("Display Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Contact Phone") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = { onUpdateMyProfileDetails(displayName, phone) },
                enabled = !isBusy && displayName.trim().isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Update Profile Details") }

            SettingSwitchRow(
                "GPS Journey Location Consent",
                "Allow GPS capture only during authorised active journeys & school perimeter alerts",
                profile?.locationConsentGranted == true,
                onToggleLocationConsent
            )

            HorizontalDivider()
            Text("Google Sign-In Password & Two-Factor Protection", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "Your SafeTrack account authenticates via Google Identity. Manage your Google password, passkeys, and 2-Step Verification directly in Google Security.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://myaccount.google.com/security"))) } },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Password & Recovery")
                }
                OutlinedButton(
                    onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://myaccount.google.com/signinoptions/two-step-verification"))) } },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Google 2FA")
                }
            }

            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.Devices, contentDescription = null, tint = SafeBlue700, modifier = Modifier.size(18.dp))
                Text("Registered Devices & Sessions (${deviceSessions.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            deviceSessions.forEach { sess ->
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(sess.deviceName, style = MaterialTheme.typography.titleSmall)
                            Text("${sess.platform} • App v${sess.appVersion}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { onRevokeDeviceSession(sess.id) }) { Text("Revoke") }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onSignOut, modifier = Modifier.weight(1f)) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Sign Out")
                }
                Button(
                    onClick = onRequestDeleteAccount,
                    colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed600),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Delete Account")
                }
            }
        }
    }
}

@Composable
private fun NotificationPreferencesSection(
    appSettings: AppSettingsState,
    onUpdateNotificationSetting: (String, Boolean) -> Unit
) {
    val context = LocalContext.current
    var hasNotifPerm by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        )
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasNotifPerm = granted
    }

    Card(shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("D. Notification Controls", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            if (!hasNotifPerm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Button(onClick = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Grant Android System Notification Permission")
                }
            }
            SettingSwitchRow("Attendance Alerts", "Morning roll call & tardiness updates", appSettings.notifyAttendance) {
                onUpdateNotificationSetting("attendance", it)
            }
            SettingSwitchRow("Pupil Movement Alerts", "Morning departures, teacher dismissals & safe arrivals", appSettings.notifyMovement) {
                onUpdateNotificationSetting("movement", it)
            }
            SettingSwitchRow("Geofence Notifications", "Campus gate perimeter entry & exit triggers", appSettings.notifyGeofence) {
                onUpdateNotificationSetting("geofence", it)
            }
            SettingSwitchRow("Incident Notifications", "Safety & medical incident dispatches", appSettings.notifyIncidents) {
                onUpdateNotificationSetting("incidents", it)
            }
            SettingSwitchRow("Academic Reminders", "Exam report publication & grading notifications", appSettings.notifyAcademics) {
                onUpdateNotificationSetting("academics", it)
            }
            SettingSwitchRow("Parent Communications", "Direct parent-teacher announcements & messages", appSettings.notifyComms) {
                onUpdateNotificationSetting("comms", it)
            }
        }
    }
}

@Composable
private fun AcademicConfigSection(
    classWeightPercent: Int,
    onUpdateAssessmentWeight: (Int) -> Unit,
    onNavigateAcademics: () -> Unit
) {
    var sliderVal by remember(classWeightPercent) { mutableFloatStateOf(classWeightPercent.toFloat()) }

    Card(shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("E. Academic Configuration", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Text(
                "Assessment Weighting: Class ${sliderVal.roundToInt()}% / Exam ${100 - sliderVal.roundToInt()}%",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Slider(
                value = sliderVal,
                onValueChange = { sliderVal = it },
                onValueChangeFinished = { onUpdateAssessmentWeight(sliderVal.roundToInt()) },
                valueRange = 20f..80f,
                steps = 11
            )

            Text("Standard Grading Scale (G1–G6)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            ExamGrading.scaleBands.forEach { band ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${band.gradeCode} (${band.percentRangeLabel})", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(band.interpretation, style = MaterialTheme.typography.bodyMedium, color = SafeGreen600)
                }
            }
            Button(onClick = onNavigateAcademics, modifier = Modifier.fillMaxWidth()) {
                Text("Open Subjects & Report Cards Setup")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DataManagementSection(
    recordSummaryCounts: List<Pair<String, Int>>,
    lastBackupInspection: BackupSummary?,
    onExportBackupJson: () -> Unit,
    onOpenInspectDialog: () -> Unit,
    onExportPdf: (ReportType) -> Unit,
    onExportCsv: (ReportType) -> Unit,
    onClearExportCache: () -> Unit
) {
    val context = LocalContext.current
    var cacheBytes by remember { mutableStateOf(SchoolBackupExporter.calculateLocalCacheBytes(context)) }

    Card(shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Backup, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("F. Data Management & Backups", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                recordSummaryCounts.forEach { (label, count) ->
                    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)) {
                        Text("$label: $count", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                }
            }

            Button(onClick = {
                onExportBackupJson()
                cacheBytes = SchoolBackupExporter.calculateLocalCacheBytes(context)
            }, modifier = Modifier.fillMaxWidth().testTag("export_full_json_backup_button")) {
                Text("Export Full School JSON Backup")
            }
            OutlinedButton(onClick = onOpenInspectDialog, modifier = Modifier.fillMaxWidth()) {
                Text("Verify / Inspect JSON Backup File")
            }

            lastBackupInspection?.let { summary ->
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                        Text("Verified Backup: ${summary.schoolName}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            "${summary.classCount} classes • ${summary.studentCount} pupils • ${summary.attendanceCount} attendance • ${summary.feeRecordCount} fees • ${summary.inventoryCount} inventory",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            HorizontalDivider()
            Text("Reports Export", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            ReportType.entries.forEach { rt ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(rt.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = { onExportPdf(rt) }) { Text("PDF") }
                        OutlinedButton(onClick = { onExportCsv(rt) }) { Text("CSV") }
                    }
                }
            }

            HorizontalDivider()
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Local Export Cache: ${Formatter.formatShortFileSize(context, cacheBytes)}", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = {
                    onClearExportCache()
                    cacheBytes = 0L
                }) { Text("Clear Cache") }
            }
        }
    }
}

@Composable
private fun SubscriptionSupportSection(
    school: School?,
    onNavigateFinance: () -> Unit,
    onOpenGuide: () -> Unit,
    onContactSupport: () -> Unit
) {
    Card(shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Payments, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("G. Subscription & Support", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            if (school != null) {
                Text(
                    "Active Plan: ${school.planName} (${school.subscriptionStatus.uppercase()}) • Up to ${school.pupilLimit} pupils",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Button(onClick = onNavigateFinance, modifier = Modifier.fillMaxWidth()) {
                Text("Manage Plan & School Billing")
            }
            OutlinedButton(onClick = onOpenGuide, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text("Help Centre & 5-Slide Interactive Guide")
            }
            OutlinedButton(onClick = onContactSupport, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.SupportAgent, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text("Contact Support (info.cefelixacademy.com)")
            }
            HorizontalDivider()
            Text(
                "App Version: ${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE}) • ${BuildConfig.APPLICATION_ID}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
