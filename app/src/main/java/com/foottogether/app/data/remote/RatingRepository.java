package com.foottogether.app.data.remote;

import com.foottogether.app.data.model.Rating;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Évaluations entre joueurs (lot 9). Voir la classe Rating pour les deux emplacements.
 */
public class RatingRepository {

    public static final String SUBCOLLECTION_RATINGS = "ratings";
    private static final int RECEIVED_LIMIT = 200;

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    /**
     * Enregistre une évaluation aux deux endroits, dans un WriteBatch (tout ou rien).
     * Le serveur vérifie (firestore.rules) : les deux joueurs ont participé, le match a commencé,
     * les notes vont de 1 à 5, et l'évaluation n'existe pas déjà.
     */
    public void rate(Rating rating, ResultCallback<Void> callback) {
        DocumentReference inMatch = db.collection(MatchRepository.COLLECTION_MATCHES)
                .document(rating.getMatchId())
                .collection(SUBCOLLECTION_RATINGS)
                .document(rating.getRaterId() + "_" + rating.getRatedId());
        DocumentReference inUser = db.collection(UserRepository.COLLECTION_USERS)
                .document(rating.getRatedId())
                .collection(SUBCOLLECTION_RATINGS)
                .document(rating.getMatchId() + "_" + rating.getRaterId());

        WriteBatch batch = db.batch();
        batch.set(inMatch, rating);
        batch.set(inUser, rating);
        batch.commit()
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }

    /** Temps réel : les joueurs que J'AI déjà évalués dans ce match (leurs uid). */
    public ListenerRegistration listenRatedPlayers(String matchId, String raterId,
                                                  ResultCallback<Set<String>> callback) {
        return db.collection(MatchRepository.COLLECTION_MATCHES).document(matchId)
                .collection(SUBCOLLECTION_RATINGS)
                .whereEqualTo("raterId", raterId)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        callback.onError(error);
                        return;
                    }
                    Set<String> rated = new HashSet<>();
                    if (snapshot != null) {
                        for (DocumentSnapshot doc : snapshot.getDocuments()) {
                            String ratedId = doc.getString("ratedId");
                            if (ratedId != null) rated.add(ratedId);
                        }
                    }
                    callback.onSuccess(rated);
                });
    }

    /** Toutes les évaluations reçues par un joueur (pour ses moyennes et le badge fair-play). */
    public void getReceivedRatings(String uid, ResultCallback<List<Rating>> callback) {
        db.collection(UserRepository.COLLECTION_USERS).document(uid)
                .collection(SUBCOLLECTION_RATINGS)
                .limit(RECEIVED_LIMIT)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Rating> ratings = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        Rating rating = doc.toObject(Rating.class);
                        if (rating != null) ratings.add(rating);
                    }
                    callback.onSuccess(ratings);
                })
                .addOnFailureListener(callback::onError);
    }
}
