package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface OfflineAttendanceDao {
    @Query("SELECT * FROM offline_attendance_queue WHERE schoolId = :schoolId AND isSynced = 0 ORDER BY queuedAtMillis ASC")
    fun observeUnsyncedForSchool(schoolId: String): Flow<List<OfflineAttendanceEntity>>

    @Query("SELECT * FROM offline_attendance_queue WHERE schoolId = :schoolId AND isSynced = 0 ORDER BY queuedAtMillis ASC")
    suspend fun getUnsyncedForSchool(schoolId: String): List<OfflineAttendanceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(entity: OfflineAttendanceEntity)

    @Query("UPDATE offline_attendance_queue SET isSynced = 1 WHERE localId = :localId")
    suspend fun markSynced(localId: Long)

    @Query("DELETE FROM offline_attendance_queue WHERE isSynced = 1")
    suspend fun clearSynced()

    @Query("DELETE FROM offline_attendance_queue")
    suspend fun clearAll()
}
