package com.foottogether.app.util;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.foottogether.app.data.remote.ResultCallback;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;

/**
 * Position du téléphone avec le « Fused Location Provider » de Google Play services
 * (gratuit) : il combine GPS, Wi-Fi et réseau mobile pour donner une position rapidement.
 *
 * La permission de localisation doit être ACCORDÉE par l'utilisateur pendant l'utilisation
 * (demandée par les écrans avec ActivityResultContracts.RequestMultiplePermissions).
 * Si elle est refusée, l'app fonctionne quand même : recherche par ville.
 */
public final class LocationHelper {

    /** Les deux permissions demandées ensemble (Android 12+ laisse choisir « approximative »). */
    public static final String[] PERMISSIONS = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    };

    /** Dernière position obtenue, gardée en mémoire pour les autres écrans. */
    @Nullable
    private static Location lastLocation;

    private LocationHelper() {
    }

    /** Vrai si l'utilisateur a accordé la localisation (précise ou approximative). */
    public static boolean hasPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Nullable
    public static Location getLastLocation() {
        return lastLocation;
    }

    /**
     * Demande la position actuelle ; si le téléphone ne la trouve pas,
     * on se rabat sur la dernière position connue.
     */
    @SuppressLint("MissingPermission") // la permission est vérifiée juste avant
    public static void getCurrentLocation(Context context, ResultCallback<Location> callback) {
        if (!hasPermission(context)) {
            callback.onError(new SecurityException("Localisation non autorisée"));
            return;
        }
        FusedLocationProviderClient client = LocationServices.getFusedLocationProviderClient(context);
        client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                        new CancellationTokenSource().getToken())
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        lastLocation = location;
                        callback.onSuccess(location);
                        return;
                    }
                    client.getLastLocation()
                            .addOnSuccessListener(last -> {
                                if (last != null) {
                                    lastLocation = last;
                                    callback.onSuccess(last);
                                } else {
                                    callback.onError(new IllegalStateException("Position inconnue"));
                                }
                            })
                            .addOnFailureListener(callback::onError);
                })
                .addOnFailureListener(callback::onError);
    }
}
