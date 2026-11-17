package com.example

import com.example.data.model.AppDistributionConfig
import com.example.data.model.AppViewAccessMode
import com.example.data.model.CefelixAcademyTemplate
import com.example.data.model.DeviceViewLayoutMode
import com.example.data.model.ExamGrading
import com.example.data.model.ExamReport
import com.example.data.model.ExamReportValidator
import com.example.data.model.SchoolLevel
import com.example.data.model.Subject
import com.example.util.AppLinkValidator
import com.example.util.DownloadDestinationType
import com.example.util.QrCodeGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun appDistributionConfig_defaultsDoNotInventPublicPreviewUrl() {
        val cfg = AppDistributionConfig()
        assertEquals("", AppDistributionConfig.DEFAULT_PUBLIC_APP_URL)
        assertEquals("", cfg.downloadUrl)
        assertEquals(AppViewAccessMode.PUBLIC_OPEN.key, cfg.viewAccessMode)
        assertEquals(listOf("phone", "tablet", "computer"), cfg.allowedDeviceTypes)
    }

    @Test
    fun appViewAccessAndDeviceLayout_fromKeyFallbackCorrectly() {
        assertEquals(AppViewAccessMode.INVITE_ONLY, AppViewAccessMode.fromKey("invite_only"))
        assertEquals(AppViewAccessMode.STAFF_AND_PARENTS, AppViewAccessMode.fromKey("staff_and_parents"))
        assertEquals(AppViewAccessMode.PUBLIC_OPEN, AppViewAccessMode.fromKey("unknown"))

        assertEquals(DeviceViewLayoutMode.PHONE, DeviceViewLayoutMode.fromKey("phone"))
        assertEquals(DeviceViewLayoutMode.TABLET, DeviceViewLayoutMode.fromKey("tablet"))
        assertEquals(DeviceViewLayoutMode.COMPUTER, DeviceViewLayoutMode.fromKey("computer"))
        assertEquals(DeviceViewLayoutMode.AUTO, DeviceViewLayoutMode.fromKey("unknown"))
    }

    @Test
    fun appLinkValidator_acceptsVerifiedHttpsDestinationsAndRejectsPreviewOrInsecureLinks() {
        val play = AppLinkValidator.validatePublicDownloadUrl(
            "https://play.google.com/store/apps/details?id=com.aistudio.safetrack.xkqvmz"
        )
        assertTrue(play.isValid)
        assertEquals(DownloadDestinationType.GOOGLE_PLAY, play.destinationType)

        val firebaseDist = AppLinkValidator.validatePublicDownloadUrl(
            "https://appdistribution.firebase.google.com/pub/i/safetrack01"
        )
        assertTrue(firebaseDist.isValid)
        assertEquals(DownloadDestinationType.FIREBASE_APP_DISTRIBUTION, firebaseDist.destinationType)

        val customWeb = AppLinkValidator.validatePublicDownloadUrl(
            "https://downloads.ridgeacademy.edu.gh/safetrack"
        )
        assertTrue(customWeb.isValid)
        assertEquals(DownloadDestinationType.HTTPS_WEB_HOST, customWeb.destinationType)

        val blank = AppLinkValidator.validatePublicDownloadUrl("")
        assertFalse(blank.isValid)
        assertEquals(DownloadDestinationType.UNCONFIGURED, blank.destinationType)

        val http = AppLinkValidator.validatePublicDownloadUrl("http://downloads.ridgeacademy.edu.gh/app.apk")
        assertFalse(http.isValid)
        assertEquals(DownloadDestinationType.INVALID, http.destinationType)

        val preview = AppLinkValidator.validatePublicDownloadUrl(
            "https://ais-pre-bmveivitgy4vceabezjujv-228515890579.europe-west2.run.app"
        )
        assertFalse(preview.isValid)
        assertEquals(DownloadDestinationType.INVALID, preview.destinationType)

        val withSpaces = AppLinkValidator.validatePublicDownloadUrl("https://example.org/my app")
        assertFalse(withSpaces.isValid)
    }

    @Test
    fun appLinkValidator_buildsCanonicalShareUrlWithoutExposingPersonalData() {
        val share = AppLinkValidator.buildShareableUrl(
            baseUrl = "https://downloads.ridgeacademy.edu.gh/safetrack",
            inviteCode = "ST-INV-9988"
        )
        assertTrue(share.isValid)
        assertEquals("https://downloads.ridgeacademy.edu.gh/safetrack?invite=ST-INV-9988", share.normalizedUrl)

        val playShare = AppLinkValidator.buildShareableUrl(
            baseUrl = "https://play.google.com/store/apps/details?id=com.aistudio.safetrack.xkqvmz",
            inviteCode = "INV_1234"
        )
        assertTrue(playShare.isValid)
        assertEquals(
            "https://play.google.com/store/apps/details?id=com.aistudio.safetrack.xkqvmz&invite=INV_1234",
            playShare.normalizedUrl
        )

        assertNull(AppLinkValidator.sanitizeInviteCode("bad invite code with spaces"))
        val emptyShare = AppLinkValidator.buildShareableUrl("", "INV-1234")
        assertFalse(emptyShare.isValid)
        assertEquals(DownloadDestinationType.UNCONFIGURED, emptyShare.destinationType)
    }

    @Test
    fun cefelixAcademyTemplate_andGradingBands_matchInternationalPrimarySpec() {
        assertEquals("CEFELIX ACADEMY", CefelixAcademyTemplate.SCHOOL_NAME)
        assertEquals("Accra, Ghana.", CefelixAcademyTemplate.SCHOOL_LOCATION)
        assertEquals("0503402872", CefelixAcademyTemplate.CONTACT_PHONE)
        assertEquals("info.cefelixacademy.com", CefelixAcademyTemplate.CONTACT_EMAIL)
        assertEquals("2026/2027", CefelixAcademyTemplate.ACADEMIC_YEAR)
        assertEquals("Term 1", CefelixAcademyTemplate.TERM)
        assertEquals(7, CefelixAcademyTemplate.primarySubjects.size)
        assertEquals(
            listOf(
                "Computing",
                "Creative Arts & Design",
                "English Language",
                "Ghanaian Language",
                "Integrated Science",
                "Mathematics",
                "Religious & Moral Education"
            ),
            CefelixAcademyTemplate.primarySubjects
        )

        assertEquals(1 to "Excellent", ExamGrading.gradeAndRemark(80.0))
        assertEquals(2 to "Very Good", ExamGrading.gradeAndRemark(70.0))
        assertEquals(3 to "Good", ExamGrading.gradeAndRemark(60.0))
        assertEquals(4 to "Credit", ExamGrading.gradeAndRemark(50.0))
        assertEquals(5 to "Pass", ExamGrading.gradeAndRemark(40.0))
        assertEquals(6 to "Fail", ExamGrading.gradeAndRemark(39.9))
        assertEquals("G1", ExamGrading.scaleBands.first().gradeCode)
        assertEquals("80–100%", ExamGrading.scaleBands.first().percentRangeLabel)
    }

    @Test
    fun examReportValidator_detectsDiscrepanciesWithoutSilentlyMutatingScores() {
        val math = Subject(id = "s1", name = "Mathematics", isCore = true, coreSlot = "mathematics")
        val eng = Subject(id = "s2", name = "English Language", isCore = true, coreSlot = "english")
        val sci = Subject(id = "s3", name = "Integrated Science", isCore = true, coreSlot = "science")
        val comp = Subject(id = "s4", name = "Computing", isCore = true, coreSlot = "computing")

        val validScores = listOf(
            ExamGrading.buildScore(math, 90.0, 84.0),   // 87.0 -> G1
            ExamGrading.buildScore(eng, 80.0, 70.0),    // 75.0 -> G2
            ExamGrading.buildScore(sci, 64.0, 62.0),    // 63.0 -> G3
            ExamGrading.buildScore(comp, 82.0, 80.0)    // 81.0 -> G1
        )
        val (calcAgg, _) = ExamGrading.computeAggregate(validScores, SchoolLevel.PRIMARY)
        assertEquals(7, calcAgg)

        val validReport = ExamReport(
            schoolName = CefelixAcademyTemplate.SCHOOL_NAME,
            level = SchoolLevel.PRIMARY.key,
            scores = validScores,
            overallTotal = validScores.sumOf { it.totalScore },
            averageScore = validScores.sumOf { it.totalScore } / validScores.size,
            aggregate = calcAgg
        )
        assertTrue(ExamReportValidator.validate(validReport).isConsistent)

        // Mismatched total, wrong grade, wrong overall average, and mismatched aggregate must all be flagged
        val corruptedScore = validScores.first().copy(totalScore = 99.0, grade = 3)
        val brokenReport = validReport.copy(
            scores = listOf(corruptedScore) + validScores.drop(1),
            overallTotal = 250.0,
            averageScore = 60.0,
            aggregate = 15
        )
        val check = ExamReportValidator.validate(brokenReport)
        assertFalse(check.isConsistent)
        assertTrue(check.issues.size >= 4)
        // Stored broken numbers must remain untouched for human review
        assertEquals(99.0, brokenReport.scores.first().totalScore, 0.01)
    }

    @Test
    fun qrCodeGenerator_createsValidSquareMatrixWithThreeFinderPatternsAndDeterministicOutput() {
        val shortUrl = "https://cefelix.edu.gh"
        val playUrl = "https://play.google.com/store/apps/details?id=com.aistudio.safetrack.xkqvmz&invite=INV-CFX-01"

        val qr1 = QrCodeGenerator.encode(shortUrl)
        val qr1Again = QrCodeGenerator.encode(shortUrl)
        val qr2 = QrCodeGenerator.encode(playUrl)

        assertTrue(qr1.size >= 21)
        assertEquals(qr1.size, qr1.modules.size)
        assertTrue(qr1.modules.all { it.size == qr1.size })
        for (r in 0 until qr1.size) {
            assertTrue(qr1.modules[r].contentEquals(qr1Again.modules[r]))
        }

        // Top-left, top-right, bottom-left 7x7 finder pattern outer rings must be set dark
        listOf(
            0 to 0,
            0 to (qr1.size - 7),
            (qr1.size - 7) to 0
        ).forEach { (r0, c0) ->
            for (i in 0..6) {
                assertTrue(qr1.get(r0, c0 + i))
                assertTrue(qr1.get(r0 + 6, c0 + i))
                assertTrue(qr1.get(r0 + i, c0))
                assertTrue(qr1.get(r0 + i, c0 + 6))
            }
            // Inner 3x3 dark core at +2..+4
            assertTrue(qr1.get(r0 + 3, c0 + 3))
        }

        assertTrue(qr2.size >= qr1.size)
    }

    @Test
    fun appLinkValidator_resolvesStoreFallbackUrlsAndBuildsWhatsAppUri() {
        val fallbackPlay = AppLinkValidator.resolvePreferredDownloadUrl(
            primaryUrl = "",
            playStoreUrl = "https://play.google.com/store/apps/details?id=com.aistudio.safetrack.xkqvmz",
            appStoreUrl = "https://apps.apple.com/app/id123456",
            apkDownloadUrl = "https://downloads.cefelixacademy.com/safetrack.apk",
            inviteCode = "CFX-2026"
        )
        assertTrue(fallbackPlay.isValid)
        assertEquals(DownloadDestinationType.GOOGLE_PLAY, fallbackPlay.destinationType)
        assertEquals(
            "https://play.google.com/store/apps/details?id=com.aistudio.safetrack.xkqvmz&invite=CFX-2026",
            fallbackPlay.normalizedUrl
        )

        val previewRejected = AppLinkValidator.resolvePreferredDownloadUrl(
            primaryUrl = "https://ais-pre-abc-123.europe-west2.run.app",
            playStoreUrl = "",
            appStoreUrl = "",
            apkDownloadUrl = "https://releases.cefelixacademy.com/cefelix.apk"
        )
        assertTrue(previewRejected.isValid)
        assertEquals("https://releases.cefelixacademy.com/cefelix.apk", previewRejected.normalizedUrl)

        val waUri = AppLinkValidator.buildWhatsAppShareUri(fallbackPlay.normalizedUrl, "Join Cefelix Academy")
        assertTrue(waUri.startsWith("https://wa.me/?text="))
        assertTrue(waUri.contains("CFX-2026"))
    }
}
