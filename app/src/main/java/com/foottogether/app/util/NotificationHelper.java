package com.foottogether.app.util;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.format.DateFormat;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.TaskStackBuilder;
import androidx.core.content.ContextCompat;

import com.foottogether.app.R;
import com.foottogether.app.data.model.AppNotification;
import com.foottogether.app.ui.detail.MatchDetailActivity;

import java.util.Date;

/**
 * Affiche les notifications Android (dans la barre du haut du téléphone).
 *
 * Depuis Android 8 (API 26), chaque notification appartient à un CANAL : l'utilisateur peut
 * couper un canal dans les Paramètres d'Android (ex. garder les rappels, couper le reste).
 * Depuis Android 13 (API 33), il faut aussi la permission POST_NOTIFICATIONS (demandée par MainActivity).
 */
public final class NotificationHelper {

    public static final String CHANNEL_MATCHES = "matches";
    public static final String CHANNEL_REMINDERS = "reminders";

    private NotificationHelper() {
    }

    /** Crée les deux canaux (sans effet s'ils existent déjà). */
    public static void createChannels(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return; // pas de canaux avant Android 8
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;

        NotificationChannel matches = new NotificationChannel(CHANNEL_MATCHES,
                context.getString(R.string.notif_channel_matches), NotificationManager.IMPORTANCE_DEFAULT);
        matches.setDescription(context.getString(R.string.notif_channel_matches_desc));

        NotificationChannel reminders = new NotificationChannel(CHANNEL_REMINDERS,
                context.getString(R.string.notif_channel_reminders), NotificationManager.IMPORTANCE_HIGH);
        reminders.setDescription(context.getString(R.string.notif_channel_reminders_desc));

        manager.createNotificationChannel(matches);
        manager.createNotificationChannel(reminders);
    }

    /** Vrai si l'app a le droit d'afficher des notifications (permission + réglage d'Android). */
    public static boolean canNotify(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled();
    }

    /** Notification venue de Firestore (nouveau joueur, match annulé...). */
    public static void showMatchNotification(Context context, AppNotification n) {
        String key = n.getId() != null ? n.getId() : String.valueOf(System.currentTimeMillis());
        show(context, CHANNEL_MATCHES, key.hashCode(),
                context.getString(titleFor(n.getType())), bodyFor(context, n), n.getMatchId());
    }

    /** Rappel avant un match (déclenché par MatchReminderReceiver). */
    public static void showReminder(Context context, String matchId, String title, long matchTime) {
        String hour = DateFormat.getTimeFormat(context).format(new Date(matchTime));
        show(context, CHANNEL_REMINDERS, ("reminder" + matchId).hashCode(),
                context.getString(R.string.reminder_title),
                context.getString(R.string.reminder_body, title, hour), matchId);
    }

    @SuppressLint("MissingPermission") // vérifiée juste avant par canNotify()
    private static void show(Context context, String channel, int id,
                             String title, String body, String matchId) {
        createChannels(context);
        if (!canNotify(context)) return;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channel)
                .setSmallIcon(R.drawable.ic_stat_foot) // icône blanche de la barre d'état
                .setColor(ContextCompat.getColor(context, R.color.green_primary))
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body)) // texte long dépliable
                .setPriority(CHANNEL_REMINDERS.equals(channel)
                        ? NotificationCompat.PRIORITY_HIGH : NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true); // disparaît quand on la touche

        if (matchId != null) builder.setContentIntent(openMatchIntent(context, matchId, id));
        NotificationManagerCompat.from(context).notify(id, builder.build());
    }

    /**
     * Toucher la notification ouvre le Détail du match.
     * TaskStackBuilder ajoute MainActivity « derrière » (parentActivityName du manifeste) :
     * le bouton Retour ramène à l'Accueil au lieu de quitter l'app.
     * PendingIntent = une action préparée que le système exécutera plus tard à notre place.
     */
    private static PendingIntent openMatchIntent(Context context, String matchId, int requestCode) {
        return TaskStackBuilder.create(context)
                .addNextIntentWithParentStack(MatchDetailActivity.newIntent(context, matchId))
                .getPendingIntent(requestCode,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    // ===== Textes : composés sur le téléphone qui affiche, donc dans SA langue =====

    @StringRes
    public static int titleFor(String type) {
        if (AppNotification.TYPE_JOIN_CONFIRMED.equals(type)) return R.string.notif_join_confirmed_title;
        if (AppNotification.TYPE_NEW_PARTICIPANT.equals(type)) return R.string.notif_new_participant_title;
        if (AppNotification.TYPE_MATCH_CANCELLED.equals(type)) return R.string.notif_match_cancelled_title;
        if (AppNotification.TYPE_MATCH_UPDATED.equals(type)) return R.string.notif_match_updated_title;
        if (AppNotification.TYPE_SPOT_AVAILABLE.equals(type)) return R.string.notif_spot_available_title;
        return R.string.app_name;
    }

    @NonNull
    public static String bodyFor(Context context, AppNotification n) {
        String match = n.getMatchTitle() == null ? "" : n.getMatchTitle();
        String sender = n.getSenderPseudo() == null ? "" : n.getSenderPseudo();
        String type = n.getType();
        if (AppNotification.TYPE_JOIN_CONFIRMED.equals(type)) {
            return context.getString(R.string.notif_join_confirmed_body, match);
        } else if (AppNotification.TYPE_NEW_PARTICIPANT.equals(type)) {
            return context.getString(R.string.notif_new_participant_body, sender, match);
        } else if (AppNotification.TYPE_MATCH_CANCELLED.equals(type)) {
            return context.getString(R.string.notif_match_cancelled_body, sender, match);
        } else if (AppNotification.TYPE_MATCH_UPDATED.equals(type)) {
            return context.getString(R.string.notif_match_updated_body, sender, match);
        } else if (AppNotification.TYPE_SPOT_AVAILABLE.equals(type)) {
            return context.getString(R.string.notif_spot_available_body, match);
        }
        return match;
    }

    /** Petite icône de chaque type (écran Notifications). */
    @DrawableRes
    public static int iconFor(String type) {
        if (AppNotification.TYPE_NEW_PARTICIPANT.equals(type)) return R.drawable.ic_person;
        if (AppNotification.TYPE_MATCH_CANCELLED.equals(type)) return R.drawable.ic_error;
        if (AppNotification.TYPE_MATCH_UPDATED.equals(type)) return R.drawable.ic_edit;
        if (AppNotification.TYPE_SPOT_AVAILABLE.equals(type)) return R.drawable.ic_add;
        return R.drawable.ic_event;
    }
}
