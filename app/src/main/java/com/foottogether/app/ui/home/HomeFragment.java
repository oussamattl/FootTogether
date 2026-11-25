package com.foottogether.app.ui.home;

import android.content.Context;
import android.location.Location;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.BundleCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.foottogether.app.R;
import com.foottogether.app.data.local.MatchCacheDao;
import com.foottogether.app.data.model.Match;
import com.foottogether.app.data.model.User;
import com.foottogether.app.data.remote.AuthRepository;
import com.foottogether.app.data.remote.MatchRepository;
import com.foottogether.app.data.remote.ResultCallback;
import com.foottogether.app.data.remote.UserRepository;
import com.foottogether.app.ui.common.MatchAdapter;
import com.foottogether.app.ui.detail.MatchDetailActivity;
import com.foottogether.app.ui.main.MainActivity;
import com.foottogether.app.util.BackgroundTasks;
import com.foottogether.app.util.LocationHelper;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.List;

/**
 * Onglet « Accueil » : liste des matchs à venir, mise à jour EN TEMPS RÉEL.
 *
 * Cycle de vie : on s'abonne aux changements Firestore dans onStart (écran visible)
 * et on se désabonne dans onStop (écran caché), pour ne pas consommer de réseau
 * ni de batterie inutilement.
 */
public class HomeFragment extends Fragment {

    private static final String TAG = "HomeFragment";
    private static final int CACHE_LIMIT = 50;
    private static final String KEY_LIST_STATE = "list_state";

    private final MatchRepository matchRepository = new MatchRepository();
    private final UserRepository userRepository = new UserRepository();
    private final AuthRepository authRepository = new AuthRepository();

    private ListenerRegistration matchesListener;
    private MatchAdapter adapter;
    /** Vrai dès que Firestore a répondu : la liste SQLite ne doit plus remplacer la sienne. */
    private boolean serverLoaded;

    private TextView pseudoText;
    private SwipeRefreshLayout swipe;
    private View loading;
    private View emptyState;
    private RecyclerView list;
    /** Position de défilement à remettre après une rotation (lot 8, Bundle). */
    @Nullable
    private Parcelable pendingListState;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        pseudoText = view.findViewById(R.id.home_pseudo);
        swipe = view.findViewById(R.id.home_swipe);
        loading = view.findViewById(R.id.home_loading);
        emptyState = view.findViewById(R.id.home_empty);

        // Liste : 1 colonne sur téléphone, 2 en paysage ou sur tablette, 3 sur tablette en paysage
        // (res/values-land, values-sw600dp, values-sw600dp-land/integers.xml)
        list = view.findViewById(R.id.home_list);
        // Bundle : position de défilement enregistrée avant la rotation (rendue quand les matchs arrivent)
        if (savedInstanceState != null) {
            pendingListState = BundleCompat.getParcelable(savedInstanceState, KEY_LIST_STATE, Parcelable.class);
        }
        int columns = getResources().getInteger(R.integer.match_columns);
        list.setLayoutManager(new GridLayoutManager(requireContext(), columns));
        // Toucher une carte ouvre le détail du match (nouvelle activité, on lui passe l'id du match)
        adapter = new MatchAdapter(match ->
                startActivity(MatchDetailActivity.newIntent(requireContext(), match.getId())));
        list.setAdapter(adapter);

        // Tirer vers le bas : on relance l'abonnement Firestore
        swipe.setColorSchemeResources(R.color.green_primary);
        swipe.setOnRefreshListener(() -> {
            stopListening();
            startListening();
        });

        view.findViewById(R.id.home_empty_create).setOnClickListener(v ->
                mainActivity().selectTab(R.id.nav_create));

        loadPseudo();
    }

    @Override
    public void onStart() {
        super.onStart();
        startListening();
        showCachedMatches();
        showDistances();
    }

    /**
     * Lot 7 : affiche tout de suite les matchs enregistrés dans SQLite (dernière liste reçue),
     * sans attendre le serveur. Hors ligne, c'est cette liste qui reste affichée.
     * Dès que Firestore répond, sa liste (à jour) remplace celle-ci.
     */
    private void showCachedMatches() {
        if (serverLoaded) return;
        Context app = requireContext().getApplicationContext();
        BackgroundTasks.run(() -> new MatchCacheDao(app).getUpcoming(CACHE_LIMIT),
                new ResultCallback<List<Match>>() {
                    @Override
                    public void onSuccess(List<Match> cached) {
                        if (getView() == null || serverLoaded) return;
                        loading.setVisibility(View.GONE);
                        showMatches(cached);
                        emptyState.setVisibility(cached.isEmpty() ? View.VISIBLE : View.GONE);
                    }

                    @Override
                    public void onError(Exception e) {
                        Log.w(TAG, "Cache SQLite illisible", e);
                    }
                });
    }

    /**
     * Affiche une liste de matchs. Juste après une rotation, la liste est vide au début :
     * Android ne peut donc pas remettre la position de défilement tout seul.
     * On la remet nous-mêmes, une fois les cartes affichées (callback de submitList).
     */
    private void showMatches(List<Match> matches) {
        adapter.submitList(matches, () -> {
            if (pendingListState != null && list.getLayoutManager() != null && !matches.isEmpty()) {
                list.getLayoutManager().onRestoreInstanceState(pendingListState);
                pendingListState = null;
            }
        });
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (list != null && list.getLayoutManager() != null) {
            outState.putParcelable(KEY_LIST_STATE, list.getLayoutManager().onSaveInstanceState());
        }
    }

    /** Enregistre la liste reçue du serveur dans SQLite (en arrière-plan), puis fait le ménage. */
    private void saveToCache(List<Match> matches) {
        Context app = requireContext().getApplicationContext();
        BackgroundTasks.execute(() -> {
            MatchCacheDao dao = new MatchCacheDao(app);
            dao.saveAll(matches);
            dao.prune();
        });
    }

    /**
     * Si la localisation a déjà été autorisée (dans Rechercher ou Créer), on affiche la distance
     * sur les cartes. L'Accueil ne la demande pas lui-même, pour ne pas déranger à l'ouverture.
     */
    private void showDistances() {
        if (!LocationHelper.hasPermission(requireContext())) return;
        adapter.setUserLocation(LocationHelper.getLastLocation());
        LocationHelper.getCurrentLocation(requireContext(), new ResultCallback<Location>() {
            @Override
            public void onSuccess(Location location) {
                if (getView() != null) adapter.setUserLocation(location);
            }

            @Override
            public void onError(Exception e) {
                Log.w(TAG, "Position indisponible", e);
            }
        });
    }

    @Override
    public void onStop() {
        stopListening();
        super.onStop();
    }

    private void startListening() {
        // true : une réponse vide venant seulement du cache Firestore n'efface pas la liste SQLite
        matchesListener = matchRepository.listenUpcomingMatches(true, new ResultCallback<List<Match>>() {
            @Override
            public void onSuccess(List<Match> matches) {
                if (getView() == null) return;
                serverLoaded = true;
                saveToCache(matches);
                loading.setVisibility(View.GONE);
                swipe.setRefreshing(false);
                showMatches(matches);
                emptyState.setVisibility(matches.isEmpty() ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onError(Exception e) {
                // Si Firestore réclame un index, le lien pour le créer est dans ce message (Logcat)
                Log.e(TAG, "Erreur d'écoute des matchs", e);
                if (getView() == null) return;
                loading.setVisibility(View.GONE);
                swipe.setRefreshing(false);
                Snackbar.make(requireView(), R.string.error_matches_load, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    private void stopListening() {
        if (matchesListener != null) {
            matchesListener.remove();
            matchesListener = null;
        }
    }

    /** Affiche « Bonjour, pseudo ». */
    private void loadPseudo() {
        String uid = authRepository.getCurrentUid();
        if (uid == null) return;
        userRepository.getUser(uid, new ResultCallback<User>() {
            @Override
            public void onSuccess(User user) {
                if (getView() == null) return;
                pseudoText.setText(user.getPseudo());
            }

            @Override
            public void onError(Exception e) {
                Log.w(TAG, "Pseudo non chargé", e);
            }
        });
    }

    private MainActivity mainActivity() {
        return (MainActivity) requireActivity();
    }
}
