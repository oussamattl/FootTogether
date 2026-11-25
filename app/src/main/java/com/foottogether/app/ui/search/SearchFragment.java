package com.foottogether.app.ui.search;

import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.foottogether.app.R;
import com.foottogether.app.data.prefs.AppPrefs;
import com.foottogether.app.data.model.Match;
import com.foottogether.app.data.model.User;
import com.foottogether.app.data.remote.AuthRepository;
import com.foottogether.app.data.remote.MatchRepository;
import com.foottogether.app.data.remote.ResultCallback;
import com.foottogether.app.data.remote.UserRepository;
import com.foottogether.app.ui.common.MatchAdapter;
import com.foottogether.app.ui.detail.MatchDetailActivity;
import com.foottogether.app.util.DistanceUtils;
import com.foottogether.app.util.LocationHelper;
import com.foottogether.app.util.MapConfig;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.ListenerRegistration;

import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Onglet « Rechercher » : tous les matchs à venir, filtrés SUR LE TÉLÉPHONE par
 * ville, distance, date, format, niveau et places disponibles, affichés en liste ou sur une carte.
 *
 * Pourquoi filtrer sur le téléphone ? Firestore ne sait pas faire de requête « à moins de 10 km »
 * ni combiner librement plusieurs filtres sans index. On écoute donc les 50 prochains matchs
 * (temps réel), puis on filtre en Java : simple et suffisant à l'échelle d'une ville.
 *
 * Bundle : le texte et les chips sont restaurés par Android ; nous sauvegardons en plus
 * le mode (liste / carte), l'ouverture du panneau de filtres, le centre et le zoom de la carte.
 */
public class SearchFragment extends Fragment {

    private static final String TAG = "SearchFragment";

    private static final String KEY_MAP_MODE = "map_mode";
    private static final String KEY_FILTERS_OPEN = "filters_open";
    private static final String KEY_LOCATION_ASKED = "location_asked";
    private static final String KEY_MAP_LAT = "map_lat";
    private static final String KEY_MAP_LNG = "map_lng";
    private static final String KEY_MAP_ZOOM = "map_zoom";

    /** Centre par défaut de la carte : Valenciennes. */
    private static final GeoPoint DEFAULT_CENTER = new GeoPoint(50.3570, 3.5235);
    private static final double DEFAULT_ZOOM = 12.0;
    private static final long DAY_MILLIS = 24L * 60 * 60 * 1000;

    private final MatchRepository matchRepository = new MatchRepository();
    private final UserRepository userRepository = new UserRepository();
    private final AuthRepository authRepository = new AuthRepository();

    private ListenerRegistration matchesListener;
    private final List<Match> allMatches = new ArrayList<>();
    @Nullable
    private Location userLocation;

    private boolean mapMode;
    private boolean filtersOpen;
    private boolean locationAsked;
    /** Centre / zoom restaurés après une rotation (null = centrage automatique). */
    @Nullable
    private GeoPoint savedCenter;
    private double savedZoom = DEFAULT_ZOOM;
    private boolean mapCentered;

    private MatchAdapter adapter;
    private TextInputEditText cityInput;
    private MaterialButtonToggleGroup modeGroup;
    private MaterialButton filtersToggle;
    private View filtersPanel;
    private TextView resultCount;
    private ChipGroup distanceGroup;
    private ChipGroup dateGroup;
    private ChipGroup formatGroup;
    private ChipGroup levelGroup;
    private Chip placesChip;
    private View locationInfo;
    private RecyclerView list;
    private MapView map;
    private View emptyState;

    /** Fenêtre système « Autoriser FootTogether à accéder à la position ? ». */
    private final ActivityResultLauncher<String[]> locationPermission = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (getView() == null) return;
                updateLocationAvailability();
                if (LocationHelper.hasPermission(requireContext())) {
                    loadUserLocation();
                } else {
                    prefillProfileCity(); // repli : chercher par la ville du profil
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // osmdroid doit être configuré AVANT de créer la MapView du layout
        MapConfig.init(requireContext());
        return inflater.inflate(R.layout.fragment_search, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (savedInstanceState != null) {
            mapMode = savedInstanceState.getBoolean(KEY_MAP_MODE);
            filtersOpen = savedInstanceState.getBoolean(KEY_FILTERS_OPEN);
            locationAsked = savedInstanceState.getBoolean(KEY_LOCATION_ASKED);
            if (savedInstanceState.containsKey(KEY_MAP_LAT)) {
                savedCenter = new GeoPoint(savedInstanceState.getDouble(KEY_MAP_LAT),
                        savedInstanceState.getDouble(KEY_MAP_LNG));
                savedZoom = savedInstanceState.getDouble(KEY_MAP_ZOOM, DEFAULT_ZOOM);
            }
        }
        bindViews(view);
        setUpList();
        setUpMap();

        filtersToggle.setOnClickListener(v -> {
            filtersOpen = !filtersOpen;
            updateFiltersPanel();
        });
        view.findViewById(R.id.search_location_allow).setOnClickListener(v ->
                locationPermission.launch(LocationHelper.PERMISSIONS));

        // Localisation : déjà accordée → on l'utilise ; sinon on la demande une seule fois
        updateLocationAvailability();
        if (LocationHelper.hasPermission(requireContext())) {
            loadUserLocation();
        } else if (!locationAsked) {
            locationAsked = true;
            locationPermission.launch(LocationHelper.PERMISSIONS);
        } else {
            prefillProfileCity();
        }
    }

    /**
     * Après la restauration automatique (texte, chips) : on branche les écouteurs
     * et on applique l'état sauvegardé. Brancher avant déclencherait des filtrages inutiles.
     */
    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        modeGroup.check(mapMode ? R.id.search_mode_map : R.id.search_mode_list);
        modeGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            mapMode = checkedId == R.id.search_mode_map;
            updateMode();
        });
        ChipGroup.OnCheckedStateChangeListener refilter = (group, ids) -> applyFilters();
        distanceGroup.setOnCheckedStateChangeListener(refilter);
        dateGroup.setOnCheckedStateChangeListener(refilter);
        formatGroup.setOnCheckedStateChangeListener(refilter);
        levelGroup.setOnCheckedStateChangeListener(refilter);
        placesChip.setOnCheckedChangeListener((chip, checked) -> applyFilters());
        cityInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                applyFilters();
            }
        });
        updateMode();
        updateFiltersPanel();
        applyFilters();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_MAP_MODE, mapMode);
        outState.putBoolean(KEY_FILTERS_OPEN, filtersOpen);
        outState.putBoolean(KEY_LOCATION_ASKED, locationAsked);
        if (map != null && mapCentered) {
            outState.putDouble(KEY_MAP_LAT, map.getMapCenter().getLatitude());
            outState.putDouble(KEY_MAP_LNG, map.getMapCenter().getLongitude());
            outState.putDouble(KEY_MAP_ZOOM, map.getZoomLevelDouble());
        }
    }

    // ===== Cycle de vie : abonnement Firestore et carte =====

    @Override
    public void onStart() {
        super.onStart();
        matchesListener = matchRepository.listenUpcomingMatches(new ResultCallback<List<Match>>() {
            @Override
            public void onSuccess(List<Match> matches) {
                allMatches.clear();
                allMatches.addAll(matches);
                if (getView() != null) applyFilters();
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Erreur d'écoute des matchs", e);
                if (getView() != null) {
                    Snackbar.make(requireView(), R.string.error_matches_load, Snackbar.LENGTH_LONG).show();
                }
            }
        });
    }

    @Override
    public void onStop() {
        if (matchesListener != null) {
            matchesListener.remove();
            matchesListener = null;
        }
        super.onStop();
    }

    @Override
    public void onResume() {
        super.onResume();
        map.onResume(); // reprend le chargement des tuiles
    }

    @Override
    public void onPause() {
        map.onPause();
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        map.onDetach(); // libère les ressources de la carte
        super.onDestroyView();
    }

    // ===== Mise en place =====

    private void bindViews(View view) {
        cityInput = view.findViewById(R.id.search_city);
        modeGroup = view.findViewById(R.id.search_mode);
        filtersToggle = view.findViewById(R.id.search_filters_toggle);
        filtersPanel = view.findViewById(R.id.search_filters);
        resultCount = view.findViewById(R.id.search_result_count);
        distanceGroup = view.findViewById(R.id.search_distance_group);
        dateGroup = view.findViewById(R.id.search_date_group);
        formatGroup = view.findViewById(R.id.search_format_group);
        levelGroup = view.findViewById(R.id.search_level_group);
        placesChip = view.findViewById(R.id.search_places);
        locationInfo = view.findViewById(R.id.search_location_info);
        list = view.findViewById(R.id.search_list);
        map = view.findViewById(R.id.search_map);
        emptyState = view.findViewById(R.id.search_empty);
    }

    private void setUpList() {
        list.setLayoutManager(new GridLayoutManager(requireContext(),
                getResources().getInteger(R.integer.match_columns)));
        adapter = new MatchAdapter(this::openMatch);
        list.setAdapter(adapter);
    }

    private void setUpMap() {
        map.setTileSource(TileSourceFactory.MAPNIK);       // tuiles OpenStreetMap standard
        map.setMultiTouchControls(true);                    // pincer pour zoomer
        map.setTilesScaledToDpi(true);                      // texte lisible sur les écrans denses
        map.setMinZoomLevel(4.0);
        map.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT);
        map.getController().setZoom(savedZoom);
        map.getController().setCenter(savedCenter != null ? savedCenter : DEFAULT_CENTER);
        mapCentered = savedCenter != null;
    }

    private void openMatch(Match match) {
        startActivity(MatchDetailActivity.newIntent(requireContext(), match.getId()));
    }

    // ===== Localisation =====

    private void loadUserLocation() {
        LocationHelper.getCurrentLocation(requireContext(), new ResultCallback<Location>() {
            @Override
            public void onSuccess(Location location) {
                if (getView() == null) return;
                userLocation = location;
                adapter.setUserLocation(location);
                applyFilters();
            }

            @Override
            public void onError(Exception e) {
                Log.w(TAG, "Position indisponible", e);
                if (getView() == null) return;
                Snackbar.make(requireView(), R.string.location_unavailable, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    /** Sans localisation : filtres de distance grisés et bandeau d'explication. */
    private void updateLocationAvailability() {
        boolean allowed = LocationHelper.hasPermission(requireContext());
        locationInfo.setVisibility(allowed ? View.GONE : View.VISIBLE);
        for (int i = 0; i < distanceGroup.getChildCount(); i++) {
            View chip = distanceGroup.getChildAt(i);
            chip.setEnabled(allowed || chip.getId() == R.id.search_distance_all);
        }
        if (!allowed) distanceGroup.check(R.id.search_distance_all);
    }

    /**
     * Repli si la localisation est refusée : on cherche dans la ville par défaut des Paramètres
     * (SharedPreferences), sinon dans la ville du profil.
     */
    private void prefillProfileCity() {
        String uid = authRepository.getCurrentUid();
        if (uid == null || textOf(cityInput).length() > 0) return;
        String defaultCity = new AppPrefs(requireContext()).getDefaultCity();
        if (!defaultCity.isEmpty()) {
            cityInput.setText(defaultCity);
            return;
        }
        userRepository.getUser(uid, new ResultCallback<User>() {
            @Override
            public void onSuccess(User user) {
                if (getView() == null || textOf(cityInput).length() > 0) return;
                if (user.getCity() != null && !user.getCity().isEmpty()) cityInput.setText(user.getCity());
            }

            @Override
            public void onError(Exception e) {
                Log.w(TAG, "Profil non chargé", e);
            }
        });
    }

    // ===== Filtres =====

    private void applyFilters() {
        if (getView() == null) return;
        String city = normalize(textOf(cityInput));
        float maxMeters = selectedMaxDistance();
        List<Match> result = new ArrayList<>();
        for (Match match : allMatches) {
            if (keep(match, city, maxMeters)) result.add(match);
        }
        // Filtre de distance actif : les plus proches d'abord ; sinon par date (déjà triés)
        if (maxMeters > 0 && userLocation != null) {
            Collections.sort(result, Comparator.comparing((Match m) -> DistanceUtils.metersTo(userLocation, m)));
        }
        adapter.submitList(result);
        resultCount.setText(getResources().getQuantityString(R.plurals.search_result_count,
                result.size(), result.size()));
        emptyState.setVisibility(result.isEmpty() && !mapMode ? View.VISIBLE : View.GONE);
        updateFilterButton();
        updateMarkers(result);
    }

    private boolean keep(Match match, String city, float maxMeters) {
        // On ne propose que les matchs encore jouables
        if (match.isCancelled() || match.isFinished()) return false;
        if (!city.isEmpty() && !normalize(match.getCity()).contains(city)
                && !normalize(match.getAddress()).contains(city)) {
            return false;
        }
        if (maxMeters > 0) {
            Float meters = DistanceUtils.metersTo(userLocation, match);
            if (meters == null || meters > maxMeters) return false;
        }
        if (!matchesDate(match)) return false;
        String format = selectedFormat();
        if (format != null && !format.equals(match.getFormat())) return false;
        String level = selectedLevel();
        if (level != null && !level.equals(match.getLevel())) return false;
        return !placesChip.isChecked() || !match.isFull();
    }

    private boolean matchesDate(Match match) {
        int id = dateGroup.getCheckedChipId();
        if (id == R.id.search_date_all || match.getDateTime() == null) return true;
        long time = match.getDateTime().toDate().getTime();
        long now = System.currentTimeMillis();
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);
        long startOfToday = today.getTimeInMillis();

        if (id == R.id.search_date_today) {
            return time < startOfToday + DAY_MILLIS;
        }
        if (id == R.id.search_date_weekend) {
            // Du samedi 0 h au lundi 0 h de ce week-end (le dimanche : le week-end en cours)
            int dayOfWeek = today.get(Calendar.DAY_OF_WEEK);
            int daysToSaturday = dayOfWeek == Calendar.SUNDAY ? -1
                    : (Calendar.SATURDAY - dayOfWeek + 7) % 7;
            Calendar saturday = (Calendar) today.clone();
            saturday.add(Calendar.DAY_OF_MONTH, daysToSaturday);
            Calendar monday = (Calendar) saturday.clone();
            monday.add(Calendar.DAY_OF_MONTH, 2);
            return time >= saturday.getTimeInMillis() && time < monday.getTimeInMillis();
        }
        return time < now + 7 * DAY_MILLIS; // 7 prochains jours
    }

    /** Distance maximale choisie, en mètres (0 = pas de limite). */
    private float selectedMaxDistance() {
        int id = distanceGroup.getCheckedChipId();
        if (id == R.id.search_distance_5) return 5_000;
        if (id == R.id.search_distance_10) return 10_000;
        if (id == R.id.search_distance_25) return 25_000;
        return 0;
    }

    @Nullable
    private String selectedFormat() {
        int id = formatGroup.getCheckedChipId();
        if (id == R.id.search_format_five) return Match.FORMAT_FIVE;
        if (id == R.id.search_format_seven) return Match.FORMAT_SEVEN;
        if (id == R.id.search_format_eleven) return Match.FORMAT_ELEVEN;
        return null;
    }

    @Nullable
    private String selectedLevel() {
        int id = levelGroup.getCheckedChipId();
        if (id == R.id.search_level_beginner) return User.LEVEL_BEGINNER;
        if (id == R.id.search_level_intermediate) return User.LEVEL_INTERMEDIATE;
        if (id == R.id.search_level_advanced) return User.LEVEL_ADVANCED;
        return null;
    }

    /** « Filtres (2) » : nombre de filtres actifs, visible même panneau fermé. */
    private void updateFilterButton() {
        int active = 0;
        if (distanceGroup.getCheckedChipId() != R.id.search_distance_all) active++;
        if (dateGroup.getCheckedChipId() != R.id.search_date_all) active++;
        if (formatGroup.getCheckedChipId() != R.id.search_format_all) active++;
        if (levelGroup.getCheckedChipId() != R.id.search_level_all) active++;
        if (placesChip.isChecked()) active++;
        filtersToggle.setText(active == 0 ? getString(R.string.filters)
                : getString(R.string.filters_count, active));
    }

    private void updateFiltersPanel() {
        filtersPanel.setVisibility(filtersOpen ? View.VISIBLE : View.GONE);
    }

    /** Ignore majuscules et accents : « valenciennes » trouve « Valenciennes », « eté » trouve « Été ». */
    private static String normalize(@Nullable String text) {
        if (text == null) return "";
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    // ===== Liste / carte =====

    private void updateMode() {
        list.setVisibility(mapMode ? View.GONE : View.VISIBLE);
        map.setVisibility(mapMode ? View.VISIBLE : View.GONE);
        emptyState.setVisibility(!mapMode && adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
        if (mapMode) centerMapIfNeeded();
    }

    /** Une épingle par match (s'il a des coordonnées), plus un point « Toi ». Toucher une épingle ouvre le match. */
    private void updateMarkers(List<Match> matches) {
        map.getOverlays().clear();
        Drawable pin = ContextCompat.getDrawable(requireContext(), R.drawable.ic_map_pin);
        for (Match match : matches) {
            if (match.getLat() == null || match.getLng() == null) continue;
            Marker marker = new Marker(map);
            marker.setPosition(new GeoPoint(match.getLat(), match.getLng()));
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            marker.setIcon(pin);
            marker.setTitle(match.getTitle());
            marker.setOnMarkerClickListener((clicked, mapView) -> {
                openMatch(match);
                return true;
            });
            map.getOverlays().add(marker);
        }
        if (userLocation != null) {
            Marker me = new Marker(map);
            me.setPosition(new GeoPoint(userLocation.getLatitude(), userLocation.getLongitude()));
            me.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
            me.setIcon(ContextCompat.getDrawable(requireContext(), R.drawable.ic_user_dot));
            me.setTitle(getString(R.string.map_you));
            me.setOnMarkerClickListener((clicked, mapView) -> true); // pas de bulle
            map.getOverlays().add(me);
        }
        map.invalidate();
        if (mapMode) centerMapIfNeeded();
    }

    /** Premier affichage de la carte : centrée sur le joueur, sinon sur le premier match, sinon Valenciennes. */
    private void centerMapIfNeeded() {
        if (mapCentered) return;
        GeoPoint center = null;
        if (userLocation != null) {
            center = new GeoPoint(userLocation.getLatitude(), userLocation.getLongitude());
        } else {
            for (Match match : adapter.getCurrentList()) {
                if (match.getLat() != null && match.getLng() != null) {
                    center = new GeoPoint(match.getLat(), match.getLng());
                    break;
                }
            }
        }
        if (center == null) return; // on garde Valenciennes en attendant des données
        map.getController().setZoom(DEFAULT_ZOOM);
        map.getController().setCenter(center);
        mapCentered = true;
    }

    private static String textOf(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }
}
