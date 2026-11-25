package com.foottogether.app.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import com.foottogether.app.data.model.AppNotification;
import com.foottogether.app.data.model.Match;
import com.foottogether.app.data.remote.AuthRepository;
import com.foottogether.app.data.remote.MatchRepository;
import com.foottogether.app.data.remote.NotificationRepository;
import com.foottogether.app.data.remote.ResultCallback;
import com.foottogether.app.util.NotificationHelper;
import com.foottogether.app.util.ReminderScheduler;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Service = un composant Android qui travaille SANS écran, même quand on change d'activité.
 * C'est un service « démarré » (startService) par MainActivity.
 *
 * Son travail : écouter en temps réel users/{uid}/notifications où delivered == false,
 * afficher chaque nouvelle notification dans la barre d'Android, puis passer delivered à true.
 * Il remet aussi les rappels à jour quand un match est modifié ou annulé.
 *
 * Limite assumée (pas de serveur payant) : Android arrête les services d'arrière-plan
 * environ une minute après qu'on a quitté l'app. Les notifications arrivent donc quand l'app
 * est ouverte ou vient d'être quittée ; sinon on les retrouve dans l'écran Notifications.
 */
public class NotificationSyncService extends Service {

    private static final String TAG = "NotificationSync";

    private final NotificationRepository notificationRepository = new NotificationRepository();
    private final MatchRepository matchRepository = new MatchRepository();

    @Nullable
    private ListenerRegistration listener;
    @Nullable
    private String uid;
    /** Notifications déjà affichées pendant cette session (évite les doublons). */
    private final Set<String> shownIds = new HashSet<>();

    /** Démarre le service (appelé par MainActivity, quand l'app est au premier plan). */
    public static void start(Context context) {
        try {
            context.startService(new Intent(context, NotificationSyncService.class));
        } catch (IllegalStateException e) {
            // Android refuse de démarrer un service si l'app est en arrière-plan : pas grave
            Log.w(TAG, "Service non démarré (app en arrière-plan)", e);
        }
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, NotificationSyncService.class));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String currentUid = new AuthRepository().getCurrentUid();
        if (currentUid == null) {
            stopSelf(); // plus personne de connecté
            return START_NOT_STICKY;
        }
        // Déjà en écoute pour ce joueur (ex. MainActivity recréée après une rotation)
        if (listener != null && currentUid.equals(uid)) return START_NOT_STICKY;

        stopListening();
        uid = currentUid;
        NotificationHelper.createChannels(this);
        listener = notificationRepository.listenUndelivered(uid, new ResultCallback<List<AppNotification>>() {
            @Override
            public void onSuccess(List<AppNotification> notifications) {
                deliver(notifications);
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Écoute des notifications impossible", e);
            }
        });
        syncReminders();
        // NOT_STICKY : si Android arrête le service, il ne le relance pas tout seul
        // (MainActivity le redémarrera à la prochaine ouverture)
        return START_NOT_STICKY;
    }

    private void deliver(List<AppNotification> notifications) {
        boolean matchChanged = false;
        for (AppNotification n : notifications) {
            if (n.getId() == null || !shownIds.add(n.getId())) continue;
            NotificationHelper.showMatchNotification(this, n);
            if (uid != null) notificationRepository.markDelivered(uid, n.getId());
            if (AppNotification.TYPE_MATCH_UPDATED.equals(n.getType())
                    || AppNotification.TYPE_MATCH_CANCELLED.equals(n.getType())) {
                matchChanged = true;
            }
        }
        // Match déplacé ou annulé : le rappel doit suivre
        if (matchChanged) syncReminders();
    }

    /** Relit mes matchs sur le serveur et met les rappels (AlarmManager + SQLite) d'accord. */
    private void syncReminders() {
        if (uid == null) return;
        matchRepository.getMyParticipations(uid, new ResultCallback<List<Match>>() {
            @Override
            public void onSuccess(List<Match> matches) {
                ReminderScheduler.sync(NotificationSyncService.this, matches);
            }

            @Override
            public void onError(Exception e) {
                Log.w(TAG, "Rappels non synchronisés", e);
            }
        });
    }

    private void stopListening() {
        if (listener != null) {
            listener.remove();
            listener = null;
        }
        shownIds.clear();
    }

    @Override
    public void onDestroy() {
        stopListening();
        super.onDestroy();
    }

    /** Service « démarré » et non « lié » : pas de connexion directe avec une activité. */
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
