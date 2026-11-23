package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FilePresent
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AppDistributionConfig
import com.example.data.model.AppViewAccessMode
import com.example.ui.components.QrCodePreviewBox
import com.example.ui.components.copyAppUrlToClipboard
import com.example.ui.components.openAppDownloadUrl
import com.example.ui.components.shareAppDownloadUrl
import com.example.ui.components.shareAppViaWhatsApp
import com.example.ui.components.shareInstalledApkFile
import com.example.ui.theme.EmergencyRed100
import com.example.ui.theme.EmergencyRed700
import com.example.ui.theme.SafeBlue100
import com.example.ui.theme.SafeBlue700
import com.example.ui.theme.SafeGreen100
import com.example.ui.theme.SafeGreen700
import com.example.util.AppLinkValidator

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsShareAppSection(
    config: AppDistributionConfig,
    schoolName: String,
    availableInviteCodes: List<String>,
    canEditAccessSettings: Boolean,
    isBusy: Boolean,
    onSaveDistributionConfig: (
        downloadUrl: String,
        viewAccessMode: AppViewAccessMode,
        allowedDeviceTypes: List<String>,
        releaseNotes: String,
        playStoreUrl: String,
        appStoreUrl: String,
        apkDownloadUrl: String
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var primaryUrl by remember(config.downloadUrl) { mutableStateOf(config.downloadUrl) }
    var playStoreUrl by remember(config.playStoreUrl) { mutableStateOf(config.playStoreUrl) }
    var appStoreUrl by remember(config.appStoreUrl) { mutableStateOf(config.appStoreUrl) }
    var apkUrl by remember(config.apkDownloadUrl) { mutableStateOf(config.apkDownloadUrl) }
    var accessMode by remember(config.viewAccessMode) { mutableStateOf(AppViewAccessMode.fromKey(config.viewAccessMode)) }
    var selectedDevices by remember(config.allowedDeviceTypes) {
        mutableStateOf(config.allowedDeviceTypes.ifEmpty { listOf("phone", "tablet", "computer") }.toSet())
    }
    var releaseNotes by remember(config.releaseNotes) { mutableStateOf(config.releaseNotes) }
    var optionalInvite by remember(availableInviteCodes) { mutableStateOf(availableInviteCodes.firstOrNull().orEmpty()) }
    var showQrCode by remember { mutableStateOf(true) }

    val preferred = remember(primaryUrl, playStoreUrl, appStoreUrl, apkUrl) {
        AppLinkValidator.resolvePreferredDownloadUrl(
            primaryUrl = primaryUrl,
            playStoreUrl = playStoreUrl,
            appStoreUrl = appStoreUrl,
            apkDownloadUrl = apkUrl
        )
    }
    val shareResult = remember(preferred.normalizedUrl, optionalInvite) {
        AppLinkValidator.buildShareableUrl(preferred.normalizedUrl, optionalInvite)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Share, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column {
                    Text("Share App & Play-Free Distribution", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "Distribute directly over the internet without Google Play Store (Direct APK URL, Firebase App Distribution, Web Portal, or APK File Share) across Phone, Tablet & Desktop.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = SafeBlue100.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "How to Share Without Google Play Store",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = SafeBlue700
                    )
                    Text(
                        text = "1. Export your SafeTrack APK and upload it to Firebase App Distribution, GitHub Releases, or an HTTPS site (hosting/index.html is included in this project).\n2. Paste that https:// link below and save to generate a public link & QR code.\n3. Or tap 'Share APK File' below to send the installer directly over WhatsApp, Drive, or Nearby Share.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            OutlinedTextField(
                value = apkUrl,
                onValueChange = { if (canEditAccessSettings) apkUrl = it },
                readOnly = !canEditAccessSettings,
                label = { Text("Direct HTTPS APK URL (No Play Store Required)") },
                placeholder = { Text("https://downloads.yourschool.org/SafeTrack.apk") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = primaryUrl,
                onValueChange = { if (canEditAccessSettings) primaryUrl = it },
                readOnly = !canEditAccessSettings,
                label = { Text("Primary Web Portal / Firebase App Distribution URL") },
                placeholder = { Text("https://appdistribution.firebase.google.com/pub/i/...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("settings_primary_url_input")
            )
            OutlinedTextField(
                value = playStoreUrl,
                onValueChange = { if (canEditAccessSettings) playStoreUrl = it },
                readOnly = !canEditAccessSettings,
                label = { Text("Google Play Store Link (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = appStoreUrl,
                onValueChange = { if (canEditAccessSettings) appStoreUrl = it },
                readOnly = !canEditAccessSettings,
                label = { Text("Apple App Store / Desktop Web Link (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = optionalInvite,
                onValueChange = { optionalInvite = it.uppercase() },
                label = { Text("Include School Invite Code in Shared Link") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (shareResult.isValid) SafeGreen100 else EmergencyRed100,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = if (shareResult.isValid) "Active Verified Link (${shareResult.destinationType.label})" else "Public Link Not Configured Yet",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (shareResult.isValid) SafeGreen700 else EmergencyRed700
                    )
                    Text(
                        text = if (shareResult.isValid) shareResult.normalizedUrl else AppLinkValidator.installMethodHint(shareResult),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (shareResult.isValid) SafeGreen700 else EmergencyRed700
                    )
                    if (shareResult.isValid) {
                        Text(
                            text = AppLinkValidator.installMethodHint(shareResult),
                            style = MaterialTheme.typography.bodySmall,
                            color = SafeGreen700
                        )
                    }
                }
            }

            if (shareResult.isValid && showQrCode) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    QrCodePreviewBox(content = shareResult.normalizedUrl, size = 180.dp)
                    Text("Scan QR code with another phone or tablet to open link", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { copyAppUrlToClipboard(context, shareResult.normalizedUrl) },
                    enabled = shareResult.isValid,
                    modifier = Modifier.testTag("settings_copy_url_button")
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Copy URL")
                }
                Button(
                    onClick = { shareAppViaWhatsApp(context, shareResult.normalizedUrl, schoolName) },
                    enabled = shareResult.isValid,
                    modifier = Modifier.testTag("settings_whatsapp_share_button")
                ) {
                    Icon(Icons.Filled.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Share via WhatsApp")
                }
                OutlinedButton(
                    onClick = { shareAppDownloadUrl(context, shareResult.normalizedUrl, releaseNotes) },
                    enabled = shareResult.isValid,
                    modifier = Modifier.testTag("settings_native_share_button")
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("System Share")
                }
                OutlinedButton(
                    onClick = { showQrCode = !showQrCode },
                    modifier = Modifier.testTag("settings_toggle_qr_button")
                ) {
                    Icon(Icons.Filled.QrCode2, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text(if (showQrCode) "Hide QR" else "Show QR")
                }
                OutlinedButton(
                    onClick = { openAppDownloadUrl(context, shareResult.normalizedUrl) },
                    enabled = shareResult.isValid
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Test URL")
                }
                OutlinedButton(
                    onClick = { shareInstalledApkFile(context, schoolName) },
                    modifier = Modifier.testTag("settings_share_apk_file_button")
                ) {
                    Icon(Icons.Filled.FilePresent, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Share APK File")
                }
            }

            HorizontalDivider()

            Text("View Access Policy", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            AppViewAccessMode.entries.forEach { mode ->
                val selected = accessMode == mode
                Surface(
                    onClick = { if (canEditAccessSettings) accessMode = mode },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) SafeBlue700 else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                    color = if (selected) SafeBlue100.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(mode.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        Text(mode.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Text("Allowed Device Types", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("phone" to "Phone", "tablet" to "Tablet", "computer" to "Computer").forEach { (k, label) ->
                    val checked = k in selectedDevices
                    FilterChip(
                        selected = checked,
                        onClick = {
                            if (canEditAccessSettings) {
                                selectedDevices = if (checked && selectedDevices.size > 1) selectedDevices - k else selectedDevices + k
                            }
                        },
                        label = { Text(label) }
                    )
                }
            }

            if (canEditAccessSettings) {
                OutlinedTextField(
                    value = releaseNotes,
                    onValueChange = { releaseNotes = it },
                    label = { Text("Recipient Install Notes") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        onSaveDistributionConfig(
                            primaryUrl.trim(),
                            accessMode,
                            selectedDevices.toList(),
                            releaseNotes.trim(),
                            playStoreUrl.trim(),
                            appStoreUrl.trim(),
                            apkUrl.trim()
                        )
                    },
                    enabled = !isBusy && preferred.isValid,
                    modifier = Modifier.fillMaxWidth().testTag("settings_save_share_config_button")
                ) {
                    Text("Save Distribution & Access Settings")
                }
            }
        }
    }
}
