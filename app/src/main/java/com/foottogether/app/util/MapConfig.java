package com.foottogether.app.util;

import android.content.Context;

import com.foottogether.app.BuildConfig;

import org.osmdroid.config.Configuration;
import org.osmdroid.config.IConfigurationProvider;

import java.io.File;

/**
 * Réglages d'osmdroid (carte OpenStreetMap, gratuite et sans clé), à faire AVANT d'afficher une carte.
 * - User-Agent = identifiant de l'app : les serveurs de tuiles OpenStreetMap l'exigent.
 * - Les tuiles (images de la carte) sont mises en cache dans le dossier cache de l'app :
 *   pas besoin de permission de stockage.
 */
public final class MapConfig {

    private static boolean initialized;

    private MapConfig() {
    }

    public static void init(Context context) {
        if (initialized) return;
        Context appContext = context.getApplicationContext();
        IConfigurationProvider config = Configuration.getInstance();
        config.load(appContext, appContext.getSharedPreferences("osmdroid", Context.MODE_PRIVATE));
        config.setUserAgentValue(BuildConfig.APPLICATION_ID);
        File base = new File(appContext.getCacheDir(), "osmdroid");
        config.setOsmdroidBasePath(base);
        config.setOsmdroidTileCache(new File(base, "tiles"));
        initialized = true;
    }
}
