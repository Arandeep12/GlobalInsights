package com.globalinsights.app.data;

import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

public class FirebaseLocationManager {

    private static final String TAG = "FirebaseLocationManager";

    public interface LocationUpdateCallback {
        void onLocationUpdated(UserLocation location);
        void onError(String errorMessage);
    }

    public interface ActionCallback {
        void onSuccess();
        void onError(String errorMessage);
    }

    private static DocumentReference getLocationDocumentRef() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || user.getUid() == null || user.getUid().isEmpty()) {
            return null;
        }
        return FirebaseFirestore.getInstance()
                .collection("users")
                .document(user.getUid())
                .collection("location")
                .document("current");
    }

    public static void updateLocation(double lat, double lng, boolean isSharing, String countryCode, ActionCallback callback) {
        DocumentReference docRef = getLocationDocumentRef();
        if (docRef == null) {
            String err = "Firestore update failed: FirebaseUser is unauthenticated.";
            Log.e(TAG, err);
            if (callback != null) callback.onError(err);
            return;
        }

        UserLocation loc = new UserLocation(lat, lng, System.currentTimeMillis(), isSharing, countryCode);
        Log.d(TAG, "Writing location to Firestore path: " + docRef.getPath() + " (lat=" + lat + ", lng=" + lng + ", isSharing=" + isSharing + ")");

        docRef.set(loc)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Firestore Location Write SUCCESS: lat=" + lat + ", lng=" + lng + ", isSharing=" + isSharing);
                    if (callback != null) callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Firestore Location Write FAILURE at " + docRef.getPath(), e);
                    String errorMsg = e.getLocalizedMessage() != null ? e.getLocalizedMessage() : "Firestore Write Failed";
                    if (callback != null) callback.onError(errorMsg);
                });
    }

    public static ListenerRegistration listenToLocation(LocationUpdateCallback callback) {
        DocumentReference docRef = getLocationDocumentRef();
        if (docRef == null) {
            String err = "Firestore listener failed: FirebaseUser is unauthenticated.";
            Log.e(TAG, err);
            callback.onError(err);
            return null;
        }

        Log.d(TAG, "Attaching Firestore Snapshot Listener for path: " + docRef.getPath());

        return docRef.addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                Log.e(TAG, "Firestore real-time location snapshot listener error", error);
                callback.onError("Real-time sync error: " + error.getLocalizedMessage());
                return;
            }

            if (snapshot != null && snapshot.exists()) {
                UserLocation location = snapshot.toObject(UserLocation.class);
                if (location != null) {
                    Log.d(TAG, "Firestore Snapshot Received: lat=" + location.getLatitude() + ", lng=" + location.getLongitude() + ", isSharing=" + location.isSharing());
                    callback.onLocationUpdated(location);
                }
            } else {
                Log.d(TAG, "Firestore Snapshot Received: Document does not exist yet.");
            }
        });
    }
}