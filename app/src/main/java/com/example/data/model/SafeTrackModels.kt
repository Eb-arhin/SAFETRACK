package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class UserRole(val key: String, val label: String) {
    PARENT("parent", "Parent / Guardian"),
    TEACHER("teacher", "Teacher"),
    SCHOOL_ADMIN("school_admin", "School Admin"),
    SUPER_ADMIN("super_admin", "Super Admin"),
    PICKUP_PERSON("pickup_person", "Authorised Pickup");

    companion object {
        fun fromKey(key: String): UserRole = entries.firstOrNull { it.key == key } ?: PARENT
    }
}

enum class MovementStatus(val key: String, val label: String) {
    PREPARING_FOR_SCHOOL("preparing_for_school", "Preparing for school"),
    ON_THE_WAY_TO_SCHOOL("on_the_way_to_school", "On the way to school"),
    ARRIVED_AT_SCHOOL("arrived_at_school", "Arrived at school"),
    ON_THE_WAY_HOME("on_the_way_home", "On the way home"),
    ARRIVED_HOME("arrived_home", "Arrived home"),
    AT_APPROVED_ALTERNATIVE_DESTINATION("at_approved_alternative_destination", "At approved alternative destination"),
    JOURNEY_DELAYED("journey_delayed", "Journey delayed"),
    EMERGENCY_REPORTED("emergency_reported", "Emergency reported");

    companion object {
        fun fromKey(key: String): MovementStatus = entries.firstOrNull { it.key == key } ?: PREPARING_FOR_SCHOOL
    }
}

enum class AttendanceStatus(val key: String, val label: String) {
    PRESENT("present", "Present"),
    ABSENT("absent", "Absent"),
    LATE("late", "Late"),
    EXCUSED_ABSENCE("excused_absence", "Excused Absence"),
    EARLY_DEPARTURE("early_departure", "Early Departure"),
    PENDING_CONFIRMATION("pending_confirmation", "Pending Confirmation");

    companion object {
        fun fromKey(key: String): AttendanceStatus = entries.firstOrNull { it.key == key } ?: PENDING_CONFIRMATION
    }
}

enum class AppViewAccessMode(val key: String, val label: String, val description: String) {
    PUBLIC_OPEN(
        key = "public_open",
        label = "Public Open Access",
        description = "Anyone with the app URL can download, register a school, or join with an invite code."
    ),
    INVITE_ONLY(
        key = "invite_only",
        label = "Invite-Only Access",
        description = "Only teachers and parents with an admin-issued invite code or URL can view school records."
    ),
    STAFF_AND_PARENTS(
        key = "staff_and_parents",
        label = "Verified Members Only",
        description = "Restricted strictly to verified linked staff and guardians."
    );

    companion object {
        fun fromKey(key: String): AppViewAccessMode = entries.firstOrNull { it.key == key } ?: PUBLIC_OPEN
    }
}

enum class DeviceViewLayoutMode(val key: String, val label: String) {
    AUTO("auto", "Auto Responsive"),
    PHONE("phone", "Phone View"),
    TABLET("tablet", "Tablet View"),
    COMPUTER("computer", "Computer / Desktop View");

    companion object {
        fun fromKey(key: String): DeviceViewLayoutMode = entries.firstOrNull { it.key == key } ?: AUTO
    }
}

@IgnoreExtraProperties
data class AppDistributionConfig(
    @DocumentId val id: String = "",
    val configId: String = "global",
    val downloadUrl: String = DEFAULT_PUBLIC_APP_URL,
    val playStoreUrl: String = "",
    val appStoreUrl: String = "",
    val apkDownloadUrl: String = "",
    val viewAccessMode: String = AppViewAccessMode.PUBLIC_OPEN.key,
    val allowedDeviceTypes: List<String> = listOf("phone", "tablet", "computer"),
    val releaseNotes: String = "Configure a verified Google Play listing, Apple App Store page, Firebase App Distribution release, or hosted HTTPS download link for phones, tablets, and desktops.",
    val updatedByUid: String = "",
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    companion object {
        const val DEFAULT_PUBLIC_APP_URL = ""
    }
}

enum class InviteInspectionStatus {
    VALID_PENDING,
    ALREADY_CLAIMED,
    REVOKED,
    NOT_FOUND,
    INVALID_FORMAT,
    EMAIL_MISMATCH_OR_RESTRICTED
}

data class InviteInspectionResult(
    val code: String,
    val status: InviteInspectionStatus,
    val message: String,
    val invite: SchoolInvite? = null
)

@IgnoreExtraProperties
data class SubscriptionPlan(
    @DocumentId val id: String = "",
    val name: String = "Starter School",
    val monthlyPriceGhs: Double = 120.0,
    val annualDiscountPercent: Int = 15,
    val pupilLimit: Int = 100,
    val trialDays: Int = 14,
    val features: List<String> = emptyList(),
    val isActive: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val email: String = "",
    val displayName: String = "",
    val phone: String? = null,
    val activeSchoolId: String = "",
    val activeRole: String = UserRole.PARENT.key,
    val locationConsentGranted: Boolean = false,
    val fcmToken: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class MembershipIndex(
    @DocumentId val id: String = "",
    val schoolId: String = "",
    val schoolName: String = "",
    val role: String = UserRole.PARENT.key,
    val status: String = "active",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class School(
    @DocumentId val id: String = "",
    val name: String = "",
    val code: String = "",
    val address: String? = null,
    val city: String = "",
    val country: String = "Ghana",
    val phone: String? = null,
    val email: String? = null,
    val motto: String = "",
    val academicYear: String = "2026/2027",
    val currentTerm: String = "Term 1",
    val operatingHours: String = "07:00 – 15:30",
    val ownerUserId: String = "",
    val adminUids: List<String> = emptyList(),
    val planId: String = "starter",
    val planName: String = "Starter School",
    val monthlyPriceGhs: Double = 120.0,
    val annualDiscountPercent: Int = 15,
    val pupilLimit: Int = 100,
    val billingCycle: String = "monthly",
    val subscriptionStatus: String = "trial",
    val paymentProvider: String = "pending",
    val paymentReference: String? = null,
    val logoUri: String? = null,
    val geofenceLatitude: Double? = null,
    val geofenceLongitude: Double? = null,
    val geofenceRadiusMeters: Double = 150.0,
    val geofenceEnabled: Boolean = false,
    val studentCount: Int = 0,
    val trialDays: Int = 14,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class SchoolMember(
    @DocumentId val id: String = "",
    val schoolId: String = "",
    val userId: String = "",
    val email: String = "",
    val displayName: String = "",
    val phone: String? = null,
    val role: String = UserRole.TEACHER.key,
    val assignedClassIds: List<String> = emptyList(),
    val assignedSubjectIds: List<String> = emptyList(),
    val assignedSubjectNames: List<String> = emptyList(),
    val linkedStudentIds: List<String> = emptyList(),
    val adminUids: List<String> = emptyList(),
    val inviteCode: String? = null,
    val status: String = "active",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class SchoolInvite(
    @DocumentId val id: String = "",
    val inviteCode: String = "",
    val schoolId: String = "",
    val schoolName: String = "",
    val targetEmail: String = "",
    val role: String = UserRole.TEACHER.key,
    val assignedClassIds: List<String> = emptyList(),
    val linkedStudentIds: List<String> = emptyList(),
    val adminUids: List<String> = emptyList(),
    val status: String = "pending",
    val claimedByUid: String? = null,
    val createdByUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class SchoolClass(
    @DocumentId val id: String = "",
    val schoolId: String = "",
    val name: String = "",
    val gradeLevel: String = "",
    val academicYear: String = "2026/2027",
    val teacherUids: List<String> = emptyList(),
    val adminUids: List<String> = emptyList(),
    val studentCount: Int = 0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Student(
    @DocumentId val id: String = "",
    val schoolId: String = "",
    val admissionNumber: String = "",
    val fullName: String = "",
    val classId: String = "",
    val className: String = "",
    val academicYear: String = "2026/2027",
    val status: String = "active",
    val photoUri: String? = null,
    val emergencyContactName: String? = null,
    val emergencyContactPhone: String? = null,
    val medicalNotes: String? = null,
    val approvedDestinations: List<String> = emptyList(),
    val authorisedPickups: List<String> = emptyList(),
    val guardianUids: List<String> = emptyList(),
    val teacherUids: List<String> = emptyList(),
    val pickupUids: List<String> = emptyList(),
    val adminUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Journey(
    @DocumentId val id: String = "",
    val schoolId: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val classId: String = "",
    val className: String = "",
    val direction: String = "morning_to_school",
    val status: String = MovementStatus.ON_THE_WAY_TO_SCHOOL.key,
    val destinationName: String = "",
    val destinationType: String = "School",
    val initiatedByUid: String = "",
    val initiatedByRole: String = "",
    val initiatedByName: String = "",
    val confirmedByUid: String? = null,
    val confirmedByName: String? = null,
    val gpsAvailable: Boolean = false,
    val consentGranted: Boolean = false,
    val liveLatitude: Double? = null,
    val liveLongitude: Double? = null,
    val lastKnownLatitude: Double? = null,
    val lastKnownLongitude: Double? = null,
    val isTrackingActive: Boolean = false,
    val manualNote: String? = null,
    val guardianUids: List<String> = emptyList(),
    val teacherUids: List<String> = emptyList(),
    val pickupUids: List<String> = emptyList(),
    val adminUids: List<String> = emptyList(),
    @ServerTimestamp val startedAt: Timestamp? = null,
    @ServerTimestamp val completedAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class ClassDepartureEntry(
    val student: Student,
    val isLeavingLate: Boolean = false,
    val lateReason: String = "",
    val destinationName: String = "Home"
)

data class BulkDepartureResult(
    val departedCount: Int,
    val lateExceptionCount: Int
)

@IgnoreExtraProperties
data class AttendanceRecord(
    @DocumentId val id: String = "",
    val schoolId: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val classId: String = "",
    val className: String = "",
    val dateKey: String = "",
    val term: String = "Term 1",
    val status: String = AttendanceStatus.PENDING_CONFIRMATION.key,
    val arrivalTime: String? = null,
    val recordedByUid: String = "",
    val recordedByName: String = "",
    val correctionReason: String = "",
    val isOfflineSynced: Boolean = false,
    val guardianUids: List<String> = emptyList(),
    val teacherUids: List<String> = emptyList(),
    val adminUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Incident(
    @DocumentId val id: String = "",
    val schoolId: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val journeyId: String? = null,
    val severity: String = "medium",
    val status: String = "open",
    val title: String = "",
    val description: String = "",
    val reportedByUid: String = "",
    val reportedByName: String = "",
    val reportedByRole: String = "",
    val guardianUids: List<String> = emptyList(),
    val teacherUids: List<String> = emptyList(),
    val adminUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Message(
    @DocumentId val id: String = "",
    val schoolId: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val senderUid: String = "",
    val senderName: String = "",
    val senderRole: String = "",
    val body: String = "",
    val guardianUids: List<String> = emptyList(),
    val teacherUids: List<String> = emptyList(),
    val adminUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AppNotification(
    @DocumentId val id: String = "",
    val schoolId: String = "",
    val recipientUid: String = "",
    val title: String = "",
    val body: String = "",
    val type: String = "journey",
    val studentId: String? = null,
    val journeyId: String? = null,
    val isRead: Boolean = false,
    val adminUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AuditLog(
    @DocumentId val id: String = "",
    val schoolId: String = "",
    val actorUid: String = "",
    val actorName: String = "",
    val actorRole: String = "",
    val action: String = "",
    val targetType: String = "",
    val targetId: String = "",
    val details: String = "",
    val reason: String? = null,
    val adminUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null
)

enum class SchoolLevel(val key: String, val label: String) {
    PRIMARY("primary", "Primary"),
    JHS("jhs", "JHS");

    companion object {
        fun fromKey(key: String): SchoolLevel = entries.firstOrNull { it.key == key } ?: PRIMARY
    }
}

@IgnoreExtraProperties
data class Subject(
    @DocumentId val id: String = "",
    val schoolId: String = "",
    val name: String = "",
    val code: String = "",
    val level: String = "both",
    val isCore: Boolean = false,
    val coreSlot: String = "",
    val teacherUids: List<String> = emptyList(),
    val adminUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ExamSubjectScore(
    val subjectId: String = "",
    val subjectName: String = "",
    val isCore: Boolean = false,
    val coreSlot: String = "",
    val rawClassScore: Double = 0.0,
    val rawExamScore: Double = 0.0,
    val classScore50: Double = 0.0,
    val examScore50: Double = 0.0,
    val totalScore: Double = 0.0,
    val grade: Int = 6,
    val remark: String = "Fail"
)

@IgnoreExtraProperties
data class ExamReport(
    @DocumentId val id: String = "",
    val schoolId: String = "",
    val schoolName: String = "",
    val schoolLogoUri: String? = null,
    val schoolCity: String = "",
    val schoolPhone: String = "",
    val schoolEmail: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val admissionNumber: String = "",
    val classId: String = "",
    val className: String = "",
    val academicYear: String = "2026/2027",
    val term: String = "Term 1",
    val level: String = SchoolLevel.PRIMARY.key,
    val scores: List<ExamSubjectScore> = emptyList(),
    val overallTotal: Double = 0.0,
    val averageScore: Double = 0.0,
    val positionText: String = "",
    val aggregate: Int = 0,
    val aggregateBreakdown: String = "",
    val attendanceSummary: String = "",
    val attitudeAndConduct: String = "",
    val respectfulness: String = "",
    val punctuality: String = "",
    val classroomParticipation: String = "",
    val neatnessAndResponsibility: String = "",
    val teacherRemarks: String = "",
    val headTeacherRemarks: String = "",
    val classTeacherName: String = "",
    val classTeacherSignature: String = "",
    val headTeacherName: String = "",
    val headTeacherSignature: String = "",
    val issuanceDate: String = "",
    val recordedByUid: String = "",
    val recordedByName: String = "",
    val guardianUids: List<String> = emptyList(),
    val teacherUids: List<String> = emptyList(),
    val adminUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class GradingScaleBand(
    val rangeLabel: String,
    val grade: Int,
    val interpretation: String
) {
    val gradeCode: String get() = "G$grade"
    val percentRangeLabel: String get() = "$rangeLabel%"
}

object CefelixAcademyTemplate {
    const val SCHOOL_NAME = "CEFELIX ACADEMY"
    const val SCHOOL_LOCATION = "Accra, Ghana."
    const val CONTACT_PHONE = "0503402872"
    const val CONTACT_EMAIL = "info.cefelixacademy.com"
    const val ACADEMIC_YEAR = "2026/2027"
    const val TERM = "Term 1"
    const val LEVEL_KEY = "primary"
    const val CREST_INITIALS = "CA"

    val primarySubjects: List<String> = listOf(
        "Computing",
        "Creative Arts & Design",
        "English Language",
        "Ghanaian Language",
        "Integrated Science",
        "Mathematics",
        "Religious & Moral Education"
    )
}

data class ReportIntegrityCheck(
    val isConsistent: Boolean,
    val issues: List<String>
)

object ExamReportValidator {
    private fun round1(v: Double): Double = kotlin.math.round(v * 10.0) / 10.0

    fun validate(report: ExamReport): ReportIntegrityCheck {
        val issues = mutableListOf<String>()
        report.scores.forEach { score ->
            val expectedTotal = round1(score.classScore50 + score.examScore50)
            if (kotlin.math.abs(expectedTotal - round1(score.totalScore)) > 0.15) {
                issues.add("${score.subjectName}: Class (${score.classScore50}) + Exam (${score.examScore50}) = $expectedTotal, stored ${score.totalScore}")
            }
            val (expectedGrade, expectedRemark) = ExamGrading.gradeAndRemark(score.totalScore)
            if (score.grade != expectedGrade) {
                issues.add("${score.subjectName}: Score ${score.totalScore}% maps to G$expectedGrade ($expectedRemark), stored G${score.grade}")
            }
        }
        val expectedOverall = round1(report.scores.sumOf { it.totalScore })
        if (kotlin.math.abs(expectedOverall - round1(report.overallTotal)) > 0.25) {
            issues.add("Overall total sum is $expectedOverall, stored ${report.overallTotal}")
        }
        val expectedAvg = if (report.scores.isNotEmpty()) round1(expectedOverall / report.scores.size) else 0.0
        if (report.scores.isNotEmpty() && kotlin.math.abs(expectedAvg - round1(report.averageScore)) > 0.2) {
            issues.add("Overall average for ${report.scores.size} subjects is $expectedAvg%, stored ${report.averageScore}%")
        }
        val level = SchoolLevel.fromKey(report.level)
        val (calcAgg, _) = ExamGrading.computeAggregate(report.scores, level)
        if (calcAgg > 0 && report.aggregate > 0 && calcAgg != report.aggregate) {
            issues.add("Core aggregate (${report.aggregate}) differs from sum of 4 core grades ($calcAgg); verify manual override")
        }
        return ReportIntegrityCheck(isConsistent = issues.isEmpty(), issues = issues)
    }
}

object ExamGrading {
    val scaleBands: List<GradingScaleBand> = listOf(
        GradingScaleBand("80–100", 1, "Excellent"),
        GradingScaleBand("70–79", 2, "Very Good"),
        GradingScaleBand("60–69", 3, "Good"),
        GradingScaleBand("50–59", 4, "Credit"),
        GradingScaleBand("40–49", 5, "Pass"),
        GradingScaleBand("0–39", 6, "Fail")
    )

    fun gradeAndRemark(totalScore: Double): Pair<Int, String> {
        return when {
            totalScore >= 80.0 -> 1 to "Excellent"
            totalScore >= 70.0 -> 2 to "Very Good"
            totalScore >= 60.0 -> 3 to "Good"
            totalScore >= 50.0 -> 4 to "Credit"
            totalScore >= 40.0 -> 5 to "Pass"
            else -> 6 to "Fail"
        }
    }

    fun buildScore(
        subject: Subject,
        rawClassScore: Double,
        rawExamScore: Double
    ): ExamSubjectScore {
        val clampedClass = rawClassScore.coerceIn(0.0, 100.0)
        val clampedExam = rawExamScore.coerceIn(0.0, 100.0)
        val class50 = (clampedClass * 0.5).let { kotlin.math.round(it * 10.0) / 10.0 }
        val exam50 = (clampedExam * 0.5).let { kotlin.math.round(it * 10.0) / 10.0 }
        val total = (class50 + exam50).let { kotlin.math.round(it * 10.0) / 10.0 }
        val (grade, remark) = gradeAndRemark(total)
        return ExamSubjectScore(
            subjectId = subject.id,
            subjectName = subject.name,
            isCore = subject.isCore,
            coreSlot = subject.coreSlot,
            rawClassScore = clampedClass,
            rawExamScore = clampedExam,
            classScore50 = class50,
            examScore50 = exam50,
            totalScore = total,
            grade = grade,
            remark = remark
        )
    }

    fun inferCoreSlot(subjectName: String): String {
        val lower = subjectName.trim().lowercase()
        return when {
            lower.contains("math") -> "mathematics"
            lower.contains("english") -> "english"
            lower.contains("science") -> "science"
            lower.contains("social") -> "social_studies"
            lower.contains("comput") || lower.contains("ict") -> "computing"
            else -> ""
        }
    }

    fun computeAggregate(
        scores: List<ExamSubjectScore>,
        level: SchoolLevel
    ): Pair<Int, String> {
        val fourthSlot = if (level == SchoolLevel.JHS) "social_studies" else "computing"
        val fourthLabel = if (level == SchoolLevel.JHS) "Social Studies" else "Computing"

        val requiredSlots = listOf(
            "mathematics" to "Math",
            "english" to "English",
            "science" to "Science",
            fourthSlot to fourthLabel
        )

        var sum = 0
        val parts = mutableListOf<String>()
        val missing = mutableListOf<String>()

        requiredSlots.forEach { (slot, label) ->
            val match = scores.firstOrNull {
                it.coreSlot == slot || inferCoreSlot(it.subjectName) == slot
            }
            if (match != null) {
                sum += match.grade
                parts.add("$label: G${match.grade}")
            } else {
                missing.add(label)
            }
        }

        val breakdown = buildString {
            if (parts.isNotEmpty()) append(parts.joinToString(" • "))
            if (missing.isNotEmpty()) {
                if (parts.isNotEmpty()) append(" | ")
                append("Missing: ${missing.joinToString(", ")}")
            }
        }
        return sum to breakdown
    }

    fun formatOrdinal(rank: Int): String {
        val mod100 = rank % 100
        val suffix = if (mod100 in 11..13) "th" else when (rank % 10) {
            1 -> "st"
            2 -> "nd"
            3 -> "rd"
            else -> "th"
        }
        return "$rank$suffix"
    }
}

@IgnoreExtraProperties
data class InventoryItem(
    @DocumentId val id: String = "",
    val schoolId: String = "",
    val name: String = "",
    val category: String = "Classroom Supplies",
    val quantity: Int = 0,
    val minimumStock: Int = 5,
    val condition: String = "good",
    val assignedTo: String = "",
    val notes: String = "",
    val teacherUids: List<String> = emptyList(),
    val adminUids: List<String> = emptyList(),
    val updatedByUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FeeRecord(
    @DocumentId val id: String = "",
    val schoolId: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val className: String = "",
    val title: String = "Term Tuition & Levies",
    val term: String = "Term 1",
    val academicYear: String = "2026/2027",
    val amountDueGhs: Double = 0.0,
    val amountPaidGhs: Double = 0.0,
    val status: String = "unpaid",
    val paymentMethod: String = "mobile_money",
    val receiptNumber: String = "",
    val dueDate: String = "",
    val guardianUids: List<String> = emptyList(),
    val adminUids: List<String> = emptyList(),
    val recordedByUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val balanceGhs: Double get() = (amountDueGhs - amountPaidGhs).coerceAtLeast(0.0)
}

@IgnoreExtraProperties
data class UserDeviceSession(
    @DocumentId val id: String = "",
    val userId: String = "",
    val deviceName: String = "",
    val platform: String = "Android",
    val appVersion: String = "1.0.0",
    val lastActiveLabel: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)


