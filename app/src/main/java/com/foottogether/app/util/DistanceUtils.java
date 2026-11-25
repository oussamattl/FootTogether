package com.foottogether.app.util;

import android.content.Context;
import android.location.Location;

import androidx.annotation.Nullable;

import com.foottogether.app.data.model.Match;

import java.util.Locale;

/**
 * Distance entre le joueur et un match, et son affichage (« 850 m », « 1,2 km », « 24 km »).
 */
public final class DistanceUtils {

    private DistanceUtils() {
    }

    /** Distance en mètres, ou null si on ne connaît pas l'une des deux positions. */
    @Nullable
    public static Float metersTo(@Nullable Location user, Match match) {
        if (user == null || match.getLat() == null || match.getLng() == null) return null;
        float[] result = new float[1];
        // Calcul « à vol d'oiseau » sur la sphère terrestre, fourni par Android
        Location.distanceBetween(user.getLatitude(), user.getLongitude(),
                match.getLat(), match.getLng(), result);
        return result[0];
    }

    /** Mise en forme lisible, avec la virgule (français) ou le point (anglais) selon la langue de l'app. */
    public static String format(Context context, float meters) {
        Locale locale = LocaleHelper.appLocale(context);
        if (meters < 1000) {
            return String.format(locale, "%d m", Math.round(meters / 10f) * 10);
        }
        if (meters < 10_000) {
            return String.format(locale, "%.1f km", meters / 1000f);
        }
        return String.format(locale, "%d km", Math.round(meters / 1000f));
    }
}
