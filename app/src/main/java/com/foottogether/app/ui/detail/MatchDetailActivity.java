package com.foottogether.app.ui.detail;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.foottogether.app.R;
import com.foottogether.app.data.local.FavoritesDao;
import com.foottogether.app.data.local.MatchCacheDao;
import com.foottogether.app.data.local.RecentDao;
import com.foottogether.app.data.model.Match;
import com.foottogether.app.data.model.Participant;
import com.foottogether.app.data.remote.AuthRepository;
import com.foottogether.app.data.remote.MatchRepository;
import com.foottogether.app.data.remote.RatingRepository;
import com.foottogether.app.data.remote.ResultCallback;
import com.foottogether.app.ui.common.ChipStyle;
import com.foottogether.app.ui.create.EditMatchActivity;
import com.foottogether.app.util.BackgroundTasks;
import com.foottogether.app.util.DateFormatter;
import com.foottogether.app.util.DistanceUtils;
import com.foottogether.app.util.LocationHelper;
import com.foottogether.app.util.WeatherClient;
import com.foottogether.app.util.ErrorMessages;
import com.foottogether.app.util.MatchLabels;
import com.foottogether.app.util.NetworkMonitor;
import com.foottogether.app.util.ReminderScheduler;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.divider.MaterialDividerItemDecoration;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Écran Détail d'un match : infos, participants, bouton Rejoindre / Quitter.
 *
 * C'est le cœur de la démo à deux téléphones :
 * - deux abonnements temps réel (le match + ses participants) mettent l'écran à jour
 *   dès que quelqu'un rejoint ou quitte, sur TOUS les téléphones ;
 * - Rejoindre / Quitter passent par des transactions Firestore (MatchRepository).
 *
 * On reçoit seulement l'identifiant du match (dans l'Intent), puis on lit le match
 * en direct dans Firestore : l'écran montre toujours les données du serveur.
 */
public class MatchDetailActivity extends AppCompatActivity {

    private static final String TAG = "MatchDetail";
    private static final String EXTRA_MATCH_ID = "match_id";

    /** Fabrique l'Intent qui ouvre le détail d'un match. */
    public static Intent newIntent(Context context, String matchId) {
        return new Intent(context, MatchDetailActivity.class).putExtra(EXTRA_MATCH_ID, matchId);
    }

    /** Ce que le bouton du bas permet de faire, selon le match et l'utilisateur. */
    private enum Action { JOIN, LEAVE, CANCEL, FOLLOW, UNFOLLOW, RATE, NONE }

    /**
     * Ouvre « Modifier le match » et attend sa réponse : RESULT_OK = modifications enregistrées.
     * (Activity Result API : remplace l'ancien startActivityForResult.)
     */
    private final ActivityResultLauncher<Intent> editLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) showMessage(R.string.match_updated);
            });

    private final MatchRepository matchRepository = new MatchRepository();
    private final AuthRepository authRepository = new AuthRepository();

    private String matchId;
    private String currentUid;
    @Nullable
    private Match currentMatch;
    private Action action = Action.NONE;
    /** Vrai pendant une transaction : on bloque le bouton pour éviter un double clic. */
    private boolean actionInProgress;
    /** Réseau disponible ? Hors ligne, les boutons sont désactivés (les transactions échoueraient). */
    private boolean online = true;
    private NetworkMonitor networkMonitor;
    private View offlineBanner;
    /** Cœur en haut à droite : match en favori (SQLite, lot 7). */
    private MaterialButton favoriteButton;
    private boolean favorite;
    /** Le match a été ajouté aux « vus récemment » (une fois par ouverture). */
    private boolean recentSaved;

    private ListenerRegistration matchListener;
    private ListenerRegistration participantsListener;
    /** Lot 9 : joueurs que j'ai déjà évalués dans ce match (temps réel). */
    private ListenerRegistration ratingsListener;
    private final RatingRepository ratingRepository = new RatingRepository();
    private Set<String> ratedIds = new HashSet<>();
    private List<Participant> participants = new ArrayList<>();
    private ParticipantAdapter participantAdapter;

    private View root;
    private View loading;
    private View content;
    private TextView format;
    private TextView level;
    private TextView status;
    private TextView title;
    private TextView date;
    private TextView place;
    private TextView distance;
    private TextView weather;
    /** Position du joueur, si la localisation est autorisée. */
    @Nullable
    private Location userLocation;
    /** « lat,lng,date » de la dernière météo demandée : on ne la redemande que si ça change. */
    @Nullable
    private String weatherKey;
    private TextView description;
    private TextView count;
    private LinearProgressIndicator progress;
    private TextView placesNumber;
    private TextView placesLabel;
    private MaterialButton actionButton;
    /** Bouton « Modifier », visible seulement pour l'organisateur. */
    private MaterialButton editButton;
    /** Couleurs d'origine du bouton (style Material), remises quand il est désactivé. */
    private ColorStateList defaultButtonBackground;
    private ColorStateList defaultButtonText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        matchId = getIntent().getStringExtra(EXTRA_MATCH_ID);
        currentUid = authRepository.getCurrentUid();
        if (matchId == null || currentUid == null) {
            finish(); // rien à afficher (ou plus connecté)
            return;
        }

        WindowCompat.enableEdgeToEdge(getWindow());
        setContentView(R.layout.activity_match_detail);
        // L'en-tête est vert : icônes de la barre d'état en blanc
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(false);

        bindViews();
        applyInsets();

        findViewById(R.id.detail_back).setOnClickListener(v -> finish());
        favoriteButton = findViewById(R.id.detail_favorite);
        favoriteButton.setOnClickListener(v -> toggleFavorite());
        loadFavoriteState();
        actionButton.setOnClickListener(v -> onActionClicked());
        editButton.setOnClickListener(v -> openEdit());

        RecyclerView list = findViewById(R.id.detail_participants);
        list.setLayoutManager(new LinearLayoutManager(this));
        participantAdapter = new ParticipantAdapter(currentUid);
        list.setAdapter(participantAdapter);
        // Fine ligne de séparation entre les participants
        MaterialDividerItemDecoration divider =
                new MaterialDividerItemDecoration(this, LinearLayoutManager.VERTICAL);
        divider.setDividerColor(ContextCompat.getColor(this, R.color.outline));
        divider.setLastItemDecorated(false);
        list.addItemDecoration(divider);

        // La feuille « Évaluer les joueurs » renvoie le nombre d'évaluations envoyées (Fragment Result API)
        getSupportFragmentManager().setFragmentResultListener(RatePlayersSheet.RESULT_DONE, this,
                (key, result) -> {
                    if (result.getInt(RatePlayersSheet.RESULT_COUNT, 0) > 0) showMessage(R.string.rate_thanks);
                });
    }

    private void bindViews() {
        root = findViewById(R.id.detail_root);
        loading = findViewById(R.id.detail_loading);
        content = findViewById(R.id.detail_content);
        format = findViewById(R.id.detail_format);
        level = findViewById(R.id.detail_level);
        status = findViewById(R.id.detail_status);
        title = findViewById(R.id.detail_title);
        date = findViewById(R.id.detail_date);
        place = findViewById(R.id.detail_place);
        distance = findViewById(R.id.detail_distance);
        weather = findViewById(R.id.detail_weather);
        description = findViewById(R.id.detail_description);
        count = findViewById(R.id.detail_count);
        progress = findViewById(R.id.detail_progress);
        placesNumber = findViewById(R.id.detail_places_number);
        placesLabel = findViewById(R.id.detail_places_label);
        actionButton = findViewById(R.id.detail_action);
        editButton = findViewById(R.id.detail_edit);
        offlineBanner = findViewById(R.id.detail_offline_banner);
        networkMonitor = new NetworkMonitor(this);
        defaultButtonBackground = actionButton.getBackgroundTintList();
        defaultButtonText = actionButton.getTextColors();
    }

    /** Affichage bord à bord : on décale les boutons du haut et la barre du bas des barres système. */
    private void applyInsets() {
        View headerBar = findViewById(R.id.detail_header_bar);
        int headerPadding = headerBar.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(headerBar, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(headerPadding + bars.left, headerPadding + bars.top,
                    headerPadding + bars.right, headerPadding);
            return insets;
        });
        // Paysage (layout-land) : la colonne de droite touche le haut de l'écran,
        // on la décale sous la barre d'état. Cette vue n'existe pas en portrait (null).
        View rightColumn = findViewById(R.id.detail_right_column);
        if (rightColumn != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rightColumn, (v, insets) -> {
                v.setPadding(0, insets.getInsets(WindowInsetsCompat.Type.systemBars()).top, 0, 0);
                return insets;
            });
        }
        View bottomBar = findViewById(R.id.detail_bottom_bar);
        int bottomPadding = bottomBar.getPaddingBottom();
        int sidePadding = bottomBar.getPaddingLeft();
        ViewCompat.setOnApplyWindowInsetsListener(bottomBar, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(sidePadding + bars.left, v.getPaddingTop(),
                    sidePadding + bars.right, bottomPadding + bars.bottom);
            return insets;
        });
    }

    // ===== Temps réel : abonnements ouverts seulement quand l'écran est visible =====

    @Override
    protected void onStart() {
        super.onStart();
        networkMonitor.start(isOnline -> {
            online = isOnline;
            offlineBanner.setVisibility(isOnline ? View.GONE : View.VISIBLE);
            // Pas avant le premier chargement du match (sinon « Ce match n'existe plus » clignote)
            if (!actionInProgress && currentMatch != null) updateActionButton();
        });
        loadUserLocation();
        matchListener = matchRepository.listenMatch(matchId, new ResultCallback<Match>() {
            @Override
            public void onSuccess(Match match) {
                showMatch(match);
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Erreur d'écoute du match", e);
                loading.setVisibility(View.GONE);
                showMessage(R.string.error_match_load);
            }
        });
        participantsListener = matchRepository.listenParticipants(matchId,
                new ResultCallback<List<Participant>>() {
                    @Override
                    public void onSuccess(List<Participant> list) {
                        participants = list;
                        participantAdapter.submitList(list);
                        refreshActionIfLoaded();
                    }

                    @Override
                    public void onError(Exception e) {
                        Log.e(TAG, "Erreur d'écoute des participants", e);
                    }
                });
        ratingsListener = ratingRepository.listenRatedPlayers(matchId, currentUid,
                new ResultCallback<Set<String>>() {
                    @Override
                    public void onSuccess(Set<String> rated) {
                        ratedIds = rated;
                        refreshActionIfLoaded();
                    }

                    @Override
                    public void onError(Exception e) {
                        Log.w(TAG, "Évaluations non lues", e);
                    }
                });
    }

    /** Recalcule le bouton du bas, seulement une fois le match chargé et hors transaction. */
    private void refreshActionIfLoaded() {
        if (currentMatch != null && !actionInProgress) updateActionButton();
    }

    @Override
    protected void onStop() {
        networkMonitor.stop();
        if (matchListener != null) {
            matchListener.remove();
            matchListener = null;
        }
        if (participantsListener != null) {
            participantsListener.remove();
            participantsListener = null;
        }
        if (ratingsListener != null) {
            ratingsListener.remove();
            ratingsListener = null;
        }
        super.onStop();
    }

    // ===== Affichage =====

    /** Appelée à chaque changement du match sur le serveur (et une première fois à l'ouverture). */
    private void showMatch(@Nullable Match match) {
        loading.setVisibility(View.GONE);
        currentMatch = match;
        if (match == null) {
            content.setVisibility(View.GONE);
            showMessage(R.string.error_match_not_found);
            updateActionButton();
            return;
        }
        content.setVisibility(View.VISIBLE);

        format.setText(MatchLabels.formatLabel(match.getFormat()));
        ChipStyle.apply(level, MatchLabels.levelLabel(match.getLevel()), R.color.green_soft, R.color.on_green_soft);
        showStatusChip(match);
        title.setText(match.getTitle());
        date.setText(match.getDateTime() == null
                ? "" : DateFormatter.formatMatchDate(this, match.getDateTime().toDate()));
        place.setText(getString(R.string.match_place_format, match.getAddress(), match.getCity()));

        String info = match.getDescription();
        boolean hasInfo = info != null && !info.trim().isEmpty();
        description.setVisibility(hasInfo ? View.VISIBLE : View.GONE);
        description.setText(hasInfo ? info.trim() : "");

        // Compteur et barre des places : c'est ce qui bouge pendant la démo (1/10 → 2/10)
        int max = (int) match.getMaxPlayers();
        int current = (int) match.getParticipantCount();
        count.setText(getString(R.string.detail_count, current, max));
        progress.setMax(Math.max(1, max));
        progress.setProgressCompat(current, true);
        progress.setIndicatorColor(ContextCompat.getColor(this,
                match.isFull() ? R.color.progress_full : R.color.green_primary));

        int left = match.getPlacesLeft();
        placesNumber.setText(String.valueOf(left));
        placesNumber.setTextColor(ContextCompat.getColor(this,
                left == 0 ? R.color.warning_text : R.color.on_green_soft));
        placesLabel.setText(getResources().getQuantityString(R.plurals.places_left_label, left));

        participantAdapter.setOrganizerId(match.getOrganizerId());
        saveLocally(match);
        showDistance();
        loadWeatherIfNeeded(match);
        updateActionButton();
    }

    // ===== SQLite (lot 7) : cache, vus récemment, favoris =====

    /**
     * Copie le match dans le cache SQLite (à chaque changement) et l'ajoute aux « vus récemment »
     * (une fois). Écriture en arrière-plan : le disque ne doit pas ralentir l'écran.
     */
    private void saveLocally(Match match) {
        boolean addRecent = !recentSaved;
        recentSaved = true;
        BackgroundTasks.execute(() -> {
            new MatchCacheDao(this).save(match);
            if (addRecent) new RecentDao(this).add(currentUid, matchId);
        });
    }

    private void loadFavoriteState() {
        BackgroundTasks.run(() -> new FavoritesDao(this).isFavorite(currentUid, matchId),
                new ResultCallback<Boolean>() {
                    @Override
                    public void onSuccess(Boolean isFavorite) {
                        favorite = isFavorite;
                        updateFavoriteIcon();
                    }

                    @Override
                    public void onError(Exception e) {
                        Log.w(TAG, "Favori non lu", e);
                    }
                });
    }

    /** Ajoute / retire des favoris. Purement local : pas besoin de réseau. */
    private void toggleFavorite() {
        favorite = !favorite;
        updateFavoriteIcon();
        boolean add = favorite;
        BackgroundTasks.execute(() -> {
            FavoritesDao dao = new FavoritesDao(this);
            if (add) {
                // Le match doit être dans le cache pour s'afficher dans l'écran Favoris
                if (currentMatch != null) new MatchCacheDao(this).save(currentMatch);
                dao.add(currentUid, matchId);
            } else {
                dao.remove(currentUid, matchId);
            }
        });
        showMessage(add ? R.string.favorite_added : R.string.favorite_removed);
    }

    private void updateFavoriteIcon() {
        favoriteButton.setIconResource(favorite ? R.drawable.ic_favorite : R.drawable.ic_favorite_border);
        favoriteButton.setContentDescription(getString(
                favorite ? R.string.action_unfavorite : R.string.action_favorite));
    }

    // ===== Distance et météo (lot 5) =====

    /** Position du joueur (seulement si déjà autorisée : le Détail ne la demande pas). */
    private void loadUserLocation() {
        if (!LocationHelper.hasPermission(this)) return;
        userLocation = LocationHelper.getLastLocation();
        LocationHelper.getCurrentLocation(this, new ResultCallback<Location>() {
            @Override
            public void onSuccess(Location location) {
                userLocation = location;
                showDistance();
            }

            @Override
            public void onError(Exception e) {
                Log.w(TAG, "Position indisponible", e);
            }
        });
    }

    private void showDistance() {
        Float meters = currentMatch == null ? null : DistanceUtils.metersTo(userLocation, currentMatch);
        distance.setVisibility(meters == null ? View.GONE : View.VISIBLE);
        if (meters != null) distance.setText(DistanceUtils.format(this, meters));
    }

    /**
     * Météo prévue au lieu et à l'heure du match (Open-Meteo, appel HTTP en arrière-plan).
     * Pas de météo si le lieu n'a pas de coordonnées ou si le match est passé / annulé.
     */
    private void loadWeatherIfNeeded(Match match) {
        if (match.getLat() == null || match.getLng() == null || match.getDateTime() == null
                || match.isFinished() || match.isCancelled()) {
            weather.setVisibility(View.GONE);
            weatherKey = null;
            return;
        }
        String key = match.getLat() + "," + match.getLng() + "," + match.getDateTime().getSeconds();
        if (key.equals(weatherKey)) return; // déjà demandée pour ce lieu et cette heure
        weatherKey = key;
        weather.setVisibility(View.VISIBLE);
        weather.setText(R.string.weather_loading);
        WeatherClient.fetch(match.getLat(), match.getLng(), match.getDateTime().toDate(),
                new ResultCallback<WeatherClient.Forecast>() {
                    @Override
                    public void onSuccess(WeatherClient.Forecast forecast) {
                        if (isFinishing() || isDestroyed() || !key.equals(weatherKey)) return;
                        if (forecast == null) {
                            weather.setText(R.string.weather_too_early);
                        } else {
                            weather.setText(getString(R.string.weather_forecast,
                                    Math.round(forecast.temperature),
                                    getString(WeatherClient.describe(forecast.code))));
                        }
                    }

                    @Override
                    public void onError(Exception e) {
                        Log.w(TAG, "Météo indisponible", e);
                        if (isFinishing() || isDestroyed() || !key.equals(weatherKey)) return;
                        weather.setText(R.string.weather_unavailable);
                    }
                });
    }

    private void showStatusChip(Match match) {
        if (match.isCancelled()) {
            ChipStyle.apply(status, R.string.status_cancelled, R.color.neutral_chip_bg, R.color.text_muted);
        } else if (match.isFinished()) {
            ChipStyle.apply(status, R.string.status_finished, R.color.neutral_chip_bg, R.color.text_muted);
        } else if (match.isStarted()) {
            ChipStyle.apply(status, R.string.status_in_progress, R.color.warning_bg, R.color.warning_text);
        } else if (match.isFull()) {
            ChipStyle.apply(status, R.string.status_full, R.color.warning_bg, R.color.warning_text);
        } else {
            ChipStyle.apply(status, R.string.status_open, R.color.green_soft, R.color.on_green_soft);
        }
    }

    /**
     * Choisit le texte et l'état du bouton du bas. Ordre des cas :
     * annulé, commencé avec des joueurs à évaluer (« Évaluer les joueurs »), terminé, commencé,
     * organisateur, déjà inscrit, complet (« Me prévenir »), sinon « Rejoindre ».
     * L'organisateur d'un match à venir voit deux boutons : « Modifier » et « Annuler le match ».
     */
    private void updateActionButton() {
        Match match = currentMatch;
        boolean joined = match != null && match.getParticipantIds() != null
                && match.getParticipantIds().contains(currentUid);
        boolean organizer = match != null && currentUid.equals(match.getOrganizerId());
        boolean editable = organizer && !match.isCancelled() && !match.isStarted();
        editButton.setVisibility(editable ? View.VISIBLE : View.GONE);
        editButton.setEnabled(!actionInProgress && online);

        if (match == null) {
            setAction(Action.NONE, R.string.error_match_not_found);
        } else if (match.isCancelled()) {
            setAction(Action.NONE, R.string.detail_cancelled);
        } else if (match.isStarted() && joined && !playersToRate().isEmpty()) {
            // Lot 9 : dès le coup d'envoi, un participant peut évaluer les autres joueurs
            setAction(Action.RATE, R.string.action_rate_players);
        } else if (match.isFinished()) {
            setAction(Action.NONE, R.string.detail_finished);
        } else if (match.isStarted()) {
            setAction(Action.NONE, R.string.detail_started);
        } else if (organizer) {
            // L'organisateur ne quitte pas son match : il l'annule
            setAction(Action.CANCEL, R.string.action_cancel_match);
        } else if (joined) {
            setAction(Action.LEAVE, R.string.action_leave);
        } else if (match.isFull()) {
            // Complet : on peut demander à être prévenu si une place se libère (followerIds)
            boolean following = match.getFollowerIds() != null && match.getFollowerIds().contains(currentUid);
            if (following) {
                setAction(Action.UNFOLLOW, R.string.action_unfollow);
            } else {
                setAction(Action.FOLLOW, R.string.action_follow);
            }
        } else {
            setAction(Action.JOIN, R.string.action_join);
        }
    }

    private void setAction(Action newAction, @StringRes int text) {
        action = newAction;
        actionButton.setText(text);
        actionButton.setEnabled(newAction != Action.NONE && !actionInProgress && online);
        // Quitter / Annuler : bouton rouge clair, pour bien le distinguer de « Rejoindre ».
        // Ne plus être prévenu : vert clair (action secondaire).
        boolean leave = newAction == Action.LEAVE || newAction == Action.CANCEL;
        boolean soft = newAction == Action.UNFOLLOW;
        @ColorRes int background = leave ? R.color.error_bg : soft ? R.color.green_soft : R.color.green_primary;
        @ColorRes int textColor = leave ? R.color.error_text : soft ? R.color.on_green_soft : R.color.white;
        if (actionButton.isEnabled()) {
            actionButton.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, background)));
            actionButton.setTextColor(ContextCompat.getColor(this, textColor));
        } else {
            // Couleurs du style Material, qui gère l'aspect grisé « désactivé »
            actionButton.setBackgroundTintList(defaultButtonBackground);
            actionButton.setTextColor(defaultButtonText);
        }
    }

    // ===== Rejoindre / Quitter =====

    private void onActionClicked() {
        if (currentMatch == null || actionInProgress) return;
        // Les transactions ont besoin du serveur : on prévient tout de suite si pas de réseau
        if (!NetworkMonitor.isOnline(this)) {
            showMessage(R.string.offline_banner);
            return;
        }
        if (action == Action.JOIN) {
            join();
        } else if (action == Action.LEAVE) {
            confirmLeave();
        } else if (action == Action.CANCEL) {
            confirmCancel();
        } else if (action == Action.FOLLOW || action == Action.UNFOLLOW) {
            setFollowing(action == Action.FOLLOW);
        } else if (action == Action.RATE) {
            openRating();
        }
    }

    // ===== Évaluations après le match (lot 9) =====

    /** Les autres participants que je n'ai pas encore évalués. */
    private List<Participant> playersToRate() {
        List<Participant> result = new ArrayList<>();
        for (Participant p : participants) {
            if (p.getUid() != null && !p.getUid().equals(currentUid) && !ratedIds.contains(p.getUid())) {
                result.add(p);
            }
        }
        return result;
    }

    private void openRating() {
        if (getSupportFragmentManager().findFragmentByTag(RatePlayersSheet.TAG) != null) return;
        ArrayList<String> uids = new ArrayList<>();
        ArrayList<String> pseudos = new ArrayList<>();
        for (Participant p : playersToRate()) {
            uids.add(p.getUid());
            pseudos.add(p.getPseudo());
        }
        RatePlayersSheet.newInstance(matchId, currentUid, uids, pseudos)
                .show(getSupportFragmentManager(), RatePlayersSheet.TAG);
    }

    // ===== Match complet : être prévenu si une place se libère (lot 6) =====

    private void setFollowing(boolean follow) {
        setInProgress(true);
        matchRepository.setFollowing(matchId, currentUid, follow, new ResultCallback<Void>() {
            @Override
            public void onSuccess(Void unused) {
                setInProgress(false);
                showMessage(follow ? R.string.follow_on : R.string.follow_off);
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Alerte non modifiée", e);
                setInProgress(false);
                showMessage(ErrorMessages.forFirestore(e, R.string.error_follow));
            }
        });
    }

    // ===== Organisateur : modifier / annuler =====

    private void openEdit() {
        if (currentMatch == null || actionInProgress) return;
        editLauncher.launch(EditMatchActivity.newIntent(this, matchId));
    }

    private void confirmCancel() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.cancel_match_confirm_title)
                .setMessage(R.string.cancel_match_confirm_message)
                .setNegativeButton(R.string.keep_match, null)
                .setPositiveButton(R.string.action_cancel_match, (dialog, which) -> cancelMatch())
                .show();
    }

    private void cancelMatch() {
        setInProgress(true);
        matchRepository.cancelMatch(matchId, currentUid, new ResultCallback<Void>() {
            @Override
            public void onSuccess(Void unused) {
                // L'écoute temps réel affiche aussitôt « Annulé » chez tous les participants
                ReminderScheduler.cancel(MatchDetailActivity.this, matchId);
                setInProgress(false);
                showMessage(R.string.match_cancelled);
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Annulation impossible", e);
                setInProgress(false);
                showMessage(ErrorMessages.forFirestore(e, R.string.error_cancel_match));
            }
        });
    }

    private void join() {
        setInProgress(true);
        matchRepository.joinMatch(matchId, currentUid, new ResultCallback<Void>() {
            @Override
            public void onSuccess(Void unused) {
                // Affiché SEULEMENT après l'accord du serveur
                if (currentMatch != null) ReminderScheduler.schedule(MatchDetailActivity.this, currentMatch);
                setInProgress(false);
                showMessage(R.string.join_success);
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Inscription impossible", e);
                setInProgress(false);
                showMessage(ErrorMessages.forFirestore(e, R.string.error_join));
            }
        });
    }

    private void confirmLeave() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.leave_confirm_title)
                .setMessage(R.string.leave_confirm_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.action_leave_short, (dialog, which) -> leave())
                .show();
    }

    private void leave() {
        setInProgress(true);
        matchRepository.leaveMatch(matchId, currentUid, new ResultCallback<Void>() {
            @Override
            public void onSuccess(Void unused) {
                ReminderScheduler.cancel(MatchDetailActivity.this, matchId);
                setInProgress(false);
                showMessage(R.string.leave_success);
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Départ impossible", e);
                setInProgress(false);
                showMessage(ErrorMessages.forFirestore(e, R.string.error_leave));
            }
        });
    }

    /** Pendant la transaction : bouton bloqué avec « Patiente… ». */
    private void setInProgress(boolean inProgress) {
        if (isFinishing() || isDestroyed()) return;
        actionInProgress = inProgress;
        if (inProgress) {
            actionButton.setEnabled(false);
            editButton.setEnabled(false);
            actionButton.setText(R.string.action_in_progress);
        } else {
            updateActionButton();
        }
    }

    private void showMessage(@StringRes int message) {
        if (isFinishing() || isDestroyed()) return;
        Snackbar.make(root, message, Snackbar.LENGTH_LONG)
                .setAnchorView(R.id.detail_bottom_bar)
                .show();
    }

}
