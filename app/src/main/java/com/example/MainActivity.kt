package com.example

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Class
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.local.AppSettingsState
import com.example.data.local.AppSettingsStore
import com.example.data.local.SafeTrackDatabase
import com.example.data.model.DeviceViewLayoutMode
import com.example.data.model.UiState
import com.example.data.model.UserRole
import com.example.data.remote.SafeTrackRepository
import com.example.ui.auth.attemptAutoSignIn
import com.example.ui.auth.authStateFlow
import com.example.ui.auth.signOut
import com.example.ui.components.AppViewAndDownloadDialog
import com.example.ui.components.OnboardingSlidesModal
import com.example.ui.screens.AcademicsScreen
import com.example.ui.screens.ClassesPupilsScreen
import com.example.ui.screens.DailyAttendanceScreen
import com.example.ui.screens.ExecutiveDashboardScreen
import com.example.ui.screens.FinanceScreen
import com.example.ui.screens.MovementTabScreen
import com.example.ui.screens.NotificationsScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.SafetyCommsScreen
import com.example.ui.screens.SettingsManagementScreen
import com.example.ui.screens.SignInScreen
import com.example.ui.screens.StaffInventoryScreen
import com.example.ui.screens.SuperAdminDashboardScreen
import com.example.ui.theme.SafeTrackTheme
import com.example.ui.viewmodel.SafeTrackViewModel
import com.example.util.GeofenceManager
import com.example.util.NotificationHelper
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

private enum class AppSection(
    val key: String,
    val title: String,
    val compactLabel: String,
    val icon: ImageVector
) {
    DASHBOARD("dashboard", "Dashboard", "Home", Icons.Filled.Dashboard),
    CLASSES_PUPILS("classes_pupils", "Classes & Pupils", "Classes", Icons.Filled.Class),
    STAFF_INVENTORY("staff_inventory", "Staff & Inventory", "Staff", Icons.Filled.Badge),
    ATTENDANCE_MOVEMENT("attendance_movement", "Attendance & Movement", "Attend", Icons.Filled.FactCheck),
    ACADEMICS("academics", "Academics", "Academics", Icons.Filled.MenuBook),
    SAFETY_SECURITY("safety_security", "Safety & Security", "Safety", Icons.Filled.Security),
    FINANCE("finance", "Finance", "Finance", Icons.Filled.AccountBalanceWallet),
    NOTIFICATIONS("notifications", "Notifications", "Alerts", Icons.Filled.Notifications),
    SETTINGS("settings", "Settings & App Management", "Settings", Icons.Filled.Settings),
    ONBOARDING("onboarding", "School Onboarding", "Join", Icons.Filled.Apartment);

    companion object {
        fun fromKey(key: String): AppSection = entries.firstOrNull { it.key == key } ?: DASHBOARD
    }
}

class MainActivity : ComponentActivity() {
    private var deepLinkInviteCodeState by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        NotificationHelper.ensureChannel(this)
        deepLinkInviteCodeState = extractInviteCode(intent)

        setContent {
            val context = LocalContext.current
            val settingsStore = remember(context) { AppSettingsStore(context.applicationContext) }
            val appSettings by settingsStore.settingsFlow.collectAsStateWithLifecycle(initialValue = AppSettingsState())

            SafeTrackTheme(
                themeMode = appSettings.themeMode,
                accentPalette = appSettings.accentPalette,
                fontScale = appSettings.fontScale
            ) {
                SafeTrackAppRoot(
                    settingsStore = settingsStore,
                    appSettings = appSettings,
                    deepLinkInviteCode = deepLinkInviteCodeState
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val newCode = extractInviteCode(intent)
        if (!newCode.isNullOrBlank()) {
            deepLinkInviteCodeState = newCode
        }
    }

    private fun extractInviteCode(intent: Intent?): String? {
        val data = intent?.data ?: return null
        val raw = data.getQueryParameter("invite")
            ?: data.getQueryParameter("code")
            ?: return null
        return raw.trim().uppercase().takeIf { it.isNotBlank() }
    }
}

@Composable
fun SafeTrackAppRoot(
    settingsStore: AppSettingsStore,
    appSettings: AppSettingsState,
    deepLinkInviteCode: String? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val currentUser by Firebase.auth.authStateFlow().collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)
    var preAuthLayoutMode by remember(appSettings.deviceLayoutMode) { mutableStateOf(appSettings.deviceLayoutMode) }
    val guidePrefs = remember(context) {
        context.getSharedPreferences("safetrack_ui_prefs", Context.MODE_PRIVATE)
    }
    var showOnboardingGuide by remember {
        mutableStateOf(!guidePrefs.getBoolean("onboarding_guide_seen", false))
    }
    val dismissGuide: () -> Unit = {
        guidePrefs.edit().putBoolean("onboarding_guide_seen", true).apply()
        showOnboardingGuide = false
    }

    LaunchedEffect(Unit) {
        if (Firebase.auth.currentUser == null) {
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {},
                onUnauthenticated = {},
                scope = scope
            )
        }
    }

    val user = currentUser
    if (user == null) {
        val frameWidth = when (preAuthLayoutMode) {
            DeviceViewLayoutMode.PHONE -> 420.dp
            DeviceViewLayoutMode.TABLET -> 860.dp
            DeviceViewLayoutMode.AUTO, DeviceViewLayoutMode.COMPUTER -> 2000.dp
        }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            SignInScreen(
                onAuthSuccess = {},
                deviceViewLayoutMode = preAuthLayoutMode,
                onSelectDeviceLayoutMode = { mode ->
                    preAuthLayoutMode = mode
                    scope.launch { settingsStore.setDeviceLayoutMode(mode) }
                },
                onOpenGuide = { showOnboardingGuide = true },
                modifier = if (preAuthLayoutMode == DeviceViewLayoutMode.PHONE || preAuthLayoutMode == DeviceViewLayoutMode.TABLET) {
                    Modifier.widthIn(max = frameWidth).fillMaxSize()
                } else {
                    Modifier.fillMaxSize()
                }
            )
        }
    } else {
        val uid = user.uid
        val email = user.email.orEmpty()
        val displayName = user.displayName ?: email.substringBefore("@").ifBlank { "SafeTrack User" }
        key(uid) {
            val viewModel: SafeTrackViewModel = viewModel(
                key = uid,
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY])
                        val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                        val offlineDao = SafeTrackDatabase.getInstance(app).offlineAttendanceDao()
                        SafeTrackViewModel(
                            repository = SafeTrackRepository(db, offlineDao),
                            currentUserId = uid,
                            userEmail = email,
                            userDisplayName = displayName,
                            settingsStore = settingsStore
                        )
                    }
                }
            )
            LaunchedEffect(deepLinkInviteCode) {
                if (!deepLinkInviteCode.isNullOrBlank()) {
                    viewModel.setDeepLinkInviteCode(deepLinkInviteCode)
                }
            }
            SafeTrackMainShell(
                viewModel = viewModel,
                onOpenGuide = { showOnboardingGuide = true },
                onSignOut = {
                    scope.launch {
                        runCatching { GeofenceManager.unregisterSchoolGeofence(context) }
                    }
                    signOut(credentialManager = credentialManager, scope = scope)
                }
            )
        }
    }

    if (showOnboardingGuide) {
        OnboardingSlidesModal(onDismiss = dismissGuide)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafeTrackMainShell(
    viewModel: SafeTrackViewModel,
    onOpenGuide: () -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    val profileState by viewModel.userProfile.collectAsStateWithLifecycle()
    val memberships by viewModel.memberships.collectAsStateWithLifecycle()
    val plans by viewModel.subscriptionPlans.collectAsStateWithLifecycle()
    val pendingInvites by viewModel.pendingEmailInvites.collectAsStateWithLifecycle()
    val activeSchool by viewModel.activeSchool.collectAsStateWithLifecycle()
    val allSchools by viewModel.allSchoolsForSuperAdmin.collectAsStateWithLifecycle()
    val classes by viewModel.classes.collectAsStateWithLifecycle()
    val students by viewModel.students.collectAsStateWithLifecycle()
    val journeys by viewModel.journeys.collectAsStateWithLifecycle()
    val attendance by viewModel.attendanceRecords.collectAsStateWithLifecycle()
    val pendingOffline by viewModel.pendingOfflineAttendance.collectAsStateWithLifecycle()
    val incidents by viewModel.incidents.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val schoolMembers by viewModel.schoolMembers.collectAsStateWithLifecycle()
    val schoolInvites by viewModel.schoolInvites.collectAsStateWithLifecycle()
    val auditLogs by viewModel.auditLogs.collectAsStateWithLifecycle()
    val subjects by viewModel.subjects.collectAsStateWithLifecycle()
    val examReports by viewModel.examReports.collectAsStateWithLifecycle()
    val inventoryItems by viewModel.inventoryItems.collectAsStateWithLifecycle()
    val feeRecords by viewModel.feeRecords.collectAsStateWithLifecycle()
    val deviceSessions by viewModel.deviceSessions.collectAsStateWithLifecycle()
    val appSettings by viewModel.appSettings.collectAsStateWithLifecycle()
    val lastBackupInspection by viewModel.lastBackupInspection.collectAsStateWithLifecycle()
    val bannerMessage by viewModel.bannerMessage.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val isOfflineMode by viewModel.isOfflineCaptureMode.collectAsStateWithLifecycle()
    val geofenceStatus by viewModel.geofenceStatus.collectAsStateWithLifecycle()
    val perimeterCheckStatus by viewModel.perimeterCheckStatus.collectAsStateWithLifecycle()
    val lastGpsAccuracyMeters by viewModel.lastGpsAccuracyMeters.collectAsStateWithLifecycle()
    val deviceViewLayoutMode by viewModel.deviceViewLayoutMode.collectAsStateWithLifecycle()
    val appDistributionConfig by viewModel.appDistributionConfig.collectAsStateWithLifecycle()
    val pendingDeepLinkInviteCode by viewModel.pendingDeepLinkInviteCode.collectAsStateWithLifecycle()
    val deepLinkInviteResult by viewModel.deepLinkInviteResult.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.refreshGeofenceStatus(context)
    }

    val seenNotifIds = remember { mutableSetOf<String>() }
    LaunchedEffect(notifications, appSettings) {
        val nowMs = System.currentTimeMillis()
        notifications.forEach { notif ->
            if (notif.id.isNotBlank() && seenNotifIds.add(notif.id) && !notif.isRead) {
                val createdMs = notif.createdAt?.toDate()?.time
                val isRecent = createdMs == null || (nowMs - createdMs) <= 90_000L
                val allowCategory = when (notif.type) {
                    "geofence_enter", "geofence_exit" -> appSettings.notifyGeofence
                    "attendance" -> appSettings.notifyAttendance
                    "journey_started", "journey", "dismissed", "late_departure" -> appSettings.notifyMovement
                    "emergency", "incident" -> appSettings.notifyIncidents
                    "academic", "report_card" -> appSettings.notifyAcademics
                    "message" -> appSettings.notifyComms
                    else -> true
                }
                if (isRecent && allowCategory) {
                    NotificationHelper.showAlert(
                        context = context,
                        title = notif.title,
                        body = notif.body,
                        notificationId = notif.id.hashCode()
                    )
                }
            }
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(bannerMessage) {
        bannerMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearBanner()
        }
    }

    val profile = (profileState as? UiState.Success)?.data
    val activeSchoolId = profile?.activeSchoolId.orEmpty()
    val activeRole = UserRole.fromKey(profile?.activeRole ?: UserRole.PARENT.key)

    var selectedSection by remember { mutableStateOf(AppSection.DASHBOARD) }
    var settingsCategoryIndex by remember { mutableIntStateOf(0) }
    var attendanceMovementSubTab by remember { mutableIntStateOf(0) }
    var showSchoolSwitcher by remember { mutableStateOf(false) }
    var showAppDistributionDialog by remember { mutableStateOf(false) }
    var showAllModulesSheet by remember { mutableStateOf(false) }

    LaunchedEffect(pendingDeepLinkInviteCode) {
        if (!pendingDeepLinkInviteCode.isNullOrBlank()) {
            selectedSection = AppSection.ONBOARDING
        }
    }

    val showWorkspace = activeSchoolId.isNotBlank() || activeRole == UserRole.SUPER_ADMIN
    val canAccessStaffAndClasses = activeRole in listOf(UserRole.TEACHER, UserRole.SCHOOL_ADMIN, UserRole.SUPER_ADMIN)
    val canAccessDailyAttendance = activeSchoolId.isNotBlank() && canAccessStaffAndClasses

    val visibleSections = remember(activeRole, showWorkspace) {
        if (!showWorkspace) listOf(AppSection.DASHBOARD, AppSection.SETTINGS)
        else buildList {
            add(AppSection.DASHBOARD)
            if (canAccessStaffAndClasses) {
                add(AppSection.CLASSES_PUPILS)
                add(AppSection.STAFF_INVENTORY)
            }
            add(AppSection.ATTENDANCE_MOVEMENT)
            if (activeRole != UserRole.PICKUP_PERSON) {
                add(AppSection.ACADEMICS)
            }
            add(AppSection.SAFETY_SECURITY)
            if (activeRole != UserRole.PICKUP_PERSON) {
                add(AppSection.FINANCE)
            }
            add(AppSection.NOTIFICATIONS)
            add(AppSection.SETTINGS)
        }
    }

    if (selectedSection !in visibleSections && selectedSection != AppSection.ONBOARDING) {
        LaunchedEffect(visibleSections) {
            selectedSection = AppSection.DASHBOARD
        }
    }

    if (selectedSection != AppSection.DASHBOARD) {
        BackHandler { selectedSection = AppSection.DASHBOARD }
    }

    val unreadCount = notifications.count { !it.isRead }
    val unreadMovementCount = notifications.count {
        !it.isRead && it.type in listOf("journey", "journey_started", "dismissed", "late_departure", "geofence_enter", "geofence_exit")
    }
    val canEditDistributionSettings =
        activeRole == UserRole.SCHOOL_ADMIN || activeRole == UserRole.SUPER_ADMIN || viewModel.isSuperAdmin

    val shellContainerModifier = when (deviceViewLayoutMode) {
        DeviceViewLayoutMode.PHONE -> Modifier.widthIn(max = 420.dp).fillMaxSize()
        DeviceViewLayoutMode.TABLET -> Modifier.widthIn(max = 860.dp).fillMaxSize()
        DeviceViewLayoutMode.AUTO, DeviceViewLayoutMode.COMPUTER -> Modifier.fillMaxSize()
    }

    val navigateTo: (AppSection) -> Unit = { target ->
        selectedSection = target
        scope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = showWorkspace,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(280.dp)) {
                DrawerMenuContent(
                    schoolName = activeSchool?.name ?: "Cefelix SafeTrack",
                    roleLabel = "${activeRole.label} • ${profile?.displayName.orEmpty()}",
                    sections = visibleSections,
                    selectedSection = selectedSection,
                    unreadNotifications = unreadCount,
                    unreadMovements = unreadMovementCount,
                    onSelect = navigateTo
                )
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Scaffold(
                modifier = shellContainerModifier,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            titleContentColor = MaterialTheme.colorScheme.onPrimary,
                            navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                            actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        navigationIcon = {
                            if (showWorkspace) {
                                IconButton(
                                    onClick = { scope.launch { drawerState.open() } },
                                    modifier = Modifier.testTag("open_navigation_drawer_button")
                                ) {
                                    Icon(Icons.Filled.Menu, contentDescription = "Open navigation menu")
                                }
                            }
                        },
                        title = {
                            Column {
                                Text(
                                    text = when {
                                        activeRole == UserRole.SUPER_ADMIN && activeSchoolId.isBlank() && selectedSection == AppSection.DASHBOARD ->
                                            "SafeTrack Super Admin"
                                        activeSchool != null -> activeSchool!!.name
                                        else -> "SafeTrack Onboarding"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${selectedSection.title} • ${activeRole.label}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        },
                        actions = {
                            if (memberships.isNotEmpty() || viewModel.isSuperAdmin) {
                                Box {
                                    IconButton(onClick = { showSchoolSwitcher = true }) {
                                        Icon(Icons.Filled.SwapHoriz, contentDescription = "Switch school or role")
                                    }
                                    DropdownMenu(
                                        expanded = showSchoolSwitcher,
                                        onDismissRequest = { showSchoolSwitcher = false }
                                    ) {
                                        if (viewModel.isSuperAdmin) {
                                            DropdownMenuItem(
                                                text = { Text("Super Admin Platform Console") },
                                                leadingIcon = { Icon(Icons.Filled.AdminPanelSettings, contentDescription = null) },
                                                onClick = {
                                                    viewModel.switchContext("", UserRole.SUPER_ADMIN)
                                                    selectedSection = AppSection.DASHBOARD
                                                    showSchoolSwitcher = false
                                                }
                                            )
                                        }
                                        memberships.forEach { m ->
                                            DropdownMenuItem(
                                                text = { Text("${m.schoolName} (${UserRole.fromKey(m.role).label})") },
                                                leadingIcon = { Icon(Icons.Filled.Apartment, contentDescription = null) },
                                                onClick = {
                                                    viewModel.switchContext(m.schoolId, UserRole.fromKey(m.role))
                                                    selectedSection = AppSection.DASHBOARD
                                                    showSchoolSwitcher = false
                                                }
                                            )
                                        }
                                        DropdownMenuItem(
                                            text = { Text("+ Register / Join Another School") },
                                            onClick = {
                                                viewModel.switchContext("", UserRole.PARENT)
                                                selectedSection = AppSection.ONBOARDING
                                                showSchoolSwitcher = false
                                            }
                                        )
                                    }
                                }
                            }
                            IconButton(
                                onClick = { showAppDistributionDialog = true },
                                modifier = Modifier.testTag("app_share_view_access_button")
                            ) {
                                Icon(Icons.Filled.Share, contentDescription = "Share download URL and change app view access")
                            }
                            IconButton(
                                onClick = onOpenGuide,
                                modifier = Modifier.testTag("top_bar_help_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = "Open SafeTrack 5-slide guide")
                            }
                            IconButton(onClick = onSignOut) {
                                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign out")
                            }
                        }
                    )
                },
                bottomBar = {
                    if (showWorkspace) {
                        BoxWithConstraints {
                            val showBottomBar = when (deviceViewLayoutMode) {
                                DeviceViewLayoutMode.PHONE -> true
                                DeviceViewLayoutMode.TABLET, DeviceViewLayoutMode.COMPUTER -> false
                                DeviceViewLayoutMode.AUTO -> maxWidth < 600.dp
                            }
                            if (showBottomBar) {
                                val primarySlots = remember(activeRole) {
                                    buildList {
                                        add(AppSection.DASHBOARD)
                                        add(AppSection.ATTENDANCE_MOVEMENT)
                                        if (activeRole != UserRole.PICKUP_PERSON) add(AppSection.ACADEMICS)
                                        add(AppSection.SAFETY_SECURITY)
                                    }
                                }
                                NavigationBar(modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
                                    primarySlots.forEach { sec ->
                                        val badgeNum = when (sec) {
                                            AppSection.ATTENDANCE_MOVEMENT -> unreadMovementCount
                                            AppSection.SAFETY_SECURITY -> unreadCount
                                            else -> 0
                                        }
                                        NavigationBarItem(
                                            selected = selectedSection == sec,
                                            onClick = { selectedSection = sec },
                                            icon = {
                                                BadgedBox(badge = { if (badgeNum > 0) Badge { Text(badgeNum.toString()) } }) {
                                                    Icon(sec.icon, contentDescription = sec.title)
                                                }
                                            },
                                            label = { Text(sec.compactLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                        )
                                    }
                                    val isInMore = selectedSection !in primarySlots && selectedSection != AppSection.ONBOARDING
                                    NavigationBarItem(
                                        selected = isInMore,
                                        onClick = { showAllModulesSheet = true },
                                        icon = {
                                            BadgedBox(badge = { if (unreadCount > 0) Badge { Text(unreadCount.toString()) } }) {
                                                Icon(Icons.Filled.MoreHoriz, contentDescription = "All modules")
                                            }
                                        },
                                        label = { Text("Modules", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        modifier = Modifier.testTag("bottom_bar_more_modules_button")
                                    )
                                }
                            }
                        }
                    }
                }
            ) { innerPadding ->
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    val isDesktopWide = showWorkspace && when (deviceViewLayoutMode) {
                        DeviceViewLayoutMode.COMPUTER -> true
                        DeviceViewLayoutMode.PHONE, DeviceViewLayoutMode.TABLET -> false
                        DeviceViewLayoutMode.AUTO -> maxWidth >= 1024.dp
                    }
                    val isTabletRail = showWorkspace && !isDesktopWide && when (deviceViewLayoutMode) {
                        DeviceViewLayoutMode.TABLET -> true
                        DeviceViewLayoutMode.PHONE, DeviceViewLayoutMode.COMPUTER -> false
                        DeviceViewLayoutMode.AUTO -> maxWidth >= 600.dp
                    }

                    Row(modifier = Modifier.fillMaxSize()) {
                        if (isDesktopWide) {
                            PermanentDrawerSheet(modifier = Modifier.width(244.dp).fillMaxHeight()) {
                                DrawerMenuContent(
                                    schoolName = activeSchool?.name ?: "Cefelix SafeTrack",
                                    roleLabel = activeRole.label,
                                    sections = visibleSections,
                                    selectedSection = selectedSection,
                                    unreadNotifications = unreadCount,
                                    unreadMovements = unreadMovementCount,
                                    onSelect = { selectedSection = it }
                                )
                            }
                            VerticalDivider()
                        } else if (isTabletRail) {
                            NavigationRail(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(92.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                visibleSections.forEach { sec ->
                                    val badgeNum = when (sec) {
                                        AppSection.NOTIFICATIONS -> unreadCount
                                        AppSection.ATTENDANCE_MOVEMENT -> unreadMovementCount
                                        else -> 0
                                    }
                                    NavigationRailItem(
                                        selected = selectedSection == sec,
                                        onClick = { selectedSection = sec },
                                        icon = {
                                            BadgedBox(badge = { if (badgeNum > 0) Badge { Text(badgeNum.toString()) } }) {
                                                Icon(sec.icon, contentDescription = sec.title)
                                            }
                                        },
                                        label = { Text(sec.compactLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                    )
                                }
                            }
                            VerticalDivider()
                        }

                        when {
                            profileState is UiState.Loading -> {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            }
                            selectedSection == AppSection.ONBOARDING || (activeSchoolId.isBlank() && activeRole != UserRole.SUPER_ADMIN) -> {
                                OnboardingScreen(
                                    plans = plans,
                                    pendingInvites = pendingInvites,
                                    isSuperAdmin = viewModel.isSuperAdmin,
                                    isBusy = isBusy,
                                    initialInviteCode = pendingDeepLinkInviteCode.orEmpty(),
                                    inviteInspectionResult = deepLinkInviteResult,
                                    appDistributionConfig = appDistributionConfig,
                                    deviceViewLayoutMode = deviceViewLayoutMode,
                                    onOpenShareDialog = { showAppDistributionDialog = true },
                                    onDismissInviteInspection = { viewModel.consumeDeepLinkInviteCode() },
                                    onRegisterSchool = { name, city, country, addr, phone, email, plan, cycle ->
                                        viewModel.registerNewSchool(name, city, country, addr, phone, email, plan, cycle)
                                        viewModel.consumeDeepLinkInviteCode()
                                        selectedSection = AppSection.DASHBOARD
                                    },
                                    onClaimInvite = { code ->
                                        viewModel.claimInvite(code) { selectedSection = AppSection.DASHBOARD }
                                    },
                                    onOpenSuperAdmin = {
                                        viewModel.switchContext("", UserRole.SUPER_ADMIN)
                                        selectedSection = AppSection.DASHBOARD
                                    }
                                )
                            }
                            selectedSection == AppSection.DASHBOARD -> {
                                if (activeRole == UserRole.SUPER_ADMIN && activeSchoolId.isBlank()) {
                                    SuperAdminDashboardScreen(
                                        schools = allSchools,
                                        plans = plans,
                                        auditLogs = auditLogs,
                                        isBusy = isBusy,
                                        onSavePlan = { viewModel.saveSubscriptionPlan(it) },
                                        onUpdateSchoolStatus = { sch, st ->
                                            viewModel.updateSchoolSubscription(sch, null, sch.billingCycle, st, sch.paymentProvider, sch.paymentReference)
                                        },
                                        onInspectSchool = { sch -> viewModel.switchContext(sch.id, UserRole.SUPER_ADMIN) },
                                        appDistributionConfig = appDistributionConfig,
                                        deviceViewLayoutMode = deviceViewLayoutMode,
                                        onOpenAppAccessDialog = { showAppDistributionDialog = true }
                                    )
                                } else {
                                    ExecutiveDashboardScreen(
                                        role = activeRole,
                                        displayName = profile?.displayName ?: "User",
                                        school = activeSchool,
                                        students = students,
                                        members = schoolMembers,
                                        attendance = attendance,
                                        journeys = journeys,
                                        incidents = incidents,
                                        notifications = notifications,
                                        auditLogs = auditLogs,
                                        feeRecords = feeRecords,
                                        geofenceStatus = geofenceStatus,
                                        lastGpsAccuracyMeters = lastGpsAccuracyMeters,
                                        appSettings = appSettings,
                                        onNavigateSection = { key -> selectedSection = AppSection.fromKey(key) },
                                        onOpenSettingsTab = {
                                            settingsCategoryIndex = 0
                                            selectedSection = AppSection.SETTINGS
                                        },
                                        onMarkNotificationRead = { viewModel.markNotificationRead(it) }
                                    )
                                }
                            }
                            selectedSection == AppSection.CLASSES_PUPILS -> {
                                ClassesPupilsScreen(
                                    role = activeRole,
                                    school = activeSchool,
                                    classes = classes,
                                    students = students,
                                    members = schoolMembers,
                                    isBusy = isBusy,
                                    onCreateClass = { name, grade, year -> viewModel.createClass(name, grade, year, emptyList()) },
                                    onEnrollStudent = { adm, name, cls, emN, emP, med, dest, pick, photo, selfLink ->
                                        viewModel.enrollStudent(adm, name, cls, emN, emP, med, dest, pick, photo, selfLink)
                                    },
                                    onUpdateStudentProfile = { stu, adm, name, cls, emN, emP, med, dests, pickups ->
                                        viewModel.updateStudentProfile(stu, adm, name, cls, emN, emP, med, dests, pickups)
                                    },
                                    onDeleteStudent = { viewModel.deleteStudent(it) },
                                    onUpdateStudentLifecycle = { stu, st -> viewModel.updateStudentLifecycle(stu, st) },
                                    onLinkMemberToStudent = { stu, mUid, r -> viewModel.linkMemberToStudent(stu, mUid, r) }
                                )
                            }
                            selectedSection == AppSection.STAFF_INVENTORY -> {
                                StaffInventoryScreen(
                                    role = activeRole,
                                    school = activeSchool,
                                    classes = classes,
                                    subjects = subjects,
                                    members = schoolMembers,
                                    invites = schoolInvites,
                                    inventoryItems = inventoryItems,
                                    isBusy = isBusy,
                                    onCreateInvite = { email, r -> viewModel.createInvite(email, r) },
                                    onSaveTeacherMember = { existing, name, email, phone, memberRole, classIds, subjectIds, subjectNames, status ->
                                        viewModel.saveTeacherMember(existing, name, email, phone, memberRole, classIds, subjectIds, subjectNames, status)
                                    },
                                    onDeleteTeacherMember = { viewModel.deleteTeacherMember(it) },
                                    onSaveInventoryItem = { existing, name, cat, qty, min, cond, assigned, notes ->
                                        viewModel.saveInventoryItem(existing, name, cat, qty, min, cond, assigned, notes)
                                    },
                                    onDeleteInventoryItem = { viewModel.deleteInventoryItem(it) }
                                )
                            }
                            selectedSection == AppSection.ATTENDANCE_MOVEMENT -> {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    if (canAccessDailyAttendance) {
                                        TabRow(selectedTabIndex = attendanceMovementSubTab) {
                                            Tab(selected = attendanceMovementSubTab == 0, onClick = { attendanceMovementSubTab = 0 }, text = { Text("Daily Attendance") })
                                            Tab(selected = attendanceMovementSubTab == 1, onClick = { attendanceMovementSubTab = 1 }, text = { Text("Movement & Geofencing") })
                                        }
                                    }
                                    if (canAccessDailyAttendance && attendanceMovementSubTab == 0) {
                                        DailyAttendanceScreen(
                                            school = activeSchool,
                                            classes = classes,
                                            students = students,
                                            attendance = attendance,
                                            pendingOffline = pendingOffline,
                                            isOfflineCaptureMode = isOfflineMode,
                                            isBusy = isBusy,
                                            onToggleOfflineMode = { viewModel.toggleOfflineCaptureMode(it) },
                                            onSyncOffline = { viewModel.syncOfflineQueue() },
                                            onRecordAttendance = { s, date, st, reason -> viewModel.recordAttendance(s, date, st, reason) },
                                            onMarkAllPresent = { list, date -> viewModel.markAllPresentForDate(list, date) }
                                        )
                                    } else {
                                        MovementTabScreen(
                                            role = activeRole,
                                            currentUserId = viewModel.signedInUserId,
                                            school = activeSchool,
                                            classes = classes,
                                            students = students,
                                            journeys = journeys,
                                            notifications = notifications,
                                            geofenceStatus = geofenceStatus,
                                            perimeterCheckStatus = perimeterCheckStatus,
                                            isBusy = isBusy,
                                            lastGpsAccuracyMeters = lastGpsAccuracyMeters,
                                            onStartMorningJourney = { stu, dest, note -> viewModel.startMorningJourney(context, stu, dest, note) },
                                            onConfirmArrivalAtSchool = { j, s, late -> viewModel.confirmPhysicalArrival(context, j, s, late) },
                                            onInitiateAfternoonDismissal = { stu, dest, alt, note ->
                                                viewModel.initiateAfternoonDismissal(context, stu, dest, alt, note)
                                            },
                                            onDispatchClassDeparture = { entries, notifyLate ->
                                                viewModel.dispatchClassDeparture(context, entries, notifyLate)
                                            },
                                            onConfirmSafeArrivalHome = { j -> viewModel.confirmSafeArrivalHome(context, j) },
                                            onUpdateJourneyStatus = { j, st, note -> viewModel.updateJourneyStatus(context, j, st, note) },
                                            onMarkNotificationRead = { viewModel.markNotificationRead(it) },
                                            onEnableGeofenceMonitoring = { target -> viewModel.enableSchoolGeofenceMonitoring(context, target) },
                                            onDisableGeofenceMonitoring = { viewModel.disableSchoolGeofenceMonitoring(context) },
                                            onCheckPerimeterNow = { sch -> viewModel.checkSchoolPerimeterNow(context, sch) },
                                            onCaptureSchoolGps = { onCap -> viewModel.captureSchoolLocationForGeofence(context, onCap) },
                                            onSaveSchoolGeofence = { lat, lng, rad, en -> viewModel.updateSchoolGeofence(lat, lng, rad, en) }
                                        )
                                    }
                                }
                            }
                            selectedSection == AppSection.ACADEMICS -> {
                                AcademicsScreen(
                                    role = activeRole,
                                    school = activeSchool,
                                    classes = classes,
                                    students = students,
                                    members = schoolMembers,
                                    subjects = subjects,
                                    examReports = examReports,
                                    attendance = attendance,
                                    currentUserDisplayName = profile?.displayName ?: "Class Teacher",
                                    isBusy = isBusy,
                                    onUpdateSchoolProfile = { name, city, phone, email, logo ->
                                        viewModel.updateSchoolProfile(name, city, phone, email, logo)
                                    },
                                    onEnrollStudent = { adm, name, cls, emN, emP, med, dest, pick, photo, selfLink ->
                                        viewModel.enrollStudent(adm, name, cls, emN, emP, med, dest, pick, photo, selfLink)
                                    },
                                    onUpdateStudentProfile = { stu, adm, name, cls, emN, emP, med, dests, pickups ->
                                        viewModel.updateStudentProfile(stu, adm, name, cls, emN, emP, med, dests, pickups)
                                    },
                                    onDeleteStudent = { viewModel.deleteStudent(it) },
                                    onBulkImportStudents = { cls, rows, linkSelf -> viewModel.bulkImportStudents(cls, rows, linkSelf) },
                                    onSaveTeacherMember = { existing, name, email, phone, memberRole, classIds, subjectIds, subjectNames, status ->
                                        viewModel.saveTeacherMember(existing, name, email, phone, memberRole, classIds, subjectIds, subjectNames, status)
                                    },
                                    onDeleteTeacherMember = { viewModel.deleteTeacherMember(it) },
                                    onSaveSubject = { existing, name, code, level, isCore, slot ->
                                        viewModel.saveSubject(existing, name, code, level, isCore, slot)
                                    },
                                    onDeleteSubject = { viewModel.deleteSubject(it) },
                                    onSeedDefaultSubjects = { viewModel.seedDefaultSubjects() },
                                    onSaveExamReport = { report, autoRank -> viewModel.saveExamReport(report, autoRank) },
                                    onExportExamReportPdf = { viewModel.exportExamReportPdf(context, it) },
                                    onExportBlankMasterPdf = { viewModel.exportBlankMasterReportPdf(context) },
                                    onApplyCefelixTemplate = { viewModel.applyCefelixAcademyMasterSetup() }
                                )
                            }
                            selectedSection == AppSection.SAFETY_SECURITY -> {
                                SafetyCommsScreen(
                                    role = activeRole,
                                    students = students,
                                    notifications = notifications,
                                    messages = messages,
                                    incidents = incidents,
                                    locationConsentGranted = profile?.locationConsentGranted == true,
                                    isBusy = isBusy,
                                    onToggleConsent = { viewModel.setLocationConsent(it) },
                                    onMarkNotificationRead = { viewModel.markNotificationRead(it) },
                                    onSendMessage = { stu, body -> viewModel.sendMessage(stu, body) },
                                    onReportIncident = { stu, jId, sev, title, desc ->
                                        viewModel.reportIncident(context, stu, jId, sev, title, desc)
                                    },
                                    onResolveIncident = { inc, st -> viewModel.resolveIncident(inc, st) },
                                    onDeleteAccount = { viewModel.deleteMyAccount(context) { onSignOut() } },
                                    onOpenGuide = onOpenGuide
                                )
                            }
                            selectedSection == AppSection.FINANCE -> {
                                FinanceScreen(
                                    role = activeRole,
                                    school = activeSchool,
                                    students = students,
                                    feeRecords = feeRecords,
                                    plans = plans,
                                    isBusy = isBusy,
                                    onSaveFeeRecord = { stu, existing, title, term, year, due, paid, method, receipt, dueDate ->
                                        viewModel.saveFeeRecord(stu, existing, title, term, year, due, paid, method, receipt, dueDate)
                                    },
                                    onDeleteFeeRecord = { viewModel.deleteFeeRecord(it) },
                                    onUpdateSubscription = { sch, plan, cycle, prov, ref ->
                                        viewModel.updateSchoolSubscription(sch, plan, cycle, null, prov, ref)
                                    }
                                )
                            }
                            selectedSection == AppSection.NOTIFICATIONS -> {
                                NotificationsScreen(
                                    notifications = notifications,
                                    onMarkNotificationRead = { viewModel.markNotificationRead(it) },
                                    onOpenNotificationPreferences = {
                                        settingsCategoryIndex = 3
                                        selectedSection = AppSection.SETTINGS
                                    }
                                )
                            }
                            selectedSection == AppSection.SETTINGS -> {
                                SettingsManagementScreen(
                                    initialCategoryIndex = settingsCategoryIndex,
                                    role = activeRole,
                                    profile = profile,
                                    school = activeSchool,
                                    appSettings = appSettings,
                                    appDistributionConfig = appDistributionConfig,
                                    availableInviteCodes = schoolInvites.filter { it.status == "pending" }.map { it.inviteCode },
                                    deviceSessions = deviceSessions,
                                    lastBackupInspection = lastBackupInspection,
                                    recordSummaryCounts = listOf(
                                        "Classes" to classes.size,
                                        "Pupils" to students.size,
                                        "Attendance" to attendance.size,
                                        "Exams" to examReports.size,
                                        "Fees" to feeRecords.size,
                                        "Inventory" to inventoryItems.size
                                    ),
                                    isBusy = isBusy,
                                    onUpdateThemeMode = { viewModel.updateThemeMode(it) },
                                    onUpdateAccentPalette = { viewModel.updateAccentPalette(it) },
                                    onUpdateFontScale = { viewModel.updateFontScale(it) },
                                    onUpdateCompactCards = { viewModel.updateCompactCards(it) },
                                    onUpdateDashboardWidgets = { a, g -> viewModel.updateDashboardWidgets(a, g) },
                                    onSelectDeviceLayoutMode = { viewModel.setDeviceViewLayoutMode(it) },
                                    onRestoreDefaults = { viewModel.restoreDefaultSettings() },
                                    onUpdateSchoolInfo = { name, city, addr, phone, email, logo, motto, yr, term, hrs ->
                                        viewModel.updateSchoolProfile(name, city, phone, email, logo, addr, motto, yr, term, hrs)
                                    },
                                    onUpdateMyProfileDetails = { dn, ph -> viewModel.updateMyProfileDetails(dn, ph) },
                                    onToggleLocationConsent = { viewModel.setLocationConsent(it) },
                                    onRevokeDeviceSession = { viewModel.revokeDeviceSession(it) },
                                    onUpdateNotificationSetting = { cat, en -> viewModel.updateNotificationSetting(cat, en) },
                                    onUpdateAssessmentWeight = { viewModel.updateAssessmentWeight(it) },
                                    onNavigateAcademics = { selectedSection = AppSection.ACADEMICS },
                                    onNavigateFinance = { selectedSection = AppSection.FINANCE },
                                    onExportBackupJson = { viewModel.exportFullSchoolBackup(context) },
                                    onInspectBackupJson = { viewModel.inspectBackupJsonText(it) },
                                    onExportPdf = { viewModel.exportPdfReport(context, it) },
                                    onExportCsv = { viewModel.exportCsvReport(context, it) },
                                    onClearExportCache = { viewModel.clearLocalExportCache(context) },
                                    onSaveDistributionConfig = { url, mode, devices, notes, playUrl, appleUrl, apkUrl ->
                                        viewModel.saveAppDistributionConfig(
                                            downloadUrl = url,
                                            viewAccessMode = mode,
                                            allowedDeviceTypes = devices,
                                            releaseNotes = notes,
                                            useGlobalConfig = (activeRole == UserRole.SUPER_ADMIN || activeSchoolId.isBlank()),
                                            playStoreUrl = playUrl,
                                            appStoreUrl = appleUrl,
                                            apkDownloadUrl = apkUrl
                                        )
                                    },
                                    onOpenGuide = onOpenGuide,
                                    onSignOut = onSignOut,
                                    onDeleteAccount = { viewModel.deleteMyAccount(context) { onSignOut() } }
                                )
                            }
                        }
                    }
                }
            }

            if (showAllModulesSheet) {
                ModalBottomSheet(onDismissRequest = { showAllModulesSheet = false }) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "All SafeTrack Modules",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                        visibleSections.forEach { sec ->
                            val badge = when (sec) {
                                AppSection.NOTIFICATIONS -> unreadCount
                                AppSection.ATTENDANCE_MOVEMENT -> unreadMovementCount
                                else -> 0
                            }
                            Surface(
                                onClick = {
                                    selectedSection = sec
                                    showAllModulesSheet = false
                                },
                                color = if (selectedSection == sec) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                                shape = MaterialTheme.shapes.small
                            ) {
                                ListItem(
                                    headlineContent = { Text(sec.title, fontWeight = FontWeight.Medium) },
                                    leadingContent = { Icon(sec.icon, contentDescription = null) },
                                    trailingContent = {
                                        if (badge > 0) Badge { Text(badge.toString()) }
                                    }
                                )
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }

            if (showAppDistributionDialog) {
                AppViewAndDownloadDialog(
                    config = appDistributionConfig,
                    currentDeviceLayoutMode = deviceViewLayoutMode,
                    canEditAccessSettings = canEditDistributionSettings,
                    isBusy = isBusy,
                    availableInviteCodes = schoolInvites.filter { it.status == "pending" }.map { it.inviteCode },
                    onSelectDeviceLayoutMode = { viewModel.setDeviceViewLayoutMode(it) },
                    onSaveConfig = { url, mode, devices, notes ->
                        viewModel.saveAppDistributionConfig(
                            downloadUrl = url,
                            viewAccessMode = mode,
                            allowedDeviceTypes = devices,
                            releaseNotes = notes,
                            useGlobalConfig = (activeRole == UserRole.SUPER_ADMIN || activeSchoolId.isBlank())
                        )
                    },
                    onDismiss = { showAppDistributionDialog = false }
                )
            }
        }
    }
}

@Composable
private fun DrawerMenuContent(
    schoolName: String,
    roleLabel: String,
    sections: List<AppSection>,
    selectedSection: AppSection,
    unreadNotifications: Int,
    unreadMovements: Int,
    onSelect: (AppSection) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
            Text(schoolName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(roleLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        sections.forEach { sec ->
            val badgeCount = when (sec) {
                AppSection.NOTIFICATIONS -> unreadNotifications
                AppSection.ATTENDANCE_MOVEMENT -> unreadMovements
                else -> 0
            }
            NavigationDrawerItem(
                selected = selectedSection == sec,
                onClick = { onSelect(sec) },
                icon = { Icon(sec.icon, contentDescription = null, modifier = Modifier.size(20.dp)) },
                label = { Text(sec.title) },
                badge = if (badgeCount > 0) {
                    { Badge { Text(badgeCount.toString()) } }
                } else null,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
            )
        }
    }
}
