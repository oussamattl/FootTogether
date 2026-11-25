package com.foottogether.app.ui.create;

import android.content.Context;
import android.location.Address;
import android.location.Location;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.foottogether.app.R;
import com.foottogether.app.data.prefs.AppPrefs;
import com.foottogether.app.data.model.Match;
import com.foottogether.app.data.model.User;
import com.foottogether.app.data.remote.AuthRepository;
import com.foottogether.app.data.remote.MatchRepository;
import com.foottogether.app.data.remote.ResultCallback;
import com.foottogether.app.data.remote.UserRepository;
import com.foottogether.app.util.DateFormatter;
import com.foottogether.app.util.ErrorMessages;
import com.foottogether.app.util.GeocoderHelper;
import com.foottogether.app.util.InputValidator;
import com.foottogether.app.util.LocationHelper;
import com.foottogether.app.util.MatchLabels;
import com.foottogether.app.util.NetworkMonitor;
import com.foottogether.app.util.ReminderScheduler;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointForward;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;
import com.google.firebase.Timestamp;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

/**
 * Formulaire d'un match, utilisé de DEUX façons :
 * - onglet « Créer » de MainActivity : création d'un nouveau match ;
 * - EditMatchActivity : modification d'un match existant par son organisateur
 *   (on passe alors l'id du match dans les arguments du fragment : newEditInstance).
 *
 * Le fragment ne sait pas dans quelle activité il se trouve : à la fin, il prévient
 * son activité via l'interface Host (chaque activité décide quoi faire ensuite).
 *
 * Sauvegarde d'état (Bundle) : Android garde tout seul le texte des champs et le choix
 * des chips (ils ont un id). Nous sauvegardons nous-mêmes ce qu'Android ne connaît pas :
 * la date, l'heure, le nombre de joueurs, et en modification le minimum de joueurs.
 */
public class CreateMatchFragment extends Fragment {

    /** Ce que l'activité qui contient le formulaire doit savoir faire. */
    public interface Host {
        /** Appelée quand le serveur a confirmé l'enregistrement du match. */
        void onMatchSaved(boolean edited);
    }

    private static final String TAG = "CreateMatchFragment";

    private static final String ARG_MATCH_ID = "match_id";

    // Clés du Bundle
    private static final String KEY_DATE_UTC = "date_utc";
    private static final String KEY_HOUR = "hour";
    private static final String KEY_MINUTE = "minute";
    private static final String KEY_MAX_PLAYERS = "max_players";
    private static final String KEY_MIN_PLAYERS = "min_players";
    private static final String KEY_EDIT_LOADED = "edit_loaded";
    private static final String KEY_LAT = "picked_lat";
    private static final String KEY_LNG = "picked_lng";

    // Étiquettes des boîtes de dialogue (pour les retrouver après une rotation)
    private static final String TAG_DATE_PICKER = "date_picker";
    private static final String TAG_TIME_PICKER = "time_picker";

    private static final int TITLE_MIN_LENGTH = 3;
    private static final int TITLE_MAX_LENGTH = 60;

    private final AuthRepository authRepository = new AuthRepository();
    private final UserRepository userRepository = new UserRepository();
    private final MatchRepository matchRepository = new MatchRepository();

    /** null en création ; id du match en modification. */
    @Nullable
    private String editMatchId;
    /** Vrai quand le match à modifier a été lu et le formulaire pré-rempli. */
    private boolean editLoaded;

    // État du formulaire non géré automatiquement par Android
    private Long selectedDateUtc;     // jour choisi (minuit UTC, format du sélecteur Material)
    private int selectedHour = -1;    // -1 = pas encore choisi
    private int selectedMinute = -1;
    private int maxPlayers = 10;
    /** En modification : on ne peut pas descendre sous le nombre de joueurs déjà inscrits. */
    private int minPlayers = Match.MIN_PLAYERS;

    // Coordonnées du terrain (lot 5) : venant de « Utiliser ma position » ou du match modifié.
    // null = inconnues : on cherchera l'adresse avec le Geocoder au moment d'enregistrer.
    @Nullable
    private Double pickedLat;
    @Nullable
    private Double pickedLng;
    /** Vrai pendant que le code remplit lui-même l'adresse (pour ne pas effacer la position). */
    private boolean fillingAddress;

    private MaterialButton useLocationButton;
    private TextView locationStatus;

    /** Demande de permission de localisation (fenêtre système « Autoriser ... ? »). */
    private final ActivityResultLauncher<String[]> locationPermission = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (getView() == null) return;
                if (LocationHelper.hasPermission(requireContext())) {
                    fetchCurrentPosition();
                } else {
                    Snackbar.make(requireView(), R.string.location_denied_create, Snackbar.LENGTH_LONG).show();
                }
            });

    private TextInputLayout titleLayout;
    private TextInputLayout dateLayout;
    private TextInputLayout timeLayout;
    private TextInputLayout addressLayout;
    private TextInputLayout cityLayout;
    private TextInputEditText titleInput;
    private TextInputEditText dateInput;
    private TextInputEditText timeInput;
    private TextInputEditText addressInput;
    private TextInputEditText cityInput;
    private TextInputEditText descriptionInput;
    private ChipGroup formatGroup;
    private ChipGroup levelGroup;
    private TextView playersCount;
    private MaterialButton publishButton;
    private CircularProgressIndicator progress;

    /** Formulaire en mode « modifier le match matchId ». */
    public static CreateMatchFragment newEditInstance(String matchId) {
        Bundle args = new Bundle();
        args.putString(ARG_MATCH_ID, matchId);
        CreateMatchFragment fragment = new CreateMatchFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_create_match, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        editMatchId = getArguments() == null ? null : getArguments().getString(ARG_MATCH_ID);
        bindViews(view);

        // Restauration depuis le Bundle (après une rotation par exemple)
        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey(KEY_DATE_UTC)) {
                selectedDateUtc = savedInstanceState.getLong(KEY_DATE_UTC);
            }
            selectedHour = savedInstanceState.getInt(KEY_HOUR, -1);
            selectedMinute = savedInstanceState.getInt(KEY_MINUTE, -1);
            minPlayers = savedInstanceState.getInt(KEY_MIN_PLAYERS, Match.MIN_PLAYERS);
            editLoaded = savedInstanceState.getBoolean(KEY_EDIT_LOADED, false);
            if (savedInstanceState.containsKey(KEY_LAT)) {
                pickedLat = savedInstanceState.getDouble(KEY_LAT);
                pickedLng = savedInstanceState.getDouble(KEY_LNG);
            }
        } else {
            maxPlayers = MatchLabels.defaultPlayersFor(selectedFormat());
        }
        updateDateField();
        updateTimeField();
        updatePlayersCount();
        publishButton.setText(submitLabel());

        dateInput.setOnClickListener(v -> showDatePicker());
        timeInput.setOnClickListener(v -> showTimePicker());
        view.findViewById(R.id.create_players_minus).setOnClickListener(v -> changePlayers(-1));
        view.findViewById(R.id.create_players_plus).setOnClickListener(v -> changePlayers(+1));
        publishButton.setOnClickListener(v -> attemptPublish());
        useLocationButton.setOnClickListener(v -> onUseLocationClicked());
        updateLocationStatus();

        // Si un sélecteur était ouvert avant la rotation, Android l'a recréé : on rebranche son écouteur
        reattachPickers();

        // Modification : on lit le match une seule fois pour pré-remplir le formulaire
        if (isEditMode() && !editLoaded) loadMatchForEdit();

        // Nouveau match : la ville par défaut des Paramètres (SharedPreferences) est pré-remplie.
        // Seulement au premier affichage : après une rotation, Android remet le texte tapé.
        if (!isEditMode() && savedInstanceState == null) {
            cityInput.setText(new AppPrefs(requireContext()).getDefaultCity());
        }
    }

    /**
     * Appelée après qu'Android a restauré l'état des vues (texte, chips cochées).
     * On remet le nombre de joueurs ICI, car la restauration du chip de format
     * déclenche son écouteur, qui remettrait sinon la valeur par défaut du format.
     */
    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        if (savedInstanceState != null) {
            maxPlayers = savedInstanceState.getInt(KEY_MAX_PLAYERS, maxPlayers);
            updatePlayersCount();
        }
        // Branché après la restauration : changer de format propose le nombre de joueurs adapté
        formatGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            maxPlayers = Math.max(minPlayers, MatchLabels.defaultPlayersFor(selectedFormat()));
            updatePlayersCount();
        });
        // Si l'utilisateur retape l'adresse ou la ville, l'ancienne position ne correspond plus :
        // on l'oublie, et le Geocoder cherchera la nouvelle adresse à l'enregistrement.
        // (Branché ici aussi : la restauration du texte après rotation ne doit pas l'effacer.)
        TextWatcher placeWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (fillingAddress || pickedLat == null) return;
                pickedLat = null;
                pickedLng = null;
                updateLocationStatus();
            }
        };
        addressInput.addTextChangedListener(placeWatcher);
        cityInput.addTextChangedListener(placeWatcher);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (selectedDateUtc != null) outState.putLong(KEY_DATE_UTC, selectedDateUtc);
        outState.putInt(KEY_HOUR, selectedHour);
        outState.putInt(KEY_MINUTE, selectedMinute);
        outState.putInt(KEY_MAX_PLAYERS, maxPlayers);
        outState.putInt(KEY_MIN_PLAYERS, minPlayers);
        outState.putBoolean(KEY_EDIT_LOADED, editLoaded);
        if (pickedLat != null && pickedLng != null) {
            outState.putDouble(KEY_LAT, pickedLat);
            outState.putDouble(KEY_LNG, pickedLng);
        }
    }

    private boolean isEditMode() {
        return editMatchId != null;
    }

    private int submitLabel() {
        return isEditMode() ? R.string.action_save_match : R.string.action_publish;
    }

    private void bindViews(View view) {
        titleLayout = view.findViewById(R.id.create_title_layout);
        dateLayout = view.findViewById(R.id.create_date_layout);
        timeLayout = view.findViewById(R.id.create_time_layout);
        addressLayout = view.findViewById(R.id.create_address_layout);
        cityLayout = view.findViewById(R.id.create_city_layout);
        titleInput = view.findViewById(R.id.create_title);
        dateInput = view.findViewById(R.id.create_date);
        timeInput = view.findViewById(R.id.create_time);
        addressInput = view.findViewById(R.id.create_address);
        cityInput = view.findViewById(R.id.create_city);
        descriptionInput = view.findViewById(R.id.create_description);
        formatGroup = view.findViewById(R.id.create_format_group);
        levelGroup = view.findViewById(R.id.create_level_group);
        playersCount = view.findViewById(R.id.create_players_count);
        publishButton = view.findViewById(R.id.create_publish);
        progress = view.findViewById(R.id.create_progress);
        useLocationButton = view.findViewById(R.id.create_use_location);
        locationStatus = view.findViewById(R.id.create_location_status);
    }

    // ===== Position du terrain (lot 5) =====

    private void onUseLocationClicked() {
        if (LocationHelper.hasPermission(requireContext())) {
            fetchCurrentPosition();
        } else {
            locationPermission.launch(LocationHelper.PERMISSIONS);
        }
    }

    /** Position actuelle = position du terrain ; puis on remplit l'adresse et la ville (Geocoder inverse). */
    private void fetchCurrentPosition() {
        useLocationButton.setEnabled(false);
        useLocationButton.setText(R.string.location_searching);
        LocationHelper.getCurrentLocation(requireContext(), new ResultCallback<Location>() {
            @Override
            public void onSuccess(Location location) {
                if (getView() == null) return;
                pickedLat = location.getLatitude();
                pickedLng = location.getLongitude();
                resetLocationButton();
                updateLocationStatus();
                fillAddressFrom(location);
            }

            @Override
            public void onError(Exception e) {
                Log.w(TAG, "Position indisponible", e);
                if (getView() == null) return;
                resetLocationButton();
                Snackbar.make(requireView(), R.string.location_unavailable, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    private void fillAddressFrom(Location location) {
        GeocoderHelper.reverse(requireContext(), location.getLatitude(), location.getLongitude(),
                new ResultCallback<Address>() {
                    @Override
                    public void onSuccess(Address address) {
                        if (getView() == null || address == null) return;
                        fillingAddress = true; // ne pas effacer la position qu'on vient d'obtenir
                        String street = address.getThoroughfare();
                        if (street != null) {
                            String number = address.getSubThoroughfare();
                            addressInput.setText(number == null ? street : number + " " + street);
                        }
                        if (address.getLocality() != null) cityInput.setText(address.getLocality());
                        fillingAddress = false;
                    }

                    @Override
                    public void onError(Exception e) {
                        Log.w(TAG, "Adresse introuvable pour cette position", e);
                    }
                });
    }

    private void resetLocationButton() {
        useLocationButton.setEnabled(true);
        useLocationButton.setText(R.string.use_my_location);
    }

    private void updateLocationStatus() {
        locationStatus.setVisibility(pickedLat != null ? View.VISIBLE : View.GONE);
    }

    // ===== Modification : pré-remplissage =====

    private void loadMatchForEdit() {
        setLoading(true);
        matchRepository.getMatch(editMatchId, new ResultCallback<Match>() {
            @Override
            public void onSuccess(Match match) {
                if (getView() == null) return;
                fillForm(match);
                editLoaded = true;
                setLoading(false);
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Match à modifier non chargé", e);
                if (getView() == null) return;
                setLoading(false);
                Snackbar.make(requireView(), ErrorMessages.forFirestore(e, R.string.error_match_load),
                        Snackbar.LENGTH_LONG).show();
            }
        });
    }

    private void fillForm(Match match) {
        titleInput.setText(match.getTitle());
        fillingAddress = true;
        addressInput.setText(match.getAddress());
        cityInput.setText(match.getCity());
        fillingAddress = false;
        // On garde les coordonnées actuelles tant que l'adresse n'est pas modifiée
        pickedLat = match.getLat();
        pickedLng = match.getLng();
        updateLocationStatus();
        descriptionInput.setText(match.getDescription());

        if (match.getDateTime() != null) {
            // Date locale du match → jour à minuit UTC (format attendu par le sélecteur Material)
            Calendar local = Calendar.getInstance();
            local.setTime(match.getDateTime().toDate());
            Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            utc.clear();
            utc.set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH));
            selectedDateUtc = utc.getTimeInMillis();
            selectedHour = local.get(Calendar.HOUR_OF_DAY);
            selectedMinute = local.get(Calendar.MINUTE);
        }

        minPlayers = Math.max(Match.MIN_PLAYERS, (int) match.getParticipantCount());
        // Cocher le format déclenche son écouteur : on fixe maxPlayers APRÈS
        formatGroup.check(formatChipId(match.getFormat()));
        levelGroup.check(levelChipId(match.getLevel()));
        maxPlayers = (int) match.getMaxPlayers();

        updateDateField();
        updateTimeField();
        updatePlayersCount();
    }

    private int formatChipId(String format) {
        if (Match.FORMAT_SEVEN.equals(format)) return R.id.create_format_seven;
        if (Match.FORMAT_ELEVEN.equals(format)) return R.id.create_format_eleven;
        return R.id.create_format_five;
    }

    private int levelChipId(String level) {
        if (User.LEVEL_BEGINNER.equals(level)) return R.id.create_level_beginner;
        if (User.LEVEL_ADVANCED.equals(level)) return R.id.create_level_advanced;
        return R.id.create_level_intermediate;
    }

    // ===== Date et heure =====

    private void showDatePicker() {
        if (getChildFragmentManager().findFragmentByTag(TAG_DATE_PICKER) != null) return;
        // On interdit les jours passés dans le calendrier
        CalendarConstraints constraints = new CalendarConstraints.Builder()
                .setValidator(DateValidatorPointForward.now())
                .build();
        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(R.string.pick_date_title)
                .setSelection(selectedDateUtc != null
                        ? selectedDateUtc : MaterialDatePicker.todayInUtcMilliseconds())
                .setCalendarConstraints(constraints)
                .build();
        attachDateListener(picker);
        picker.show(getChildFragmentManager(), TAG_DATE_PICKER);
    }

    private void showTimePicker() {
        if (getChildFragmentManager().findFragmentByTag(TAG_TIME_PICKER) != null) return;
        boolean is24h = DateFormat.is24HourFormat(requireContext());
        MaterialTimePicker picker = new MaterialTimePicker.Builder()
                .setTimeFormat(is24h ? TimeFormat.CLOCK_24H : TimeFormat.CLOCK_12H)
                .setHour(selectedHour >= 0 ? selectedHour : 15)
                .setMinute(selectedMinute >= 0 ? selectedMinute : 0)
                .setTitleText(R.string.pick_time_title)
                .build();
        attachTimeListener(picker);
        picker.show(getChildFragmentManager(), TAG_TIME_PICKER);
    }

    private void attachDateListener(MaterialDatePicker<Long> picker) {
        picker.addOnPositiveButtonClickListener(selection -> {
            selectedDateUtc = selection;
            dateLayout.setError(null);
            updateDateField();
        });
    }

    private void attachTimeListener(MaterialTimePicker picker) {
        picker.addOnPositiveButtonClickListener(v -> {
            selectedHour = picker.getHour();
            selectedMinute = picker.getMinute();
            timeLayout.setError(null);
            updateTimeField();
        });
    }

    @SuppressWarnings("unchecked")
    private void reattachPickers() {
        Fragment datePicker = getChildFragmentManager().findFragmentByTag(TAG_DATE_PICKER);
        if (datePicker instanceof MaterialDatePicker) {
            attachDateListener((MaterialDatePicker<Long>) datePicker);
        }
        Fragment timePicker = getChildFragmentManager().findFragmentByTag(TAG_TIME_PICKER);
        if (timePicker instanceof MaterialTimePicker) {
            attachTimeListener((MaterialTimePicker) timePicker);
        }
    }

    private void updateDateField() {
        Date day = buildDate(0, 0);
        dateInput.setText(day == null ? "" : DateFormatter.formatShortDay(requireContext(), day));
    }

    private void updateTimeField() {
        if (selectedHour < 0) {
            timeInput.setText("");
            return;
        }
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, selectedHour);
        calendar.set(Calendar.MINUTE, selectedMinute);
        timeInput.setText(DateFormatter.formatTime(requireContext(), calendar.getTime()));
    }

    /**
     * Combine le jour choisi et une heure en une date locale.
     * Le sélecteur Material renvoie le jour à minuit UTC : on relit l'année/mois/jour en UTC,
     * puis on construit la date dans le fuseau horaire du téléphone.
     */
    @Nullable
    private Date buildDate(int hour, int minute) {
        if (selectedDateUtc == null) return null;
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        utc.setTimeInMillis(selectedDateUtc);
        Calendar local = Calendar.getInstance();
        local.clear();
        local.set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH),
                hour, minute, 0);
        return local.getTime();
    }

    // ===== Nombre de joueurs =====

    private void changePlayers(int delta) {
        int lowest = Math.max(Match.MIN_PLAYERS, minPlayers);
        int wanted = maxPlayers + delta;
        if (wanted < lowest && isEditMode()) {
            // On explique pourquoi on ne peut pas descendre plus bas
            Snackbar.make(requireView(), getString(R.string.error_max_below_count_n, minPlayers),
                    Snackbar.LENGTH_SHORT).show();
        }
        maxPlayers = Math.max(lowest, Math.min(Match.MAX_PLAYERS, wanted));
        updatePlayersCount();
    }

    private void updatePlayersCount() {
        playersCount.setText(String.valueOf(maxPlayers));
    }

    // ===== Choix =====

    private String selectedFormat() {
        int id = formatGroup.getCheckedChipId();
        if (id == R.id.create_format_seven) return Match.FORMAT_SEVEN;
        if (id == R.id.create_format_eleven) return Match.FORMAT_ELEVEN;
        return Match.FORMAT_FIVE;
    }

    private String selectedLevel() {
        int id = levelGroup.getCheckedChipId();
        if (id == R.id.create_level_beginner) return User.LEVEL_BEGINNER;
        if (id == R.id.create_level_advanced) return User.LEVEL_ADVANCED;
        return User.LEVEL_INTERMEDIATE;
    }

    // ===== Publication / enregistrement =====

    private void attemptPublish() {
        hideKeyboard();
        if (isEditMode() && !editLoaded) return; // formulaire pas encore pré-rempli
        String title = textOf(titleInput).trim();
        String address = textOf(addressInput).trim();
        String city = textOf(cityInput).trim();
        String description = textOf(descriptionInput).trim();
        Date dateTime = selectedHour < 0 ? null : buildDate(selectedHour, selectedMinute);

        if (!validate(title, address, city, dateTime)) return;

        // Pas de réseau : on prévient tout de suite au lieu d'attendre
        if (!NetworkMonitor.isOnline(requireContext())) {
            Snackbar.make(requireView(), R.string.offline_banner, Snackbar.LENGTH_LONG).show();
            return;
        }

        Match match = new Match();
        match.setTitle(title);
        match.setDescription(description);
        match.setDateTime(new Timestamp(dateTime));
        match.setAddress(address);
        match.setCity(city);
        match.setFormat(selectedFormat());
        match.setMaxPlayers(maxPlayers);
        match.setLevel(selectedLevel());

        String uid = authRepository.getCurrentUid();
        if (uid == null) return;
        setLoading(true);
        // 1. coordonnées du terrain, 2. enregistrement
        resolveCoordinates(match, () -> {
            if (isEditMode()) {
                saveChanges(match, uid);
            } else {
                // On relit le profil pour connaître le pseudo de l'organisateur, puis on crée le match
                userRepository.getUser(uid, new ResultCallback<User>() {
                    @Override
                    public void onSuccess(User organizer) {
                        if (getView() == null) return;
                        publish(match, organizer);
                    }

                    @Override
                    public void onError(Exception e) {
                        showSaveError(e);
                    }
                });
            }
        });
    }

    /**
     * Place les coordonnées dans le match : la position choisie avec « Utiliser ma position »,
     * sinon l'adresse convertie par le Geocoder. Si rien n'est trouvé, on enregistre quand même
     * (le match n'aura simplement ni distance, ni épingle sur la carte, ni météo).
     */
    private void resolveCoordinates(Match match, Runnable then) {
        if (pickedLat != null && pickedLng != null) {
            match.setLat(pickedLat);
            match.setLng(pickedLng);
            then.run();
            return;
        }
        GeocoderHelper.geocode(requireContext(), match.getAddress() + ", " + match.getCity(),
                new ResultCallback<Address>() {
                    @Override
                    public void onSuccess(Address address) {
                        if (getView() == null) return;
                        if (address != null) {
                            match.setLat(address.getLatitude());
                            match.setLng(address.getLongitude());
                        } else {
                            Log.w(TAG, "Adresse introuvable : match sans coordonnées");
                        }
                        then.run();
                    }

                    @Override
                    public void onError(Exception e) {
                        Log.w(TAG, "Geocoder indisponible : match sans coordonnées", e);
                        if (getView() != null) then.run();
                    }
                });
    }

    private void publish(Match match, User organizer) {
        matchRepository.createMatch(match, organizer, new ResultCallback<String>() {
            @Override
            public void onSuccess(String matchId) {
                // Rappel avant le match pour l'organisateur (lot 6)
                match.setId(matchId);
                if (getContext() != null) ReminderScheduler.schedule(getContext(), match);
                // Le match n'est annoncé comme publié qu'APRÈS la confirmation du serveur
                notifyHost(false);
            }

            @Override
            public void onError(Exception e) {
                showSaveError(e);
            }
        });
    }

    private void saveChanges(Match match, String uid) {
        matchRepository.updateMatchDetails(editMatchId, uid, match, new ResultCallback<Void>() {
            @Override
            public void onSuccess(Void unused) {
                // La date a peut-être changé : on reprogramme le rappel de l'organisateur
                match.setId(editMatchId);
                if (getContext() != null) ReminderScheduler.schedule(getContext(), match);
                notifyHost(true);
            }

            @Override
            public void onError(Exception e) {
                showSaveError(e);
            }
        });
    }

    private void notifyHost(boolean edited) {
        if (getView() == null) return;
        if (requireActivity() instanceof Host) {
            ((Host) requireActivity()).onMatchSaved(edited);
        }
    }

    private void showSaveError(Exception e) {
        Log.e(TAG, "Enregistrement du match impossible", e);
        if (getView() == null) return;
        setLoading(false);
        int fallback = isEditMode() ? R.string.error_update_match : R.string.error_create_match;
        Snackbar.make(requireView(), ErrorMessages.forFirestore(e, fallback), Snackbar.LENGTH_LONG).show();
    }

    /** Vérifications côté téléphone (le serveur revérifie avec les règles Firestore). */
    private boolean validate(String title, String address, String city, @Nullable Date dateTime) {
        titleLayout.setError(null);
        dateLayout.setError(null);
        timeLayout.setError(null);
        addressLayout.setError(null);
        cityLayout.setError(null);

        boolean valid = true;
        if (title.length() < TITLE_MIN_LENGTH || title.length() > TITLE_MAX_LENGTH) {
            titleLayout.setError(getString(title.isEmpty() ? R.string.error_required : R.string.error_title_length));
            valid = false;
        }
        if (selectedDateUtc == null) {
            dateLayout.setError(getString(R.string.error_required));
            valid = false;
        }
        if (selectedHour < 0) {
            timeLayout.setError(getString(R.string.error_required));
            valid = false;
        }
        if (dateTime != null && dateTime.getTime() <= System.currentTimeMillis()) {
            timeLayout.setError(getString(R.string.error_date_past));
            valid = false;
        }
        if (InputValidator.isBlank(address)) {
            addressLayout.setError(getString(R.string.error_required));
            valid = false;
        }
        if (InputValidator.isBlank(city)) {
            cityLayout.setError(getString(R.string.error_required));
            valid = false;
        }
        return valid;
    }

    private void setLoading(boolean loading) {
        publishButton.setEnabled(!loading);
        publishButton.setText(loading ? "" : getString(submitLabel()));
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager)
                requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(requireView().getWindowToken(), 0);
    }

    private static String textOf(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString();
    }
}
