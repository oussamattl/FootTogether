package com.foottogether.app.ui.common;

import android.content.Context;
import android.location.Location;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.foottogether.app.R;
import com.foottogether.app.data.model.Match;
import com.foottogether.app.util.DateFormatter;
import com.foottogether.app.util.DistanceUtils;
import com.foottogether.app.util.MatchLabels;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.Objects;

/**
 * Adapter de la liste des matchs (RecyclerView).
 *
 * Un RecyclerView n'affiche que les cartes visibles et RECYCLE celles qui sortent de l'écran :
 * l'adapter « remplit » une carte (ViewHolder) avec les données d'un match.
 *
 * ListAdapter + DiffUtil : quand Firestore envoie une nouvelle liste, DiffUtil calcule
 * ce qui a changé et ne redessine que ces cartes (avec une petite animation),
 * par exemple le compteur qui passe de 1/10 à 2/10.
 */
public class MatchAdapter extends ListAdapter<Match, MatchAdapter.MatchViewHolder> {

    /** Ce qui se passe quand on touche une carte (défini par l'écran qui utilise l'adapter). */
    public interface OnMatchClickListener {
        void onMatchClick(Match match);
    }

    private final OnMatchClickListener listener;
    /** Position du joueur (null si inconnue ou refusée) : sert à afficher la distance. */
    @Nullable
    private Location userLocation;

    /** Appelée quand la position est connue : les cartes affichent alors la distance. */
    public void setUserLocation(@Nullable Location location) {
        userLocation = location;
        notifyItemRangeChanged(0, getItemCount());
    }

    public MatchAdapter(OnMatchClickListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    @NonNull
    @Override
    public MatchViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_match, parent, false);
        return new MatchViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MatchViewHolder holder, int position) {
        holder.bind(getItem(position), listener, userLocation);
    }

    /** Règles de comparaison utilisées par DiffUtil. */
    private static final DiffUtil.ItemCallback<Match> DIFF_CALLBACK = new DiffUtil.ItemCallback<Match>() {
        @Override
        public boolean areItemsTheSame(@NonNull Match a, @NonNull Match b) {
            return Objects.equals(a.getId(), b.getId()); // même match ?
        }

        @Override
        public boolean areContentsTheSame(@NonNull Match a, @NonNull Match b) {
            // même affichage ?
            return Objects.equals(a.getTitle(), b.getTitle())
                    && Objects.equals(a.getDateTime(), b.getDateTime())
                    && Objects.equals(a.getAddress(), b.getAddress())
                    && Objects.equals(a.getCity(), b.getCity())
                    && Objects.equals(a.getFormat(), b.getFormat())
                    && Objects.equals(a.getLevel(), b.getLevel())
                    && Objects.equals(a.getStatus(), b.getStatus())
                    && Objects.equals(a.getOrganizerPseudo(), b.getOrganizerPseudo())
                    && Objects.equals(a.getLat(), b.getLat())
                    && Objects.equals(a.getLng(), b.getLng())
                    && a.getParticipantCount() == b.getParticipantCount()
                    && a.getMaxPlayers() == b.getMaxPlayers();
        }
    };

    /** Une carte de match : garde les références vers ses vues pour ne pas refaire findViewById. */
    static class MatchViewHolder extends RecyclerView.ViewHolder {

        private final TextView format;
        private final TextView chip;
        private final TextView title;
        private final TextView date;
        private final TextView place;
        private final LinearProgressIndicator progress;
        private final TextView placesLeft;
        private final TextView count;
        private final TextView distance;
        private String boundMatchId;

        MatchViewHolder(@NonNull View itemView) {
            super(itemView);
            format = itemView.findViewById(R.id.match_format);
            chip = itemView.findViewById(R.id.match_chip);
            title = itemView.findViewById(R.id.match_title);
            date = itemView.findViewById(R.id.match_date);
            place = itemView.findViewById(R.id.match_place);
            progress = itemView.findViewById(R.id.match_progress);
            placesLeft = itemView.findViewById(R.id.match_places_left);
            count = itemView.findViewById(R.id.match_count);
            distance = itemView.findViewById(R.id.match_distance);
        }

        void bind(Match match, OnMatchClickListener listener, @Nullable Location userLocation) {
            // Distance à droite du badge, seulement si on connaît les deux positions
            Float meters = DistanceUtils.metersTo(userLocation, match);
            distance.setVisibility(meters == null ? View.GONE : View.VISIBLE);
            if (meters != null) distance.setText(DistanceUtils.format(itemView.getContext(), meters));

            Context context = itemView.getContext();
            boolean inactive = match.isCancelled() || match.isFinished();

            format.setText(MatchLabels.formatLabel(match.getFormat()));

            // Étiquette : l'état si le match n'est pas ouvert, sinon le niveau
            ChipStyle.applyStatusOrLevel(chip, match);

            title.setText(match.getTitle());
            date.setText(match.getDateTime() == null
                    ? "" : DateFormatter.formatMatchDate(context, match.getDateTime().toDate()));
            place.setText(context.getString(R.string.match_place_format, match.getAddress(), match.getCity()));

            // Barre des places : animée seulement si c'est le même match qui change (ex. 1/10 → 2/10)
            int max = (int) match.getMaxPlayers();
            int current = (int) match.getParticipantCount();
            boolean sameMatch = Objects.equals(match.getId(), boundMatchId);
            progress.setMax(Math.max(1, max));
            progress.setProgressCompat(current, sameMatch);
            progress.setIndicatorColor(ContextCompat.getColor(context,
                    match.isFull() ? R.color.progress_full : R.color.green_primary));

            if (match.isFull()) {
                placesLeft.setText(R.string.status_full);
                placesLeft.setTextColor(ContextCompat.getColor(context, R.color.warning_text));
            } else {
                int left = match.getPlacesLeft();
                placesLeft.setText(context.getResources().getQuantityString(R.plurals.match_places_left, left, left));
                placesLeft.setTextColor(ContextCompat.getColor(context, R.color.on_green_soft));
            }
            count.setText(context.getString(R.string.match_count_by, current, max, match.getOrganizerPseudo()));

            // Match annulé ou terminé : carte atténuée
            itemView.setAlpha(inactive ? 0.55f : 1f);
            itemView.setOnClickListener(v -> listener.onMatchClick(match));
            boundMatchId = match.getId();
        }
    }
}
