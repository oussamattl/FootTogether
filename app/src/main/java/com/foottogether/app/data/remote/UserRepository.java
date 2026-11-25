package com.foottogether.app.data.remote;

import android.util.Log;

import androidx.annotation.Nullable;

import com.foottogether.app.data.model.User;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Accès aux profils des joueurs dans Cloud Firestore (collection "users").
 * Les écrans ne parlent jamais directement à Firestore : ils passent par cette classe.
 */
public class UserRepository {

    private static final String TAG = "UserRepository";
    public static final String COLLECTION_USERS = "users";

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    /**
     * Classement (lot 9) : les joueurs qui ont le plus de matchs, triés du plus grand au plus petit.
     * Un tri sur un seul champ ne demande pas d'index composite à Firestore.
     *
     * @param field "matchesPlayed" (joueurs) ou "matchesOrganized" (organisateurs)
     */
    public void getTopUsers(String field, int limit, ResultCallback<List<User>> callback) {
        db.collection(COLLECTION_USERS)
                .orderBy(field, Query.Direction.DESCENDING)
                .limit(limit)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<User> users = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        User user = doc.toObject(User.class);
                        if (user != null) users.add(user);
                    }
                    callback.onSuccess(users);
                })
                .addOnFailureListener(callback::onError);
    }

    /** Crée (ou remplace) le document users/{uid} du joueur. */
    public void createUser(User user, ResultCallback<Void> callback) {
        db.collection(COLLECTION_USERS)
                .document(user.getUid())
                .set(user)
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }

    /** Lit une seule fois le profil d'un joueur. */
    public void getUser(String uid, ResultCallback<User> callback) {
        db.collection(COLLECTION_USERS)
                .document(uid)
                .get()
                .addOnSuccessListener((DocumentSnapshot snapshot) -> {
                    // toObject() remplit un objet User à partir des champs du document
                    User user = snapshot.exists() ? snapshot.toObject(User.class) : null;
                    if (user == null) {
                        callback.onError(new IllegalStateException("Profil introuvable"));
                    } else {
                        callback.onSuccess(user);
                    }
                })
                .addOnFailureListener(callback::onError);
    }

    /**
     * Écoute EN TEMPS RÉEL son profil : les compteurs (matchs joués / organisés)
     * et la photo se mettent à jour tout seuls sur l'écran Profil.
     */
    public ListenerRegistration listenUser(String uid, ResultCallback<User> callback) {
        return db.collection(COLLECTION_USERS).document(uid)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        callback.onError(error);
                        return;
                    }
                    User user = (snapshot != null && snapshot.exists()) ? snapshot.toObject(User.class) : null;
                    if (user != null) callback.onSuccess(user);
                });
    }

    /**
     * Enregistre les infos modifiables du profil (pas l'e-mail, géré par Firebase Auth,
     * ni les compteurs, gérés par les transactions des matchs).
     * update() ne modifie QUE les champs donnés, contrairement à set() qui remplace tout.
     */
    public void updateProfile(User user, ResultCallback<Void> callback) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("pseudo", user.getPseudo());
        fields.put("name", user.getName());
        fields.put("city", user.getCity());
        fields.put("position", user.getPosition());
        fields.put("level", user.getLevel());
        db.collection(COLLECTION_USERS).document(user.getUid())
                .update(fields)
                .addOnSuccessListener(unused -> {
                    syncCopies(user.getUid());
                    callback.onSuccess(null);
                })
                .addOnFailureListener(callback::onError);
    }

    /**
     * Enregistre la photo de profil (ou la supprime si photo == null).
     * @param photo     photo 256×256 en Base64
     * @param thumbnail miniature 96×96 en Base64
     */
    public void updatePhoto(String uid, @Nullable String photo, @Nullable String thumbnail,
                            ResultCallback<Void> callback) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("photoBase64", photo);
        fields.put("photoThumbBase64", thumbnail);
        db.collection(COLLECTION_USERS).document(uid)
                .update(fields)
                .addOnSuccessListener(unused -> {
                    syncCopies(uid);
                    callback.onSuccess(null);
                })
                .addOnFailureListener(callback::onError);
    }

    /**
     * Le pseudo, le poste, le niveau et la miniature sont RECOPIÉS dans les fiches participants
     * (et le pseudo dans organizerPseudo des matchs organisés), pour afficher les listes sans
     * relire chaque profil. Après une modification, on met ces copies à jour en un WriteBatch.
     *
     * C'est fait « au mieux » : si ça échoue (hors ligne...), le profil lui-même est bien
     * enregistré ; seules les anciennes copies garderaient l'ancienne valeur.
     */
    private void syncCopies(String uid) {
        DocumentReference userRef = db.collection(COLLECTION_USERS).document(uid);
        userRef.get().addOnSuccessListener(userSnapshot -> {
            User user = userSnapshot.toObject(User.class);
            if (user == null) return;
            db.collection(MatchRepository.COLLECTION_MATCHES)
                    .whereArrayContains("participantIds", uid)
                    .get()
                    .addOnSuccessListener((QuerySnapshot matches) -> {
                        WriteBatch batch = db.batch();
                        Map<String, Object> participantFields = new HashMap<>();
                        participantFields.put("pseudo", user.getPseudo());
                        participantFields.put("position", user.getPosition());
                        participantFields.put("level", user.getLevel());
                        participantFields.put("photoThumbBase64", user.getPhotoThumbBase64());
                        for (DocumentSnapshot match : matches.getDocuments()) {
                            batch.update(match.getReference()
                                    .collection(MatchRepository.SUBCOLLECTION_PARTICIPANTS)
                                    .document(uid), participantFields);
                            if (uid.equals(match.getString("organizerId"))
                                    && !user.getPseudo().equals(match.getString("organizerPseudo"))) {
                                batch.update(match.getReference(), "organizerPseudo", user.getPseudo());
                            }
                        }
                        batch.commit().addOnFailureListener(e ->
                                Log.w(TAG, "Copies du profil non mises à jour", e));
                    });
        });
    }
}
