package com.foottogether.app.ui.detail;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.foottogether.app.R;
import com.foottogether.app.data.model.Participant;
import com.foottogether.app.util.ImageUtils;
import com.foottogether.app.util.MatchLabels;

import java.util.Locale;
import java.util.Objects;

/**
 * Adapter de la liste des participants d'un match (écran Détail).
 * Même principe que MatchAdapter : ListAdapter + DiffUtil, donc quand Mehdi rejoint,
 * seule sa ligne apparaît (avec une animation) au lieu de tout redessiner.
 */
public class ParticipantAdapter extends ListAdapter<Participant, ParticipantAdapter.ParticipantViewHolder> {

    /** uid de l'utilisateur connecté : sa ligne affiche « (toi) ». */
    private final String currentUid;
    /** uid de l'organisateur : sa ligne affiche l'étiquette ORGANISATEUR. */
    private String organizerId;

    public ParticipantAdapter(String currentUid) {
        super(DIFF_CALLBACK);
        this.currentUid = currentUid;
    }

    /** Appelé quand le match est chargé (l'organisateur est connu à ce moment-là). */
    public void setOrganizerId(String organizerId) {
        if (Objects.equals(this.organizerId, organizerId)) return;
        this.organizerId = organizerId;
        notifyItemRangeChanged(0, getItemCount());
    }

    @NonNull
    @Override
    public ParticipantViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_participant, parent, false);
        return new ParticipantViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ParticipantViewHolder holder, int position) {
        Participant participant = getItem(position);
        holder.bind(participant,
                Objects.equals(participant.getUid(), organizerId),
                Objects.equals(participant.getUid(), currentUid));
    }

    private static final DiffUtil.ItemCallback<Participant> DIFF_CALLBACK = new DiffUtil.ItemCallback<Participant>() {
        @Override
        public boolean areItemsTheSame(@NonNull Participant a, @NonNull Participant b) {
            return Objects.equals(a.getUid(), b.getUid());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Participant a, @NonNull Participant b) {
            return Objects.equals(a.getPseudo(), b.getPseudo())
                    && Objects.equals(a.getPosition(), b.getPosition())
                    && Objects.equals(a.getLevel(), b.getLevel())
                    && Objects.equals(a.getPhotoThumbBase64(), b.getPhotoThumbBase64());
        }
    };

    static class ParticipantViewHolder extends RecyclerView.ViewHolder {

        private final TextView avatar;
        private final ImageView photo;
        private final TextView pseudo;
        private final TextView info;
        private final TextView organizerBadge;

        ParticipantViewHolder(@NonNull View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.participant_avatar);
            photo = itemView.findViewById(R.id.participant_photo);
            pseudo = itemView.findViewById(R.id.participant_pseudo);
            info = itemView.findViewById(R.id.participant_info);
            organizerBadge = itemView.findViewById(R.id.participant_organizer);
        }

        void bind(Participant participant, boolean isOrganizer, boolean isMe) {
            Context context = itemView.getContext();
            String name = participant.getPseudo() == null ? "?" : participant.getPseudo();

            // Initiale dans un rond : foncé + lime pour l'organisateur, vert clair pour les autres
            avatar.setText(name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(Locale.ROOT));
            ViewCompat.setBackgroundTintList(avatar, ColorStateList.valueOf(
                    ContextCompat.getColor(context, isOrganizer ? R.color.ink : R.color.green_soft)));
            avatar.setTextColor(ContextCompat.getColor(context,
                    isOrganizer ? R.color.lime_accent : R.color.on_green_soft));

            // Miniature de la photo (petite, donc rapide à décoder ici)
            Bitmap thumbnail = ImageUtils.fromBase64(participant.getPhotoThumbBase64());
            photo.setImageBitmap(thumbnail);
            photo.setVisibility(thumbnail == null ? View.GONE : View.VISIBLE);

            pseudo.setText(isMe ? context.getString(R.string.participant_you, name) : name);
            info.setText(context.getString(R.string.participant_info,
                    context.getString(MatchLabels.positionLabel(participant.getPosition())),
                    context.getString(MatchLabels.levelLabel(participant.getLevel()))));
            organizerBadge.setVisibility(isOrganizer ? View.VISIBLE : View.GONE);
        }
    }
}
