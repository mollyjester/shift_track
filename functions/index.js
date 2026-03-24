const { onDocumentWritten } = require("firebase-functions/v2/firestore");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

initializeApp();

/**
 * Sends an FCM data message to all spectators of a host user when their
 * shifts, leaves, or overtime data changes in Firestore.
 *
 * Triggered by any write (create/update/delete) to:
 *   users/{hostUid}/shifts/{date}
 *   users/{hostUid}/leaves/{date}
 *   users/{hostUid}/overtime/{date}
 *
 * The message is data-only (no notification payload) so the Android client's
 * ShiftTrackMessagingService.onMessageReceived() handles it by scheduling
 * an immediate SyncWorker run + widget refresh.
 */
const notifySpectators = async (hostUid) => {
  const db = getFirestore();
  const messaging = getMessaging();

  // Read the host's user document to get the spectators list.
  const hostDoc = await db.collection("users").doc(hostUid).get();
  if (!hostDoc.exists) return;

  const spectators = hostDoc.data().spectators || [];
  if (spectators.length === 0) return;

  // Collect FCM tokens for all spectators.
  const tokens = [];
  for (const spectatorUid of spectators) {
    const specDoc = await db.collection("users").doc(spectatorUid).get();
    if (specDoc.exists && specDoc.data().fcmToken) {
      tokens.push(specDoc.data().fcmToken);
    }
  }

  if (tokens.length === 0) return;

  // Send a data-only message to each spectator.
  const message = {
    data: {
      type: "host_data_changed",
      hostUid: hostUid,
    },
    tokens: tokens,
  };

  const response = await messaging.sendEachForMulticast(message);

  // Clean up invalid tokens.
  if (response.failureCount > 0) {
    response.responses.forEach((resp, idx) => {
      if (resp.error) {
        const code = resp.error.code;
        if (
          code === "messaging/invalid-registration-token" ||
          code === "messaging/registration-token-not-registered"
        ) {
          // Token is stale — remove it from the spectator's user doc.
          const staleToken = tokens[idx];
          const staleUid = spectators[idx];
          if (staleUid) {
            db.collection("users")
              .doc(staleUid)
              .update({ fcmToken: FieldValue.delete() })
              .catch(() => {});
          }
        }
      }
    });
  }
};

exports.onShiftWrite = onDocumentWritten(
  "users/{hostUid}/shifts/{date}",
  (event) => notifySpectators(event.params.hostUid),
);

exports.onLeaveWrite = onDocumentWritten(
  "users/{hostUid}/leaves/{date}",
  (event) => notifySpectators(event.params.hostUid),
);

exports.onOvertimeWrite = onDocumentWritten(
  "users/{hostUid}/overtime/{date}",
  (event) => notifySpectators(event.params.hostUid),
);
