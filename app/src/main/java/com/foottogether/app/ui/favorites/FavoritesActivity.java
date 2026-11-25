package com.foottogether.app.ui.favorites;

import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.foottogether.app.R;
import com.foottogether.app.data.local.MatchCacheDao;
import com.foottogether.app.data.local.RecentDao;
import com.foottogether.app.data.model.Match;
import com.foottogether.app.data.remote.AuthRepository;
import com.foottogether.app.data.remote.ResultCallback;
import com.foottogether.app.ui.common.MatchAdapter;
import com.foottogether.app.ui.detail.MatchDetailActivity;
import com.foottogether.app.util.BackgroundTasks;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;

import java.util.List;

/**
 * Écran Favoris (menu ⋮ → Favoris), avec deux onglets :
 * - « Favoris » : les matchs marqués d'un cœur dans le Détail ;
 * - « Vus récemment » : les derniers matchs ouverts.
 * Tout vient de SQLite (FavoritesDao / RecentDao + match_cache) : l'écran marche sans réseau.
 */
public class FavoritesActivity extends AppCompatActivity {

    private static final String TAG = "FavoritesActivity";
    /** Clé du Bundle : l'onglet choisi survit à la rotation. */
    private static final String KEY_TAB = "selected_tab";
    private static final int TAB_FAVORITES = 0;
    private static final int TAB_RECENTS = 1;

    private String uid;
    private int selectedTab = TAB_FAVORITES;
    private MatchAdapter adapter;
    private MenuItem clearItem;

    private View root;
    private View emptyState;
    private ImageView emptyIcon;
    private TextView emptyTitle;
    private TextView emptyMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        uid = new AuthRepository().getCurrentUid();
        if (uid == null) {
            finish();
            return;
        }
        if (savedInstanceState != null) selectedTab = savedInstanceState.getInt(KEY_TAB, TAB_FAVORITES);

        WindowCompat.enableEdgeToEdge(getWindow());
        setContentView(R.layout.activity_favorites);
        root = findViewById(R.id.favorites_root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        MaterialToolbar toolbar = findViewById(R.id.favorites_toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        clearItem = toolbar.getMenu().findItem(R.id.action_clear_recents);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_clear_recents) {
                clearRecents();
                return true;
            }
            return false;
        });

        emptyState = findViewById(R.id.favorites_empty);
        emptyIcon = findViewById(R.id.favorites_empty_icon);
        emptyTitle = findViewById(R.id.favorites_empty_title);
        emptyMessage = findViewById(R.id.favorites_empty_message);

        RecyclerView list = findViewById(R.id.favorites_list);
        list.setLayoutManager(new GridLayoutManager(this, getResources().getInteger(R.integer.match_columns)));
        adapter = new MatchAdapter(match -> startActivity(MatchDetailActivity.newIntent(this, match.getId())));
        list.setAdapter(adapter);

        TabLayout tabs = findViewById(R.id.favorites_tabs);
        tabs.addTab(tabs.newTab().setText(R.string.favorites_tab), selectedTab == TAB_FAVORITES);
        tabs.addTab(tabs.newTab().setText(R.string.recents_tab), selectedTab == TAB_RECENTS);
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                selectedTab = tab.getPosition();
                adapter.submitList(null); // on vide avant de charger l'autre liste
                load();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });
    }

    /** onStart : on recharge à chaque retour sur l'écran (un favori a pu être retiré dans le Détail). */
    @Override
    protected void onStart() {
        super.onStart();
        load();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_TAB, selectedTab);
    }

    /** Lecture SQLite en arrière-plan, puis affichage sur le thread principal. */
    private void load() {
        int tab = selectedTab;
        clearItem.setVisible(tab == TAB_RECENTS);
        BackgroundTasks.run(() -> {
            MatchCacheDao dao = new MatchCacheDao(this);
            return tab == TAB_FAVORITES ? dao.getFavorites(uid) : dao.getRecents(uid);
        }, new ResultCallback<List<Match>>() {
            @Override
            public void onSuccess(List<Match> matches) {
                if (isFinishing() || tab != selectedTab) return; // l'onglet a changé entre-temps
                adapter.submitList(matches);
                showEmptyState(matches.isEmpty());
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Lecture SQLite impossible", e);
            }
        });
    }

    private void showEmptyState(boolean empty) {
        emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (!empty) return;
        boolean favorites = selectedTab == TAB_FAVORITES;
        emptyIcon.setImageResource(favorites ? R.drawable.ic_favorite_border : R.drawable.ic_schedule);
        emptyTitle.setText(favorites ? R.string.favorites_empty_title : R.string.recents_empty_title);
        emptyMessage.setText(favorites ? R.string.favorites_empty_message : R.string.recents_empty_message);
    }

    private void clearRecents() {
        BackgroundTasks.run(() -> {
            new RecentDao(this).clear(uid);
            return true;
        }, new ResultCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean done) {
                if (isFinishing()) return;
                Snackbar.make(root, R.string.recents_cleared, Snackbar.LENGTH_SHORT).show();
                load();
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Historique non effacé", e);
            }
        });
    }
}
