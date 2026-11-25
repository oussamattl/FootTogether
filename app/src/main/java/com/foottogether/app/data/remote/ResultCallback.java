package com.foottogether.app.data.remote;

/**
 * Les appels au serveur prennent du temps : on ne peut pas attendre la réponse
 * sans bloquer l'écran. On passe donc un "callback" (rappel) : le repository
 * appellera onSuccess ou onError plus tard, quand Firebase aura répondu.
 * Firebase appelle ses écouteurs sur le thread principal : on peut toucher l'interface directement.
 *
 * @param <T> type du résultat (par exemple User), ou Void s'il n'y a rien à renvoyer
 */
public interface ResultCallback<T> {
    void onSuccess(T result);

    void onError(Exception e);
}
