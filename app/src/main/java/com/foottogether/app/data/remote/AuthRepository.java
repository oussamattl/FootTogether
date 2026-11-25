package com.foottogether.app.data.remote;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.foottogether.app.R;
import com.foottogether.app.data.model.User;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.FirebaseUser;

/**
 * Tout ce qui concerne le compte de l'utilisateur, via Firebase Authentication :
 * inscription, connexion, déconnexion, session en cours.
 *
 * La session est conservée automatiquement par Firebase sur le téléphone :
 * après une connexion, getCurrentUser() reste non nul même si on ferme l'app.
 */
public class AuthRepository {

    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final UserRepository userRepository = new UserRepository();

    /** Vrai si un utilisateur est connecté sur ce téléphone. */
    public boolean isLoggedIn() {
        return auth.getCurrentUser() != null;
    }

    /** uid (identifiant unique) de l'utilisateur connecté, ou null. */
    @Nullable
    public String getCurrentUid() {
        FirebaseUser user = auth.getCurrentUser();
        return user == null ? null : user.getUid();
    }

    /** Connexion avec e-mail et mot de passe. */
    public void login(String email, String password, ResultCallback<Void> callback) {
        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }

    /**
     * Inscription en deux étapes :
     * 1. Firebase Auth crée le compte (e-mail + mot de passe haché côté serveur) ;
     * 2. on crée le profil users/{uid} dans Firestore (pseudo, nom...).
     * Si l'étape 2 échoue, on supprime le compte créé à l'étape 1 pour ne pas laisser
     * un compte sans profil, et on signale l'erreur.
     */
    public void register(String pseudo, String name, String email, String password,
                         ResultCallback<User> callback) {
        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    FirebaseUser firebaseUser = result.getUser();
                    if (firebaseUser == null) {
                        callback.onError(new IllegalStateException("Compte non créé"));
                        return;
                    }
                    User user = new User(firebaseUser.getUid(), pseudo, name, email);
                    userRepository.createUser(user, new ResultCallback<Void>() {
                        @Override
                        public void onSuccess(Void unused) {
                            callback.onSuccess(user);
                        }

                        @Override
                        public void onError(Exception e) {
                            // Retour en arrière : on supprime le compte Auth sans profil
                            firebaseUser.delete();
                            callback.onError(e);
                        }
                    });
                })
                .addOnFailureListener(callback::onError);
    }

    /** Déconnexion : Firebase oublie la session enregistrée sur le téléphone. */
    public void logout() {
        auth.signOut();
    }

    /**
     * Traduit une erreur Firebase en message clair pour l'utilisateur
     * (identifiant d'un texte de strings.xml, donc traduit en FR/EN).
     */
    @StringRes
    public static int errorMessage(Exception e) {
        if (e instanceof FirebaseNetworkException) {
            return R.string.offline_banner;
        } else if (e instanceof FirebaseAuthUserCollisionException) {
            return R.string.error_email_taken;
        } else if (e instanceof FirebaseAuthWeakPasswordException) {
            return R.string.error_weak_password;
        } else if (e instanceof FirebaseAuthInvalidCredentialsException
                || e instanceof FirebaseAuthInvalidUserException) {
            return R.string.error_wrong_credentials;
        } else if (e instanceof FirebaseTooManyRequestsException) {
            return R.string.error_too_many_requests;
        }
        return R.string.error_generic;
    }
}
