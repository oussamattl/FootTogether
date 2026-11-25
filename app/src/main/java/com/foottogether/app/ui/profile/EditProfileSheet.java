package com.foottogether.app.ui.profile;

import android.app.Dialog;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.foottogether.app.R;
import com.foottogether.app.data.model.User;
import com.foottogether.app.data.remote.ResultCallback;
import com.foottogether.app.data.remote.UserRepository;
import com.foottogether.app.util.InputValidator;
import com.foottogether.app.util.NetworkMonitor;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/**
 * Feuille « Modifier le profil » (BottomSheetDialogFragment = un fragment affiché en bas de l'écran).
 *
 * Les valeurs actuelles du profil lui sont passées dans ses « arguments » (un Bundle) :
 * Android les conserve si l'écran tourne, et les champs gardent ce que l'utilisateur a tapé.
 * Quand l'enregistrement réussit, on prévient l'écran Profil avec la « Fragment Result API ».
 */
public class EditProfileSheet extends BottomSheetDialogFragment {

    public static final String TAG = "edit_profile";
    /** Clé du résultat envoyé à ProfileFragment après un enregistrement réussi. */
    public static final String RESULT_SAVED = "profile_saved";

    private static final String ARG_UID = "uid";
    private static final String ARG_PSEUDO = "pseudo";
    private static final String ARG_NAME = "name";
    private static final String ARG_CITY = "city";
    private static final String ARG_POSITION = "position";
    private static final String ARG_LEVEL = "level";

    private final UserRepository userRepository = new UserRepository();

    private TextInputLayout pseudoLayout;
    private TextInputLayout nameLayout;
    private TextInputEditText pseudoInput;
    private TextInputEditText nameInput;
    private TextInputEditText cityInput;
    private ChipGroup positionGroup;
    private ChipGroup levelGroup;
    private MaterialButton saveButton;

    public static EditProfileSheet newInstance(User user) {
        Bundle args = new Bundle();
        args.putString(ARG_UID, user.getUid());
        args.putString(ARG_PSEUDO, user.getPseudo());
        args.putString(ARG_NAME, user.getName());
        args.putString(ARG_CITY, user.getCity());
        args.putString(ARG_POSITION, user.getPosition());
        args.putString(ARG_LEVEL, user.getLevel());
        EditProfileSheet sheet = new EditProfileSheet();
        sheet.setArguments(args);
        return sheet;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        // Ouverte en entier directement (pas à moitié)
        dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        dialog.getBehavior().setSkipCollapsed(true);
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_edit_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        pseudoLayout = view.findViewById(R.id.edit_pseudo_layout);
        nameLayout = view.findViewById(R.id.edit_name_layout);
        pseudoInput = view.findViewById(R.id.edit_pseudo);
        nameInput = view.findViewById(R.id.edit_name);
        cityInput = view.findViewById(R.id.edit_city);
        positionGroup = view.findViewById(R.id.edit_position_group);
        levelGroup = view.findViewById(R.id.edit_level_group);
        saveButton = view.findViewById(R.id.edit_save);

        // Premier affichage seulement : on remplit avec le profil actuel.
        // Après une rotation, Android remet lui-même ce que l'utilisateur avait modifié.
        if (savedInstanceState == null) {
            Bundle args = requireArguments();
            pseudoInput.setText(args.getString(ARG_PSEUDO));
            nameInput.setText(args.getString(ARG_NAME));
            cityInput.setText(args.getString(ARG_CITY));
            positionGroup.check(positionChipId(args.getString(ARG_POSITION)));
            levelGroup.check(levelChipId(args.getString(ARG_LEVEL)));
        }
        saveButton.setOnClickListener(v -> save());
    }

    private void save() {
        String pseudo = textOf(pseudoInput).trim();
        String name = textOf(nameInput).trim();
        String city = textOf(cityInput).trim();

        pseudoLayout.setError(null);
        nameLayout.setError(null);
        boolean valid = true;
        if (!InputValidator.isValidPseudo(pseudo)) {
            pseudoLayout.setError(getString(R.string.error_pseudo_length));
            valid = false;
        }
        if (InputValidator.isBlank(name)) {
            nameLayout.setError(getString(R.string.error_required));
            valid = false;
        }
        if (!valid) return;
        if (!NetworkMonitor.isOnline(requireContext())) {
            Toast.makeText(requireContext(), R.string.offline_banner, Toast.LENGTH_LONG).show();
            return;
        }

        User user = new User();
        user.setUid(requireArguments().getString(ARG_UID));
        user.setPseudo(pseudo);
        user.setName(name);
        user.setCity(city);
        user.setPosition(selectedPosition());
        user.setLevel(selectedLevel());

        saveButton.setEnabled(false);
        saveButton.setText(R.string.action_in_progress);
        userRepository.updateProfile(user, new ResultCallback<Void>() {
            @Override
            public void onSuccess(Void unused) {
                if (!isAdded()) return;
                getParentFragmentManager().setFragmentResult(RESULT_SAVED, new Bundle());
                dismiss();
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Profil non enregistré", e);
                if (!isAdded()) return;
                saveButton.setEnabled(true);
                saveButton.setText(R.string.action_save);
                Toast.makeText(requireContext(), R.string.error_profile_save, Toast.LENGTH_LONG).show();
            }
        });
    }

    private int positionChipId(String position) {
        if (User.POSITION_GOALKEEPER.equals(position)) return R.id.edit_position_goalkeeper;
        if (User.POSITION_DEFENDER.equals(position)) return R.id.edit_position_defender;
        if (User.POSITION_FORWARD.equals(position)) return R.id.edit_position_forward;
        return R.id.edit_position_midfielder;
    }

    private int levelChipId(String level) {
        if (User.LEVEL_BEGINNER.equals(level)) return R.id.edit_level_beginner;
        if (User.LEVEL_ADVANCED.equals(level)) return R.id.edit_level_advanced;
        return R.id.edit_level_intermediate;
    }

    private String selectedPosition() {
        int id = positionGroup.getCheckedChipId();
        if (id == R.id.edit_position_goalkeeper) return User.POSITION_GOALKEEPER;
        if (id == R.id.edit_position_defender) return User.POSITION_DEFENDER;
        if (id == R.id.edit_position_forward) return User.POSITION_FORWARD;
        return User.POSITION_MIDFIELDER;
    }

    private String selectedLevel() {
        int id = levelGroup.getCheckedChipId();
        if (id == R.id.edit_level_beginner) return User.LEVEL_BEGINNER;
        if (id == R.id.edit_level_advanced) return User.LEVEL_ADVANCED;
        return User.LEVEL_INTERMEDIATE;
    }

    private static String textOf(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString();
    }
}
