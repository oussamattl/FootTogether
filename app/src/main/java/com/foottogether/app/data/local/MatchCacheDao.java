package com.foottogether.app.data.local;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;

import com.foottogether.app.data.model.Match;
import com.google.firebase.Timestamp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static com.foottogether.app.data.local.FootDbHelper.*;

/**
 * DAO du cache des matchs : une copie locale des matchs déjà vus (Accueil, Détail).
 * - L'Accueil l'affiche tout de suite à l'ouverture, avant la réponse du serveur, et hors ligne ;
 * - les écrans Favoris et Récents lisent leurs matchs ici (requêtes avec JOIN).
 * À appeler depuis un thread d'arrière-plan (BackgroundTasks).
 */
public class MatchCacheDao {

    /** On garde dans le cache les matchs passés depuis au plus 7 jours (sauf favoris / récents). */
    private static final long KEEP_PAST_MILLIS = 7L * 24 * 60 * 60 * 1000;

    private final FootDbHelper helper;

    public MatchCacheDao(Context context) {
        helper = FootDbHelper.getInstance(context);
    }

    public void save(Match match) {
        if (match.getId() == null || match.getDateTime() == null) return;
        helper.getWritableDatabase().insertWithOnConflict(TABLE_MATCH_CACHE, null,
                toValues(match), SQLiteDatabase.CONFLICT_REPLACE);
    }

    /**
     * Enregistre toute une liste dans UNE transaction SQLite : beaucoup plus rapide
     * que 50 écritures séparées, et tout ou rien en cas d'erreur.
     */
    public void saveAll(List<Match> matches) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            for (Match match : matches) {
                if (match.getId() == null || match.getDateTime() == null) continue;
                db.insertWithOnConflict(TABLE_MATCH_CACHE, null, toValues(match),
                        SQLiteDatabase.CONFLICT_REPLACE);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /** Matchs à venir (ou en cours), triés par date : la liste de l'Accueil sans réseau. */
    public List<Match> getUpcoming(int limit) {
        long from = System.currentTimeMillis() - Match.DURATION_MILLIS;
        Cursor cursor = helper.getReadableDatabase().query(TABLE_MATCH_CACHE, null,
                COL_DATE_TIME + " >= ?", new String[]{String.valueOf(from)},
                null, null, COL_DATE_TIME + " ASC", String.valueOf(limit));
        return readAll(cursor);
    }

    /** Favoris du joueur : JOIN entre favorites et match_cache, triés par date du match. */
    public List<Match> getFavorites(String uid) {
        Cursor cursor = helper.getReadableDatabase().rawQuery(
                "SELECT c.* FROM " + TABLE_MATCH_CACHE + " c"
                        + " INNER JOIN " + TABLE_FAVORITES + " f ON c." + COL_MATCH_ID + " = f." + COL_MATCH_ID
                        + " WHERE f." + COL_UID + " = ?"
                        + " ORDER BY c." + COL_DATE_TIME + " ASC",
                new String[]{uid});
        return readAll(cursor);
    }

    /** Matchs consultés récemment : du plus récent au plus ancien. */
    public List<Match> getRecents(String uid) {
        Cursor cursor = helper.getReadableDatabase().rawQuery(
                "SELECT c.* FROM " + TABLE_MATCH_CACHE + " c"
                        + " INNER JOIN " + TABLE_RECENTS + " r ON c." + COL_MATCH_ID + " = r." + COL_MATCH_ID
                        + " WHERE r." + COL_UID + " = ?"
                        + " ORDER BY r." + COL_VIEWED_AT + " DESC",
                new String[]{uid});
        return readAll(cursor);
    }

    /** Ménage : supprime les vieux matchs qui ne sont ni en favori ni dans les récents. */
    public void prune() {
        long limit = System.currentTimeMillis() - KEEP_PAST_MILLIS;
        helper.getWritableDatabase().delete(TABLE_MATCH_CACHE,
                COL_DATE_TIME + " < ?"
                        + " AND " + COL_MATCH_ID + " NOT IN (SELECT " + COL_MATCH_ID + " FROM " + TABLE_FAVORITES + ")"
                        + " AND " + COL_MATCH_ID + " NOT IN (SELECT " + COL_MATCH_ID + " FROM " + TABLE_RECENTS + ")",
                new String[]{String.valueOf(limit)});
    }

    // ===== Conversion Match ↔ ligne SQLite =====

    private static ContentValues toValues(Match m) {
        ContentValues v = new ContentValues();
        v.put(COL_MATCH_ID, m.getId());
        v.put(COL_TITLE, m.getTitle());
        v.put(COL_DESCRIPTION, m.getDescription());
        v.put(COL_DATE_TIME, m.getDateTime().toDate().getTime());
        v.put(COL_ADDRESS, m.getAddress());
        v.put(COL_CITY, m.getCity());
        v.put(COL_LAT, m.getLat());   // null accepté : match sans coordonnées
        v.put(COL_LNG, m.getLng());
        v.put(COL_FORMAT, m.getFormat());
        v.put(COL_MAX_PLAYERS, m.getMaxPlayers());
        v.put(COL_LEVEL, m.getLevel());
        v.put(COL_ORGANIZER_ID, m.getOrganizerId());
        v.put(COL_ORGANIZER_PSEUDO, m.getOrganizerPseudo());
        v.put(COL_STATUS, m.getStatus());
        v.put(COL_PARTICIPANT_COUNT, m.getParticipantCount());
        // Une liste ne rentre pas dans une colonne : on la stocke en texte « uid1,uid2 »
        v.put(COL_PARTICIPANT_IDS, m.getParticipantIds() == null ? "" : TextUtils.join(",", m.getParticipantIds()));
        v.put(COL_CACHED_AT, System.currentTimeMillis());
        return v;
    }

    private static List<Match> readAll(Cursor cursor) {
        List<Match> matches = new ArrayList<>();
        // try-with-resources : le Cursor est toujours fermé
        try (Cursor c = cursor) {
            while (c.moveToNext()) matches.add(fromCursor(c));
        }
        return matches;
    }

    private static Match fromCursor(Cursor c) {
        Match m = new Match();
        m.setId(c.getString(c.getColumnIndexOrThrow(COL_MATCH_ID)));
        m.setTitle(c.getString(c.getColumnIndexOrThrow(COL_TITLE)));
        m.setDescription(c.getString(c.getColumnIndexOrThrow(COL_DESCRIPTION)));
        m.setDateTime(new Timestamp(new Date(c.getLong(c.getColumnIndexOrThrow(COL_DATE_TIME)))));
        m.setAddress(c.getString(c.getColumnIndexOrThrow(COL_ADDRESS)));
        m.setCity(c.getString(c.getColumnIndexOrThrow(COL_CITY)));
        int latCol = c.getColumnIndexOrThrow(COL_LAT);
        int lngCol = c.getColumnIndexOrThrow(COL_LNG);
        m.setLat(c.isNull(latCol) ? null : c.getDouble(latCol));
        m.setLng(c.isNull(lngCol) ? null : c.getDouble(lngCol));
        m.setFormat(c.getString(c.getColumnIndexOrThrow(COL_FORMAT)));
        m.setMaxPlayers(c.getLong(c.getColumnIndexOrThrow(COL_MAX_PLAYERS)));
        m.setLevel(c.getString(c.getColumnIndexOrThrow(COL_LEVEL)));
        m.setOrganizerId(c.getString(c.getColumnIndexOrThrow(COL_ORGANIZER_ID)));
        m.setOrganizerPseudo(c.getString(c.getColumnIndexOrThrow(COL_ORGANIZER_PSEUDO)));
        m.setStatus(c.getString(c.getColumnIndexOrThrow(COL_STATUS)));
        m.setParticipantCount(c.getLong(c.getColumnIndexOrThrow(COL_PARTICIPANT_COUNT)));
        String ids = c.getString(c.getColumnIndexOrThrow(COL_PARTICIPANT_IDS));
        m.setParticipantIds(ids == null || ids.isEmpty()
                ? new ArrayList<>() : new ArrayList<>(Arrays.asList(ids.split(","))));
        return m;
    }
}
