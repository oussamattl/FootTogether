package com.foottogether.app.data.remote;

import com.foottogether.app.R;
import com.foottogether.app.data.model.AppNotification;
import com.foottogether.app.data.model.Match;
import com.foottogether.app.data.model.Participant;
import com.foottogether.app.data.model.User;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.Transaction;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Accès aux matchs dans Cloud Firestore (collection "matches").
 */
public class MatchRepository {

    public static final String COLLECTION_MATCHES = "matches";
    public static final String SUBCOLLECTION_PARTICIPANTS = "participants";

    private static final int UPCOMING_LIMIT = 50;
    private static final int MY_MATCHES_LIMIT = 100;

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    /**
     * Crée un match. L'organisateur en est automatiquement le premier participant (1/N).
     *
     * On utilise un WriteBatch : plusieurs écritures envoyées ENSEMBLE au serveur.
     * Soit elles réussissent toutes, soit aucune n'est appliquée (pas de match à moitié créé) :
     *   1. le document du match ;
     *   2. la fiche participant de l'organisateur ;
     *   3. +1 sur le compteur matchesOrganized de son profil.
     *
     * @param match     match déjà rempli par le formulaire (titre, date, lieu, format...)
     * @param organizer profil de l'utilisateur connecté
     * @param callback  reçoit l'identifiant du match créé
     */
    public void createMatch(Match match, User organizer, ResultCallback<String> callback) {
        // document() sans argument : Firestore génère un identifiant unique
        DocumentReference matchRef = db.collection(COLLECTION_MATCHES).document();

        match.setOrganizerId(organizer.getUid());
        match.setOrganizerPseudo(organizer.getPseudo());
        match.setStatus(Match.STATUS_OPEN);
        match.setParticipantCount(1);
        match.setParticipantIds(new ArrayList<>(Collections.singletonList(organizer.getUid())));
        match.setFollowerIds(new ArrayList<>());

        DocumentReference participantRef = matchRef
                .collection(SUBCOLLECTION_PARTICIPANTS)
                .document(organizer.getUid());
        DocumentReference userRef = db.collection(UserRepository.COLLECTION_USERS)
                .document(organizer.getUid());

        WriteBatch batch = db.batch();
        batch.set(matchRef, match);
        batch.set(participantRef, Participant.fromUser(organizer));
        // FieldValue.increment : le serveur ajoute 1, même si deux écritures arrivent en même temps
        batch.update(userRef, "matchesOrganized", FieldValue.increment(1));

        batch.commit()
                .addOnSuccessListener(unused -> callback.onSuccess(matchRef.getId()))
                .addOnFailureListener(callback::onError);
    }

    /**
     * Écoute EN TEMPS RÉEL les matchs à venir (ou en cours), triés par date.
     * Firestore rappelle onSuccess à chaque changement côté serveur : nouveau match,
     * quelqu'un qui rejoint (compteur), annulation... sans rien recharger à la main.
     *
     * Hors ligne, Firestore renvoie les dernières données en cache.
     *
     * @return l'abonnement ; il FAUT appeler remove() quand l'écran n'est plus visible
     */
    public ListenerRegistration listenUpcomingMatches(ResultCallback<List<Match>> callback) {
        return listenUpcomingMatches(false, callback);
    }

    /**
     * @param skipEmptyLocalResult vrai : si Firestore n'a qu'une réponse VIDE tirée de son cache
     *                             (hors ligne, rien en mémoire), on ne la transmet pas, pour ne pas
     *                             effacer la liste déjà affichée depuis SQLite (Accueil)
     */
    public ListenerRegistration listenUpcomingMatches(boolean skipEmptyLocalResult,
                                                      ResultCallback<List<Match>> callback) {
        // On garde aussi les matchs commencés depuis moins de 90 min (« en cours »)
        Date from = new Date(System.currentTimeMillis() - Match.DURATION_MILLIS);
        return db.collection(COLLECTION_MATCHES)
                .whereGreaterThanOrEqualTo("dateTime", new Timestamp(from))
                .orderBy("dateTime", Query.Direction.ASCENDING)
                .limit(UPCOMING_LIMIT)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        callback.onError(error);
                        return;
                    }
                    if (skipEmptyLocalResult && snapshot != null && snapshot.isEmpty()
                            && snapshot.getMetadata().isFromCache()) {
                        return; // on attend la vraie réponse du serveur
                    }
                    List<Match> matches = new ArrayList<>();
                    if (snapshot != null) {
                        for (DocumentSnapshot doc : snapshot.getDocuments()) {
                            Match match = doc.toObject(Match.class);
                            if (match != null) matches.add(match);
                        }
                    }
                    callback.onSuccess(matches);
                });
    }

    /**
     * Écoute EN TEMPS RÉEL un seul match (écran Détail) : le compteur, le statut...
     * onSuccess reçoit null si le match n'existe pas (ou plus).
     */
    public ListenerRegistration listenMatch(String matchId, ResultCallback<Match> callback) {
        return db.collection(COLLECTION_MATCHES).document(matchId)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        callback.onError(error);
                        return;
                    }
                    Match match = (snapshot != null && snapshot.exists())
                            ? snapshot.toObject(Match.class) : null;
                    callback.onSuccess(match);
                });
    }

    /** Écoute EN TEMPS RÉEL la liste des participants d'un match, dans l'ordre d'inscription. */
    public ListenerRegistration listenParticipants(String matchId,
                                                   ResultCallback<List<Participant>> callback) {
        return db.collection(COLLECTION_MATCHES).document(matchId)
                .collection(SUBCOLLECTION_PARTICIPANTS)
                .orderBy("joinedAt", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        callback.onError(error);
                        return;
                    }
                    List<Participant> participants = new ArrayList<>();
                    if (snapshot != null) {
                        for (DocumentSnapshot doc : snapshot.getDocuments()) {
                            Participant participant = doc.toObject(Participant.class);
                            if (participant != null) participants.add(participant);
                        }
                    }
                    callback.onSuccess(participants);
                });
    }

    /**
     * Rejoindre un match, dans une TRANSACTION Firestore.
     *
     * Pourquoi une transaction ? Si Mehdi et Lina touchent « Rejoindre » en même temps
     * pour la dernière place, chacun lit « 9/10 ». Sans transaction, les deux écriraient
     * « 10/10 » et on aurait 11 joueurs. Avec runTransaction, Firestore voit que le match
     * a changé entre la lecture et l'écriture : il relance la transaction du second joueur,
     * qui relit alors « 10/10 » et reçoit « match complet ».
     *
     * Hors ligne, une transaction échoue (elle a besoin du serveur) : c'est voulu, on
     * n'affiche jamais « Inscription confirmée » sans l'accord du serveur.
     */
    public void joinMatch(String matchId, String uid, ResultCallback<Void> callback) {
        DocumentReference matchRef = db.collection(COLLECTION_MATCHES).document(matchId);
        DocumentReference participantRef = matchRef.collection(SUBCOLLECTION_PARTICIPANTS).document(uid);
        DocumentReference userRef = db.collection(UserRepository.COLLECTION_USERS).document(uid);

        db.runTransaction((Transaction.Function<Void>) transaction -> {
            // 1. LECTURES (dans une transaction, on lit toujours AVANT d'écrire)
            Match match = transaction.get(matchRef).toObject(Match.class);
            User user = transaction.get(userRef).toObject(User.class);
            if (match == null) throw new MatchActionException(R.string.error_match_not_found);
            if (user == null) throw new MatchActionException(R.string.error_profile_load);

            // 2. VÉRIFICATIONS sur les données fraîches du serveur
            List<String> ids = safeList(match.getParticipantIds());
            if (ids.contains(uid)) throw new MatchActionException(R.string.error_already_joined);
            if (match.isCancelled()) throw new MatchActionException(R.string.error_match_closed);
            if (match.isStarted()) throw new MatchActionException(R.string.error_match_started);
            if (!Match.STATUS_OPEN.equals(match.getStatus()) || ids.size() >= match.getMaxPlayers()) {
                throw new MatchActionException(R.string.error_join_full);
            }

            // 3. ÉCRITURES (appliquées toutes ensemble, ou pas du tout)
            ids.add(uid);
            long newCount = ids.size();
            Map<String, Object> updates = new HashMap<>();
            updates.put("participantCount", newCount);
            updates.put("participantIds", ids);
            // Dernière place prise : le match passe « Complet »
            updates.put("status", newCount >= match.getMaxPlayers() ? Match.STATUS_FULL : Match.STATUS_OPEN);
            updates.put("updatedAt", FieldValue.serverTimestamp());

            // Il suivait ce match complet ? Il le rejoint : plus besoin de le prévenir
            List<String> followers = safeList(match.getFollowerIds());
            if (followers.remove(uid)) updates.put("followerIds", followers);

            transaction.update(matchRef, updates);
            transaction.set(participantRef, Participant.fromUser(user));
            transaction.update(userRef, "matchesPlayed", FieldValue.increment(1));

            // Notifications (lot 6), dans la MÊME transaction : une pour soi (historique)
            // et une pour l'organisateur (« Mehdi a rejoint ton match »)
            notify(transaction, uid, new AppNotification(AppNotification.TYPE_JOIN_CONFIRMED,
                    matchId, match.getTitle(), uid, user.getPseudo()));
            if (!uid.equals(match.getOrganizerId())) {
                notify(transaction, match.getOrganizerId(), new AppNotification(
                        AppNotification.TYPE_NEW_PARTICIPANT, matchId, match.getTitle(), uid, user.getPseudo()));
            }
            return null;
        })
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }

    /**
     * Quitter un match : transaction inverse de joinMatch.
     * Interdit à l'organisateur (lui, il annule le match) et une fois le match commencé.
     */
    public void leaveMatch(String matchId, String uid, ResultCallback<Void> callback) {
        DocumentReference matchRef = db.collection(COLLECTION_MATCHES).document(matchId);
        DocumentReference participantRef = matchRef.collection(SUBCOLLECTION_PARTICIPANTS).document(uid);
        DocumentReference userRef = db.collection(UserRepository.COLLECTION_USERS).document(uid);

        db.runTransaction((Transaction.Function<Void>) transaction -> {
            Match match = transaction.get(matchRef).toObject(Match.class);
            User user = transaction.get(userRef).toObject(User.class);
            if (match == null) throw new MatchActionException(R.string.error_match_not_found);

            List<String> ids = safeList(match.getParticipantIds());
            if (!ids.contains(uid)) throw new MatchActionException(R.string.error_not_participant);
            boolean wasFull = ids.size() >= match.getMaxPlayers();
            if (uid.equals(match.getOrganizerId())) throw new MatchActionException(R.string.error_organizer_leave);
            if (match.isCancelled()) throw new MatchActionException(R.string.error_match_closed);
            if (match.isStarted()) throw new MatchActionException(R.string.error_match_started);

            ids.remove(uid);
            Map<String, Object> updates = new HashMap<>();
            updates.put("participantCount", (long) ids.size());
            updates.put("participantIds", ids);
            // Une place se libère : le match redevient ouvert
            updates.put("status", Match.STATUS_OPEN);
            updates.put("updatedAt", FieldValue.serverTimestamp());

            transaction.update(matchRef, updates);
            transaction.delete(participantRef);
            transaction.update(userRef, "matchesPlayed", FieldValue.increment(-1));

            // Le match était complet : on prévient ceux qui attendaient une place (followerIds)
            if (wasFull) {
                String pseudo = user == null ? "" : user.getPseudo();
                for (String followerId : safeList(match.getFollowerIds())) {
                    if (followerId.equals(uid)) continue;
                    notify(transaction, followerId, new AppNotification(
                            AppNotification.TYPE_SPOT_AVAILABLE, matchId, match.getTitle(), uid, pseudo));
                }
            }
            return null;
        })
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }

    // ===== Lot 4 : Mes matchs, modifier, annuler =====

    /**
     * Écoute EN TEMPS RÉEL « mes matchs » :
     * - organized = false : les matchs où je suis inscrit (whereArrayContains sur participantIds) ;
     * - organized = true  : les matchs que j'organise (whereEqualTo sur organizerId).
     * Pas de orderBy ici : combiner un filtre et un tri sur deux champs différents
     * demanderait un index composite à Firestore. On trie donc sur le téléphone.
     */
    public ListenerRegistration listenMyMatches(String uid, boolean organized,
                                                ResultCallback<List<Match>> callback) {
        Query query = organized
                ? db.collection(COLLECTION_MATCHES).whereEqualTo("organizerId", uid)
                : db.collection(COLLECTION_MATCHES).whereArrayContains("participantIds", uid);
        return query.limit(MY_MATCHES_LIMIT).addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                callback.onError(error);
                return;
            }
            List<Match> matches = new ArrayList<>();
            if (snapshot != null) {
                for (DocumentSnapshot doc : snapshot.getDocuments()) {
                    Match match = doc.toObject(Match.class);
                    if (match != null) matches.add(match);
                }
            }
            callback.onSuccess(matches);
        });
    }

    /** Lit une seule fois un match (pour pré-remplir le formulaire de modification). */
    public void getMatch(String matchId, ResultCallback<Match> callback) {
        db.collection(COLLECTION_MATCHES).document(matchId).get()
                .addOnSuccessListener(snapshot -> {
                    Match match = snapshot.exists() ? snapshot.toObject(Match.class) : null;
                    if (match == null) {
                        callback.onError(new MatchActionException(R.string.error_match_not_found));
                    } else {
                        callback.onSuccess(match);
                    }
                })
                .addOnFailureListener(callback::onError);
    }

    /**
     * L'organisateur modifie son match, en TRANSACTION : on relit le nombre d'inscrits
     * pour vérifier que le nouveau maximum n'est pas plus petit, et pour recalculer
     * le statut (OPEN / FULL) avec le compteur à jour.
     *
     * @param edited match contenant les nouvelles valeurs du formulaire
     */
    public void updateMatchDetails(String matchId, String uid, Match edited, ResultCallback<Void> callback) {
        DocumentReference matchRef = db.collection(COLLECTION_MATCHES).document(matchId);
        db.runTransaction((Transaction.Function<Void>) transaction -> {
            Match current = transaction.get(matchRef).toObject(Match.class);
            if (current == null) throw new MatchActionException(R.string.error_match_not_found);
            if (!uid.equals(current.getOrganizerId())) throw new MatchActionException(R.string.error_not_organizer);
            if (current.isCancelled()) throw new MatchActionException(R.string.error_match_closed);
            if (current.isStarted()) throw new MatchActionException(R.string.error_match_started);
            if (edited.getMaxPlayers() < current.getParticipantCount()) {
                throw new MatchActionException(R.string.error_max_below_count);
            }

            Map<String, Object> updates = new HashMap<>();
            updates.put("title", edited.getTitle());
            updates.put("description", edited.getDescription());
            updates.put("dateTime", edited.getDateTime());
            updates.put("address", edited.getAddress());
            updates.put("city", edited.getCity());
            updates.put("lat", edited.getLat());
            updates.put("lng", edited.getLng());
            updates.put("format", edited.getFormat());
            updates.put("maxPlayers", edited.getMaxPlayers());
            updates.put("level", edited.getLevel());
            updates.put("status", current.getParticipantCount() >= edited.getMaxPlayers()
                    ? Match.STATUS_FULL : Match.STATUS_OPEN);
            updates.put("updatedAt", FieldValue.serverTimestamp());
            transaction.update(matchRef, updates);

            // Une notification par participant (sauf l'organisateur lui-même)
            for (String participantId : safeList(current.getParticipantIds())) {
                if (participantId.equals(uid)) continue;
                notify(transaction, participantId, new AppNotification(AppNotification.TYPE_MATCH_UPDATED,
                        matchId, edited.getTitle(), uid, current.getOrganizerPseudo()));
            }
            return null;
        })
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }

    /**
     * L'organisateur annule son match : statut CANCELLED (on ne supprime jamais un match,
     * les participants doivent pouvoir voir qu'il est annulé).
     */
    public void cancelMatch(String matchId, String uid, ResultCallback<Void> callback) {
        DocumentReference matchRef = db.collection(COLLECTION_MATCHES).document(matchId);
        db.runTransaction((Transaction.Function<Void>) transaction -> {
            Match current = transaction.get(matchRef).toObject(Match.class);
            if (current == null) throw new MatchActionException(R.string.error_match_not_found);
            if (!uid.equals(current.getOrganizerId())) throw new MatchActionException(R.string.error_not_organizer);
            if (current.isCancelled()) throw new MatchActionException(R.string.error_match_closed);
            if (current.isStarted()) throw new MatchActionException(R.string.error_match_started);

            Map<String, Object> updates = new HashMap<>();
            updates.put("status", Match.STATUS_CANCELLED);
            updates.put("updatedAt", FieldValue.serverTimestamp());
            transaction.update(matchRef, updates);

            // Participants ET joueurs qui attendaient une place : tous sont prévenus
            List<String> recipients = safeList(current.getParticipantIds());
            for (String followerId : safeList(current.getFollowerIds())) {
                if (!recipients.contains(followerId)) recipients.add(followerId);
            }
            for (String recipientId : recipients) {
                if (recipientId.equals(uid)) continue;
                notify(transaction, recipientId, new AppNotification(AppNotification.TYPE_MATCH_CANCELLED,
                        matchId, current.getTitle(), uid, current.getOrganizerPseudo()));
            }
            return null;
        })
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }

    // ===== Lot 6 : suivre un match complet, notifications, rappels =====

    /**
     * Suivre (ou ne plus suivre) un match COMPLET : on ajoute / retire son uid de followerIds.
     * Quand un joueur quittera ce match, il écrira une notification « place libérée » à chaque suiveur.
     * arrayUnion / arrayRemove : le serveur modifie la liste sans écraser les autres uid.
     */
    public void setFollowing(String matchId, String uid, boolean follow, ResultCallback<Void> callback) {
        db.collection(COLLECTION_MATCHES).document(matchId)
                .update("followerIds", follow ? FieldValue.arrayUnion(uid) : FieldValue.arrayRemove(uid))
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }

    /**
     * Lit une fois les matchs où je suis inscrit (l'organisateur est aussi dans participantIds).
     * Sert à remettre les rappels à jour (match déplacé, annulé, autre téléphone...).
     */
    public void getMyParticipations(String uid, ResultCallback<List<Match>> callback) {
        db.collection(COLLECTION_MATCHES)
                .whereArrayContains("participantIds", uid)
                .limit(MY_MATCHES_LIMIT)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Match> matches = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        Match match = doc.toObject(Match.class);
                        if (match != null) matches.add(match);
                    }
                    callback.onSuccess(matches);
                })
                .addOnFailureListener(callback::onError);
    }

    /**
     * Écrit une notification chez un joueur, DANS la transaction en cours :
     * si l'action échoue, la notification n'est pas créée (et inversement).
     */
    private void notify(Transaction transaction, String recipientUid, AppNotification notification) {
        DocumentReference ref = db.collection(UserRepository.COLLECTION_USERS).document(recipientUid)
                .collection(NotificationRepository.SUBCOLLECTION_NOTIFICATIONS).document();
        transaction.set(ref, notification);
    }

    /** Copie modifiable d'une liste (ou liste vide si le champ manque dans Firestore). */
    private static List<String> safeList(List<String> list) {
        return list == null ? new ArrayList<>() : new ArrayList<>(list);
    }
}
