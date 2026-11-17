package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [OfflineAttendanceEntity::class], version = 1, exportSchema = false)
abstract class SafeTrackDatabase : RoomDatabase() {
    abstract fun offlineAttendanceDao(): OfflineAttendanceDao

    companion object {
        @Volatile
        private var instance: SafeTrackDatabase? = null

        fun getInstance(context: Context): SafeTrackDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SafeTrackDatabase::class.java,
                    "safetrack_offline.db"
                ).build().also { instance = it }
            }
        }
    }
}
