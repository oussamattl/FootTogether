package com.foottogether.app.data.local;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

/**
 * DAO des matchs consultés récemment (chaque ouverture de l'écran Détail).
 * On ne garde que les 20 derniers par joueur.
 */
public class RecentDao {

    private static final int MAX_RECENTS = 20;

    private final FootDbHelper helper;

    public RecentDao(Context context) {
        helper = FootDbHelper.getInstance(context);
    }

    /** Enregistre (ou remet en tête) un match consulté, puis supprime les plus anciens. */
    public void add(String uid, String matchId) {
        SQLiteDatabase db = helper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(FootDbHelper.COL_UID, uid);
        values.put(FootDbHelper.COL_MATCH_ID, matchId);
        values.put(FootDbHelper.COL_VIEWED_AT, System.currentTimeMillis());
        db.insertWithOnConflict(FootDbHelper.TABLE_RECENTS, null, values, SQLiteDatabase.CONFLICT_REPLACE);

        // Garde seulement les 20 plus récents de ce joueur
        db.execSQL("DELETE FROM " + FootDbHelper.TABLE_RECENTS
                        + " WHERE " + FootDbHelper.COL_UID + " = ?"
                        + " AND " + FootDbHelper.COL_MATCH_ID + " NOT IN ("
                        + "SELECT " + FootDbHelper.COL_MATCH_ID + " FROM " + FootDbHelper.TABLE_RECENTS
                        + " WHERE " + FootDbHelper.COL_UID + " = ?"
                        + " ORDER BY " + FootDbHelper.COL_VIEWED_AT + " DESC LIMIT " + MAX_RECENTS + ")",
                new Object[]{uid, uid});
    }

    /** « Effacer l'historique ». */
    public void clear(String uid) {
        helper.getWritableDatabase().delete(FootDbHelper.TABLE_RECENTS,
                FootDbHelper.COL_UID + " = ?", new String[]{uid});
    }
}
