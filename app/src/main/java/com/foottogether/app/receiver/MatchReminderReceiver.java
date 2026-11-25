package com.foottogether.app.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.foottogether.app.util.NotificationHelper;

/**
 * BroadcastReceiver = un composant Android qui « reçoit un message » du système.
 * Ici, le message vient de l'AlarmManager, à l'heure du rappel programmée par ReminderScheduler.
 * Android réveille l'app (même fermée), appelle onReceive, puis peut la refermer.
 * onReceive doit être rapide : on affiche juste la notification.
 */
public class MatchReminderReceiver extends BroadcastReceiver {

    public static final String EXTRA_MATCH_ID = "match_id";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_MATCH_TIME = "match_time";

    @Override
    public void onReceive(Context context, Intent intent) {
        String matchId = intent.getStringExtra(EXTRA_MATCH_ID);
        String title = intent.getStringExtra(EXTRA_TITLE);
        long matchTime = intent.getLongExtra(EXTRA_MATCH_TIME, 0);
        if (matchId == null || matchTime == 0) return;
        NotificationHelper.showReminder(context, matchId, title == null ? "" : title, matchTime);
    }
}
