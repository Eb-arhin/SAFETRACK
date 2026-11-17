package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.ExamGrading
import com.example.data.model.ExamReport
import com.example.data.model.SchoolClass
import com.example.data.model.Subject
import com.example.ui.theme.DelayAmber700
import com.example.ui.theme.EmergencyRed600
import com.example.ui.theme.SafeBlue100
import com.example.ui.theme.SafeBlue600
import com.example.ui.theme.SafeBlue700
import com.example.ui.theme.SafeBlue900
import com.example.ui.theme.SafeGreen600
import com.example.ui.theme.SafeGreen700
import kotlin.math.roundToInt

private data class SubjectStat(
    val subjectName: String,
    val averageScore: Double,
    val minScore: Double,
    val maxScore: Double,
    val entries: Int,
    val passRatePct: Int
)

private data class TermPoint(
    val termLabel: String,
    val overallAvg: Double,
    val reportCount: Int,
    val perSubjectAvg: Map<String, Double>
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PerformanceAnalyticsDashboard(
    classes: List<SchoolClass>,
    subjects: List<Subject>,
    examReports: List<ExamReport>,
    modifier: Modifier = Modifier
) {
    var selectedClassId by remember { mutableStateOf("all") }
    var selectedTerm by remember { mutableStateOf("All Terms") }
    var focusedSubject by remember { mutableStateOf<String?>(null) }

    val termOptions = listOf("All Terms", "Term 1", "Term 2", "Term 3")

    val classFilteredReports = remember(examReports, selectedClassId) {
        if (selectedClassId == "all") examReports
        else examReports.filter { it.classId == selectedClassId }
    }
    val filteredReports = remember(classFilteredReports, selectedTerm) {
        if (selectedTerm == "All Terms") classFilteredReports
        else classFilteredReports.filter { it.term == selectedTerm }
    }

    val allScores = remember(filteredReports) {
        filteredReports.flatMap { it.scores }
    }

    val subjectStats = remember(allScores, subjects) {
        val grouped = allScores.groupBy { it.subjectName.trim().ifBlank { "Subject" } }
        val statsFromScores = grouped.map { (name, items) ->
            val avg = items.map { it.totalScore }.average()
            val min = items.minOf { it.totalScore }
            val max = items.maxOf { it.totalScore }
            val passPct = if (items.isNotEmpty()) {
                (items.count { it.grade <= 5 } * 100.0 / items.size).roundToInt()
            } else 0
            SubjectStat(
                subjectName = name,
                averageScore = avg,
                minScore = min,
                maxScore = max,
                entries = items.size,
                passRatePct = passPct
            )
        }.sortedByDescending { it.averageScore }
        statsFromScores
    }

    val gradeCounts = remember(allScores) {
        (1..6).associateWith { grade -> allScores.count { it.grade == grade } }
    }

    val termTrendPoints = remember(classFilteredReports) {
        listOf("Term 1", "Term 2", "Term 3").map { term ->
            val termReports = classFilteredReports.filter { it.term == term }
            val overallAvg = if (termReports.isNotEmpty()) termReports.map { it.averageScore }.average() else 0.0
            val perSub = termReports
                .flatMap { it.scores }
                .groupBy { it.subjectName }
                .mapValues { (_, list) -> list.map { it.totalScore }.average() }
            TermPoint(
                termLabel = term,
                overallAvg = overallAvg,
                reportCount = termReports.size,
                perSubjectAvg = perSub
            )
        }
    }

    val overallAvg = if (filteredReports.isNotEmpty()) filteredReports.map { it.averageScore }.average() else 0.0
    val totalGradesCount = allScores.size.coerceAtLeast(1)
    val passRate = if (allScores.isNotEmpty()) {
        (allScores.count { it.grade <= 5 } * 100.0 / totalGradesCount).roundToInt()
    } else 0
    val topSubjectStat = subjectStats.firstOrNull()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("performance_analytics_dashboard"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Filter Header Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, SafeBlue700.copy(alpha = 0.25f)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.Analytics, contentDescription = null, tint = SafeBlue700)
                    Column {
                        Text("Student Performance Trends & Analytics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "Subject average scores, Grade 1–6 distributions, and multi-term academic trends.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (classes.isNotEmpty()) {
                    Text("Filter by Class", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilterChip(
                            selected = selectedClassId == "all",
                            onClick = { selectedClassId = "all" },
                            label = { Text("All Classes (${examReports.size})") }
                        )
                        classes.forEach { cls ->
                            val count = examReports.count { it.classId == cls.id }
                            FilterChip(
                                selected = selectedClassId == cls.id,
                                onClick = { selectedClassId = cls.id },
                                label = { Text("${cls.name} ($count)") }
                            )
                        }
                    }
                }

                Text("Filter by Term", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    termOptions.forEach { term ->
                        FilterChip(
                            selected = selectedTerm == term,
                            onClick = { selectedTerm = term },
                            label = { Text(term) }
                        )
                    }
                }
            }
        }

        // KPI Summary Cards
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            maxItemsInEachRow = 4
        ) {
            KpiMetricCard(
                label = "Graded Reports",
                value = filteredReports.size.toString(),
                icon = Icons.Filled.BarChart,
                accentColor = SafeBlue700,
                modifier = Modifier.weight(1f)
            )
            KpiMetricCard(
                label = "Mean Score",
                value = if (filteredReports.isNotEmpty()) "${"%.1f".format(overallAvg)}%" else "—",
                icon = Icons.Filled.TrendingUp,
                accentColor = SafeGreen600,
                modifier = Modifier.weight(1f)
            )
            KpiMetricCard(
                label = "Pass Rate (G1–5)",
                value = if (allScores.isNotEmpty()) "$passRate%" else "—",
                icon = Icons.Filled.ShowChart,
                accentColor = if (passRate >= 70) SafeGreen600 else DelayAmber700,
                modifier = Modifier.weight(1f)
            )
            KpiMetricCard(
                label = "Top Subject",
                value = topSubjectStat?.subjectName?.take(12) ?: "—",
                icon = Icons.Filled.Analytics,
                accentColor = SafeBlue700,
                modifier = Modifier.weight(1f)
            )
        }

        if (filteredReports.isEmpty()) {
            EmptyStateCard(
                title = "No Graded Reports for Selected Filter",
                subtitle = "Save pupil scores on the Reports slide to populate live subject averages, grade distributions, and term trend charts."
            )
        } else {
            // 1. Subject Average Scores Bar Chart
            Card(
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Average Scores Across Subjects ($selectedTerm)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "Dashed guides mark 50% Pass & 80% Excellence thresholds.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    val displayedStats = subjectStats.take(8)
                    val activeFocus = displayedStats.firstOrNull { it.subjectName == focusedSubject } ?: displayedStats.firstOrNull()

                    SubjectAverageBarChart(
                        stats = displayedStats,
                        focusedName = activeFocus?.subjectName,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                            .testTag("subject_average_bar_chart")
                    )

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        displayedStats.forEach { stat ->
                            FilterChip(
                                selected = activeFocus?.subjectName == stat.subjectName,
                                onClick = { focusedSubject = stat.subjectName },
                                label = { Text("${stat.subjectName}: ${"%.1f".format(stat.averageScore)}%") }
                            )
                        }
                    }

                    activeFocus?.let { f ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = SafeBlue100.copy(alpha = 0.35f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(f.subjectName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text(
                                        "Mean ${"%.1f".format(f.averageScore)}% • Range ${"%.0f".format(f.minScore)}–${"%.0f".format(f.maxScore)}% • ${f.entries} pupil(s)",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Text(
                                    "Pass ${f.passRatePct}%",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = SafeGreen700,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // 2. Grade Distribution Over the Term (Grades 1–6)
            Card(
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Grade Distribution (Grades 1–6)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Distribution of all ${allScores.size} graded subject entries across the term.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    GradeDistributionChart(
                        gradeCounts = gradeCounts,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(175.dp)
                            .testTag("grade_distribution_chart")
                    )

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ExamGrading.scaleBands.forEach { band ->
                            val count = gradeCounts[band.grade] ?: 0
                            val pct = (count * 100.0 / totalGradesCount).roundToInt()
                            val badgeColor = gradeColor(band.grade)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = badgeColor.copy(alpha = 0.14f),
                                border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(modifier = Modifier.size(8.dp).background(badgeColor, CircleShape))
                                    Text(
                                        text = "G${band.grade} ${band.interpretation}: $count ($pct%)",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = badgeColor,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. Multi-Term Performance Trend Chart (Term 1 -> Term 2 -> Term 3)
            Card(
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Term-over-Term Performance Trend", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Compares class overall mean score and top subjects across Term 1, Term 2, and Term 3.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val trackedSubjectNames = subjectStats.take(2).map { it.subjectName }
                    TermTrendLineChart(
                        termPoints = termTrendPoints,
                        trackedSubjects = trackedSubjectNames,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp)
                            .testTag("term_trend_line_chart")
                    )

                    // Legend & Term Summary Row
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LegendDot(color = SafeBlue900, label = "Overall Class Mean")
                        if (trackedSubjectNames.isNotEmpty()) {
                            LegendDot(color = SafeGreen600, label = trackedSubjectNames[0])
                        }
                        if (trackedSubjectNames.size > 1) {
                            LegendDot(color = DelayAmber700, label = trackedSubjectNames[1])
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        termTrendPoints.forEach { tp ->
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(tp.termLabel, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = if (tp.reportCount > 0) "${"%.1f".format(tp.overallAvg)}%" else "—",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (tp.reportCount > 0) SafeBlue700 else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text("${tp.reportCount} report(s)", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

private fun gradeColor(grade: Int): Color = when (grade) {
    1 -> SafeGreen700
    2 -> SafeGreen600
    3 -> SafeBlue700
    4 -> SafeBlue600
    5 -> DelayAmber700
    else -> EmergencyRed600
}

@Composable
private fun SubjectAverageBarChart(
    stats: List<SubjectStat>,
    focusedName: String?,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
        val passLineColor = DelayAmber700.copy(alpha = 0.55f)
        val excelLineColor = SafeGreen600.copy(alpha = 0.55f)

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val leftPad = 12.dp.toPx()
                val rightPad = 12.dp.toPx()
                val topPad = 14.dp.toPx()
                val bottomPad = 10.dp.toPx()
                val chartW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
                val chartH = (size.height - topPad - bottomPad).coerceAtLeast(1f)

                // Horizontal grid lines (0, 25, 50, 75, 100)
                listOf(0f, 0.25f, 0.50f, 0.80f, 1.0f).forEach { frac ->
                    val y = topPad + chartH * (1f - frac)
                    val color = when (frac) {
                        0.50f -> passLineColor
                        0.80f -> excelLineColor
                        else -> gridColor
                    }
                    val pathEffect = if (frac == 0.50f || frac == 0.80f) {
                        PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                    } else null
                    drawLine(
                        color = color,
                        start = Offset(leftPad, y),
                        end = Offset(leftPad + chartW, y),
                        strokeWidth = if (frac == 0.50f || frac == 0.80f) 1.5.dp.toPx() else 1.dp.toPx(),
                        pathEffect = pathEffect
                    )
                }

                if (stats.isNotEmpty()) {
                    val slotW = chartW / stats.size
                    val barW = (slotW * 0.56f).coerceIn(14.dp.toPx(), 42.dp.toPx())
                    stats.forEachIndexed { idx, stat ->
                        val frac = (stat.averageScore / 100.0).toFloat().coerceIn(0.03f, 1f)
                        val barH = chartH * frac
                        val x = leftPad + idx * slotW + (slotW - barW) / 2f
                        val y = topPad + chartH - barH
                        val isFocused = stat.subjectName == focusedName
                        val baseColor = when {
                            stat.averageScore >= 80.0 -> SafeGreen600
                            stat.averageScore >= 60.0 -> SafeBlue700
                            stat.averageScore >= 40.0 -> DelayAmber700
                            else -> EmergencyRed600
                        }
                        drawRoundRect(
                            color = if (isFocused) baseColor else baseColor.copy(alpha = 0.72f),
                            topLeft = Offset(x, y),
                            size = Size(barW, barH),
                            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                        )
                    }
                }
            }
        }

        // Subject Short Labels Row
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            stats.forEach { stat ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${stat.averageScore.roundToInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = SafeBlue900
                    )
                    Text(
                        text = stat.subjectName.take(6),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun GradeDistributionChart(
    gradeCounts: Map<Int, Int>,
    modifier: Modifier = Modifier
) {
    val maxCount = (gradeCounts.values.maxOrNull() ?: 1).coerceAtLeast(1)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val leftPad = 14.dp.toPx()
                val rightPad = 14.dp.toPx()
                val topPad = 10.dp.toPx()
                val bottomPad = 8.dp.toPx()
                val chartW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
                val chartH = (size.height - topPad - bottomPad).coerceAtLeast(1f)

                drawLine(
                    color = gridColor,
                    start = Offset(leftPad, topPad + chartH),
                    end = Offset(leftPad + chartW, topPad + chartH),
                    strokeWidth = 1.dp.toPx()
                )

                val slotW = chartW / 6f
                val barW = (slotW * 0.55f).coerceIn(16.dp.toPx(), 44.dp.toPx())
                (1..6).forEachIndexed { idx, grade ->
                    val count = gradeCounts[grade] ?: 0
                    val frac = if (count == 0) 0.04f else (count.toFloat() / maxCount.toFloat()).coerceIn(0.08f, 1f)
                    val barH = chartH * frac
                    val x = leftPad + idx * slotW + (slotW - barW) / 2f
                    val y = topPad + chartH - barH
                    drawRoundRect(
                        color = gradeColor(grade).copy(alpha = if (count == 0) 0.2f else 0.88f),
                        topLeft = Offset(x, y),
                        size = Size(barW, barH),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                    )
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
            (1..6).forEach { grade ->
                val count = gradeCounts[grade] ?: 0
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(count.toString(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text("G$grade", style = MaterialTheme.typography.labelSmall, color = gradeColor(grade), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun TermTrendLineChart(
    termPoints: List<TermPoint>,
    trackedSubjects: List<String>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val leftPad = 24.dp.toPx()
                val rightPad = 24.dp.toPx()
                val topPad = 14.dp.toPx()
                val bottomPad = 14.dp.toPx()
                val chartW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
                val chartH = (size.height - topPad - bottomPad).coerceAtLeast(1f)

                listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { frac ->
                    val y = topPad + chartH * (1f - frac)
                    drawLine(
                        color = gridColor,
                        start = Offset(leftPad, y),
                        end = Offset(leftPad + chartW, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                fun xForIndex(i: Int): Float = if (termPoints.size <= 1) leftPad else leftPad + (chartW * i / (termPoints.size - 1))
                fun yForScore(score: Double): Float = topPad + chartH * (1f - (score / 100.0).toFloat().coerceIn(0f, 1f))

                // Overall area + line
                val activeOverall = termPoints.mapIndexedNotNull { idx, tp ->
                    if (tp.reportCount > 0) Offset(xForIndex(idx), yForScore(tp.overallAvg)) else null
                }
                if (activeOverall.size >= 2) {
                    val areaPath = Path().apply {
                        moveTo(activeOverall.first().x, topPad + chartH)
                        activeOverall.forEach { lineTo(it.x, it.y) }
                        lineTo(activeOverall.last().x, topPad + chartH)
                        close()
                    }
                    drawPath(
                        path = areaPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(SafeBlue700.copy(alpha = 0.24f), Color.Transparent),
                            startY = topPad,
                            endY = topPad + chartH
                        )
                    )
                    val linePath = Path().apply {
                        moveTo(activeOverall.first().x, activeOverall.first().y)
                        activeOverall.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    drawPath(
                        path = linePath,
                        color = SafeBlue900,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }
                activeOverall.forEach { pt ->
                    drawCircle(color = Color.White, radius = 6.dp.toPx(), center = pt)
                    drawCircle(color = SafeBlue900, radius = 4.dp.toPx(), center = pt)
                }

                // Secondary tracked subject series lines
                val seriesColors = listOf(SafeGreen600, DelayAmber700)
                trackedSubjects.forEachIndexed { sIdx, subName ->
                    val sColor = seriesColors.getOrElse(sIdx) { SafeBlue600 }
                    val pts = termPoints.mapIndexedNotNull { idx, tp ->
                        val v = tp.perSubjectAvg[subName]
                        if (v != null) Offset(xForIndex(idx), yForScore(v)) else null
                    }
                    if (pts.size >= 2) {
                        val p = Path().apply {
                            moveTo(pts.first().x, pts.first().y)
                            pts.drop(1).forEach { lineTo(it.x, it.y) }
                        }
                        drawPath(
                            path = p,
                            color = sColor,
                            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }
                    pts.forEach { pt ->
                        drawCircle(color = sColor, radius = 3.5.dp.toPx(), center = pt)
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            termPoints.forEach { tp ->
                Text(tp.termLabel, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
