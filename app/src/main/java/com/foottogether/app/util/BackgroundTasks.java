package com.foottogether.app.util;

import android.os.Handler;
import android.os.Looper;

import com.foottogether.app.data.remote.ResultCallback;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Exécute un travail lent (appel HTTP, Geocoder...) dans un thread d'arrière-plan,
 * puis renvoie le résultat sur le thread principal (le seul qui a le droit de toucher à l'écran).
 *
 * Android interdit le réseau sur le thread principal (NetworkOnMainThreadException) :
 * sans ça, l'application se figerait pendant l'attente.
 */
public final class BackgroundTasks {

    /** 2 threads réutilisés pour toute l'application (ExecutorService). */
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2);
    /** Handler lié au Looper principal : poste du code sur le thread de l'interface. */
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private BackgroundTasks() {
    }

    /** Travail en arrière-plan dont on n'attend pas de résultat (ex. écrire dans SQLite). */
    public static void execute(Runnable work) {
        EXECUTOR.execute(work);
    }

    public static <T> void run(Callable<T> work, ResultCallback<T> callback) {
        EXECUTOR.execute(() -> {
            try {
                T result = work.call();                       // en arrière-plan
                MAIN.post(() -> callback.onSuccess(result));  // retour sur le thread principal
            } catch (Exception e) {
                MAIN.post(() -> callback.onError(e));
            }
        });
    }
}
