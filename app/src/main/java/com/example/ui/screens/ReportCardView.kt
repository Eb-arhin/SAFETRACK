package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.CefelixAcademyTemplate
import com.example.data.model.ExamGrading
import com.example.data.model.ExamReport
import com.example.data.model.ExamReportValidator
import com.example.data.model.ExamSubjectScore
import com.example.data.model.School
import com.example.data.model.SchoolClass
import com.example.data.model.SchoolLevel
import com.example.data.model.Student
import com.example.ui.theme.DelayAmber100
import com.example.ui.theme.DelayAmber700
import com.example.ui.theme.EmergencyRed600
import com.example.ui.theme.ReportBorder
import com.example.ui.theme.ReportGold
import com.example.ui.theme.ReportGoldDark
import com.example.ui.theme.ReportGoldSoft
import com.example.ui.theme.ReportNavy
import com.example.ui.theme.ReportNavyLight
import com.example.ui.theme.ReportRowTint
import com.example.ui.theme.SafeBlue700
import com.example.ui.theme.SafeGreen700

@Composable
fun SchoolBrandCrest(
    schoolName: String,
    logoUri: String?,
    sizeDp: Int = 56
) {
    if (!logoUri.isNullOrBlank()) {
        AsyncImage(
            model = logoUri,
            contentDescription = "$schoolName Logo",
            modifier = Modifier
                .size(sizeDp.dp)
                .clip(CircleShape)
                .border(2.dp, ReportGold, CircleShape),
            contentScale = ContentScale.Crop
        )
    } else {
        val initials = schoolName.split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
            .ifBlank { CefelixAcademyTemplate.CREST_INITIALS }
        Box(
            modifier = Modifier
                .size(sizeDp.dp)
                .clip(CircleShape)
                .background(ReportNavy)
                .border(2.5.dp, ReportGold, CircleShape)
                .padding(4.dp)
                .border(1.dp, ReportGold.copy(alpha = 0.65f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = initials,
                    fontFamily = FontFamily.Serif,
                    fontSize = (sizeDp * 0.34f).sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TerminalReportCardView(
    report: ExamReport,
    school: School?,
    onExportPdf: (() -> Unit)? = null,
    onExportBlankMasterPdf: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val logoToShow = report.schoolLogoUri ?: school?.logoUri
    val schoolNameToShow = report.schoolName.ifBlank { school?.name ?: CefelixAcademyTemplate.SCHOOL_NAME }
    val cityToShow = report.schoolCity.ifBlank { school?.city.orEmpty() }
    val phoneToShow = report.schoolPhone.ifBlank { school?.phone.orEmpty() }
    val emailToShow = report.schoolEmail.ifBlank { school?.email.orEmpty() }
    val contactLine = listOf(cityToShow, phoneToShow, emailToShow)
        .filter { it.isNotBlank() }
        .joinToString("  •  ")
    val integrity = remember(report) { ExamReportValidator.validate(report) }
    val tableScroll = rememberScrollState()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("terminal_report_card_view"),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.5.dp, ReportNavy.copy(alpha = 0.3f)),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            // SECTION A: SCHOOL HEADER (Navy Blue, White Text, Subtle Gold Detailing)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ReportNavy)
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    SchoolBrandCrest(schoolName = schoolNameToShow, logoUri = logoToShow, sizeDp = 62)
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = schoolNameToShow.uppercase(),
                            fontFamily = FontFamily.Serif,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 0.8.sp
                        )
                        if (contactLine.isNotBlank()) {
                            Text(
                                text = contactLine,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.88f)
                            )
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = ReportGold.copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, ReportGold)
                            ) {
                                Text(
                                    text = "${SchoolLevel.fromKey(report.level).label.uppercase()} SCHOOL  •  ${report.academicYear}  •  ${report.term.uppercase()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFFDE68A),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ReportNavyLight, RoundedCornerShape(6.dp))
                        .border(1.dp, ReportGold.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .padding(vertical = 6.dp, horizontal = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "OFFICIAL TERMINAL ACADEMIC REPORT CARD",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 1.2.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
            // Gold Accent Divider Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(ReportGold)
            )

            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Data Integrity Flag Banner (non-destructive review notice if mismatch exists)
                if (!integrity.isConsistent) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = DelayAmber100,
                        border = BorderStroke(1.dp, DelayAmber700.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(Icons.Filled.Warning, contentDescription = "Calculation review required", tint = DelayAmber700, modifier = Modifier.size(18.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    "Calculation Verification Notice (For Human Review)",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = DelayAmber700
                                )
                                integrity.issues.forEach { issue ->
                                    Text("• $issue", style = MaterialTheme.typography.bodySmall, color = Color(0xFF78350F))
                                }
                            }
                        }
                    }
                }

                // SECTION B: LEARNER INFORMATION
                ReportSectionHeader(letter = "B", title = "LEARNER INFORMATION")
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = ReportRowTint,
                    border = BorderStroke(1.dp, ReportBorder)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            LearnerField("PUPIL'S FULL NAME", report.studentName, Modifier.weight(1.4f), bold = true)
                            LearnerField("ADMISSION NO.", report.admissionNumber, Modifier.weight(1f))
                            LearnerField("CLASS", report.className, Modifier.weight(1f))
                        }
                        HorizontalDivider(thickness = 0.75.dp, color = ReportBorder)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            LearnerField("POSITION IN CLASS", report.positionText.ifBlank { "Pending" }, Modifier.weight(1.4f), bold = true, accent = ReportNavy)
                            LearnerField("CORE AGGREGATE", if (report.aggregate > 0) report.aggregate.toString() else "Pending", Modifier.weight(1f), bold = true, accent = SafeGreen700)
                            LearnerField("ATTENDANCE RECORD", report.attendanceSummary.ifBlank { "Pending" }, Modifier.weight(1f))
                        }
                        if (report.aggregateBreakdown.isNotBlank()) {
                            Text(
                                text = "Core Subjects Breakdown: ${report.aggregateBreakdown}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF475569)
                            )
                        }
                    }
                }

                // SECTION C: ACADEMIC PERFORMANCE TABLE
                ReportSectionHeader(letter = "C", title = "ACADEMIC PERFORMANCE")
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, ReportBorder, RoundedCornerShape(8.dp))
                        .clip(RoundedCornerShape(8.dp))
                        .horizontalScroll(tableScroll)
                ) {
                    Row(
                        modifier = Modifier
                            .background(ReportNavy)
                            .padding(vertical = 10.dp, horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Subject", Modifier.width(196.dp), color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text("Class Assessment (50%)", Modifier.width(116.dp), color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                        Text("Examination (50%)", Modifier.width(108.dp), color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                        Text("Total Score (100%)", Modifier.width(108.dp), color = Color(0xFFFDE68A), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                        Text("Grade", Modifier.width(64.dp), color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        Text("Remarks", Modifier.width(98.dp), color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }

                    if (report.scores.isEmpty()) {
                        Box(modifier = Modifier.width(690.dp).background(Color.White).padding(16.dp)) {
                            Text(
                                "No subject scores entered yet. Enter scores above or export a Blank Master Template.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF64748B)
                            )
                        }
                    } else {
                        report.scores.forEachIndexed { idx, s ->
                            val rowBg = if (idx % 2 == 1) ReportRowTint else Color.White
                            Row(
                                modifier = Modifier
                                    .background(rowBg)
                                    .padding(vertical = 9.dp, horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (s.isCore) "${s.subjectName} •" else s.subjectName,
                                    modifier = Modifier.width(196.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (s.isCore) FontWeight.SemiBold else FontWeight.Normal,
                                    color = Color(0xFF0F172A)
                                )
                                Text(
                                    text = "%.1f".format(s.classScore50),
                                    modifier = Modifier.width(116.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.End,
                                    color = Color(0xFF1E293B)
                                )
                                Text(
                                    text = "%.1f".format(s.examScore50),
                                    modifier = Modifier.width(108.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.End,
                                    color = Color(0xFF1E293B)
                                )
                                Text(
                                    text = "%.1f".format(s.totalScore),
                                    modifier = Modifier.width(108.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.End,
                                    color = ReportNavy
                                )
                                Box(modifier = Modifier.width(64.dp), contentAlignment = Alignment.Center) {
                                    val gradeColor = gradeTint(s.grade)
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = gradeColor.copy(alpha = 0.12f),
                                        border = BorderStroke(0.75.dp, gradeColor.copy(alpha = 0.45f))
                                    ) {
                                        Text(
                                            text = "G${s.grade}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = gradeColor,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = s.remark,
                                    modifier = Modifier.width(98.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF334155)
                                )
                            }
                            HorizontalDivider(thickness = 0.6.dp, color = ReportBorder)
                        }
                        Row(
                            modifier = Modifier
                                .background(ReportGoldSoft)
                                .padding(vertical = 9.dp, horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "TOTAL / OVERALL AVERAGE (${report.scores.size} SUBJECTS)",
                                Modifier.width(420.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = ReportNavy
                            )
                            Text(
                                text = "%.1f".format(report.overallTotal),
                                Modifier.width(108.dp),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.End,
                                color = ReportNavy
                            )
                            Text(
                                text = "${"%.1f".format(report.averageScore)}%",
                                Modifier.width(162.dp),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                color = ReportGoldDark
                            )
                        }
                    }
                }

                // SECTION D: OVERALL PERFORMANCE SUMMARY & SUBJECT CHART
                ReportSectionHeader(letter = "D", title = "OVERALL PERFORMANCE SUMMARY")
                val maxPossible = (report.scores.size * 100).coerceAtLeast(100)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    maxItemsInEachRow = 4
                ) {
                    SummaryMetricTile("Overall Total", "${"%.1f".format(report.overallTotal)} / $maxPossible", ReportNavy, Modifier.weight(1f))
                    SummaryMetricTile("Overall Average", "${"%.1f".format(report.averageScore)}%", SafeBlue700, Modifier.weight(1f))
                    SummaryMetricTile("Core Aggregate", if (report.aggregate > 0) "${report.aggregate}" else "—", SafeGreen700, Modifier.weight(1f))
                    SummaryMetricTile("Class Position", report.positionText.ifBlank { "—" }, ReportGoldDark, Modifier.weight(1f))
                }

                OverallAchievementMeter(averagePercent = report.averageScore)

                if (report.scores.isNotEmpty()) {
                    SubjectPerformanceComparisonChart(scores = report.scores)
                }

                // SECTION E: LEARNER CONDUCT AND ATTITUDE
                ReportSectionHeader(letter = "E", title = "LEARNER CONDUCT AND ATTITUDE")
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, ReportBorder)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ConductAttributeRow("Attitude & Conduct", report.attitudeAndConduct)
                        HorizontalDivider(thickness = 0.5.dp, color = ReportBorder)
                        ConductAttributeRow("Respectfulness", report.respectfulness)
                        HorizontalDivider(thickness = 0.5.dp, color = ReportBorder)
                        ConductAttributeRow("Punctuality", report.punctuality)
                        HorizontalDivider(thickness = 0.5.dp, color = ReportBorder)
                        ConductAttributeRow("Participation in Classroom Activities", report.classroomParticipation)
                        HorizontalDivider(thickness = 0.5.dp, color = ReportBorder)
                        ConductAttributeRow("Neatness & Responsibility", report.neatnessAndResponsibility)
                    }
                }

                // SECTION F: TEACHER'S COMMENTS AND RECOMMENDATIONS
                ReportSectionHeader(letter = "F", title = "TEACHER'S COMMENTS AND RECOMMENDATIONS")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CommentBoxCard(
                        title = "Class Teacher's Remarks",
                        guidance = "Academic performance, strengths, areas for improvement & recommendations",
                        content = report.teacherRemarks,
                        modifier = Modifier.weight(1f)
                    )
                    CommentBoxCard(
                        title = "Head Teacher's Remarks",
                        guidance = "Overall assessment, academic progress & encouragement",
                        content = report.headTeacherRemarks,
                        modifier = Modifier.weight(1f)
                    )
                }

                // SECTION G: SIGNATURES AND OFFICIAL VALIDATION
                ReportSectionHeader(letter = "G", title = "SIGNATURES AND OFFICIAL VALIDATION")
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = ReportRowTint.copy(alpha = 0.6f),
                    border = BorderStroke(1.dp, ReportBorder)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        SignatureBlock(
                            roleTitle = "Class Teacher",
                            nameValue = report.classTeacherName,
                            signatureValue = report.classTeacherSignature,
                            modifier = Modifier.weight(1f)
                        )
                        SignatureBlock(
                            roleTitle = "Head Teacher",
                            nameValue = report.headTeacherName,
                            signatureValue = report.headTeacherSignature,
                            modifier = Modifier.weight(1f)
                        )
                        Column(
                            modifier = Modifier.weight(0.95f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("DATE OF ISSUANCE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = ReportNavy)
                            Text(
                                text = report.issuanceDate.ifBlank { "____ / ____ / 20__" },
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF0F172A)
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .border(1.dp, ReportGold, RoundedCornerShape(6.dp))
                                    .background(Color.White, RoundedCornerShape(6.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "OFFICIAL SCHOOL STAMP",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 9.sp,
                                    color = ReportGoldDark,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                // SECTION H: EXAMINATION GRADING SCALE
                ReportSectionHeader(letter = "H", title = "EXAMINATION GRADING SCALE")
                GradingScaleSummaryCard()

                if (onExportPdf != null || onExportBlankMasterPdf != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (onExportPdf != null) {
                            Button(
                                onClick = onExportPdf,
                                colors = ButtonDefaults.buttonColors(containerColor = ReportNavy),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("export_report_card_pdf_button")
                            ) {
                                Icon(Icons.Filled.PictureAsPdf, contentDescription = null)
                                Spacer(Modifier.size(8.dp))
                                Text("Export A4 PDF")
                            }
                        }
                        if (onExportBlankMasterPdf != null) {
                            OutlinedButton(
                                onClick = onExportBlankMasterPdf,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("export_blank_master_pdf_button")
                            ) {
                                Icon(Icons.Filled.Description, contentDescription = null)
                                Spacer(Modifier.size(6.dp))
                                Text("Blank Master PDF")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportSectionHeader(letter: String, title: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(ReportNavy, RoundedCornerShape(4.dp))
                .border(1.dp, ReportGold, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(letter, style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
        }
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = ReportNavy,
            letterSpacing = 0.6.sp
        )
        HorizontalDivider(modifier = Modifier.weight(1f), thickness = 1.dp, color = ReportGold.copy(alpha = 0.55f))
    }
}

@Composable
private fun LearnerField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    bold: Boolean = false,
    accent: Color = Color(0xFF0F172A)
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = Color(0xFF64748B), fontWeight = FontWeight.SemiBold)
        Text(
            text = value.ifBlank { "—" },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
            color = accent
        )
    }
}

@Composable
private fun SummaryMetricTile(
    title: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = ReportRowTint,
        border = BorderStroke(1.dp, ReportBorder)
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = Color(0xFF475569))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun OverallAchievementMeter(averagePercent: Double) {
    val clamped = averagePercent.coerceIn(0.0, 100.0)
    val (grade, label) = ExamGrading.gradeAndRemark(clamped)
    val meterColor = gradeTint(grade)
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.White,
        border = BorderStroke(1.dp, ReportBorder)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = meterColor, modifier = Modifier.size(16.dp))
                    Text("Overall Achievement Band", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = ReportNavy)
                }
                Text(
                    text = "${"%.1f".format(clamped)}% • G$grade ($label)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = meterColor
                )
            }
            Canvas(modifier = Modifier.fillMaxWidth().height(12.dp)) {
                drawRoundRect(
                    color = Color(0xFFE2E8F0),
                    size = size,
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                )
                val fillW = (size.width * (clamped.toFloat() / 100f)).coerceAtLeast(0f)
                if (fillW > 0f) {
                    drawRoundRect(
                        color = meterColor,
                        size = Size(fillW, size.height),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                    )
                }
            }
        }
    }
}

@Composable
private fun SubjectPerformanceComparisonChart(scores: List<ExamSubjectScore>) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.White,
        border = BorderStroke(1.dp, ReportBorder)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Subject Performance Comparison (0–100%)",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = ReportNavy
            )
            scores.forEach { score ->
                val pct = score.totalScore.coerceIn(0.0, 100.0)
                val barColor = gradeTint(score.grade)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = score.subjectName,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF1E293B),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(138.dp)
                    )
                    Canvas(modifier = Modifier.weight(1f).height(11.dp)) {
                        drawRoundRect(
                            color = Color(0xFFF1F5F9),
                            size = size,
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )
                        val barW = (size.width * (pct.toFloat() / 100f)).coerceAtLeast(0f)
                        if (barW > 0f) {
                            drawRoundRect(
                                color = barColor,
                                size = Size(barW, size.height),
                                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                            )
                        }
                        // 80% benchmark line
                        val markX = size.width * 0.8f
                        drawLine(
                            color = ReportGold,
                            start = Offset(markX, 0f),
                            end = Offset(markX, size.height),
                            strokeWidth = 1.5.dp.toPx()
                        )
                    }
                    Text(
                        text = "${"%.0f".format(pct)}% (G${score.grade})",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = barColor,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(66.dp)
                    )
                }
            }
            Text(
                "Gold vertical marker indicates 80% (G1 Excellent) benchmark.",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF64748B)
            )
        }
    }
}

@Composable
private fun ConductAttributeRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = Color(0xFF0F172A), modifier = Modifier.weight(1f))
        Text(
            text = value.ifBlank { "__________________  (Pending)" },
            style = MaterialTheme.typography.bodySmall,
            fontStyle = if (value.isBlank()) FontStyle.Italic else FontStyle.Normal,
            fontWeight = if (value.isBlank()) FontWeight.Normal else FontWeight.SemiBold,
            color = if (value.isBlank()) Color(0xFF94A3B8) else ReportNavy,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun CommentBoxCard(
    title: String,
    guidance: String,
    content: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = Color.White,
        border = BorderStroke(1.dp, ReportBorder)
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = ReportNavy)
            Text(guidance, style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = Color(0xFF64748B))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .background(ReportRowTint.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                    .padding(8.dp)
            ) {
                Text(
                    text = content.ifBlank { "Pending teacher/head remarks..." },
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = if (content.isBlank()) FontStyle.Italic else FontStyle.Normal,
                    color = if (content.isBlank()) Color(0xFF94A3B8) else Color(0xFF0F172A)
                )
            }
        }
    }
}

@Composable
private fun SignatureBlock(
    roleTitle: String,
    nameValue: String,
    signatureValue: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(roleTitle.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = ReportNavy)
        Text(
            text = "Name: ${nameValue.ifBlank { "________________" }}",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF334155)
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = signatureValue.ifBlank { "____________________" },
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF0F172A)
        )
        HorizontalDivider(thickness = 1.dp, color = ReportNavy.copy(alpha = 0.5f))
        Text("Signature", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = Color(0xFF64748B))
    }
}

private fun gradeTint(grade: Int): Color = when {
    grade <= 2 -> SafeGreen700
    grade <= 4 -> ReportNavy
    grade == 5 -> DelayAmber700
    else -> EmergencyRed600
}

@Composable
fun GradingScaleSummaryCard(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = ReportRowTint,
        border = BorderStroke(1.dp, ReportBorder)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ReportNavy, RoundedCornerShape(4.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text("Score Range", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color.White)
                Text("Grade", Modifier.weight(0.7f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color(0xFFFDE68A), textAlign = TextAlign.Center)
                Text("Interpretation", Modifier.weight(1.2f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color.White)
            }
            ExamGrading.scaleBands.forEachIndexed { index, band ->
                val bg = if (index % 2 == 1) Color.White else Color.Transparent
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(bg)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(band.percentRangeLabel, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = Color(0xFF0F172A))
                    Text(band.gradeCode, Modifier.weight(0.7f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = gradeTint(band.grade), textAlign = TextAlign.Center)
                    Text(band.interpretation, Modifier.weight(1.2f), style = MaterialTheme.typography.bodySmall, color = Color(0xFF334155))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditStudentDialog(
    student: Student,
    classes: List<SchoolClass>,
    onDismiss: () -> Unit,
    onSave: (
        admissionNumber: String,
        fullName: String,
        schoolClass: SchoolClass,
        emergencyName: String,
        emergencyPhone: String,
        medicalNotes: String,
        approvedDestinations: List<String>,
        authorisedPickups: List<String>
    ) -> Unit
) {
    var fullName by remember(student.id) { mutableStateOf(student.fullName) }
    var adm by remember(student.id) { mutableStateOf(student.admissionNumber) }
    var selectedClass by remember(student.id) {
        mutableStateOf(
            classes.firstOrNull { it.id == student.classId }
                ?: SchoolClass(id = student.classId, schoolId = student.schoolId, name = student.className, academicYear = student.academicYear)
        )
    }
    var emName by remember(student.id) { mutableStateOf(student.emergencyContactName.orEmpty()) }
    var emPhone by remember(student.id) { mutableStateOf(student.emergencyContactPhone.orEmpty()) }
    var medical by remember(student.id) { mutableStateOf(student.medicalNotes.orEmpty()) }
    var destCsv by remember(student.id) { mutableStateOf(student.approvedDestinations.joinToString(", ")) }
    var pickupCsv by remember(student.id) { mutableStateOf(student.authorisedPickups.joinToString(", ")) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Pupil Details") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(value = fullName, onValueChange = { fullName = it }, label = { Text("Full Name *") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = adm, onValueChange = { adm = it }, label = { Text("Admission Number *") }, modifier = Modifier.fillMaxWidth())
                if (classes.isNotEmpty()) {
                    Text("Class", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        classes.forEach { cls ->
                            FilterChip(selected = selectedClass.id == cls.id, onClick = { selectedClass = cls }, label = { Text(cls.name) })
                        }
                    }
                }
                OutlinedTextField(value = destCsv, onValueChange = { destCsv = it }, label = { Text("Approved Destinations (comma-separated)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = pickupCsv, onValueChange = { pickupCsv = it }, label = { Text("Authorised Pickups (comma-separated)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = emName, onValueChange = { emName = it }, label = { Text("Emergency Contact Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = emPhone, onValueChange = { emPhone = it }, label = { Text("Emergency Phone") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = medical, onValueChange = { medical = it }, label = { Text("Medical Notes") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        adm,
                        fullName,
                        selectedClass,
                        emName,
                        emPhone,
                        medical,
                        destCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                        pickupCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    )
                    onDismiss()
                },
                enabled = fullName.trim().length >= 2 && adm.trim().isNotEmpty()
            ) { Text("Save Changes") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun EditSchoolBrandingDialog(
    school: School,
    onDismiss: () -> Unit,
    onSave: (name: String, city: String, phone: String, email: String, logoUri: String?) -> Unit
) {
    var name by remember(school.id) { mutableStateOf(school.name) }
    var city by remember(school.id) { mutableStateOf(school.city) }
    var phone by remember(school.id) { mutableStateOf(school.phone.orEmpty()) }
    var email by remember(school.id) { mutableStateOf(school.email.orEmpty()) }
    var logoUri by remember(school.id) { mutableStateOf(school.logoUri) }

    val logoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) logoUri = uri.toString() }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.School, contentDescription = null) },
        title = { Text("Edit School Name & Logo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SchoolBrandCrest(schoolName = name, logoUri = logoUri, sizeDp = 52)
                    OutlinedButton(
                        onClick = { logoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                    ) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text(if (logoUri.isNullOrBlank()) "Pick School Logo" else "Change Logo")
                    }
                }
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("School Name *") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = city, onValueChange = { city = it }, label = { Text("City / Town") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("School Phone") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("School Email") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(name, city, phone, email, logoUri)
                    onDismiss()
                },
                enabled = name.trim().length >= 2
            ) { Text("Save Branding") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
