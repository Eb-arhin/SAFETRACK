package com.example.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GeofenceManagerTest {

    @Test
    fun configPersistence_and_alertFormatting_workCorrectly() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sample = GeofenceMonitoringConfig(
            isActive = true,
            schoolId = "sch_123",
            schoolName = "Ridge Model School",
            latitude = 5.6037,
            longitude = -0.1870,
            radiusMeters = 150.0,
            trackedStudents = listOf(
                TrackedGeofenceStudent(
                    id = "stu_1",
                    fullName = "Ama Boateng",
                    classId = "cls_1",
                    className = "Primary 3",
                    guardianUids = listOf("par_1"),
                    teacherUids = listOf("tea_1"),
                    adminUids = listOf("adm_1")
                )
            )
        )
        GeofenceManager.saveConfig(context, sample)
        val loaded = GeofenceManager.loadConfig(context)
        assertTrue(loaded.isActive)
        assertEquals("sch_123", loaded.schoolId)
        assertEquals("Ridge Model School", loaded.schoolName)
        assertEquals(150.0, loaded.radiusMeters, 0.01)
        assertEquals(1, loaded.trackedStudents.size)
        assertEquals("Ama Boateng", loaded.trackedStudents.first().fullName)

        val (enterTitle, enterBody) = GeofenceManager.formatGeofenceAlert(
            studentName = "Ama Boateng",
            schoolName = "Ridge Model School",
            isEntering = true,
            timeLabel = "07:40"
        )
        assertTrue(enterTitle.contains("Entered School Premises"))
        assertTrue(enterBody.contains("07:40"))

        val (exitTitle, exitBody) = GeofenceManager.formatGeofenceAlert(
            studentName = "Ama Boateng",
            schoolName = "Ridge Model School",
            isEntering = false,
            timeLabel = "15:10"
        )
        assertTrue(exitTitle.contains("Left School Premises"))
        assertTrue(exitBody.contains("15:10"))

        // Clear back to inactive
        GeofenceManager.saveConfig(context, sample.copy(isActive = false, trackedStudents = emptyList()))
        assertFalse(GeofenceManager.loadConfig(context).isActive)
    }

    @Test
    fun distanceMeters_computesAccurateSchoolPerimeterDistances() {
        val samePoint = GeofenceManager.distanceMeters(5.6037, -0.1870, 5.6037, -0.1870)
        assertEquals(0f, samePoint, 0.5f)

        // ~0.002 degrees latitude is roughly 222 meters (outside 150m perimeter)
        val outsideDist = GeofenceManager.distanceMeters(5.6037, -0.1870, 5.6057, -0.1870)
        assertTrue("Expected distance > 150m, got $outsideDist", outsideDist > 150f)
        assertEquals("safetrack_school_sch_abc", GeofenceManager.geofenceRequestIdForSchool("sch_abc"))
    }
}
