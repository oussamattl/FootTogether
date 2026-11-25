package com.foottogether.app.ui.create;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.foottogether.app.R;
import com.google.android.material.appbar.MaterialToolbar;

/**
 * Écran « Modifier le match » (organisateur seulement).
 * Il réutilise le MÊME formulaire que l'onglet Créer (CreateMatchFragment), en mode modification.
 * Quand l'enregistrement réussit, il renvoie RESULT_OK à l'écran Détail, qui affiche un message.
 */
public class EditMatchActivity extends AppCompatActivity implements CreateMatchFragment.Host {

    private static final String EXTRA_MATCH_ID = "match_id";

    public static Intent newIntent(Context context, String matchId) {
        return new Intent(context, EditMatchActivity.class).putExtra(EXTRA_MATCH_ID, matchId);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String matchId = getIntent().getStringExtra(EXTRA_MATCH_ID);
        if (matchId == null) {
            finish();
            return;
        }
        WindowCompat.enableEdgeToEdge(getWindow());
        setContentView(R.layout.activity_edit_match);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.edit_root), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
            return insets;
        });
        // Flèche ← de la barre du haut : on ferme l'écran sans enregistrer
        MaterialToolbar toolbar = findViewById(R.id.edit_toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        // Après une rotation, le FragmentManager remet lui-même le fragment : on ne l'ajoute qu'une fois
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.edit_container, CreateMatchFragment.newEditInstance(matchId))
                    .commit();
        }
    }

    @Override
    public void onMatchSaved(boolean edited) {
        setResult(Activity.RESULT_OK);
        finish();
    }
}
