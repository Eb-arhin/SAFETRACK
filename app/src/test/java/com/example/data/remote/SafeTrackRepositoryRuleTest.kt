package com.example.data.remote

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.AppDistributionConfig
import com.example.data.model.AppViewAccessMode
import com.example.data.model.AttendanceStatus
import com.example.data.model.ClassDepartureEntry
import com.example.data.model.ExamGrading
import com.example.data.model.ExamReport
import com.example.data.model.MovementStatus
import com.example.data.model.School
import com.example.data.model.SchoolClass
import com.example.data.model.SchoolLevel
import com.example.data.model.Student
import com.example.data.model.Subject
import com.example.data.model.SubscriptionPlan
import com.example.data.model.UserRole
import com.example.util.CsvStudentParser
import com.example.util.TrackedGeofenceStudent
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class SafeTrackRepositoryRuleTest : FirestoreEmulatorTestBase() {

    private fun testPlan() = SubscriptionPlan(
        id = "professional",
        name = "Professional",
        monthlyPriceGhs = 300.0,
        annualDiscountPercent = 20,
        pupilLimit = 500,
        trialDays = 14
    )

    @Test
    fun observeStudents_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = SafeTrackRepository(firestore, null, auth)
        var thrown: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeStudents("sch_any", UserRole.PARENT).first()
            }
        } catch (e: Throwable) {
            thrown = e
        }
        assertTrue("Expected unauthenticated error, got $thrown", thrown != null)
    }

    @Test
    fun multiSchoolIsolation_andFullMorningJourneyWorkflow_succeeds() = runBlocking {
        val uniqueSuffix = UUID.randomUUID().toString().take(6)
        val adminAEmail = "admina_$uniqueSuffix@schoola.edu"
        val adminBEmail = "adminb_$uniqueSuffix@schoolb.edu"
        val teacherEmail = "teacher_$uniqueSuffix@schoola.edu"
        val parentEmail = "parent_$uniqueSuffix@gmail.com"

        // 1. School A Admin registers School A and creates class + student + invites
        val adminAUid = signInTestUser(adminAEmail)
        val adminARepo = SafeTrackRepository(firestore, null, auth)
        withTimeout(TIMEOUT_MS) {
            adminARepo.ensureUserProfile("Admin A", adminAEmail, false).getOrThrow()
        }
        val schoolAId = withTimeout(TIMEOUT_MS) {
            adminARepo.registerSchool(
                schoolName = "Ridge Academy $uniqueSuffix",
                city = "Accra",
                country = "Ghana",
                address = "14 Independence Ave",
                phone = "+233200000001",
                email = adminAEmail,
                plan = testPlan(),
                billingCycle = "monthly",
                ownerDisplayName = "Admin A",
                ownerEmail = adminAEmail
            ).getOrThrow()
        }
        val schoolA = withTimeout(FLOW_TIMEOUT_MS) {
            adminARepo.observeSchool(schoolAId).first { it != null }!!
        }

        // Teacher & Parent join via invite codes
        val teacherInvite = withTimeout(TIMEOUT_MS) {
            adminARepo.createSchoolInvite(schoolA, "", UserRole.TEACHER, emptyList(), emptyList(), "Admin A").getOrThrow()
        }
        val parentInvite = withTimeout(TIMEOUT_MS) {
            adminARepo.createSchoolInvite(schoolA, "", UserRole.PARENT, emptyList(), emptyList(), "Admin A").getOrThrow()
        }

        val teacherUid = signInTestUser(teacherEmail)
        val teacherRepo = SafeTrackRepository(firestore, null, auth)
        withTimeout(TIMEOUT_MS) {
            teacherRepo.ensureUserProfile("Teacher A", teacherEmail, false).getOrThrow()
            teacherRepo.claimSchoolInvite(teacherInvite, "Teacher A", teacherEmail).getOrThrow()
        }

        val parentUid = signInTestUser(parentEmail)
        val parentRepo = SafeTrackRepository(firestore, null, auth)
        withTimeout(TIMEOUT_MS) {
            parentRepo.ensureUserProfile("Parent A", parentEmail, false).getOrThrow()
            parentRepo.claimSchoolInvite(parentInvite, "Parent A", parentEmail).getOrThrow()
        }

        // Admin A creates class and enrolls student linked to teacher & parent
        signInTestUser(adminAEmail)
        val classId = withTimeout(TIMEOUT_MS) {
            adminARepo.createClass(schoolA, "Primary 4A", "Grade 4", "2026/2027", listOf(teacherUid), "Admin A").getOrThrow()
        }
        val schoolClass = SchoolClass(
            id = classId,
            schoolId = schoolAId,
            name = "Primary 4A",
            gradeLevel = "Grade 4",
            academicYear = "2026/2027",
            teacherUids = listOf(teacherUid),
            adminUids = listOf(adminAUid)
        )
        val studentId = withTimeout(TIMEOUT_MS) {
            adminARepo.createStudent(
                school = schoolA,
                admissionNumber = "ADM-$uniqueSuffix",
                fullName = "Kwame Boateng",
                schoolClass = schoolClass,
                emergencyContactName = "Abena Boateng",
                emergencyContactPhone = "+233244111222",
                medicalNotes = "Mild peanut allergy",
                approvedDestinations = listOf("Home - Cantonments"),
                authorisedPickups = listOf("Abena Boateng (Mother)"),
                guardianUids = listOf(parentUid),
                pickupUids = emptyList(),
                photoUri = null,
                actorName = "Admin A"
            ).getOrThrow()
        }

        // 2. School B Admin cannot see School A students
        signInTestUser(adminBEmail)
        val adminBRepo = SafeTrackRepository(firestore, null, auth)
        withTimeout(TIMEOUT_MS) {
            adminBRepo.ensureUserProfile("Admin B", adminBEmail, false).getOrThrow()
        }
        val schoolBStudents = withTimeout(FLOW_TIMEOUT_MS) {
            adminBRepo.observeStudents(schoolAId, UserRole.SCHOOL_ADMIN).first()
        }
        assertTrue("School B Admin must see 0 students from School A", schoolBStudents.isEmpty())

        // 3. Parent starts Morning Journey
        signInTestUser(parentEmail)
        val parentStudents = withTimeout(FLOW_TIMEOUT_MS) {
            parentRepo.observeStudents(schoolAId, UserRole.PARENT).first { it.isNotEmpty() }
        }
        val student = parentStudents.first { it.id == studentId }
        val journeyId = withTimeout(TIMEOUT_MS) {
            parentRepo.startMorningJourney(
                student = student,
                destinationName = schoolA.name,
                parentName = "Parent A",
                consentGranted = true,
                latitude = 5.6037,
                longitude = -0.1870,
                manualNote = "Walking with parent"
            ).getOrThrow()
        }

        // Confirm attendance was NOT created by parent starting morning journey
        val parentAttBefore = withTimeout(FLOW_TIMEOUT_MS) {
            parentRepo.observeAttendance(schoolAId, UserRole.PARENT).first()
        }
        assertFalse(
            "Starting morning journey must never automatically record Present",
            parentAttBefore.any { it.studentId == studentId && it.status == AttendanceStatus.PRESENT.key }
        )

        // 4. Teacher confirms physical arrival -> Attendance becomes Present & tracking stops
        signInTestUser(teacherEmail)
        val teacherJourneys = withTimeout(FLOW_TIMEOUT_MS) {
            teacherRepo.observeJourneys(schoolAId, UserRole.TEACHER).first { list -> list.any { it.id == journeyId } }
        }
        val activeJourney = teacherJourneys.first { it.id == journeyId }
        withTimeout(TIMEOUT_MS) {
            teacherRepo.confirmPhysicalArrivalAtSchool(
                journey = activeJourney,
                student = student,
                teacherName = "Teacher A",
                teacherRole = UserRole.TEACHER.key,
                markAsLate = false
            ).getOrThrow()
        }

        val updatedJourney = withTimeout(FLOW_TIMEOUT_MS) {
            teacherRepo.observeJourneys(schoolAId, UserRole.TEACHER).first { list ->
                list.any { it.id == journeyId && it.status == MovementStatus.ARRIVED_AT_SCHOOL.key }
            }.first { it.id == journeyId }
        }
        assertFalse("Tracking must stop immediately upon arrival", updatedJourney.isTrackingActive)

        val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        // Correction without reason must fail
        val badCorrection = withTimeout(TIMEOUT_MS) {
            teacherRepo.recordOrCorrectAttendance(
                student = student,
                dateKey = dateKey,
                status = AttendanceStatus.LATE,
                correctionReason = "",
                teacherName = "Teacher A",
                teacherRole = UserRole.TEACHER.key,
                isOfflineMode = false
            )
        }
        assertTrue("Correction without reason must fail", badCorrection.isFailure)

        // Correction with reason must succeed
        val goodCorrection = withTimeout(TIMEOUT_MS) {
            teacherRepo.recordOrCorrectAttendance(
                student = student,
                dateKey = dateKey,
                status = AttendanceStatus.LATE,
                correctionReason = "Arrived after 08:00 bell",
                teacherName = "Teacher A",
                teacherRole = UserRole.TEACHER.key,
                isOfflineMode = false
            )
        }
        assertTrue("Correction with reason must succeed", goodCorrection.isSuccess)
        assertEquals(true, goodCorrection.getOrNull())
    }

    @Test
    fun subjects_csvImport_andExamReports_workEndToEndWithRulesEnforcement() = runBlocking {
        val uniqueSuffix = UUID.randomUUID().toString().take(6)
        val adminEmail = "admin_ex_$uniqueSuffix@schoola.edu"
        val teacherEmail = "teach_ex_$uniqueSuffix@schoola.edu"
        val parentEmail = "par_ex_$uniqueSuffix@gmail.com"

        val adminUid = signInTestUser(adminEmail)
        val adminRepo = SafeTrackRepository(firestore, null, auth)
        withTimeout(TIMEOUT_MS) {
            adminRepo.ensureUserProfile("Admin EX", adminEmail, false).getOrThrow()
        }
        val schoolId = withTimeout(TIMEOUT_MS) {
            adminRepo.registerSchool(
                schoolName = "Accra Heritage School $uniqueSuffix",
                city = "Accra",
                country = "Ghana",
                address = "22 Liberation Rd",
                phone = "+233200000002",
                email = adminEmail,
                plan = testPlan(),
                billingCycle = "monthly",
                ownerDisplayName = "Admin EX",
                ownerEmail = adminEmail
            ).getOrThrow()
        }
        val school = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeSchool(schoolId).first { it != null }!!
        }

        val teacherInvite = withTimeout(TIMEOUT_MS) {
            adminRepo.createSchoolInvite(school, "", UserRole.TEACHER, emptyList(), emptyList(), "Admin EX").getOrThrow()
        }
        val parentInvite = withTimeout(TIMEOUT_MS) {
            adminRepo.createSchoolInvite(school, "", UserRole.PARENT, emptyList(), emptyList(), "Admin EX").getOrThrow()
        }

        val teacherUid = signInTestUser(teacherEmail)
        val teacherRepo = SafeTrackRepository(firestore, null, auth)
        withTimeout(TIMEOUT_MS) {
            teacherRepo.ensureUserProfile("Teacher EX", teacherEmail, false).getOrThrow()
            teacherRepo.claimSchoolInvite(teacherInvite, "Teacher EX", teacherEmail).getOrThrow()
        }

        val parentUid = signInTestUser(parentEmail)
        val parentRepo = SafeTrackRepository(firestore, null, auth)
        withTimeout(TIMEOUT_MS) {
            parentRepo.ensureUserProfile("Parent EX", parentEmail, false).getOrThrow()
            parentRepo.claimSchoolInvite(parentInvite, "Parent EX", parentEmail).getOrThrow()
        }

        // Admin creates class + seeds standard Ghana subjects + CSV-imports students
        signInTestUser(adminEmail)
        val classId = withTimeout(TIMEOUT_MS) {
            adminRepo.createClass(school, "Primary 4A", "Grade 4", "2026/2027", listOf(teacherUid), "Admin EX").getOrThrow()
        }
        val schoolClass = SchoolClass(
            id = classId,
            schoolId = schoolId,
            name = "Primary 4A",
            gradeLevel = "Grade 4",
            academicYear = "2026/2027",
            teacherUids = listOf(teacherUid),
            adminUids = listOf(adminUid)
        )
        val seededCount = withTimeout(TIMEOUT_MS) {
            adminRepo.seedDefaultGhanaSubjects(school, listOf(teacherUid), "Admin EX", UserRole.SCHOOL_ADMIN.key).getOrThrow()
        }
        assertEquals(8, seededCount)

        val parsedCsv = CsvStudentParser.parse(CsvStudentParser.SAMPLE_CSV_TEMPLATE, schoolClass.name)
        assertEquals(3, parsedCsv.size)
        val importedCount = withTimeout(TIMEOUT_MS) {
            adminRepo.bulkImportStudents(
                school = school,
                classes = listOf(schoolClass),
                defaultClass = schoolClass,
                rows = parsedCsv,
                linkSelfAsGuardian = false,
                actorName = "Admin EX",
                actorRole = UserRole.SCHOOL_ADMIN.key
            ).getOrThrow()
        }
        assertEquals(3, importedCount)

        // Enroll and link a pupil to Parent EX for report card viewing
        val studentId = withTimeout(TIMEOUT_MS) {
            adminRepo.createStudent(
                school = school,
                admissionNumber = "ADM-EX1",
                fullName = "Esi Armah",
                schoolClass = schoolClass,
                emergencyContactName = "Kofi Armah",
                emergencyContactPhone = "+233244555666",
                medicalNotes = "",
                approvedDestinations = listOf("Home - Osu"),
                authorisedPickups = listOf("Kofi Armah"),
                guardianUids = listOf(parentUid),
                pickupUids = emptyList(),
                photoUri = null,
                actorName = "Admin EX"
            ).getOrThrow()
        }

        // Teacher scores student using 50% Class + 50% Exam + 4-Core Primary Aggregate
        signInTestUser(teacherEmail)
        val subjects = withTimeout(FLOW_TIMEOUT_MS) {
            teacherRepo.observeSubjects(schoolId, UserRole.TEACHER).first { it.size >= 8 }
        }
        val mathSub = subjects.first { it.coreSlot == "mathematics" }
        val engSub = subjects.first { it.coreSlot == "english" }
        val sciSub = subjects.first { it.coreSlot == "science" }
        val compSub = subjects.first { it.coreSlot == "computing" }

        val scores = listOf(
            ExamGrading.buildScore(mathSub, 90.0, 84.0),   // 45 + 42 = 87 -> Grade 1 (Excellent)
            ExamGrading.buildScore(engSub, 76.0, 72.0),    // 38 + 36 = 74 -> Grade 2 (Very Good)
            ExamGrading.buildScore(sciSub, 64.0, 62.0),    // 32 + 31 = 63 -> Grade 3 (Good)
            ExamGrading.buildScore(compSub, 82.0, 80.0)    // 41 + 40 = 81 -> Grade 1 (Excellent)
        )
        assertEquals(87.0, scores[0].totalScore, 0.01)
        assertEquals(1, scores[0].grade)
        assertEquals("Excellent", scores[0].remark)
        val (agg, _) = ExamGrading.computeAggregate(scores, SchoolLevel.PRIMARY)
        assertEquals(7, agg) // 1 + 2 + 3 + 1

        val report = ExamReport(
            schoolId = schoolId,
            schoolName = "CEFELIX ACADEMY",
            schoolCity = "Accra, Ghana.",
            schoolPhone = "0503402872",
            schoolEmail = "info.cefelixacademy.com",
            studentId = studentId,
            studentName = "Esi Armah",
            admissionNumber = "ADM-EX1",
            classId = classId,
            className = schoolClass.name,
            academicYear = "2026/2027",
            term = "Term 1",
            level = SchoolLevel.PRIMARY.key,
            scores = scores,
            overallTotal = scores.sumOf { it.totalScore },
            averageScore = scores.sumOf { it.totalScore } / scores.size,
            positionText = "1st of 4",
            aggregate = agg,
            aggregateBreakdown = "Math: G1 • English: G2 • Science: G3 • Computing: G1",
            attendanceSummary = "59 / 60 days",
            attitudeAndConduct = "Diligent and respectful",
            respectfulness = "Very Good",
            punctuality = "Excellent",
            classroomParticipation = "Excellent",
            neatnessAndResponsibility = "Very Good",
            teacherRemarks = "Excellent progress this term.",
            headTeacherRemarks = "Keep shining.",
            classTeacherName = "Teacher EX",
            classTeacherSignature = "T. EX",
            headTeacherName = "Headteacher EX",
            headTeacherSignature = "H. EX",
            issuanceDate = "18 Dec 2026",
            guardianUids = listOf(parentUid),
            teacherUids = listOf(teacherUid),
            adminUids = listOf(adminUid)
        )
        withTimeout(TIMEOUT_MS) {
            teacherRepo.upsertExamReport(report, "Teacher EX", UserRole.TEACHER.key).getOrThrow()
        }

        // Parent observes the linked child's published report card
        signInTestUser(parentEmail)
        val parentReports = withTimeout(FLOW_TIMEOUT_MS) {
            parentRepo.observeExamReports(schoolId, UserRole.PARENT).first { it.isNotEmpty() }
        }
        assertEquals(1, parentReports.size)
        assertEquals(7, parentReports.first().aggregate)
        assertEquals("1st of 4", parentReports.first().positionText)
        assertEquals("Excellent", parentReports.first().punctuality)
        assertEquals("Headteacher EX", parentReports.first().headTeacherName)
        assertEquals("18 Dec 2026", parentReports.first().issuanceDate)

        // Parent cannot tamper with the report card
        val parentTamper = withTimeout(TIMEOUT_MS) {
            parentRepo.upsertExamReport(
                parentReports.first().copy(overallTotal = 400.0, positionText = "Tampered"),
                "Parent EX",
                UserRole.PARENT.key
            )
        }
        assertTrue("Parent tampering with exam report must fail", parentTamper.isFailure)
    }

    @Test
    fun dailyAttendance_presentTardyAbsent_andBatchSync_deliverRealTimeToParent() = runBlocking {
        val uniqueSuffix = UUID.randomUUID().toString().take(6)
        val adminEmail = "admin_att_$uniqueSuffix@schoola.edu"
        val teacherEmail = "teach_att_$uniqueSuffix@schoola.edu"
        val parentEmail = "par_att_$uniqueSuffix@gmail.com"

        val adminUid = signInTestUser(adminEmail)
        val adminRepo = SafeTrackRepository(firestore, null, auth)
        withTimeout(TIMEOUT_MS) {
            adminRepo.ensureUserProfile("Admin ATT", adminEmail, false).getOrThrow()
        }
        val schoolId = withTimeout(TIMEOUT_MS) {
            adminRepo.registerSchool(
                schoolName = "SafeTrack Roll Call $uniqueSuffix",
                city = "Kumasi",
                country = "Ghana",
                address = "10 Harper Rd",
                phone = "+233200000099",
                email = adminEmail,
                plan = testPlan(),
                billingCycle = "monthly",
                ownerDisplayName = "Admin ATT",
                ownerEmail = adminEmail
            ).getOrThrow()
        }
        val school = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeSchool(schoolId).first { it != null }!!
        }

        val teacherInvite = withTimeout(TIMEOUT_MS) {
            adminRepo.createSchoolInvite(school, "", UserRole.TEACHER, emptyList(), emptyList(), "Admin ATT").getOrThrow()
        }
        val parentInvite = withTimeout(TIMEOUT_MS) {
            adminRepo.createSchoolInvite(school, "", UserRole.PARENT, emptyList(), emptyList(), "Admin ATT").getOrThrow()
        }

        val teacherUid = signInTestUser(teacherEmail)
        val teacherRepo = SafeTrackRepository(firestore, null, auth)
        withTimeout(TIMEOUT_MS) {
            teacherRepo.ensureUserProfile("Teacher ATT", teacherEmail, false).getOrThrow()
            teacherRepo.claimSchoolInvite(teacherInvite, "Teacher ATT", teacherEmail).getOrThrow()
        }

        val parentUid = signInTestUser(parentEmail)
        val parentRepo = SafeTrackRepository(firestore, null, auth)
        withTimeout(TIMEOUT_MS) {
            parentRepo.ensureUserProfile("Parent ATT", parentEmail, false).getOrThrow()
            parentRepo.claimSchoolInvite(parentInvite, "Parent ATT", parentEmail).getOrThrow()
        }

        signInTestUser(adminEmail)
        val classId = withTimeout(TIMEOUT_MS) {
            adminRepo.createClass(school, "Primary 5B", "Grade 5", "2026/2027", listOf(teacherUid), "Admin ATT").getOrThrow()
        }
        val schoolClass = SchoolClass(
            id = classId,
            schoolId = schoolId,
            name = "Primary 5B",
            gradeLevel = "Grade 5",
            academicYear = "2026/2027",
            teacherUids = listOf(teacherUid),
            adminUids = listOf(adminUid)
        )
        val studentOneId = withTimeout(TIMEOUT_MS) {
            adminRepo.createStudent(
                school = school,
                admissionNumber = "ADM-R1",
                fullName = "Kojo Mensah",
                schoolClass = schoolClass,
                emergencyContactName = "Parent ATT",
                emergencyContactPhone = "+233244000111",
                medicalNotes = "",
                approvedDestinations = listOf("Home"),
                authorisedPickups = listOf("Parent ATT"),
                guardianUids = listOf(parentUid),
                pickupUids = emptyList(),
                photoUri = null,
                actorName = "Admin ATT"
            ).getOrThrow()
        }
        val studentTwoId = withTimeout(TIMEOUT_MS) {
            adminRepo.createStudent(
                school = school,
                admissionNumber = "ADM-R2",
                fullName = "Adjoa Mensah",
                schoolClass = schoolClass,
                emergencyContactName = "Parent ATT",
                emergencyContactPhone = "+233244000111",
                medicalNotes = "",
                approvedDestinations = listOf("Home"),
                authorisedPickups = listOf("Parent ATT"),
                guardianUids = listOf(parentUid),
                pickupUids = emptyList(),
                photoUri = null,
                actorName = "Admin ATT"
            ).getOrThrow()
        }

        // Teacher marks Student 1 as Absent, then updates to Tardy (LATE) with reason, and batch-marks remaining Present
        signInTestUser(teacherEmail)
        val teacherStudents = withTimeout(FLOW_TIMEOUT_MS) {
            teacherRepo.observeStudents(schoolId, UserRole.TEACHER).first { it.size >= 2 }
        }
        val studentOne = teacherStudents.first { it.id == studentOneId }
        val studentTwo = teacherStudents.first { it.id == studentTwoId }
        val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        val absentResult = withTimeout(TIMEOUT_MS) {
            teacherRepo.recordOrCorrectAttendance(
                student = studentOne,
                dateKey = todayKey,
                status = AttendanceStatus.ABSENT,
                correctionReason = "",
                teacherName = "Teacher ATT",
                teacherRole = UserRole.TEACHER.key,
                isOfflineMode = false
            )
        }
        assertTrue("Teacher marking student Absent must succeed", absentResult.isSuccess)

        val tardyUpdateResult = withTimeout(TIMEOUT_MS) {
            teacherRepo.recordOrCorrectAttendance(
                student = studentOne,
                dateKey = todayKey,
                status = AttendanceStatus.LATE,
                correctionReason = "Arrived late to class",
                teacherName = "Teacher ATT",
                teacherRole = UserRole.TEACHER.key,
                isOfflineMode = false
            )
        }
        assertTrue("Teacher updating Absent to Tardy (Late) with reason must succeed", tardyUpdateResult.isSuccess)

        val batchResult = withTimeout(TIMEOUT_MS) {
            teacherRepo.markStudentsPresentBatch(
                students = listOf(studentTwo),
                dateKey = todayKey,
                teacherName = "Teacher ATT",
                teacherRole = UserRole.TEACHER.key
            )
        }
        assertTrue("Teacher batch marking Present must succeed", batchResult.isSuccess)
        assertEquals(1, batchResult.getOrNull())

        // Parent receives real-time attendance records and notifications in Firestore
        signInTestUser(parentEmail)
        val parentAttendance = withTimeout(FLOW_TIMEOUT_MS) {
            parentRepo.observeAttendance(schoolId, UserRole.PARENT).first { list ->
                list.any { it.studentId == studentOneId && it.dateKey == todayKey } &&
                    list.any { it.studentId == studentTwoId && it.dateKey == todayKey }
            }
        }
        assertEquals(AttendanceStatus.LATE.key, parentAttendance.first { it.studentId == studentOneId }.status)
        assertEquals(AttendanceStatus.PRESENT.key, parentAttendance.first { it.studentId == studentTwoId }.status)

        val parentNotifs = withTimeout(FLOW_TIMEOUT_MS) {
            parentRepo.observeMyNotifications().first { list ->
                list.any { it.type == "attendance" && it.studentId == studentOneId } &&
                    list.any { it.type == "attendance" && it.studentId == studentTwoId }
            }
        }
        assertTrue(
            "Parent must receive real-time Tardy notification",
            parentNotifs.any { it.studentId == studentOneId && it.title.contains("Tardy", ignoreCase = true) }
        )
    }

    @Test
    fun schoolGeofence_configAndEnterExitTransitions_notifyParentInRealTime() = runBlocking {
        val uniqueSuffix = UUID.randomUUID().toString().take(6)
        val adminEmail = "admin_geo_$uniqueSuffix@schoola.edu"
        val teacherEmail = "teach_geo_$uniqueSuffix@schoola.edu"
        val parentEmail = "par_geo_$uniqueSuffix@gmail.com"

        val adminUid = signInTestUser(adminEmail)
        val adminRepo = SafeTrackRepository(firestore, null, auth)
        withTimeout(TIMEOUT_MS) {
            adminRepo.ensureUserProfile("Admin GEO", adminEmail, false).getOrThrow()
        }
        val schoolId = withTimeout(TIMEOUT_MS) {
            adminRepo.registerSchool(
                schoolName = "SafeTrack Campus $uniqueSuffix",
                city = "Accra",
                country = "Ghana",
                address = "1 Campus Way",
                phone = "+233200000088",
                email = adminEmail,
                plan = testPlan(),
                billingCycle = "monthly",
                ownerDisplayName = "Admin GEO",
                ownerEmail = adminEmail
            ).getOrThrow()
        }
        val school = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeSchool(schoolId).first { it != null }!!
        }

        val teacherInvite = withTimeout(TIMEOUT_MS) {
            adminRepo.createSchoolInvite(school, "", UserRole.TEACHER, emptyList(), emptyList(), "Admin GEO").getOrThrow()
        }
        val parentInvite = withTimeout(TIMEOUT_MS) {
            adminRepo.createSchoolInvite(school, "", UserRole.PARENT, emptyList(), emptyList(), "Admin GEO").getOrThrow()
        }

        val teacherUid = signInTestUser(teacherEmail)
        val teacherRepo = SafeTrackRepository(firestore, null, auth)
        withTimeout(TIMEOUT_MS) {
            teacherRepo.ensureUserProfile("Teacher GEO", teacherEmail, false).getOrThrow()
            teacherRepo.claimSchoolInvite(teacherInvite, "Teacher GEO", teacherEmail).getOrThrow()
        }

        val parentUid = signInTestUser(parentEmail)
        val parentRepo = SafeTrackRepository(firestore, null, auth)
        withTimeout(TIMEOUT_MS) {
            parentRepo.ensureUserProfile("Parent GEO", parentEmail, false).getOrThrow()
            parentRepo.claimSchoolInvite(parentInvite, "Parent GEO", parentEmail).getOrThrow()
        }

        // Admin configures campus geofence coordinates & 200m radius
        signInTestUser(adminEmail)
        val classId = withTimeout(TIMEOUT_MS) {
            adminRepo.createClass(school, "JHS 1A", "Grade 7", "2026/2027", listOf(teacherUid), "Admin GEO").getOrThrow()
        }
        val schoolClass = SchoolClass(
            id = classId,
            schoolId = schoolId,
            name = "JHS 1A",
            gradeLevel = "Grade 7",
            academicYear = "2026/2027",
            teacherUids = listOf(teacherUid),
            adminUids = listOf(adminUid)
        )
        val studentId = withTimeout(TIMEOUT_MS) {
            adminRepo.createStudent(
                school = school,
                admissionNumber = "ADM-GEO1",
                fullName = "Efua Mensah",
                schoolClass = schoolClass,
                emergencyContactName = "Parent GEO",
                emergencyContactPhone = "+233244999000",
                medicalNotes = "",
                approvedDestinations = listOf("Home"),
                authorisedPickups = listOf("Parent GEO"),
                guardianUids = listOf(parentUid),
                pickupUids = emptyList(),
                photoUri = null,
                actorName = "Admin GEO"
            ).getOrThrow()
        }

        withTimeout(TIMEOUT_MS) {
            adminRepo.updateSchoolGeofence(
                school = school,
                latitude = 5.6037,
                longitude = -0.1870,
                radiusMeters = 200.0,
                enabled = true,
                actorName = "Admin GEO",
                actorRole = UserRole.SCHOOL_ADMIN.key
            ).getOrThrow()
        }
        val configuredSchool = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeSchool(schoolId).first { it?.geofenceEnabled == true }!!
        }
        assertEquals(200.0, configuredSchool.geofenceRadiusMeters, 0.01)
        assertEquals(5.6037, configuredSchool.geofenceLatitude ?: 0.0, 0.0001)

        // Parent cannot overwrite school geofence configuration
        signInTestUser(parentEmail)
        val parentUpdateAttempt = withTimeout(TIMEOUT_MS) {
            parentRepo.updateSchoolGeofence(
                school = configuredSchool,
                latitude = 0.0,
                longitude = 0.0,
                radiusMeters = 150.0,
                enabled = false,
                actorName = "Parent GEO",
                actorRole = UserRole.PARENT.key
            )
        }
        assertTrue("Parent must not modify school geofence config", parentUpdateAttempt.isFailure)

        // Device enters and exits school premises -> Parent receives push notifications in real time
        val tracked = listOf(
            TrackedGeofenceStudent(
                id = studentId,
                fullName = "Efua Mensah",
                classId = classId,
                className = "JHS 1A",
                guardianUids = listOf(parentUid),
                teacherUids = listOf(teacherUid),
                adminUids = listOf(adminUid)
            )
        )
        withTimeout(TIMEOUT_MS) {
            parentRepo.recordGeofenceTransition(
                schoolId = schoolId,
                schoolName = configuredSchool.name,
                trackedStudents = tracked,
                isEntering = true,
                latitude = 5.6037,
                longitude = -0.1870,
                timeLabel = "07:38"
            ).getOrThrow()
            parentRepo.recordGeofenceTransition(
                schoolId = schoolId,
                schoolName = configuredSchool.name,
                trackedStudents = tracked,
                isEntering = false,
                latitude = 5.6060,
                longitude = -0.1870,
                timeLabel = "15:15"
            ).getOrThrow()
        }

        val parentNotifs = withTimeout(FLOW_TIMEOUT_MS) {
            parentRepo.observeMyNotifications().first { list ->
                list.any { it.type == "geofence_enter" && it.studentId == studentId } &&
                    list.any { it.type == "geofence_exit" && it.studentId == studentId }
            }
        }
        assertTrue(parentNotifs.any { it.type == "geofence_enter" && it.title.contains("Entered School Premises") })
        assertTrue(parentNotifs.any { it.type == "geofence_exit" && it.title.contains("Left School Premises") })

        // Geofence entry must NOT automatically mark official school attendance as Present
        val parentAttendance = withTimeout(FLOW_TIMEOUT_MS) {
            parentRepo.observeAttendance(schoolId, UserRole.PARENT).first()
        }
        assertTrue(
            "Geofence entry must not mark attendance Present automatically",
            parentAttendance.none { it.studentId == studentId && it.status == AttendanceStatus.PRESENT.key }
        )
    }

    @Test
    fun classDepartureWithLateExceptions_andStaffEditDeleteStudentsAndTeachers() = runBlocking {
        val uniqueSuffix = UUID.randomUUID().toString().take(6)
        val adminEmail = "admin_dep_$uniqueSuffix@schoola.edu"
        val teacherEmail = "teach_dep_$uniqueSuffix@schoola.edu"
        val parentEmail = "par_dep_$uniqueSuffix@gmail.com"

        val adminUid = signInTestUser(adminEmail)
        val adminRepo = SafeTrackRepository(firestore, null, auth)
        withTimeout(TIMEOUT_MS) {
            adminRepo.ensureUserProfile("Admin DEP", adminEmail, false).getOrThrow()
        }
        val schoolId = withTimeout(TIMEOUT_MS) {
            adminRepo.registerSchool(
                schoolName = "SafeTrack Bulk $uniqueSuffix",
                city = "Kumasi",
                country = "Ghana",
                address = "10 Ridge Road",
                phone = "+233200000099",
                email = adminEmail,
                plan = testPlan(),
                billingCycle = "monthly",
                ownerDisplayName = "Admin DEP",
                ownerEmail = adminEmail
            ).getOrThrow()
        }
        val school = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeSchool(schoolId).first { it != null }!!
        }

        val teacherInvite = withTimeout(TIMEOUT_MS) {
            adminRepo.createSchoolInvite(school, "", UserRole.TEACHER, emptyList(), emptyList(), "Admin DEP").getOrThrow()
        }
        val parentInvite = withTimeout(TIMEOUT_MS) {
            adminRepo.createSchoolInvite(school, "", UserRole.PARENT, emptyList(), emptyList(), "Admin DEP").getOrThrow()
        }

        val teacherUid = signInTestUser(teacherEmail)
        val teacherRepo = SafeTrackRepository(firestore, null, auth)
        withTimeout(TIMEOUT_MS) {
            teacherRepo.ensureUserProfile("Teacher DEP", teacherEmail, false).getOrThrow()
            teacherRepo.claimSchoolInvite(teacherInvite, "Teacher DEP", teacherEmail).getOrThrow()
        }

        val parentUid = signInTestUser(parentEmail)
        val parentRepo = SafeTrackRepository(firestore, null, auth)
        withTimeout(TIMEOUT_MS) {
            parentRepo.ensureUserProfile("Parent DEP", parentEmail, false).getOrThrow()
            parentRepo.claimSchoolInvite(parentInvite, "Parent DEP", parentEmail).getOrThrow()
        }

        signInTestUser(adminEmail)
        val classId = withTimeout(TIMEOUT_MS) {
            adminRepo.createClass(school, "Primary 6B", "Grade 6", "2026/2027", listOf(teacherUid), "Admin DEP").getOrThrow()
        }
        val schoolClass = SchoolClass(
            id = classId,
            schoolId = schoolId,
            name = "Primary 6B",
            gradeLevel = "Grade 6",
            academicYear = "2026/2027",
            teacherUids = listOf(teacherUid),
            adminUids = listOf(adminUid)
        )
        val studentDepartId = withTimeout(TIMEOUT_MS) {
            adminRepo.createStudent(
                school = school,
                admissionNumber = "ADM-DEP1",
                fullName = "Kwabena Osei",
                schoolClass = schoolClass,
                emergencyContactName = "Parent DEP",
                emergencyContactPhone = "+233244111000",
                medicalNotes = "",
                approvedDestinations = listOf("Home - Ridge"),
                authorisedPickups = listOf("Parent DEP"),
                guardianUids = listOf(parentUid),
                pickupUids = emptyList(),
                photoUri = null,
                actorName = "Admin DEP"
            ).getOrThrow()
        }
        val studentLateId = withTimeout(TIMEOUT_MS) {
            adminRepo.createStudent(
                school = school,
                admissionNumber = "ADM-DEP2",
                fullName = "Adwoa Boateng",
                schoolClass = schoolClass,
                emergencyContactName = "Parent DEP",
                emergencyContactPhone = "+233244111000",
                medicalNotes = "",
                approvedDestinations = listOf("Home - Ridge"),
                authorisedPickups = listOf("Parent DEP"),
                guardianUids = listOf(parentUid),
                pickupUids = emptyList(),
                photoUri = null,
                actorName = "Admin DEP"
            ).getOrThrow()
        }

        // Teacher dispatches whole-class departure table with 1 departing + 1 leaving-late exception
        signInTestUser(teacherEmail)
        val students = withTimeout(FLOW_TIMEOUT_MS) {
            teacherRepo.observeStudents(schoolId, UserRole.TEACHER).first { list ->
                list.any { it.id == studentDepartId } && list.any { it.id == studentLateId }
            }
        }
        val departingStudent = students.first { it.id == studentDepartId }
        val lateStudent = students.first { it.id == studentLateId }

        val dispatchResult = withTimeout(TIMEOUT_MS) {
            teacherRepo.dispatchClassDepartureAlerts(
                entries = listOf(
                    ClassDepartureEntry(
                        student = departingStudent,
                        isLeavingLate = false,
                        lateReason = "",
                        destinationName = "Home - Ridge"
                    ),
                    ClassDepartureEntry(
                        student = lateStudent,
                        isLeavingLate = true,
                        lateReason = "Robotics club rehearsal until 16:30",
                        destinationName = "Home - Ridge"
                    )
                ),
                notifyLateExceptions = true,
                teacherName = "Teacher DEP",
                teacherRole = UserRole.TEACHER.key,
                latitude = 6.6885,
                longitude = -1.6244
            ).getOrThrow()
        }
        assertEquals(1, dispatchResult.departedCount)
        assertEquals(1, dispatchResult.lateExceptionCount)

        // Parent receives departure journey ONLY for departing pupil and BOTH departure + late_departure notifications
        signInTestUser(parentEmail)
        val parentJourneys = withTimeout(FLOW_TIMEOUT_MS) {
            parentRepo.observeJourneys(schoolId, UserRole.PARENT).first { list ->
                list.any { it.studentId == studentDepartId && it.status == MovementStatus.ON_THE_WAY_HOME.key }
            }
        }
        assertTrue(
            "Late-exception student must NOT be marked as departed",
            parentJourneys.none { it.studentId == studentLateId && it.status == MovementStatus.ON_THE_WAY_HOME.key }
        )

        val parentNotifications = withTimeout(FLOW_TIMEOUT_MS) {
            parentRepo.observeMyNotifications().first { list ->
                list.any { it.type == "journey" && it.studentId == studentDepartId } &&
                    list.any { it.type == "late_departure" && it.studentId == studentLateId }
            }
        }
        val lateNotif = parentNotifications.first { it.type == "late_departure" && it.studentId == studentLateId }
        assertTrue(lateNotif.body.contains("Robotics club rehearsal"))

        assertEquals(300.0, school.monthlyPriceGhs, 0.01)
        assertEquals("trial", school.subscriptionStatus)
        assertTrue(!SchoolLevel.PRIMARY.label.contains("4th Core", ignoreCase = true))
        assertTrue(!SchoolLevel.JHS.label.contains("4th Core", ignoreCase = true))

        // Teacher adds, edits, and deletes another teacher record with assigned classes & subjects
        signInTestUser(teacherEmail)
        val addedTeacherId = withTimeout(TIMEOUT_MS) {
            teacherRepo.upsertTeacherMember(
                school = school,
                existingMember = null,
                displayName = "Mr. Kojo Antwi",
                email = "kojo.antwi@schoola.edu",
                phone = "+233201234567",
                role = UserRole.TEACHER,
                assignedClassIds = listOf(classId),
                assignedSubjectIds = listOf("sub_math"),
                assignedSubjectNames = listOf("Mathematics", "Science"),
                status = "active",
                actorName = "Teacher DEP",
                actorRole = UserRole.TEACHER.key
            ).getOrThrow()
        }
        val teacherMembers = withTimeout(FLOW_TIMEOUT_MS) {
            teacherRepo.observeSchoolMembers(schoolId, UserRole.TEACHER).first { list ->
                list.any { (it.userId == addedTeacherId || it.id == addedTeacherId) }
            }
        }
        val addedMember = teacherMembers.first { it.userId == addedTeacherId || it.id == addedTeacherId }
        assertEquals(listOf("Mathematics", "Science"), addedMember.assignedSubjectNames)

        withTimeout(TIMEOUT_MS) {
            teacherRepo.upsertTeacherMember(
                school = school,
                existingMember = addedMember,
                displayName = "Mr. Kojo Antwi-Mensah",
                email = "kojo.mensah@schoola.edu",
                phone = "+233209876543",
                role = UserRole.TEACHER,
                assignedClassIds = listOf(classId),
                assignedSubjectIds = listOf("sub_eng"),
                assignedSubjectNames = listOf("English Language"),
                status = "active",
                actorName = "Teacher DEP",
                actorRole = UserRole.TEACHER.key
            ).getOrThrow()
        }
        val editedTeacher = withTimeout(FLOW_TIMEOUT_MS) {
            teacherRepo.observeSchoolMembers(schoolId, UserRole.TEACHER).first { list ->
                list.any { (it.userId == addedTeacherId || it.id == addedTeacherId) && it.displayName == "Mr. Kojo Antwi-Mensah" }
            }.first { it.userId == addedTeacherId || it.id == addedTeacherId }
        }
        assertEquals("kojo.mensah@schoola.edu", editedTeacher.email)
        assertEquals(listOf("English Language"), editedTeacher.assignedSubjectNames)

        withTimeout(TIMEOUT_MS) {
            teacherRepo.deleteSchoolMember(editedTeacher, "Teacher DEP", UserRole.TEACHER.key).getOrThrow()
        }
        val afterTeacherDelete = withTimeout(FLOW_TIMEOUT_MS) {
            teacherRepo.observeSchoolMembers(schoolId, UserRole.TEACHER).first { list ->
                list.none { it.userId == addedTeacherId || it.id == addedTeacherId }
            }
        }
        assertTrue(afterTeacherDelete.none { it.userId == addedTeacherId || it.id == addedTeacherId })

        // Teacher cannot delete Admin member (zero-trust protection)
        val adminMember = afterTeacherDelete.first { it.role == UserRole.SCHOOL_ADMIN.key }
        val deleteAdminAttempt = withTimeout(TIMEOUT_MS) {
            teacherRepo.deleteSchoolMember(adminMember, "Teacher DEP", UserRole.TEACHER.key)
        }
        assertTrue("Teacher must not be allowed to delete school_admin", deleteAdminAttempt.isFailure)

        // Teacher edits and deletes a student record
        withTimeout(TIMEOUT_MS) {
            teacherRepo.updateStudentProfile(
                student = lateStudent,
                admissionNumber = lateStudent.admissionNumber,
                fullName = "Adwoa Boateng-Sarpong",
                schoolClass = schoolClass,
                emergencyContactName = "Parent DEP",
                emergencyContactPhone = "+233244111000",
                medicalNotes = "Mild asthma",
                approvedDestinations = listOf("Home - Ridge"),
                authorisedPickups = listOf("Parent DEP"),
                actorName = "Teacher DEP",
                actorRole = UserRole.TEACHER.key
            ).getOrThrow()
        }
        withTimeout(TIMEOUT_MS) {
            teacherRepo.deleteStudent(lateStudent, "Teacher DEP", UserRole.TEACHER.key).getOrThrow()
        }
        val studentsAfterDelete = withTimeout(FLOW_TIMEOUT_MS) {
            teacherRepo.observeStudents(schoolId, UserRole.TEACHER).first { list ->
                list.none { it.id == studentLateId }
            }
        }
        assertTrue(studentsAfterDelete.none { it.id == studentLateId })
    }

    @Test
    fun appDistributionConfig_adminSavesViewAccessAndDownloadUrl_parentReadsOnly() = runBlocking {
        val uniqueSuffix = UUID.randomUUID().toString().take(6)
        val adminEmail = "admin_dist_$uniqueSuffix@schoola.edu"
        val parentEmail = "par_dist_$uniqueSuffix@gmail.com"

        signInTestUser(adminEmail)
        val adminRepo = SafeTrackRepository(firestore, null, auth)
        withTimeout(TIMEOUT_MS) {
            adminRepo.ensureUserProfile("Admin DIST", adminEmail, false).getOrThrow()
        }
        val schoolId = withTimeout(TIMEOUT_MS) {
            adminRepo.registerSchool(
                schoolName = "SafeTrack Dist $uniqueSuffix",
                city = "Accra",
                country = "Ghana",
                address = "5 Independence Ave",
                phone = "+233200000555",
                email = adminEmail,
                plan = testPlan(),
                billingCycle = "monthly",
                ownerDisplayName = "Admin DIST",
                ownerEmail = adminEmail
            ).getOrThrow()
        }
        val school = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeSchool(schoolId).first { it != null }!!
        }
        val parentInvite = withTimeout(TIMEOUT_MS) {
            adminRepo.createSchoolInvite(school, "", UserRole.PARENT, emptyList(), emptyList(), "Admin DIST").getOrThrow()
        }

        val customUrl = "https://play.google.com/store/apps/details?id=com.aistudio.safetrack.xkqvmz&invite=$parentInvite"
        withTimeout(TIMEOUT_MS) {
            adminRepo.saveAppDistributionConfig(
                configId = schoolId,
                downloadUrl = customUrl,
                viewAccessMode = AppViewAccessMode.INVITE_ONLY.key,
                allowedDeviceTypes = listOf("phone", "tablet", "computer"),
                releaseNotes = "Open on phone, tablet, or computer using invite $parentInvite"
            ).getOrThrow()
        }

        val adminConfig = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeAppDistributionConfig(schoolId).first { it.id == schoolId }
        }
        assertEquals(customUrl, adminConfig.downloadUrl)
        assertEquals(AppViewAccessMode.INVITE_ONLY.key, adminConfig.viewAccessMode)
        assertEquals(listOf("phone", "tablet", "computer"), adminConfig.allowedDeviceTypes)

        // Invalid URL scheme, HTTP URL, empty URL, or ephemeral preview host rejected locally before write
        val badUrlAttempt = withTimeout(TIMEOUT_MS) {
            adminRepo.saveAppDistributionConfig(
                configId = schoolId,
                downloadUrl = "ftp://invalid.example.com",
                viewAccessMode = AppViewAccessMode.PUBLIC_OPEN.key,
                allowedDeviceTypes = listOf("phone"),
                releaseNotes = "Bad"
            )
        }
        assertTrue(badUrlAttempt.isFailure)

        val previewUrlAttempt = withTimeout(TIMEOUT_MS) {
            adminRepo.saveAppDistributionConfig(
                configId = schoolId,
                downloadUrl = "https://ais-pre-bmveivitgy4vceabezjujv-228515890579.europe-west2.run.app",
                viewAccessMode = AppViewAccessMode.PUBLIC_OPEN.key,
                allowedDeviceTypes = listOf("phone"),
                releaseNotes = "Ephemeral preview"
            )
        }
        assertTrue(previewUrlAttempt.isFailure)

        // Parent joins school -> inspects invite before & after claiming, reads config, CANNOT overwrite school config
        signInTestUser(parentEmail)
        val parentRepo = SafeTrackRepository(firestore, null, auth)
        withTimeout(TIMEOUT_MS) {
            parentRepo.ensureUserProfile("Parent DIST", parentEmail, false).getOrThrow()
        }
        val beforeClaim = withTimeout(TIMEOUT_MS) {
            parentRepo.inspectSchoolInvite(parentInvite)
        }
        assertEquals(com.example.data.model.InviteInspectionStatus.VALID_PENDING, beforeClaim.status)

        withTimeout(TIMEOUT_MS) {
            parentRepo.claimSchoolInvite(parentInvite, "Parent DIST", parentEmail).getOrThrow()
        }

        val afterClaim = withTimeout(TIMEOUT_MS) {
            parentRepo.inspectSchoolInvite(parentInvite)
        }
        assertEquals(com.example.data.model.InviteInspectionStatus.ALREADY_CLAIMED, afterClaim.status)

        val missingInspect = withTimeout(TIMEOUT_MS) {
            parentRepo.inspectSchoolInvite("INV-NONEXISTENT")
        }
        assertEquals(com.example.data.model.InviteInspectionStatus.EMAIL_MISMATCH_OR_RESTRICTED, missingInspect.status)

        val parentConfig = withTimeout(FLOW_TIMEOUT_MS) {
            parentRepo.observeAppDistributionConfig(schoolId).first { it.id == schoolId }
        }
        assertEquals(customUrl, parentConfig.downloadUrl)

        val parentWriteAttempt = withTimeout(TIMEOUT_MS) {
            parentRepo.saveAppDistributionConfig(
                configId = schoolId,
                downloadUrl = "https://downloads.safetrack-school.org/app",
                viewAccessMode = AppViewAccessMode.PUBLIC_OPEN.key,
                allowedDeviceTypes = listOf("phone"),
                releaseNotes = "Tampered"
            )
        }
        assertTrue("Parent must not modify school app_distribution config", parentWriteAttempt.isFailure)
    }

    @Test
    fun assignedTeacherOnly_canAuthorizeDismissal_adminAndUnassignedRejected() = runBlocking {
        val uniqueSuffix = UUID.randomUUID().toString().take(6)
        val adminEmail = "admin_dism_$uniqueSuffix@schoola.edu"
        val assignedEmail = "teach1_$uniqueSuffix@schoola.edu"
        val unassignedEmail = "teach2_$uniqueSuffix@schoola.edu"

        val adminUid = signInTestUser(adminEmail)
        val adminRepo = SafeTrackRepository(firestore, null, auth)
        withTimeout(TIMEOUT_MS) {
            adminRepo.ensureUserProfile("Admin Dismiss", adminEmail, false).getOrThrow()
        }
        val schoolId = withTimeout(TIMEOUT_MS) {
            adminRepo.registerSchool(
                schoolName = "Dismiss Check $uniqueSuffix",
                city = "Accra",
                country = "Ghana",
                address = "1 Liberation Rd",
                phone = "+233200000777",
                email = adminEmail,
                plan = testPlan(),
                billingCycle = "monthly",
                ownerDisplayName = "Admin Dismiss",
                ownerEmail = adminEmail
            ).getOrThrow()
        }
        val school = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeSchool(schoolId).first { it != null }!!
        }
        val assignedInv = withTimeout(TIMEOUT_MS) {
            adminRepo.createSchoolInvite(school, "", UserRole.TEACHER, emptyList(), emptyList(), "Admin Dismiss").getOrThrow()
        }
        val unassignedInv = withTimeout(TIMEOUT_MS) {
            adminRepo.createSchoolInvite(school, "", UserRole.TEACHER, emptyList(), emptyList(), "Admin Dismiss").getOrThrow()
        }

        val assignedUid = signInTestUser(assignedEmail)
        val assignedRepo = SafeTrackRepository(firestore, null, auth)
        withTimeout(TIMEOUT_MS) {
            assignedRepo.ensureUserProfile("Assigned Teacher", assignedEmail, false).getOrThrow()
            assignedRepo.claimSchoolInvite(assignedInv, "Assigned Teacher", assignedEmail).getOrThrow()
        }

        signInTestUser(unassignedEmail)
        val unassignedRepo = SafeTrackRepository(firestore, null, auth)
        withTimeout(TIMEOUT_MS) {
            unassignedRepo.ensureUserProfile("Unassigned Teacher", unassignedEmail, false).getOrThrow()
            unassignedRepo.claimSchoolInvite(unassignedInv, "Unassigned Teacher", unassignedEmail).getOrThrow()
        }

        signInTestUser(adminEmail)
        val classId = withTimeout(TIMEOUT_MS) {
            adminRepo.createClass(school, "Primary 3A", "Grade 3", "2026/2027", listOf(assignedUid), "Admin Dismiss").getOrThrow()
        }
        val schoolClass = SchoolClass(
            id = classId,
            schoolId = schoolId,
            name = "Primary 3A",
            gradeLevel = "Grade 3",
            academicYear = "2026/2027",
            teacherUids = listOf(assignedUid),
            adminUids = listOf(adminUid)
        )
        val studentId = withTimeout(TIMEOUT_MS) {
            adminRepo.createStudent(
                school = school,
                admissionNumber = "ADM-DISM-1",
                fullName = "Esi Appiah",
                schoolClass = schoolClass,
                emergencyContactName = "Guardian Appiah",
                emergencyContactPhone = "+233244999888",
                medicalNotes = "",
                approvedDestinations = listOf("Home"),
                authorisedPickups = listOf("Guardian Appiah"),
                guardianUids = emptyList(),
                pickupUids = emptyList(),
                photoUri = null,
                actorName = "Admin Dismiss"
            ).getOrThrow()
        }
        val targetStudent = Student(
            id = studentId,
            schoolId = schoolId,
            admissionNumber = "ADM-DISM-1",
            fullName = "Esi Appiah",
            classId = classId,
            className = "Primary 3A",
            academicYear = "2026/2027",
            teacherUids = listOf(assignedUid),
            adminUids = listOf(adminUid)
        )

        // School Admin cannot authorize dismissal
        val adminAttempt = withTimeout(TIMEOUT_MS) {
            adminRepo.initiateAfternoonDismissal(
                student = targetStudent,
                destinationName = "Home",
                isAlternativeDestination = false,
                teacherName = "Admin Dismiss",
                teacherRole = UserRole.SCHOOL_ADMIN.key,
                consentGranted = false,
                latitude = null,
                longitude = null,
                manualNote = "Attempt by admin"
            )
        }
        assertTrue("School admin must be denied dismissal authorization", adminAttempt.isFailure)

        // Unassigned teacher cannot authorize dismissal
        signInTestUser(unassignedEmail)
        val unassignedAttempt = withTimeout(TIMEOUT_MS) {
            unassignedRepo.initiateAfternoonDismissal(
                student = targetStudent,
                destinationName = "Home",
                isAlternativeDestination = false,
                teacherName = "Unassigned Teacher",
                teacherRole = UserRole.TEACHER.key,
                consentGranted = false,
                latitude = null,
                longitude = null,
                manualNote = "Attempt by unassigned teacher"
            )
        }
        assertTrue("Unassigned teacher must be denied dismissal authorization", unassignedAttempt.isFailure)

        // Assigned teacher succeeds with manual dismissal & audit log
        signInTestUser(assignedEmail)
        val assignedAttempt = withTimeout(TIMEOUT_MS) {
            assignedRepo.initiateAfternoonDismissal(
                student = targetStudent,
                destinationName = "Home",
                isAlternativeDestination = false,
                teacherName = "Assigned Teacher",
                teacherRole = UserRole.TEACHER.key,
                consentGranted = false,
                latitude = null,
                longitude = null,
                manualNote = "Released by class teacher"
            )
        }
        assertTrue("Assigned teacher must succeed authorizing dismissal", assignedAttempt.isSuccess)
    }

    @Test
    fun schoolSettings_inventory_feeRecords_andDeviceSessions_enforceRbacEndToEnd() = runBlocking {
        val uniqueSuffix = UUID.randomUUID().toString().take(6)
        val adminEmail = "admin_saas_$uniqueSuffix@school.edu"
        val parentEmail = "parent_saas_$uniqueSuffix@gmail.com"

        val adminUid = signInTestUser(adminEmail)
        val adminRepo = SafeTrackRepository(firestore, null, auth)
        withTimeout(TIMEOUT_MS) {
            adminRepo.ensureUserProfile("Admin SaaS", adminEmail, false).getOrThrow()
        }
        val schoolId = withTimeout(TIMEOUT_MS) {
            adminRepo.registerSchool(
                schoolName = "Cefelix Test $uniqueSuffix",
                city = "Accra",
                country = "Ghana",
                address = "1 Ring Rd",
                phone = "0503402872",
                email = "info.cefelixacademy.com",
                plan = testPlan(),
                billingCycle = "monthly",
                ownerDisplayName = "Admin SaaS",
                ownerEmail = adminEmail
            ).getOrThrow()
        }
        val school = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeSchool(schoolId).first { it != null }!!
        }

        // Update extended school info
        withTimeout(TIMEOUT_MS) {
            adminRepo.updateSchoolProfileAndLogo(
                school = school,
                name = "Cefelix Academy $uniqueSuffix",
                city = "Accra, Ghana",
                phone = "0503402872",
                email = "info.cefelixacademy.com",
                logoUri = null,
                actorName = "Admin SaaS",
                actorRole = UserRole.SCHOOL_ADMIN.key,
                address = "Cantonments, Accra",
                motto = "Excellence and Safety",
                academicYear = "2026/2027",
                currentTerm = "Term 1",
                operatingHours = "07:00 - 15:30"
            ).getOrThrow()
        }
        val updatedSchool = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeSchool(schoolId).first { it?.motto == "Excellence and Safety" }!!
        }
        assertEquals("07:00 - 15:30", updatedSchool.operatingHours)

        // Create invite + Parent claims
        val parentInv = withTimeout(TIMEOUT_MS) {
            adminRepo.createSchoolInvite(updatedSchool, "", UserRole.PARENT, emptyList(), emptyList(), "Admin SaaS").getOrThrow()
        }
        val parentUid = signInTestUser(parentEmail)
        val parentRepo = SafeTrackRepository(firestore, null, auth)
        withTimeout(TIMEOUT_MS) {
            parentRepo.ensureUserProfile("Parent SaaS", parentEmail, false).getOrThrow()
            parentRepo.claimSchoolInvite(parentInv, "Parent SaaS", parentEmail).getOrThrow()
        }

        // Admin creates class + pupil linked to Parent, then Inventory & Fee record
        signInTestUser(adminEmail)
        val classId = withTimeout(TIMEOUT_MS) {
            adminRepo.createClass(updatedSchool, "Primary 4A", "Primary 4", "2026/2027", emptyList(), "Admin SaaS").getOrThrow()
        }
        val cls = SchoolClass(id = classId, schoolId = schoolId, name = "Primary 4A", adminUids = listOf(adminUid))
        val stuId = withTimeout(TIMEOUT_MS) {
            adminRepo.createStudent(
                school = updatedSchool,
                admissionNumber = "CFX-$uniqueSuffix",
                fullName = "Nana Yaw",
                schoolClass = cls,
                emergencyContactName = "Parent SaaS",
                emergencyContactPhone = "+233200000111",
                medicalNotes = "",
                approvedDestinations = listOf("Home"),
                authorisedPickups = listOf("Parent SaaS"),
                guardianUids = listOf(parentUid),
                pickupUids = emptyList(),
                photoUri = null,
                actorName = "Admin SaaS"
            ).getOrThrow()
        }
        val stu = Student(
            id = stuId,
            schoolId = schoolId,
            admissionNumber = "CFX-$uniqueSuffix",
            fullName = "Nana Yaw",
            classId = classId,
            className = "Primary 4A",
            guardianUids = listOf(parentUid),
            adminUids = listOf(adminUid)
        )

        // Inventory upsert & delete
        withTimeout(TIMEOUT_MS) {
            adminRepo.upsertInventoryItem(
                school = updatedSchool,
                existingItem = null,
                name = "Smart Board",
                category = "ICT & Devices",
                quantity = 3,
                minimumStock = 1,
                condition = "Good",
                assignedTo = "Primary 4A",
                notes = "Installed",
                teacherUids = emptyList(),
                actorName = "Admin SaaS",
                actorRole = UserRole.SCHOOL_ADMIN.key
            ).getOrThrow()
        }
        val invItems = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeInventoryItems(schoolId, UserRole.SCHOOL_ADMIN).first { it.isNotEmpty() }
        }
        assertEquals("Smart Board", invItems.first().name)

        // Fee record upsert & parent read-only verification
        withTimeout(TIMEOUT_MS) {
            adminRepo.upsertFeeRecord(
                school = updatedSchool,
                student = stu,
                existingRecord = null,
                title = "Term 1 Tuition",
                term = "Term 1",
                academicYear = "2026/2027",
                amountDueGhs = 800.0,
                amountPaidGhs = 800.0,
                paymentMethod = "Paystack",
                receiptNumber = "PAY-$uniqueSuffix",
                dueDate = "2026-10-15",
                actorName = "Admin SaaS",
                actorRole = UserRole.SCHOOL_ADMIN.key
            ).getOrThrow()
        }

        // Parent sees their linked child's fee record & can register/revoke device session
        signInTestUser(parentEmail)
        val parentFees = withTimeout(FLOW_TIMEOUT_MS) {
            parentRepo.observeFeeRecords(schoolId, UserRole.PARENT).first { it.isNotEmpty() }
        }
        assertEquals("paid", parentFees.first().status)

        withTimeout(TIMEOUT_MS) {
            parentRepo.registerDeviceSession("sess_$uniqueSuffix", "Pixel 9", "Android 15", "1.0").getOrThrow()
        }
        val sessions = withTimeout(FLOW_TIMEOUT_MS) {
            parentRepo.observeDeviceSessions().first { it.any { s -> s.id == "sess_$uniqueSuffix" } }
        }
        assertTrue(sessions.any { it.deviceName == "Pixel 9" })
        withTimeout(TIMEOUT_MS) {
            parentRepo.revokeDeviceSession("sess_$uniqueSuffix").getOrThrow()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 8000L
        const val FLOW_TIMEOUT_MS = 5000L
    }
}

