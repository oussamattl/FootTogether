package com.foottogether.app.ui.profile;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;

import com.foottogether.app.R;
import com.foottogether.app.data.model.Rating;
import com.foottogether.app.data.model.User;
import com.foottogether.app.data.remote.AuthRepository;
import com.foottogether.app.data.remote.RatingRepository;
import com.foottogether.app.data.remote.ResultCallback;
import com.foottogether.app.data.remote.UserRepository;
import com.foottogether.app.ui.leaderboard.LeaderboardActivity;
import com.foottogether.app.ui.minigame.MiniGameActivity;
import com.foottogether.app.util.Badges;
import com.foottogether.app.util.ImageUtils;
import com.foottogether.app.util.LocaleHelper;
import com.foottogether.app.util.MatchLabels;
import com.foottogether.app.util.NetworkMonitor;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Onglet « Profil » : affiche le profil EN TEMPS RÉEL (compteurs, photo), permet de le modifier
 * (feuille EditProfileSheet) et de changer de photo.
 *
 * Photo de profil (point « Appareil photo » de l'énoncé) :
 * - « Prendre une photo » : ActivityResultContracts.TakePicturePreview() ouvre l'appareil photo
 *   et renvoie une petite image (Bitmap). Pas besoin de FileProvider ni de permission CAMERA,
 *   car c'est l'application Appareil photo du téléphone qui prend la photo.
 * - « Choisir dans la galerie » : PickVisualMedia (sélecteur de photos d'Android).
 * La photo est réduite (ImageUtils) dans un thread d'arrière-plan, puis envoyée à Firestore.
 */
public class ProfileFragment extends Fragment {

    private static final String TAG = "ProfileFragment";

    private final AuthRepository authRepository = new AuthRepository();
    private final UserRepository userRepository = new UserRepository();
    private final RatingRepository ratingRepository = new RatingRepository();
    /** Thread d'arrière-plan pour réduire la photo sans bloquer l'interface. */
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private ListenerRegistration userListener;
    @Nullable
    private User currentUser;
    /** Moyennes des évaluations reçues (lot 9), lues une fois à chaque affichage de l'onglet. */
    private Badges.Summary ratingSummary = Badges.Summary.of(Collections.emptyList());
    private TextView ratingsText;

    private TextView avatar;
    private ImageView photo;
    private TextView pseudo;
    private TextView city;
    private TextView position;
    private TextView level;
    private TextView played;
    private TextView organized;
    private TextView name;
    private TextView email;

    // Les « lanceurs » doivent être créés avant que le fragment soit affiché (champs initialisés ici)
    private final ActivityResultLauncher<Void> takePicture =
            registerForActivityResult(new ActivityResultContracts.TakePicturePreview(), bitmap -> {
                if (bitmap != null) processAndSave(() -> bitmap);
            });

    private final ActivityResultLauncher<PickVisualMediaRequest> pickImage =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) processAndSave(() -> decodeGalleryImage(uri));
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        avatar = view.findViewById(R.id.profile_avatar);
        photo = view.findViewById(R.id.profile_photo);
        pseudo = view.findViewById(R.id.profile_pseudo);
        city = view.findViewById(R.id.profile_city);
        position = view.findViewById(R.id.profile_position);
        level = view.findViewById(R.id.profile_level);
        played = view.findViewById(R.id.profile_played);
        organized = view.findViewById(R.id.profile_organized);
        name = view.findViewById(R.id.profile_name);
        email = view.findViewById(R.id.profile_email);

        view.findViewById(R.id.profile_photo_button).setOnClickListener(v -> choosePhotoSource());
        view.findViewById(R.id.profile_edit).setOnClickListener(v -> openEditSheet());
        view.findViewById(R.id.profile_leaderboard).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), LeaderboardActivity.class)));
        view.findViewById(R.id.profile_minigame).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), MiniGameActivity.class)));
        ratingsText = view.findViewById(R.id.profile_ratings);
        setUpBadge(view, R.id.profile_badge_regular, R.drawable.ic_event, R.string.badge_regular);
        setUpBadge(view, R.id.profile_badge_organizer, R.drawable.ic_add, R.string.badge_organizer);
        setUpBadge(view, R.id.profile_badge_fair_play, R.drawable.ic_favorite, R.string.badge_fair_play);

        // La feuille de modification nous prévient quand le profil est enregistré
        getChildFragmentManager().setFragmentResultListener(EditProfileSheet.RESULT_SAVED,
                getViewLifecycleOwner(), (key, result) -> showMessage(R.string.profile_saved));
    }

    @Override
    public void onStart() {
        super.onStart();
        String uid = authRepository.getCurrentUid();
        if (uid == null) return;
        userListener = userRepository.listenUser(uid, new ResultCallback<User>() {
            @Override
            public void onSuccess(User user) {
                if (getView() == null) return;
                currentUser = user;
                showUser(user);
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Profil non chargé", e);
                showMessage(R.string.error_profile_load);
            }
        });
        loadRatings(uid);
    }

    // ===== Badges et évaluations (lot 9) =====

    private void loadRatings(String uid) {
        ratingRepository.getReceivedRatings(uid, new ResultCallback<List<Rating>>() {
            @Override
            public void onSuccess(List<Rating> ratings) {
                if (getView() == null) return;
                ratingSummary = Badges.Summary.of(ratings);
                showRatings();
                if (currentUser != null) showBadges(currentUser);
            }

            @Override
            public void onError(Exception e) {
                Log.w(TAG, "Évaluations non chargées", e);
            }
        });
    }

    private void setUpBadge(View root, int rowId, int iconRes, @StringRes int titleRes) {
        View row = root.findViewById(rowId);
        ((ImageView) row.findViewById(R.id.badge_icon)).setImageResource(iconRes);
        ((TextView) row.findViewById(R.id.badge_title)).setText(titleRes);
    }

    /** Badge gagné : opaque + « Obtenu ». Pas encore gagné : transparent + progression. */
    private void showBadge(int rowId, boolean earned, String progress) {
        View row = requireView().findViewById(rowId);
        row.setAlpha(earned ? 1f : 0.45f);
        ((TextView) row.findViewById(R.id.badge_desc)).setText(
                earned ? getString(R.string.badge_earned) : progress);
    }

    private void showBadges(User user) {
        showBadge(R.id.profile_badge_regular, Badges.isRegular(user),
                getString(R.string.badge_regular_progress, user.getMatchesPlayed(), Badges.REGULAR_MIN_MATCHES));
        showBadge(R.id.profile_badge_organizer, Badges.isActiveOrganizer(user),
                getString(R.string.badge_organizer_progress, user.getMatchesOrganized(), Badges.ORGANIZER_MIN_MATCHES));
        showBadge(R.id.profile_badge_fair_play, Badges.isFairPlay(ratingSummary),
                getString(R.string.badge_fair_play_rule, Badges.FAIR_PLAY_MIN_RATINGS));
    }

    private void showRatings() {
        if (ratingSummary.count == 0) {
            ratingsText.setText(R.string.ratings_none);
            return;
        }
        Locale locale = LocaleHelper.appLocale(requireContext());
        ratingsText.setText(getResources().getQuantityString(R.plurals.ratings_summary, ratingSummary.count,
                String.format(locale, "%.1f", ratingSummary.fairPlay),
                String.format(locale, "%.1f", ratingSummary.punctuality),
                String.format(locale, "%.1f", ratingSummary.teamSpirit),
                ratingSummary.count));
    }

    @Override
    public void onStop() {
        if (userListener != null) {
            userListener.remove();
            userListener = null;
        }
        super.onStop();
    }

    @Override
    public void onDestroy() {
        executor.shutdown();
        super.onDestroy();
    }

    private void showUser(User user) {
        String userPseudo = user.getPseudo() == null ? "?" : user.getPseudo();
        avatar.setText(userPseudo.isEmpty() ? "?" : userPseudo.substring(0, 1).toUpperCase(Locale.ROOT));
        Bitmap bitmap = ImageUtils.fromBase64(user.getPhotoBase64());
        photo.setImageBitmap(bitmap);
        photo.setVisibility(bitmap == null ? View.GONE : View.VISIBLE);

        pseudo.setText(userPseudo);
        boolean hasCity = user.getCity() != null && !user.getCity().isEmpty();
        city.setText(hasCity ? user.getCity() : getString(R.string.profile_no_city));
        position.setText(MatchLabels.positionLabel(user.getPosition()));
        level.setText(MatchLabels.levelLabel(user.getLevel()));
        played.setText(String.valueOf(user.getMatchesPlayed()));
        organized.setText(String.valueOf(user.getMatchesOrganized()));
        name.setText(user.getName());
        email.setText(user.getEmail());
        showBadges(user);
        showRatings();
    }

    private void openEditSheet() {
        if (currentUser == null) return;
        if (getChildFragmentManager().findFragmentByTag(EditProfileSheet.TAG) != null) return;
        EditProfileSheet.newInstance(currentUser).show(getChildFragmentManager(), EditProfileSheet.TAG);
    }

    // ===== Photo =====

    private void choosePhotoSource() {
        boolean hasPhoto = currentUser != null && currentUser.getPhotoBase64() != null;
        String[] choices = hasPhoto
                ? new String[]{getString(R.string.photo_take), getString(R.string.photo_gallery),
                getString(R.string.photo_remove)}
                : new String[]{getString(R.string.photo_take), getString(R.string.photo_gallery)};
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.profile_change_photo)
                .setItems(choices, (dialog, which) -> {
                    if (which == 0) {
                        launchCamera();
                    } else if (which == 1) {
                        pickImage.launch(new PickVisualMediaRequest.Builder()
                                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                                .build());
                    } else {
                        savePhoto(null, null);
                    }
                })
                .show();
    }

    private void launchCamera() {
        try {
            takePicture.launch(null);
        } catch (ActivityNotFoundException e) {
            showMessage(R.string.photo_no_camera); // aucune application Appareil photo
        }
    }

    /** Source d'image lue dans le thread d'arrière-plan. */
    private interface BitmapSource {
        @Nullable
        Bitmap load() throws Exception;
    }

    @Nullable
    private Bitmap decodeGalleryImage(Uri uri) throws Exception {
        return ImageUtils.decodeFromUri(requireContext().getApplicationContext(), uri, ImageUtils.PHOTO_SIZE_PX);
    }

    /** Réduit la photo en arrière-plan, puis revient sur le thread principal pour l'envoyer. */
    private void processAndSave(BitmapSource source) {
        showMessage(R.string.photo_saving);
        executor.execute(() -> {
            String full = null;
            String thumb = null;
            try {
                Bitmap bitmap = source.load();
                if (bitmap != null) {
                    full = ImageUtils.toSquareBase64(bitmap, ImageUtils.PHOTO_SIZE_PX);
                    thumb = ImageUtils.toSquareBase64(bitmap, ImageUtils.THUMB_SIZE_PX);
                }
            } catch (Exception e) {
                Log.e(TAG, "Photo illisible", e);
            }
            String finalFull = full;
            String finalThumb = thumb;
            mainHandler.post(() -> {
                if (getView() == null) return;
                if (finalFull == null) {
                    showMessage(R.string.photo_error);
                } else {
                    savePhoto(finalFull, finalThumb);
                }
            });
        });
    }

    private void savePhoto(@Nullable String full, @Nullable String thumb) {
        String uid = authRepository.getCurrentUid();
        if (uid == null) return;
        if (!NetworkMonitor.isOnline(requireContext())) {
            showMessage(R.string.offline_banner);
            return;
        }
        userRepository.updatePhoto(uid, full, thumb, new ResultCallback<Void>() {
            @Override
            public void onSuccess(Void unused) {
                // L'affichage se met à jour tout seul grâce à l'écoute temps réel du profil
                showMessage(full == null ? R.string.photo_removed : R.string.photo_saved);
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Photo non enregistrée", e);
                showMessage(R.string.photo_error);
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
