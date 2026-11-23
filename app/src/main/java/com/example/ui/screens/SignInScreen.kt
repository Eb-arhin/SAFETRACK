package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.data.model.AppDistributionConfig
import com.example.data.model.DeviceViewLayoutMode
import com.example.ui.auth.onGoogleSignInClicked
import com.example.ui.components.AppViewAndDownloadDialog
import com.example.ui.theme.SafeBlue100
import com.example.ui.theme.SafeBlue700
import com.example.ui.theme.SafeBlue900
import com.example.ui.theme.SafeGreen100
import com.example.ui.theme.SafeGreen700

@Composable
fun SignInScreen(
    onAuthSuccess: () -> Unit,
    appDistributionConfig: AppDistributionConfig = AppDistributionConfig(),
    deviceViewLayoutMode: DeviceViewLayoutMode = DeviceViewLayoutMode.AUTO,
    onSelectDeviceLayoutMode: (DeviceViewLayoutMode) -> Unit = {},
    onOpenGuide: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showShareDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        SafeBlue900,
                        SafeBlue700,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.widthIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = SafeGreen100
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = "SafeTrack emblem",
                            tint = SafeGreen700,
                            modifier = Modifier
                                .padding(10.dp)
                                .size(28.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "SafeTrack",
                            style = MaterialTheme.typography.displaySmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "School, Child Movement & Attendance Platform",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SafeBlue100
                        )
                    }
                }

                Image(
                    painter = painterResource(id = R.drawable.img_hero_safetrack),
                    contentDescription = "SafeTrack school journey safety illustration",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .clip(RoundedCornerShape(22.dp)),
                    contentScale = ContentScale.Crop
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Verified Access for Every School",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Sign in with Google to access your school's encrypted workspace as a Parent, Teacher, School Admin, or Platform Super Admin.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        FeatureRow(
                            icon = Icons.Filled.Apartment,
                            title = "Strict Multi-School Isolation",
                            subtitle = "Each school's pupils, classes, and logs are tenant-isolated in the cloud."
                        )
                        FeatureRow(
                            icon = Icons.Filled.CheckCircle,
                            title = "Physical Arrival Confirmation",
                            subtitle = "Morning journeys notify teachers; attendance is recorded only on physical confirmation."
                        )
                        FeatureRow(
                            icon = Icons.Filled.GpsFixed,
                            title = "Consent-Based Journey GPS",
                            subtitle = "Foreground location runs only during authorised journeys and stops on safe arrival."
                        )

                        if (errorMessage != null) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.errorContainer
                            ) {
                                Text(
                                    text = errorMessage!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }

                        Button(
                            onClick = {
                                errorMessage = null
                                isLoading = true
                                onGoogleSignInClicked(
                                    context = context,
                                    credentialManager = credentialManager,
                                    onAuthSuccess = {
                                        isLoading = false
                                        onAuthSuccess()
                                    },
                                    onAuthError = { msg ->
                                        isLoading = false
                                        errorMessage = msg
                                    },
                                    scope = scope,
                                    onAuthCancelled = { isLoading = false }
                                )
                            },
                            enabled = !isLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("google_sign_in_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = SafeBlue700),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(10.dp))
                                Text("Sign in with Google", style = MaterialTheme.typography.labelLarge)
                            }
                        }

                        OutlinedButton(
                            onClick = { showShareDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("signin_share_download_url_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Filled.Devices, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Download Link & Device View")
                        }

                        OutlinedButton(
                            onClick = onOpenGuide,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("signin_open_guide_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("How SafeTrack Works (5-Slide Guide)")
                        }

                        Text(
                            text = "Privacy Notice: SafeTrack isolates each school's pupil records. Children's locations are never placed in public pages or share links.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (showShareDialog) {
        AppViewAndDownloadDialog(
            config = appDistributionConfig,
            currentDeviceLayoutMode = deviceViewLayoutMode,
            canEditAccessSettings = false,
            isBusy = false,
            onSelectDeviceLayoutMode = onSelectDeviceLayoutMode,
            onSaveConfig = { _, _, _, _ -> },
            onDismiss = { showShareDialog = false }
        )
    }
}

@Composable
private fun FeatureRow(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = SafeBlue100
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = SafeBlue900,
                modifier = Modifier
                    .padding(8.dp)
                    .size(18.dp)
            )
        }
        Column {
            Text(text = title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
