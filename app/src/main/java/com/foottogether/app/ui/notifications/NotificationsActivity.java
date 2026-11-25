package com.foottogether.app.ui.notifications;

import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.foottogether.app.R;
import com.foottogether.app.data.model.AppNotification;
import com.foottogether.app.data.remote.AuthRepository;
import com.foottogether.app.data.remote.NotificationRepository;
import com.foottogether.app.data.remote.ResultCallback;
import com.foottogether.app.ui.detail.MatchDetailActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.divider.MaterialDividerItemDecoration;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

/**
 * Écran Notifications (ouvert par la cloche de la barre du haut) : l'historique en temps réel.
 * Toucher une notification la marque comme lue et ouvre le match concerné.
 */
public class NotificationsActivity extends AppCompatActivity {

    private static final String TAG = "NotificationsActivity";

    private final NotificationRepository notificationRepository = new NotificationRepository();
    private String uid;
    private ListenerRegistration listener;
    private NotificationAdapter adapter;
    private List<AppNotification> current = new ArrayList<>();

    private View loading;
    private View emptyState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        uid = new AuthRepository().getCurrentUid();
        if (uid == null) {
            finish();
            return;
        }
        WindowCompat.enableEdgeToEdge(getWindow());
        setContentView(R.layout.activity_notifications);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.notifications_root), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        MaterialToolbar toolbar = findViewById(R.id.notifications_toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_mark_all_read) {
                notificationRepository.markAllRead(uid, current);
                return true;
            }
            return false;
        });

        loading = findViewById(R.id.notifications_loading);
        emptyState = findViewById(R.id.notifications_empty);

        RecyclerView list = findViewById(R.id.notifications_list);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new NotificationAdapter(this::open);
        list.setAdapter(adapter);
        MaterialDividerItemDecoration divider =
                new MaterialDividerItemDecoration(this, LinearLayoutManager.VERTICAL);
        divider.setDividerColor(ContextCompat.getColor(this, R.color.outline));
        divider.setLastItemDecorated(false);
        list.addItemDecoration(divider);
    }

    @Override
    protected void onStart() {
        super.onStart();
        listener = notificationRepository.listenAll(uid, new ResultCallback<List<AppNotification>>() {
            @Override
            public void onSuccess(List<AppNotification> notifications) {
                current = notifications;
                loading.setVisibility(View.GONE);
                adapter.submitList(notifications);
                emptyState.setVisibility(notifications.isEmpty() ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Notifications non chargées", e);
                loading.setVisibility(View.GONE);
                Snackbar.make(findViewById(R.id.notifications_root), R.string.error_generic,
                        Snackbar.LENGTH_LONG).show();
            }
        });
    }

    @Override
    protected void onStop() {
        if (listener != null) {
            listener.remove();
            listener = null;
        }
        super.onStop();
    }

    private void open(AppNotification notification) {
        if (notification.getId() != null && !notification.isRead()) {
            notificationRepository.markRead(uid, notification.getId());
        }
        if (notification.getMatchId() != null) {
            startActivity(MatchDetailActivity.newIntent(this, notification.getMatchId()));
        }
    }
}
