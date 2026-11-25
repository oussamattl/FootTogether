package com.foottogether.app.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Surveillance de la connexion réseau.
 * - isOnline() : vérification ponctuelle « suis-je connecté maintenant ? » ;
 * - start() / stop() : écoute EN CONTINU (registerDefaultNetworkCallback) pour afficher
 *   ou cacher le bandeau « Serveur momentanément inaccessible » dès que le réseau change.
 *
 * Android appelle le NetworkCallback sur un autre thread : on repasse sur le thread
 * principal (Handler + Looper principal) avant de toucher à l'écran.
 */
public final class NetworkMonitor {

    /** Reçoit l'état du réseau : au démarrage, puis à chaque changement. */
    public interface Listener {
        void onNetworkChanged(boolean online);
    }

    private final Context context;
    private final ConnectivityManager connectivityManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    @Nullable
    private ConnectivityManager.NetworkCallback callback;

    public NetworkMonitor(Context context) {
        this.context = context.getApplicationContext();
        this.connectivityManager = this.context.getSystemService(ConnectivityManager.class);
    }

    /** À appeler dans onStart de l'écran. */
    public void start(Listener listener) {
        stop();
        listener.onNetworkChanged(isOnline(context));
        if (connectivityManager == null) return;
        callback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                mainHandler.post(() -> listener.onNetworkChanged(true));
            }

            @Override
            public void onLost(@NonNull Network network) {
                // Un autre réseau a peut-être pris le relais (Wi-Fi → 4G) : on revérifie
                mainHandler.post(() -> listener.onNetworkChanged(isOnline(context)));
            }
        };
        connectivityManager.registerDefaultNetworkCallback(callback);
    }

    /** À appeler dans onStop : on ne laisse pas d'écouteur actif quand l'écran est caché. */
    public void stop() {
        if (callback != null && connectivityManager != null) {
            connectivityManager.unregisterNetworkCallback(callback);
        }
        callback = null;
        mainHandler.removeCallbacksAndMessages(null);
    }

    /** Vrai si le téléphone a un réseau avec accès à Internet (Wi-Fi ou données mobiles). */
    public static boolean isOnline(Context context) {
        ConnectivityManager cm = context.getSystemService(ConnectivityManager.class);
        if (cm == null) return false;
        Network network = cm.getActiveNetwork();
        if (network == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }
}
