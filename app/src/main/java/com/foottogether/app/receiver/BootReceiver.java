package com.foottogether.app.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.foottogether.app.data.remote.ResultCallback;
import com.foottogether.app.util.BackgroundTasks;
import com.foottogether.app.util.ReminderScheduler;

/**
 * Reçoit BOOT_COMPLETED : le message envoyé par Android quand le téléphone a fini de démarrer.
 *
 * Problème : au redémarrage, Android efface toutes les alarmes (AlarmManager).
 * Solution : les rappels sont aussi enregistrés dans SQLite ; on les relit et on les reprogramme.
 * (Il faut la permission RECEIVE_BOOT_COMPLETED dans le manifeste.)
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;

        // Lire SQLite prend un peu de temps : goAsync() demande à Android de patienter
        // pendant qu'on travaille en arrière-plan, puis finish() quand c'est fini.
        PendingResult pendingResult = goAsync();
        Context app = context.getApplicationContext();
        BackgroundTasks.run(() -> ReminderScheduler.rescheduleAllBlocking(app),
                new ResultCallback<Integer>() {
                    @Override
                    public void onSuccess(Integer count) {
                        Log.i(TAG, count + " rappel(s) reprogrammé(s) après le redémarrage");
                        pendingResult.finish();
                    }

                    @Override
                    public void onError(Exception e) {
                        Log.e(TAG, "Rappels non reprogrammés", e);
                        pendingResult.finish();
                    }
                });
    }
}
