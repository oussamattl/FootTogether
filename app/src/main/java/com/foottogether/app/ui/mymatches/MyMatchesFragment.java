package com.foottogether.app.ui.mymatches;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.foottogether.app.R;
import com.foottogether.app.data.model.Match;
import com.foottogether.app.data.remote.AuthRepository;
import com.foottogether.app.data.remote.MatchRepository;
import com.foottogether.app.data.remote.ResultCallback;
import com.foottogether.app.ui.common.MatchAdapter;
import com.foottogether.app.ui.detail.MatchDetailActivity;
import com.foottogether.app.util.ErrorMessages;
import com.foottogether.app.util.NetworkMonitor;
import com.foottogether.app.util.ReminderScheduler;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Onglet « Mes matchs » : deux listes en temps réel.
 * - « Je participe » : les matchs que j'ai rejoints (sans ceux que j'organise).
 *   On peut GLISSER une carte vers la gauche ou la droite pour quitter le match (ItemTouchHelper).
 * - « J'organise » : les matchs que j'ai créés.
 * L'onglet choisi est sauvegardé dans le Bundle (rotation).
 */
public class MyMatchesFragment extends Fragment {

    private static final String TAG = "MyMatchesFragment";
    private static final String KEY_TAB = "selected_tab";
    private static final int TAB_PARTICIPATING = 0;
    private static final int TAB_ORGANIZING = 1;

    private final MatchRepository matchRepository = new MatchRepository();
    private final AuthRepository authRepository = new AuthRepository();

    private String uid;
    private int selectedTab = TAB_PARTICIPATING;
    private ListenerRegistration listener;
    private MatchAdapter adapter;

    private View loading;
    private View emptyState;
    private TextView emptyTitle;
    private TextView emptyMessage;
    private View swipeHint;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_my_matches, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        uid = authRepository.getCurrentUid();
        if (savedInstanceState != null) {
            selectedTab = savedInstanceState.getInt(KEY_TAB, TAB_PARTICIPATING);
        }
        loading = view.findViewById(R.id.my_loading);
        emptyState = view.findViewById(R.id.my_empty);
        emptyTitle = view.findViewById(R.id.my_empty_title);
        emptyMessage = view.findViewById(R.id.my_empty_message);
        swipeHint = view.findViewById(R.id.my_swipe_hint);

        RecyclerView list = view.findViewById(R.id.my_list);
        list.setLayoutManager(new GridLayoutManager(requireContext(),
                getResources().getInteger(R.integer.match_columns)));
        adapter = new MatchAdapter(match ->
                startActivity(MatchDetailActivity.newIntent(requireContext(), match.getId())));
        list.setAdapter(adapter);
        new ItemTouchHelper(new SwipeToLeaveCallback()).attachToRecyclerView(list);

        TabLayout tabs = view.findViewById(R.id.my_tabs);
        tabs.addTab(tabs.newTab().setText(R.string.my_matches_participating), selectedTab == TAB_PARTICIPATING);
        tabs.addTab(tabs.newTab().setText(R.string.my_matches_organizing), selectedTab == TAB_ORGANIZING);
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                selectedTab = tab.getPosition();
                updateTabTexts();
                stopListening();
                startListening();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });
        updateTabTexts();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_TAB, selectedTab);
    }

    @Override
    public void onStart() {
        super.onStart();
        startListening();
    }

    @Override
    public void onStop() {
        stopListening();
        super.onStop();
    }

    private void updateTabTexts() {
        boolean participating = selectedTab == TAB_PARTICIPATING;
        swipeHint.setVisibility(participating ? View.VISIBLE : View.GONE);
        emptyTitle.setText(participating ? R.string.my_matches_empty_participating_title
                : R.string.my_matches_empty_organizing_title);
        emptyMessage.setText(participating ? R.string.my_matches_empty_participating_message
                : R.string.my_matches_empty_organizing_message);
    }

    private void startListening() {
        if (uid == null) return;
        loading.setVisibility(View.VISIBLE);
        emptyState.setVisibility(View.GONE);
        boolean organizing = selectedTab == TAB_ORGANIZING;
        listener = matchRepository.listenMyMatches(uid, organizing, new ResultCallback<List<Match>>() {
            @Override
            public void onSuccess(List<Match> matches) {
                if (getView() == null) return;
                List<Match> shown = organizing ? new ArrayList<>(matches) : withoutOrganizedByMe(matches);
                sortForDisplay(shown);
                loading.setVisibility(View.GONE);
                adapter.submitList(shown);
                emptyState.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onError(Exception e) {
                // Si Firestore réclame un index, le lien pour le créer est dans ce message (Logcat)
                Log.e(TAG, "Erreur d'écoute de mes matchs", e);
                if (getView() == null) return;
                loading.setVisibility(View.GONE);
                showMessage(R.string.error_matches_load);
            }
        });
    }

    private void stopListening() {
        if (listener != null) {
            listener.remove();
            listener = null;
        }
    }

    private List<Match> withoutOrganizedByMe(List<Match> matches) {
        List<Match> result = new ArrayList<>();
        for (Match match : matches) {
            if (!uid.equals(match.getOrganizerId())) result.add(match);
        }
        return result;
    }

    /**
     * Tri sur le téléphone : d'abord les matchs à venir (le plus proche en premier),
     * puis les matchs terminés ou annulés (le plus récent en premier).
     */
    private static void sortForDisplay(List<Match> matches) {
        matches.sort(Comparator
                .comparing((Match m) -> m.isFinished() || m.isCancelled())
                .thenComparing((a, b) -> {
                    long ta = a.getDateTime() == null ? 0 : a.getDateTime().toDate().getTime();
                    long tb = b.getDateTime() == null ? 0 : b.getDateTime().toDate().getTime();
                    boolean past = a.isFinished() || a.isCancelled();
                    return past ? Long.compare(tb, ta) : Long.compare(ta, tb);
                }));
    }

    // ===== Glisser pour quitter =====

    /** Un match peut être quitté en glissant seulement s'il est encore ouvert à ce moment-là. */
    private boolean canLeave(Match match) {
        return selectedTab == TAB_PARTICIPATING
                && !match.isCancelled() && !match.isStarted()
                && !uid.equals(match.getOrganizerId());
    }

    private class SwipeToLeaveCallback extends ItemTouchHelper.SimpleCallback {

        private final Paint backgroundPaint = new Paint();
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        SwipeToLeaveCallback() {
            super(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT);
            backgroundPaint.setColor(ContextCompat.getColor(requireContext(), R.color.error_bg));
            textPaint.setColor(ContextCompat.getColor(requireContext(), R.color.error_text));
            // 15 sp convertis en pixels (respecte la taille de texte choisie dans les réglages)
            textPaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 15,
                    getResources().getDisplayMetrics()));
            textPaint.setFakeBoldText(true);
        }

        @Override
        public int getSwipeDirs(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder holder) {
            int position = holder.getBindingAdapterPosition();
            if (position == RecyclerView.NO_POSITION) return 0;
            // 0 = cette carte ne peut pas être glissée
            return canLeave(adapter.getCurrentList().get(position)) ? super.getSwipeDirs(recyclerView, holder) : 0;
        }

        @Override
        public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder from,
                              @NonNull RecyclerView.ViewHolder to) {
            return false; // pas de réorganisation par glisser-déposer
        }

        @Override
        public void onSwiped(@NonNull RecyclerView.ViewHolder holder, int direction) {
            int position = holder.getBindingAdapterPosition();
            if (position == RecyclerView.NO_POSITION) return;
            confirmLeave(adapter.getCurrentList().get(position), position);
        }

        /** Dessine le fond rouge et le mot « Quitter » derrière la carte pendant le glissement. */
        @Override
        public void onChildDraw(@NonNull Canvas canvas, @NonNull RecyclerView recyclerView,
                                @NonNull RecyclerView.ViewHolder holder, float dX, float dY,
                                int actionState, boolean isCurrentlyActive) {
            View item = holder.itemView;
            if (dX != 0) {
                Rect area = dX > 0
                        ? new Rect(item.getLeft(), item.getTop(), item.getLeft() + (int) dX, item.getBottom())
                        : new Rect(item.getRight() + (int) dX, item.getTop(), item.getRight(), item.getBottom());
                canvas.drawRect(area, backgroundPaint);
                String label = getString(R.string.action_leave_short);
                float margin = 24 * getResources().getDisplayMetrics().density;
                float textY = item.getTop() + item.getHeight() / 2f + textPaint.getTextSize() / 3f;
                float textX = dX > 0 ? item.getLeft() + margin
                        : item.getRight() - margin - textPaint.measureText(label);
                canvas.drawText(label, textX, textY, textPaint);
            }
            super.onChildDraw(canvas, recyclerView, holder, dX, dY, actionState, isCurrentlyActive);
        }
    }

    private void confirmLeave(Match match, int position) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.leave_confirm_title)
                .setMessage(getString(R.string.leave_confirm_named, match.getTitle()))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.action_leave_short, (dialog, which) -> leave(match, position))
                // Fermée sans quitter (Annuler, Retour, toucher à côté) : on remet la carte en place
                .setOnDismissListener(dialog -> {
                    if (getView() != null) adapter.notifyItemChanged(position);
                })
                .show();
    }

    private void leave(Match match, int position) {
        if (!NetworkMonitor.isOnline(requireContext())) {
            showMessage(R.string.offline_banner);
            return;
        }
        matchRepository.leaveMatch(match.getId(), uid, new ResultCallback<Void>() {
            @Override
            public void onSuccess(Void unused) {
                // La carte disparaît toute seule : l'écoute temps réel reçoit la nouvelle liste
                if (getContext() != null) ReminderScheduler.cancel(getContext(), match.getId());
                showMessage(R.string.leave_success);
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Départ impossible", e);
                if (getView() != null) adapter.notifyItemChanged(position);
                showMessage(ErrorMessages.forFirestore(e, R.string.error_leave));
            }
        });
    }

    private void showMessage(@StringRes int message) {
        if (getView() == null) return;
        Snackbar snackbar = Snackbar.make(requireView(), message, Snackbar.LENGTH_SHORT);
        View bottomNav = requireActivity().findViewById(R.id.bottom_nav);
        if (bottomNav != null) snackbar.setAnchorView(bottomNav);
        snackbar.show();
    }
}
