const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_1";
const BOB_UID = "bob_uid_2";
const CHARLIE_UID = "charlie_uid_3";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastDate(offsetMs = 5000) {
  return new Date(Date.now() - offsetMs);
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

test("Unauthenticated users cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").where("discoverable", "==", true).get());
  await assertFails(unauthDb.collection("conversations").where("memberUids", "array-contains", ALICE_UID).get());
  await assertFails(unauthDb.collection("calls").where("participantUids", "array-contains", ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set({
      uid: ALICE_UID,
      displayName: "Alice",
      bio: "Hi",
      avatarColorHex: "#0D7C7B",
      showLastSeen: true,
      online: true,
      discoverable: true,
      lastSeenAt: pastDate(),
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );
});

test("UserProfile: owner create, partial update, directory list, and cross-user denial", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = pastDate(10000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      uid: ALICE_UID,
      displayName: "Alice Rahman",
      bio: "Available on Tammie",
      avatarColorHex: "#0D7C7B",
      showLastSeen: true,
      online: true,
      discoverable: true,
      lastSeenAt: created,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Filtered directory list succeeds; unfiltered fails
  await assertSucceeds(aliceDb.collection("users").where("discoverable", "==", true).get());
  await assertFails(aliceDb.collection("users").get());

  // Partial profile update succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      bio: "Encrypted & calm",
      showLastSeen: false,
      updatedAt: pastDate(1000),
    })
  );

  // Reject ghost field / future timestamp / cross-user edit
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: pastDate(500),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: pastDate(200),
      updatedAt: pastDate(200),
    })
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      displayName: "Hacked Alice",
      updatedAt: pastDate(500),
    })
  );
});

test("Conversations & Messages: member isolation, admin-only group edits, and attachment payloads", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const created = pastDate(15000);
  const convId = "grp_tammie_team";

  await assertSucceeds(
    aliceDb.collection("conversations").doc(convId).set({
      type: "GROUP",
      title: "Tammie Core",
      accentColorHex: "#0D7C7B",
      memberUids: [ALICE_UID, BOB_UID],
      memberNames: {
        [ALICE_UID]: "Alice",
        [BOB_UID]: "Bob",
      },
      adminUid: ALICE_UID,
      encryptionSalt: "tammie_salt_0123456789abcdef",
      lastMessageCiphertext: "",
      lastMessageIv: "",
      lastMessageType: "NONE",
      lastMessageSenderId: "",
      lastMessageAt: created,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Scoped conversation list succeeds; unfiltered fails; non-member fails
  await assertSucceeds(bobDb.collection("conversations").where("memberUids", "array-contains", BOB_UID).get());
  await assertFails(bobDb.collection("conversations").get());
  await assertFails(charlieDb.collection("conversations").doc(convId).get());

  // Non-admin member cannot rename group, but can update lastMessage metadata
  await assertFails(
    bobDb.collection("conversations").doc(convId).update({
      title: "Renamed by Bob",
      updatedAt: pastDate(2000),
    })
  );
  await assertSucceeds(
    aliceDb.collection("conversations").doc(convId).update({
      title: "Tammie Family",
      updatedAt: pastDate(2000),
    })
  );

  // Send encrypted DOCUMENT message mirroring Kotlin payload (no `id` key, null keys omitted)
  const msgRef = bobDb.collection("conversations").doc(convId).collection("messages").doc("msg_1");
  await assertSucceeds(
    msgRef.set({
      senderId: BOB_UID,
      senderName: "Bob",
      memberUids: [ALICE_UID, BOB_UID],
      messageType: "DOCUMENT",
      ciphertext: "base64ciphertext==",
      iv: "base64iv12345",
      encryptionVersion: 1,
      attachmentUrl: "https://firebasestorage.googleapis.com/v0/b/test/o/doc.pdf",
      attachmentPath: `chat_attachments/${convId}/${BOB_UID}/doc.pdf`,
      attachmentName: "project_plan.pdf",
      attachmentMimeType: "application/pdf",
      attachmentSizeBytes: 245760,
      createdAt: pastDate(1000),
    })
  );

  // Update conversation lastMessage metadata as member Bob
  await assertSucceeds(
    bobDb.collection("conversations").doc(convId).update({
      lastMessageCiphertext: "base64ciphertext==",
      lastMessageIv: "base64iv12345",
      lastMessageType: "DOCUMENT",
      lastMessageSenderId: BOB_UID,
      lastMessageAt: pastDate(1000),
      updatedAt: pastDate(1000),
    })
  );

  // Scoped messages list succeeds; Charlie denied on direct get and unfiltered list; sender spoofing denied; message update denied
  await assertSucceeds(
    aliceDb.collection("conversations").doc(convId).collection("messages")
      .where("memberUids", "array-contains", ALICE_UID).get()
  );
  await assertFails(
    charlieDb.collection("conversations").doc(convId).collection("messages").doc("msg_1").get()
  );
  await assertFails(
    charlieDb.collection("conversations").doc(convId).collection("messages").get()
  );
  await assertFails(
    charlieDb.collection("conversations").doc(convId).collection("messages").doc("msg_bad").set({
      senderId: ALICE_UID,
      senderName: "Alice",
      memberUids: [ALICE_UID, BOB_UID],
      messageType: "TEXT",
      ciphertext: "spoof",
      iv: "base64iv12345",
      encryptionVersion: 1,
      createdAt: pastDate(500),
    })
  );
  await assertFails(msgRef.update({ ciphertext: "tampered" }));
});

test("Calls: signaling lifecycle, terminal state locking, and non-participant denial", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const convId = "direct_alice_bob";
  const created = pastDate(10000);

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("conversations").doc(convId).set({
      type: "DIRECT",
      title: "",
      accentColorHex: "#0D7C7B",
      memberUids: [ALICE_UID, BOB_UID],
      memberNames: { [ALICE_UID]: "Alice", [BOB_UID]: "Bob" },
      adminUid: ALICE_UID,
      encryptionSalt: "salt_direct_0123456789abcdef",
      lastMessageCiphertext: "",
      lastMessageIv: "",
      lastMessageType: "NONE",
      lastMessageSenderId: "",
      lastMessageAt: created,
      createdAt: created,
      updatedAt: created,
    });
  });

  const callRef = aliceDb.collection("calls").doc("call_1");
  await assertSucceeds(
    callRef.set({
      conversationId: convId,
      callerUid: ALICE_UID,
      callerName: "Alice",
      calleeNames: "Bob",
      calleeUids: [BOB_UID],
      participantUids: [ALICE_UID, BOB_UID],
      callType: "VIDEO",
      status: "RINGING",
      startedAt: pastDate(4000),
      updatedAt: pastDate(4000),
    })
  );

  // Scoped call query succeeds; Charlie cannot read or join
  await assertSucceeds(bobDb.collection("calls").where("participantUids", "array-contains", BOB_UID).get());
  await assertFails(charlieDb.collection("calls").doc("call_1").get());

  // Bob accepts, then Alice ends
  await assertSucceeds(
    bobDb.collection("calls").doc("call_1").update({
      status: "CONNECTED",
      updatedAt: pastDate(2000),
    })
  );
  await assertSucceeds(
    callRef.update({
      status: "ENDED",
      endedAt: pastDate(1000),
      updatedAt: pastDate(1000),
    })
  );

  // Terminal state lock rejects any subsequent status change
  await assertFails(
    bobDb.collection("calls").doc("call_1").update({
      status: "CONNECTED",
      updatedAt: pastDate(500),
    })
  );
});
