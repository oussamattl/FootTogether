package com.foottogether.app.data.local;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import java.util.ArrayList;
import java.util.List;

/**
 * DAO des scores du mini-jeu « jongles » (table scores) : chaque partie terminée est enregistrée.
 * À appeler depuis un thread d'arrière-plan.
 */
public class ScoreDao {

    private final FootDbHelper helper;

    public ScoreDao(Context context) {
        helper = FootDbHelper.getInstance(context);
    }

    public void add(String uid, int score) {
        ContentValues values = new ContentValues();
        values.put(FootDbHelper.COL_UID, uid);
        values.put(FootDbHelper.COL_SCORE, score);
        values.put(FootDbHelper.COL_PLAYED_AT, System.currentTimeMillis());
        helper.getWritableDatabase().insert(FootDbHelper.TABLE_SCORES, null, values);
    }

    /** Les derniers scores du joueur, du plus récent au plus ancien. */
    public List<Integer> getLastScores(String uid, int limit) {
        List<Integer> scores = new ArrayList<>();
        try (Cursor cursor = helper.getReadableDatabase().query(FootDbHelper.TABLE_SCORES,
                new String[]{FootDbHelper.COL_SCORE},
                FootDbHelper.COL_UID + " = ?", new String[]{uid},
                null, null, FootDbHelper.COL_PLAYED_AT + " DESC", String.valueOf(limit))) {
            while (cursor.moveToNext()) scores.add(cursor.getInt(0));
        }
        return scores;
    }

    /** Nombre de parties jouées par ce joueur sur ce téléphone (SELECT COUNT(*)). */
    public int countGames(String uid) {
        try (Cursor cursor = helper.getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM " + FootDbHelper.TABLE_SCORES + " WHERE " + FootDbHelper.COL_UID + " = ?",
                new String[]{uid})) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }
}
