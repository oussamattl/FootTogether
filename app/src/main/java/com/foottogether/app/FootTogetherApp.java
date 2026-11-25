package com.foottogether.app;

import android.app.Application;

import com.foottogether.app.data.prefs.AppPrefs;
import com.foottogether.app.util.NotificationHelper;

/**
 * Classe Application : créée par Android AVANT tout écran, une fois par lancement de l'app
 * (déclarée dans le manifeste avec android:name).
 * On y applique les réglages qui doivent valoir partout dès le départ.
 */
public class FootTogetherApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // Thème clair / sombre choisi dans les Paramètres (SharedPreferences)
        AppPrefs.applyTheme(new AppPrefs(this).getTheme());
        // Canaux de notification prêts avant la première notification ou le premier rappel
        NotificationHelper.createChannels(this);
    }
}
