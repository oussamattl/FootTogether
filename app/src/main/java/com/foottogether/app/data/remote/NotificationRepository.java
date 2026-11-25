package com.foottogether.app.data.remote;

import com.foottogether.app.data.model.AppNotification;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.List;

/**
 * Notifications d'un joueur dans Firestore : users/{uid}/notifications.
 * L'écriture des notifications se fait dans MatchRepository, dans la même transaction
 * que l'action (rejoindre, annuler...) : pas d'action sans notification, et inversement.
 */
public class NotificationRepository {

    public static final String SUBCOLLECTION_NOTIFICATIONS = "notifications";
    private static final int LIST_LIMIT = 50;

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private CollectionReference notificationsOf(String uid) {
        return db.collection(UserRepository.COLLECTION_USERS).document(uid)
                .collection(SUBCOLLECTION_NOTIFICATIONS);
    }

    /** Écran Notifications : les 50 dernières, de la plus récente à la plus ancienne. */
    public ListenerRegistration listenAll(String uid, ResultCallback<List<AppNotification>> callback) {
        return listen(notificationsOf(uid)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(LIST_LIMIT), callback);
    }

    /** Service : celles qui n'ont pas encore été affichées dans la barre d'Android. */
    public ListenerRegistration listenUndelivered(String uid, ResultCallback<List<AppNotification>> callback) {
        return listen(notificationsOf(uid).whereEqualTo("delivered", false), callback);
    }

    /** Cloche de la barre du haut : y a-t-il des notifications non lues ? */
    public ListenerRegistration listenUnread(String uid, ResultCallback<List<AppNotification>> callback) {
        return listen(notificationsOf(uid).whereEqualTo("read", false).limit(LIST_LIMIT), callback);
    }

    private ListenerRegistration listen(Query query, ResultCallback<List<AppNotification>> callback) {
        return query.addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                callback.onError(error);
                return;
            }
            List<AppNotification> list = new ArrayList<>();
            if (snapshot != null) {
                for (DocumentSnapshot doc : snapshot.getDocuments()) {
                    AppNotification notification = doc.toObject(AppNotification.class);
                    if (notification != null) list.add(notification);
                }
            }
            callback.onSuccess(list);
        });
    }

    /** La notification a été affichée par Android : on ne la montrera plus. */
    public void markDelivered(String uid, String notificationId) {
        notificationsOf(uid).document(notificationId).update("delivered", true);
    }

    /** Touchée dans l'écran Notifications. */
    public void markRead(String uid, String notificationId) {
        notificationsOf(uid).document(notificationId).update("read", true, "delivered", true);
    }

    /** « Tout marquer comme lu » : un seul envoi groupé (WriteBatch). */
    public void markAllRead(String uid, List<AppNotification> notifications) {
        WriteBatch batch = db.batch();
        int count = 0;
        for (AppNotification n : notifications) {
            if (n.isRead() || n.getId() == null) continue;
            batch.update(notificationsOf(uid).document(n.getId()), "read", true, "delivered", true);
            count++;
        }
        if (count > 0) batch.commit();
    }
}
