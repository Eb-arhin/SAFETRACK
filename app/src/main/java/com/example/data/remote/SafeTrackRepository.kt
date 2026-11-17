package com.example.data.remote

import android.content.Context
import com.example.R
import com.example.data.local.OfflineAttendanceDao
import com.example.data.local.OfflineAttendanceEntity
import com.example.data.model.AppDistributionConfig
import com.example.data.model.AppNotification
import com.example.data.model.AppViewAccessMode
import com.example.data.model.AttendanceRecord
import com.example.data.model.AttendanceStatus
import com.example.data.model.AuditLog
import com.example.data.model.BulkDepartureResult
import com.example.data.model.ClassDepartureEntry
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
import com.example.data.model.UserDeviceSession
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.util.AppLinkValidator
import com.example.util.GeofenceManager
import com.example.util.ParsedStudentRow
import com.example.util.TrackedGeofenceStudent
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class SafeTrackRepository(
    private val db: FirebaseFirestore,
    private val offlineDao: OfflineAttendanceDao? = null,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context, offlineDao: OfflineAttendanceDao? = null) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        offlineDao = offlineDao,
        auth = Firebase.auth
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Google Sign-In is required before accessing SafeTrack.")
    }

    private fun todayDateKey(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    private fun currentTimeLabel(): String {
        return SimpleDateFormat("HH:mm", Locale.US).format(Date())
    }

    private inline fun <T> safeSnapshotFlow(
        path: String,
        crossinline block: () -> Flow<T>
    ): Flow<T> = flow {
        val uid = requireUserId()
        emitAll(
            block().catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                    handleFirestoreError(exceptionToLog, OperationType.LIST, path, auth)
                }
                throw error
            }
        )
    }

    // --- USER PROFILE & MEMBERSHIPS ---

    suspend fun ensureUserProfile(
        displayName: String,
        email: String,
        isSuperAdmin: Boolean
    ): Result<UserProfile> {
        val uid = requireUserId()
        val path = "users/$uid"
        return try {
            val docRef = db.document(path)
            val snap = docRef.get().await()
            if (snap.exists()) {
                val existing = snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                if (existing != null) {
                    return Result.success(existing)
                }
            }
            val initialRole = if (isSuperAdmin) UserRole.SUPER_ADMIN.key else UserRole.PARENT.key
            val payload = mapOf(
                "userId" to uid,
                "email" to email,
                "displayName" to displayName.ifBlank { email.substringBefore("@").ifBlank { "SafeTrack User" } },
                "activeSchoolId" to "",
                "activeRole" to initialRole,
                "locationConsentGranted" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            val created = docRef.get().await()
                .toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: UserProfile(
                    id = uid,
                    userId = uid,
                    email = email,
                    displayName = displayName,
                    activeSchoolId = "",
                    activeRole = initialRole,
                    locationConsentGranted = false
                )
            Result.success(created)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path, auth)
            Result.failure(e)
        }
    }

    fun observeUserProfile(): Flow<UserProfile?> = flow {
        val uid = requireUserId()
        val path = "users/$uid"
        emitAll(
            db.document(path).snapshots()
                .map { it.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(firestoreError ?: error, OperationType.GET, path, auth)
                    }
                    throw error
                }
        )
    }

    fun observeMyMemberships(): Flow<List<MembershipIndex>> = safeSnapshotFlow("users/memberships") {
        val uid = requireUserId()
        db.collection("users").document(uid).collection("memberships")
            .snapshots()
            .map { it.toObjects(MembershipIndex::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
    }

    suspend fun updateActiveContext(schoolId: String, role: String): Result<Unit> {
        val uid = requireUserId()
        val path = "users/$uid"
        return try {
            db.document(path).update(
                mapOf(
                    "activeSchoolId" to schoolId,
                    "activeRole" to role,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path, auth)
            Result.failure(e)
        }
    }

    suspend fun updateLocationConsent(granted: Boolean, schoolId: String, actorName: String, actorRole: String, adminUids: List<String>): Result<Unit> {
        val uid = requireUserId()
        val path = "users/$uid"
        return try {
            val batch = db.batch()
            batch.update(
                db.document(path),
                mapOf(
                    "locationConsentGranted" to granted,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            if (schoolId.isNotBlank()) {
                val logId = "log_${UUID.randomUUID().toString().replace("-", "").take(16)}"
                batch.set(
                    db.collection("audit_logs").document(logId),
                    mapOf(
                        "schoolId" to schoolId,
                        "actorUid" to uid,
                        "actorName" to actorName,
                        "actorRole" to actorRole,
                        "action" to if (granted) "location_consent_granted" else "location_consent_revoked",
                        "targetType" to "user",
                        "targetId" to uid,
                        "details" to "Parental/device GPS consent set to $granted",
                        "adminUids" to adminUids,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path, auth)
            Result.failure(e)
        }
    }

    // --- SUBSCRIPTION PLANS (MULTI-SCHOOL SAAS) ---

    fun observeSubscriptionPlans(): Flow<List<SubscriptionPlan>> = safeSnapshotFlow("subscription_plans") {
        db.collection("subscription_plans")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(SubscriptionPlan::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.monthlyPriceGhs }
            }
    }

    suspend fun seedDefaultPlansIfNeeded(): Result<Unit> {
        requireUserId()
        return try {
            val existing = db.collection("subscription_plans").get().await()
            if (existing.isEmpty) {
                val defaults = listOf(
                    "starter" to mapOf(
                        "name" to "Starter School",
                        "monthlyPriceGhs" to 120.0,
                        "annualDiscountPercent" to 15,
                        "pupilLimit" to 100,
                        "trialDays" to 14,
                        "features" to listOf(
                            "Up to 100 enrolled pupils",
                            "Morning & afternoon movements + GPS/manual updates",
                            "Teacher-only dismissal & daily attendance",
                            "Email & push arrival alerts"
                        ),
                        "isActive" to true,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    "professional" to mapOf(
                        "name" to "Professional",
                        "monthlyPriceGhs" to 300.0,
                        "annualDiscountPercent" to 20,
                        "pupilLimit" to 500,
                        "trialDays" to 14,
                        "features" to listOf(
                            "Up to 500 enrolled pupils",
                            "School perimeter geofencing + bulk class alerts",
                            "Exam reports, offline Room sync & audit logs",
                            "Priority school admin onboarding"
                        ),
                        "isActive" to true,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    "campus_pro" to mapOf(
                        "name" to "Campus Pro",
                        "monthlyPriceGhs" to 650.0,
                        "annualDiscountPercent" to 20,
                        "pupilLimit" to 2500,
                        "trialDays" to 14,
                        "features" to listOf(
                            "Up to 2,500 enrolled pupils across departments",
                            "Full multi-role analytics & CSV/PDF exports",
                            "Dedicated multi-device distribution configuration",
                            "Custom retention & compliance audit history"
                        ),
                        "isActive" to true,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                val batch = db.batch()
                defaults.forEach { (id, payload) ->
                    batch.set(db.collection("subscription_plans").document(id), payload)
                }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "subscription_plans", auth)
            Result.failure(e)
        }
    }

    suspend fun upsertSubscriptionPlan(plan: SubscriptionPlan): Result<Unit> {
        requireUserId()
        val planId = plan.id.ifBlank { "plan_${UUID.randomUUID().toString().replace("-", "").take(10)}" }
        val path = "subscription_plans/$planId"
        return try {
            val docRef = db.document(path)
            val snap = docRef.get().await()
            if (snap.exists()) {
                docRef.update(
                    mapOf(
                        "name" to plan.name,
                        "monthlyPriceGhs" to plan.monthlyPriceGhs,
                        "annualDiscountPercent" to plan.annualDiscountPercent,
                        "pupilLimit" to plan.pupilLimit,
                        "trialDays" to plan.trialDays,
                        "features" to plan.features.take(20),
                        "isActive" to plan.isActive,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "name" to plan.name,
                        "monthlyPriceGhs" to plan.monthlyPriceGhs,
                        "annualDiscountPercent" to plan.annualDiscountPercent,
                        "pupilLimit" to plan.pupilLimit,
                        "trialDays" to plan.trialDays,
                        "features" to plan.features.take(20),
                        "isActive" to plan.isActive,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path, auth)
            Result.failure(e)
        }
    }

    // --- SCHOOL REGISTRATION, ONBOARDING & MULTI-TENANT ADMIN ---

    suspend fun registerSchool(
        schoolName: String,
        city: String,
        country: String,
        address: String,
        phone: String,
        email: String,
        plan: SubscriptionPlan,
        billingCycle: String,
        ownerDisplayName: String,
        ownerEmail: String
    ): Result<String> {
        val uid = requireUserId()
        val schoolId = "sch_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val prefix = schoolName.filter { it.isLetterOrDigit() }.uppercase().take(3).ifBlank { "SCH" }
        val code = "ST-$prefix-${(1000..9999).random()}"
        val path = "schools/$schoolId"
        return try {
            val batch = db.batch()
            batch.set(
                db.document(path),
                mapOf(
                    "name" to schoolName.trim(),
                    "code" to code,
                    "address" to address.trim(),
                    "city" to city.trim().ifBlank { "Accra" },
                    "country" to country.trim().ifBlank { "Ghana" },
                    "phone" to phone.trim(),
                    "email" to email.trim(),
                    "ownerUserId" to uid,
                    "adminUids" to listOf(uid),
                    "planId" to plan.id.ifBlank { "starter" },
                    "planName" to plan.name.ifBlank { "Starter School" },
                    "monthlyPriceGhs" to plan.monthlyPriceGhs.coerceAtLeast(0.0),
                    "annualDiscountPercent" to plan.annualDiscountPercent.coerceIn(0, 90),
                    "pupilLimit" to plan.pupilLimit.coerceAtLeast(1),
                    "billingCycle" to billingCycle,
                    "subscriptionStatus" to if (plan.trialDays > 0) "trial" else "active",
                    "paymentProvider" to "pending",
                    "studentCount" to 0,
                    "trialDays" to plan.trialDays.coerceAtLeast(0),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                db.collection("schools").document(schoolId).collection("members").document(uid),
                mapOf(
                    "schoolId" to schoolId,
                    "userId" to uid,
                    "email" to ownerEmail,
                    "displayName" to ownerDisplayName.ifBlank { "School Admin" },
                    "role" to UserRole.SCHOOL_ADMIN.key,
                    "assignedClassIds" to emptyList<String>(),
                    "assignedSubjectIds" to emptyList<String>(),
                    "assignedSubjectNames" to emptyList<String>(),
                    "linkedStudentIds" to emptyList<String>(),
                    "adminUids" to listOf(uid),
                    "status" to "active",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                db.collection("users").document(uid).collection("memberships").document(schoolId),
                mapOf(
                    "schoolId" to schoolId,
                    "schoolName" to schoolName.trim(),
                    "role" to UserRole.SCHOOL_ADMIN.key,
                    "status" to "active",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                db.collection("users").document(uid),
                mapOf(
                    "activeSchoolId" to schoolId,
                    "activeRole" to UserRole.SCHOOL_ADMIN.key,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            val logId = "log_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            batch.set(
                db.collection("audit_logs").document(logId),
                mapOf(
                    "schoolId" to schoolId,
                    "actorUid" to uid,
                    "actorName" to ownerDisplayName.ifBlank { ownerEmail },
                    "actorRole" to UserRole.SCHOOL_ADMIN.key,
                    "action" to "school_registered",
                    "targetType" to "school",
                    "targetId" to schoolId,
                    "details" to "Registered ${schoolName.trim()} ($code) on ${plan.name} plan",
                    "adminUids" to listOf(uid),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(schoolId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path, auth)
            Result.failure(e)
        }
    }

    fun observeSchool(schoolId: String): Flow<School?> = flow {
        val uid = requireUserId()
        if (schoolId.isBlank()) {
            emit(null)
            return@flow
        }
        val path = "schools/$schoolId"
        emitAll(
            db.document(path).snapshots()
                .map { it.toObject(School::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(firestoreError ?: error, OperationType.GET, path, auth)
                    }
                    throw error
                }
        )
    }

    fun observeAllSchoolsForSuperAdmin(): Flow<List<School>> = safeSnapshotFlow("schools") {
        db.collection("schools")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(School::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
    }

    suspend fun updateSchoolSubscription(
        schoolId: String,
        plan: SubscriptionPlan?,
        billingCycle: String,
        subscriptionStatus: String?,
        paymentProvider: String,
        paymentReference: String?,
        actorName: String,
        actorRole: String,
        adminUids: List<String>
    ): Result<Unit> {
        val uid = requireUserId()
        val path = "schools/$schoolId"
        return try {
            val updates = mutableMapOf<String, Any>(
                "billingCycle" to billingCycle,
                "paymentProvider" to paymentProvider,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (plan != null) {
                updates["planId"] = plan.id
                updates["planName"] = plan.name
                updates["monthlyPriceGhs"] = plan.monthlyPriceGhs
                updates["annualDiscountPercent"] = plan.annualDiscountPercent
                updates["pupilLimit"] = plan.pupilLimit
            }
            if (subscriptionStatus != null) {
                updates["subscriptionStatus"] = subscriptionStatus
            }
            if (!paymentReference.isNullOrBlank()) {
                updates["paymentReference"] = paymentReference
            }
            val batch = db.batch()
            batch.update(db.document(path), updates)
            val logId = "log_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            batch.set(
                db.collection("audit_logs").document(logId),
                mapOf(
                    "schoolId" to schoolId,
                    "actorUid" to uid,
                    "actorName" to actorName,
                    "actorRole" to actorRole,
                    "action" to "subscription_updated",
                    "targetType" to "school",
                    "targetId" to schoolId,
                    "details" to "Updated billing ($billingCycle, $paymentProvider${subscriptionStatus?.let { ", status=$it" } ?: ""})",
                    "adminUids" to adminUids,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path, auth)
            Result.failure(e)
        }
    }

    fun observeSchoolMembers(
        schoolId: String,
        role: UserRole = UserRole.SCHOOL_ADMIN
    ): Flow<List<SchoolMember>> =
        if (schoolId.isBlank()) flowOf(emptyList())
        else safeSnapshotFlow("schools/$schoolId/members") {
            val uid = requireUserId()
            val ref = db.collection("schools").document(schoolId).collection("members")
            val query = when (role) {
                UserRole.SCHOOL_ADMIN -> ref.whereArrayContains("adminUids", uid)
                UserRole.SUPER_ADMIN, UserRole.TEACHER -> ref
                else -> return@safeSnapshotFlow flowOf(emptyList())
            }
            query.snapshots().map { snapshot ->
                snapshot.toObjects(SchoolMember::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedWith(compareBy<SchoolMember> { it.role != UserRole.TEACHER.key }.thenBy { it.displayName })
            }
        }

    suspend fun upsertTeacherMember(
        school: School,
        existingMember: SchoolMember?,
        displayName: String,
        email: String,
        phone: String,
        role: UserRole,
        assignedClassIds: List<String>,
        assignedSubjectIds: List<String> = emptyList(),
        assignedSubjectNames: List<String> = emptyList(),
        status: String,
        actorName: String,
        actorRole: String
    ): Result<String> {
        val uid = requireUserId()
        val memberUid = existingMember?.userId?.takeIf { it.isNotBlank() }
            ?: "tch_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val path = "schools/${school.id}/members/$memberUid"
        val cleanName = displayName.trim()
        val cleanEmail = email.trim().lowercase()
        val cleanPhone = phone.trim()
        val cleanSubjectIds = assignedSubjectIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(30)
        val cleanSubjectNames = assignedSubjectNames.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(30)
        val effectiveRole = if (actorRole == UserRole.TEACHER.key) UserRole.TEACHER.key else role.key
        return try {
            val batch = db.batch()
            if (existingMember != null) {
                val updates = mutableMapOf<String, Any>(
                    "displayName" to cleanName,
                    "email" to cleanEmail,
                    "role" to effectiveRole,
                    "assignedClassIds" to assignedClassIds.distinct().take(30),
                    "assignedSubjectIds" to cleanSubjectIds,
                    "assignedSubjectNames" to cleanSubjectNames,
                    "status" to if (status in listOf("active", "suspended")) status else existingMember.status,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (cleanPhone.isNotBlank()) {
                    updates["phone"] = cleanPhone
                }
                batch.update(db.document(path), updates)
            } else {
                val payload = mutableMapOf<String, Any>(
                    "schoolId" to school.id,
                    "userId" to memberUid,
                    "email" to cleanEmail,
                    "displayName" to cleanName,
                    "role" to effectiveRole,
                    "assignedClassIds" to assignedClassIds.distinct().take(30),
                    "assignedSubjectIds" to cleanSubjectIds,
                    "assignedSubjectNames" to cleanSubjectNames,
                    "linkedStudentIds" to emptyList<String>(),
                    "adminUids" to school.adminUids,
                    "status" to if (status in listOf("active", "suspended")) status else "active",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (cleanPhone.isNotBlank()) {
                    payload["phone"] = cleanPhone
                }
                batch.set(db.document(path), payload)
            }
            val logId = "log_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            batch.set(
                db.collection("audit_logs").document(logId),
                mapOf(
                    "schoolId" to school.id,
                    "actorUid" to uid,
                    "actorName" to actorName,
                    "actorRole" to actorRole,
                    "action" to if (existingMember != null) "member_updated" else "member_created",
                    "targetType" to "member",
                    "targetId" to memberUid,
                    "details" to "${if (existingMember != null) "Updated" else "Added"} $cleanName ($effectiveRole)",
                    "adminUids" to school.adminUids,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            // Sync class/student teacherUids when a School Admin or Super Admin assigns a real Firebase user to classes
            if (effectiveRole == UserRole.TEACHER.key &&
                !memberUid.startsWith("tch_") &&
                assignedClassIds.isNotEmpty() &&
                (actorRole == UserRole.SCHOOL_ADMIN.key || actorRole == UserRole.SUPER_ADMIN.key)
            ) {
                runCatching {
                    assignedClassIds.distinct().forEach { classId ->
                        db.collection("classes").document(classId)
                            .update("teacherUids", FieldValue.arrayUnion(memberUid))
                            .await()
                        val classStudents = db.collection("students")
                            .whereEqualTo("schoolId", school.id)
                            .whereEqualTo("classId", classId)
                            .whereArrayContains("adminUids", uid)
                            .get()
                            .await()
                        classStudents.documents.forEach { stuDoc ->
                            stuDoc.reference.update("teacherUids", FieldValue.arrayUnion(memberUid)).await()
                        }
                    }
                }
            }
            Result.success(memberUid)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path, auth)
            Result.failure(e)
        }
    }

    suspend fun deleteSchoolMember(
        member: SchoolMember,
        actorName: String,
        actorRole: String
    ): Result<Unit> {
        val uid = requireUserId()
        val memberUid = member.userId.ifBlank { member.id }
        val path = "schools/${member.schoolId}/members/$memberUid"
        return try {
            val batch = db.batch()
            batch.delete(db.document(path))
            val logId = "log_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            batch.set(
                db.collection("audit_logs").document(logId),
                mapOf(
                    "schoolId" to member.schoolId,
                    "actorUid" to uid,
                    "actorName" to actorName,
                    "actorRole" to actorRole,
                    "action" to "member_deleted",
                    "targetType" to "member",
                    "targetId" to memberUid,
                    "details" to "Deleted member ${member.displayName} (${member.role})",
                    "adminUids" to member.adminUids,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path, auth)
            Result.failure(e)
        }
    }

    fun observeSchoolInvites(schoolId: String): Flow<List<SchoolInvite>> =
        if (schoolId.isBlank()) flowOf(emptyList())
        else safeSnapshotFlow("school_invites") {
            val uid = requireUserId()
            db.collection("school_invites")
                .whereEqualTo("schoolId", schoolId)
                .whereArrayContains("adminUids", uid)
                .snapshots()
                .map { it.toObjects(SchoolInvite::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
        }

    fun observePendingInvitesForEmail(email: String): Flow<List<SchoolInvite>> =
        if (email.isBlank()) flowOf(emptyList())
        else safeSnapshotFlow("school_invites") {
            db.collection("school_invites")
                .whereEqualTo("targetEmail", email.lowercase().trim())
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(SchoolInvite::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .filter { it.status == "pending" }
                }
        }

    suspend fun createSchoolInvite(
        school: School,
        targetEmail: String,
        role: UserRole,
        assignedClassIds: List<String>,
        linkedStudentIds: List<String>,
        actorName: String
    ): Result<String> {
        val uid = requireUserId()
        val inviteCode = "INV-${school.code.filter { it.isLetterOrDigit() }.take(4)}-${UUID.randomUUID().toString().replace("-", "").take(6).uppercase()}"
        val path = "school_invites/$inviteCode"
        return try {
            val batch = db.batch()
            batch.set(
                db.document(path),
                mapOf(
                    "inviteCode" to inviteCode,
                    "schoolId" to school.id,
                    "schoolName" to school.name,
                    "targetEmail" to targetEmail.lowercase().trim(),
                    "role" to role.key,
                    "assignedClassIds" to assignedClassIds.take(30),
                    "linkedStudentIds" to linkedStudentIds.take(30),
                    "adminUids" to school.adminUids,
                    "status" to "pending",
                    "createdByUid" to uid,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            val logId = "log_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            batch.set(
                db.collection("audit_logs").document(logId),
                mapOf(
                    "schoolId" to school.id,
                    "actorUid" to uid,
                    "actorName" to actorName,
                    "actorRole" to UserRole.SCHOOL_ADMIN.key,
                    "action" to "invite_created",
                    "targetType" to "invite",
                    "targetId" to inviteCode,
                    "details" to "Invited ${targetEmail.ifBlank { "open code" }} as ${role.label}",
                    "adminUids" to school.adminUids,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(inviteCode)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path, auth)
            Result.failure(e)
        }
    }

    suspend fun inspectSchoolInvite(inviteCode: String): InviteInspectionResult {
        val clean = AppLinkValidator.sanitizeInviteCode(inviteCode)
            ?: return InviteInspectionResult(
                code = inviteCode.trim(),
                status = InviteInspectionStatus.INVALID_FORMAT,
                message = "This invite link is malformed or incomplete. Ask your school admin for a fresh invite code."
            )
        val normalized = clean.uppercase()
        return try {
            val snap = db.document("school_invites/$normalized").get().await()
            val invite = snap.toObject(SchoolInvite::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: return InviteInspectionResult(
                    code = normalized,
                    status = InviteInspectionStatus.NOT_FOUND,
                    message = "Invite code $normalized was not found. It may have been typed incorrectly or removed."
                )
            when (invite.status) {
                "pending" -> InviteInspectionResult(
                    code = normalized,
                    status = InviteInspectionStatus.VALID_PENDING,
                    message = "Ready to join ${invite.schoolName} as ${UserRole.fromKey(invite.role).label}.",
                    invite = invite
                )
                "claimed" -> InviteInspectionResult(
                    code = normalized,
                    status = InviteInspectionStatus.ALREADY_CLAIMED,
                    message = "Invite code $normalized has already been used. Sign in with the account that claimed it or request a new code.",
                    invite = invite
                )
                "revoked" -> InviteInspectionResult(
                    code = normalized,
                    status = InviteInspectionStatus.REVOKED,
                    message = "Invite code $normalized was revoked by the school admin. Please request a new invite.",
                    invite = invite
                )
                else -> InviteInspectionResult(
                    code = normalized,
                    status = InviteInspectionStatus.NOT_FOUND,
                    message = "Invite code $normalized is no longer active."
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            InviteInspectionResult(
                code = normalized,
                status = InviteInspectionStatus.EMAIL_MISMATCH_OR_RESTRICTED,
                message = "Invite $normalized was not found or is restricted to another Google email account. Check the code or ask your school admin for a new invite."
            )
        }
    }

    suspend fun claimSchoolInvite(
        inviteCode: String,
        displayName: String,
        email: String
    ): Result<SchoolInvite> {
        val uid = requireUserId()
        val sanitized = AppLinkValidator.sanitizeInviteCode(inviteCode)
            ?: return Result.failure(IllegalArgumentException("Invalid invite code format. Check the code and try again."))
        val normalized = sanitized.uppercase()
        val path = "school_invites/$normalized"
        return try {
            val snap = db.document(path).get().await()
            val invite = snap.toObject(SchoolInvite::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: return Result.failure(IllegalArgumentException("Invite code $normalized not found."))
            if (invite.status != "pending") {
                return Result.failure(IllegalStateException("This invite has already been ${invite.status}."))
            }
            val batch = db.batch()
            batch.set(
                db.collection("schools").document(invite.schoolId).collection("members").document(uid),
                mapOf(
                    "schoolId" to invite.schoolId,
                    "userId" to uid,
                    "email" to email,
                    "displayName" to displayName.ifBlank { email.substringBefore("@") },
                    "role" to invite.role,
                    "assignedClassIds" to invite.assignedClassIds,
                    "linkedStudentIds" to invite.linkedStudentIds,
                    "adminUids" to invite.adminUids,
                    "inviteCode" to normalized,
                    "status" to "active",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                db.collection("users").document(uid).collection("memberships").document(invite.schoolId),
                mapOf(
                    "schoolId" to invite.schoolId,
                    "schoolName" to invite.schoolName,
                    "role" to invite.role,
                    "status" to "active",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                db.document(path),
                mapOf(
                    "status" to "claimed",
                    "claimedByUid" to uid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                db.collection("users").document(uid),
                mapOf(
                    "activeSchoolId" to invite.schoolId,
                    "activeRole" to invite.role,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(invite)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path, auth)
            Result.failure(e)
        }
    }

    // --- CLASSES & STUDENTS ---

    fun observeClasses(schoolId: String, role: UserRole): Flow<List<SchoolClass>> =
        if (schoolId.isBlank()) flowOf(emptyList())
        else safeSnapshotFlow("classes") {
            val uid = requireUserId()
            val query = when (role) {
                UserRole.SUPER_ADMIN -> db.collection("classes").whereEqualTo("schoolId", schoolId)
                UserRole.SCHOOL_ADMIN -> db.collection("classes")
                    .whereEqualTo("schoolId", schoolId)
                    .whereArrayContains("adminUids", uid)
                else -> db.collection("classes")
                    .whereEqualTo("schoolId", schoolId)
                    .whereArrayContains("teacherUids", uid)
            }
            query.snapshots().map { it.toObjects(SchoolClass::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
        }

    suspend fun createClass(
        school: School,
        name: String,
        gradeLevel: String,
        academicYear: String,
        teacherUids: List<String>,
        actorName: String
    ): Result<String> {
        val uid = requireUserId()
        val classId = "cls_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val path = "classes/$classId"
        return try {
            val batch = db.batch()
            batch.set(
                db.document(path),
                mapOf(
                    "schoolId" to school.id,
                    "name" to name.trim(),
                    "gradeLevel" to gradeLevel.trim(),
                    "academicYear" to academicYear.trim(),
                    "teacherUids" to teacherUids.distinct().take(20),
                    "adminUids" to school.adminUids,
                    "studentCount" to 0,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            val logId = "log_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            batch.set(
                db.collection("audit_logs").document(logId),
                mapOf(
                    "schoolId" to school.id,
                    "actorUid" to uid,
                    "actorName" to actorName,
                    "actorRole" to UserRole.SCHOOL_ADMIN.key,
                    "action" to "class_created",
                    "targetType" to "class",
                    "targetId" to classId,
                    "details" to "Created class ${name.trim()} (${gradeLevel.trim()})",
                    "adminUids" to school.adminUids,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(classId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path, auth)
            Result.failure(e)
        }
    }

    fun observeStudents(schoolId: String, role: UserRole): Flow<List<Student>> =
        if (schoolId.isBlank()) flowOf(emptyList())
        else safeSnapshotFlow("students") {
            val uid = requireUserId()
            val base = db.collection("students").whereEqualTo("schoolId", schoolId)
            val query = when (role) {
                UserRole.SUPER_ADMIN -> base
                UserRole.SCHOOL_ADMIN -> base.whereArrayContains("adminUids", uid)
                UserRole.TEACHER -> base.whereArrayContains("teacherUids", uid)
                UserRole.PICKUP_PERSON -> base.whereArrayContains("pickupUids", uid)
                UserRole.PARENT -> base.whereArrayContains("guardianUids", uid)
            }
            query.snapshots().map { snapshot ->
                snapshot.toObjects(Student::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.fullName }
            }
        }

    suspend fun createStudent(
        school: School,
        admissionNumber: String,
        fullName: String,
        schoolClass: SchoolClass,
        emergencyContactName: String,
        emergencyContactPhone: String,
        medicalNotes: String,
        approvedDestinations: List<String>,
        authorisedPickups: List<String>,
        guardianUids: List<String>,
        pickupUids: List<String>,
        photoUri: String?,
        actorName: String
    ): Result<String> {
        val uid = requireUserId()
        val studentId = "stu_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val path = "students/$studentId"
        return try {
            val destinations = (listOf(school.name) + approvedDestinations)
                .map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(15)
            val payload = mutableMapOf<String, Any>(
                "schoolId" to school.id,
                "admissionNumber" to admissionNumber.trim(),
                "fullName" to fullName.trim(),
                "classId" to schoolClass.id,
                "className" to schoolClass.name,
                "academicYear" to schoolClass.academicYear,
                "status" to "active",
                "approvedDestinations" to destinations,
                "authorisedPickups" to authorisedPickups.map { it.trim() }.filter { it.isNotEmpty() }.take(15),
                "guardianUids" to guardianUids.distinct().take(10),
                "teacherUids" to schoolClass.teacherUids.distinct().take(20),
                "pickupUids" to pickupUids.distinct().take(10),
                "adminUids" to school.adminUids,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (emergencyContactName.isNotBlank()) payload["emergencyContactName"] = emergencyContactName.trim()
            if (emergencyContactPhone.isNotBlank()) payload["emergencyContactPhone"] = emergencyContactPhone.trim()
            if (medicalNotes.isNotBlank()) payload["medicalNotes"] = medicalNotes.trim()
            if (!photoUri.isNullOrBlank()) payload["photoUri"] = photoUri

            val batch = db.batch()
            batch.set(db.document(path), payload)
            batch.update(
                db.collection("schools").document(school.id),
                mapOf(
                    "studentCount" to FieldValue.increment(1),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                db.collection("classes").document(schoolClass.id),
                mapOf(
                    "studentCount" to FieldValue.increment(1),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            val logId = "log_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            batch.set(
                db.collection("audit_logs").document(logId),
                mapOf(
                    "schoolId" to school.id,
                    "actorUid" to uid,
                    "actorName" to actorName,
                    "actorRole" to UserRole.SCHOOL_ADMIN.key,
                    "action" to "student_enrolled",
                    "targetType" to "student",
                    "targetId" to studentId,
                    "details" to "Enrolled ${fullName.trim()} (${admissionNumber.trim()}) into ${schoolClass.name}",
                    "adminUids" to school.adminUids,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(studentId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path, auth)
            Result.failure(e)
        }
    }

    suspend fun linkMemberToStudent(
        student: Student,
        memberUid: String,
        role: UserRole,
        actorName: String
    ): Result<Unit> {
        val uid = requireUserId()
        val path = "students/${student.id}"
        return try {
            val fieldName = when (role) {
                UserRole.PARENT -> "guardianUids"
                UserRole.TEACHER -> "teacherUids"
                UserRole.PICKUP_PERSON -> "pickupUids"
                UserRole.SCHOOL_ADMIN -> "adminUids"
                else -> "guardianUids"
            }
            val currentList = when (role) {
                UserRole.PARENT -> student.guardianUids
                UserRole.TEACHER -> student.teacherUids
                UserRole.PICKUP_PERSON -> student.pickupUids
                UserRole.SCHOOL_ADMIN -> student.adminUids
                else -> student.guardianUids
            }
            val updated = (currentList + memberUid).distinct().take(10)
            val batch = db.batch()
            batch.update(
                db.document(path),
                mapOf(
                    fieldName to updated,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            val logId = "log_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            batch.set(
                db.collection("audit_logs").document(logId),
                mapOf(
                    "schoolId" to student.schoolId,
                    "actorUid" to uid,
                    "actorName" to actorName,
                    "actorRole" to UserRole.SCHOOL_ADMIN.key,
                    "action" to "member_linked_to_student",
                    "targetType" to "student",
                    "targetId" to student.id,
                    "details" to "Linked $memberUid (${role.label}) to ${student.fullName}",
                    "adminUids" to student.adminUids,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path, auth)
            Result.failure(e)
        }
    }

    suspend fun updateStudentLifecycleStatus(
        student: Student,
        newStatus: String,
        newClassId: String?,
        newClassName: String?,
        actorName: String
    ): Result<Unit> {
        val uid = requireUserId()
        val path = "students/${student.id}"
        return try {
            val updates = mutableMapOf<String, Any>(
                "status" to newStatus,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!newClassId.isNullOrBlank() && !newClassName.isNullOrBlank()) {
                updates["classId"] = newClassId
                updates["className"] = newClassName
            }
            val batch = db.batch()
            batch.update(db.document(path), updates)
            val logId = "log_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            batch.set(
                db.collection("audit_logs").document(logId),
                mapOf(
                    "schoolId" to student.schoolId,
                    "actorUid" to uid,
                    "actorName" to actorName,
                    "actorRole" to UserRole.SCHOOL_ADMIN.key,
                    "action" to "student_lifecycle_$newStatus",
                    "targetType" to "student",
                    "targetId" to student.id,
                    "details" to "${student.fullName} status changed to $newStatus${newClassName?.let { " ($it)" } ?: ""}",
                    "adminUids" to student.adminUids,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path, auth)
            Result.failure(e)
        }
    }

    suspend fun addApprovedDestination(student: Student, destination: String): Result<Unit> {
        requireUserId()
        val path = "students/${student.id}"
        return try {
            val updated = (student.approvedDestinations + destination.trim())
                .filter { it.isNotBlank() }.distinct().take(15)
            db.document(path).update(
                mapOf(
                    "approvedDestinations" to updated,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path, auth)
            Result.failure(e)
        }
    }

    // --- MOVEMENT TRACKING & MORNING/AFTERNOON JOURNEYS ---

    fun observeJourneys(schoolId: String, role: UserRole): Flow<List<Journey>> =
        if (schoolId.isBlank()) flowOf(emptyList())
        else safeSnapshotFlow("journeys") {
            val uid = requireUserId()
            val base = db.collection("journeys").whereEqualTo("schoolId", schoolId)
            val query = when (role) {
                UserRole.SUPER_ADMIN -> base
                UserRole.SCHOOL_ADMIN -> base.whereArrayContains("adminUids", uid)
                UserRole.TEACHER -> base.whereArrayContains("teacherUids", uid)
                UserRole.PICKUP_PERSON -> base.whereArrayContains("pickupUids", uid)
                UserRole.PARENT -> base.whereArrayContains("guardianUids", uid)
            }
            query.snapshots().map { snapshot ->
                snapshot.toObjects(Journey::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.updatedAt?.seconds ?: it.createdAt?.seconds ?: 0L }
            }
        }

    suspend fun startMorningJourney(
        student: Student,
        destinationName: String,
        parentName: String,
        consentGranted: Boolean,
        latitude: Double?,
        longitude: Double?,
        manualNote: String?
    ): Result<String> {
        val uid = requireUserId()
        val journeyId = "jrn_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        val path = "journeys/$journeyId"
        val gpsAvailable = consentGranted && latitude != null && longitude != null
        return try {
            val payload = mutableMapOf<String, Any>(
                "schoolId" to student.schoolId,
                "studentId" to student.id,
                "studentName" to student.fullName,
                "classId" to student.classId,
                "className" to student.className,
                "direction" to "morning_to_school",
                "status" to MovementStatus.ON_THE_WAY_TO_SCHOOL.key,
                "destinationName" to destinationName,
                "destinationType" to "School",
                "initiatedByUid" to uid,
                "initiatedByRole" to UserRole.PARENT.key,
                "initiatedByName" to parentName,
                "gpsAvailable" to gpsAvailable,
                "consentGranted" to consentGranted,
                "isTrackingActive" to gpsAvailable,
                "guardianUids" to student.guardianUids,
                "teacherUids" to student.teacherUids,
                "pickupUids" to student.pickupUids,
                "adminUids" to student.adminUids,
                "startedAt" to FieldValue.serverTimestamp(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (consentGranted && latitude != null && longitude != null) {
                payload["liveLatitude"] = latitude
                payload["liveLongitude"] = longitude
                payload["lastKnownLatitude"] = latitude
                payload["lastKnownLongitude"] = longitude
            }
            if (!manualNote.isNullOrBlank()) {
                payload["manualNote"] = manualNote.trim()
            }

            val batch = db.batch()
            batch.set(db.document(path), payload)
            student.teacherUids.forEach { teacherUid ->
                val notifId = "ntf_${UUID.randomUUID().toString().replace("-", "").take(14)}"
                batch.set(
                    db.collection("notifications").document(notifId),
                    mapOf(
                        "schoolId" to student.schoolId,
                        "recipientUid" to teacherUid,
                        "title" to "${student.fullName} is on the way",
                        "body" to "Parent started morning journey to $destinationName. Confirm arrival physically upon arrival.",
                        "type" to "journey_started",
                        "studentId" to student.id,
                        "journeyId" to journeyId,
                        "isRead" to false,
                        "adminUids" to student.adminUids,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            val logId = "log_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            batch.set(
                db.collection("audit_logs").document(logId),
                mapOf(
                    "schoolId" to student.schoolId,
                    "actorUid" to uid,
                    "actorName" to parentName,
                    "actorRole" to UserRole.PARENT.key,
                    "action" to "morning_journey_started",
                    "targetType" to "journey",
                    "targetId" to journeyId,
                    "details" to "Morning journey started for ${student.fullName} (GPS=${gpsAvailable}, consent=$consentGranted)",
                    "adminUids" to student.adminUids,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(journeyId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path, auth)
            Result.failure(e)
        }
    }

    /**
     * Teacher/Admin physical arrival confirmation.
     * ONLY this action marks the student Present (or Late) in Attendance and stops morning GPS tracking.
     */
    suspend fun confirmPhysicalArrivalAtSchool(
        journey: Journey,
        student: Student,
        teacherName: String,
        teacherRole: String,
        markAsLate: Boolean
    ): Result<Unit> {
        val uid = requireUserId()
        val journeyPath = "journeys/${journey.id}"
        val dateKey = todayDateKey()
        val attendanceId = "${student.id}_$dateKey"
        val arrivalTime = currentTimeLabel()
        val attStatus = if (markAsLate) AttendanceStatus.LATE.key else AttendanceStatus.PRESENT.key
        return try {
            val batch = db.batch()
            batch.update(
                db.document(journeyPath),
                mapOf(
                    "status" to MovementStatus.ARRIVED_AT_SCHOOL.key,
                    "confirmedByUid" to uid,
                    "confirmedByName" to teacherName,
                    "isTrackingActive" to false,
                    "completedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            val attRef = db.collection("attendance").document(attendanceId)
            val existingAtt = attRef.get().await()
            if (existingAtt.exists()) {
                batch.update(
                    attRef,
                    mapOf(
                        "status" to attStatus,
                        "arrivalTime" to arrivalTime,
                        "recordedByUid" to uid,
                        "recordedByName" to teacherName,
                        "correctionReason" to "Physical arrival confirmed at school gate",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                batch.set(
                    attRef,
                    mapOf(
                        "schoolId" to student.schoolId,
                        "studentId" to student.id,
                        "studentName" to student.fullName,
                        "classId" to student.classId,
                        "className" to student.className,
                        "dateKey" to dateKey,
                        "term" to "Term 1",
                        "status" to attStatus,
                        "arrivalTime" to arrivalTime,
                        "recordedByUid" to uid,
                        "recordedByName" to teacherName,
                        "correctionReason" to "",
                        "isOfflineSynced" to false,
                        "guardianUids" to student.guardianUids,
                        "teacherUids" to student.teacherUids,
                        "adminUids" to student.adminUids,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            student.guardianUids.forEach { guardianUid ->
                val notifId = "ntf_${UUID.randomUUID().toString().replace("-", "").take(14)}"
                batch.set(
                    db.collection("notifications").document(notifId),
                    mapOf(
                        "schoolId" to student.schoolId,
                        "recipientUid" to guardianUid,
                        "title" to "${student.fullName} arrived safely at school",
                        "body" to "Physically confirmed by $teacherName at $arrivalTime (${if (markAsLate) "Late" else "Present"}).",
                        "type" to "arrived_at_school",
                        "studentId" to student.id,
                        "journeyId" to journey.id,
                        "isRead" to false,
                        "adminUids" to student.adminUids,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            val logId = "log_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            batch.set(
                db.collection("audit_logs").document(logId),
                mapOf(
                    "schoolId" to student.schoolId,
                    "actorUid" to uid,
                    "actorName" to teacherName,
                    "actorRole" to teacherRole,
                    "action" to "physical_arrival_confirmed",
                    "targetType" to "journey",
                    "targetId" to journey.id,
                    "details" to "Confirmed physical arrival for ${student.fullName} at $arrivalTime ($attStatus). Tracking stopped.",
                    "adminUids" to student.adminUids,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, journeyPath, auth)
            Result.failure(e)
        }
    }

    /**
     * Assigned-teacher authorised afternoon dismissal.
     */
    suspend fun initiateAfternoonDismissal(
        student: Student,
        destinationName: String,
        isAlternativeDestination: Boolean,
        teacherName: String,
        teacherRole: String,
        consentGranted: Boolean = false,
        latitude: Double?,
        longitude: Double?,
        manualNote: String?
    ): Result<String> {
        val uid = requireUserId()
        if (uid !in student.teacherUids) {
            return Result.failure(
                SecurityException("Only the assigned class teacher may authorize dismissal for ${student.fullName}.")
            )
        }
        val journeyId = "jrn_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        val path = "journeys/$journeyId"
        val gpsAvailable = consentGranted && latitude != null && longitude != null
        return try {
            val payload = mutableMapOf<String, Any>(
                "schoolId" to student.schoolId,
                "studentId" to student.id,
                "studentName" to student.fullName,
                "classId" to student.classId,
                "className" to student.className,
                "direction" to if (isAlternativeDestination) "alternative_destination" else "afternoon_to_home",
                "status" to MovementStatus.ON_THE_WAY_HOME.key,
                "destinationName" to destinationName,
                "destinationType" to if (isAlternativeDestination) "Approved Alternative" else "Home",
                "initiatedByUid" to uid,
                "initiatedByRole" to teacherRole,
                "initiatedByName" to teacherName,
                "gpsAvailable" to gpsAvailable,
                "consentGranted" to consentGranted,
                "isTrackingActive" to gpsAvailable,
                "guardianUids" to student.guardianUids,
                "teacherUids" to student.teacherUids,
                "pickupUids" to student.pickupUids,
                "adminUids" to student.adminUids,
                "startedAt" to FieldValue.serverTimestamp(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (gpsAvailable && latitude != null && longitude != null) {
                payload["liveLatitude"] = latitude
                payload["liveLongitude"] = longitude
                payload["lastKnownLatitude"] = latitude
                payload["lastKnownLongitude"] = longitude
            }
            if (!manualNote.isNullOrBlank()) {
                payload["manualNote"] = manualNote.trim()
            }

            val batch = db.batch()
            batch.set(db.document(path), payload)
            student.guardianUids.forEach { guardianUid ->
                val notifId = "ntf_${UUID.randomUUID().toString().replace("-", "").take(14)}"
                batch.set(
                    db.collection("notifications").document(notifId),
                    mapOf(
                        "schoolId" to student.schoolId,
                        "recipientUid" to guardianUid,
                        "title" to "${student.fullName} dismissed from school",
                        "body" to "Dismissed by $teacherName to $destinationName. Please confirm safe arrival upon reaching destination.",
                        "type" to "dismissed",
                        "studentId" to student.id,
                        "journeyId" to journeyId,
                        "isRead" to false,
                        "adminUids" to student.adminUids,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            val logId = "log_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            batch.set(
                db.collection("audit_logs").document(logId),
                mapOf(
                    "schoolId" to student.schoolId,
                    "actorUid" to uid,
                    "actorName" to teacherName,
                    "actorRole" to teacherRole,
                    "action" to "afternoon_dismissal_authorised",
                    "targetType" to "journey",
                    "targetId" to journeyId,
                    "details" to "Authorised dismissal for ${student.fullName} to $destinationName",
                    "adminUids" to student.adminUids,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(journeyId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path, auth)
            Result.failure(e)
        }
    }

    /**
     * Whole-class single-click departure alert authorised by the assigned class teacher.
     */
    suspend fun dispatchClassDepartureAlerts(
        entries: List<ClassDepartureEntry>,
        notifyLateExceptions: Boolean,
        teacherName: String,
        teacherRole: String,
        consentGranted: Boolean = false,
        latitude: Double?,
        longitude: Double?
    ): Result<BulkDepartureResult> {
        val uid = requireUserId()
        if (entries.isEmpty()) return Result.success(BulkDepartureResult(0, 0))
        val unauthorized = entries.firstOrNull { !it.isLeavingLate && uid !in it.student.teacherUids }
        if (unauthorized != null) {
            return Result.failure(
                SecurityException("Only the assigned class teacher may dispatch dismissal for ${unauthorized.student.fullName}.")
            )
        }
        val timeLabel = currentTimeLabel()
        val gpsAvailable = consentGranted && latitude != null && longitude != null
        var departedCount = 0
        var lateExceptionCount = 0

        return try {
            for (chunk in entries.chunked(35)) {
                val batch = db.batch()
                var chunkHasWrites = false
                for (entry in chunk) {
                    val student = entry.student
                    if (!entry.isLeavingLate) {
                        val dest = entry.destinationName.trim().ifBlank { "Home" }
                        val isAlternative = !dest.contains("Home", ignoreCase = true)
                        val journeyId = "jrn_${UUID.randomUUID().toString().replace("-", "").take(14)}"
                        val payload = mutableMapOf<String, Any>(
                            "schoolId" to student.schoolId,
                            "studentId" to student.id,
                            "studentName" to student.fullName,
                            "classId" to student.classId,
                            "className" to student.className,
                            "direction" to if (isAlternative) "alternative_destination" else "afternoon_to_home",
                            "status" to MovementStatus.ON_THE_WAY_HOME.key,
                            "destinationName" to dest,
                            "destinationType" to if (isAlternative) "Approved Alternative" else "Home",
                            "initiatedByUid" to uid,
                            "initiatedByRole" to teacherRole,
                            "initiatedByName" to teacherName,
                            "gpsAvailable" to gpsAvailable,
                            "consentGranted" to consentGranted,
                            "isTrackingActive" to gpsAvailable,
                            "manualNote" to "Class departure alert sent at $timeLabel",
                            "guardianUids" to student.guardianUids,
                            "teacherUids" to student.teacherUids,
                            "pickupUids" to student.pickupUids,
                            "adminUids" to student.adminUids,
                            "startedAt" to FieldValue.serverTimestamp(),
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                        if (gpsAvailable && latitude != null && longitude != null) {
                            payload["liveLatitude"] = latitude
                            payload["liveLongitude"] = longitude
                            payload["lastKnownLatitude"] = latitude
                            payload["lastKnownLongitude"] = longitude
                        }
                        batch.set(db.collection("journeys").document(journeyId), payload)
                        chunkHasWrites = true
                        student.guardianUids.forEach { guardianUid ->
                            val notifId = "ntf_${UUID.randomUUID().toString().replace("-", "").take(14)}"
                            batch.set(
                                db.collection("notifications").document(notifId),
                                mapOf(
                                    "schoolId" to student.schoolId,
                                    "recipientUid" to guardianUid,
                                    "title" to "${student.fullName} has left school",
                                    "body" to "${student.className} dismissed at $timeLabel by $teacherName. Heading to $dest.",
                                    "type" to "journey",
                                    "studentId" to student.id,
                                    "journeyId" to journeyId,
                                    "isRead" to false,
                                    "adminUids" to student.adminUids,
                                    "createdAt" to FieldValue.serverTimestamp(),
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                            )
                        }
                        departedCount++
                    } else {
                        lateExceptionCount++
                        if (notifyLateExceptions) {
                            val cleanReason = entry.lateReason.trim().ifBlank { "Held after class for late pickup / school activity" }
                            student.guardianUids.forEach { guardianUid ->
                                val notifId = "ntf_${UUID.randomUUID().toString().replace("-", "").take(14)}"
                                batch.set(
                                    db.collection("notifications").document(notifId),
                                    mapOf(
                                        "schoolId" to student.schoolId,
                                        "recipientUid" to guardianUid,
                                        "title" to "${student.fullName} — Leaving Late Exception",
                                        "body" to "Not departing with ${student.className} at $timeLabel. Reason: $cleanReason (noted by $teacherName).",
                                        "type" to "late_departure",
                                        "studentId" to student.id,
                                        "isRead" to false,
                                        "adminUids" to student.adminUids,
                                        "createdAt" to FieldValue.serverTimestamp(),
                                        "updatedAt" to FieldValue.serverTimestamp()
                                    )
                                )
                                chunkHasWrites = true
                            }
                        }
                    }
                }
                if (chunkHasWrites) {
                    val sample = chunk.first().student
                    val logId = "log_${UUID.randomUUID().toString().replace("-", "").take(16)}"
                    batch.set(
                        db.collection("audit_logs").document(logId),
                        mapOf(
                            "schoolId" to sample.schoolId,
                            "actorUid" to uid,
                            "actorName" to teacherName,
                            "actorRole" to teacherRole,
                            "action" to "class_departure_dispatched",
                            "targetType" to "class",
                            "targetId" to sample.classId,
                            "details" to "Class departure batch: $departedCount departed, $lateExceptionCount leaving-late exception(s) at $timeLabel",
                            "adminUids" to sample.adminUids,
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                    )
                    batch.commit().await()
                }
            }
            Result.success(BulkDepartureResult(departedCount, lateExceptionCount))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "journeys", auth)
            Result.failure(e)
        }
    }

    suspend fun confirmSafeArrivalHome(
        journey: Journey,
        parentName: String
    ): Result<Unit> {
        val uid = requireUserId()
        val path = "journeys/${journey.id}"
        val terminalStatus = if (journey.direction == "alternative_destination") {
            MovementStatus.AT_APPROVED_ALTERNATIVE_DESTINATION.key
        } else {
            MovementStatus.ARRIVED_HOME.key
        }
        return try {
            val batch = db.batch()
            batch.update(
                db.document(path),
                mapOf(
                    "status" to terminalStatus,
                    "confirmedByUid" to uid,
                    "confirmedByName" to parentName,
                    "isTrackingActive" to false,
                    "gpsAvailable" to false,
                    "completedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            journey.teacherUids.forEach { teacherUid ->
                val notifId = "ntf_${UUID.randomUUID().toString().replace("-", "").take(14)}"
                batch.set(
                    db.collection("notifications").document(notifId),
                    mapOf(
                        "schoolId" to journey.schoolId,
                        "recipientUid" to teacherUid,
                        "title" to "${journey.studentName} arrived safely",
                        "body" to "Parent $parentName confirmed safe arrival at ${journey.destinationName}. Tracking stopped.",
                        "type" to "arrived_home",
                        "studentId" to journey.studentId,
                        "journeyId" to journey.id,
                        "isRead" to false,
                        "adminUids" to journey.adminUids,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            val logId = "log_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            batch.set(
                db.collection("audit_logs").document(logId),
                mapOf(
                    "schoolId" to journey.schoolId,
                    "actorUid" to uid,
                    "actorName" to parentName,
                    "actorRole" to UserRole.PARENT.key,
                    "action" to "safe_arrival_home_confirmed",
                    "targetType" to "journey",
                    "targetId" to journey.id,
                    "details" to "Parent confirmed safe arrival ($terminalStatus). Location tracking stopped.",
                    "adminUids" to journey.adminUids,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path, auth)
            Result.failure(e)
        }
    }

    suspend fun updateJourneyLiveOrManual(
        journey: Journey,
        newStatus: String,
        gpsAvailable: Boolean,
        latitude: Double?,
        longitude: Double?,
        manualNote: String?,
        actorName: String,
        actorRole: String
    ): Result<Unit> {
        val uid = requireUserId()
        val path = "journeys/${journey.id}"
        return try {
            val updates = mutableMapOf<String, Any>(
                "status" to newStatus,
                "gpsAvailable" to gpsAvailable,
                "isTrackingActive" to gpsAvailable,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (gpsAvailable && latitude != null && longitude != null) {
                updates["liveLatitude"] = latitude
                updates["liveLongitude"] = longitude
                updates["lastKnownLatitude"] = latitude
                updates["lastKnownLongitude"] = longitude
            }
            if (!manualNote.isNullOrBlank()) {
                updates["manualNote"] = manualNote.trim()
            }
            val batch = db.batch()
            batch.update(db.document(path), updates)
            if (newStatus == MovementStatus.JOURNEY_DELAYED.key || newStatus == MovementStatus.EMERGENCY_REPORTED.key) {
                val recipients = (journey.guardianUids + journey.teacherUids + journey.adminUids).distinct()
                recipients.forEach { recipient ->
                    val notifId = "ntf_${UUID.randomUUID().toString().replace("-", "").take(14)}"
                    batch.set(
                        db.collection("notifications").document(notifId),
                        mapOf(
                            "schoolId" to journey.schoolId,
                            "recipientUid" to recipient,
                            "title" to if (newStatus == MovementStatus.EMERGENCY_REPORTED.key)
                                "EMERGENCY: ${journey.studentName}" else "Delay Alert: ${journey.studentName}",
                            "body" to (manualNote?.ifBlank { null } ?: "Journey status updated to ${MovementStatus.fromKey(newStatus).label}"),
                            "type" to newStatus,
                            "studentId" to journey.studentId,
                            "journeyId" to journey.id,
                            "isRead" to false,
                            "adminUids" to journey.adminUids,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }
            val logId = "log_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            batch.set(
                db.collection("audit_logs").document(logId),
                mapOf(
                    "schoolId" to journey.schoolId,
                    "actorUid" to uid,
                    "actorName" to actorName,
                    "actorRole" to actorRole,
                    "action" to "journey_updated_$newStatus",
                    "targetType" to "journey",
                    "targetId" to journey.id,
                    "details" to "Status=$newStatus, GPS=$gpsAvailable${manualNote?.let { ", note=$it" } ?: ""}",
                    "adminUids" to journey.adminUids,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path, auth)
            Result.failure(e)
        }
    }

    // --- ATTENDANCE & OFFLINE SYNC ---

    fun observeAttendance(schoolId: String, role: UserRole): Flow<List<AttendanceRecord>> =
        if (schoolId.isBlank()) flowOf(emptyList())
        else safeSnapshotFlow("attendance") {
            val uid = requireUserId()
            val base = db.collection("attendance").whereEqualTo("schoolId", schoolId)
            val query = when (role) {
                UserRole.SUPER_ADMIN -> base
                UserRole.SCHOOL_ADMIN -> base.whereArrayContains("adminUids", uid)
                UserRole.TEACHER -> base.whereArrayContains("teacherUids", uid)
                else -> base.whereArrayContains("guardianUids", uid)
            }
            query.snapshots().map { snapshot ->
                snapshot.toObjects(AttendanceRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.dateKey }
            }
        }

    fun observePendingOfflineAttendance(schoolId: String): Flow<List<OfflineAttendanceEntity>> {
        return if (schoolId.isBlank() || offlineDao == null) flowOf(emptyList())
        else offlineDao.observeUnsyncedForSchool(schoolId)
    }

    suspend fun recordOrCorrectAttendance(
        student: Student,
        dateKey: String,
        status: AttendanceStatus,
        correctionReason: String,
        teacherName: String,
        teacherRole: String,
        isOfflineMode: Boolean
    ): Result<Boolean> {
        val uid = requireUserId()
        val docId = "${student.id}_$dateKey"
        val path = "attendance/$docId"
        val arrivalTime = if (status == AttendanceStatus.PRESENT || status == AttendanceStatus.LATE) currentTimeLabel() else null

        if (isOfflineMode && offlineDao != null) {
            offlineDao.enqueue(
                OfflineAttendanceEntity(
                    documentId = docId,
                    schoolId = student.schoolId,
                    studentId = student.id,
                    studentName = student.fullName,
                    classId = student.classId,
                    className = student.className,
                    dateKey = dateKey,
                    term = "Term 1",
                    status = status.key,
                    arrivalTime = arrivalTime,
                    recordedByUid = uid,
                    recordedByName = teacherName,
                    correctionReason = correctionReason.trim(),
                    guardianUidsCsv = student.guardianUids.joinToString(","),
                    teacherUidsCsv = student.teacherUids.joinToString(","),
                    adminUidsCsv = student.adminUids.joinToString(",")
                )
            )
            return Result.success(false)
        }

        return try {
            val docRef = db.document(path)
            val snap = docRef.get().await()
            val batch = db.batch()
            if (snap.exists()) {
                val existingStatus = snap.getString("status") ?: ""
                if (existingStatus != status.key && correctionReason.trim().length < 3) {
                    return Result.failure(IllegalArgumentException("A correction reason (at least 3 characters) is required when changing recorded attendance."))
                }
                val updates = mutableMapOf<String, Any>(
                    "status" to status.key,
                    "recordedByUid" to uid,
                    "recordedByName" to teacherName,
                    "correctionReason" to correctionReason.trim(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (arrivalTime != null) updates["arrivalTime"] = arrivalTime
                batch.update(docRef, updates)
            } else {
                val payload = mutableMapOf<String, Any>(
                    "schoolId" to student.schoolId,
                    "studentId" to student.id,
                    "studentName" to student.fullName,
                    "classId" to student.classId,
                    "className" to student.className,
                    "dateKey" to dateKey,
                    "term" to "Term 1",
                    "status" to status.key,
                    "recordedByUid" to uid,
                    "recordedByName" to teacherName,
                    "correctionReason" to correctionReason.trim(),
                    "isOfflineSynced" to false,
                    "guardianUids" to student.guardianUids,
                    "teacherUids" to student.teacherUids,
                    "adminUids" to student.adminUids,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (arrivalTime != null) payload["arrivalTime"] = arrivalTime
                batch.set(docRef, payload)
            }
            val statusDisplay = if (status == AttendanceStatus.LATE) "Tardy (Late)" else status.label
            student.guardianUids.forEach { guardianUid ->
                val notifId = "ntf_${UUID.randomUUID().toString().replace("-", "").take(14)}"
                batch.set(
                    db.collection("notifications").document(notifId),
                    mapOf(
                        "schoolId" to student.schoolId,
                        "recipientUid" to guardianUid,
                        "title" to "${student.fullName} — Attendance: $statusDisplay",
                        "body" to "Marked $statusDisplay on $dateKey by $teacherName${arrivalTime?.let { " at $it" } ?: ""}${correctionReason.trim().takeIf { it.isNotBlank() }?.let { " • Note: $it" } ?: ""}",
                        "type" to "attendance",
                        "studentId" to student.id,
                        "isRead" to false,
                        "adminUids" to student.adminUids,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            val logId = "log_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            batch.set(
                db.collection("audit_logs").document(logId),
                mapOf(
                    "schoolId" to student.schoolId,
                    "actorUid" to uid,
                    "actorName" to teacherName,
                    "actorRole" to teacherRole,
                    "action" to if (snap.exists()) "attendance_corrected" else "attendance_recorded",
                    "targetType" to "attendance",
                    "targetId" to docId,
                    "details" to "${student.fullName} ($dateKey) marked $statusDisplay",
                    "reason" to correctionReason.trim().ifBlank { "Initial register entry" },
                    "adminUids" to student.adminUids,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path, auth)
            Result.failure(e)
        }
    }

    suspend fun markStudentsPresentBatch(
        students: List<Student>,
        dateKey: String,
        teacherName: String,
        teacherRole: String
    ): Result<Int> {
        val uid = requireUserId()
        if (students.isEmpty()) return Result.success(0)
        val arrivalTime = currentTimeLabel()
        return try {
            var markedCount = 0
            for (chunk in students.chunked(80)) {
                val batch = db.batch()
                for (student in chunk) {
                    val docId = "${student.id}_$dateKey"
                    val docRef = db.collection("attendance").document(docId)
                    val snap = docRef.get().await()
                    if (!snap.exists()) {
                        batch.set(
                            docRef,
                            mapOf(
                                "schoolId" to student.schoolId,
                                "studentId" to student.id,
                                "studentName" to student.fullName,
                                "classId" to student.classId,
                                "className" to student.className,
                                "dateKey" to dateKey,
                                "term" to "Term 1",
                                "status" to AttendanceStatus.PRESENT.key,
                                "arrivalTime" to arrivalTime,
                                "recordedByUid" to uid,
                                "recordedByName" to teacherName,
                                "correctionReason" to "",
                                "isOfflineSynced" to false,
                                "guardianUids" to student.guardianUids,
                                "teacherUids" to student.teacherUids,
                                "adminUids" to student.adminUids,
                                "createdAt" to FieldValue.serverTimestamp(),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                        student.guardianUids.forEach { guardianUid ->
                            val notifId = "ntf_${UUID.randomUUID().toString().replace("-", "").take(14)}"
                            batch.set(
                                db.collection("notifications").document(notifId),
                                mapOf(
                                    "schoolId" to student.schoolId,
                                    "recipientUid" to guardianUid,
                                    "title" to "${student.fullName} — Attendance: Present",
                                    "body" to "Marked Present on $dateKey at $arrivalTime by $teacherName",
                                    "type" to "attendance",
                                    "studentId" to student.id,
                                    "isRead" to false,
                                    "adminUids" to student.adminUids,
                                    "createdAt" to FieldValue.serverTimestamp(),
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                            )
                        }
                        markedCount++
                    }
                }
                if (markedCount > 0) {
                    val first = chunk.first()
                    val logId = "log_${UUID.randomUUID().toString().replace("-", "").take(16)}"
                    batch.set(
                        db.collection("audit_logs").document(logId),
                        mapOf(
                            "schoolId" to first.schoolId,
                            "actorUid" to uid,
                            "actorName" to teacherName,
                            "actorRole" to teacherRole,
                            "action" to "attendance_bulk_recorded",
                            "targetType" to "attendance",
                            "targetId" to dateKey,
                            "details" to "Marked $markedCount unmarked pupil(s) Present on $dateKey",
                            "reason" to "Daily class roll call",
                            "adminUids" to first.adminUids,
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                    )
                    batch.commit().await()
                }
            }
            Result.success(markedCount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "attendance", auth)
            Result.failure(e)
        }
    }

    suspend fun syncPendingOfflineAttendance(schoolId: String, actorName: String, actorRole: String): Result<Int> {
        val uid = requireUserId()
        val dao = offlineDao ?: return Result.success(0)
        val pending = dao.getUnsyncedForSchool(schoolId)
        if (pending.isEmpty()) return Result.success(0)
        var syncedCount = 0
        return try {
            for (item in pending) {
                val docRef = db.collection("attendance").document(item.documentId)
                val snap = docRef.get().await()
                val guardians = item.guardianUidsCsv.split(",").filter { it.isNotBlank() }
                val teachers = item.teacherUidsCsv.split(",").filter { it.isNotBlank() }
                val admins = item.adminUidsCsv.split(",").filter { it.isNotBlank() }
                val batch = db.batch()
                if (snap.exists()) {
                    val reason = item.correctionReason.ifBlank { "Synced offline attendance capture" }
                    val updates = mutableMapOf<String, Any>(
                        "status" to item.status,
                        "recordedByUid" to uid,
                        "recordedByName" to item.recordedByName,
                        "correctionReason" to reason,
                        "isOfflineSynced" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    item.arrivalTime?.let { updates["arrivalTime"] = it }
                    batch.update(docRef, updates)
                } else {
                    val payload = mutableMapOf<String, Any>(
                        "schoolId" to item.schoolId,
                        "studentId" to item.studentId,
                        "studentName" to item.studentName,
                        "classId" to item.classId,
                        "className" to item.className,
                        "dateKey" to item.dateKey,
                        "term" to item.term,
                        "status" to item.status,
                        "recordedByUid" to uid,
                        "recordedByName" to item.recordedByName,
                        "correctionReason" to item.correctionReason,
                        "isOfflineSynced" to true,
                        "guardianUids" to guardians,
                        "teacherUids" to teachers,
                        "adminUids" to admins,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    item.arrivalTime?.let { payload["arrivalTime"] = it }
                    batch.set(docRef, payload)
                }
                val logId = "log_${UUID.randomUUID().toString().replace("-", "").take(16)}"
                batch.set(
                    db.collection("audit_logs").document(logId),
                    mapOf(
                        "schoolId" to item.schoolId,
                        "actorUid" to uid,
                        "actorName" to actorName,
                        "actorRole" to actorRole,
                        "action" to "offline_attendance_synced",
                        "targetType" to "attendance",
                        "targetId" to item.documentId,
                        "details" to "Synced offline record for ${item.studentName} (${item.status})",
                        "adminUids" to admins,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.commit().await()
                dao.markSynced(item.localId)
                syncedCount++
            }
            dao.clearSynced()
            Result.success(syncedCount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "attendance", auth)
            Result.failure(e)
        }
    }

    // --- INCIDENTS, MESSAGES, NOTIFICATIONS, AUDIT LOGS ---

    fun observeIncidents(schoolId: String, role: UserRole): Flow<List<Incident>> =
        if (schoolId.isBlank()) flowOf(emptyList())
        else safeSnapshotFlow("incidents") {
            val uid = requireUserId()
            val base = db.collection("incidents").whereEqualTo("schoolId", schoolId)
            val query = when (role) {
                UserRole.SUPER_ADMIN -> base
                UserRole.SCHOOL_ADMIN -> base.whereArrayContains("adminUids", uid)
                UserRole.TEACHER -> base.whereArrayContains("teacherUids", uid)
                else -> base.whereArrayContains("guardianUids", uid)
            }
            query.snapshots().map { snapshot ->
                snapshot.toObjects(Incident::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
        }

    suspend fun reportIncident(
        student: Student,
        journeyId: String?,
        severity: String,
        title: String,
        description: String,
        reporterName: String,
        reporterRole: String
    ): Result<String> {
        val uid = requireUserId()
        val incidentId = "inc_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val path = "incidents/$incidentId"
        return try {
            val payload = mutableMapOf<String, Any>(
                "schoolId" to student.schoolId,
                "studentId" to student.id,
                "studentName" to student.fullName,
                "severity" to severity,
                "status" to "open",
                "title" to title.trim(),
                "description" to description.trim(),
                "reportedByUid" to uid,
                "reportedByName" to reporterName,
                "reportedByRole" to reporterRole,
                "guardianUids" to student.guardianUids,
                "teacherUids" to student.teacherUids,
                "adminUids" to student.adminUids,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!journeyId.isNullOrBlank()) payload["journeyId"] = journeyId

            val batch = db.batch()
            batch.set(db.document(path), payload)
            val recipients = (student.guardianUids + student.teacherUids + student.adminUids).distinct()
            recipients.forEach { recipient ->
                val notifId = "ntf_${UUID.randomUUID().toString().replace("-", "").take(14)}"
                batch.set(
                    db.collection("notifications").document(notifId),
                    mapOf(
                        "schoolId" to student.schoolId,
                        "recipientUid" to recipient,
                        "title" to "Safety Alert (${severity.uppercase()}): ${student.fullName}",
                        "body" to title.trim(),
                        "type" to "emergency",
                        "studentId" to student.id,
                        "isRead" to false,
                        "adminUids" to student.adminUids,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            val logId = "log_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            batch.set(
                db.collection("audit_logs").document(logId),
                mapOf(
                    "schoolId" to student.schoolId,
                    "actorUid" to uid,
                    "actorName" to reporterName,
                    "actorRole" to reporterRole,
                    "action" to "incident_reported",
                    "targetType" to "incident",
                    "targetId" to incidentId,
                    "details" to "Reported $severity incident for ${student.fullName}: ${title.trim()}",
                    "adminUids" to student.adminUids,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(incidentId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path, auth)
            Result.failure(e)
        }
    }

    suspend fun updateIncidentStatus(incident: Incident, newStatus: String): Result<Unit> {
        requireUserId()
        val path = "incidents/${incident.id}"
        return try {
            db.document(path).update(
                mapOf(
                    "status" to newStatus,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path, auth)
            Result.failure(e)
        }
    }

    fun observeMessages(schoolId: String, role: UserRole): Flow<List<Message>> =
        if (schoolId.isBlank()) flowOf(emptyList())
        else safeSnapshotFlow("messages") {
            val uid = requireUserId()
            val base = db.collection("messages").whereEqualTo("schoolId", schoolId)
            val query = when (role) {
                UserRole.SUPER_ADMIN -> base
                UserRole.SCHOOL_ADMIN -> base.whereArrayContains("adminUids", uid)
                UserRole.TEACHER -> base.whereArrayContains("teacherUids", uid)
                else -> base.whereArrayContains("guardianUids", uid)
            }
            query.snapshots().map { snapshot ->
                snapshot.toObjects(Message::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
        }

    suspend fun sendMessage(
        student: Student,
        senderName: String,
        senderRole: String,
        body: String
    ): Result<Unit> {
        val uid = requireUserId()
        val msgId = "msg_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        val path = "messages/$msgId"
        return try {
            db.document(path).set(
                mapOf(
                    "schoolId" to student.schoolId,
                    "studentId" to student.id,
                    "studentName" to student.fullName,
                    "senderUid" to uid,
                    "senderName" to senderName,
                    "senderRole" to senderRole,
                    "body" to body.trim(),
                    "guardianUids" to student.guardianUids,
                    "teacherUids" to student.teacherUids,
                    "adminUids" to student.adminUids,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path, auth)
            Result.failure(e)
        }
    }

    fun observeMyNotifications(): Flow<List<AppNotification>> = safeSnapshotFlow("notifications") {
        val uid = requireUserId()
        db.collection("notifications")
            .whereEqualTo("recipientUid", uid)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(AppNotification::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
    }

    suspend fun markNotificationRead(notificationId: String): Result<Unit> {
        requireUserId()
        val path = "notifications/$notificationId"
        return try {
            db.document(path).update(
                mapOf(
                    "isRead" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path, auth)
            Result.failure(e)
        }
    }

    fun observeAuditLogs(schoolId: String, isSuperAdmin: Boolean): Flow<List<AuditLog>> =
        safeSnapshotFlow("audit_logs") {
            val uid = requireUserId()
            val query = when {
                isSuperAdmin && schoolId.isBlank() -> db.collection("audit_logs")
                isSuperAdmin -> db.collection("audit_logs").whereEqualTo("schoolId", schoolId)
                schoolId.isNotBlank() -> db.collection("audit_logs")
                    .whereEqualTo("schoolId", schoolId)
                    .whereArrayContains("adminUids", uid)
                else -> db.collection("audit_logs").whereArrayContains("adminUids", uid)
            }
            query.snapshots().map { snapshot ->
                snapshot.toObjects(AuditLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
        }

    // --- SCHOOL PROFILE & LOGO ---

    suspend fun updateSchoolProfileAndLogo(
        school: School,
        name: String,
        city: String,
        phone: String,
        email: String,
        logoUri: String?,
        actorName: String,
        actorRole: String,
        address: String = school.address.orEmpty(),
        motto: String = school.motto,
        academicYear: String = school.academicYear,
        currentTerm: String = school.currentTerm,
        operatingHours: String = school.operatingHours
    ): Result<Unit> {
        val uid = requireUserId()
        val path = "schools/${school.id}"
        return try {
            val updates = mutableMapOf<String, Any>(
                "name" to name.trim().ifBlank { school.name },
                "city" to city.trim().ifBlank { school.city },
                "address" to address.trim().take(240),
                "phone" to phone.trim().take(40),
                "email" to email.trim().take(200),
                "motto" to motto.trim().take(200),
                "academicYear" to academicYear.trim().ifBlank { "2026/2027" }.take(40),
                "currentTerm" to currentTerm.trim().ifBlank { "Term 1" }.take(40),
                "operatingHours" to operatingHours.trim().ifBlank { "07:00 – 15:30" }.take(80),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!logoUri.isNullOrBlank()) {
                updates["logoUri"] = logoUri
            }
            val batch = db.batch()
            batch.update(db.document(path), updates)
            val logId = "log_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            batch.set(
                db.collection("audit_logs").document(logId),
                mapOf(
                    "schoolId" to school.id,
                    "actorUid" to uid,
                    "actorName" to actorName,
                    "actorRole" to actorRole,
                    "action" to "school_profile_updated",
                    "targetType" to "school",
                    "targetId" to school.id,
                    "details" to "Updated school profile for ${name.trim().ifBlank { school.name }}",
                    "adminUids" to school.adminUids,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path, auth)
            Result.failure(e)
        }
    }

    // --- STUDENT EDIT & CSV BULK IMPORT ---

    suspend fun updateStudentProfile(
        student: Student,
        admissionNumber: String,
        fullName: String,
        schoolClass: SchoolClass,
        emergencyContactName: String,
        emergencyContactPhone: String,
        medicalNotes: String,
        approvedDestinations: List<String>,
        authorisedPickups: List<String>,
        actorName: String,
        actorRole: String
    ): Result<Unit> {
        val uid = requireUserId()
        val path = "students/${student.id}"
        return try {
            val updates = mutableMapOf<String, Any>(
                "admissionNumber" to admissionNumber.trim(),
                "fullName" to fullName.trim(),
                "classId" to schoolClass.id,
                "className" to schoolClass.name,
                "academicYear" to schoolClass.academicYear,
                "emergencyContactName" to emergencyContactName.trim(),
                "emergencyContactPhone" to emergencyContactPhone.trim(),
                "medicalNotes" to medicalNotes.trim(),
                "approvedDestinations" to approvedDestinations.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(15),
                "authorisedPickups" to authorisedPickups.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(15),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val batch = db.batch()
            batch.update(db.document(path), updates)
            val logId = "log_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            batch.set(
                db.collection("audit_logs").document(logId),
                mapOf(
                    "schoolId" to student.schoolId,
                    "actorUid" to uid,
                    "actorName" to actorName,
                    "actorRole" to actorRole,
                    "action" to "student_updated",
                    "targetType" to "student",
                    "targetId" to student.id,
                    "details" to "Updated profile for ${fullName.trim()} (${admissionNumber.trim()}, ${schoolClass.name})",
                    "adminUids" to student.adminUids,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path, auth)
            Result.failure(e)
        }
    }

    suspend fun deleteStudent(
        student: Student,
        actorName: String,
        actorRole: String
    ): Result<Unit> {
        val uid = requireUserId()
        val path = "students/${student.id}"
        return try {
            val isAdmin = uid in student.adminUids
            val schoolSnap = if (isAdmin) db.collection("schools").document(student.schoolId).get().await() else null
            val classSnap = if (isAdmin && student.classId.isNotBlank()) {
                db.collection("classes").document(student.classId).get().await()
            } else null

            val batch = db.batch()
            batch.delete(db.document(path))
            if (isAdmin && schoolSnap != null && schoolSnap.exists() && (schoolSnap.getLong("studentCount") ?: 0L) > 0L) {
                batch.update(
                    db.collection("schools").document(student.schoolId),
                    mapOf(
                        "studentCount" to FieldValue.increment(-1),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            if (isAdmin && classSnap != null && classSnap.exists() && (classSnap.getLong("studentCount") ?: 0L) > 0L) {
                batch.update(
                    db.collection("classes").document(student.classId),
                    mapOf(
                        "studentCount" to FieldValue.increment(-1),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            val logId = "log_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            batch.set(
                db.collection("audit_logs").document(logId),
                mapOf(
                    "schoolId" to student.schoolId,
                    "actorUid" to uid,
                    "actorName" to actorName,
                    "actorRole" to actorRole,
                    "action" to "student_deleted",
                    "targetType" to "student",
                    "targetId" to student.id,
                    "details" to "Deleted pupil ${student.fullName} (${student.admissionNumber}, ${student.className})",
                    "adminUids" to student.adminUids,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path, auth)
            Result.failure(e)
        }
    }

    suspend fun bulkImportStudents(
        school: School,
        classes: List<SchoolClass>,
        defaultClass: SchoolClass,
        rows: List<ParsedStudentRow>,
        linkSelfAsGuardian: Boolean,
        actorName: String,
        actorRole: String
    ): Result<Int> {
        val uid = requireUserId()
        if (rows.isEmpty()) return Result.success(0)
        return try {
            var count = 0
            val chunks = rows.chunked(200)
            for (chunk in chunks) {
                val batch = db.batch()
                val classIncrements = mutableMapOf<String, Int>()
                for (row in chunk) {
                    val matchedClass = classes.firstOrNull {
                        it.name.equals(row.className, ignoreCase = true)
                    } ?: defaultClass
                    val studentId = "stu_${UUID.randomUUID().toString().replace("-", "").take(12)}"
                    val teacherList = (matchedClass.teacherUids + if (actorRole == UserRole.TEACHER.key) listOf(uid) else emptyList())
                        .distinct().take(20)
                    val guardianList = if (linkSelfAsGuardian) listOf(uid) else emptyList()
                    val destinations = listOf(school.name, row.homeDestination)
                        .map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(15)
                    val pickups = listOf(row.authorisedPickup)
                        .map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(15)

                    val payload = mutableMapOf<String, Any>(
                        "schoolId" to school.id,
                        "admissionNumber" to row.admissionNumber.trim(),
                        "fullName" to row.fullName.trim(),
                        "classId" to matchedClass.id,
                        "className" to matchedClass.name,
                        "academicYear" to matchedClass.academicYear,
                        "status" to "active",
                        "approvedDestinations" to destinations,
                        "authorisedPickups" to pickups,
                        "guardianUids" to guardianList,
                        "teacherUids" to teacherList,
                        "pickupUids" to emptyList<String>(),
                        "adminUids" to school.adminUids,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    if (row.emergencyContactName.isNotBlank()) payload["emergencyContactName"] = row.emergencyContactName.trim()
                    if (row.emergencyContactPhone.isNotBlank()) payload["emergencyContactPhone"] = row.emergencyContactPhone.trim()
                    batch.set(db.collection("students").document(studentId), payload)
                    classIncrements[matchedClass.id] = (classIncrements[matchedClass.id] ?: 0) + 1
                    count++
                }
                if (uid in school.adminUids) {
                    batch.update(
                        db.collection("schools").document(school.id),
                        mapOf(
                            "studentCount" to FieldValue.increment(chunk.size.toLong()),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    classIncrements.forEach { (clsId, inc) ->
                        batch.update(
                            db.collection("classes").document(clsId),
                            mapOf(
                                "studentCount" to FieldValue.increment(inc.toLong()),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                }
                val logId = "log_${UUID.randomUUID().toString().replace("-", "").take(16)}"
                batch.set(
                    db.collection("audit_logs").document(logId),
                    mapOf(
                        "schoolId" to school.id,
                        "actorUid" to uid,
                        "actorName" to actorName,
                        "actorRole" to actorRole,
                        "action" to "students_csv_imported",
                        "targetType" to "student",
                        "targetId" to school.id,
                        "details" to "Bulk imported ${chunk.size} pupil(s) via CSV/roster import",
                        "adminUids" to school.adminUids,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.commit().await()
            }
            Result.success(count)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "students", auth)
            Result.failure(e)
        }
    }

    // --- SUBJECTS MANAGEMENT ---

    fun observeSubjects(schoolId: String, role: UserRole): Flow<List<Subject>> =
        if (schoolId.isBlank()) flowOf(emptyList())
        else safeSnapshotFlow("subjects") {
            val uid = requireUserId()
            val base = db.collection("subjects").whereEqualTo("schoolId", schoolId)
            val query = when (role) {
                UserRole.SUPER_ADMIN -> base
                UserRole.SCHOOL_ADMIN -> base.whereArrayContains("adminUids", uid)
                UserRole.TEACHER -> base.whereArrayContains("teacherUids", uid)
                else -> return@safeSnapshotFlow flowOf(emptyList())
            }
            query.snapshots().map { snapshot ->
                snapshot.toObjects(Subject::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedWith(compareByDescending<Subject> { it.isCore }.thenBy { it.name })
            }
        }

    suspend fun upsertSubject(
        school: School,
        existingSubject: Subject?,
        name: String,
        code: String,
        level: String,
        isCore: Boolean,
        coreSlot: String,
        teacherUids: List<String>,
        actorName: String,
        actorRole: String
    ): Result<String> {
        val uid = requireUserId()
        val subjectId = existingSubject?.id?.takeIf { it.isNotBlank() }
            ?: "sub_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val path = "subjects/$subjectId"
        val resolvedCoreSlot = coreSlot.ifBlank { ExamGrading.inferCoreSlot(name) }
        val effectiveTeachers = (teacherUids + (existingSubject?.teacherUids.orEmpty()) +
            if (actorRole == UserRole.TEACHER.key) listOf(uid) else emptyList())
            .distinct().take(20)
        return try {
            val batch = db.batch()
            if (existingSubject != null) {
                batch.update(
                    db.document(path),
                    mapOf(
                        "name" to name.trim(),
                        "code" to code.trim().uppercase(),
                        "level" to level,
                        "isCore" to isCore,
                        "coreSlot" to resolvedCoreSlot,
                        "teacherUids" to effectiveTeachers,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                batch.set(
                    db.document(path),
                    mapOf(
                        "schoolId" to school.id,
                        "name" to name.trim(),
                        "code" to code.trim().uppercase().ifBlank { name.trim().take(4).uppercase() },
                        "level" to level,
                        "isCore" to isCore,
                        "coreSlot" to resolvedCoreSlot,
                        "teacherUids" to effectiveTeachers,
                        "adminUids" to school.adminUids,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            val logId = "log_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            batch.set(
                db.collection("audit_logs").document(logId),
                mapOf(
                    "schoolId" to school.id,
                    "actorUid" to uid,
                    "actorName" to actorName,
                    "actorRole" to actorRole,
                    "action" to if (existingSubject != null) "subject_updated" else "subject_created",
                    "targetType" to "subject",
                    "targetId" to subjectId,
                    "details" to "${if (existingSubject != null) "Updated" else "Added"} subject ${name.trim()} ($level, core=$isCore)",
                    "adminUids" to school.adminUids,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(subjectId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path, auth)
            Result.failure(e)
        }
    }

    suspend fun deleteSubject(
        subject: Subject,
        actorName: String,
        actorRole: String
    ): Result<Unit> {
        val uid = requireUserId()
        val path = "subjects/${subject.id}"
        return try {
            val batch = db.batch()
            batch.delete(db.document(path))
            val logId = "log_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            batch.set(
                db.collection("audit_logs").document(logId),
                mapOf(
                    "schoolId" to subject.schoolId,
                    "actorUid" to uid,
                    "actorName" to actorName,
                    "actorRole" to actorRole,
                    "action" to "subject_deleted",
                    "targetType" to "subject",
                    "targetId" to subject.id,
                    "details" to "Deleted subject ${subject.name}",
                    "adminUids" to subject.adminUids,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path, auth)
            Result.failure(e)
        }
    }

    suspend fun seedDefaultGhanaSubjects(
        school: School,
        teacherUids: List<String>,
        actorName: String,
        actorRole: String
    ): Result<Int> {
        val uid = requireUserId()
        val effectiveTeachers = (teacherUids + if (actorRole == UserRole.TEACHER.key) listOf(uid) else emptyList())
            .distinct().take(20)
        val presets = listOf(
            Triple("Mathematics", "MATH", "mathematics") to ("both" to true),
            Triple("English Language", "ENG", "english") to ("both" to true),
            Triple("Integrated Science", "SCI", "science") to ("both" to true),
            Triple("Computing", "COMP", "computing") to ("primary" to true),
            Triple("Social Studies", "SOC", "social_studies") to ("jhs" to true),
            Triple("Religious & Moral Education", "RME", "") to ("both" to false),
            Triple("Ghanaian Language", "GHL", "") to ("both" to false),
            Triple("Creative Arts & Design", "CAD", "") to ("both" to false)
        )
        return try {
            val batch = db.batch()
            presets.forEach { (triple, levelCore) ->
                val (name, code, slot) = triple
                val (level, isCore) = levelCore
                val subId = "sub_${school.id.take(8)}_${code.lowercase()}"
                batch.set(
                    db.collection("subjects").document(subId),
                    mapOf(
                        "schoolId" to school.id,
                        "name" to name,
                        "code" to code,
                        "level" to level,
                        "isCore" to isCore,
                        "coreSlot" to slot,
                        "teacherUids" to effectiveTeachers,
                        "adminUids" to school.adminUids,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            val logId = "log_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            batch.set(
                db.collection("audit_logs").document(logId),
                mapOf(
                    "schoolId" to school.id,
                    "actorUid" to uid,
                    "actorName" to actorName,
                    "actorRole" to actorRole,
                    "action" to "subjects_seeded",
                    "targetType" to "subject",
                    "targetId" to school.id,
                    "details" to "Added ${presets.size} standard Ghana Core & Elective subjects",
                    "adminUids" to school.adminUids,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(presets.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "subjects", auth)
            Result.failure(e)
        }
    }

    // --- EXAM SCORES & TERMINAL REPORT CARDS ---

    fun observeExamReports(schoolId: String, role: UserRole): Flow<List<ExamReport>> =
        if (schoolId.isBlank()) flowOf(emptyList())
        else safeSnapshotFlow("exam_reports") {
            val uid = requireUserId()
            val base = db.collection("exam_reports").whereEqualTo("schoolId", schoolId)
            val query = when (role) {
                UserRole.SUPER_ADMIN -> base
                UserRole.SCHOOL_ADMIN -> base.whereArrayContains("adminUids", uid)
                UserRole.TEACHER -> base.whereArrayContains("teacherUids", uid)
                else -> base.whereArrayContains("guardianUids", uid)
            }
            query.snapshots().map { snapshot ->
                snapshot.toObjects(ExamReport::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedWith(compareBy<ExamReport> { it.term }.thenByDescending { it.overallTotal })
            }
        }

    suspend fun upsertExamReport(
        report: ExamReport,
        actorName: String,
        actorRole: String
    ): Result<String> {
        val uid = requireUserId()
        val safeTerm = report.term.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val reportId = report.id.ifBlank { "rep_${report.studentId}_$safeTerm" }
        val path = "exam_reports/$reportId"
        return try {
            val docRef = db.document(path)
            val existing = docRef.get().await()
            val scoreMaps = report.scores.take(30).map { s ->
                mapOf(
                    "subjectId" to s.subjectId,
                    "subjectName" to s.subjectName,
                    "isCore" to s.isCore,
                    "coreSlot" to s.coreSlot,
                    "rawClassScore" to s.rawClassScore,
                    "rawExamScore" to s.rawExamScore,
                    "classScore50" to s.classScore50,
                    "examScore50" to s.examScore50,
                    "totalScore" to s.totalScore,
                    "grade" to s.grade,
                    "remark" to s.remark
                )
            }
            val batch = db.batch()
            if (existing.exists()) {
                val updates = mutableMapOf<String, Any>(
                    "schoolName" to report.schoolName,
                    "schoolCity" to report.schoolCity,
                    "schoolPhone" to report.schoolPhone,
                    "schoolEmail" to report.schoolEmail,
                    "studentName" to report.studentName,
                    "admissionNumber" to report.admissionNumber,
                    "classId" to report.classId,
                    "className" to report.className,
                    "academicYear" to report.academicYear,
                    "term" to report.term,
                    "level" to report.level,
                    "scores" to scoreMaps,
                    "overallTotal" to report.overallTotal,
                    "averageScore" to report.averageScore,
                    "positionText" to report.positionText,
                    "aggregate" to report.aggregate,
                    "aggregateBreakdown" to report.aggregateBreakdown,
                    "attendanceSummary" to report.attendanceSummary,
                    "attitudeAndConduct" to report.attitudeAndConduct,
                    "respectfulness" to report.respectfulness,
                    "punctuality" to report.punctuality,
                    "classroomParticipation" to report.classroomParticipation,
                    "neatnessAndResponsibility" to report.neatnessAndResponsibility,
                    "teacherRemarks" to report.teacherRemarks,
                    "headTeacherRemarks" to report.headTeacherRemarks,
                    "classTeacherName" to report.classTeacherName,
                    "classTeacherSignature" to report.classTeacherSignature,
                    "headTeacherName" to report.headTeacherName,
                    "headTeacherSignature" to report.headTeacherSignature,
                    "issuanceDate" to report.issuanceDate,
                    "recordedByUid" to uid,
                    "recordedByName" to actorName,
                    "guardianUids" to report.guardianUids,
                    "teacherUids" to report.teacherUids,
                    "adminUids" to report.adminUids,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (!report.schoolLogoUri.isNullOrBlank()) {
                    updates["schoolLogoUri"] = report.schoolLogoUri
                }
                batch.update(docRef, updates)
            } else {
                val payload = mutableMapOf<String, Any>(
                    "schoolId" to report.schoolId,
                    "schoolName" to report.schoolName,
                    "schoolCity" to report.schoolCity,
                    "schoolPhone" to report.schoolPhone,
                    "schoolEmail" to report.schoolEmail,
                    "studentId" to report.studentId,
                    "studentName" to report.studentName,
                    "admissionNumber" to report.admissionNumber,
                    "classId" to report.classId,
                    "className" to report.className,
                    "academicYear" to report.academicYear,
                    "term" to report.term,
                    "level" to report.level,
                    "scores" to scoreMaps,
                    "overallTotal" to report.overallTotal,
                    "averageScore" to report.averageScore,
                    "positionText" to report.positionText,
                    "aggregate" to report.aggregate,
                    "aggregateBreakdown" to report.aggregateBreakdown,
                    "attendanceSummary" to report.attendanceSummary,
                    "attitudeAndConduct" to report.attitudeAndConduct,
                    "respectfulness" to report.respectfulness,
                    "punctuality" to report.punctuality,
                    "classroomParticipation" to report.classroomParticipation,
                    "neatnessAndResponsibility" to report.neatnessAndResponsibility,
                    "teacherRemarks" to report.teacherRemarks,
                    "headTeacherRemarks" to report.headTeacherRemarks,
                    "classTeacherName" to report.classTeacherName,
                    "classTeacherSignature" to report.classTeacherSignature,
                    "headTeacherName" to report.headTeacherName,
                    "headTeacherSignature" to report.headTeacherSignature,
                    "issuanceDate" to report.issuanceDate,
                    "recordedByUid" to uid,
                    "recordedByName" to actorName,
                    "guardianUids" to report.guardianUids,
                    "teacherUids" to report.teacherUids,
                    "adminUids" to report.adminUids,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (!report.schoolLogoUri.isNullOrBlank()) {
                    payload["schoolLogoUri"] = report.schoolLogoUri
                }
                batch.set(docRef, payload)
            }
            report.guardianUids.forEach { guardianUid ->
                val notifId = "ntf_${UUID.randomUUID().toString().replace("-", "").take(14)}"
                batch.set(
                    db.collection("notifications").document(notifId),
                    mapOf(
                        "schoolId" to report.schoolId,
                        "recipientUid" to guardianUid,
                        "title" to "${report.studentName} — ${report.term} Report Card Published",
                        "body" to "Position: ${report.positionText.ifBlank { "—" }} • Core Aggregate: ${report.aggregate} • Recorded by $actorName",
                        "type" to "exam_report",
                        "studentId" to report.studentId,
                        "isRead" to false,
                        "adminUids" to report.adminUids,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            val logId = "log_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            batch.set(
                db.collection("audit_logs").document(logId),
                mapOf(
                    "schoolId" to report.schoolId,
                    "actorUid" to uid,
                    "actorName" to actorName,
                    "actorRole" to actorRole,
                    "action" to if (existing.exists()) "exam_report_updated" else "exam_report_created",
                    "targetType" to "exam_report",
                    "targetId" to reportId,
                    "details" to "Saved ${report.term} terminal report for ${report.studentName} (Agg=${report.aggregate}, Pos=${report.positionText})",
                    "adminUids" to report.adminUids,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(reportId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path, auth)
            Result.failure(e)
        }
    }

    // --- SCHOOL PREMISES GEOFENCING ---

    suspend fun updateSchoolGeofence(
        school: School,
        latitude: Double,
        longitude: Double,
        radiusMeters: Double,
        enabled: Boolean,
        actorName: String,
        actorRole: String
    ): Result<Unit> {
        val uid = requireUserId()
        val path = "schools/${school.id}"
        val clampedRadius = radiusMeters.coerceIn(100.0, 2000.0)
        return try {
            val batch = db.batch()
            batch.update(
                db.document(path),
                mapOf(
                    "geofenceLatitude" to latitude,
                    "geofenceLongitude" to longitude,
                    "geofenceRadiusMeters" to clampedRadius,
                    "geofenceEnabled" to enabled,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            val logId = "log_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            batch.set(
                db.collection("audit_logs").document(logId),
                mapOf(
                    "schoolId" to school.id,
                    "actorUid" to uid,
                    "actorName" to actorName,
                    "actorRole" to actorRole,
                    "action" to "school_geofence_updated",
                    "targetType" to "school",
                    "targetId" to school.id,
                    "details" to "Geofence ${if (enabled) "enabled" else "disabled"} at (${"%.5f".format(latitude)}, ${"%.5f".format(longitude)}) radius=${clampedRadius.toInt()}m",
                    "adminUids" to school.adminUids,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path, auth)
            Result.failure(e)
        }
    }

    suspend fun recordGeofenceTransition(
        schoolId: String,
        schoolName: String,
        trackedStudents: List<TrackedGeofenceStudent>,
        isEntering: Boolean,
        latitude: Double,
        longitude: Double,
        timeLabel: String = currentTimeLabel()
    ): Result<Int> {
        val uid = requireUserId()
        if (schoolId.isBlank() || trackedStudents.isEmpty()) return Result.success(0)
        val notifType = if (isEntering) "geofence_enter" else "geofence_exit"
        val auditAction = if (isEntering) "geofence_entered" else "geofence_exited"
        return try {
            var notificationsCreated = 0
            val batch = db.batch()
            trackedStudents.take(20).forEach { student ->
                val (title, body) = GeofenceManager.formatGeofenceAlert(
                    studentName = student.fullName,
                    schoolName = schoolName,
                    isEntering = isEntering,
                    timeLabel = timeLabel
                )
                val recipients = (student.guardianUids + student.teacherUids).distinct()
                recipients.forEach { recipientUid ->
                    val notifId = "ntf_${UUID.randomUUID().toString().replace("-", "").take(14)}"
                    batch.set(
                        db.collection("notifications").document(notifId),
                        mapOf(
                            "schoolId" to schoolId,
                            "recipientUid" to recipientUid,
                            "title" to title,
                            "body" to body,
                            "type" to notifType,
                            "studentId" to student.id,
                            "isRead" to false,
                            "adminUids" to student.adminUids,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    notificationsCreated++
                }
                val logId = "log_${UUID.randomUUID().toString().replace("-", "").take(16)}"
                batch.set(
                    db.collection("audit_logs").document(logId),
                    mapOf(
                        "schoolId" to schoolId,
                        "actorUid" to uid,
                        "actorName" to "Play Services Geofence",
                        "actorRole" to "system",
                        "action" to auditAction,
                        "targetType" to "student",
                        "targetId" to student.id,
                        "details" to "$title at (${"%.5f".format(latitude)}, ${"%.5f".format(longitude)})",
                        "adminUids" to student.adminUids,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(notificationsCreated)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "notifications", auth)
            Result.failure(e)
        }
    }

    suspend fun deleteMyAccountData(): Result<Unit> {
        val uid = requireUserId()
        return try {
            val memberships = db.collection("users").document(uid)
                .collection("memberships").get().await()
            val batch = db.batch()
            memberships.documents.forEach { doc ->
                batch.delete(doc.reference)
            }
            batch.delete(db.collection("users").document(uid))
            batch.commit().await()
            runCatching { offlineDao?.clearAll() }
            runCatching { auth.currentUser?.delete()?.await() }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "users/$uid", auth)
            Result.failure(e)
        }
    }

    // --- APP VIEW ACCESS & DOWNLOAD DISTRIBUTION ---

    fun observeAppDistributionConfig(configId: String): Flow<AppDistributionConfig> =
        if (configId.isBlank()) flowOf(AppDistributionConfig())
        else safeSnapshotFlow("app_distribution/$configId") {
            db.collection("app_distribution").document(configId)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(
                            AppDistributionConfig::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        ) ?: AppDistributionConfig(configId = configId)
                    } else {
                        AppDistributionConfig(configId = configId)
                    }
                }
        }

    suspend fun saveAppDistributionConfig(
        configId: String,
        downloadUrl: String,
        viewAccessMode: String,
        allowedDeviceTypes: List<String>,
        releaseNotes: String,
        playStoreUrl: String = "",
        appStoreUrl: String = "",
        apkDownloadUrl: String = ""
    ): Result<Unit> {
        val uid = requireUserId()
        val cleanConfigId = configId.trim().ifBlank { "global" }

        val urlsToValidate = listOf(
            "Primary URL" to downloadUrl.trim(),
            "Google Play URL" to playStoreUrl.trim(),
            "Apple App Store URL" to appStoreUrl.trim(),
            "APK Download URL" to apkDownloadUrl.trim()
        )
        for ((label, raw) in urlsToValidate) {
            if (raw.isNotEmpty()) {
                val res = AppLinkValidator.validatePublicDownloadUrl(raw)
                if (!res.isValid) {
                    return Result.failure(
                        IllegalArgumentException("$label: ${res.errorMessage ?: "Enter a valid public HTTPS URL."}")
                    )
                }
            }
        }
        val anyUrlProvided = urlsToValidate.any { it.second.isNotEmpty() }
        if (!anyUrlProvided) {
            return Result.failure(
                IllegalArgumentException("Enter at least one verified HTTPS download, Google Play, Apple App Store, or APK URL.")
            )
        }
        val effectivePrimary = downloadUrl.trim().ifBlank {
            playStoreUrl.trim().ifBlank { apkDownloadUrl.trim().ifBlank { appStoreUrl.trim() } }
        }

        val cleanMode = AppViewAccessMode.fromKey(viewAccessMode).key
        val cleanDevices = allowedDeviceTypes
            .map { it.trim().lowercase() }
            .filter { it in listOf("phone", "tablet", "computer") }
            .distinct()
            .ifEmpty { listOf("phone", "tablet", "computer") }
        val cleanNotes = releaseNotes.trim().take(300)
        val path = "app_distribution/$cleanConfigId"
        return try {
            db.document(path).set(
                mapOf(
                    "configId" to cleanConfigId,
                    "downloadUrl" to effectivePrimary.take(500),
                    "playStoreUrl" to playStoreUrl.trim().take(500),
                    "appStoreUrl" to appStoreUrl.trim().take(500),
                    "apkDownloadUrl" to apkDownloadUrl.trim().take(500),
                    "viewAccessMode" to cleanMode,
                    "allowedDeviceTypes" to cleanDevices,
                    "releaseNotes" to cleanNotes,
                    "updatedByUid" to uid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path, auth)
            Result.failure(e)
        }
    }

    // --- USER PROFILE & DEVICE SESSIONS ---

    suspend fun updateUserProfileDetails(displayName: String, phone: String): Result<Unit> {
        val uid = requireUserId()
        val cleanName = displayName.trim()
        if (cleanName.isBlank()) return Result.failure(IllegalArgumentException("Display name cannot be empty."))
        val path = "users/$uid"
        return try {
            db.document(path).update(
                mapOf(
                    "displayName" to cleanName.take(100),
                    "phone" to phone.trim().take(40),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path, auth)
            Result.failure(e)
        }
    }

    fun observeDeviceSessions(): Flow<List<UserDeviceSession>> = safeSnapshotFlow("users/sessions") {
        val uid = requireUserId()
        db.collection("users").document(uid).collection("sessions")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(UserDeviceSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.updatedAt?.seconds ?: 0L }
            }
    }

    suspend fun registerDeviceSession(
        sessionId: String,
        deviceName: String,
        platform: String,
        appVersion: String
    ): Result<Unit> {
        val uid = requireUserId()
        val safeId = sessionId.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(64).ifBlank { "device_current" }
        val path = "users/$uid/sessions/$safeId"
        return try {
            val docRef = db.document(path)
            val existing = docRef.get().await()
            val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
            if (existing.exists()) {
                docRef.update(
                    mapOf(
                        "deviceName" to deviceName.take(120).ifBlank { "Android Device" },
                        "platform" to platform.take(40).ifBlank { "Android" },
                        "appVersion" to appVersion.take(40),
                        "lastActiveLabel" to timeStr,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "deviceName" to deviceName.take(120).ifBlank { "Android Device" },
                        "platform" to platform.take(40).ifBlank { "Android" },
                        "appVersion" to appVersion.take(40),
                        "lastActiveLabel" to timeStr,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path, auth)
            Result.failure(e)
        }
    }

    suspend fun revokeDeviceSession(sessionId: String): Result<Unit> {
        val uid = requireUserId()
        val path = "users/$uid/sessions/$sessionId"
        return try {
            db.document(path).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path, auth)
            Result.failure(e)
        }
    }

    // --- STAFF & SCHOOL INVENTORY ---

    fun observeInventoryItems(schoolId: String, role: UserRole): Flow<List<InventoryItem>> =
        if (schoolId.isBlank()) flowOf(emptyList())
        else safeSnapshotFlow("inventory_items") {
            val uid = requireUserId()
            val base = db.collection("inventory_items").whereEqualTo("schoolId", schoolId)
            val query = when (role) {
                UserRole.SUPER_ADMIN -> base
                UserRole.SCHOOL_ADMIN -> base.whereArrayContains("adminUids", uid)
                UserRole.TEACHER -> base.whereArrayContains("teacherUids", uid)
                else -> return@safeSnapshotFlow flowOf(emptyList())
            }
            query.snapshots().map { snapshot ->
                snapshot.toObjects(InventoryItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.name.lowercase() }
            }
        }

    suspend fun upsertInventoryItem(
        school: School,
        existingItem: InventoryItem?,
        name: String,
        category: String,
        quantity: Int,
        minimumStock: Int,
        condition: String,
        assignedTo: String,
        notes: String,
        teacherUids: List<String>,
        actorName: String,
        actorRole: String
    ): Result<String> {
        val uid = requireUserId()
        if (name.trim().length < 2) {
            return Result.failure(IllegalArgumentException("Item name must be at least 2 characters."))
        }
        val itemId = existingItem?.id?.takeIf { it.isNotBlank() }
            ?: "inv_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val path = "inventory_items/$itemId"
        val cleanTeachers = (teacherUids + existingItem?.teacherUids.orEmpty() +
            if (actorRole == UserRole.TEACHER.key) listOf(uid) else emptyList()).distinct().take(30)
        val cleanCondition = if (condition in listOf("new", "good", "fair", "needs_repair", "retired")) condition else "good"
        return try {
            val batch = db.batch()
            if (existingItem != null) {
                batch.update(
                    db.document(path),
                    mapOf(
                        "name" to name.trim().take(120),
                        "category" to category.trim().ifBlank { "General" }.take(60),
                        "quantity" to quantity.coerceIn(0, 1_000_000),
                        "minimumStock" to minimumStock.coerceIn(0, 1_000_000),
                        "condition" to cleanCondition,
                        "assignedTo" to assignedTo.trim().take(100),
                        "notes" to notes.trim().take(300),
                        "teacherUids" to cleanTeachers,
                        "updatedByUid" to uid,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                batch.set(
                    db.document(path),
                    mapOf(
                        "schoolId" to school.id,
                        "name" to name.trim().take(120),
                        "category" to category.trim().ifBlank { "General" }.take(60),
                        "quantity" to quantity.coerceIn(0, 1_000_000),
                        "minimumStock" to minimumStock.coerceIn(0, 1_000_000),
                        "condition" to cleanCondition,
                        "assignedTo" to assignedTo.trim().take(100),
                        "notes" to notes.trim().take(300),
                        "teacherUids" to cleanTeachers,
                        "adminUids" to school.adminUids,
                        "updatedByUid" to uid,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            val logId = "log_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            batch.set(
                db.collection("audit_logs").document(logId),
                mapOf(
                    "schoolId" to school.id,
                    "actorUid" to uid,
                    "actorName" to actorName,
                    "actorRole" to actorRole,
                    "action" to if (existingItem != null) "inventory_updated" else "inventory_added",
                    "targetType" to "inventory_item",
                    "targetId" to itemId,
                    "details" to "${if (existingItem != null) "Updated" else "Added"} ${name.trim()} (qty=${quantity.coerceAtLeast(0)})",
                    "adminUids" to school.adminUids,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(itemId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path, auth)
            Result.failure(e)
        }
    }

    suspend fun deleteInventoryItem(
        item: InventoryItem,
        actorName: String,
        actorRole: String
    ): Result<Unit> {
        val uid = requireUserId()
        val path = "inventory_items/${item.id}"
        return try {
            val batch = db.batch()
            batch.delete(db.document(path))
            val logId = "log_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            batch.set(
                db.collection("audit_logs").document(logId),
                mapOf(
                    "schoolId" to item.schoolId,
                    "actorUid" to uid,
                    "actorName" to actorName,
                    "actorRole" to actorRole,
                    "action" to "inventory_deleted",
                    "targetType" to "inventory_item",
                    "targetId" to item.id,
                    "details" to "Deleted inventory item ${item.name}",
                    "adminUids" to item.adminUids,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path, auth)
            Result.failure(e)
        }
    }

    // --- SCHOOL FINANCE & FEE LEDGER ---

    fun observeFeeRecords(schoolId: String, role: UserRole): Flow<List<FeeRecord>> =
        if (schoolId.isBlank()) flowOf(emptyList())
        else safeSnapshotFlow("fee_records") {
            val uid = requireUserId()
            val base = db.collection("fee_records").whereEqualTo("schoolId", schoolId)
            val query = when (role) {
                UserRole.SUPER_ADMIN -> base
                UserRole.SCHOOL_ADMIN -> base.whereArrayContains("adminUids", uid)
                UserRole.PARENT -> base.whereArrayContains("guardianUids", uid)
                else -> return@safeSnapshotFlow flowOf(emptyList())
            }
            query.snapshots().map { snapshot ->
                snapshot.toObjects(FeeRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.updatedAt?.seconds ?: 0L }
            }
        }

    suspend fun upsertFeeRecord(
        school: School,
        student: Student,
        existingRecord: FeeRecord?,
        title: String,
        term: String,
        academicYear: String,
        amountDueGhs: Double,
        amountPaidGhs: Double,
        paymentMethod: String,
        receiptNumber: String,
        dueDate: String,
        actorName: String,
        actorRole: String
    ): Result<String> {
        val uid = requireUserId()
        if (title.trim().length < 2) {
            return Result.failure(IllegalArgumentException("Fee title must be at least 2 characters."))
        }
        val due = amountDueGhs.coerceIn(0.0, 1_000_000.0)
        val paid = amountPaidGhs.coerceIn(0.0, 1_000_000.0)
        val computedStatus = when {
            paymentMethod == "waiver" -> "waived"
            paid >= due && due > 0 -> "paid"
            paid > 0.0 -> "partial"
            due == 0.0 -> "paid"
            else -> "unpaid"
        }
        val cleanMethod = if (paymentMethod in listOf("mobile_money", "bank_transfer", "cash", "card", "waiver")) {
            paymentMethod
        } else {
            "mobile_money"
        }
        val feeId = existingRecord?.id?.takeIf { it.isNotBlank() }
            ?: "fee_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val path = "fee_records/$feeId"
        return try {
            val batch = db.batch()
            if (existingRecord != null) {
                batch.update(
                    db.document(path),
                    mapOf(
                        "studentName" to student.fullName.take(120),
                        "className" to student.className.take(80),
                        "title" to title.trim().take(120),
                        "term" to term.trim().take(40),
                        "academicYear" to academicYear.trim().take(40),
                        "amountDueGhs" to due,
                        "amountPaidGhs" to paid,
                        "status" to computedStatus,
                        "paymentMethod" to cleanMethod,
                        "receiptNumber" to receiptNumber.trim().take(80),
                        "dueDate" to dueDate.trim().take(40),
                        "guardianUids" to student.guardianUids.take(10),
                        "recordedByUid" to uid,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                batch.set(
                    db.document(path),
                    mapOf(
                        "schoolId" to school.id,
                        "studentId" to student.id,
                        "studentName" to student.fullName.take(120),
                        "className" to student.className.take(80),
                        "title" to title.trim().take(120),
                        "term" to term.trim().take(40),
                        "academicYear" to academicYear.trim().take(40),
                        "amountDueGhs" to due,
                        "amountPaidGhs" to paid,
                        "status" to computedStatus,
                        "paymentMethod" to cleanMethod,
                        "receiptNumber" to receiptNumber.trim().take(80),
                        "dueDate" to dueDate.trim().take(40),
                        "guardianUids" to student.guardianUids.take(10),
                        "adminUids" to school.adminUids,
                        "recordedByUid" to uid,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            val logId = "log_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            batch.set(
                db.collection("audit_logs").document(logId),
                mapOf(
                    "schoolId" to school.id,
                    "actorUid" to uid,
                    "actorName" to actorName,
                    "actorRole" to actorRole,
                    "action" to if (existingRecord != null) "fee_record_updated" else "fee_record_created",
                    "targetType" to "fee_record",
                    "targetId" to feeId,
                    "details" to "Fee '${title.trim()}' for ${student.fullName}: Paid GH₵${"%.2f".format(paid)} / Due GH₵${"%.2f".format(due)} ($computedStatus)",
                    "adminUids" to school.adminUids,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(feeId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path, auth)
            Result.failure(e)
        }
    }

    suspend fun deleteFeeRecord(
        record: FeeRecord,
        actorName: String,
        actorRole: String
    ): Result<Unit> {
        val uid = requireUserId()
        val path = "fee_records/${record.id}"
        return try {
            val batch = db.batch()
            batch.delete(db.document(path))
            val logId = "log_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            batch.set(
                db.collection("audit_logs").document(logId),
                mapOf(
                    "schoolId" to record.schoolId,
                    "actorUid" to uid,
                    "actorName" to actorName,
                    "actorRole" to actorRole,
                    "action" to "fee_record_deleted",
                    "targetType" to "fee_record",
                    "targetId" to record.id,
                    "details" to "Deleted fee record ${record.title} for ${record.studentName}",
                    "adminUids" to record.adminUids,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path, auth)
            Result.failure(e)
        }
    }
}
