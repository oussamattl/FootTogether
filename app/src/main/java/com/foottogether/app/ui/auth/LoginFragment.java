package com.foottogether.app.ui.auth;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.foottogether.app.R;
import com.foottogether.app.data.remote.AuthRepository;
import com.foottogether.app.data.remote.ResultCallback;
import com.foottogether.app.util.InputValidator;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/**
 * Onglet « Connexion » : e-mail + mot de passe → Firebase Authentication.
 */
public class LoginFragment extends Fragment {

    private final AuthRepository authRepository = new AuthRepository();

    private TextInputLayout emailLayout;
    private TextInputLayout passwordLayout;
    private TextInputEditText emailInput;
    private TextInputEditText passwordInput;
    private MaterialButton loginButton;
    private CircularProgressIndicator progress;
    private TextView errorText;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // "inflate" = transformer le fichier XML en vraies vues à l'écran
        return inflater.inflate(R.layout.fragment_login, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        emailLayout = view.findViewById(R.id.login_email_layout);
        passwordLayout = view.findViewById(R.id.login_password_layout);
        emailInput = view.findViewById(R.id.login_email);
        passwordInput = view.findViewById(R.id.login_password);
        loginButton = view.findViewById(R.id.login_button);
        progress = view.findViewById(R.id.login_progress);
        errorText = view.findViewById(R.id.login_error);

        loginButton.setOnClickListener(v -> attemptLogin());

        // Touche "OK" du clavier sur le mot de passe = clic sur "Se connecter"
        passwordInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptLogin();
                return true;
            }
            return false;
        });

        view.findViewById(R.id.login_go_register).setOnClickListener(v ->
                ((AuthActivity) requireActivity()).showTab(AuthActivity.TAB_REGISTER));
    }

    private void attemptLogin() {
        String email = textOf(emailInput).trim();
        String password = textOf(passwordInput);

        // 1. Vérifications locales
        emailLayout.setError(null);
        passwordLayout.setError(null);
        errorText.setVisibility(View.GONE);

        boolean valid = true;
        if (!InputValidator.isValidEmail(email)) {
            emailLayout.setError(getString(InputValidator.isBlank(email)
                    ? R.string.error_required : R.string.error_email_invalid));
            valid = false;
        }
        if (InputValidator.isBlank(password)) {
            passwordLayout.setError(getString(R.string.error_required));
            valid = false;
        }
        if (!valid) return;

        // 2. Appel au serveur
        setLoading(true);
        authRepository.login(email, password, new ResultCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                // Le fragment a pu être fermé pendant l'attente : on vérifie avant de toucher l'écran
                if (getView() == null) return;
                ((AuthActivity) requireActivity()).openMain();
            }

            @Override
            public void onError(Exception e) {
                if (getView() == null) return;
                setLoading(false);
                errorText.setText(AuthRepository.errorMessage(e));
                errorText.setVisibility(View.VISIBLE);
            }
        });
    }

    /** Pendant l'appel : bouton désactivé (pas de double clic) et cercle de chargement. */
    private void setLoading(boolean loading) {
        loginButton.setEnabled(!loading);
        loginButton.setText(loading ? "" : getString(R.string.action_login));
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private static String textOf(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString();
    }
}
