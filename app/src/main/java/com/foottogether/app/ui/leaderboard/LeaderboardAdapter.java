package com.foottogether.app.ui.leaderboard;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.foottogether.app.R;
import com.foottogether.app.data.model.User;
import com.foottogether.app.util.ImageUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Lignes du classement. La ligne du joueur connecté est surlignée en vert clair. */
public class LeaderboardAdapter extends RecyclerView.Adapter<LeaderboardAdapter.ViewHolder> {

    private final String currentUid;
    private List<User> users = new ArrayList<>();
    /** Vrai : on affiche les matchs organisés ; faux : les matchs joués. */
    private boolean organizers;

    public LeaderboardAdapter(String currentUid) {
        this.currentUid = currentUid;
    }

    /** Nouvelle liste : simple remplacement (le classement est relu en entier à chaque fois). */
    public void setUsers(List<User> users, boolean organizers) {
        this.users = users;
        this.organizers = organizers;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_leaderboard, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        User user = users.get(position);
        Context context = holder.itemView.getContext();
        boolean me = user.getUid() != null && user.getUid().equals(currentUid);

        holder.rank.setText(String.valueOf(position + 1));
        // Podium : les 3 premiers en vert
        holder.rank.setTextColor(ContextCompat.getColor(context,
                position < 3 ? R.color.green_primary : R.color.text_muted));

        String pseudo = user.getPseudo() == null ? "?" : user.getPseudo();
        holder.avatar.setText(pseudo.isEmpty() ? "?" : pseudo.substring(0, 1).toUpperCase(Locale.ROOT));
        Bitmap thumb = ImageUtils.fromBase64(user.getPhotoThumbBase64());
        holder.photo.setImageBitmap(thumb);
        holder.photo.setVisibility(thumb == null ? View.GONE : View.VISIBLE);

        holder.pseudo.setText(me ? context.getString(R.string.participant_you, pseudo) : pseudo);
        boolean hasCity = user.getCity() != null && !user.getCity().isEmpty();
        holder.city.setText(hasCity ? user.getCity() : context.getString(R.string.profile_no_city));
        holder.value.setText(String.valueOf(organizers ? user.getMatchesOrganized() : user.getMatchesPlayed()));
        holder.itemView.setBackgroundColor(me ? ContextCompat.getColor(context, R.color.green_soft) : 0);
    }

    @Override
    public int getItemCount() {
        return users.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView rank;
        final TextView avatar;
        final ImageView photo;
        final TextView pseudo;
        final TextView city;
        final TextView value;

        ViewHolder(View view) {
            super(view);
            rank = view.findViewById(R.id.rank_position);
            avatar = view.findViewById(R.id.rank_avatar);
            photo = view.findViewById(R.id.rank_photo);
            pseudo = view.findViewById(R.id.rank_pseudo);
            city = view.findViewById(R.id.rank_city);
            value = view.findViewById(R.id.rank_value);
        }
    }
}
