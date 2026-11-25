package com.foottogether.app.ui.auth;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.foottogether.app.R;
import com.foottogether.app.data.model.User;
import com.foottogether.app.data.remote.AuthRepository;
import com.foottogether.app.data.remote.ResultCallback;
import com.foottogether.app.util.InputValidator;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/**
 * Onglet « Inscription » : crée le compte Firebase Auth puis le profil users/{uid} dans Firestore.
 */
public class RegisterFragment extends Fragment {

    private final AuthRepository authRepository = new AuthRepository();

    private TextInputLayout pseudoLayout;
    private TextInputLayout nameLayout;
    private TextInputLayout emailLayout;
    private TextInputLayout passwordLayout;
    private TextInputLayout confirmLayout;
    private TextInputEditText pseudoInput;
    private TextInputEditText nameInput;
    private TextInputEditText emailInput;
    private TextInputEditText passwordInput;
    private TextInputEditText confirmInput;
    private MaterialButton registerButton;
    private CircularProgressIndicator progress;
    private TextView errorText;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_register, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        pseudoLayout = view.findViewById(R.id.register_pseudo_layout);
        nameLayout = view.findViewById(R.id.register_name_layout);
        emailLayout = view.findViewById(R.id.register_email_layout);
        passwordLayout = view.findViewById(R.id.register_password_layout);
        confirmLayout = view.findViewById(R.id.register_password_confirm_layout);
        pseudoInput = view.findViewById(R.id.register_pseudo);
        nameInput = view.findViewById(R.id.register_name);
        emailInput = view.findViewById(R.id.register_email);
        passwordInput = view.findViewById(R.id.register_password);
        confirmInput = view.findViewById(R.id.register_password_confirm);
        registerButton = view.findViewById(R.id.register_button);
        progress = view.findViewById(R.id.register_progress);
        errorText = view.findViewById(R.id.register_error);

        registerButton.setOnClickListener(v -> attemptRegister());
        confirmInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptRegister();
                return true;
            }
            return false;
        });

        view.findViewById(R.id.register_go_login).setOnClickListener(v ->
                ((AuthActivity) requireActivity()).showTab(AuthActivity.TAB_LOGIN));
    }

    private void attemptRegister() {
        String pseudo = textOf(pseudoInput).trim();
        String name = textOf(nameInput).trim();
        String email = textOf(emailInput).trim();
        String password = textOf(passwordInput);
        String confirm = textOf(confirmInput);

        if (!validate(pseudo, name, email, password, confirm)) return;

        setLoading(true);
        authRepository.register(pseudo, name, email, password, new ResultCallback<User>() {
            @Override
            public void onSuccess(User user) {
                if (getView() == null) return;
                Toast.makeText(requireContext(), getString(R.string.welcome_user, user.getPseudo()),
                        Toast.LENGTH_SHORT).show();
                ((AuthActivity) requireActivity()).openMain();
            }

            @Override
            public void onError(Exception e) {
                if (getView() == null) return;
                setLoading(false);
                int message = AuthRepository.errorMessage(e);
                // Erreur inconnue après la création du compte = souvent l'écriture du profil refusée
                if (message == R.string.error_generic) message = R.string.error_profile_save;
                errorText.setText(message);
                errorText.setVisibility(View.VISIBLE);
            }
        });
    }

    /** Vérifie tous les champs et affiche l'erreur sous chaque champ incorrect. */
    private boolean validate(String pseudo, String name, String email, String password, String confirm) {
        pseudoLayout.setError(null);
        nameLayout.setError(null);
        emailLayout.setError(null);
        passwordLayout.setError(null);
        confirmLayout.setError(null);
        errorText.setVisibility(View.GONE);

        boolean valid = true;
        if (!InputValidator.isValidPseudo(pseudo)) {
            pseudoLayout.setError(getString(pseudo.isEmpty()
                    ? R.string.error_required : R.string.error_pseudo_length));
            valid = false;
        }
        if (InputValidator.isBlank(name)) {
            nameLayout.setError(getString(R.string.error_required));
            valid = false;
        }
        if (!InputValidator.isValidEmail(email)) {
            emailLayout.setError(getString(email.isEmpty()
                    ? R.string.error_required : R.string.error_email_invalid));
            valid = false;
        }
        if (!InputValidator.isValidPassword(password)) {
            passwordLayout.setError(getString(R.string.error_password_short));
            valid = false;
        }
        if (!password.equals(confirm)) {
            confirmLayout.setError(getString(R.string.error_password_mismatch));
            valid = false;
        }
        return valid;
    }

    private void setLoading(boolean loading) {
        registerButton.setEnabled(!loading);
        registerButton.setText(loading ? "" : getString(R.string.action_register));
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private static String textOf(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString();
    }
}
