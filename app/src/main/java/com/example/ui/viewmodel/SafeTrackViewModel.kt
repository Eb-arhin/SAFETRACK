package com.example.ui.viewmodel

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.local.AccentPalette
import com.example.data.local.AppSettingsState
import com.example.data.local.AppSettingsStore
import com.example.data.local.AppThemeMode
import com.example.data.local.OfflineAttendanceEntity
import com.example.data.model.AppDistributionConfig
import com.example.data.model.AppNotification
import com.example.data.model.AppViewAccessMode
import com.example.data.model.AttendanceRecord
import com.example.data.model.AttendanceStatus
import com.example.data.model.AuditLog
import com.example.data.model.CefelixAcademyTemplate
import com.example.data.model.ClassDepartureEntry
import com.example.data.model.DeviceViewLayoutMode
import com.example.data.model.ExamGrading
import com.example.data.model.ExamReport
import com.example.data.model.FeeRecord
import com.example.data.model.Incident
import com.example.data.model.InventoryItem
import com.example.data.model.InviteInspectionResult
import com.example.data.model.InviteInspectionStatus
import com.example.data.model.Journey
import com.example.data.model.MembershipIndex
import com.example.data.model.Message
import com.example.data.model.MovementStatus
import com.example.data.model.School
import com.example.data.model.SchoolClass
import com.example.data.model.SchoolInvite
import com.example.data.model.SchoolMember
import com.example.data.model.Student
import com.example.data.model.Subject
import com.example.data.model.SubscriptionPlan
import com.example.data.model.UiState
import com.example.data.model.UserDeviceSession
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.data.remote.SafeTrackRepository
import com.example.util.AppLinkValidator
import com.example.util.BackupSummary
import com.example.util.GeofenceManager
import com.example.util.GeofenceMonitoringConfig
import com.example.util.GpsFetchResult
import com.example.util.LocationTracker
import com.example.util.NotificationHelper
import com.example.util.ParsedStudentRow
import com.example.util.ReportGenerator
import com.example.util.ReportType
import com.example.util.SchoolBackupExporter
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class SafeTrackViewModel(
    private val repository: SafeTrackRepository,
    private val currentUserId: String,
    private val userEmail: String,
    private val userDisplayName: String,
    private val settingsStore: AppSettingsStore? = null
) : ViewModel() {

    val isSuperAdmin: Boolean = userEmail.equals(SUPER_ADMIN_EMAIL, ignoreCase = true)
    val signedInUserId: String get() = currentUserId

    private val _bannerMessage = MutableStateFlow<String?>(null)
    val bannerMessage: StateFlow<String?> = _bannerMessage.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _isOfflineCaptureMode = MutableStateFlow(false)
    val isOfflineCaptureMode: StateFlow<Boolean> = _isOfflineCaptureMode.asStateFlow()

    private val _gpsStatusLabel = MutableStateFlow("GPS idle — active only when permitted and enabled")
    val gpsStatusLabel: StateFlow<String> = _gpsStatusLabel.asStateFlow()

    val geofenceStatus: StateFlow<GeofenceMonitoringConfig> = GeofenceManager.monitoringStatus

    private val _perimeterCheckStatus = MutableStateFlow<String?>(null)
    val perimeterCheckStatus: StateFlow<String?> = _perimeterCheckStatus.asStateFlow()

    private val _lastGpsAccuracyMeters = MutableStateFlow<Float?>(null)
    val lastGpsAccuracyMeters: StateFlow<Float?> = _lastGpsAccuracyMeters.asStateFlow()

    private val _deviceViewLayoutMode = MutableStateFlow(DeviceViewLayoutMode.AUTO)
    val deviceViewLayoutMode: StateFlow<DeviceViewLayoutMode> = _deviceViewLayoutMode.asStateFlow()

    val appSettings: StateFlow<AppSettingsState> =
        (settingsStore?.settingsFlow ?: flowOf(AppSettingsState()))
            .catch { emit(AppSettingsState()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), AppSettingsState())

    private val _pendingDeepLinkInviteCode = MutableStateFlow<String?>(null)
    val pendingDeepLinkInviteCode: StateFlow<String?> = _pendingDeepLinkInviteCode.asStateFlow()

    private val _deepLinkInviteResult = MutableStateFlow<InviteInspectionResult?>(null)
    val deepLinkInviteResult: StateFlow<InviteInspectionResult?> = _deepLinkInviteResult.asStateFlow()

    private val _lastBackupInspection = MutableStateFlow<BackupSummary?>(null)
    val lastBackupInspection: StateFlow<BackupSummary?> = _lastBackupInspection.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureUserProfile(userDisplayName, userEmail, isSuperAdmin)
            if (isSuperAdmin) {
                repository.seedDefaultPlansIfNeeded()
            }
            val modelName = listOf(Build.MANUFACTURER, Build.MODEL)
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString(" ")
                .ifBlank { "Android Device" }
            val sessionId = "dev_${Build.MODEL.replace(Regex("[^a-zA-Z0-9]"), "_").take(24)}_${currentUserId.take(8)}"
            repository.registerDeviceSession(
                sessionId = sessionId,
                deviceName = modelName,
                platform = "Android ${Build.VERSION.RELEASE}",
                appVersion = BuildConfig.VERSION_NAME
            )
        }
        settingsStore?.let { store ->
            viewModelScope.launch {
                store.settingsFlow.collect { state ->
                    _deviceViewLayoutMode.value = state.deviceLayoutMode
                }
            }
        }
    }

    private fun <T> Flow<T>.toUiState(label: String): StateFlow<UiState<T>> =
        this.map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing $label", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to load $label"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    private fun <T> Flow<List<T>>.safeListState(label: String): StateFlow<List<T>> =
        this.catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            Log.w(TAG, "Stream error on $label", firestoreError ?: error)
            emit(emptyList())
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val userProfile: StateFlow<UiState<UserProfile?>> =
        repository.observeUserProfile().toUiState("profile")

    val memberships: StateFlow<List<MembershipIndex>> =
        repository.observeMyMemberships().safeListState("memberships")

    val subscriptionPlans: StateFlow<List<SubscriptionPlan>> =
        repository.observeSubscriptionPlans()
            .map { plans ->
                plans.ifEmpty { defaultFallbackPlanCatalog() }
            }
            .safeListState("plans")

    val pendingEmailInvites: StateFlow<List<SchoolInvite>> =
        repository.observePendingInvitesForEmail(userEmail).safeListState("emailInvites")

    private val activeContext: Flow<Pair<String, UserRole>> = userProfile.map { state ->
        val profile = (state as? UiState.Success)?.data
        val schoolId = profile?.activeSchoolId.orEmpty()
        val role = UserRole.fromKey(profile?.activeRole ?: if (isSuperAdmin) UserRole.SUPER_ADMIN.key else UserRole.PARENT.key)
        schoolId to role
    }

    val activeSchool: StateFlow<School?> = activeContext.flatMapLatest { (schoolId, _) ->
        if (schoolId.isBlank()) flowOf(null) else repository.observeSchool(schoolId)
    }.catch { emit(null) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val allSchoolsForSuperAdmin: StateFlow<List<School>> =
        if (isSuperAdmin) repository.observeAllSchoolsForSuperAdmin().safeListState("allSchools")
        else MutableStateFlow(emptyList())

    val classes: StateFlow<List<SchoolClass>> = activeContext.flatMapLatest { (schoolId, role) ->
        repository.observeClasses(schoolId, role)
    }.safeListState("classes")

    val students: StateFlow<List<Student>> = activeContext.flatMapLatest { (schoolId, role) ->
        repository.observeStudents(schoolId, role)
    }.safeListState("students")

    val journeys: StateFlow<List<Journey>> = activeContext.flatMapLatest { (schoolId, role) ->
        repository.observeJourneys(schoolId, role)
    }.safeListState("journeys")

    val attendanceRecords: StateFlow<List<AttendanceRecord>> = activeContext.flatMapLatest { (schoolId, role) ->
        repository.observeAttendance(schoolId, role)
    }.safeListState("attendance")

    val pendingOfflineAttendance: StateFlow<List<OfflineAttendanceEntity>> = activeContext.flatMapLatest { (schoolId, _) ->
        repository.observePendingOfflineAttendance(schoolId)
    }.safeListState("offlineQueue")

    val incidents: StateFlow<List<Incident>> = activeContext.flatMapLatest { (schoolId, role) ->
        repository.observeIncidents(schoolId, role)
    }.safeListState("incidents")

    val messages: StateFlow<List<Message>> = activeContext.flatMapLatest { (schoolId, role) ->
        repository.observeMessages(schoolId, role)
    }.safeListState("messages")

    val notifications: StateFlow<List<AppNotification>> =
        repository.observeMyNotifications().safeListState("notifications")

    val schoolMembers: StateFlow<List<SchoolMember>> = activeContext.flatMapLatest { (schoolId, role) ->
        if (role == UserRole.SCHOOL_ADMIN || role == UserRole.SUPER_ADMIN || role == UserRole.TEACHER) {
            repository.observeSchoolMembers(schoolId, role)
        } else {
            flowOf(emptyList())
        }
    }.safeListState("schoolMembers")

    val schoolInvites: StateFlow<List<SchoolInvite>> = activeContext.flatMapLatest { (schoolId, role) ->
        if (role == UserRole.SCHOOL_ADMIN || role == UserRole.SUPER_ADMIN) {
            repository.observeSchoolInvites(schoolId)
        } else {
            flowOf(emptyList())
        }
    }.safeListState("schoolInvites")

    val auditLogs: StateFlow<List<AuditLog>> = activeContext.flatMapLatest { (schoolId, role) ->
        if (role == UserRole.SCHOOL_ADMIN || isSuperAdmin) {
            repository.observeAuditLogs(schoolId, isSuperAdmin)
        } else {
            flowOf(emptyList())
        }
    }.safeListState("auditLogs")

    val subjects: StateFlow<List<Subject>> = activeContext.flatMapLatest { (schoolId, role) ->
        repository.observeSubjects(schoolId, role)
    }.safeListState("subjects")

    val examReports: StateFlow<List<ExamReport>> = activeContext.flatMapLatest { (schoolId, role) ->
        repository.observeExamReports(schoolId, role)
    }.safeListState("examReports")

    val inventoryItems: StateFlow<List<InventoryItem>> = activeContext.flatMapLatest { (schoolId, role) ->
        repository.observeInventoryItems(schoolId, role)
    }.safeListState("inventoryItems")

    val feeRecords: StateFlow<List<FeeRecord>> = activeContext.flatMapLatest { (schoolId, role) ->
        repository.observeFeeRecords(schoolId, role)
    }.safeListState("feeRecords")

    val deviceSessions: StateFlow<List<UserDeviceSession>> =
        repository.observeDeviceSessions().safeListState("deviceSessions")

    private val globalDistributionConfig: Flow<AppDistributionConfig> =
        repository.observeAppDistributionConfig("global")
            .catch { emit(AppDistributionConfig(configId = "global")) }

    private val schoolDistributionConfig: Flow<AppDistributionConfig> =
        activeContext.flatMapLatest { (schoolId, _) ->
            if (schoolId.isBlank()) flowOf(AppDistributionConfig(configId = "global"))
            else repository.observeAppDistributionConfig(schoolId)
                .catch { emit(AppDistributionConfig(configId = schoolId)) }
        }

    val appDistributionConfig: StateFlow<AppDistributionConfig> =
        combine(globalDistributionConfig, schoolDistributionConfig) { globalCfg, schoolCfg ->
            when {
                schoolCfg.id.isNotBlank() -> schoolCfg
                globalCfg.id.isNotBlank() -> globalCfg
                else -> AppDistributionConfig(
                    configId = schoolCfg.configId.ifBlank { "global" }
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = AppDistributionConfig()
        )

    fun clearBanner() {
        _bannerMessage.value = null
    }

    fun setDeviceViewLayoutMode(mode: DeviceViewLayoutMode) {
        _deviceViewLayoutMode.value = mode
        viewModelScope.launch { settingsStore?.setDeviceLayoutMode(mode) }
        _bannerMessage.value = "Switched layout view to ${mode.label}."
    }

    fun updateThemeMode(mode: AppThemeMode) {
        viewModelScope.launch { settingsStore?.setThemeMode(mode) }
    }

    fun updateAccentPalette(palette: AccentPalette) {
        viewModelScope.launch { settingsStore?.setAccentPalette(palette) }
    }

    fun updateFontScale(scale: Float) {
        viewModelScope.launch { settingsStore?.setFontScale(scale) }
    }

    fun updateCompactCards(compact: Boolean) {
        viewModelScope.launch { settingsStore?.setCompactCards(compact) }
    }

    fun updateDashboardWidgets(showActivities: Boolean, showGeofence: Boolean) {
        viewModelScope.launch {
            settingsStore?.setShowDashboardActivities(showActivities)
            settingsStore?.setShowDashboardGeofence(showGeofence)
        }
    }

    fun updateNotificationSetting(category: String, enabled: Boolean) {
        viewModelScope.launch { settingsStore?.setNotificationToggle(category, enabled) }
    }

    fun updateAssessmentWeight(classPercent: Int) {
        viewModelScope.launch { settingsStore?.setClassAssessmentWeightPercent(classPercent) }
    }

    fun restoreDefaultSettings() {
        viewModelScope.launch {
            settingsStore?.restoreDefaults()
            _deviceViewLayoutMode.value = DeviceViewLayoutMode.AUTO
            _bannerMessage.value = "Restored default appearance, layout, and notification settings."
        }
    }

    fun setDeepLinkInviteCode(code: String?) {
        val raw = code?.trim().orEmpty()
        if (raw.isBlank()) return
        val sanitized = AppLinkValidator.sanitizeInviteCode(raw)
        if (sanitized == null) {
            _pendingDeepLinkInviteCode.value = raw
            _deepLinkInviteResult.value = InviteInspectionResult(
                code = raw,
                status = InviteInspectionStatus.INVALID_FORMAT,
                message = "The invite link is malformed or incomplete. Ask your school administrator for a fresh invite code."
            )
            return
        }
        val normalized = sanitized.uppercase()
        _pendingDeepLinkInviteCode.value = normalized
        inspectPendingInvite(normalized)
    }

    fun inspectPendingInvite(code: String) {
        viewModelScope.launch {
            _deepLinkInviteResult.value = repository.inspectSchoolInvite(code)
        }
    }

    fun consumeDeepLinkInviteCode() {
        _pendingDeepLinkInviteCode.value = null
        _deepLinkInviteResult.value = null
    }

    fun toggleOfflineCaptureMode(enabled: Boolean) {
        _isOfflineCaptureMode.value = enabled
        _bannerMessage.value = if (enabled) {
            "Offline capture mode enabled. Attendance will queue locally in Room and sync when ready."
        } else {
            "Online real-time mode active."
        }
    }

    fun switchContext(schoolId: String, role: UserRole) {
        viewModelScope.launch {
            repository.updateActiveContext(schoolId, role.key)
                .onFailure { _bannerMessage.value = it.localizedMessage }
        }
    }

    fun setLocationConsent(granted: Boolean) {
        val profile = (userProfile.value as? UiState.Success)?.data
        val school = activeSchool.value
        viewModelScope.launch {
            repository.updateLocationConsent(
                granted = granted,
                schoolId = school?.id.orEmpty(),
                actorName = profile?.displayName ?: userDisplayName,
                actorRole = profile?.activeRole ?: UserRole.PARENT.key,
                adminUids = school?.adminUids.orEmpty()
            ).onSuccess {
                _bannerMessage.value = if (granted) {
                    "GPS sharing consent enabled for authorised journeys only."
                } else {
                    "GPS sharing consent revoked. Journeys will use manual status updates."
                }
            }.onFailure {
                _bannerMessage.value = it.localizedMessage
            }
        }
    }

    fun registerNewSchool(
        name: String,
        city: String,
        country: String,
        address: String,
        phone: String,
        email: String,
        plan: SubscriptionPlan,
        billingCycle: String
    ) {
        if (name.trim().length < 2) {
            _bannerMessage.value = "Please enter a valid school name."
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            repository.registerSchool(
                schoolName = name,
                city = city,
                country = country,
                address = address,
                phone = phone,
                email = email,
                plan = plan,
                billingCycle = billingCycle,
                ownerDisplayName = userDisplayName,
                ownerEmail = userEmail
            ).onSuccess {
                _bannerMessage.value = if (plan.trialDays > 0) {
                    "$name registered on ${plan.name} (${plan.trialDays}-day trial)."
                } else {
                    "$name registered on ${plan.name}."
                }
            }.onFailure {
                _bannerMessage.value = it.localizedMessage ?: "Could not register school."
            }
            _isBusy.value = false
        }
    }

    fun claimInvite(inviteCode: String, onSuccess: () -> Unit = {}) {
        if (inviteCode.isBlank()) {
            _bannerMessage.value = "Enter a valid invite code."
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            repository.claimSchoolInvite(inviteCode, userDisplayName, userEmail)
                .onSuccess { invite ->
                    consumeDeepLinkInviteCode()
                    _bannerMessage.value = "Joined ${invite.schoolName} as ${UserRole.fromKey(invite.role).label}."
                    onSuccess()
                }
                .onFailure {
                    _deepLinkInviteResult.value = repository.inspectSchoolInvite(inviteCode)
                    _bannerMessage.value = it.localizedMessage ?: "Unable to claim invite."
                }
            _isBusy.value = false
        }
    }

    fun createInvite(targetEmail: String, role: UserRole) {
        val school = activeSchool.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            repository.createSchoolInvite(
                school = school,
                targetEmail = targetEmail,
                role = role,
                assignedClassIds = emptyList(),
                linkedStudentIds = emptyList(),
                actorName = userDisplayName
            ).onSuccess { code ->
                _bannerMessage.value = "Invite created: $code"
            }.onFailure {
                _bannerMessage.value = it.localizedMessage
            }
            _isBusy.value = false
        }
    }

    fun createClass(name: String, gradeLevel: String, academicYear: String, teacherUids: List<String>) {
        val school = activeSchool.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            repository.createClass(school, name, gradeLevel, academicYear, teacherUids.ifEmpty { listOf(currentUserId) }, userDisplayName)
                .onSuccess { _bannerMessage.value = "Class $name added." }
                .onFailure { _bannerMessage.value = it.localizedMessage }
            _isBusy.value = false
        }
    }

    fun enrollStudent(
        admissionNumber: String,
        fullName: String,
        schoolClass: SchoolClass,
        emergencyName: String,
        emergencyPhone: String,
        medicalNotes: String,
        homeDestination: String,
        authorisedPickup: String,
        photoUri: String?,
        linkSelfAsGuardian: Boolean
    ) {
        val school = activeSchool.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            val guardians = if (linkSelfAsGuardian) listOf(currentUserId) else emptyList()
            val destinations = listOfNotNull(homeDestination.takeIf { it.isNotBlank() })
            val pickups = listOfNotNull(authorisedPickup.takeIf { it.isNotBlank() })
            repository.createStudent(
                school = school,
                admissionNumber = admissionNumber,
                fullName = fullName,
                schoolClass = schoolClass,
                emergencyContactName = emergencyName,
                emergencyContactPhone = emergencyPhone,
                medicalNotes = medicalNotes,
                approvedDestinations = destinations,
                authorisedPickups = pickups,
                guardianUids = guardians,
                pickupUids = emptyList(),
                photoUri = photoUri,
                actorName = userDisplayName
            ).onSuccess {
                _bannerMessage.value = "Enrolled $fullName into ${schoolClass.name}."
            }.onFailure {
                _bannerMessage.value = it.localizedMessage
            }
            _isBusy.value = false
        }
    }

    fun linkMemberToStudent(student: Student, memberUid: String, role: UserRole) {
        viewModelScope.launch {
            repository.linkMemberToStudent(student, memberUid, role, userDisplayName)
                .onSuccess { _bannerMessage.value = "Linked ${role.label} to ${student.fullName}." }
                .onFailure { _bannerMessage.value = it.localizedMessage }
        }
    }

    fun updateStudentLifecycle(student: Student, newStatus: String) {
        viewModelScope.launch {
            repository.updateStudentLifecycleStatus(student, newStatus, null, null, userDisplayName)
                .onSuccess { _bannerMessage.value = "${student.fullName} marked $newStatus." }
                .onFailure { _bannerMessage.value = it.localizedMessage }
        }
    }

    fun addDestination(student: Student, destination: String) {
        if (destination.isBlank()) return
        viewModelScope.launch {
            repository.addApprovedDestination(student, destination)
                .onSuccess { _bannerMessage.value = "Approved destination '$destination' saved." }
                .onFailure { _bannerMessage.value = it.localizedMessage }
        }
    }

    fun startMorningJourney(context: Context, student: Student, destinationName: String, manualNote: String) {
        val profile = (userProfile.value as? UiState.Success)?.data
        val hasOsPerm = LocationTracker.hasLocationPermission(context)
        val consent = (profile?.locationConsentGranted == true) && hasOsPerm
        viewModelScope.launch {
            _isBusy.value = true
            var lat: Double? = null
            var lng: Double? = null
            if (consent) {
                when (val gps = LocationTracker.fetchCurrentLocation(context)) {
                    is GpsFetchResult.Available -> {
                        lat = gps.latitude
                        lng = gps.longitude
                        _gpsStatusLabel.value = "Live GPS active (${"%.4f".format(lat)}, ${"%.4f".format(lng)})"
                    }
                    is GpsFetchResult.Unavailable -> {
                        _gpsStatusLabel.value = "GPS unavailable: ${gps.reason} (manual update saved)"
                    }
                }
            } else {
                _gpsStatusLabel.value = if (profile?.locationConsentGranted == true && !hasOsPerm) {
                    "Manual journey mode — device location permission not granted"
                } else {
                    "Manual journey mode (GPS sharing off)"
                }
            }
            repository.startMorningJourney(
                student = student,
                destinationName = destinationName,
                parentName = profile?.displayName ?: userDisplayName,
                consentGranted = consent,
                latitude = lat,
                longitude = lng,
                manualNote = manualNote.ifBlank { null }
            ).onSuccess {
                NotificationHelper.showAlert(
                    context,
                    "Morning Journey Started",
                    "${student.fullName} is on the way to $destinationName. Teacher confirmation required for attendance."
                )
                _bannerMessage.value = "Morning journey started. Teachers notified to confirm physical arrival."
            }.onFailure {
                _bannerMessage.value = it.localizedMessage
            }
            _isBusy.value = false
        }
    }

    fun confirmPhysicalArrival(context: Context, journey: Journey, student: Student, markAsLate: Boolean) {
        val profile = (userProfile.value as? UiState.Success)?.data
        viewModelScope.launch {
            _isBusy.value = true
            repository.confirmPhysicalArrivalAtSchool(
                journey = journey,
                student = student,
                teacherName = profile?.displayName ?: userDisplayName,
                teacherRole = profile?.activeRole ?: UserRole.TEACHER.key,
                markAsLate = markAsLate
            ).onSuccess {
                NotificationHelper.showAlert(
                    context,
                    "Arrival Confirmed",
                    "${student.fullName} physically confirmed at school (${if (markAsLate) "Late" else "Present"})."
                )
                _gpsStatusLabel.value = "Tracking stopped upon school arrival."
                _bannerMessage.value = "Physical arrival confirmed & attendance recorded."
            }.onFailure {
                _bannerMessage.value = it.localizedMessage
            }
            _isBusy.value = false
        }
    }

    fun initiateAfternoonDismissal(
        context: Context,
        student: Student,
        destinationName: String,
        isAlternative: Boolean,
        manualNote: String
    ) {
        val profile = (userProfile.value as? UiState.Success)?.data
        val hasOsPerm = LocationTracker.hasLocationPermission(context)
        val consent = (profile?.locationConsentGranted == true) && hasOsPerm
        viewModelScope.launch {
            _isBusy.value = true
            var lat: Double? = null
            var lng: Double? = null
            if (consent) {
                val gps = LocationTracker.fetchCurrentLocation(context)
                lat = (gps as? GpsFetchResult.Available)?.latitude
                lng = (gps as? GpsFetchResult.Available)?.longitude
            }
            _gpsStatusLabel.value = if (lat != null && lng != null) {
                "Dismissal GPS active (${"%.4f".format(lat)}, ${"%.4f".format(lng)})"
            } else if (!consent) {
                "Manual dismissal mode — enable GPS consent & permission to share coordinates"
            } else {
                "GPS unavailable — manual dismissal update recorded"
            }
            repository.initiateAfternoonDismissal(
                student = student,
                destinationName = destinationName,
                isAlternativeDestination = isAlternative,
                teacherName = profile?.displayName ?: userDisplayName,
                teacherRole = profile?.activeRole ?: UserRole.TEACHER.key,
                consentGranted = consent,
                latitude = lat,
                longitude = lng,
                manualNote = manualNote.ifBlank { null }
            ).onSuccess {
                NotificationHelper.showAlert(
                    context,
                    "Afternoon Dismissal Initiated",
                    "${student.fullName} dismissed to $destinationName."
                )
                _bannerMessage.value = "Dismissal authorised. Parent notified."
            }.onFailure {
                _bannerMessage.value = it.localizedMessage
            }
            _isBusy.value = false
        }
    }

    fun dispatchClassDeparture(
        context: Context,
        entries: List<ClassDepartureEntry>,
        notifyLateExceptions: Boolean
    ) {
        if (entries.isEmpty()) return
        val profile = (userProfile.value as? UiState.Success)?.data
        val hasOsPerm = LocationTracker.hasLocationPermission(context)
        val consent = (profile?.locationConsentGranted == true) && hasOsPerm
        viewModelScope.launch {
            _isBusy.value = true
            var lat: Double? = null
            var lng: Double? = null
            if (consent) {
                val gps = LocationTracker.fetchCurrentLocation(context)
                lat = (gps as? GpsFetchResult.Available)?.latitude
                lng = (gps as? GpsFetchResult.Available)?.longitude
            }
            repository.dispatchClassDepartureAlerts(
                entries = entries,
                notifyLateExceptions = notifyLateExceptions,
                teacherName = profile?.displayName ?: userDisplayName,
                teacherRole = profile?.activeRole ?: UserRole.TEACHER.key,
                consentGranted = consent,
                latitude = lat,
                longitude = lng
            ).onSuccess { result ->
                val summary = buildString {
                    append("${result.departedCount} departed")
                    if (result.lateExceptionCount > 0) {
                        append(", ${result.lateExceptionCount} leaving late")
                    }
                }
                NotificationHelper.showAlert(
                    context = context,
                    title = "Class Departure Alert Sent",
                    body = "Parent notifications dispatched ($summary)."
                )
                _bannerMessage.value = "Class departure sent: $summary."
            }.onFailure {
                _bannerMessage.value = it.localizedMessage ?: "Failed to dispatch class departure alert."
            }
            _isBusy.value = false
        }
    }

    fun confirmSafeArrivalHome(context: Context, journey: Journey) {
        val profile = (userProfile.value as? UiState.Success)?.data
        viewModelScope.launch {
            _isBusy.value = true
            repository.confirmSafeArrivalHome(journey, profile?.displayName ?: userDisplayName)
                .onSuccess {
                    _gpsStatusLabel.value = "Journey completed — location tracking stopped immediately."
                    NotificationHelper.showAlert(
                        context,
                        "Safe Arrival Confirmed",
                        "${journey.studentName} reached ${journey.destinationName}. Tracking stopped."
                    )
                    _bannerMessage.value = "Safe arrival confirmed. Tracking stopped."
                }
                .onFailure { _bannerMessage.value = it.localizedMessage }
            _isBusy.value = false
        }
    }

    fun updateJourneyStatus(context: Context, journey: Journey, status: MovementStatus, note: String) {
        val profile = (userProfile.value as? UiState.Success)?.data
        val consent = (profile?.locationConsentGranted == true) && LocationTracker.hasLocationPermission(context)
        viewModelScope.launch {
            var lat: Double? = null
            var lng: Double? = null
            if (consent && status != MovementStatus.ARRIVED_HOME && status != MovementStatus.ARRIVED_AT_SCHOOL) {
                val gps = LocationTracker.fetchCurrentLocation(context)
                if (gps is GpsFetchResult.Available) {
                    lat = gps.latitude
                    lng = gps.longitude
                }
            }
            repository.updateJourneyLiveOrManual(
                journey = journey,
                newStatus = status.key,
                gpsAvailable = lat != null && lng != null,
                latitude = lat,
                longitude = lng,
                manualNote = note.ifBlank { null },
                actorName = profile?.displayName ?: userDisplayName,
                actorRole = profile?.activeRole ?: UserRole.PARENT.key
            ).onSuccess {
                _bannerMessage.value = "Journey updated: ${status.label}"
            }.onFailure {
                _bannerMessage.value = it.localizedMessage
            }
        }
    }

    fun recordAttendance(student: Student, dateKey: String, status: AttendanceStatus, reason: String) {
        val profile = (userProfile.value as? UiState.Success)?.data
        val statusLabel = if (status == AttendanceStatus.LATE) "Tardy" else status.label
        viewModelScope.launch {
            repository.recordOrCorrectAttendance(
                student = student,
                dateKey = dateKey,
                status = status,
                correctionReason = reason,
                teacherName = profile?.displayName ?: userDisplayName,
                teacherRole = profile?.activeRole ?: UserRole.TEACHER.key,
                isOfflineMode = _isOfflineCaptureMode.value
            ).onSuccess { syncedImmediately ->
                _bannerMessage.value = if (syncedImmediately) {
                    "${student.fullName} marked $statusLabel — synced to Firestore for parents."
                } else {
                    "Saved offline for ${student.fullName}. Tap Sync when online."
                }
            }.onFailure {
                _bannerMessage.value = it.localizedMessage
            }
        }
    }

    fun markAllPresentForDate(targetStudents: List<Student>, dateKey: String) {
        if (targetStudents.isEmpty()) return
        val profile = (userProfile.value as? UiState.Success)?.data
        viewModelScope.launch {
            _isBusy.value = true
            if (_isOfflineCaptureMode.value) {
                var queued = 0
                targetStudents.forEach { stu ->
                    repository.recordOrCorrectAttendance(
                        student = stu,
                        dateKey = dateKey,
                        status = AttendanceStatus.PRESENT,
                        correctionReason = "",
                        teacherName = profile?.displayName ?: userDisplayName,
                        teacherRole = profile?.activeRole ?: UserRole.TEACHER.key,
                        isOfflineMode = true
                    ).onSuccess { queued++ }
                }
                _bannerMessage.value = "Queued $queued pupil(s) as Present offline."
            } else {
                repository.markStudentsPresentBatch(
                    students = targetStudents,
                    dateKey = dateKey,
                    teacherName = profile?.displayName ?: userDisplayName,
                    teacherRole = profile?.activeRole ?: UserRole.TEACHER.key
                ).onSuccess { count ->
                    _bannerMessage.value = if (count > 0) {
                        "Marked $count unmarked pupil(s) Present — synced live to Firestore."
                    } else {
                        "All pupils in view are already marked for $dateKey."
                    }
                }.onFailure {
                    _bannerMessage.value = it.localizedMessage
                }
            }
            _isBusy.value = false
        }
    }

    fun syncOfflineQueue() {
        val schoolId = activeSchool.value?.id ?: return
        val profile = (userProfile.value as? UiState.Success)?.data
        viewModelScope.launch {
            _isBusy.value = true
            repository.syncPendingOfflineAttendance(
                schoolId = schoolId,
                actorName = profile?.displayName ?: userDisplayName,
                actorRole = profile?.activeRole ?: UserRole.TEACHER.key
            ).onSuccess { count ->
                _isOfflineCaptureMode.value = false
                _bannerMessage.value = if (count > 0) "Synced $count offline attendance record(s) to cloud." else "No pending offline records."
            }.onFailure {
                _bannerMessage.value = it.localizedMessage
            }
            _isBusy.value = false
        }
    }

    fun reportIncident(context: Context, student: Student, journeyId: String?, severity: String, title: String, description: String) {
        val profile = (userProfile.value as? UiState.Success)?.data
        viewModelScope.launch {
            _isBusy.value = true
            repository.reportIncident(
                student = student,
                journeyId = journeyId,
                severity = severity,
                title = title,
                description = description,
                reporterName = profile?.displayName ?: userDisplayName,
                reporterRole = profile?.activeRole ?: UserRole.TEACHER.key
            ).onSuccess {
                NotificationHelper.showAlert(context, "Emergency / Incident Logged", "$title (${student.fullName})")
                _bannerMessage.value = "Incident reported and school/guardian alerts dispatched."
            }.onFailure {
                _bannerMessage.value = it.localizedMessage
            }
            _isBusy.value = false
        }
    }

    fun resolveIncident(incident: Incident, status: String) {
        viewModelScope.launch {
            repository.updateIncidentStatus(incident, status)
                .onSuccess { _bannerMessage.value = "Incident marked $status." }
                .onFailure { _bannerMessage.value = it.localizedMessage }
        }
    }

    fun sendMessage(student: Student, body: String) {
        if (body.isBlank()) return
        val profile = (userProfile.value as? UiState.Success)?.data
        viewModelScope.launch {
            repository.sendMessage(
                student = student,
                senderName = profile?.displayName ?: userDisplayName,
                senderRole = profile?.activeRole ?: UserRole.PARENT.key,
                body = body
            ).onFailure { _bannerMessage.value = it.localizedMessage }
        }
    }

    fun markNotificationRead(notificationId: String) {
        viewModelScope.launch {
            repository.markNotificationRead(notificationId)
        }
    }

    fun updateSchoolSubscription(
        school: School,
        plan: SubscriptionPlan?,
        billingCycle: String,
        newStatus: String?,
        paymentProvider: String,
        paymentRef: String?
    ) {
        val profile = (userProfile.value as? UiState.Success)?.data
        viewModelScope.launch {
            _isBusy.value = true
            repository.updateSchoolSubscription(
                schoolId = school.id,
                plan = plan,
                billingCycle = billingCycle,
                subscriptionStatus = newStatus,
                paymentProvider = paymentProvider,
                paymentReference = paymentRef,
                actorName = profile?.displayName ?: userDisplayName,
                actorRole = if (isSuperAdmin) UserRole.SUPER_ADMIN.key else UserRole.SCHOOL_ADMIN.key,
                adminUids = school.adminUids
            ).onSuccess {
                _bannerMessage.value = "Subscription configuration updated for ${school.name}."
            }.onFailure {
                _bannerMessage.value = it.localizedMessage
            }
            _isBusy.value = false
        }
    }

    fun saveSubscriptionPlan(plan: SubscriptionPlan) {
        if (!isSuperAdmin) return
        viewModelScope.launch {
            _isBusy.value = true
            repository.upsertSubscriptionPlan(plan)
                .onSuccess { _bannerMessage.value = "Saved ${plan.name} plan (GH₵${plan.monthlyPriceGhs.toInt()}/mo)." }
                .onFailure { _bannerMessage.value = it.localizedMessage }
            _isBusy.value = false
        }
    }

    fun updateSchoolProfile(
        name: String,
        city: String,
        phone: String,
        email: String,
        logoUri: String?,
        address: String? = null,
        motto: String? = null,
        academicYear: String? = null,
        currentTerm: String? = null,
        operatingHours: String? = null
    ) {
        val school = activeSchool.value ?: return
        val profile = (userProfile.value as? UiState.Success)?.data
        viewModelScope.launch {
            _isBusy.value = true
            repository.updateSchoolProfileAndLogo(
                school = school,
                name = name,
                city = city,
                phone = phone,
                email = email,
                logoUri = logoUri,
                actorName = profile?.displayName ?: userDisplayName,
                actorRole = profile?.activeRole ?: UserRole.SCHOOL_ADMIN.key,
                address = address ?: school.address.orEmpty(),
                motto = motto ?: school.motto,
                academicYear = academicYear ?: school.academicYear,
                currentTerm = currentTerm ?: school.currentTerm,
                operatingHours = operatingHours ?: school.operatingHours
            ).onSuccess {
                _bannerMessage.value = "Updated school profile & academic settings."
            }.onFailure {
                _bannerMessage.value = it.localizedMessage
            }
            _isBusy.value = false
        }
    }

    fun updateStudentProfile(
        student: Student,
        admissionNumber: String,
        fullName: String,
        schoolClass: SchoolClass,
        emergencyName: String,
        emergencyPhone: String,
        medicalNotes: String,
        approvedDestinations: List<String>,
        authorisedPickups: List<String>
    ) {
        val profile = (userProfile.value as? UiState.Success)?.data
        viewModelScope.launch {
            _isBusy.value = true
            repository.updateStudentProfile(
                student = student,
                admissionNumber = admissionNumber,
                fullName = fullName,
                schoolClass = schoolClass,
                emergencyContactName = emergencyName,
                emergencyContactPhone = emergencyPhone,
                medicalNotes = medicalNotes,
                approvedDestinations = approvedDestinations,
                authorisedPickups = authorisedPickups,
                actorName = profile?.displayName ?: userDisplayName,
                actorRole = profile?.activeRole ?: UserRole.TEACHER.key
            ).onSuccess {
                _bannerMessage.value = "Updated details for $fullName."
            }.onFailure {
                _bannerMessage.value = it.localizedMessage
            }
            _isBusy.value = false
        }
    }

    fun deleteStudent(student: Student) {
        val profile = (userProfile.value as? UiState.Success)?.data
        viewModelScope.launch {
            _isBusy.value = true
            repository.deleteStudent(
                student = student,
                actorName = profile?.displayName ?: userDisplayName,
                actorRole = profile?.activeRole ?: UserRole.TEACHER.key
            ).onSuccess {
                _bannerMessage.value = "Deleted pupil ${student.fullName}."
            }.onFailure {
                _bannerMessage.value = it.localizedMessage ?: "Failed to delete pupil."
            }
            _isBusy.value = false
        }
    }

    fun saveTeacherMember(
        existingMember: SchoolMember?,
        displayName: String,
        email: String,
        phone: String,
        role: UserRole,
        assignedClassIds: List<String>,
        assignedSubjectIds: List<String> = emptyList(),
        assignedSubjectNames: List<String> = emptyList(),
        status: String
    ) {
        val school = activeSchool.value ?: return
        val profile = (userProfile.value as? UiState.Success)?.data
        viewModelScope.launch {
            _isBusy.value = true
            repository.upsertTeacherMember(
                school = school,
                existingMember = existingMember,
                displayName = displayName,
                email = email,
                phone = phone,
                role = role,
                assignedClassIds = assignedClassIds,
                assignedSubjectIds = assignedSubjectIds,
                assignedSubjectNames = assignedSubjectNames,
                status = status,
                actorName = profile?.displayName ?: userDisplayName,
                actorRole = profile?.activeRole ?: UserRole.TEACHER.key
            ).onSuccess {
                _bannerMessage.value = "${if (existingMember != null) "Updated" else "Added"} $displayName."
            }.onFailure {
                _bannerMessage.value = it.localizedMessage ?: "Failed to save staff details."
            }
            _isBusy.value = false
        }
    }

    fun deleteTeacherMember(member: SchoolMember) {
        val profile = (userProfile.value as? UiState.Success)?.data
        viewModelScope.launch {
            _isBusy.value = true
            repository.deleteSchoolMember(
                member = member,
                actorName = profile?.displayName ?: userDisplayName,
                actorRole = profile?.activeRole ?: UserRole.TEACHER.key
            ).onSuccess {
                _bannerMessage.value = "Removed ${member.displayName} from school directory."
            }.onFailure {
                _bannerMessage.value = it.localizedMessage ?: "Failed to remove member."
            }
            _isBusy.value = false
        }
    }

    fun bulkImportStudents(
        defaultClass: SchoolClass,
        rows: List<ParsedStudentRow>,
        linkSelfAsGuardian: Boolean
    ) {
        val school = activeSchool.value ?: return
        val profile = (userProfile.value as? UiState.Success)?.data
        if (rows.isEmpty()) {
            _bannerMessage.value = "No valid student rows found in CSV/text."
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            repository.bulkImportStudents(
                school = school,
                classes = classes.value,
                defaultClass = defaultClass,
                rows = rows,
                linkSelfAsGuardian = linkSelfAsGuardian,
                actorName = profile?.displayName ?: userDisplayName,
                actorRole = profile?.activeRole ?: UserRole.SCHOOL_ADMIN.key
            ).onSuccess { count ->
                _bannerMessage.value = "Imported $count pupil(s) into ${school.name}."
            }.onFailure {
                _bannerMessage.value = it.localizedMessage
            }
            _isBusy.value = false
        }
    }

    fun saveSubject(
        existingSubject: Subject?,
        name: String,
        code: String,
        level: String,
        isCore: Boolean,
        coreSlot: String
    ) {
        val school = activeSchool.value ?: return
        val profile = (userProfile.value as? UiState.Success)?.data
        val allTeachers = classes.value.flatMap { it.teacherUids }.distinct()
        viewModelScope.launch {
            _isBusy.value = true
            repository.upsertSubject(
                school = school,
                existingSubject = existingSubject,
                name = name,
                code = code,
                level = level,
                isCore = isCore,
                coreSlot = coreSlot,
                teacherUids = allTeachers,
                actorName = profile?.displayName ?: userDisplayName,
                actorRole = profile?.activeRole ?: UserRole.TEACHER.key
            ).onSuccess {
                _bannerMessage.value = "${if (existingSubject != null) "Updated" else "Added"} subject $name."
            }.onFailure {
                _bannerMessage.value = it.localizedMessage
            }
            _isBusy.value = false
        }
    }

    fun deleteSubject(subject: Subject) {
        val profile = (userProfile.value as? UiState.Success)?.data
        viewModelScope.launch {
            _isBusy.value = true
            repository.deleteSubject(
                subject = subject,
                actorName = profile?.displayName ?: userDisplayName,
                actorRole = profile?.activeRole ?: UserRole.TEACHER.key
            ).onSuccess {
                _bannerMessage.value = "Deleted subject ${subject.name}."
            }.onFailure {
                _bannerMessage.value = it.localizedMessage
            }
            _isBusy.value = false
        }
    }

    fun seedDefaultSubjects() {
        val school = activeSchool.value ?: return
        val profile = (userProfile.value as? UiState.Success)?.data
        val allTeachers = classes.value.flatMap { it.teacherUids }.distinct()
        viewModelScope.launch {
            _isBusy.value = true
            repository.seedDefaultGhanaSubjects(
                school = school,
                teacherUids = allTeachers,
                actorName = profile?.displayName ?: userDisplayName,
                actorRole = profile?.activeRole ?: UserRole.TEACHER.key
            ).onSuccess { count ->
                _bannerMessage.value = "Loaded $count standard Ghana Core & Elective subjects."
            }.onFailure {
                _bannerMessage.value = it.localizedMessage
            }
            _isBusy.value = false
        }
    }

    fun saveExamReport(
        report: ExamReport,
        autoRankPosition: Boolean
    ) {
        val profile = (userProfile.value as? UiState.Success)?.data
        val finalPosition = if (autoRankPosition || report.positionText.isBlank()) {
            val peers = examReports.value
                .filter { it.classId == report.classId && it.term == report.term && it.studentId != report.studentId }
                .map { it.studentId to it.overallTotal } + (report.studentId to report.overallTotal)
            val sorted = peers.sortedByDescending { it.second }
            val rankIndex = sorted.indexOfFirst { it.first == report.studentId }
            val rank = if (rankIndex >= 0) rankIndex + 1 else 1
            "${ExamGrading.formatOrdinal(rank)} of ${sorted.size}"
        } else {
            report.positionText.trim()
        }
        val finalReport = report.copy(positionText = finalPosition)
        viewModelScope.launch {
            _isBusy.value = true
            repository.upsertExamReport(
                report = finalReport,
                actorName = profile?.displayName ?: userDisplayName,
                actorRole = profile?.activeRole ?: UserRole.TEACHER.key
            ).onSuccess {
                _bannerMessage.value = "Saved ${finalReport.term} report card for ${finalReport.studentName} (${finalReport.positionText})."
            }.onFailure {
                _bannerMessage.value = it.localizedMessage
            }
            _isBusy.value = false
        }
    }

    fun exportExamReportPdf(context: Context, report: ExamReport) {
        ReportGenerator.generateAndShareExamReportPdf(
            context = context,
            school = activeSchool.value,
            report = report
        ).onSuccess {
            _bannerMessage.value = "Generated Report Card PDF: ${it.name}"
        }.onFailure {
            _bannerMessage.value = "Report Card PDF failed: ${it.localizedMessage}"
        }
    }

    fun exportBlankMasterReportPdf(context: Context) {
        ReportGenerator.generateAndShareBlankMasterReportPdf(
            context = context,
            school = activeSchool.value
        ).onSuccess {
            _bannerMessage.value = "Generated Blank Master A4 Report PDF: ${it.name}"
        }.onFailure {
            _bannerMessage.value = "Blank Master PDF failed: ${it.localizedMessage}"
        }
    }

    fun applyCefelixAcademyMasterSetup() {
        val school = activeSchool.value ?: return
        val profile = (userProfile.value as? UiState.Success)?.data
        val roleKey = profile?.activeRole ?: UserRole.TEACHER.key
        val allTeachers = classes.value.flatMap { it.teacherUids }.distinct()
        viewModelScope.launch {
            _isBusy.value = true
            val canUpdateBranding = roleKey == UserRole.SCHOOL_ADMIN.key || roleKey == UserRole.SUPER_ADMIN.key
            if (canUpdateBranding) {
                repository.updateSchoolProfileAndLogo(
                    school = school,
                    name = CefelixAcademyTemplate.SCHOOL_NAME,
                    city = CefelixAcademyTemplate.SCHOOL_LOCATION,
                    phone = CefelixAcademyTemplate.CONTACT_PHONE,
                    email = CefelixAcademyTemplate.CONTACT_EMAIL,
                    logoUri = school.logoUri,
                    actorName = profile?.displayName ?: userDisplayName,
                    actorRole = roleKey
                )
            }
            repository.seedDefaultGhanaSubjects(
                school = school,
                teacherUids = allTeachers,
                actorName = profile?.displayName ?: userDisplayName,
                actorRole = roleKey
            ).onSuccess { count ->
                _bannerMessage.value = if (canUpdateBranding) {
                    "Applied CEFELIX ACADEMY branding & synced $count subjects (including 7 Primary subjects)."
                } else {
                    "Synced $count subjects. Cefelix Academy report header applied for term printing."
                }
            }.onFailure {
                _bannerMessage.value = it.localizedMessage ?: "Failed to apply Cefelix Academy setup."
            }
            _isBusy.value = false
        }
    }

    fun exportPdfReport(context: Context, reportType: ReportType) {
        ReportGenerator.generateAndSharePdf(
            context = context,
            school = activeSchool.value,
            reportType = reportType,
            attendance = attendanceRecords.value,
            journeys = journeys.value,
            incidents = incidents.value
        ).onSuccess {
            _bannerMessage.value = "Generated PDF: ${it.name}"
        }.onFailure {
            _bannerMessage.value = "PDF export failed: ${it.localizedMessage}"
        }
    }

    fun exportCsvReport(context: Context, reportType: ReportType) {
        ReportGenerator.generateAndShareCsv(
            context = context,
            school = activeSchool.value,
            reportType = reportType,
            attendance = attendanceRecords.value,
            journeys = journeys.value,
            incidents = incidents.value
        ).onSuccess {
            _bannerMessage.value = "Generated Excel CSV: ${it.name}"
        }.onFailure {
            _bannerMessage.value = "CSV export failed: ${it.localizedMessage}"
        }
    }

    fun refreshGeofenceStatus(context: Context) {
        GeofenceManager.refreshStatus(context)
    }

    fun updateSchoolGeofence(
        latitude: Double,
        longitude: Double,
        radiusMeters: Double,
        enabled: Boolean
    ) {
        val school = activeSchool.value ?: return
        val profile = (userProfile.value as? UiState.Success)?.data
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) {
            _bannerMessage.value = "Enter valid coordinates (Lat -90..90, Lng -180..180)."
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            repository.updateSchoolGeofence(
                school = school,
                latitude = latitude,
                longitude = longitude,
                radiusMeters = radiusMeters,
                enabled = enabled,
                actorName = profile?.displayName ?: userDisplayName,
                actorRole = profile?.activeRole ?: UserRole.SCHOOL_ADMIN.key
            ).onSuccess {
                _bannerMessage.value = if (enabled) {
                    "School premises geofence saved (${radiusMeters.toInt()}m radius)."
                } else {
                    "School premises geofence disabled."
                }
            }.onFailure {
                _bannerMessage.value = it.localizedMessage ?: "Failed to update school geofence."
            }
            _isBusy.value = false
        }
    }

    fun captureSchoolLocationForGeofence(
        context: Context,
        onCaptured: (Double, Double) -> Unit
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            when (val res = LocationTracker.fetchCurrentLocation(context)) {
                is GpsFetchResult.Available -> {
                    _lastGpsAccuracyMeters.value = res.accuracyMeters
                    onCaptured(res.latitude, res.longitude)
                    val accText = res.accuracyMeters?.let { " (±${it.toInt()}m)" }.orEmpty()
                    _bannerMessage.value = "Captured campus GPS (${"%.5f".format(res.latitude)}, ${"%.5f".format(res.longitude)})$accText."
                }
                is GpsFetchResult.Unavailable -> {
                    _bannerMessage.value = "Could not capture GPS: ${res.reason}"
                }
            }
            _isBusy.value = false
        }
    }

    fun enableSchoolGeofenceMonitoring(context: Context, targetStudents: List<Student>) {
        val school = activeSchool.value
        if (school == null) {
            _bannerMessage.value = "Join or select a school before enabling geofence monitoring."
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            GeofenceManager.registerSchoolGeofence(context, school, targetStudents)
                .onSuccess { msg ->
                    _gpsStatusLabel.value = msg
                    _bannerMessage.value = msg
                }
                .onFailure { err ->
                    _bannerMessage.value = err.localizedMessage ?: "Could not register school geofence."
                }
            _isBusy.value = false
        }
    }

    fun disableSchoolGeofenceMonitoring(context: Context) {
        viewModelScope.launch {
            _isBusy.value = true
            GeofenceManager.unregisterSchoolGeofence(context)
                .onSuccess {
                    _gpsStatusLabel.value = "School geofence monitoring stopped."
                    _bannerMessage.value = "School premises geofence monitoring turned off."
                }
                .onFailure {
                    _bannerMessage.value = it.localizedMessage ?: "Stopped local geofence monitoring."
                }
            _isBusy.value = false
        }
    }

    fun checkSchoolPerimeterNow(context: Context, school: School) {
        val lat = school.geofenceLatitude
        val lng = school.geofenceLongitude
        if (lat == null || lng == null) {
            _perimeterCheckStatus.value = "School Admin has not configured campus coordinates yet."
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            when (val res = LocationTracker.fetchCurrentLocation(context)) {
                is GpsFetchResult.Available -> {
                    _lastGpsAccuracyMeters.value = res.accuracyMeters
                    val distance = GeofenceManager.distanceMeters(res.latitude, res.longitude, lat, lng)
                    val radius = school.geofenceRadiusMeters.toFloat().coerceIn(100f, 2000f)
                    val inside = distance <= radius
                    val accSuffix = res.accuracyMeters?.let { " • GPS accuracy ±${it.toInt()}m" }.orEmpty()
                    _perimeterCheckStatus.value = if (inside) {
                        "Inside school premises (${distance.toInt()}m from center, limit ${radius.toInt()}m)$accSuffix"
                    } else {
                        "Outside school premises (${distance.toInt()}m away from center, limit ${radius.toInt()}m)$accSuffix"
                    }
                }
                is GpsFetchResult.Unavailable -> {
                    _perimeterCheckStatus.value = "GPS unavailable: ${res.reason}"
                }
            }
            _isBusy.value = false
        }
    }

    fun deleteMyAccount(context: Context, onAccountDeleted: () -> Unit) {
        viewModelScope.launch {
            _isBusy.value = true
            runCatching { GeofenceManager.unregisterSchoolGeofence(context) }
            repository.deleteMyAccountData()
                .onSuccess {
                    _isBusy.value = false
                    onAccountDeleted()
                }
                .onFailure { err ->
                    _bannerMessage.value = err.localizedMessage ?: "Failed to delete account data."
                    _isBusy.value = false
                }
        }
    }

    fun saveAppDistributionConfig(
        downloadUrl: String,
        viewAccessMode: AppViewAccessMode,
        allowedDeviceTypes: List<String>,
        releaseNotes: String,
        useGlobalConfig: Boolean = false,
        playStoreUrl: String = appDistributionConfig.value.playStoreUrl,
        appStoreUrl: String = appDistributionConfig.value.appStoreUrl,
        apkDownloadUrl: String = appDistributionConfig.value.apkDownloadUrl
    ) {
        val schoolId = activeSchool.value?.id.orEmpty()
        val targetConfigId = if (useGlobalConfig || schoolId.isBlank()) "global" else schoolId
        viewModelScope.launch {
            _isBusy.value = true
            repository.saveAppDistributionConfig(
                configId = targetConfigId,
                downloadUrl = downloadUrl,
                viewAccessMode = viewAccessMode.key,
                allowedDeviceTypes = allowedDeviceTypes,
                releaseNotes = releaseNotes,
                playStoreUrl = playStoreUrl,
                appStoreUrl = appStoreUrl,
                apkDownloadUrl = apkDownloadUrl
            ).onSuccess {
                _bannerMessage.value = "Updated app view access (${viewAccessMode.label}) & share URLs."
            }.onFailure { err ->
                _bannerMessage.value = err.localizedMessage ?: "Unable to update app view access & download URL."
            }
            _isBusy.value = false
        }
    }

    fun updateMyProfileDetails(displayName: String, phone: String) {
        viewModelScope.launch {
            _isBusy.value = true
            repository.updateUserProfileDetails(displayName, phone)
                .onSuccess { _bannerMessage.value = "Updated your account profile details." }
                .onFailure { _bannerMessage.value = it.localizedMessage ?: "Could not update profile." }
            _isBusy.value = false
        }
    }

    fun revokeDeviceSession(sessionId: String) {
        viewModelScope.launch {
            _isBusy.value = true
            repository.revokeDeviceSession(sessionId)
                .onSuccess { _bannerMessage.value = "Removed device session." }
                .onFailure { _bannerMessage.value = it.localizedMessage ?: "Could not remove session." }
            _isBusy.value = false
        }
    }

    fun saveInventoryItem(
        existingItem: InventoryItem?,
        name: String,
        category: String,
        quantity: Int,
        minimumStock: Int,
        condition: String,
        assignedTo: String,
        notes: String
    ) {
        val school = activeSchool.value ?: return
        val profile = (userProfile.value as? UiState.Success)?.data
        val allTeachers = classes.value.flatMap { it.teacherUids }.distinct()
        viewModelScope.launch {
            _isBusy.value = true
            repository.upsertInventoryItem(
                school = school,
                existingItem = existingItem,
                name = name,
                category = category,
                quantity = quantity,
                minimumStock = minimumStock,
                condition = condition,
                assignedTo = assignedTo,
                notes = notes,
                teacherUids = allTeachers,
                actorName = profile?.displayName ?: userDisplayName,
                actorRole = profile?.activeRole ?: UserRole.SCHOOL_ADMIN.key
            ).onSuccess {
                _bannerMessage.value = "${if (existingItem != null) "Updated" else "Added"} $name in inventory."
            }.onFailure {
                _bannerMessage.value = it.localizedMessage ?: "Could not save inventory item."
            }
            _isBusy.value = false
        }
    }

    fun deleteInventoryItem(item: InventoryItem) {
        val profile = (userProfile.value as? UiState.Success)?.data
        viewModelScope.launch {
            _isBusy.value = true
            repository.deleteInventoryItem(
                item = item,
                actorName = profile?.displayName ?: userDisplayName,
                actorRole = profile?.activeRole ?: UserRole.SCHOOL_ADMIN.key
            ).onSuccess {
                _bannerMessage.value = "Removed ${item.name} from inventory."
            }.onFailure {
                _bannerMessage.value = it.localizedMessage ?: "Could not delete inventory item."
            }
            _isBusy.value = false
        }
    }

    fun saveFeeRecord(
        student: Student,
        existingRecord: FeeRecord?,
        title: String,
        term: String,
        academicYear: String,
        amountDueGhs: Double,
        amountPaidGhs: Double,
        paymentMethod: String,
        receiptNumber: String,
        dueDate: String
    ) {
        val school = activeSchool.value ?: return
        val profile = (userProfile.value as? UiState.Success)?.data
        viewModelScope.launch {
            _isBusy.value = true
            repository.upsertFeeRecord(
                school = school,
                student = student,
                existingRecord = existingRecord,
                title = title,
                term = term,
                academicYear = academicYear,
                amountDueGhs = amountDueGhs,
                amountPaidGhs = amountPaidGhs,
                paymentMethod = paymentMethod,
                receiptNumber = receiptNumber,
                dueDate = dueDate,
                actorName = profile?.displayName ?: userDisplayName,
                actorRole = profile?.activeRole ?: UserRole.SCHOOL_ADMIN.key
            ).onSuccess {
                _bannerMessage.value = "Saved fee record for ${student.fullName}."
            }.onFailure {
                _bannerMessage.value = it.localizedMessage ?: "Could not save fee record."
            }
            _isBusy.value = false
        }
    }

    fun deleteFeeRecord(record: FeeRecord) {
        val profile = (userProfile.value as? UiState.Success)?.data
        viewModelScope.launch {
            _isBusy.value = true
            repository.deleteFeeRecord(
                record = record,
                actorName = profile?.displayName ?: userDisplayName,
                actorRole = profile?.activeRole ?: UserRole.SCHOOL_ADMIN.key
            ).onSuccess {
                _bannerMessage.value = "Deleted fee record ${record.title}."
            }.onFailure {
                _bannerMessage.value = it.localizedMessage ?: "Could not delete fee record."
            }
            _isBusy.value = false
        }
    }

    fun exportFullSchoolBackup(context: Context) {
        SchoolBackupExporter.exportAndShareBackupJson(
            context = context,
            school = activeSchool.value,
            classes = classes.value,
            students = students.value,
            subjects = subjects.value,
            attendance = attendanceRecords.value,
            examReports = examReports.value,
            feeRecords = feeRecords.value,
            inventoryItems = inventoryItems.value
        ).onSuccess { file ->
            _bannerMessage.value = "Exported full backup: ${file.name}"
        }.onFailure { err ->
            _bannerMessage.value = "Backup export failed: ${err.localizedMessage}"
        }
    }

    fun inspectBackupJsonText(rawJson: String) {
        SchoolBackupExporter.inspectBackupJson(rawJson)
            .onSuccess { summary ->
                _lastBackupInspection.value = summary
                _bannerMessage.value = "Verified backup for ${summary.schoolName} (${summary.studentCount} pupils)."
            }
            .onFailure { err ->
                _bannerMessage.value = err.localizedMessage ?: "Invalid backup JSON."
            }
    }

    fun clearLocalExportCache(context: Context) {
        SchoolBackupExporter.clearLocalExportCache(context)
        _bannerMessage.value = "Cleared local exported PDF/CSV/JSON cache."
    }

    companion object {
        private const val TAG = "SafeTrackVM"
        const val SUPER_ADMIN_EMAIL = "ebenezerarhin414@gmail.com"

        fun defaultFallbackPlanCatalog(): List<SubscriptionPlan> = listOf(
            SubscriptionPlan(
                id = "starter",
                name = "Starter School",
                monthlyPriceGhs = 120.0,
                annualDiscountPercent = 15,
                pupilLimit = 100,
                trialDays = 14,
                features = listOf(
                    "Up to 100 enrolled pupils",
                    "Morning & afternoon movements + GPS/manual updates",
                    "Teacher-only dismissal & daily attendance",
                    "Email & push arrival alerts"
                ),
                isActive = true
            ),
            SubscriptionPlan(
                id = "professional",
                name = "Professional",
                monthlyPriceGhs = 300.0,
                annualDiscountPercent = 20,
                pupilLimit = 500,
                trialDays = 14,
                features = listOf(
                    "Up to 500 enrolled pupils",
                    "School perimeter geofencing + bulk class alerts",
                    "Exam reports, offline Room sync & audit logs",
                    "Priority school admin onboarding"
                ),
                isActive = true
            ),
            SubscriptionPlan(
                id = "campus_pro",
                name = "Campus Pro",
                monthlyPriceGhs = 650.0,
                annualDiscountPercent = 20,
                pupilLimit = 2500,
                trialDays = 14,
                features = listOf(
                    "Up to 2,500 enrolled pupils across departments",
                    "Full multi-role analytics & CSV/PDF exports",
                    "Dedicated multi-device distribution configuration",
                    "Custom retention & compliance audit history"
                ),
                isActive = true
            )
        )
    }
}
