package com.globalinsights.app.data;

import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;

public class FirebaseNotesManager {

    private static final String TAG = "FirebaseNotesManager";

    public interface NotesCallback {
        void onSuccess(List<Note> notes);
        void onError(String errorMessage);
    }

    public interface NoteActionCallback {
        void onSuccess();
        void onError(String errorMessage);
    }

    private static CollectionReference getUserNotesCollection() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || user.getUid() == null || user.getUid().isEmpty()) {
            return null;
        }
        return FirebaseFirestore.getInstance()
                .collection("users")
                .document(user.getUid())
                .collection("notes");
    }

    public static void saveNote(Note note, NoteActionCallback callback) {
        CollectionReference notesRef = getUserNotesCollection();
        if (notesRef == null) {
            callback.onError("Authentication required. Please wait for Firebase sign-in.");
            return;
        }

        DocumentReference docRef;
        if (note.getId() == null || note.getId().isEmpty()) {
            docRef = notesRef.document();
            note.setId(docRef.getId());
        } else {
            docRef = notesRef.document(note.getId());
        }

        docRef.set(note)
                .addOnSuccessListener(aVoid -> callback.onSuccess())
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to save note to Firestore", e);
                    callback.onError("Failed to save note: " + e.getLocalizedMessage());
                });
    }

    public static void fetchNotes(NotesCallback callback) {
        CollectionReference notesRef = getUserNotesCollection();
        if (notesRef == null) {
            callback.onError("Authentication required. Please wait for Firebase sign-in.");
            return;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String uid = user != null ? user.getUid() : "null";
        String path = notesRef.getPath();

        Log.d(TAG, "=== FIRESTORE FETCH DIAGNOSTICS ===");
        Log.d(TAG, "Authenticated UID: " + uid);
        Log.d(TAG, "Firestore Query Path: " + path);

        notesRef.orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Note> validNotesList = new ArrayList<>();
                    if (queryDocumentSnapshots != null) {
                        Log.d(TAG, "Retrieved Document Count from Firestore: " + queryDocumentSnapshots.size());

                        for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                            Note note = doc.toObject(Note.class);
                            if (note != null) {
                                if (note.getId() == null || note.getId().isEmpty()) {
                                    note.setId(doc.getId());
                                }

                                String title = note.getTitle() != null ? note.getTitle() : "";
                                String content = note.getContent() != null ? note.getContent() : "";
                                String summary = note.getSummary() != null ? note.getSummary() : "";
                                String country = note.getCountryCode() != null ? note.getCountryCode() : "";

                                Log.d(TAG, "----------------------------------------");
                                Log.d(TAG, "Doc ID: " + note.getId());
                                Log.d(TAG, "Title: " + title);
                                Log.d(TAG, "CountryCode: " + country);
                                Log.d(TAG, "Timestamp: " + note.getTimestamp());
                                Log.d(TAG, "Summary: " + summary);
                                Log.d(TAG, "Content Snippet: " + (content.length() > 50 ? content.substring(0, 50) + "..." : content));

                                // Check if document is a malformed diagnostic report note from previous debugging
                                boolean isDiagnosticNote = title.contains("Root Cause") ||
                                        title.contains("Technical Details") ||
                                        content.contains("Root Cause") ||
                                        content.contains("PreferencesManager") ||
                                        content.contains("FirebaseNotesManager") ||
                                        summary.contains("Root Cause") ||
                                        summary.contains("PreferencesManager");

                                if (isDiagnosticNote) {
                                    Log.w(TAG, "Auto-purging malformed diagnostic report note from Cloud Firestore: " + doc.getId());
                                    notesRef.document(doc.getId()).delete()
                                            .addOnSuccessListener(aVoid -> Log.d(TAG, "Purged malformed diagnostic note: " + doc.getId()))
                                            .addOnFailureListener(e -> Log.e(TAG, "Failed to purge malformed note: " + doc.getId(), e));
                                } else {
                                    validNotesList.add(note);
                                }
                            }
                        }
                    }
                    callback.onSuccess(validNotesList);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to fetch notes from Firestore", e);
                    callback.onError("Failed to fetch notes: " + e.getLocalizedMessage());
                });
    }

    public static void deleteNote(String noteId, NoteActionCallback callback) {
        CollectionReference notesRef = getUserNotesCollection();
        if (notesRef == null || noteId == null || noteId.isEmpty()) {
            callback.onError("Invalid note or unauthenticated user.");
            return;
        }

        notesRef.document(noteId)
                .delete()
                .addOnSuccessListener(aVoid -> callback.onSuccess())
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to delete note from Firestore", e);
                    callback.onError("Failed to delete note: " + e.getLocalizedMessage());
                });
    }
}