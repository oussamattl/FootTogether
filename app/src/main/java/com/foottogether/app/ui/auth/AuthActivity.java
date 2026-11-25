package com.foottogether.app.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.foottogether.app.R;
import com.foottogether.app.ui.main.MainActivity;
import com.google.android.material.tabs.TabLayout;

/**
 * Écran d'authentification. Il contient deux onglets ; chaque onglet affiche un FRAGMENT :
 * LoginFragment (connexion) ou RegisterFragment (inscription).
 *
 * Un Fragment est un "morceau d'écran" réutilisable, avec son propre layout et son cycle de vie,
 * qui vit à l'intérieur d'une Activity. Ici, l'activité garde l'en-tête et les onglets,
 * et remplace seulement le formulaire du dessous.
 */
public class AuthActivity extends AppCompatActivity {

    public static final int TAB_LOGIN = 0;
    public static final int TAB_REGISTER = 1;

    /** Clé utilisée dans le Bundle pour retenir l'onglet choisi (ex. lors d'une rotation). */
    private static final String KEY_SELECTED_TAB = "selected_tab";

    private TabLayout tabs;
    private TextView title;
    private TextView subtitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.enableEdgeToEdge(getWindow());
        setContentView(R.layout.activity_auth);
        applyWindowInsets();

        tabs = findViewById(R.id.auth_tabs);
        title = findViewById(R.id.auth_title);
        subtitle = findViewById(R.id.auth_subtitle);

        tabs.addTab(tabs.newTab().setText(R.string.tab_login));
        tabs.addTab(tabs.newTab().setText(R.string.tab_register));

        // Bundle : si l'activité est recréée (rotation...), on retrouve l'onglet sélectionné.
        int selectedTab = savedInstanceState == null
                ? TAB_LOGIN
                : savedInstanceState.getInt(KEY_SELECTED_TAB, TAB_LOGIN);
        tabs.selectTab(tabs.getTabAt(selectedTab));
        updateHeader(selectedTab);

        // Au premier lancement seulement : à la recréation, Android remet lui-même le bon fragment
        // (et les champs de saisie gardent leur texte automatiquement).
        if (savedInstanceState == null) {
            showFragment(selectedTab);
        }

        // On branche l'écouteur APRÈS la restauration, pour ne pas recréer le fragment pour rien.
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                showFragment(tab.getPosition());
                updateHeader(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_SELECTED_TAB, tabs.getSelectedTabPosition());
    }

    /** Appelé par les fragments (liens « Créer un compte » / « Se connecter »). */
    public void showTab(int position) {
        tabs.selectTab(tabs.getTabAt(position));
    }

    /** Appelé par les fragments après une connexion ou une inscription réussie. */
    public void openMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    /** Remplace le formulaire affiché dans le conteneur par celui de l'onglet choisi. */
    private void showFragment(int position) {
        Fragment fragment = position == TAB_REGISTER ? new RegisterFragment() : new LoginFragment();
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.auth_container, fragment)
                .commit();
    }

    private void updateHeader(int position) {
        if (position == TAB_REGISTER) {
            title.setText(R.string.auth_register_title);
            subtitle.setText(R.string.auth_register_subtitle);
        } else {
            title.setText(R.string.auth_login_title);
            subtitle.setText(R.string.auth_login_subtitle);
        }
    }

    /**
     * L'app dessine sous la barre d'état : l'en-tête vert va jusqu'en haut de l'écran,
     * et on décale son contenu de la hauteur de la barre. En bas, on laisse la place
     * à la barre de navigation ou au clavier (ime).
     */
    private void applyWindowInsets() {
        View root = findViewById(R.id.main);
        View header = findViewById(R.id.auth_header);
        int headerTopPadding = getResources().getDimensionPixelSize(R.dimen.auth_header_padding_top);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            header.setPadding(header.getPaddingLeft(), headerTopPadding + bars.top,
                    header.getPaddingRight(), header.getPaddingBottom());
            v.setPadding(bars.left, 0, bars.right, Math.max(bars.bottom, ime.bottom));
            return insets;
        });
    }
}
