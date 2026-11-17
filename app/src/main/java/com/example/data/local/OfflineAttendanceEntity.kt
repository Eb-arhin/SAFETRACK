package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "offline_attendance_queue")
data class OfflineAttendanceEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val documentId: String,
    val schoolId: String,
    val studentId: String,
    val studentName: String,
    val classId: String,
    val className: String,
    val dateKey: String,
    val term: String,
    val status: String,
    val arrivalTime: String?,
    val recordedByUid: String,
    val recordedByName: String,
    val correctionReason: String,
    val guardianUidsCsv: String,
    val teacherUidsCsv: String,
    val adminUidsCsv: String,
    val queuedAtMillis: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false
)
