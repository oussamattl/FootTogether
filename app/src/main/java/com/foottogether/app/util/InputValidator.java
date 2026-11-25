package com.foottogether.app.util;

import android.util.Patterns;

/**
 * Vérifications des saisies côté téléphone, AVANT d'appeler le serveur.
 * Ça évite des allers-retours inutiles et donne un message clair tout de suite.
 * (Le serveur vérifie aussi de son côté : on ne fait jamais confiance au seul client.)
 */
public final class InputValidator {

    public static final int PASSWORD_MIN_LENGTH = 6; // minimum imposé par Firebase Auth
    public static final int PSEUDO_MIN_LENGTH = 3;
    public static final int PSEUDO_MAX_LENGTH = 20;

    private InputValidator() {
        // classe utilitaire : pas d'instance
    }

    public static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static boolean isValidEmail(String email) {
        return !isBlank(email) && Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches();
    }

    public static boolean isValidPassword(String password) {
        return password != null && password.length() >= PASSWORD_MIN_LENGTH;
    }

    public static boolean isValidPseudo(String pseudo) {
        if (pseudo == null) return false;
        int length = pseudo.trim().length();
        return length >= PSEUDO_MIN_LENGTH && length <= PSEUDO_MAX_LENGTH;
    }
}
