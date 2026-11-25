package com.foottogether.app.ui.leaderboard;

import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.foottogether.app.R;
import com.foottogether.app.data.model.User;
import com.foottogether.app.data.remote.AuthRepository;
import com.foottogether.app.data.remote.ResultCallback;
import com.foottogether.app.data.remote.UserRepository;
import com.foottogether.app.util.ErrorMessages;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.divider.MaterialDividerItemDecoration;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;

import java.util.List;

/**
 * Classement (lot 9), ouvert depuis le Profil : top 20 des joueurs (matchs joués)
 * ou des organisateurs (matchs organisés). L'onglet choisi survit à la rotation (Bundle).
 */
public class LeaderboardActivity extends AppCompatActivity {

    private static final String TAG = "Leaderboard";
    private static final String KEY_TAB = "selected_tab";
    private static final int TOP = 20;
    private static final int TAB_PLAYERS = 0;
    private static final int TAB_ORGANIZERS = 1;

    private final UserRepository userRepository = new UserRepository();
    private int selectedTab = TAB_PLAYERS;
    private LeaderboardAdapter adapter;
    private View root;
    private View loading;
    private View empty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String uid = new AuthRepository().getCurrentUid();
        if (uid == null) {
            finish();
            return;
        }
        if (savedInstanceState != null) selectedTab = savedInstanceState.getInt(KEY_TAB, TAB_PLAYERS);

        WindowCompat.enableEdgeToEdge(getWindow());
        setContentView(R.layout.activity_leaderboard);
        root = findViewById(R.id.leaderboard_root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        MaterialToolbar toolbar = findViewById(R.id.leaderboard_toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        loading = findViewById(R.id.leaderboard_loading);
        empty = findViewById(R.id.leaderboard_empty);

        RecyclerView list = findViewById(R.id.leaderboard_list);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new LeaderboardAdapter(uid);
        list.setAdapter(adapter);
        MaterialDividerItemDecoration divider =
                new MaterialDividerItemDecoration(this, LinearLayoutManager.VERTICAL);
        divider.setDividerColor(ContextCompat.getColor(this, R.color.outline));
        divider.setLastItemDecorated(false);
        list.addItemDecoration(divider);

        TabLayout tabs = findViewById(R.id.leaderboard_tabs);
        tabs.addTab(tabs.newTab().setText(R.string.leaderboard_players), selectedTab == TAB_PLAYERS);
        tabs.addTab(tabs.newTab().setText(R.string.leaderboard_organizers), selectedTab == TAB_ORGANIZERS);
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                selectedTab = tab.getPosition();
                load();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });
        load();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_TAB, selectedTab);
    }

    private void load() {
        int tab = selectedTab;
        boolean organizers = tab == TAB_ORGANIZERS;
        loading.setVisibility(View.VISIBLE);
        empty.setVisibility(View.GONE);
        userRepository.getTopUsers(organizers ? "matchesOrganized" : "matchesPlayed", TOP,
                new ResultCallback<List<User>>() {
                    @Override
                    public void onSuccess(List<User> users) {
                        if (isFinishing() || tab != selectedTab) return;
                        loading.setVisibility(View.GONE);
                        adapter.setUsers(users, organizers);
                        empty.setVisibility(users.isEmpty() ? View.VISIBLE : View.GONE);
                    }

                    @Override
                    public void onError(Exception e) {
                        Log.e(TAG, "Classement non chargé", e);
                        if (isFinishing()) return;
                        loading.setVisibility(View.GONE);
                        Snackbar.make(root, ErrorMessages.forFirestore(e, R.string.error_generic),
                                Snackbar.LENGTH_LONG).show();
                    }
                });
    }
}
