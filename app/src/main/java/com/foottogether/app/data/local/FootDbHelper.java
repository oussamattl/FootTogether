package com.foottogether.app.data.local;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Base de données SQLite locale du téléphone (fichier « foottogether.db »).
 *
 * SQLiteOpenHelper s'occupe d'ouvrir le fichier et de créer / mettre à jour les tables :
 * - onCreate : appelée UNE fois, quand la base n'existe pas encore (première installation) ;
 * - onUpgrade : appelée quand DB_VERSION augmente (mise à jour de l'app, base déjà présente) :
 *   on ajoute les tables manquantes SANS effacer les données déjà enregistrées.
 *
 * Une seule instance pour toute l'app (getInstance) : SQLite n'aime pas plusieurs connexions.
 *
 * Tables :
 * - reminders   (version 1) : rappels programmés, pour les reprogrammer après un redémarrage ;
 * - favorites   (version 2) : matchs mis en favori (cœur du Détail), par joueur ;
 * - recents     (version 2) : matchs consultés récemment, par joueur ;
 * - match_cache (version 2) : copie locale des matchs vus, affichable sans réseau ;
 * - scores      (version 3) : parties du mini-jeu « jongles ».
 */
public class FootDbHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "foottogether.db";
    private static final int DB_VERSION = 3;

    // ===== Colonnes communes =====
    public static final String COL_MATCH_ID = "match_id";
    public static final String COL_UID = "uid";

    // ===== Table des rappels =====
    public static final String TABLE_REMINDERS = "reminders";
    public static final String COL_TITLE = "title";
    public static final String COL_MATCH_TIME = "match_time";

    // ===== Favoris et récents =====
    public static final String TABLE_FAVORITES = "favorites";
    public static final String COL_ADDED_AT = "added_at";
    public static final String TABLE_RECENTS = "recents";
    public static final String COL_VIEWED_AT = "viewed_at";

    // ===== Cache des matchs (mêmes informations que le document Firestore) =====
    public static final String TABLE_MATCH_CACHE = "match_cache";
    public static final String COL_DESCRIPTION = "description";
    public static final String COL_DATE_TIME = "date_time";
    public static final String COL_ADDRESS = "address";
    public static final String COL_CITY = "city";
    public static final String COL_LAT = "lat";
    public static final String COL_LNG = "lng";
    public static final String COL_FORMAT = "format";
    public static final String COL_MAX_PLAYERS = "max_players";
    public static final String COL_LEVEL = "level";
    public static final String COL_ORGANIZER_ID = "organizer_id";
    public static final String COL_ORGANIZER_PSEUDO = "organizer_pseudo";
    public static final String COL_STATUS = "status";
    public static final String COL_PARTICIPANT_COUNT = "participant_count";
    public static final String COL_PARTICIPANT_IDS = "participant_ids";
    public static final String COL_CACHED_AT = "cached_at";

    // ===== Scores du mini-jeu (version 3) =====
    public static final String TABLE_SCORES = "scores";
    public static final String COL_ID = "_id";
    public static final String COL_SCORE = "score";
    public static final String COL_PLAYED_AT = "played_at";

    private static FootDbHelper instance;

    public static synchronized FootDbHelper getInstance(Context context) {
        if (instance == null) {
            // getApplicationContext : on ne garde pas une Activity en mémoire (fuite)
            instance = new FootDbHelper(context.getApplicationContext());
        }
        return instance;
    }

    private FootDbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    /** Première installation : on crée toutes les tables. */
    @Override
    public void onCreate(SQLiteDatabase db) {
        createRemindersTable(db);
        createVersion2Tables(db);
        createScoresTable(db);
    }

    /**
     * L'app est mise à jour alors que la base existe déjà (ex. version 1 → 2) :
     * on AJOUTE les nouvelles tables sans effacer les rappels déjà enregistrés.
     */
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) createVersion2Tables(db);
        if (oldVersion < 3) createScoresTable(db);
    }

    private static void createScoresTable(SQLiteDatabase db) {
        // AUTOINCREMENT : SQLite donne lui-même un numéro unique à chaque partie
        db.execSQL("CREATE TABLE " + TABLE_SCORES + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_UID + " TEXT NOT NULL, "
                + COL_SCORE + " INTEGER NOT NULL, "
                + COL_PLAYED_AT + " INTEGER NOT NULL)");
    }

    private static void createRemindersTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_REMINDERS + " ("
                + COL_MATCH_ID + " TEXT PRIMARY KEY, "
                + COL_TITLE + " TEXT NOT NULL, "
                + COL_MATCH_TIME + " INTEGER NOT NULL)");
    }

    private static void createVersion2Tables(SQLiteDatabase db) {
        // Clé primaire double (uid, match_id) : un même match ne peut être favori qu'une fois par joueur
        db.execSQL("CREATE TABLE " + TABLE_FAVORITES + " ("
                + COL_UID + " TEXT NOT NULL, "
                + COL_MATCH_ID + " TEXT NOT NULL, "
                + COL_ADDED_AT + " INTEGER NOT NULL, "
                + "PRIMARY KEY (" + COL_UID + ", " + COL_MATCH_ID + "))");

        db.execSQL("CREATE TABLE " + TABLE_RECENTS + " ("
                + COL_UID + " TEXT NOT NULL, "
                + COL_MATCH_ID + " TEXT NOT NULL, "
                + COL_VIEWED_AT + " INTEGER NOT NULL, "
                + "PRIMARY KEY (" + COL_UID + ", " + COL_MATCH_ID + "))");

        db.execSQL("CREATE TABLE " + TABLE_MATCH_CACHE + " ("
                + COL_MATCH_ID + " TEXT PRIMARY KEY, "
                + COL_TITLE + " TEXT, "
                + COL_DESCRIPTION + " TEXT, "
                + COL_DATE_TIME + " INTEGER NOT NULL, "
                + COL_ADDRESS + " TEXT, "
                + COL_CITY + " TEXT, "
                + COL_LAT + " REAL, "
                + COL_LNG + " REAL, "
                + COL_FORMAT + " TEXT, "
                + COL_MAX_PLAYERS + " INTEGER, "
                + COL_LEVEL + " TEXT, "
                + COL_ORGANIZER_ID + " TEXT, "
                + COL_ORGANIZER_PSEUDO + " TEXT, "
                + COL_STATUS + " TEXT, "
                + COL_PARTICIPANT_COUNT + " INTEGER, "
                + COL_PARTICIPANT_IDS + " TEXT, "
                + COL_CACHED_AT + " INTEGER NOT NULL)");
        // Index : accélère le tri des matchs à venir par date
        db.execSQL("CREATE INDEX idx_cache_date ON " + TABLE_MATCH_CACHE + " (" + COL_DATE_TIME + ")");
    }
}
