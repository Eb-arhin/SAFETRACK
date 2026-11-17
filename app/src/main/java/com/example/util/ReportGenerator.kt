package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.model.AttendanceRecord
import com.example.data.model.AttendanceStatus
import com.example.data.model.CefelixAcademyTemplate
import com.example.data.model.ExamGrading
import com.example.data.model.ExamReport
import com.example.data.model.ExamReportValidator
import com.example.data.model.ExamSubjectScore
import com.example.data.model.Incident
import com.example.data.model.Journey
import com.example.data.model.MovementStatus
import com.example.data.model.School
import com.example.data.model.SchoolLevel
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ReportType(val label: String) {
    ATTENDANCE("Attendance Summary"),
    MOVEMENTS("Student Movements"),
    LATE_ABSENT("Late Arrivals & Absences"),
    INCIDENTS("Incidents & Safety Log"),
    SUBSCRIPTION("School Plan & Enrollment Summary")
}

object ReportGenerator {

    private val NAVY = Color.rgb(10, 37, 64)       // #0A2540
    private val NAVY_LIGHT = Color.rgb(22, 58, 95) // #163A5F
    private val GOLD = Color.rgb(200, 155, 60)     // #C89B3C
    private val GOLD_DARK = Color.rgb(154, 115, 29)
    private val GOLD_SOFT = Color.rgb(253, 246, 228)
    private val ROW_TINT = Color.rgb(243, 247, 252) // #F3F7FC
    private val BORDER = Color.rgb(214, 224, 236)
    private val TEXT_DARK = Color.rgb(15, 23, 42)
    private val TEXT_MUTED = Color.rgb(71, 85, 105)
    private val GREEN_GOOD = Color.rgb(21, 128, 61)
    private val AMBER_WARN = Color.rgb(180, 83, 9)
    private val RED_FAIL = Color.rgb(185, 28, 28)

    fun generateAndSharePdf(
        context: Context,
        school: School?,
        reportType: ReportType,
        attendance: List<AttendanceRecord>,
        journeys: List<Journey>,
        incidents: List<Incident>
    ): Result<File> {
        return runCatching {
            val dir = File(context.cacheDir, "reports").apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(dir, "SafeTrack_${reportType.name.lowercase()}_$stamp.pdf")

            val pdf = PdfDocument()
            val pageWidth = 595
            val pageHeight = 842
            var pageNumber = 1
            var page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            var canvas = page.canvas

            val headerPaint = Paint().apply { color = NAVY; style = Paint.Style.FILL }
            val titlePaint = Paint().apply {
                color = Color.WHITE
                textSize = 18f
                typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            }
            val subtitlePaint = Paint().apply { color = Color.rgb(219, 234, 254); textSize = 10.5f }
            val sectionPaint = Paint().apply {
                color = NAVY
                textSize = 13f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val bodyPaint = Paint().apply { color = TEXT_DARK; textSize = 10.5f }
            val mutedPaint = Paint().apply { color = TEXT_MUTED; textSize = 9.5f }

            fun drawHeader() {
                canvas.drawRect(0f, 0f, pageWidth.toFloat(), 84f, headerPaint)
                canvas.drawRect(0f, 82f, pageWidth.toFloat(), 85f, Paint().apply { color = GOLD })
                canvas.drawText("SafeTrack — ${reportType.label}", 28f, 36f, titlePaint)
                val schoolLabel = school?.let { "${it.name} (${it.code}) • ${it.city}, ${it.country}" } ?: "SafeTrack Platform"
                canvas.drawText(
                    "$schoolLabel • Generated ${SimpleDateFormat("dd MMM yyyy HH:mm", Locale.US).format(Date())}",
                    28f,
                    58f,
                    subtitlePaint
                )
            }

            drawHeader()
            var y = 112f

            fun ensureSpace(needed: Float = 24f) {
                if (y + needed > pageHeight - 40f) {
                    pdf.finishPage(page)
                    pageNumber++
                    page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                    canvas = page.canvas
                    drawHeader()
                    y = 112f
                }
            }

            val lines = buildReportLines(school, reportType, attendance, journeys, incidents)
            canvas.drawText("Report Details (${lines.size} entries)", 28f, y, sectionPaint)
            y += 22f

            if (lines.isEmpty()) {
                canvas.drawText("No records available for the selected report category.", 28f, y, mutedPaint)
            } else {
                lines.forEach { (primary, secondary) ->
                    ensureSpace(36f)
                    canvas.drawText(primary.take(86), 28f, y, bodyPaint)
                    y += 14f
                    canvas.drawText(secondary.take(94), 36f, y, mutedPaint)
                    y += 18f
                }
            }

            pdf.finishPage(page)
            FileOutputStream(file).use { pdf.writeTo(it) }
            pdf.close()

            shareFile(context, file, "application/pdf", "SafeTrack ${reportType.label} PDF")
            file
        }
    }

    fun generateAndShareCsv(
        context: Context,
        school: School?,
        reportType: ReportType,
        attendance: List<AttendanceRecord>,
        journeys: List<Journey>,
        incidents: List<Incident>
    ): Result<File> {
        return runCatching {
            val dir = File(context.cacheDir, "reports").apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(dir, "SafeTrack_${reportType.name.lowercase()}_$stamp.csv")

            val csv = StringBuilder()
            when (reportType) {
                ReportType.ATTENDANCE -> {
                    csv.appendLine("Date,Student,Class,Status,ArrivalTime,RecordedBy,CorrectionReason,OfflineSynced")
                    attendance.forEach { r ->
                        csv.appendLine(listOf(r.dateKey, r.studentName, r.className, AttendanceStatus.fromKey(r.status).label, r.arrivalTime.orEmpty(), r.recordedByName, r.correctionReason, r.isOfflineSynced.toString()).joinToString(",") { esc(it) })
                    }
                }
                ReportType.MOVEMENTS -> {
                    csv.appendLine("Student,Class,Direction,Status,Destination,InitiatedBy,ConfirmedBy,GpsAvailable")
                    journeys.forEach { j ->
                        csv.appendLine(listOf(j.studentName, j.className, j.direction, MovementStatus.fromKey(j.status).label, j.destinationName, j.initiatedByName, j.confirmedByName.orEmpty(), j.gpsAvailable.toString()).joinToString(",") { esc(it) })
                    }
                }
                ReportType.LATE_ABSENT -> {
                    csv.appendLine("Date,Student,Class,Status,ArrivalTime,CorrectionReason")
                    attendance.filter { it.status == AttendanceStatus.LATE.key || it.status == AttendanceStatus.ABSENT.key }
                        .forEach { r ->
                            csv.appendLine(listOf(r.dateKey, r.studentName, r.className, AttendanceStatus.fromKey(r.status).label, r.arrivalTime.orEmpty(), r.correctionReason).joinToString(",") { esc(it) })
                        }
                }
                ReportType.INCIDENTS -> {
                    csv.appendLine("Student,Severity,Status,Title,ReportedBy,Role,Description")
                    incidents.forEach { inc ->
                        csv.appendLine(listOf(inc.studentName, inc.severity, inc.status, inc.title, inc.reportedByName, inc.reportedByRole, inc.description).joinToString(",") { esc(it) })
                    }
                }
                ReportType.SUBSCRIPTION -> {
                    csv.appendLine("School,Code,Plan,MonthlyPriceGHS,PupilLimit,Enrolled,BillingCycle,Status,Provider,PaymentRef")
                    school?.let { s ->
                        csv.appendLine(listOf(s.name, s.code, s.planName, s.monthlyPriceGhs.toString(), s.pupilLimit.toString(), s.studentCount.toString(), s.billingCycle, s.subscriptionStatus, s.paymentProvider, s.paymentReference.orEmpty()).joinToString(",") { esc(it) })
                    }
                }
            }

            file.writeText(csv.toString())
            shareFile(context, file, "text/csv", "SafeTrack ${reportType.label} (Excel CSV)")
            file
        }
    }

    private fun buildReportLines(
        school: School?,
        reportType: ReportType,
        attendance: List<AttendanceRecord>,
        journeys: List<Journey>,
        incidents: List<Incident>
    ): List<Pair<String, String>> {
        return when (reportType) {
            ReportType.ATTENDANCE -> attendance.map { r ->
                "${r.dateKey} • ${r.studentName} (${r.className}) — ${AttendanceStatus.fromKey(r.status).label}" to
                    "By: ${r.recordedByName}${r.arrivalTime?.let { " • Arrival: $it" } ?: ""}${r.correctionReason.takeIf { it.isNotBlank() }?.let { " • Note: $it" } ?: ""}"
            }
            ReportType.MOVEMENTS -> journeys.map { j ->
                "${j.studentName} (${j.className}) — ${MovementStatus.fromKey(j.status).label}" to
                    "Dest: ${j.destinationName} • Initiated: ${j.initiatedByName} • GPS: ${if (j.gpsAvailable) "Active" else "Unavailable/Manual"}"
            }
            ReportType.LATE_ABSENT -> attendance.filter { it.status == AttendanceStatus.LATE.key || it.status == AttendanceStatus.ABSENT.key }
                .map { r ->
                    "${r.dateKey} • ${r.studentName} — ${AttendanceStatus.fromKey(r.status).label}" to
                        "Class: ${r.className}${r.arrivalTime?.let { " • Arrived: $it" } ?: ""}${r.correctionReason.takeIf { it.isNotBlank() }?.let { " • Reason: $it" } ?: ""}"
                }
            ReportType.INCIDENTS -> incidents.map { inc ->
                "[${inc.severity.uppercase()}] ${inc.title} — ${inc.studentName} (${inc.status})" to
                    "Reported by ${inc.reportedByName}: ${inc.description}"
            }
            ReportType.SUBSCRIPTION -> listOfNotNull(school?.let { s ->
                "${s.name} (${s.code}) — ${s.planName} (${s.subscriptionStatus.uppercase()})" to
                    "GH₵${s.monthlyPriceGhs.toInt()}/mo • ${s.studentCount}/${s.pupilLimit} pupils"
            })
        }
    }

    private fun esc(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    private fun shareFile(context: Context, file: File, mimeType: String, subject: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, subject).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(chooser) }
    }

    fun buildCefelixBlankMasterReport(school: School?): ExamReport {
        val scores = CefelixAcademyTemplate.primarySubjects.map { subName ->
            ExamSubjectScore(
                subjectName = subName,
                isCore = ExamGrading.inferCoreSlot(subName) in listOf("mathematics", "english", "science", "computing"),
                coreSlot = ExamGrading.inferCoreSlot(subName),
                rawClassScore = 0.0,
                rawExamScore = 0.0,
                classScore50 = 0.0,
                examScore50 = 0.0,
                totalScore = 0.0,
                grade = 0,
                remark = ""
            )
        }
        return ExamReport(
            schoolId = school?.id.orEmpty(),
            schoolName = school?.name?.ifBlank { CefelixAcademyTemplate.SCHOOL_NAME } ?: CefelixAcademyTemplate.SCHOOL_NAME,
            schoolLogoUri = school?.logoUri,
            schoolCity = school?.city?.ifBlank { CefelixAcademyTemplate.SCHOOL_LOCATION } ?: CefelixAcademyTemplate.SCHOOL_LOCATION,
            schoolPhone = school?.phone?.ifBlank { CefelixAcademyTemplate.CONTACT_PHONE } ?: CefelixAcademyTemplate.CONTACT_PHONE,
            schoolEmail = school?.email?.ifBlank { CefelixAcademyTemplate.CONTACT_EMAIL } ?: CefelixAcademyTemplate.CONTACT_EMAIL,
            studentName = "",
            admissionNumber = "",
            className = "",
            academicYear = CefelixAcademyTemplate.ACADEMIC_YEAR,
            term = CefelixAcademyTemplate.TERM,
            level = CefelixAcademyTemplate.LEVEL_KEY,
            scores = scores,
            overallTotal = 0.0,
            averageScore = 0.0,
            positionText = "",
            aggregate = 0,
            aggregateBreakdown = "",
            attendanceSummary = "",
            attitudeAndConduct = "",
            respectfulness = "",
            punctuality = "",
            classroomParticipation = "",
            neatnessAndResponsibility = "",
            teacherRemarks = "",
            headTeacherRemarks = "",
            classTeacherName = "",
            classTeacherSignature = "",
            headTeacherName = "",
            headTeacherSignature = "",
            issuanceDate = ""
        )
    }

    fun generateAndShareBlankMasterReportPdf(
        context: Context,
        school: School?
    ): Result<File> = generateAndShareExamReportPdf(
        context = context,
        school = school,
        report = buildCefelixBlankMasterReport(school),
        isBlankTemplate = true
    )

    fun generateAndShareExamReportPdf(
        context: Context,
        school: School?,
        report: ExamReport,
        isBlankTemplate: Boolean = false
    ): Result<File> {
        return runCatching {
            val dir = File(context.cacheDir, "reports").apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val safeName = if (isBlankTemplate) "Blank_Master_Template"
            else report.studentName.ifBlank { "Learner" }.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val file = File(dir, "Cefelix_ReportCard_${safeName}_$stamp.pdf")

            val pdf = PdfDocument()
            val pageWidth = 595
            val pageHeight = 842
            val margin = 28f
            val contentWidth = pageWidth - margin * 2f // 539f

            var pageNumber = 1
            var page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            var canvas = page.canvas

            val schoolName = report.schoolName.ifBlank { school?.name ?: CefelixAcademyTemplate.SCHOOL_NAME }
            val city = report.schoolCity.ifBlank { school?.city.orEmpty() }
            val phone = report.schoolPhone.ifBlank { school?.phone.orEmpty() }
            val email = report.schoolEmail.ifBlank { school?.email.orEmpty() }
            val logoUri = report.schoolLogoUri ?: school?.logoUri
            val logoBitmap = loadLogoBitmap(context, logoUri)

            // Reusable Paints
            val navyFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = NAVY; style = Paint.Style.FILL }
            val navyLightFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = NAVY_LIGHT; style = Paint.Style.FILL }
            val goldFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GOLD; style = Paint.Style.FILL }
            val goldStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GOLD; style = Paint.Style.STROKE; strokeWidth = 1.4f }
            val rowTintFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ROW_TINT; style = Paint.Style.FILL }
            val whiteFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
            val goldSoftFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GOLD_SOFT; style = Paint.Style.FILL }
            val borderStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BORDER; style = Paint.Style.STROKE; strokeWidth = 0.9f }

            val schoolTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 16.5f
                typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            }
            val headerSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(226, 232, 240); textSize = 9.2f }
            val headerGoldBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(253, 230, 138)
                textSize = 8.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val bannerTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 9.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            val secLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = NAVY
                textSize = 9.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val fieldLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = TEXT_MUTED
                textSize = 7.2f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val bodyDark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TEXT_DARK; textSize = 8.8f }
            val boldDark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = TEXT_DARK
                textSize = 8.8f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val mutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TEXT_MUTED; textSize = 7.8f }
            val tableHdrPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 8.2f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }

            fun drawCrest(cx: Float, cy: Float, radius: Float) {
                if (logoBitmap != null) {
                    val dest = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
                    canvas.drawCircle(cx, cy, radius + 2f, goldStroke)
                    canvas.drawBitmap(logoBitmap, null, dest, null)
                } else {
                    canvas.drawCircle(cx, cy, radius, navyLightFill)
                    canvas.drawCircle(cx, cy, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GOLD; style = Paint.Style.STROKE; strokeWidth = 2.2f })
                    canvas.drawCircle(cx, cy, radius - 4f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GOLD; style = Paint.Style.STROKE; strokeWidth = 0.8f })
                    val initials = schoolName.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifBlank { "CA" }
                    val crestPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.WHITE
                        textSize = 15f
                        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
                        textAlign = Paint.Align.CENTER
                    }
                    canvas.drawText(initials, cx, cy + 5.5f, crestPaint)
                }
            }

            fun drawSectionHeader(letter: String, title: String, topY: Float): Float {
                val badge = RectF(margin, topY, margin + 15f, topY + 15f)
                canvas.drawRoundRect(badge, 3f, 3f, navyFill)
                canvas.drawRoundRect(badge, 3f, 3f, goldStroke)
                canvas.drawText(
                    letter,
                    margin + 7.5f,
                    topY + 11f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.WHITE; textSize = 8f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); textAlign = Paint.Align.CENTER
                    }
                )
                canvas.drawText(title, margin + 21f, topY + 11f, secLabelPaint)
                val lineStart = margin + 26f + secLabelPaint.measureText(title)
                if (lineStart < pageWidth - margin) {
                    canvas.drawLine(lineStart, topY + 8f, pageWidth - margin, topY + 8f, Paint().apply { color = GOLD; strokeWidth = 0.9f })
                }
                return topY + 19f
            }

            fun drawPageFooter(pageNum: Int) {
                canvas.drawLine(margin, pageHeight - 24f, pageWidth - margin, pageHeight - 24f, borderStroke)
                canvas.drawText(
                    "$schoolName • Official Academic Record • Page $pageNum",
                    margin,
                    pageHeight - 12f,
                    mutedPaint
                )
                canvas.drawText(
                    "Accra, Ghana • ${SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date())}",
                    pageWidth - margin,
                    pageHeight - 12f,
                    Paint(mutedPaint).apply { textAlign = Paint.Align.RIGHT }
                )
            }

            fun drawCompactContinuationHeader(): Float {
                canvas.drawRect(0f, 0f, pageWidth.toFloat(), 44f, navyFill)
                canvas.drawRect(0f, 44f, pageWidth.toFloat(), 46.5f, goldFill)
                canvas.drawText(
                    "${schoolName.uppercase()} — ${report.studentName.ifBlank { "LEARNER REPORT" }} (${report.term} ${report.academicYear})",
                    margin,
                    27f,
                    Paint(schoolTitlePaint).apply { textSize = 11.5f }
                )
                return 60f
            }

            var y = 0f
            fun ensureSpace(needed: Float) {
                if (y + needed > pageHeight - 34f) {
                    drawPageFooter(pageNumber)
                    pdf.finishPage(page)
                    pageNumber++
                    page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                    canvas = page.canvas
                    y = drawCompactContinuationHeader()
                }
            }

            // SECTION A: SCHOOL HEADER
            canvas.drawRect(0f, 0f, pageWidth.toFloat(), 84f, navyFill)
            canvas.drawRect(0f, 84f, pageWidth.toFloat(), 87.5f, goldFill)
            drawCrest(margin + 24f, 34f, 22f)

            canvas.drawText(schoolName.uppercase().take(42), margin + 56f, 27f, schoolTitlePaint)
            val contactLine = listOf(city, phone, email).filter { it.isNotBlank() }.joinToString("  •  ")
            if (contactLine.isNotBlank()) {
                canvas.drawText(contactLine.take(78), margin + 56f, 41f, headerSubPaint)
            }
            val termMeta = "${SchoolLevel.fromKey(report.level).label.uppercase()} SCHOOL  •  ACADEMIC YEAR: ${report.academicYear}  •  ${report.term.uppercase()}"
            canvas.drawText(termMeta, margin + 56f, 54f, headerGoldBadgePaint)

            val subBanner = RectF(margin, 61f, pageWidth - margin, 78f)
            canvas.drawRoundRect(subBanner, 4f, 4f, navyLightFill)
            canvas.drawRoundRect(subBanner, 4f, 4f, goldStroke)
            canvas.drawText("OFFICIAL TERMINAL ACADEMIC REPORT CARD", pageWidth / 2f, 72.5f, bannerTitlePaint)

            y = 97f

            // Non-destructive verification notice if mismatch exists
            val integrity = ExamReportValidator.validate(report)
            if (!isBlankTemplate && !integrity.isConsistent) {
                val boxH = 16f + (integrity.issues.size.coerceAtMost(3) * 11f)
                val warnRect = RectF(margin, y, pageWidth - margin, y + boxH)
                canvas.drawRoundRect(warnRect, 4f, 4f, Paint().apply { color = Color.rgb(254, 243, 199) })
                canvas.drawRoundRect(warnRect, 4f, 4f, Paint().apply { color = AMBER_WARN; style = Paint.Style.STROKE; strokeWidth = 0.8f })
                canvas.drawText("CALCULATION VERIFICATION NOTICE:", margin + 8f, y + 11f, Paint(boldDark).apply { color = AMBER_WARN; textSize = 7.8f })
                integrity.issues.take(3).forEachIndexed { idx, msg ->
                    canvas.drawText("• ${msg.take(92)}", margin + 10f, y + 22f + idx * 11f, Paint(bodyDark).apply { textSize = 7.4f })
                }
                y += boxH + 7f
            }

            // SECTION B: LEARNER INFORMATION
            y = drawSectionHeader("B", "LEARNER INFORMATION", y)
            val infoRect = RectF(margin, y, pageWidth - margin, y + 58f)
            canvas.drawRoundRect(infoRect, 5f, 5f, rowTintFill)
            canvas.drawRoundRect(infoRect, 5f, 5f, borderStroke)
            val col1X = margin + 10f
            val col2X = margin + 225f
            val col3X = margin + 390f
            canvas.drawText("PUPIL'S FULL NAME", col1X, y + 12f, fieldLabelPaint)
            canvas.drawText(if (isBlankTemplate || report.studentName.isBlank()) "____________________________" else report.studentName.take(34), col1X, y + 24f, boldDark)
            canvas.drawText("ADMISSION NO.", col2X, y + 12f, fieldLabelPaint)
            canvas.drawText(if (isBlankTemplate || report.admissionNumber.isBlank()) "_________________" else report.admissionNumber.take(20), col2X, y + 24f, bodyDark)
            canvas.drawText("CLASS", col3X, y + 12f, fieldLabelPaint)
            canvas.drawText(if (isBlankTemplate || report.className.isBlank()) "________________" else report.className.take(18), col3X, y + 24f, bodyDark)

            canvas.drawLine(margin + 8f, y + 30f, pageWidth - margin - 8f, y + 30f, borderStroke)

            canvas.drawText("POSITION IN CLASS", col1X, y + 41f, fieldLabelPaint)
            canvas.drawText(if (isBlankTemplate || report.positionText.isBlank()) "____________" else report.positionText, col1X, y + 53f, Paint(boldDark).apply { color = NAVY })
            canvas.drawText("CORE AGGREGATE", col2X, y + 41f, fieldLabelPaint)
            canvas.drawText(if (isBlankTemplate || report.aggregate <= 0) "____" else report.aggregate.toString(), col2X, y + 53f, Paint(boldDark).apply { color = GREEN_GOOD })
            canvas.drawText("ATTENDANCE RECORD", col3X, y + 41f, fieldLabelPaint)
            canvas.drawText(if (isBlankTemplate || report.attendanceSummary.isBlank()) "____ / ____ days" else report.attendanceSummary.take(20), col3X, y + 53f, bodyDark)
            y += 66f

            // SECTION C: ACADEMIC PERFORMANCE TABLE (6 columns across 539f width)
            y = drawSectionHeader("C", "ACADEMIC PERFORMANCE", y)
            // Widths: 185 + 74 + 74 + 76 + 46 + 84 = 539f
            val xSubj = margin + 6f
            val xClassRight = margin + 185f + 68f
            val xExamRight = margin + 259f + 68f
            val xTotalRight = margin + 333f + 70f
            val xGradeCenter = margin + 409f + 23f
            val xRemark = margin + 455f + 6f

            canvas.drawRect(margin, y, pageWidth - margin, y + 22f, navyFill)
            canvas.drawText("Subject", xSubj, y + 14f, tableHdrPaint)
            canvas.drawText("Class (50%)", xClassRight, y + 14f, Paint(tableHdrPaint).apply { textAlign = Paint.Align.RIGHT })
            canvas.drawText("Exam (50%)", xExamRight, y + 14f, Paint(tableHdrPaint).apply { textAlign = Paint.Align.RIGHT })
            canvas.drawText("Total (100%)", xTotalRight, y + 14f, Paint(tableHdrPaint).apply { textAlign = Paint.Align.RIGHT; color = Color.rgb(253, 230, 138) })
            canvas.drawText("Grade", xGradeCenter, y + 14f, Paint(tableHdrPaint).apply { textAlign = Paint.Align.CENTER })
            canvas.drawText("Remarks", xRemark, y + 14f, tableHdrPaint)
            y += 22f

            report.scores.forEachIndexed { idx, s ->
                ensureSpace(22f)
                val rowBg = if (idx % 2 == 1) rowTintFill else whiteFill
                canvas.drawRect(margin, y, pageWidth - margin, y + 18.5f, rowBg)
                canvas.drawRect(margin, y, pageWidth - margin, y + 18.5f, borderStroke)

                val subjPaint = if (s.isCore) boldDark else bodyDark
                canvas.drawText(s.subjectName.take(30), xSubj, y + 12.5f, subjPaint)
                if (!isBlankTemplate) {
                    canvas.drawText("%.1f".format(s.classScore50), xClassRight, y + 12.5f, Paint(bodyDark).apply { textAlign = Paint.Align.RIGHT })
                    canvas.drawText("%.1f".format(s.examScore50), xExamRight, y + 12.5f, Paint(bodyDark).apply { textAlign = Paint.Align.RIGHT })
                    canvas.drawText("%.1f".format(s.totalScore), xTotalRight, y + 12.5f, Paint(boldDark).apply { textAlign = Paint.Align.RIGHT; color = NAVY })
                    val gColor = gradeColorInt(s.grade)
                    canvas.drawText("G${s.grade}", xGradeCenter, y + 12.5f, Paint(boldDark).apply { textAlign = Paint.Align.CENTER; color = gColor })
                    canvas.drawText(s.remark.take(15), xRemark, y + 12.5f, bodyDark)
                }
                y += 18.5f
            }

            // Totals Footer Row
            ensureSpace(24f)
            canvas.drawRect(margin, y, pageWidth - margin, y + 19f, goldSoftFill)
            canvas.drawRect(margin, y, pageWidth - margin, y + 19f, borderStroke)
            canvas.drawText("OVERALL TOTAL & AVERAGE (${report.scores.size} SUBJECTS)", xSubj, y + 13f, Paint(boldDark).apply { color = NAVY })
            if (!isBlankTemplate) {
                canvas.drawText("%.1f".format(report.overallTotal), xTotalRight, y + 13f, Paint(boldDark).apply { textAlign = Paint.Align.RIGHT; color = NAVY })
                canvas.drawText("Avg: ${"%.1f".format(report.averageScore)}%", xRemark, y + 13f, Paint(boldDark).apply { color = GOLD_DARK })
            }
            y += 27f

            // SECTION D: OVERALL PERFORMANCE SUMMARY & SUBJECT CHART
            ensureSpace(125f)
            y = drawSectionHeader("D", "OVERALL PERFORMANCE SUMMARY", y)
            val maxPossible = (report.scores.size * 100).coerceAtLeast(100)
            val cardW = (contentWidth - 18f) / 4f
            val metrics = listOf(
                "OVERALL TOTAL" to if (isBlankTemplate) "____ / $maxPossible" else "${"%.1f".format(report.overallTotal)} / $maxPossible",
                "OVERALL AVERAGE" to if (isBlankTemplate) "____ %" else "${"%.1f".format(report.averageScore)}%",
                "CORE AGGREGATE" to if (isBlankTemplate || report.aggregate <= 0) "____" else report.aggregate.toString(),
                "CLASS POSITION" to if (isBlankTemplate || report.positionText.isBlank()) "____" else report.positionText
            )
            metrics.forEachIndexed { idx, (label, value) ->
                val mx = margin + idx * (cardW + 6f)
                val rect = RectF(mx, y, mx + cardW, y + 32f)
                canvas.drawRoundRect(rect, 4f, 4f, rowTintFill)
                canvas.drawRoundRect(rect, 4f, 4f, borderStroke)
                canvas.drawText(label, mx + 7f, y + 11f, fieldLabelPaint)
                canvas.drawText(value.take(18), mx + 7f, y + 25f, Paint(boldDark).apply { color = NAVY; textSize = 9.5f })
            }
            y += 38f

            if (!isBlankTemplate && report.scores.isNotEmpty()) {
                val chartH = 14f + report.scores.size * 12f
                ensureSpace(chartH + 8f)
                val chartBox = RectF(margin, y, pageWidth - margin, y + chartH)
                canvas.drawRoundRect(chartBox, 4f, 4f, whiteFill)
                canvas.drawRoundRect(chartBox, 4f, 4f, borderStroke)
                canvas.drawText("Subject Score Comparison (0–100% • Gold Marker = 80% Benchmark)", margin + 8f, y + 10.5f, fieldLabelPaint)
                val barStartX = margin + 150f
                val barMaxW = contentWidth - 208f
                val benchX = barStartX + barMaxW * 0.8f
                report.scores.forEachIndexed { idx, s ->
                    val ry = y + 20f + idx * 12f
                    canvas.drawText(s.subjectName.take(26), margin + 8f, ry, Paint(bodyDark).apply { textSize = 7.5f })
                    val trackRect = RectF(barStartX, ry - 6.5f, barStartX + barMaxW, ry + 0.5f)
                    canvas.drawRoundRect(trackRect, 2f, 2f, Paint().apply { color = Color.rgb(241, 245, 249) })
                    val fillW = (barMaxW * (s.totalScore.coerceIn(0.0, 100.0).toFloat() / 100f)).coerceAtLeast(0f)
                    if (fillW > 0f) {
                        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = gradeColorInt(s.grade) }
                        canvas.drawRoundRect(RectF(barStartX, ry - 6.5f, barStartX + fillW, ry + 0.5f), 2f, 2f, barPaint)
                    }
                    canvas.drawLine(benchX, ry - 7.5f, benchX, ry + 1.5f, Paint().apply { color = GOLD; strokeWidth = 1f })
                    canvas.drawText("${"%.0f".format(s.totalScore)}% (G${s.grade})", barStartX + barMaxW + 6f, ry, Paint(boldDark).apply { textSize = 7.4f })
                }
                y += chartH + 8f
            }

            // SECTION E: LEARNER CONDUCT AND ATTITUDE
            ensureSpace(88f)
            y = drawSectionHeader("E", "LEARNER CONDUCT AND ATTITUDE", y)
            val conductRows = listOf(
                "Attitude & Conduct" to report.attitudeAndConduct,
                "Respectfulness" to report.respectfulness,
                "Punctuality" to report.punctuality,
                "Participation in Classroom Activities" to report.classroomParticipation,
                "Neatness & Responsibility" to report.neatnessAndResponsibility
            )
            val conductBoxH = conductRows.size * 14.5f + 6f
            val conductBox = RectF(margin, y, pageWidth - margin, y + conductBoxH)
            canvas.drawRoundRect(conductBox, 4f, 4f, whiteFill)
            canvas.drawRoundRect(conductBox, 4f, 4f, borderStroke)
            conductRows.forEachIndexed { idx, (attr, valStr) ->
                val cy = y + 13f + idx * 14.5f
                canvas.drawText(attr, margin + 8f, cy, Paint(boldDark).apply { textSize = 8f })
                val displayVal = if (isBlankTemplate || valStr.isBlank()) "________________________________________" else valStr.take(44)
                canvas.drawText(displayVal, pageWidth - margin - 8f, cy, Paint(bodyDark).apply { textSize = 8f; textAlign = Paint.Align.RIGHT })
                if (idx < conductRows.lastIndex) {
                    canvas.drawLine(margin + 6f, cy + 3.5f, pageWidth - margin - 6f, cy + 3.5f, borderStroke)
                }
            }
            y += conductBoxH + 8f

            // SECTION F: TEACHER'S COMMENTS AND RECOMMENDATIONS
            ensureSpace(78f)
            y = drawSectionHeader("F", "TEACHER'S COMMENTS AND RECOMMENDATIONS", y)
            val halfW = (contentWidth - 8f) / 2f
            drawCommentCard(
                canvas = canvas,
                left = margin,
                top = y,
                width = halfW,
                height = 54f,
                title = "Class Teacher's Remarks",
                content = if (isBlankTemplate) "" else report.teacherRemarks,
                borderPaint = borderStroke,
                bgPaint = rowTintFill,
                titlePaint = Paint(boldDark).apply { color = NAVY; textSize = 8.2f },
                textPaint = Paint(bodyDark).apply { textSize = 7.8f }
            )
            drawCommentCard(
                canvas = canvas,
                left = margin + halfW + 8f,
                top = y,
                width = halfW,
                height = 54f,
                title = "Head Teacher's Remarks",
                content = if (isBlankTemplate) "" else report.headTeacherRemarks,
                borderPaint = borderStroke,
                bgPaint = rowTintFill,
                titlePaint = Paint(boldDark).apply { color = NAVY; textSize = 8.2f },
                textPaint = Paint(bodyDark).apply { textSize = 7.8f }
            )
            y += 62f

            // SECTION G: SIGNATURES AND OFFICIAL VALIDATION
            ensureSpace(64f)
            y = drawSectionHeader("G", "SIGNATURES AND OFFICIAL VALIDATION", y)
            val sigBox = RectF(margin, y, pageWidth - margin, y + 48f)
            canvas.drawRoundRect(sigBox, 4f, 4f, rowTintFill)
            canvas.drawRoundRect(sigBox, 4f, 4f, borderStroke)

            val sCol1 = margin + 8f
            val sCol2 = margin + 185f
            val sCol3 = margin + 368f
            canvas.drawText("CLASS TEACHER'S NAME & SIGNATURE", sCol1, y + 11f, fieldLabelPaint)
            val ctLabel = listOf(report.classTeacherName, report.classTeacherSignature).filter { it.isNotBlank() }.joinToString(" • ")
            canvas.drawText(if (isBlankTemplate || ctLabel.isBlank()) "Name: ____________________" else ctLabel.take(28), sCol1, y + 25f, bodyDark)
            canvas.drawText("Sign: _____________________", sCol1, y + 40f, mutedPaint)

            canvas.drawText("HEAD TEACHER'S NAME & SIGNATURE", sCol2, y + 11f, fieldLabelPaint)
            val htLabel = listOf(report.headTeacherName, report.headTeacherSignature).filter { it.isNotBlank() }.joinToString(" • ")
            canvas.drawText(if (isBlankTemplate || htLabel.isBlank()) "Name: ____________________" else htLabel.take(28), sCol2, y + 25f, bodyDark)
            canvas.drawText("Sign: _____________________", sCol2, y + 40f, mutedPaint)

            canvas.drawText("ISSUANCE & OFFICIAL STAMP", sCol3, y + 11f, fieldLabelPaint)
            canvas.drawText("Date: ${if (isBlankTemplate || report.issuanceDate.isBlank()) "___/___/20__" else report.issuanceDate.take(18)}", sCol3, y + 23f, Paint(bodyDark).apply { textSize = 7.8f })
            val stampRect = RectF(sCol3, y + 27f, pageWidth - margin - 8f, y + 44f)
            canvas.drawRoundRect(stampRect, 3f, 3f, whiteFill)
            canvas.drawRoundRect(stampRect, 3f, 3f, goldStroke)
            canvas.drawText("OFFICIAL SCHOOL STAMP", stampRect.centerX(), y + 38.5f, Paint(fieldLabelPaint).apply { color = GOLD_DARK; textAlign = Paint.Align.CENTER })
            y += 56f

            // SECTION H: EXAMINATION GRADING SCALE
            ensureSpace(50f)
            y = drawSectionHeader("H", "EXAMINATION GRADING SCALE", y)
            val bandW = contentWidth / ExamGrading.scaleBands.size
            ExamGrading.scaleBands.forEachIndexed { idx, band ->
                val bx = margin + idx * bandW
                val bRect = RectF(bx, y, bx + bandW, y + 30f)
                canvas.drawRect(bRect, if (idx % 2 == 1) rowTintFill else whiteFill)
                canvas.drawRect(bRect, borderStroke)
                canvas.drawText(band.percentRangeLabel, bx + bandW / 2f, y + 11f, Paint(boldDark).apply { textSize = 7.6f; textAlign = Paint.Align.CENTER })
                canvas.drawText("${band.gradeCode} • ${band.interpretation}", bx + bandW / 2f, y + 23f, Paint(mutedPaint).apply { textSize = 7.1f; textAlign = Paint.Align.CENTER; color = gradeColorInt(band.grade) })
            }

            drawPageFooter(pageNumber)
            pdf.finishPage(page)
            FileOutputStream(file).use { pdf.writeTo(it) }
            pdf.close()

            val subject = if (isBlankTemplate) "$schoolName Blank Master Academic Report Card"
            else "${report.studentName} — $schoolName Terminal Report Card"
            shareFile(context, file, "application/pdf", subject)
            file
        }
    }

    private fun drawCommentCard(
        canvas: Canvas,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        title: String,
        content: String,
        borderPaint: Paint,
        bgPaint: Paint,
        titlePaint: Paint,
        textPaint: Paint
    ) {
        val rect = RectF(left, top, left + width, top + height)
        canvas.drawRoundRect(rect, 4f, 4f, bgPaint)
        canvas.drawRoundRect(rect, 4f, 4f, borderPaint)
        canvas.drawText(title, left + 7f, top + 12f, titlePaint)
        if (content.isBlank()) {
            canvas.drawLine(left + 7f, top + 28f, left + width - 7f, top + 28f, borderPaint)
            canvas.drawLine(left + 7f, top + 43f, left + width - 7f, top + 43f, borderPaint)
        } else {
            val wrapped = wrapWords(content, 44).take(3)
            wrapped.forEachIndexed { idx, line ->
                canvas.drawText(line, left + 7f, top + 24f + idx * 11f, textPaint)
            }
        }
    }

    private fun wrapWords(text: String, maxCharsPerLine: Int): List<String> {
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        words.forEach { word ->
            if (current.isEmpty()) {
                current.append(word)
            } else if (current.length + 1 + word.length <= maxCharsPerLine) {
                current.append(' ').append(word)
            } else {
                lines.add(current.toString())
                current = StringBuilder(word)
            }
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        return lines
    }

    private fun gradeColorInt(grade: Int): Int = when {
        grade <= 2 -> GREEN_GOOD
        grade <= 4 -> NAVY
        grade == 5 -> AMBER_WARN
        else -> RED_FAIL
    }

    private fun loadLogoBitmap(context: Context, uriStr: String?): Bitmap? {
        if (uriStr.isNullOrBlank()) return null
        return runCatching {
            val uri = Uri.parse(uriStr)
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        }.getOrNull()
    }
}
