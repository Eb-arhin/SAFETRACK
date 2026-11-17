package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.SafeBlue100
import com.example.ui.theme.SafeBlue700
import com.example.ui.theme.SafeBlue900
import com.example.ui.theme.SafeTeal100
import com.example.ui.theme.SafeTeal700
import kotlinx.coroutines.launch

private data class GuideSlide(
    val title: String,
    val subtitle: String,
    val bulletPoints: List<String>,
    val statusNote: String,
    val icon: ImageVector,
    val tint: Color,
    val badgeBg: Color
)

private val SAFETRACK_GUIDE_SLIDES = listOf(
    GuideSlide(
        title = "Welcome to SafeTrack",
        subtitle = "1. What SafeTrack Does",
        bulletPoints = listOf(
            "Tracks child safety between home and school with clear arrival, attendance, and dismissal updates.",
            "Keeps every school's roster, academic reports, and alerts strictly isolated from other schools.",
            "Supports parents, teachers, school admins, and platform billing plans."
        ),
        statusNote = "Live in app today: real-time Firebase Auth + multi-school Firestore isolation.",
        icon = Icons.Filled.Shield,
        tint = SafeBlue700,
        badgeBg = SafeBlue100
    ),
    GuideSlide(
        title = "One Shared App, Role-Based Access",
        subtitle = "2. Parents & Teachers Use Different Permissions",
        bulletPoints = listOf(
            "Everyone installs the same Android app and signs in with Google.",
            "Parents see only their linked children, arrival history, academic reports, and consent settings.",
            "Teachers and School Admins see assigned classes, attendance sheets, and school operations."
        ),
        statusNote = "Enforced on the server by Firestore security rules—not just hidden buttons.",
        icon = Icons.Filled.Groups,
        tint = SafeTeal700,
        badgeBg = SafeTeal100
    ),
    GuideSlide(
        title = "GPS & Manual Movement Updates",
        subtitle = "3. Honest Location Status at Every Step",
        bulletPoints = listOf(
            "GPS and school geofencing are only active when BOTH Android permission and your consent switch are turned ON.",
            "If location is paused, denied, or unavailable, SafeTrack switches to manual movement updates.",
            "Live device tracking stops automatically as soon as safe arrival at school or home is confirmed."
        ),
        statusNote = "Manual arrival and departure logging works even without GPS or on desktop/Wi-Fi tablets.",
        icon = Icons.Filled.GpsFixed,
        tint = SafeBlue900,
        badgeBg = SafeBlue100
    ),
    GuideSlide(
        title = "Teacher-Authorized Dismissal",
        subtitle = "4. Child Safety at Home Time",
        bulletPoints = listOf(
            "Only the teacher assigned to a pupil's class can authorize afternoon dismissal or bulk departure alerts.",
            "School Admins, pickup contacts, and unassigned staff cannot trigger dismissal without teacher assignment.",
            "Every dismissal or late-leaving exception writes an immutable school audit record."
        ),
        statusNote = "Enforced both in the app workflow and inside Firestore server security rules.",
        icon = Icons.Filled.School,
        tint = SafeTeal700,
        badgeBg = SafeTeal100
    ),
    GuideSlide(
        title = "Permissions, Notifications & Help",
        subtitle = "5. Managing Alerts & Public Install Links",
        bulletPoints = listOf(
            "Manage location consent, geofencing, and device notification prompts anytime from Privacy & Alerts.",
            "You can reopen these 5 guide slides anytime using the Help button in the top bar or Settings.",
            "Public app download links require an owner-configured Google Play, Firebase App Distribution, or verified HTTPS page."
        ),
        statusNote = "Not yet implemented without owner setup: remote push servers (FCM backend) and live Play Store listing.",
        icon = Icons.Filled.NotificationsActive,
        tint = SafeBlue700,
        badgeBg = SafeBlue100
    )
)

@Composable
fun OnboardingSlidesModal(
    onDismiss: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { SAFETRACK_GUIDE_SLIDES.size })
    val scope = rememberCoroutineScope()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .widthIn(max = 560.dp)
                .testTag("onboarding_slides_dialog"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = SafeTeal100
                    ) {
                        Text(
                            text = "Slide ${pagerState.currentPage + 1} of ${SAFETRACK_GUIDE_SLIDES.size}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = SafeTeal700,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("onboarding_skip_button")
                    ) {
                        Text("Skip")
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth()
                ) { pageIndex ->
                    val slide = SAFETRACK_GUIDE_SLIDES[pageIndex]
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = slide.badgeBg
                            ) {
                                Icon(
                                    imageVector = slide.icon,
                                    contentDescription = slide.title,
                                    tint = slide.tint,
                                    modifier = Modifier
                                        .padding(12.dp)
                                        .size(28.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = slide.subtitle,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = slide.tint,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = slide.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.semantics { heading() }
                                )
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            slide.bulletPoints.forEach { bullet ->
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = slide.tint,
                                        modifier = Modifier
                                            .padding(top = 2.dp)
                                            .size(16.dp)
                                    )
                                    Text(
                                        text = bullet,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = slide.statusNote,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            )
                        }
                    }
                }

                // Step progress indicators
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(SAFETRACK_GUIDE_SLIDES.size) { index ->
                        val active = pagerState.currentPage == index
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .size(width = if (active) 22.dp else 8.dp, height = 8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (active) SafeBlue700
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                                )
                        )
                    }
                }

                // Navigation Actions (48.dp minimum accessible touch height)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (pagerState.currentPage > 0) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                }
                            },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("onboarding_prev_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Back")
                        }
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }

                    val isLast = pagerState.currentPage == SAFETRACK_GUIDE_SLIDES.lastIndex
                    Button(
                        onClick = {
                            if (isLast) {
                                onDismiss()
                            } else {
                                scope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            }
                        },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("onboarding_next_button")
                    ) {
                        Text(if (isLast) "Get Started" else "Next")
                        if (!isLast) {
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}
