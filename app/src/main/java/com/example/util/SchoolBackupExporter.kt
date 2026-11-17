package com.example.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.data.model.AttendanceRecord
import com.example.data.model.ExamReport
import com.example.data.model.FeeRecord
import com.example.data.model.InventoryItem
import com.example.data.model.School
import com.example.data.model.SchoolClass
import com.example.data.model.Student
import com.example.data.model.Subject
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BackupSummary(
    val schoolName: String,
    val exportedAtIso: String,
    val classCount: Int,
    val studentCount: Int,
    val subjectCount: Int,
    val attendanceCount: Int,
    val examReportCount: Int,
    val feeRecordCount: Int,
    val inventoryCount: Int
)

object SchoolBackupExporter {

    fun buildSchoolBackupJson(
        school: School?,
        classes: List<SchoolClass>,
        students: List<Student>,
        subjects: List<Subject>,
        attendance: List<AttendanceRecord>,
        examReports: List<ExamReport>,
        feeRecords: List<FeeRecord>,
        inventoryItems: List<InventoryItem>
    ): String {
        val nowIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
        val root = JSONObject()
        root.put("format", "cefelix_safetrack_backup_v1")
        root.put("exportedAt", nowIso)
        root.put("school", JSONObject().apply {
            put("id", school?.id.orEmpty())
            put("name", school?.name.orEmpty())
            put("code", school?.code.orEmpty())
            put("city", school?.city.orEmpty())
            put("country", school?.country ?: "Ghana")
            put("motto", school?.motto.orEmpty())
            put("academicYear", school?.academicYear ?: "2026/2027")
            put("currentTerm", school?.currentTerm ?: "Term 1")
            put("operatingHours", school?.operatingHours ?: "07:00 – 15:30")
            put("phone", school?.phone.orEmpty())
            put("email", school?.email.orEmpty())
        })
        root.put("classes", JSONArray().apply {
            classes.forEach { c ->
                put(JSONObject().apply {
                    put("id", c.id)
                    put("name", c.name)
                    put("gradeLevel", c.gradeLevel)
                    put("academicYear", c.academicYear)
                    put("studentCount", c.studentCount)
                })
            }
        })
        root.put("students", JSONArray().apply {
            students.forEach { s ->
                put(JSONObject().apply {
                    put("id", s.id)
                    put("admissionNumber", s.admissionNumber)
                    put("fullName", s.fullName)
                    put("classId", s.classId)
                    put("className", s.className)
                    put("status", s.status)
                })
            }
        })
        root.put("subjects", JSONArray().apply {
            subjects.forEach { sub ->
                put(JSONObject().apply {
                    put("id", sub.id)
                    put("name", sub.name)
                    put("code", sub.code)
                    put("level", sub.level)
                    put("isCore", sub.isCore)
                })
            }
        })
        root.put("attendance", JSONArray().apply {
            attendance.forEach { att ->
                put(JSONObject().apply {
                    put("id", att.id)
                    put("studentId", att.studentId)
                    put("studentName", att.studentName)
                    put("className", att.className)
                    put("dateKey", att.dateKey)
                    put("term", att.term)
                    put("status", att.status)
                })
            }
        })
        root.put("examReports", JSONArray().apply {
            examReports.forEach { rep ->
                put(JSONObject().apply {
                    put("id", rep.id)
                    put("studentId", rep.studentId)
                    put("studentName", rep.studentName)
                    put("className", rep.className)
                    put("term", rep.term)
                    put("overallTotal", rep.overallTotal)
                    put("averageScore", rep.averageScore)
                    put("positionText", rep.positionText)
                    put("aggregate", rep.aggregate)
                })
            }
        })
        root.put("feeRecords", JSONArray().apply {
            feeRecords.forEach { fee ->
                put(JSONObject().apply {
                    put("id", fee.id)
                    put("studentName", fee.studentName)
                    put("title", fee.title)
                    put("term", fee.term)
                    put("amountDueGhs", fee.amountDueGhs)
                    put("amountPaidGhs", fee.amountPaidGhs)
                    put("status", fee.status)
                    put("receiptNumber", fee.receiptNumber)
                })
            }
        })
        root.put("inventoryItems", JSONArray().apply {
            inventoryItems.forEach { item ->
                put(JSONObject().apply {
                    put("id", item.id)
                    put("name", item.name)
                    put("category", item.category)
                    put("quantity", item.quantity)
                    put("minimumStock", item.minimumStock)
                    put("condition", item.condition)
                })
            }
        })
        return root.toString(2)
    }

    fun inspectBackupJson(json: String): Result<BackupSummary> = runCatching {
        val root = JSONObject(json)
        val format = root.optString("format", "")
        require(format == "cefelix_safetrack_backup_v1") {
            "Unrecognized backup format. Expected SafeTrack backup JSON."
        }
        val schoolObj = root.optJSONObject("school") ?: JSONObject()
        BackupSummary(
            schoolName = schoolObj.optString("name", "School Backup"),
            exportedAtIso = root.optString("exportedAt", ""),
            classCount = root.optJSONArray("classes")?.length() ?: 0,
            studentCount = root.optJSONArray("students")?.length() ?: 0,
            subjectCount = root.optJSONArray("subjects")?.length() ?: 0,
            attendanceCount = root.optJSONArray("attendance")?.length() ?: 0,
            examReportCount = root.optJSONArray("examReports")?.length() ?: 0,
            feeRecordCount = root.optJSONArray("feeRecords")?.length() ?: 0,
            inventoryCount = root.optJSONArray("inventoryItems")?.length() ?: 0
        )
    }

    fun exportAndShareBackupJson(
        context: Context,
        school: School?,
        classes: List<SchoolClass>,
        students: List<Student>,
        subjects: List<Subject>,
        attendance: List<AttendanceRecord>,
        examReports: List<ExamReport>,
        feeRecords: List<FeeRecord>,
        inventoryItems: List<InventoryItem>
    ): Result<File> = runCatching {
        val json = buildSchoolBackupJson(
            school = school,
            classes = classes,
            students = students,
            subjects = subjects,
            attendance = attendance,
            examReports = examReports,
            feeRecords = feeRecords,
            inventoryItems = inventoryItems
        )
        val exportDir = File(context.cacheDir, "reports").apply { mkdirs() }
        val file = File(exportDir, "safetrack_backup_${System.currentTimeMillis()}.json")
        file.writeText(json)

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "${school?.name ?: "SafeTrack"} JSON Data Backup")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Save or Share School Backup JSON"))
        file
    }

    fun calculateLocalCacheBytes(context: Context): Long {
        fun folderBytes(dir: File?): Long {
            if (dir == null || !dir.exists()) return 0L
            return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        }
        return folderBytes(File(context.cacheDir, "reports")) + folderBytes(context.cacheDir)
    }

    fun clearLocalExportCache(context: Context) {
        runCatching { File(context.cacheDir, "reports").deleteRecursively() }
    }
}
