package com.foottogether.app.ui.main;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.foottogether.app.R;
import com.foottogether.app.data.model.AppNotification;
import com.foottogether.app.data.remote.AuthRepository;
import com.foottogether.app.data.remote.NotificationRepository;
import com.foottogether.app.data.remote.ResultCallback;
import com.foottogether.app.service.NotificationSyncService;
import com.foottogether.app.ui.auth.AuthActivity;
import com.foottogether.app.ui.create.CreateMatchFragment;
import com.foottogether.app.ui.favorites.FavoritesActivity;
import com.foottogether.app.ui.settings.SettingsActivity;
import com.foottogether.app.ui.home.HomeFragment;
import com.foottogether.app.ui.mymatches.MyMatchesFragment;
import com.foottogether.app.ui.notifications.NotificationsActivity;
import com.foottogether.app.ui.profile.ProfileFragment;
import com.foottogether.app.ui.search.SearchFragment;
import com.foottogether.app.util.NetworkMonitor;
import com.foottogether.app.util.ReminderScheduler;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.List;

/**
 * Écran principal, une fois connecté.
 * - En haut : MaterialToolbar avec la cloche (notifications) et le menu ⋮ (options menu),
 *   puis le bandeau « Serveur momentanément inaccessible » quand il n'y a plus de réseau.
 * - Au milieu : le fragment de l'onglet choisi.
 * - En bas : BottomNavigationView à 5 entrées + bouton rond "+" au centre.
 * Il démarre aussi le Service des notifications (NotificationSyncService).
 */
public class MainActivity extends AppCompatActivity implements CreateMatchFragment.Host {

    private static final String TAG = "MainActivity";
    /** Clé du Bundle pour retenir l'onglet sélectionné quand l'écran est recréé (rotation...). */
    private static final String KEY_SELECTED_TAB = "selected_tab";

    private final AuthRepository authRepository = new AuthRepository();
    private final NotificationRepository notificationRepository = new NotificationRepository();

    private MaterialToolbar toolbar;
    private BottomNavigationView bottomNav;
    private View offlineBanner;
    private NetworkMonitor networkMonitor;
    /** Écoute des notifications non lues (pastille sur la cloche). */
    private ListenerRegistration unreadListener;
    private boolean hasUnread;

    /**
     * Android 13+ : demande d'autorisation d'afficher des notifications.
     * En cas de refus, l'app marche quand même : l'historique reste dans l'écran Notifications.
     */
    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (!granted) showMessage(R.string.notifications_permission_denied);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Sécurité : si la session a disparu, retour à l'écran de connexion
        if (!authRepository.isLoggedIn()) {
            openAuthAndFinish();
            return;
        }

        WindowCompat.enableEdgeToEdge(getWindow());
        setContentView(R.layout.activity_main);

        toolbar = findViewById(R.id.toolbar);
        bottomNav = findViewById(R.id.bottom_nav);
        offlineBanner = findViewById(R.id.main_offline_banner);
        networkMonitor = new NetworkMonitor(this);
        // La toolbar devient la "barre d'action" de l'activité : c'est elle qui affiche le menu ⋮
        setSupportActionBar(toolbar);

        // La toolbar descend sous la barre d'état ; la barre du bas gère seule la barre de navigation.
        ViewCompat.setOnApplyWindowInsetsListener(toolbar, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, 0);
            return insets;
        });

        bottomNav.setOnItemSelectedListener(item -> {
            showFragment(item.getItemId());
            return true;
        });
        // Toucher l'onglet déjà ouvert : on ne recharge rien
        bottomNav.setOnItemReselectedListener(item -> { });

        findViewById(R.id.fab_create).setOnClickListener(v ->
                bottomNav.setSelectedItemId(R.id.nav_create));

        if (savedInstanceState == null) {
            // Premier lancement : on ouvre l'Accueil
            showFragment(R.id.nav_home);
        } else {
            // Recréation : le FragmentManager a déjà remis le bon fragment, on remet le bon titre
            int selected = savedInstanceState.getInt(KEY_SELECTED_TAB, R.id.nav_home);
            bottomNav.getMenu().findItem(selected).setChecked(true);
            updateTitle(selected);
        }

        // Le Service qui affiche les notifications reçues (un 2e démarrage est sans effet)
        NotificationSyncService.start(this);
        if (savedInstanceState == null) askNotificationPermission();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Bandeau hors ligne mis à jour en direct
        networkMonitor.start(online -> offlineBanner.setVisibility(online ? View.GONE : View.VISIBLE));
        listenUnread();
    }

    @Override
    protected void onStop() {
        networkMonitor.stop();
        if (unreadListener != null) {
            unreadListener.remove();
            unreadListener = null;
        }
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_SELECTED_TAB, bottomNav.getSelectedItemId());
    }

    // ===== Notifications =====

    /** Android 13 et plus : la permission POST_NOTIFICATIONS doit être demandée à l'utilisateur. */
    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    /** Pastille rouge sur la cloche tant qu'il reste des notifications non lues. */
    private void listenUnread() {
        String uid = authRepository.getCurrentUid();
        if (uid == null) return;
        unreadListener = notificationRepository.listenUnread(uid, new ResultCallback<List<AppNotification>>() {
            @Override
            public void onSuccess(List<AppNotification> unread) {
                boolean newValue = !unread.isEmpty();
                if (newValue != hasUnread) {
                    hasUnread = newValue;
                    invalidateOptionsMenu(); // Android rappelle onPrepareOptionsMenu
                }
            }

            @Override
            public void onError(Exception e) {
                Log.w(TAG, "Notifications non lues indisponibles", e);
            }
        });
    }

    // ===== Onglets =====

    /** Remplace le fragment affiché au centre selon l'onglet choisi. */
    private void showFragment(int itemId) {
        Fragment fragment;
        if (itemId == R.id.nav_search) {
            fragment = new SearchFragment();
        } else if (itemId == R.id.nav_create) {
            fragment = new CreateMatchFragment();
        } else if (itemId == R.id.nav_my_matches) {
            fragment = new MyMatchesFragment();
        } else if (itemId == R.id.nav_profile) {
            fragment = new ProfileFragment();
        } else {
            fragment = new HomeFragment();
        }
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.main_container, fragment)
                .commit();
        updateTitle(itemId);
    }

    private void updateTitle(int itemId) {
        int title;
        if (itemId == R.id.nav_search) {
            title = R.string.nav_search;
        } else if (itemId == R.id.nav_create) {
            title = R.string.nav_create_description;
        } else if (itemId == R.id.nav_my_matches) {
            title = R.string.nav_my_matches;
        } else if (itemId == R.id.nav_profile) {
            title = R.string.nav_profile;
        } else {
            title = R.string.app_name;
        }
        toolbar.setTitle(title);
    }

    // ===== Menu de la toolbar (options menu) =====

    /** Android appelle cette méthode pour construire le menu de la barre du haut. */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    /** Appelée avant chaque affichage du menu (et après invalidateOptionsMenu). */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem bell = menu.findItem(R.id.action_notifications);
        if (bell != null) {
            bell.setIcon(hasUnread ? R.drawable.ic_notifications_unread : R.drawable.ic_notifications);
            bell.setTitle(hasUnread ? R.string.menu_notifications_unread : R.string.menu_notifications);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    /** Appelée quand l'utilisateur touche une entrée du menu. */
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_notifications) {
            startActivity(new Intent(this, NotificationsActivity.class));
            return true;
        } else if (id == R.id.action_profile) {
            bottomNav.setSelectedItemId(R.id.nav_profile);
            return true;
        } else if (id == R.id.action_logout) {
            confirmLogout();
            return true;
        } else if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        } else if (id == R.id.action_favorites) {
            startActivity(new Intent(this, FavoritesActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /** Boîte de dialogue de confirmation avant de se déconnecter. */
    private void confirmLogout() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.logout_confirm_title)
                .setMessage(R.string.logout_confirm_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.menu_logout, (dialog, which) -> {
                    // Le joueur suivant ne doit recevoir ni les notifications ni les rappels du précédent
                    NotificationSyncService.stop(this);
                    ReminderScheduler.cancelAll(this);
                    authRepository.logout();
                    openAuthAndFinish();
                })
                .show();
    }

    /**
     * Ouvre l'écran de connexion et vide la pile d'écrans :
     * après une déconnexion, le bouton Retour ne doit pas ramener dans l'app.
     */
    private void openAuthAndFinish() {
        Intent intent = new Intent(this, AuthActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    /** Affiche un court message (Snackbar) au-dessus de la barre du bas. Utilisé aussi par les fragments. */
    public void showMessage(int messageRes) {
        Snackbar.make(findViewById(R.id.main), messageRes, Snackbar.LENGTH_SHORT)
                .setAnchorView(bottomNav)
                .show();
    }

    /** Le formulaire de l'onglet Créer a publié un match : message puis retour à l'Accueil. */
    @Override
    public void onMatchSaved(boolean edited) {
        showMessage(R.string.match_published);
        selectTab(R.id.nav_home);
    }

    /** Ouvre un onglet de la barre du bas (ex. l'Accueil après la publication d'un match). */
    public void selectTab(int itemId) {
        bottomNav.setSelectedItemId(itemId);
    }
}
