const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ADMIN_A = "admin_school_a";
const TEACHER_A = "teacher_school_a";
const PARENT_A = "parent_school_a";
const PICKUP_A = "pickup_school_a";
const ADMIN_B = "admin_school_b";
const SUPER_ADMIN_EMAIL = "ebenezerarhin414@gmail.com";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function nowTs() {
  return new Date(Date.now() - 1000);
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

async function seedSchoolA() {
  const ts = nowTs();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("schools").doc("school_a").set({
      name: "Accra Ridge Academy",
      code: "ARA01",
      city: "Accra",
      country: "Ghana",
      ownerUserId: ADMIN_A,
      adminUids: [ADMIN_A],
      planId: "professional",
      planName: "Professional",
      monthlyPriceGhs: 300,
      annualDiscountPercent: 15,
      pupilLimit: 500,
      billingCycle: "monthly",
      subscriptionStatus: "trial",
      paymentProvider: "pending",
      studentCount: 1,
      trialDays: 14,
      createdAt: ts,
      updatedAt: ts,
    });
    await db.collection("schools").doc("school_a").collection("members").doc(ADMIN_A).set({
      schoolId: "school_a",
      userId: ADMIN_A,
      email: "admin@schoola.edu.gh",
      displayName: "Admin A",
      role: "school_admin",
      assignedClassIds: [],
      linkedStudentIds: [],
      adminUids: [ADMIN_A],
      status: "active",
      createdAt: ts,
      updatedAt: ts,
    });
    await db.collection("schools").doc("school_a").collection("members").doc(TEACHER_A).set({
      schoolId: "school_a",
      userId: TEACHER_A,
      email: "teacher@schoola.edu.gh",
      displayName: "Teacher A",
      role: "teacher",
      assignedClassIds: ["class_1a"],
      linkedStudentIds: [],
      adminUids: [ADMIN_A],
      status: "active",
      createdAt: ts,
      updatedAt: ts,
    });
    await db.collection("schools").doc("school_a").collection("members").doc(PARENT_A).set({
      schoolId: "school_a",
      userId: PARENT_A,
      email: "parent@gmail.com",
      displayName: "Parent A",
      role: "parent",
      assignedClassIds: [],
      linkedStudentIds: ["student_1"],
      adminUids: [ADMIN_A],
      status: "active",
      createdAt: ts,
      updatedAt: ts,
    });
    await db.collection("students").doc("student_1").set({
      schoolId: "school_a",
      admissionNumber: "ARA-2026-001",
      fullName: "Kofi Mensah",
      classId: "class_1a",
      className: "Primary 4A",
      academicYear: "2026/2027",
      status: "active",
      approvedDestinations: ["Accra Ridge Academy", "Home - East Legon"],
      authorisedPickups: ["Ama Mensah (Aunt)"],
      guardianUids: [PARENT_A],
      teacherUids: [TEACHER_A],
      pickupUids: [PICKUP_A],
      adminUids: [ADMIN_A],
      createdAt: ts,
      updatedAt: ts,
    });
  });
}

test("Unauthenticated user: cannot list schools, students, journeys, or attendance", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("schools").get());
  await assertFails(unauthDb.collection("students").get());
  await assertFails(unauthDb.collection("journeys").get());
  await assertFails(unauthDb.collection("attendance").get());
});

test("Cross-school tenant isolation: School B admin cannot read or list School A students", async () => {
  await seedSchoolA();
  const adminBDb = testEnv.authenticatedContext(ADMIN_B, { email: "b@schoolb.edu", email_verified: true }).firestore();

  await assertFails(adminBDb.collection("students").doc("student_1").get());
  await assertFails(adminBDb.collection("students").where("schoolId", "==", "school_a").get());
  const snap = await assertSucceeds(
    adminBDb.collection("students")
      .where("schoolId", "==", "school_a")
      .where("adminUids", "array-contains", ADMIN_B)
      .get()
  );
  if (!snap.empty) throw new Error("School B admin received School A students!");
});

test("Blanket list query without role array filter fails (rules are not filters)", async () => {
  await seedSchoolA();
  const parentDb = testEnv.authenticatedContext(PARENT_A).firestore();
  await assertFails(parentDb.collection("students").where("schoolId", "==", "school_a").get());
});

test("Parent can query own linked children and start morning journey, but CANNOT confirm arrival at school or mark attendance present", async () => {
  await seedSchoolA();
  const parentDb = testEnv.authenticatedContext(PARENT_A).firestore();
  const ts = nowTs();

  await assertSucceeds(
    parentDb.collection("students")
      .where("schoolId", "==", "school_a")
      .where("guardianUids", "array-contains", PARENT_A)
      .get()
  );

  // Parent starts morning journey -> ALLOWED
  await assertSucceeds(
    parentDb.collection("journeys").doc("j_morning_1").set({
      schoolId: "school_a",
      studentId: "student_1",
      studentName: "Kofi Mensah",
      classId: "class_1a",
      className: "Primary 4A",
      direction: "morning_to_school",
      status: "on_the_way_to_school",
      destinationName: "Accra Ridge Academy",
      destinationType: "School",
      initiatedByUid: PARENT_A,
      initiatedByRole: "parent",
      initiatedByName: "Parent A",
      gpsAvailable: true,
      consentGranted: true,
      isTrackingActive: true,
      guardianUids: [PARENT_A],
      teacherUids: [TEACHER_A],
      pickupUids: [PICKUP_A],
      adminUids: [ADMIN_A],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Parent CANNOT mark arrival at school -> DENIED
  await assertFails(
    parentDb.collection("journeys").doc("j_morning_1").update({
      status: "arrived_at_school",
      isTrackingActive: false,
      updatedAt: nowTs(),
    })
  );

  // Parent CANNOT initiate afternoon dismissal -> DENIED
  await assertFails(
    parentDb.collection("journeys").doc("j_afternoon_bad").set({
      schoolId: "school_a",
      studentId: "student_1",
      studentName: "Kofi Mensah",
      classId: "class_1a",
      className: "Primary 4A",
      direction: "afternoon_to_home",
      status: "on_the_way_home",
      destinationName: "Home",
      destinationType: "Home",
      initiatedByUid: PARENT_A,
      initiatedByRole: "parent",
      initiatedByName: "Parent A",
      gpsAvailable: false,
      consentGranted: false,
      isTrackingActive: false,
      guardianUids: [PARENT_A],
      teacherUids: [TEACHER_A],
      pickupUids: [PICKUP_A],
      adminUids: [ADMIN_A],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Parent CANNOT create attendance record -> DENIED
  await assertFails(
    parentDb.collection("attendance").doc("att_bad").set({
      schoolId: "school_a",
      studentId: "student_1",
      studentName: "Kofi Mensah",
      classId: "class_1a",
      className: "Primary 4A",
      dateKey: "2026-11-14",
      term: "Term 1",
      status: "present",
      recordedByUid: PARENT_A,
      recordedByName: "Parent A",
      correctionReason: "",
      isOfflineSynced: false,
      guardianUids: [PARENT_A],
      teacherUids: [TEACHER_A],
      adminUids: [ADMIN_A],
      createdAt: ts,
      updatedAt: ts,
    })
  );
});

test("Teacher confirms arrival and records attendance; corrections require reason; audit logs are immutable", async () => {
  await seedSchoolA();
  const teacherDb = testEnv.authenticatedContext(TEACHER_A).firestore();
  const parentDb = testEnv.authenticatedContext(PARENT_A).firestore();
  const ts = nowTs();

  await assertSucceeds(
    parentDb.collection("journeys").doc("j_m_2").set({
      schoolId: "school_a",
      studentId: "student_1",
      studentName: "Kofi Mensah",
      classId: "class_1a",
      className: "Primary 4A",
      direction: "morning_to_school",
      status: "on_the_way_to_school",
      destinationName: "Accra Ridge Academy",
      destinationType: "School",
      initiatedByUid: PARENT_A,
      initiatedByRole: "parent",
      initiatedByName: "Parent A",
      gpsAvailable: true,
      consentGranted: true,
      isTrackingActive: true,
      guardianUids: [PARENT_A],
      teacherUids: [TEACHER_A],
      pickupUids: [PICKUP_A],
      adminUids: [ADMIN_A],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Teacher confirms arrival at school -> ALLOWED
  await assertSucceeds(
    teacherDb.collection("journeys").doc("j_m_2").update({
      status: "arrived_at_school",
      confirmedByUid: TEACHER_A,
      confirmedByName: "Teacher A",
      isTrackingActive: false,
      updatedAt: nowTs(),
    })
  );

  // Teacher checks attendance doc existence before upsert -> ALLOWED
  await assertSucceeds(teacherDb.collection("attendance").doc("att_1").get());

  // Teacher records Present attendance -> ALLOWED
  await assertSucceeds(
    teacherDb.collection("attendance").doc("att_1").set({
      schoolId: "school_a",
      studentId: "student_1",
      studentName: "Kofi Mensah",
      classId: "class_1a",
      className: "Primary 4A",
      dateKey: "2026-11-14",
      term: "Term 1",
      status: "present",
      arrivalTime: "07:45",
      recordedByUid: TEACHER_A,
      recordedByName: "Teacher A",
      correctionReason: "",
      isOfflineSynced: false,
      guardianUids: [PARENT_A],
      teacherUids: [TEACHER_A],
      adminUids: [ADMIN_A],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Teacher corrects attendance WITHOUT reason -> DENIED
  await assertFails(
    teacherDb.collection("attendance").doc("att_1").update({
      status: "late",
      correctionReason: "",
      updatedAt: nowTs(),
    })
  );

  // Teacher corrects attendance WITH reason -> ALLOWED
  await assertSucceeds(
    teacherDb.collection("attendance").doc("att_1").update({
      status: "late",
      correctionReason: "Arrived after 08:00 assembly",
      updatedAt: nowTs(),
    })
  );

  // Audit log creation allowed, update/delete denied
  await assertSucceeds(
    teacherDb.collection("audit_logs").doc("log_1").set({
      schoolId: "school_a",
      actorUid: TEACHER_A,
      actorName: "Teacher A",
      actorRole: "teacher",
      action: "attendance_corrected",
      targetType: "attendance",
      targetId: "att_1",
      details: "Changed present to late",
      reason: "Arrived after 08:00 assembly",
      adminUids: [ADMIN_A],
      createdAt: ts,
    })
  );
  await assertFails(teacherDb.collection("audit_logs").doc("log_1").update({ details: "tampered" }));
  await assertFails(teacherDb.collection("audit_logs").doc("log_1").delete());
});

test("Authorised pickup person cannot authorise dismissal; Super Admin email verification enforced", async () => {
  await seedSchoolA();
  const pickupDb = testEnv.authenticatedContext(PICKUP_A).firestore();
  const ts = nowTs();

  await assertFails(
    pickupDb.collection("journeys").doc("j_pickup_bad").set({
      schoolId: "school_a",
      studentId: "student_1",
      studentName: "Kofi Mensah",
      classId: "class_1a",
      className: "Primary 4A",
      direction: "afternoon_to_home",
      status: "on_the_way_home",
      destinationName: "Home",
      destinationType: "Home",
      initiatedByUid: PICKUP_A,
      initiatedByRole: "pickup_person",
      initiatedByName: "Ama",
      gpsAvailable: false,
      consentGranted: false,
      isTrackingActive: false,
      guardianUids: [PARENT_A],
      teacherUids: [TEACHER_A],
      pickupUids: [PICKUP_A],
      adminUids: [ADMIN_A],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Spoofed Super Admin without email_verified=true -> DENIED
  const spoofDb = testEnv.authenticatedContext("spoof_uid", { email: SUPER_ADMIN_EMAIL, email_verified: false }).firestore();
  await assertFails(spoofDb.collection("schools").get());

  // Verified Super Admin -> ALLOWED
  const superDb = testEnv.authenticatedContext("super_uid", { email: SUPER_ADMIN_EMAIL, email_verified: true }).firestore();
  await assertSucceeds(superDb.collection("schools").get());
});

test("Only assigned class teacher can authorize child dismissal: school admin, unassigned teacher, and super admin denied", async () => {
  await seedSchoolA();
  const UNASSIGNED_TEACHER = "teacher_unassigned_a";
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("schools").doc("school_a").collection("members").doc(UNASSIGNED_TEACHER).set({
      schoolId: "school_a",
      userId: UNASSIGNED_TEACHER,
      email: "otherteacher@schoola.edu.gh",
      displayName: "Other Teacher",
      role: "teacher",
      assignedClassIds: [],
      linkedStudentIds: [],
      adminUids: [ADMIN_A],
      status: "active",
      createdAt: nowTs(),
      updatedAt: nowTs(),
    });
  });

  const adminDb = testEnv.authenticatedContext(ADMIN_A).firestore();
  const unassignedTeacherDb = testEnv.authenticatedContext(UNASSIGNED_TEACHER).firestore();
  const assignedTeacherDb = testEnv.authenticatedContext(TEACHER_A).firestore();
  const superDb = testEnv.authenticatedContext("super_uid", { email: SUPER_ADMIN_EMAIL, email_verified: true }).firestore();
  const ts = nowTs();

  const dismissalPayload = (uid, roleName) => ({
    schoolId: "school_a",
    studentId: "student_1",
    studentName: "Kofi Mensah",
    classId: "class_1a",
    className: "Primary 4A",
    direction: "afternoon_to_home",
    status: "on_the_way_home",
    destinationName: "Home - East Legon",
    destinationType: "Home",
    initiatedByUid: uid,
    initiatedByRole: roleName,
    initiatedByName: "Staff",
    gpsAvailable: false,
    consentGranted: false,
    isTrackingActive: false,
    guardianUids: [PARENT_A],
    teacherUids: [TEACHER_A],
    pickupUids: [PICKUP_A],
    adminUids: [ADMIN_A],
    startedAt: ts,
    createdAt: ts,
    updatedAt: ts,
  });

  // School Admin cannot authorize afternoon dismissal -> DENIED
  await assertFails(adminDb.collection("journeys").doc("j_admin_dismiss_bad").set(dismissalPayload(ADMIN_A, "school_admin")));

  // Unassigned teacher cannot authorize dismissal for student_1 -> DENIED
  await assertFails(unassignedTeacherDb.collection("journeys").doc("j_unassigned_dismiss_bad").set(dismissalPayload(UNASSIGNED_TEACHER, "teacher")));

  // Super Admin cannot authorize dismissal -> DENIED
  await assertFails(superDb.collection("journeys").doc("j_super_dismiss_bad").set(dismissalPayload("super_uid", "super_admin")));

  // Assigned teacher authorizes dismissal -> ALLOWED
  await assertSucceeds(assignedTeacherDb.collection("journeys").doc("j_teacher_dismiss_ok").set(dismissalPayload(TEACHER_A, "teacher")));
});

test("Subjects and exam reports: teacher creates, parent reads child report, parent cannot write, School B isolated", async () => {
  await seedSchoolA();
  const teacherDb = testEnv.authenticatedContext(TEACHER_A).firestore();
  const parentDb = testEnv.authenticatedContext(PARENT_A).firestore();
  const adminBDb = testEnv.authenticatedContext(ADMIN_B).firestore();
  const ts = nowTs();

  await assertSucceeds(
    teacherDb.collection("subjects").doc("sub_math").set({
      schoolId: "school_a",
      name: "Mathematics",
      code: "MATH",
      level: "both",
      isCore: true,
      coreSlot: "mathematics",
      teacherUids: [TEACHER_A],
      adminUids: [ADMIN_A],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Parent cannot create a subject -> DENIED
  await assertFails(
    parentDb.collection("subjects").doc("sub_bad").set({
      schoolId: "school_a",
      name: "Hacking",
      code: "HCK",
      level: "both",
      isCore: false,
      coreSlot: "",
      teacherUids: [TEACHER_A],
      adminUids: [ADMIN_A],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Teacher creates terminal exam report -> ALLOWED
  await assertSucceeds(
    teacherDb.collection("exam_reports").doc("rep_student1_term1").set({
      schoolId: "school_a",
      schoolName: "Accra Ridge Academy",
      studentId: "student_1",
      studentName: "Kofi Mensah",
      admissionNumber: "ARA-2026-001",
      classId: "class_1a",
      className: "Primary 4A",
      academicYear: "2026/2027",
      term: "Term 1",
      level: "primary",
      scores: [{
        subjectId: "sub_math",
        subjectName: "Mathematics",
        isCore: true,
        coreSlot: "mathematics",
        rawClassScore: 90,
        rawExamScore: 84,
        classScore50: 45,
        examScore50: 42,
        totalScore: 87,
        grade: 1,
        remark: "Excellent",
      }],
      overallTotal: 87,
      averageScore: 87,
      positionText: "1st",
      aggregate: 4,
      aggregateBreakdown: "Math: 1",
      attendanceSummary: "58 / 60 days",
      attitudeAndConduct: "Respectful and hardworking",
      teacherRemarks: "Keep up the excellent performance.",
      headTeacherRemarks: "Promising pupil.",
      classTeacherSignature: "Teacher A",
      headTeacherSignature: "Headmaster",
      recordedByUid: TEACHER_A,
      recordedByName: "Teacher A",
      guardianUids: [PARENT_A],
      teacherUids: [TEACHER_A],
      adminUids: [ADMIN_A],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Parent can list & read linked child's report card -> ALLOWED
  await assertSucceeds(
    parentDb.collection("exam_reports")
      .where("schoolId", "==", "school_a")
      .where("guardianUids", "array-contains", PARENT_A)
      .get()
  );

  // Parent cannot tamper with exam report -> DENIED
  await assertFails(
    parentDb.collection("exam_reports").doc("rep_student1_term1").update({
      positionText: "1st",
      overallTotal: 100,
      updatedAt: nowTs(),
    })
  );

  // School B cannot read School A report -> DENIED
  await assertFails(adminBDb.collection("exam_reports").doc("rep_student1_term1").get());
});

test("School geofence config: School Admin updates perimeter, Parent cannot, geofence transition notifies parent", async () => {
  await seedSchoolA();
  const adminDb = testEnv.authenticatedContext(ADMIN_A).firestore();
  const parentDb = testEnv.authenticatedContext(PARENT_A).firestore();
  const teacherDb = testEnv.authenticatedContext(TEACHER_A).firestore();
  const adminBDb = testEnv.authenticatedContext(ADMIN_B).firestore();
  const ts = nowTs();

  // School Admin sets campus geofence -> ALLOWED
  await assertSucceeds(
    adminDb.collection("schools").doc("school_a").update({
      geofenceLatitude: 5.6037,
      geofenceLongitude: -0.1870,
      geofenceRadiusMeters: 150,
      geofenceEnabled: true,
      updatedAt: nowTs(),
    })
  );

  // Radius < 100m -> DENIED by rule validation
  await assertFails(
    adminDb.collection("schools").doc("school_a").update({
      geofenceRadiusMeters: 25,
      updatedAt: nowTs(),
    })
  );

  // Parent cannot modify School A geofence -> DENIED
  await assertFails(
    parentDb.collection("schools").doc("school_a").update({
      geofenceEnabled: false,
      updatedAt: nowTs(),
    })
  );

  // Teacher / carrying device logs geofence_enter notification for Parent A -> ALLOWED
  await assertSucceeds(
    teacherDb.collection("notifications").doc("ntf_geo_enter").set({
      schoolId: "school_a",
      recipientUid: PARENT_A,
      title: "Kofi Mensah Entered School Premises",
      body: "Kofi Mensah's authorised device entered Accra Ridge Academy perimeter at 07:45.",
      type: "geofence_enter",
      studentId: "student_1",
      isRead: false,
      adminUids: [ADMIN_A],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(parentDb.collection("notifications").doc("ntf_geo_enter").get());
  await assertFails(adminBDb.collection("notifications").doc("ntf_geo_enter").get());
});

test("Staff roster management: Admin and Teacher can edit/delete students & teachers, Teacher cannot escalate to school_admin, Parent denied", async () => {
  await seedSchoolA();
  const adminDb = testEnv.authenticatedContext(ADMIN_A).firestore();
  const teacherDb = testEnv.authenticatedContext(TEACHER_A).firestore();
  const parentDb = testEnv.authenticatedContext(PARENT_A).firestore();
  const ts = nowTs();

  // Teacher can add another teacher member -> ALLOWED
  await assertSucceeds(
    teacherDb.collection("schools").doc("school_a").collection("members").doc("tch_second").set({
      schoolId: "school_a",
      userId: "tch_second",
      email: "second@schoola.edu.gh",
      displayName: "Second Teacher",
      phone: "+233201112222",
      role: "teacher",
      assignedClassIds: ["class_1a"],
      linkedStudentIds: [],
      adminUids: [ADMIN_A],
      status: "active",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Teacher can edit a teacher's name -> ALLOWED
  await assertSucceeds(
    teacherDb.collection("schools").doc("school_a").collection("members").doc("tch_second").update({
      displayName: "Second Teacher Updated",
      updatedAt: nowTs(),
    })
  );

  // Teacher CANNOT escalate a teacher to school_admin -> DENIED
  await assertFails(
    teacherDb.collection("schools").doc("school_a").collection("members").doc("tch_second").update({
      role: "school_admin",
      updatedAt: nowTs(),
    })
  );

  // Teacher CANNOT modify or delete a school_admin -> DENIED
  await assertFails(
    teacherDb.collection("schools").doc("school_a").collection("members").doc(ADMIN_A).update({
      displayName: "Tampered Admin",
      updatedAt: nowTs(),
    })
  );
  await assertFails(
    teacherDb.collection("schools").doc("school_a").collection("members").doc(ADMIN_A).delete()
  );

  // Parent CANNOT edit or delete teacher members or students -> DENIED
  await assertFails(
    parentDb.collection("schools").doc("school_a").collection("members").doc("tch_second").delete()
  );
  await assertFails(
    parentDb.collection("students").doc("student_1").delete()
  );

  // Teacher can delete a teacher member -> ALLOWED
  await assertSucceeds(
    teacherDb.collection("schools").doc("school_a").collection("members").doc("tch_second").delete()
  );

  // Teacher can edit and delete a student -> ALLOWED
  await assertSucceeds(
    teacherDb.collection("students").doc("student_1").update({
      fullName: "Kofi Mensah-Boateng",
      updatedAt: nowTs(),
    })
  );
  await assertSucceeds(
    teacherDb.collection("students").doc("student_1").delete()
  );

  // Admin can also edit & delete teachers if re-seeded
  await assertSucceeds(
    adminDb.collection("schools").doc("school_a").collection("members").doc(TEACHER_A).update({
      displayName: "Teacher A Senior",
      updatedAt: nowTs(),
    })
  );
  await assertSucceeds(
    adminDb.collection("schools").doc("school_a").collection("members").doc(TEACHER_A).delete()
  );
});

test("Class departure dispatch: teacher creates afternoon departure journey & late_departure notification, parent denied", async () => {
  await seedSchoolA();
  const teacherDb = testEnv.authenticatedContext(TEACHER_A).firestore();
  const parentDb = testEnv.authenticatedContext(PARENT_A).firestore();
  const ts = nowTs();

  await assertSucceeds(
    teacherDb.collection("journeys").doc("j_class_dep_1").set({
      schoolId: "school_a",
      studentId: "student_1",
      studentName: "Kofi Mensah",
      classId: "class_1a",
      className: "Primary 4A",
      direction: "afternoon_to_home",
      status: "on_the_way_home",
      destinationName: "Home - East Legon",
      destinationType: "Home",
      initiatedByUid: TEACHER_A,
      initiatedByRole: "teacher",
      initiatedByName: "Teacher A",
      gpsAvailable: false,
      consentGranted: true,
      isTrackingActive: false,
      manualNote: "Class departure alert sent at 15:00",
      guardianUids: [PARENT_A],
      teacherUids: [TEACHER_A],
      pickupUids: [PICKUP_A],
      adminUids: [ADMIN_A],
      startedAt: ts,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    teacherDb.collection("notifications").doc("ntf_late_dep_1").set({
      schoolId: "school_a",
      recipientUid: PARENT_A,
      title: "Kofi Mensah — Leaving Late Exception",
      body: "Not departing with Primary 4A at 15:00. Reason: Extra science club practice (noted by Teacher A).",
      type: "late_departure",
      studentId: "student_1",
      isRead: false,
      adminUids: [ADMIN_A],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(parentDb.collection("notifications").doc("ntf_late_dep_1").get());
  await assertFails(
    parentDb.collection("notifications").doc("ntf_late_dep_1").update({
      title: "Tampered",
      updatedAt: nowTs(),
    })
  );
});

test("Teacher subject assignments & free-forever school registration enforced by rules", async () => {
  await seedSchoolA();
  const teacherDb = testEnv.authenticatedContext(TEACHER_A).firestore();
  const adminDb = testEnv.authenticatedContext("new_free_admin").firestore();
  const ts = nowTs();

  // Teacher adds colleague with assignedClassIds + assignedSubjectIds + assignedSubjectNames -> ALLOWED
  await assertSucceeds(
    teacherDb.collection("schools").doc("school_a").collection("members").doc("tch_subjects").set({
      schoolId: "school_a",
      userId: "tch_subjects",
      email: "math.teacher@schoola.edu.gh",
      displayName: "Ms. Abena Korkor",
      phone: "+233207778899",
      role: "teacher",
      assignedClassIds: ["class_1a"],
      assignedSubjectIds: ["sub_math", "sub_sci"],
      assignedSubjectNames: ["Mathematics", "Science"],
      linkedStudentIds: [],
      adminUids: [ADMIN_A],
      status: "active",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Teacher updates assigned subjects -> ALLOWED
  await assertSucceeds(
    teacherDb.collection("schools").doc("school_a").collection("members").doc("tch_subjects").update({
      assignedSubjectIds: ["sub_math"],
      assignedSubjectNames: ["Mathematics"],
      updatedAt: nowTs(),
    })
  );

  // Invalid assignedSubjectNames type (not a list) -> DENIED
  await assertFails(
    teacherDb.collection("schools").doc("school_a").collection("members").doc("tch_subjects").update({
      assignedSubjectNames: "Mathematics",
      updatedAt: nowTs(),
    })
  );

  // Admin registers a Free Forever school (GH₵0, active, 0 trial days) -> ALLOWED
  await assertSucceeds(
    adminDb.collection("schools").doc("school_free").set({
      name: "Free Community Academy",
      code: "FREE-01",
      city: "Takoradi",
      country: "Ghana",
      address: "1 Harbor Rd",
      phone: "+233200000123",
      email: "head@freecommunity.edu.gh",
      ownerUserId: "new_free_admin",
      adminUids: ["new_free_admin"],
      planId: "free_forever",
      planName: "Free Forever",
      monthlyPriceGhs: 0,
      annualDiscountPercent: 0,
      pupilLimit: 10000,
      billingCycle: "monthly",
      subscriptionStatus: "active",
      paymentProvider: "pending",
      studentCount: 0,
      trialDays: 0,
      createdAt: ts,
      updatedAt: ts,
    })
  );
});

test("App distribution & view access rules: Super Admin updates global, School Admin updates own school, non-admin and invalid URLs denied", async () => {
  await seedSchoolA();
  const superDb = testEnv.authenticatedContext("super_uid", { email: SUPER_ADMIN_EMAIL, email_verified: true }).firestore();
  const adminADb = testEnv.authenticatedContext(ADMIN_A).firestore();
  const adminBDb = testEnv.authenticatedContext(ADMIN_B).firestore();
  const teacherDb = testEnv.authenticatedContext(TEACHER_A).firestore();
  const parentDb = testEnv.authenticatedContext(PARENT_A).firestore();
  const ts = nowTs();

  // Super Admin sets global public & private access with direct HTTPS APK URL -> ALLOWED
  await assertSucceeds(
    superDb.collection("app_distribution").doc("global").set({
      configId: "global",
      downloadUrl: "https://downloads.cefelix.edu.gh/SafeTrack.apk",
      viewAccessMode: "public_and_private",
      allowedDeviceTypes: ["phone", "tablet", "computer"],
      releaseNotes: "Public APK release & private school workspaces for Android phones, tablets, and desktop.",
      updatedByUid: "super_uid",
      updatedAt: ts,
    })
  );

  // School Admin A cannot overwrite global config -> DENIED
  await assertFails(
    adminADb.collection("app_distribution").doc("global").set({
      configId: "global",
      downloadUrl: "https://evil.example.com/apk",
      viewAccessMode: "public_open",
      allowedDeviceTypes: ["phone"],
      releaseNotes: "Hijacked",
      updatedByUid: ADMIN_A,
      updatedAt: ts,
    })
  );

  // School Admin A sets School A view access & download link -> ALLOWED
  await assertSucceeds(
    adminADb.collection("app_distribution").doc("school_a").set({
      configId: "school_a",
      downloadUrl: "https://downloads.accraridge.edu.gh/safetrack?invite=INV-ST-1234",
      viewAccessMode: "invite_only",
      allowedDeviceTypes: ["phone", "tablet", "computer"],
      releaseNotes: "Install on phone, tablet, or Chromebook using invite code.",
      updatedByUid: ADMIN_A,
      updatedAt: ts,
    })
  );

  // Non-HTTPS or insecure HTTP URL rejected -> DENIED
  await assertFails(
    adminADb.collection("app_distribution").doc("school_a").set({
      configId: "school_a",
      downloadUrl: "javascript:alert(1)",
      viewAccessMode: "public_open",
      allowedDeviceTypes: ["phone"],
      releaseNotes: "Invalid scheme",
      updatedByUid: ADMIN_A,
      updatedAt: ts,
    })
  );
  await assertFails(
    adminADb.collection("app_distribution").doc("school_a").set({
      configId: "school_a",
      downloadUrl: "http://downloads.accraridge.edu.gh/safetrack.apk",
      viewAccessMode: "public_open",
      allowedDeviceTypes: ["phone"],
      releaseNotes: "Insecure HTTP",
      updatedByUid: ADMIN_A,
      updatedAt: ts,
    })
  );

  // School Admin B, Teacher A, Parent A cannot write School A's distribution config -> DENIED
  await assertFails(
    adminBDb.collection("app_distribution").doc("school_a").set({
      configId: "school_a",
      downloadUrl: "https://downloads.accraridge.edu.gh/safetrack",
      viewAccessMode: "public_open",
      allowedDeviceTypes: ["phone"],
      releaseNotes: "Cross-tenant write",
      updatedByUid: ADMIN_B,
      updatedAt: ts,
    })
  );
  await assertFails(
    teacherDb.collection("app_distribution").doc("school_a").update({
      viewAccessMode: "public_open",
      updatedByUid: TEACHER_A,
      updatedAt: nowTs(),
    })
  );
  await assertFails(
    parentDb.collection("app_distribution").doc("school_a").update({
      viewAccessMode: "public_open",
      updatedByUid: PARENT_A,
      updatedAt: nowTs(),
    })
  );

  // Signed-in users can read app_distribution config -> ALLOWED
  await assertSucceeds(parentDb.collection("app_distribution").doc("school_a").get());
  await assertSucceeds(teacherDb.collection("app_distribution").doc("global").get());

  // Unauthenticated pre-sign-in user can read ONLY global public download metadata -> ALLOWED, school config & list -> DENIED
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertSucceeds(unauthDb.collection("app_distribution").doc("global").get());
  await assertFails(unauthDb.collection("app_distribution").doc("school_a").get());
  await assertFails(unauthDb.collection("app_distribution").get());
});

test("Inventory items, fee records, user device sessions, and extended school settings enforce strict RBAC and multi-school isolation", async () => {
  await seedSchoolA();
  const adminADb = testEnv.authenticatedContext(ADMIN_A).firestore();
  const teacherADb = testEnv.authenticatedContext(TEACHER_A).firestore();
  const parentADb = testEnv.authenticatedContext(PARENT_A).firestore();
  const adminBDb = testEnv.authenticatedContext(ADMIN_B).firestore();
  const ts = nowTs();

  // 1. Extended School Information (motto, academicYear, currentTerm, operatingHours) & Multi-Store URLs
  await assertSucceeds(
    adminADb.collection("schools").doc("school_a").update({
      motto: "Knowledge & Character",
      academicYear: "2026/2027",
      currentTerm: "Term 1",
      operatingHours: "07:00 - 15:30",
      updatedAt: nowTs(),
    })
  );
  await assertSucceeds(
    adminADb.collection("app_distribution").doc("school_a").set({
      configId: "school_a",
      downloadUrl: "https://downloads.cefelix.edu.gh/app",
      playStoreUrl: "https://play.google.com/store/apps/details?id=com.aistudio.safetrack.xkqvmz",
      appStoreUrl: "https://apps.apple.com/app/id99887766",
      apkDownloadUrl: "https://downloads.cefelix.edu.gh/safetrack.apk",
      viewAccessMode: "public_open",
      allowedDeviceTypes: ["phone", "tablet", "computer"],
      releaseNotes: "Updated store links",
      updatedByUid: ADMIN_A,
      updatedAt: ts,
    })
  );
  // Insecure http:// playStoreUrl denied
  await assertFails(
    adminADb.collection("app_distribution").doc("school_a").set({
      configId: "school_a",
      downloadUrl: "https://downloads.cefelix.edu.gh/app",
      playStoreUrl: "http://insecure.example.com/store",
      viewAccessMode: "public_open",
      allowedDeviceTypes: ["phone"],
      releaseNotes: "Bad store url",
      updatedByUid: ADMIN_A,
      updatedAt: ts,
    })
  );

  // 2. Inventory Items: staff can create/update, Admin can delete, Teacher delete & Parent denied, School B denied
  await assertSucceeds(
    teacherADb.collection("inventory_items").doc("inv_1").set({
      schoolId: "school_a",
      name: "Lab Microscope",
      category: "Lab & Supplies",
      quantity: 8,
      minimumStock: 3,
      condition: "good",
      assignedTo: "Science Lab",
      notes: "Checked term 1",
      teacherUids: [TEACHER_A],
      adminUids: [ADMIN_A],
      updatedByUid: TEACHER_A,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    teacherADb.collection("inventory_items").doc("inv_1").update({
      quantity: 7,
      updatedByUid: TEACHER_A,
      updatedAt: nowTs(),
    })
  );
  await assertFails(parentADb.collection("inventory_items").doc("inv_1").get());
  await assertFails(parentADb.collection("inventory_items").doc("inv_2").set({
    schoolId: "school_a",
    name: "Unauthorized",
    category: "Other",
    quantity: 1,
    minimumStock: 0,
    condition: "good",
    assignedTo: "",
    notes: "",
    teacherUids: [TEACHER_A],
    adminUids: [ADMIN_A],
    updatedByUid: PARENT_A,
    createdAt: ts,
    updatedAt: ts,
  }));
  await assertFails(teacherADb.collection("inventory_items").doc("inv_1").delete());
  await assertFails(adminBDb.collection("inventory_items").doc("inv_1").get());
  await assertSucceeds(adminADb.collection("inventory_items").doc("inv_1").delete());

  // 3. Fee Records: Admin CRUD ok, Parent reads linked child only, Parent write/tamper denied, School B denied
  await assertSucceeds(
    adminADb.collection("fee_records").doc("fee_1").set({
      schoolId: "school_a",
      studentId: "student_1",
      studentName: "Kofi Mensah",
      className: "Primary 4A",
      title: "Term 1 Tuition",
      term: "Term 1",
      academicYear: "2026/2027",
      amountDueGhs: 650,
      amountPaidGhs: 400,
      status: "partial",
      paymentMethod: "mobile_money",
      receiptNumber: "MM-1001",
      dueDate: "2026-10-30",
      guardianUids: [PARENT_A],
      adminUids: [ADMIN_A],
      recordedByUid: ADMIN_A,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    parentADb.collection("fee_records")
      .where("schoolId", "==", "school_a")
      .where("guardianUids", "array-contains", PARENT_A)
      .get()
  );
  await assertFails(
    parentADb.collection("fee_records").doc("fee_1").update({
      amountPaidGhs: 650,
      status: "paid",
      updatedAt: nowTs(),
    })
  );
  await assertFails(teacherADb.collection("fee_records").doc("fee_1").delete());
  await assertFails(adminBDb.collection("fee_records").doc("fee_1").get());

  // 4. User Device Sessions: Owner only
  await assertSucceeds(
    parentADb.collection("users").doc(PARENT_A).collection("sessions").doc("dev_1").set({
      userId: PARENT_A,
      deviceName: "Pixel 8 Pro",
      platform: "Android 15",
      appVersion: "1.0",
      lastActiveLabel: "2026-11-17 10:00",
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertFails(adminADb.collection("users").doc(PARENT_A).collection("sessions").doc("dev_1").get());
  await assertSucceeds(parentADb.collection("users").doc(PARENT_A).collection("sessions").doc("dev_1").delete());
});



