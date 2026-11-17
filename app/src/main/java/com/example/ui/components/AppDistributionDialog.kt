package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TabletAndroid
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.AppDistributionConfig
import com.example.data.model.AppViewAccessMode
import com.example.data.model.DeviceViewLayoutMode
import com.example.ui.theme.DelayAmber100
import com.example.ui.theme.DelayAmber700
import com.example.ui.theme.EmergencyRed100
import com.example.ui.theme.EmergencyRed700
import com.example.ui.theme.SafeBlue100
import com.example.ui.theme.SafeBlue700
import com.example.ui.theme.SafeGreen100
import com.example.ui.theme.SafeGreen700
import com.example.util.AppLinkValidator

fun buildDownloadLinkWithInvite(baseUrl: String, inviteCode: String): String {
    return AppLinkValidator.buildShareableUrl(baseUrl, inviteCode).normalizedUrl
}

fun copyAppUrlToClipboard(context: Context, url: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("SafeTrack Download URL", url))
    Toast.makeText(context, "App download URL copied", Toast.LENGTH_SHORT).show()
}

fun shareAppDownloadUrl(context: Context, url: String, releaseNotes: String) {
    val shareBody = buildString {
        append("Download & open SafeTrack on Phone, Tablet, or Computer:\n")
        append(url)
        if (releaseNotes.isNotBlank()) {
            append("\n\n")
            append(releaseNotes)
        }
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "SafeTrack App Download Link")
        putExtra(Intent.EXTRA_TEXT, shareBody)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, "Share SafeTrack Link to Other Devices"))
    }.onFailure {
        Toast.makeText(context, "No share target available on this device", Toast.LENGTH_SHORT).show()
    }
}

fun shareAppViaWhatsApp(context: Context, url: String, schoolName: String = "SafeTrack") {
    val headline = "Join $schoolName on SafeTrack (Phone, Tablet & Desktop). Download the official app using this link:"
    val directIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        `package` = "com.whatsapp"
        putExtra(Intent.EXTRA_TEXT, "$headline\n\n$url")
    }
    runCatching {
        context.startActivity(directIntent)
    }.onFailure {
        val waUri = Uri.parse(AppLinkValidator.buildWhatsAppShareUri(url, headline))
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, waUri))
        }.onFailure {
            shareAppDownloadUrl(context, url, headline)
        }
    }
}

fun openAppDownloadUrl(context: Context, url: String) {
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    }.onFailure {
        Toast.makeText(context, "Could not open URL in browser", Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppShareDownloadCard(
    config: AppDistributionConfig,
    currentDeviceLayoutMode: DeviceViewLayoutMode,
    canEditAccessSettings: Boolean,
    onOpenFullDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val accessMode = AppViewAccessMode.fromKey(config.viewAccessMode)
    val urlCheck = remember(config.downloadUrl, config.playStoreUrl, config.appStoreUrl, config.apkDownloadUrl) {
        AppLinkValidator.resolvePreferredDownloadUrl(
            primaryUrl = config.downloadUrl,
            playStoreUrl = config.playStoreUrl,
            appStoreUrl = config.appStoreUrl,
            apkDownloadUrl = config.apkDownloadUrl
        )
    }
    var showQrModal by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            1.dp,
            if (urlCheck.isValid) SafeBlue700.copy(alpha = 0.25f) else DelayAmber700.copy(alpha = 0.4f)
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Public, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text(
                            text = "App View Access & Download Link",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${accessMode.label} • View: ${currentDeviceLayoutMode.label}",
                            style = MaterialTheme.typography.bodySmall,
                            color = SafeGreen700
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (urlCheck.isValid) SafeGreen100 else DelayAmber100
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (urlCheck.isValid) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                            contentDescription = null,
                            tint = if (urlCheck.isValid) SafeGreen700 else DelayAmber700,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = urlCheck.destinationType.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (urlCheck.isValid) SafeGreen700 else DelayAmber700
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (urlCheck.isValid) SafeBlue100.copy(alpha = 0.45f) else DelayAmber100.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (urlCheck.isValid) Icons.Filled.Link else Icons.Filled.Warning,
                        contentDescription = null,
                        tint = if (urlCheck.isValid) SafeBlue700 else DelayAmber700,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (urlCheck.isValid) {
                            urlCheck.normalizedUrl
                        } else {
                            urlCheck.errorMessage ?: "Public download URL not configured."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { copyAppUrlToClipboard(context, urlCheck.normalizedUrl) },
                    enabled = urlCheck.isValid,
                    modifier = Modifier.testTag("card_copy_app_url_button")
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Copy Link")
                }
                Button(
                    onClick = { shareAppViaWhatsApp(context, urlCheck.normalizedUrl) },
                    enabled = urlCheck.isValid,
                    modifier = Modifier.testTag("card_whatsapp_app_url_button")
                ) {
                    Icon(Icons.Filled.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("WhatsApp")
                }
                OutlinedButton(
                    onClick = { shareAppDownloadUrl(context, urlCheck.normalizedUrl, config.releaseNotes) },
                    enabled = urlCheck.isValid,
                    modifier = Modifier.testTag("card_share_app_url_button")
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Share")
                }
                OutlinedButton(
                    onClick = { showQrModal = true },
                    enabled = urlCheck.isValid,
                    modifier = Modifier.testTag("card_qr_app_url_button")
                ) {
                    Icon(Icons.Filled.QrCode2, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("QR Code")
                }
                OutlinedButton(
                    onClick = onOpenFullDialog,
                    modifier = Modifier.testTag("card_configure_view_access_button")
                ) {
                    Icon(Icons.Filled.Devices, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text(if (canEditAccessSettings) "Configure URL & Access" else "Device View & Link Info")
                }
            }
        }
    }

    if (showQrModal && urlCheck.isValid) {
        AlertDialog(
            onDismissRequest = { showQrModal = false },
            title = { Text("Scan to Download App") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QrCodePreviewBox(content = urlCheck.normalizedUrl, size = 210.dp)
                    Text(
                        text = urlCheck.normalizedUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showQrModal = false }) { Text("Close") }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppViewAndDownloadDialog(
    config: AppDistributionConfig,
    currentDeviceLayoutMode: DeviceViewLayoutMode,
    canEditAccessSettings: Boolean,
    isBusy: Boolean,
    availableInviteCodes: List<String> = emptyList(),
    onSelectDeviceLayoutMode: (DeviceViewLayoutMode) -> Unit,
    onSaveConfig: (
        downloadUrl: String,
        viewAccessMode: AppViewAccessMode,
        allowedDeviceTypes: List<String>,
        releaseNotes: String
    ) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var editableUrl by remember(config.downloadUrl) {
        mutableStateOf(config.downloadUrl.trim())
    }
    var selectedAccessMode by remember(config.viewAccessMode) {
        mutableStateOf(AppViewAccessMode.fromKey(config.viewAccessMode))
    }
    var selectedDevices by remember(config.allowedDeviceTypes) {
        mutableStateOf(
            config.allowedDeviceTypes.ifEmpty { listOf("phone", "tablet", "computer") }.toSet()
        )
    }
    var releaseNotes by remember(config.releaseNotes) {
        mutableStateOf(config.releaseNotes)
    }
    var optionalInviteCode by remember(availableInviteCodes) {
        mutableStateOf(availableInviteCodes.firstOrNull().orEmpty())
    }

    val baseCheck = remember(editableUrl) {
        AppLinkValidator.validatePublicDownloadUrl(editableUrl)
    }
    val shareableCheck = remember(editableUrl, optionalInviteCode) {
        AppLinkValidator.buildShareableUrl(editableUrl, optionalInviteCode)
    }
    val isUrlValid = baseCheck.isValid
    val isShareableValid = shareableCheck.isValid
    val shareableUrl = shareableCheck.normalizedUrl

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.Devices, contentDescription = null, tint = SafeBlue700)
                Text("App View Access & Download URL")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 540.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Switch App View Layout",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Preview or lock the interface for Phone, Tablet, or Computer / Desktop screens.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DeviceViewLayoutMode.entries.forEach { mode ->
                        FilterChip(
                            selected = currentDeviceLayoutMode == mode,
                            onClick = { onSelectDeviceLayoutMode(mode) },
                            label = { Text(mode.label) },
                            leadingIcon = {
                                Icon(
                                    imageVector = when (mode) {
                                        DeviceViewLayoutMode.AUTO -> Icons.Filled.Devices
                                        DeviceViewLayoutMode.PHONE -> Icons.Filled.PhoneAndroid
                                        DeviceViewLayoutMode.TABLET -> Icons.Filled.TabletAndroid
                                        DeviceViewLayoutMode.COMPUTER -> Icons.Filled.Computer
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            modifier = Modifier.testTag("layout_mode_chip_${mode.key}")
                        )
                    }
                }

                HorizontalDivider()

                Text(
                    text = "Canonical HTTPS Download URL",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Use your official Google Play Store link, Firebase App Distribution release URL, or a verified HTTPS hosting page. Temporary preview URLs (*.run.app) are rejected.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = editableUrl,
                    onValueChange = { if (canEditAccessSettings) editableUrl = it },
                    readOnly = !canEditAccessSettings,
                    label = { Text("Verified HTTPS Download URL") },
                    placeholder = { Text("https://play.google.com/store/apps/details?id=com.aistudio.safetrack.xkqvmz") },
                    isError = !isUrlValid,
                    supportingText = {
                        Text(
                            text = if (isUrlValid) {
                                "Detected destination: ${baseCheck.destinationType.label}"
                            } else {
                                baseCheck.errorMessage ?: "Enter a valid HTTPS URL."
                            }
                        )
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("app_download_url_input")
                )

                OutlinedTextField(
                    value = optionalInviteCode,
                    onValueChange = { optionalInviteCode = it.uppercase() },
                    label = { Text("Optional School Invite Code in Link") },
                    placeholder = { Text("e.g., INV-ST-A1B2C3") },
                    isError = isUrlValid && optionalInviteCode.isNotBlank() && !isShareableValid,
                    supportingText = {
                        if (isUrlValid && optionalInviteCode.isNotBlank() && !isShareableValid) {
                            Text(shareableCheck.errorMessage ?: "Invalid invite code format.")
                        } else {
                            Text("Appends only ?invite=CODE; no child or personal details are added.")
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("download_link_invite_code_input")
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isShareableValid) SafeGreen100.copy(alpha = 0.45f) else EmergencyRed100.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = if (isShareableValid) {
                                "Validated Link (${shareableCheck.destinationType.label}):"
                            } else {
                                "Share Link Status (${shareableCheck.destinationType.label}):"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isShareableValid) SafeGreen700 else EmergencyRed700,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isShareableValid) shareableUrl else (shareableCheck.errorMessage ?: "Enter a valid public HTTPS URL above before copying or sharing."),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.testTag("generated_shareable_url_text")
                        )
                    }
                }

                if (isShareableValid) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        QrCodePreviewBox(content = shareableUrl, size = 156.dp)
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { copyAppUrlToClipboard(context, shareableUrl) },
                        enabled = isShareableValid,
                        modifier = Modifier.testTag("dialog_copy_url_button")
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Copy Link")
                    }
                    Button(
                        onClick = { shareAppViaWhatsApp(context, shareableUrl) },
                        enabled = isShareableValid,
                        modifier = Modifier.testTag("dialog_whatsapp_url_button")
                    ) {
                        Icon(Icons.Filled.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("WhatsApp")
                    }
                    OutlinedButton(
                        onClick = { shareAppDownloadUrl(context, shareableUrl, releaseNotes) },
                        enabled = isShareableValid,
                        modifier = Modifier.testTag("dialog_share_url_button")
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Share")
                    }
                    OutlinedButton(
                        onClick = { openAppDownloadUrl(context, shareableUrl) },
                        enabled = isShareableValid,
                        modifier = Modifier.testTag("dialog_open_url_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Open")
                    }
                }

                HorizontalDivider()

                Text(
                    text = "App View Access Policy",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                AppViewAccessMode.entries.forEach { mode ->
                    val selected = selectedAccessMode == mode
                    Surface(
                        onClick = { if (canEditAccessSettings) selectedAccessMode = mode },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            width = if (selected) 1.5.dp else 1.dp,
                            color = if (selected) SafeBlue700 else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                        ),
                        color = if (selected) SafeBlue100.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("view_access_mode_${mode.key}")
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = mode.label + if (selected) " (Active)" else "",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) SafeBlue700 else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = mode.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Text(
                    text = "Enabled Device Platforms",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "phone" to "Phone",
                        "tablet" to "Tablet",
                        "computer" to "Computer"
                    ).forEach { (key, label) ->
                        val isChecked = key in selectedDevices
                        FilterChip(
                            selected = isChecked,
                            onClick = {
                                if (canEditAccessSettings) {
                                    val updated = if (isChecked && selectedDevices.size > 1) {
                                        selectedDevices - key
                                    } else {
                                        selectedDevices + key
                                    }
                                    selectedDevices = updated
                                }
                            },
                            label = { Text(label) },
                            modifier = Modifier.testTag("allowed_device_chip_$key")
                        )
                    }
                }

                if (canEditAccessSettings) {
                    OutlinedTextField(
                        value = releaseNotes,
                        onValueChange = { releaseNotes = it },
                        label = { Text("Install Instructions for Recipients") },
                        maxLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("app_release_notes_input")
                    )
                } else {
                    Text(
                        text = "Tip: School Admins and Super Admins can change the public view access policy and custom APK/Play Store download URL from their workspace.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            if (canEditAccessSettings) {
                Button(
                    onClick = {
                        onSaveConfig(
                            editableUrl.trim(),
                            selectedAccessMode,
                            selectedDevices.toList(),
                            releaseNotes.trim()
                        )
                        onDismiss()
                    },
                    enabled = isUrlValid && !isBusy,
                    modifier = Modifier.testTag("save_app_distribution_button")
                ) {
                    Text("Save Access & URL")
                }
            } else {
                Button(onClick = onDismiss) {
                    Text("Done")
                }
            }
        },
        dismissButton = {
            if (canEditAccessSettings) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}
