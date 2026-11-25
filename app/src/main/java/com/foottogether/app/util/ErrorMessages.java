package com.foottogether.app.util;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.foottogether.app.R;
import com.foottogether.app.data.remote.MatchActionException;
import com.google.firebase.firestore.FirebaseFirestoreException;

/**
 * Transforme une erreur Firestore en message clair pour l'utilisateur.
 */
public final class ErrorMessages {

    private ErrorMessages() {
    }

    /**
     * @param fallback message utilisé si l'erreur n'est pas reconnue
     */
    @StringRes
    public static int forFirestore(@NonNull Exception e, @StringRes int fallback) {
        // Refus décidé par notre transaction (match complet, déjà inscrit...)
        MatchActionException refusal = MatchActionException.find(e);
        if (refusal != null) return refusal.getMessageRes();

        // Serveur injoignable (mode avion, pas de réseau...)
        if (e instanceof FirebaseFirestoreException
                && ((FirebaseFirestoreException) e).getCode() == FirebaseFirestoreException.Code.UNAVAILABLE) {
            return R.string.offline_banner;
        }
        return fallback;
    }
}
