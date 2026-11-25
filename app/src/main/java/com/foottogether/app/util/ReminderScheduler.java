package com.foottogether.app.util;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.WorkerThread;

import com.foottogether.app.data.local.ReminderDao;
import com.foottogether.app.data.model.Match;
import com.foottogether.app.data.prefs.AppPrefs;
import com.foottogether.app.receiver.MatchReminderReceiver;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Programme les rappels « Ton match commence bientôt ».
 *
 * AlarmManager = le réveil d'Android : il réveille l'app à l'heure demandée, même si elle est
 * fermée, en envoyant un Intent à MatchReminderReceiver (un BroadcastReceiver).
 * - setAndAllowWhileIdle : fonctionne même en veille, sans permission spéciale ;
 *   l'heure n'est pas exacte à la seconde (Android regroupe les réveils pour la batterie).
 * - Les alarmes sont effacées quand le téléphone redémarre : c'est pour ça qu'on garde aussi
 *   chaque rappel dans SQLite (ReminderDao), et que BootReceiver les reprogramme.
 */
public final class ReminderScheduler {

    private static final String TAG = "ReminderScheduler";
    /** Délai par défaut : 1 h avant le match. */
    public static final int DEFAULT_DELAY_MINUTES = 60;

    private ReminderScheduler() {
    }

    /** Délai choisi par l'utilisateur, en minutes (0 = rappels désactivés). */
    static int delayMinutes(Context context) {
        return new AppPrefs(context).getReminderMinutes();
    }

    // ===== Appelés par les écrans (le travail SQLite part en arrière-plan) =====

    /** Après une création ou une inscription : on enregistre le rappel et on le programme. */
    public static void schedule(Context context, Match match) {
        Context app = context.getApplicationContext();
        if (match.getId() == null || match.getDateTime() == null) return;
        ReminderDao.Reminder reminder = new ReminderDao.Reminder(match.getId(), match.getTitle(),
                match.getDateTime().toDate().getTime());
        BackgroundTasks.execute(() -> {
            new ReminderDao(app).save(reminder);
            setAlarm(app, reminder);
        });
    }

    /** Après un départ ou une annulation : plus de rappel pour ce match. */
    public static void cancel(Context context, String matchId) {
        Context app = context.getApplicationContext();
        BackgroundTasks.execute(() -> {
            cancelAlarm(app, matchId);
            new ReminderDao(app).delete(matchId);
        });
    }

    /**
     * Met les rappels en accord avec la liste de MES matchs lue sur le serveur
     * (match déplacé par l'organisateur, annulé, quitté depuis un autre téléphone...).
     */
    public static void sync(Context context, List<Match> myMatches) {
        Context app = context.getApplicationContext();
        BackgroundTasks.execute(() -> {
            ReminderDao dao = new ReminderDao(app);
            Set<String> keep = new HashSet<>();
            for (Match match : myMatches) {
                if (match.getId() == null || match.getDateTime() == null
                        || match.isCancelled() || match.isStarted()) continue;
                ReminderDao.Reminder reminder = new ReminderDao.Reminder(match.getId(), match.getTitle(),
                        match.getDateTime().toDate().getTime());
                dao.save(reminder);
                setAlarm(app, reminder);
                keep.add(match.getId());
            }
            for (ReminderDao.Reminder old : dao.getAll()) {
                if (!keep.contains(old.matchId)) {
                    cancelAlarm(app, old.matchId);
                    dao.delete(old.matchId);
                }
            }
            Log.d(TAG, keep.size() + " rappel(s) à jour");
        });
    }

    /** Déconnexion : le joueur suivant ne doit pas recevoir les rappels du précédent. */
    public static void cancelAll(Context context) {
        Context app = context.getApplicationContext();
        BackgroundTasks.execute(() -> {
            ReminderDao dao = new ReminderDao(app);
            for (ReminderDao.Reminder reminder : dao.getAll()) cancelAlarm(app, reminder.matchId);
            dao.clear();
        });
    }

    /**
     * Reprogramme tous les rappels enregistrés dans SQLite
     * (après un redémarrage du téléphone, ou quand le délai change dans les Paramètres).
     * À appeler hors du thread principal.
     *
     * @return le nombre de rappels programmés
     */
    @WorkerThread
    public static int rescheduleAllBlocking(Context context) {
        ReminderDao dao = new ReminderDao(context);
        int count = 0;
        for (ReminderDao.Reminder reminder : dao.getAll()) {
            if (reminder.matchTime <= System.currentTimeMillis()) {
                dao.delete(reminder.matchId); // match passé : on fait le ménage
            } else if (setAlarm(context, reminder)) {
                count++;
            }
        }
        return count;
    }

    // ===== AlarmManager =====

    /** @return vrai si une alarme a été programmée */
    private static boolean setAlarm(Context context, ReminderDao.Reminder reminder) {
        int delay = delayMinutes(context);
        long triggerAt = reminder.matchTime - delay * 60_000L;
        // Rappels désactivés, ou heure du rappel déjà passée : pas d'alarme
        if (delay <= 0 || triggerAt <= System.currentTimeMillis()) {
            cancelAlarm(context, reminder.matchId);
            return false;
        }
        AlarmManager alarmManager = context.getSystemService(AlarmManager.class);
        if (alarmManager == null) return false;
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0,
                buildIntent(context, reminder), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        // RTC_WAKEUP : heure « normale » (horloge), et le téléphone est réveillé s'il dort
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
        return true;
    }

    private static void cancelAlarm(Context context, String matchId) {
        AlarmManager alarmManager = context.getSystemService(AlarmManager.class);
        // FLAG_NO_CREATE : on récupère l'alarme existante, sans en créer une nouvelle
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0,
                buildIntent(context, new ReminderDao.Reminder(matchId, "", 0)),
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (pendingIntent != null && alarmManager != null) {
            alarmManager.cancel(pendingIntent);
            pendingIntent.cancel();
        }
    }

    /**
     * L'Intent reçu par MatchReminderReceiver. Android reconnaît deux alarmes comme « la même »
     * si leurs Intents ont la même adresse (data) : on y met l'id du match, une alarme par match.
     */
    private static Intent buildIntent(Context context, ReminderDao.Reminder reminder) {
        return new Intent(context, MatchReminderReceiver.class)
                .setData(Uri.parse("foottogether://reminder/" + reminder.matchId))
                .putExtra(MatchReminderReceiver.EXTRA_MATCH_ID, reminder.matchId)
                .putExtra(MatchReminderReceiver.EXTRA_TITLE, reminder.title)
                .putExtra(MatchReminderReceiver.EXTRA_MATCH_TIME, reminder.matchTime);
    }
}
