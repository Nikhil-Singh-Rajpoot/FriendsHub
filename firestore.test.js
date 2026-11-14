const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const CHARLIE_UID = "charlie_789";
const ADMIN_EMAIL = "nikhilkumarsingh2000@gmail.com";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function serverTs(firebaseContext) {
  return new Date();
}

function validUserAccount(uid, overrides = {}) {
  const now = new Date();
  return {
    uid,
    displayName: "Test User",
    email: "user@example.com",
    dateOfBirthMillis: 946684800000,
    isAdultVerified: true,
    accountStatus: "ACTIVE",
    role: "USER",
    pushNotificationsEnabled: true,
    callAlertsEnabled: true,
    meetupRemindersEnabled: true,
    onboardingCompleted: true,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function validUserProfile(uid, overrides = {}) {
  const now = new Date();
  return {
    uid,
    firstName: "Alice",
    age: 25,
    bio: "Looking for coffee buddies",
    cityArea: "Sector 29, Gurugram",
    interests: ["Tea or Coffee", "Conversation"],
    isVerified: false,
    isAvailable: true,
    preferredActivity: "Tea or Coffee",
    availabilityDurationMinutes: 60,
    availabilityExpiresAtMillis: Date.now() + 3600000,
    approxLat: 28.46,
    approxLng: 77.06,
    locationUpdatedAtMillis: Date.now(),
    discoveryOptIn: true,
    accountStatus: "ACTIVE",
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
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

// 1. Unauthenticated bounds
test("Unauthenticated user cannot read profiles or users", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("user_profiles").where("isAvailable", "==", true).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
});

// 2. Private user PII isolation & RBAC escalation prevention
test("User can create own account with USER role, cannot self-assign ADMIN", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(validUserAccount(ALICE_UID)));
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set(validUserAccount(ALICE_UID, { role: "ADMIN" }))
  );
});

test("User cannot read or list another user's private PII doc", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc(BOB_UID).set(validUserAccount(BOB_UID));
  });
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(BOB_UID).get());
  await assertFails(aliceDb.collection("users").get());
});

// 3. Discovery query alignment & 18+ / verified anti-spoof
test("Discovery query succeeds only with active available filter; blanket list fails", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("user_profiles").doc(BOB_UID).set(validUserProfile(BOB_UID));
  });
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("user_profiles").get());
  await assertSucceeds(
    aliceDb.collection("user_profiles")
      .where("isAvailable", "==", true)
      .where("discoveryOptIn", "==", true)
      .where("accountStatus", "==", "ACTIVE")
      .get()
  );
});

test("Underage profile (<18) or self-verified profile fails create", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("user_profiles").doc(ALICE_UID).set(validUserProfile(ALICE_UID, { age: 17 })));
  await assertFails(aliceDb.collection("user_profiles").doc(ALICE_UID).set(validUserProfile(ALICE_UID, { isVerified: true })));
});

// 4. Connection requests, block check, and terminal state lock
test("Connection request create, accept, and block enforcement", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date();
  const reqPayload = {
    fromUid: ALICE_UID,
    toUid: BOB_UID,
    fromName: "Alice",
    toName: "Bob",
    fromCityArea: "Gurugram",
    activity: "Tea",
    message: "Want to grab chai?",
    status: "PENDING",
    participantUids: [ALICE_UID, BOB_UID],
    createdAt: now,
    updatedAt: now,
  };
  await assertSucceeds(aliceDb.collection("connection_requests").doc("req_1").set(reqPayload));

  // Sender cannot self-accept
  await assertFails(aliceDb.collection("connection_requests").doc("req_1").update({ status: "ACCEPTED", updatedAt: new Date() }));

  // Recipient can accept
  await assertSucceeds(bobDb.collection("connection_requests").doc("req_1").update({ status: "ACCEPTED", updatedAt: new Date() }));

  // Terminal state lock: cannot revert ACCEPTED back to PENDING
  await assertFails(bobDb.collection("connection_requests").doc("req_1").update({ status: "PENDING", updatedAt: new Date() }));

  // Bob blocks Alice -> Alice cannot send new request
  await assertSucceeds(
    bobDb.collection("blocked_users").doc(`${BOB_UID}_${ALICE_UID}`).set({
      blockerUid: BOB_UID,
      blockedUid: ALICE_UID,
      blockedName: "Alice",
      createdAt: new Date(),
    })
  );
  await assertFails(aliceDb.collection("connection_requests").doc("req_2").set(reqPayload));
});

// 5. Calls & Meetups gated to active connection participants only
test("Calls and meetups require active connection and participant membership", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const now = new Date();

  await assertSucceeds(
    aliceDb.collection("connections").doc("conn_1").set({
      participantUids: [ALICE_UID, BOB_UID],
      userAUid: ALICE_UID,
      userBUid: BOB_UID,
      userAName: "Alice",
      userBName: "Bob",
      activity: "Coffee",
      requestId: "req_1",
      status: "ACTIVE",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("calls").doc("call_1").set({
      connectionId: "conn_1",
      callerUid: ALICE_UID,
      calleeUid: BOB_UID,
      callerName: "Alice",
      calleeName: "Bob",
      participantUids: [ALICE_UID, BOB_UID],
      status: "RINGING",
      durationSeconds: 0,
      startedAtMillis: 0,
      endedAtMillis: 0,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Outsider Charlie cannot read or update the call
  await assertFails(charlieDb.collection("calls").doc("call_1").get());
  await assertFails(charlieDb.collection("calls").doc("call_1").update({ status: "ENDED", updatedAt: new Date() }));

  // Bob can answer call
  await assertSucceeds(bobDb.collection("calls").doc("call_1").update({ status: "ACTIVE", updatedAt: new Date() }));

  // Meetup creation and confirmation by non-proposer
  await assertSucceeds(
    aliceDb.collection("meetups").doc("meet_1").set({
      connectionId: "conn_1",
      participantUids: [ALICE_UID, BOB_UID],
      proposerUid: ALICE_UID,
      proposerName: "Alice",
      otherUid: BOB_UID,
      otherName: "Bob",
      activity: "Tea",
      scheduledDateText: "Today",
      scheduledTimeText: "6:30 PM",
      publicLocationName: "Blue Tokai Cafe",
      publicArea: "Sector 29, Gurugram",
      notes: "Table near the entrance",
      status: "PROPOSED",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Proposer Alice cannot self-confirm
  await assertFails(aliceDb.collection("meetups").doc("meet_1").update({ status: "CONFIRMED", confirmedByUid: ALICE_UID, updatedAt: new Date() }));

  // Bob confirms
  await assertSucceeds(bobDb.collection("meetups").doc("meet_1").update({ status: "CONFIRMED", confirmedByUid: BOB_UID, updatedAt: new Date() }));
});

// 6. Reports & Admin moderation isolation
test("Ordinary user cannot read reports of others or suspend accounts; verified admin can", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const adminDb = testEnv.authenticatedContext("admin_1", {
    email: ADMIN_EMAIL,
    email_verified: true,
  }).firestore();
  const spoofedAdminDb = testEnv.authenticatedContext("spoof_1", {
    email: ADMIN_EMAIL,
    email_verified: false,
  }).firestore();

  const now = new Date();
  await assertSucceeds(
    aliceDb.collection("reports").doc("rep_1").set({
      reporterUid: ALICE_UID,
      reportedUid: CHARLIE_UID,
      reportedName: "Charlie",
      category: "Spam",
      details: "Repeated unwanted requests",
      status: "OPEN",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertFails(bobDb.collection("reports").doc("rep_1").get());
  await assertFails(spoofedAdminDb.collection("reports").doc("rep_1").get());

  // Even a seeded /admins/{uid} document must NOT grant admin access to another user
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("admins").doc(BOB_UID).set({ role: "ADMIN" });
  });
  const otherVerifiedUserDb = testEnv.authenticatedContext(BOB_UID, {
    email: "other.admin@example.com",
    email_verified: true,
  }).firestore();
  await assertFails(otherVerifiedUserDb.collection("admins").doc(BOB_UID).get());
  await assertFails(otherVerifiedUserDb.collection("reports").doc("rep_1").get());

  await assertSucceeds(adminDb.collection("reports").doc("rep_1").get());
  await assertSucceeds(adminDb.collection("reports").doc("rep_1").update({ status: "ACTIONED", updatedAt: new Date() }));
});
