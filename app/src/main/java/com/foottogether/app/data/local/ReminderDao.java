package com.foottogether.app.data.local;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

/**
 * DAO (Data Access Object) des rappels : les seules requêtes SQL sur la table « reminders ».
 * À appeler depuis un thread d'arrière-plan (BackgroundTasks) : le disque peut être lent.
 */
public class ReminderDao {

    /** Un rappel enregistré : quel match, son titre (pour le texte), et l'heure du match. */
    public static class Reminder {
        public final String matchId;
        public final String title;
        public final long matchTime;

        public Reminder(String matchId, String title, long matchTime) {
            this.matchId = matchId;
            this.title = title;
            this.matchTime = matchTime;
        }
    }

    private final FootDbHelper helper;

    public ReminderDao(Context context) {
        helper = FootDbHelper.getInstance(context);
    }

    /** Ajoute ou remplace le rappel d'un match (CONFLICT_REPLACE : la clé est match_id). */
    public void save(Reminder reminder) {
        ContentValues values = new ContentValues();
        values.put(FootDbHelper.COL_MATCH_ID, reminder.matchId);
        values.put(FootDbHelper.COL_TITLE, reminder.title);
        values.put(FootDbHelper.COL_MATCH_TIME, reminder.matchTime);
        helper.getWritableDatabase().insertWithOnConflict(FootDbHelper.TABLE_REMINDERS, null,
                values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void delete(String matchId) {
        // Le « ? » est remplacé par matchId : jamais de concaténation (injection SQL)
        helper.getWritableDatabase().delete(FootDbHelper.TABLE_REMINDERS,
                FootDbHelper.COL_MATCH_ID + " = ?", new String[]{matchId});
    }

    public List<Reminder> getAll() {
        List<Reminder> reminders = new ArrayList<>();
        // try-with-resources : le Cursor est fermé automatiquement à la fin du bloc
        try (Cursor cursor = helper.getReadableDatabase().query(FootDbHelper.TABLE_REMINDERS,
                null, null, null, null, null, FootDbHelper.COL_MATCH_TIME + " ASC")) {
            int idCol = cursor.getColumnIndexOrThrow(FootDbHelper.COL_MATCH_ID);
            int titleCol = cursor.getColumnIndexOrThrow(FootDbHelper.COL_TITLE);
            int timeCol = cursor.getColumnIndexOrThrow(FootDbHelper.COL_MATCH_TIME);
            while (cursor.moveToNext()) {
                reminders.add(new Reminder(cursor.getString(idCol), cursor.getString(titleCol),
                        cursor.getLong(timeCol)));
            }
        }
        return reminders;
    }

    public void clear() {
        helper.getWritableDatabase().delete(FootDbHelper.TABLE_REMINDERS, null, null);
    }
}
