package com.foottogether.app.util;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;

import androidx.annotation.Nullable;

import com.foottogether.app.data.remote.ResultCallback;

import java.util.List;
import java.util.Locale;

/**
 * Conversion adresse ↔ coordonnées avec android.location.Geocoder (gratuit, fourni par le téléphone).
 * Le Geocoder interroge un serveur : on l'appelle en arrière-plan (BackgroundTasks).
 * Il peut ne rien trouver (ou être absent sur certains appareils) : onSuccess reçoit alors null,
 * et l'app propose « Utiliser ma position » comme solution de repli.
 */
public final class GeocoderHelper {

    private GeocoderHelper() {
    }

    /** Adresse texte (« City stade, Valenciennes ») → coordonnées (Address), ou null. */
    public static void geocode(Context context, String query, ResultCallback<Address> callback) {
        Context appContext = context.getApplicationContext();
        BackgroundTasks.run(() -> firstOrNull(
                Geocoder.isPresent()
                        ? fromName(new Geocoder(appContext, Locale.getDefault()), query)
                        : null), callback);
    }

    /** Coordonnées → adresse (rue, ville), ou null. */
    public static void reverse(Context context, double lat, double lng, ResultCallback<Address> callback) {
        Context appContext = context.getApplicationContext();
        BackgroundTasks.run(() -> firstOrNull(
                Geocoder.isPresent()
                        ? fromLocation(new Geocoder(appContext, Locale.getDefault()), lat, lng)
                        : null), callback);
    }

    // Les versions « synchrones » sont dépréciées depuis Android 13 au profit d'un callback,
    // mais restent les seules disponibles dès Android 7 (minSdk 24). On les appelle hors du thread principal.
    @SuppressWarnings("deprecation")
    private static List<Address> fromName(Geocoder geocoder, String query) throws java.io.IOException {
        return geocoder.getFromLocationName(query, 1);
    }

    @SuppressWarnings("deprecation")
    private static List<Address> fromLocation(Geocoder geocoder, double lat, double lng)
            throws java.io.IOException {
        return geocoder.getFromLocation(lat, lng, 1);
    }

    @Nullable
    private static Address firstOrNull(@Nullable List<Address> list) {
        return (list == null || list.isEmpty()) ? null : list.get(0);
    }
}
