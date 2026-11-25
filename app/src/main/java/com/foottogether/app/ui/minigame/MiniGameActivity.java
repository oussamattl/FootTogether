package com.foottogether.app.ui.minigame;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.foottogether.app.R;
import com.foottogether.app.data.local.ScoreDao;
import com.foottogether.app.data.prefs.AppPrefs;
import com.foottogether.app.data.remote.AuthRepository;
import com.foottogether.app.data.remote.ResultCallback;
import com.foottogether.app.util.BackgroundTasks;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

/**
 * Mini-jeu « Jongles » : le CAPTEUR demandé par l'énoncé (accéléromètre).
 *
 * Principe : pendant 30 secondes, chaque secousse du téléphone = un jongle.
 * - SensorManager donne l'accéléromètre ; on s'abonne dans onResume et on se désabonne
 *   dans onPause (un capteur allumé pour rien vide la batterie).
 * - L'accéléromètre mesure aussi la pesanteur (≈ 9,8 m/s² vers le bas). Un filtre « passe-bas »
 *   estime cette pesanteur ; on la retire pour ne garder que le mouvement de la main.
 *   Si ce mouvement dépasse un seuil → jongle (au plus un tous les 250 ms).
 * - Toucher le ballon compte aussi (utile sur l'émulateur, ou sans accéléromètre).
 * - Score de chaque partie dans SQLite (ScoreDao), record dans SharedPreferences (AppPrefs).
 * - Rotation de l'écran : score, temps restant et état de la partie sont gardés dans le Bundle.
 */
public class MiniGameActivity extends AppCompatActivity implements SensorEventListener {

    private static final String TAG = "MiniGame";

    private static final long GAME_DURATION_MS = 30_000;
    /** Accélération (hors pesanteur) à partir de laquelle on compte un jongle, en m/s². */
    private static final float SHAKE_THRESHOLD = 5f;
    private static final long MIN_DELAY_BETWEEN_JUGGLES_MS = 250;
    /** Poids du filtre passe-bas : plus il est proche de 1, plus la pesanteur estimée est stable. */
    private static final float GRAVITY_FILTER = 0.8f;
    private static final int HISTORY_SIZE = 5;

    // États de la partie
    private static final int STATE_READY = 0;
    private static final int STATE_RUNNING = 1;
    private static final int STATE_OVER = 2;

    // Clés du Bundle
    private static final String KEY_STATE = "state";
    private static final String KEY_SCORE = "score";
    private static final String KEY_REMAINING = "remaining_ms";
    private static final String KEY_NEW_RECORD = "new_record";

    private SensorManager sensorManager;
    @Nullable
    private Sensor accelerometer;
    private final float[] gravity = new float[3];
    private boolean gravityReady;
    private long lastJuggleAt;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = this::onTick;

    private int state = STATE_READY;
    private int score;
    private long remainingMs = GAME_DURATION_MS;
    /** Heure (horloge depuis le démarrage du téléphone) de fin de partie, pendant qu'elle tourne. */
    private long endAt;
    private boolean newRecord;

    private AppPrefs prefs;
    private String uid;

    private TextView timeText;
    private TextView scoreText;
    private ImageView ball;
    private TextView message;
    private MaterialButton startButton;
    private TextView recordText;
    private TextView historyText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        uid = new AuthRepository().getCurrentUid();
        if (uid == null) {
            finish();
            return;
        }
        prefs = new AppPrefs(this);

        WindowCompat.enableEdgeToEdge(getWindow());
        setContentView(R.layout.activity_minigame);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.game_root), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        MaterialToolbar toolbar = findViewById(R.id.game_toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        timeText = findViewById(R.id.game_time);
        scoreText = findViewById(R.id.game_score);
        ball = findViewById(R.id.game_ball);
        message = findViewById(R.id.game_message);
        startButton = findViewById(R.id.game_start);
        recordText = findViewById(R.id.game_record);
        historyText = findViewById(R.id.game_history);

        sensorManager = getSystemService(SensorManager.class);
        accelerometer = sensorManager == null ? null : sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        // Bundle : on reprend la partie là où elle en était avant la rotation
        if (savedInstanceState != null) {
            state = savedInstanceState.getInt(KEY_STATE, STATE_READY);
            score = savedInstanceState.getInt(KEY_SCORE, 0);
            remainingMs = savedInstanceState.getLong(KEY_REMAINING, GAME_DURATION_MS);
            newRecord = savedInstanceState.getBoolean(KEY_NEW_RECORD, false);
        }

        startButton.setOnClickListener(v -> startGame());
        ball.setOnClickListener(v -> {
            if (state == STATE_RUNNING) juggle();
        });

        updateScreen();
        loadHistory();
    }

    /** L'écran redevient visible : on rallume le capteur et on relance le chrono. */
    @Override
    protected void onResume() {
        super.onResume();
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        }
        gravityReady = false;
        if (state == STATE_RUNNING) {
            endAt = SystemClock.elapsedRealtime() + remainingMs;
            handler.post(tick);
        }
    }

    /** L'écran est caché (appel, autre app, rotation) : capteur éteint, chrono en pause. */
    @Override
    protected void onPause() {
        if (accelerometer != null) sensorManager.unregisterListener(this);
        if (state == STATE_RUNNING) {
            remainingMs = Math.max(0, endAt - SystemClock.elapsedRealtime());
        }
        handler.removeCallbacks(tick);
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_STATE, state);
        outState.putInt(KEY_SCORE, score);
        outState.putLong(KEY_REMAINING, remainingMs);
        outState.putBoolean(KEY_NEW_RECORD, newRecord);
    }

    // ===== Partie =====

    private void startGame() {
        state = STATE_RUNNING;
        score = 0;
        newRecord = false;
        remainingMs = GAME_DURATION_MS;
        endAt = SystemClock.elapsedRealtime() + remainingMs;
        handler.removeCallbacks(tick);
        handler.post(tick);
        updateScreen();
    }

    /** Appelé toutes les 100 ms pendant la partie : met à jour le chrono. */
    private void onTick() {
        remainingMs = Math.max(0, endAt - SystemClock.elapsedRealtime());
        if (remainingMs == 0) {
            endGame();
            return;
        }
        updateTime();
        handler.postDelayed(tick, 100);
    }

    private void juggle() {
        score++;
        scoreText.setText(String.valueOf(score));
        ball.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        // Petit saut du ballon : il monte puis redescend
        float jump = -getResources().getDisplayMetrics().density * 70;
        ball.animate().cancel();
        ball.setTranslationY(0);
        ball.animate().translationY(jump).setDuration(120).withEndAction(() ->
                ball.animate().translationY(0).setDuration(180).start()).start();
    }

    private void endGame() {
        state = STATE_OVER;
        handler.removeCallbacks(tick);
        int finalScore = score;
        if (finalScore > prefs.getMiniGameRecord()) {
            prefs.setMiniGameRecord(finalScore); // SharedPreferences
            newRecord = true;
        }
        updateScreen();
        // Score de la partie dans SQLite (en arrière-plan), puis on recharge l'historique
        BackgroundTasks.run(() -> {
            new ScoreDao(this).add(uid, finalScore);
            return true;
        }, new ResultCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean saved) {
                loadHistory();
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Score non enregistré", e);
            }
        });
    }

    // ===== Accéléromètre =====

    @Override
    public void onSensorChanged(SensorEvent event) {
        float[] v = event.values; // accélération sur les axes x, y, z (m/s²)
        if (!gravityReady) {
            System.arraycopy(v, 0, gravity, 0, 3);
            gravityReady = true;
            return;
        }
        // Filtre passe-bas : la pesanteur change lentement, la main bouge vite
        float linear = 0;
        for (int i = 0; i < 3; i++) {
            gravity[i] = GRAVITY_FILTER * gravity[i] + (1 - GRAVITY_FILTER) * v[i];
            float moving = v[i] - gravity[i];
            linear += moving * moving;
        }
        linear = (float) Math.sqrt(linear);

        long now = SystemClock.elapsedRealtime();
        if (state == STATE_RUNNING && linear > SHAKE_THRESHOLD
                && now - lastJuggleAt > MIN_DELAY_BETWEEN_JUGGLES_MS) {
            lastJuggleAt = now;
            juggle();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Rien à faire
    }

    // ===== Affichage =====

    private void updateScreen() {
        scoreText.setText(String.valueOf(score));
        updateTime();
        recordText.setText(getString(R.string.minigame_record, prefs.getMiniGameRecord()));
        startButton.setVisibility(state == STATE_RUNNING ? View.INVISIBLE : View.VISIBLE);
        startButton.setText(state == STATE_OVER ? R.string.minigame_restart : R.string.minigame_start);
        if (state == STATE_RUNNING) {
            message.setText(accelerometer != null ? R.string.minigame_running : R.string.minigame_running_no_sensor);
        } else if (state == STATE_OVER) {
            message.setText(newRecord
                    ? getResources().getQuantityString(R.plurals.minigame_new_record, score, score)
                    : getResources().getQuantityString(R.plurals.minigame_final_score, score, score));
        } else {
            message.setText(accelerometer != null ? R.string.minigame_instructions : R.string.minigame_no_sensor);
        }
    }

    private void updateTime() {
        // Arrondi au-dessus : on affiche « 1 » jusqu'à la toute fin
        timeText.setText(String.valueOf((remainingMs + 999) / 1000));
    }

    /** Les 5 dernières parties de ce joueur, lues dans SQLite. */
    private void loadHistory() {
        BackgroundTasks.run(() -> new ScoreDao(this).getLastScores(uid, HISTORY_SIZE),
                new ResultCallback<List<Integer>>() {
                    @Override
                    public void onSuccess(List<Integer> scores) {
                        if (isFinishing() || isDestroyed()) return;
                        if (scores.isEmpty()) {
                            historyText.setText(R.string.minigame_no_history);
                            return;
                        }
                        List<String> parts = new ArrayList<>();
                        for (Integer s : scores) parts.add(String.valueOf(s));
                        historyText.setText(getString(R.string.minigame_history, TextUtils.join(" · ", parts)));
                    }

                    @Override
                    public void onError(Exception e) {
                        Log.e(TAG, "Historique illisible", e);
                    }
                });
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(tick);
        super.onDestroy();
    }
}
