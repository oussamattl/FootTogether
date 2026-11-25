package com.foottogether.app.data.remote;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.google.firebase.firestore.FirebaseFirestoreException;

/**
 * Refus « métier » d'une inscription ou d'un départ, décidé PENDANT la transaction :
 * match complet, déjà inscrit, match commencé...
 *
 * Elle transporte le message à afficher (texte traduit de strings.xml).
 * On hérite de FirebaseFirestoreException car c'est le seul type d'erreur qu'une
 * transaction Firestore accepte qu'on lance.
 * Code INVALID_ARGUMENT : Firestore le considère comme définitif et ne relance pas
 * la transaction (contrairement à ABORTED, qui provoque de nouveaux essais).
 */
public class MatchActionException extends FirebaseFirestoreException {

    @StringRes
    private final int messageRes;

    public MatchActionException(@StringRes int messageRes) {
        super("Action refusée", Code.INVALID_ARGUMENT);
        this.messageRes = messageRes;
    }

    @StringRes
    public int getMessageRes() {
        return messageRes;
    }

    /** Retrouve une MatchActionException dans une erreur (éventuellement « emballée » dans une autre). */
    @Nullable
    public static MatchActionException find(@NonNull Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof MatchActionException) return (MatchActionException) current;
            current = current.getCause();
        }
        return null;
    }
}
