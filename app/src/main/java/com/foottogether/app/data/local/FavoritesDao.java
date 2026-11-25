package com.foottogether.app.data.local;

import android.content.ContentValues;
import android.content.Context;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;

/**
 * DAO des favoris (cœur de l'écran Détail). Chaque joueur a ses propres favoris (colonne uid),
 * même si plusieurs comptes se connectent sur le même téléphone.
 * Les infos du match sont lues dans match_cache (MatchCacheDao.getFavorites).
 */
public class FavoritesDao {

    private final FootDbHelper helper;

    public FavoritesDao(Context context) {
        helper = FootDbHelper.getInstance(context);
    }

    public void add(String uid, String matchId) {
        ContentValues values = new ContentValues();
        values.put(FootDbHelper.COL_UID, uid);
        values.put(FootDbHelper.COL_MATCH_ID, matchId);
        values.put(FootDbHelper.COL_ADDED_AT, System.currentTimeMillis());
        helper.getWritableDatabase().insertWithOnConflict(FootDbHelper.TABLE_FAVORITES, null,
                values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void remove(String uid, String matchId) {
        helper.getWritableDatabase().delete(FootDbHelper.TABLE_FAVORITES,
                FootDbHelper.COL_UID + " = ? AND " + FootDbHelper.COL_MATCH_ID + " = ?",
                new String[]{uid, matchId});
    }

    public boolean isFavorite(String uid, String matchId) {
        // queryNumEntries : SELECT COUNT(*) ... WHERE ...
        return DatabaseUtils.queryNumEntries(helper.getReadableDatabase(), FootDbHelper.TABLE_FAVORITES,
                FootDbHelper.COL_UID + " = ? AND " + FootDbHelper.COL_MATCH_ID + " = ?",
                new String[]{uid, matchId}) > 0;
    }
}
