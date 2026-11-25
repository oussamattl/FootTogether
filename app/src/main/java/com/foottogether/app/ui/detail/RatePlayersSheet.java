package com.foottogether.app.ui.detail;

import android.app.Dialog;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.foottogether.app.R;
import com.foottogether.app.data.model.Rating;
import com.foottogether.app.data.remote.RatingRepository;
import com.foottogether.app.data.remote.ResultCallback;
import com.foottogether.app.util.ErrorMessages;
import com.foottogether.app.util.NetworkMonitor;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;

/**
 * Feuille du bas « Évaluer les joueurs » (lot 9), ouverte depuis le Détail d'un match commencé.
 * Les joueurs à évaluer arrivent dans les arguments ; on les présente un par un.
 * Le numéro du joueur affiché est gardé dans le Bundle (rotation) ; les étoiles cochées
 * sont restaurées automatiquement par Android (les RatingBar ont un id).
 */
public class RatePlayersSheet extends BottomSheetDialogFragment {

    public static final String TAG = "rate_players";
    /** Résultat envoyé au Détail quand l'utilisateur a fini (Fragment Result API). */
    public static final String RESULT_DONE = "ratings_done";
    public static final String RESULT_COUNT = "count";

    private static final String ARG_MATCH_ID = "match_id";
    private static final String ARG_RATER_ID = "rater_id";
    private static final String ARG_UIDS = "uids";
    private static final String ARG_PSEUDOS = "pseudos";
    private static final String KEY_INDEX = "index";
    private static final String KEY_SENT = "sent";

    private final RatingRepository ratingRepository = new RatingRepository();

    private String matchId;
    private String raterId;
    private ArrayList<String> uids;
    private ArrayList<String> pseudos;
    private int index;
    /** Nombre d'évaluations envoyées pendant cette ouverture. */
    private int sent;
    private boolean sending;

    private TextView progressText;
    private TextView playerText;
    private TextView errorText;
    private RatingBar fairPlay;
    private RatingBar punctuality;
    private RatingBar teamSpirit;
    private MaterialButton sendButton;
    private MaterialButton skipButton;

    public static RatePlayersSheet newInstance(String matchId, String raterId,
                                               ArrayList<String> uids, ArrayList<String> pseudos) {
        Bundle args = new Bundle();
        args.putString(ARG_MATCH_ID, matchId);
        args.putString(ARG_RATER_ID, raterId);
        args.putStringArrayList(ARG_UIDS, uids);
        args.putStringArrayList(ARG_PSEUDOS, pseudos);
        RatePlayersSheet sheet = new RatePlayersSheet();
        sheet.setArguments(args);
        return sheet;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        dialog.getBehavior().setSkipCollapsed(true);
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_rate_players, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Bundle args = requireArguments();
        matchId = args.getString(ARG_MATCH_ID);
        raterId = args.getString(ARG_RATER_ID);
        uids = args.getStringArrayList(ARG_UIDS);
        pseudos = args.getStringArrayList(ARG_PSEUDOS);
        if (uids == null || pseudos == null) {
            uids = new ArrayList<>();
            pseudos = new ArrayList<>();
        }
        if (savedInstanceState != null) {
            index = savedInstanceState.getInt(KEY_INDEX, 0);
            sent = savedInstanceState.getInt(KEY_SENT, 0);
        }

        progressText = view.findViewById(R.id.rate_progress);
        playerText = view.findViewById(R.id.rate_player);
        errorText = view.findViewById(R.id.rate_error);
        fairPlay = view.findViewById(R.id.rate_fair_play);
        punctuality = view.findViewById(R.id.rate_punctuality);
        teamSpirit = view.findViewById(R.id.rate_team_spirit);
        sendButton = view.findViewById(R.id.rate_send);
        skipButton = view.findViewById(R.id.rate_skip);

        sendButton.setOnClickListener(v -> send());
        skipButton.setOnClickListener(v -> next());
        showPlayer(savedInstanceState == null);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_INDEX, index);
        outState.putInt(KEY_SENT, sent);
    }

    /** Affiche le joueur numéro index. resetStars = false après une rotation (étoiles restaurées). */
    private void showPlayer(boolean resetStars) {
        if (index >= uids.size()) {
            finish();
            return;
        }
        progressText.setText(getString(R.string.rate_progress, index + 1, uids.size()));
        playerText.setText(pseudos.get(index));
        errorText.setVisibility(View.GONE);
        if (resetStars) {
            fairPlay.setRating(0);
            punctuality.setRating(0);
            teamSpirit.setRating(0);
        }
    }

    private void send() {
        if (sending) return;
        int fair = Math.round(fairPlay.getRating());
        int punctual = Math.round(punctuality.getRating());
        int spirit = Math.round(teamSpirit.getRating());
        // Vérification côté téléphone (le serveur revérifie : notes de 1 à 5)
        if (fair < Rating.MIN || punctual < Rating.MIN || spirit < Rating.MIN) {
            errorText.setText(R.string.rate_missing);
            errorText.setVisibility(View.VISIBLE);
            return;
        }
        if (!NetworkMonitor.isOnline(requireContext())) {
            errorText.setText(R.string.offline_banner);
            errorText.setVisibility(View.VISIBLE);
            return;
        }
        setSending(true);
        Rating rating = new Rating(matchId, raterId, uids.get(index), fair, punctual, spirit);
        ratingRepository.rate(rating, new ResultCallback<Void>() {
            @Override
            public void onSuccess(Void unused) {
                if (getView() == null) return;
                setSending(false);
                sent++;
                next();
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Évaluation refusée", e);
                if (getView() == null) return;
                setSending(false);
                errorText.setText(ErrorMessages.forFirestore(e, R.string.error_rate));
                errorText.setVisibility(View.VISIBLE);
            }
        });
    }

    private void next() {
        index++;
        showPlayer(true);
    }

    private void setSending(boolean value) {
        sending = value;
        sendButton.setEnabled(!value);
        skipButton.setEnabled(!value);
        sendButton.setText(value ? R.string.action_in_progress : R.string.rate_send);
    }

    /** Plus de joueur : on prévient le Détail (s'il y a eu des envois) et on ferme. */
    private void finish() {
        Bundle result = new Bundle();
        result.putInt(RESULT_COUNT, sent);
        getParentFragmentManager().setFragmentResult(RESULT_DONE, result);
        if (sent == 0 && getContext() != null) {
            Toast.makeText(getContext(), R.string.rate_none_sent, Toast.LENGTH_SHORT).show();
        }
        dismissAllowingStateLoss();
    }
}
