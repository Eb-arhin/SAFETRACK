package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AccentPalette
import com.example.data.local.AppSettingsStore
import com.example.data.local.AppThemeMode
import com.example.data.model.AttendanceRecord
import com.example.data.model.AttendanceStatus
import com.example.data.model.DeviceViewLayoutMode
import com.example.data.model.FeeRecord
import com.example.data.model.InventoryItem
import com.example.data.model.School
import com.example.data.model.SchoolClass
import com.example.data.model.Student
import com.example.util.SchoolBackupExporter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("SafeTrack", appName)
  }

  @Test
  fun `appSettingsStore persists theme accent fontScale notifications and restores defaults`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val store = AppSettingsStore(context)

    store.restoreDefaults()
    store.setThemeMode(AppThemeMode.DARK)
    store.setAccentPalette(AccentPalette.EMERALD)
    store.setFontScale(1.15f)
    store.setCompactCards(true)
    store.setDeviceLayoutMode(DeviceViewLayoutMode.TABLET)
    store.setNotificationToggle("geofence", false)
    store.setClassAssessmentWeightPercent(40)

    val saved = store.settingsFlow.first()
    assertEquals(AppThemeMode.DARK, saved.themeMode)
    assertEquals(AccentPalette.EMERALD, saved.accentPalette)
    assertEquals(1.15f, saved.fontScale, 0.01f)
    assertTrue(saved.compactCards)
    assertEquals(DeviceViewLayoutMode.TABLET, saved.deviceLayoutMode)
    assertFalse(saved.notifyGeofence)
    assertTrue(saved.notifyAttendance)
    assertEquals(40, saved.classAssessmentWeightPercent)

    store.restoreDefaults()
    val reset = store.settingsFlow.first()
    assertEquals(AppThemeMode.SYSTEM, reset.themeMode)
    assertEquals(AccentPalette.NAVY, reset.accentPalette)
    assertEquals(1.0f, reset.fontScale, 0.01f)
    assertFalse(reset.compactCards)
    assertEquals(DeviceViewLayoutMode.AUTO, reset.deviceLayoutMode)
    assertTrue(reset.notifyGeofence)
    assertEquals(50, reset.classAssessmentWeightPercent)
  }

  @Test
  fun `schoolBackupExporter serializes and inspects backup json accurately`() {
    val school = School(
      id = "sch_1",
      name = "Cefelix Academy",
      code = "CFX01",
      city = "Accra",
      motto = "Excellence in Discipline"
    )
    val classes = listOf(SchoolClass(id = "c1", schoolId = "sch_1", name = "Primary 4A"))
    val students = listOf(Student(id = "s1", schoolId = "sch_1", admissionNumber = "CA-001", fullName = "Ama Boateng"))
    val attendance = listOf(AttendanceRecord(id = "a1", schoolId = "sch_1", studentId = "s1", studentName = "Ama Boateng", status = AttendanceStatus.PRESENT.key))
    val fees = listOf(FeeRecord(id = "f1", schoolId = "sch_1", studentId = "s1", studentName = "Ama Boateng", title = "Term 1 Tuition", amountDueGhs = 500.0, amountPaidGhs = 500.0, status = "paid"))
    val inventory = listOf(InventoryItem(id = "inv_1", schoolId = "sch_1", name = "Smart Projector", category = "ICT & Devices", quantity = 4))

    val json = SchoolBackupExporter.buildSchoolBackupJson(
      school = school,
      classes = classes,
      students = students,
      subjects = emptyList(),
      attendance = attendance,
      examReports = emptyList(),
      feeRecords = fees,
      inventoryItems = inventory
    )
    val inspected = SchoolBackupExporter.inspectBackupJson(json)
    assertTrue(inspected.isSuccess)
    val summary = inspected.getOrThrow()
    assertEquals("Cefelix Academy", summary.schoolName)
    assertEquals(1, summary.classCount)
    assertEquals(1, summary.studentCount)
    assertEquals(1, summary.attendanceCount)
    assertEquals(1, summary.feeRecordCount)
    assertEquals(1, summary.inventoryCount)

    assertTrue(SchoolBackupExporter.inspectBackupJson("{\"format\":\"wrong\"}").isFailure)
  }
}
