package com.foottogether.app.ui.splash;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

import com.foottogether.app.R;
import com.foottogether.app.data.remote.AuthRepository;
import com.foottogether.app.ui.auth.AuthActivity;
import com.foottogether.app.ui.main.MainActivity;

/**
 * Écran de démarrage (première activité lancée, voir AndroidManifest.xml).
 * Affiche le logo un court instant, puis redirige :
 * - utilisateur déjà connecté (session Firebase conservée) → MainActivity ;
 * - sinon → AuthActivity (connexion / inscription).
 */
public class SplashActivity extends AppCompatActivity {

    private static final long SPLASH_DELAY_MS = 1200;

    // Handler lié au thread principal : il exécute du code plus tard, sans bloquer l'écran
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable goToNextScreen = this::goToNextScreen;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.enableEdgeToEdge(getWindow());
        setContentView(R.layout.activity_splash);
        handler.postDelayed(goToNextScreen, SPLASH_DELAY_MS);
    }

    private void goToNextScreen() {
        boolean loggedIn = new AuthRepository().isLoggedIn();
        Class<?> target = loggedIn ? MainActivity.class : AuthActivity.class;
        startActivity(new Intent(this, target));
        // finish() : on retire le splash de la pile, le bouton Retour ne pourra pas y revenir
        finish();
    }

    @Override
    protected void onDestroy() {
        // Si l'utilisateur quitte pendant le splash, on annule la redirection prévue
        handler.removeCallbacks(goToNextScreen);
        super.onDestroy();
    }
}
