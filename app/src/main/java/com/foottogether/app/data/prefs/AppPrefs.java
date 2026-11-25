package com.foottogether.app.data.prefs;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

import com.foottogether.app.util.ReminderScheduler;

/**
 * Préférences de l'utilisateur, enregistrées avec SharedPreferences :
 * un petit fichier clé → valeur, privé à l'app, qui survit à la fermeture de l'app.
 *
 * On y met seulement des RÉGLAGES (langue, thème, ville, délai des rappels) et le record du mini-jeu.
 * JAMAIS de mot de passe ni de donnée sensible : Firebase Auth gère la session.
 */
public class AppPrefs {

    private static final String FILE_NAME = "foottogether_prefs";

    private static final String KEY_LANGUAGE = "language";
    private static final String KEY_THEME = "theme";
    private static final String KEY_DEFAULT_CITY = "default_city";
    private static final String KEY_REMINDER_MINUTES = "reminder_minutes";
    private static final String KEY_MINIGAME_RECORD = "minigame_record";

    // Valeurs possibles pour la langue et le thème ("system" = comme le téléphone)
    public static final String SYSTEM = "system";
    public static final String LANGUAGE_FR = "fr";
    public static final String LANGUAGE_EN = "en";
    public static final String THEME_LIGHT = "light";
    public static final String THEME_DARK = "dark";

    private final SharedPreferences prefs;

    public AppPrefs(Context context) {
        // MODE_PRIVATE : seule FootTogether peut lire ce fichier
        prefs = context.getApplicationContext().getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE);
    }

    // ===== Lecture (avec une valeur par défaut si rien n'est encore enregistré) =====

    public String getLanguage() {
        return prefs.getString(KEY_LANGUAGE, SYSTEM);
    }

    public String getTheme() {
        return prefs.getString(KEY_THEME, SYSTEM);
    }

    public String getDefaultCity() {
        return prefs.getString(KEY_DEFAULT_CITY, "");
    }

    /** Délai du rappel avant un match, en minutes (0 = pas de rappel). */
    public int getReminderMinutes() {
        return prefs.getInt(KEY_REMINDER_MINUTES, ReminderScheduler.DEFAULT_DELAY_MINUTES);
    }

    /** Meilleur score du mini-jeu « jongles » sur ce téléphone. */
    public int getMiniGameRecord() {
        return prefs.getInt(KEY_MINIGAME_RECORD, 0);
    }

    // ===== Écriture. apply() enregistre en arrière-plan, sans bloquer l'écran =====

    public void setLanguage(String language) {
        prefs.edit().putString(KEY_LANGUAGE, language).apply();
    }

    public void setTheme(String theme) {
        prefs.edit().putString(KEY_THEME, theme).apply();
    }

    public void setDefaultCity(String city) {
        prefs.edit().putString(KEY_DEFAULT_CITY, city == null ? "" : city.trim()).apply();
    }

    public void setReminderMinutes(int minutes) {
        prefs.edit().putInt(KEY_REMINDER_MINUTES, minutes).apply();
    }

    public void setMiniGameRecord(int record) {
        prefs.edit().putInt(KEY_MINIGAME_RECORD, record).apply();
    }

    // ===== Application des réglages =====

    /** Thème clair / sombre / comme le téléphone. Les écrans ouverts sont recréés automatiquement. */
    public static void applyTheme(String theme) {
        int mode;
        if (THEME_LIGHT.equals(theme)) {
            mode = AppCompatDelegate.MODE_NIGHT_NO;
        } else if (THEME_DARK.equals(theme)) {
            mode = AppCompatDelegate.MODE_NIGHT_YES;
        } else {
            mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        }
        AppCompatDelegate.setDefaultNightMode(mode);
    }

    // La langue s'applique avec LocaleHelper.applyLanguage (util/LocaleHelper).
}
