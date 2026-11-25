package com.foottogether.app.ui.notifications;

import android.graphics.Typeface;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.foottogether.app.R;
import com.foottogether.app.data.model.AppNotification;
import com.foottogether.app.util.NotificationHelper;

import java.util.Objects;

/** Liste des notifications (RecyclerView + ListAdapter/DiffUtil, comme la liste des matchs). */
public class NotificationAdapter extends ListAdapter<AppNotification, NotificationAdapter.ViewHolder> {

    public interface OnNotificationClickListener {
        void onNotificationClick(AppNotification notification);
    }

    private final OnNotificationClickListener listener;

    public NotificationAdapter(OnNotificationClickListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_notification, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AppNotification n = getItem(position);
        holder.icon.setImageResource(NotificationHelper.iconFor(n.getType()));
        holder.title.setText(NotificationHelper.titleFor(n.getType()));
        // Non lue : titre en gras + point vert
        holder.title.setTypeface(null, n.isRead() ? Typeface.NORMAL : Typeface.BOLD);
        holder.unread.setVisibility(n.isRead() ? View.INVISIBLE : View.VISIBLE);
        holder.body.setText(NotificationHelper.bodyFor(holder.itemView.getContext(), n));
        // « il y a 5 min », « hier »... (createdAt est null un court instant avant la réponse du serveur)
        long time = n.getCreatedAt() == null ? System.currentTimeMillis() : n.getCreatedAt().toDate().getTime();
        holder.time.setText(DateUtils.getRelativeTimeSpanString(time, System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS));
        holder.itemView.setOnClickListener(v -> listener.onNotificationClick(n));
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView title;
        final TextView body;
        final TextView time;
        final View unread;

        ViewHolder(View view) {
            super(view);
            icon = view.findViewById(R.id.notification_icon);
            title = view.findViewById(R.id.notification_title);
            body = view.findViewById(R.id.notification_body);
            time = view.findViewById(R.id.notification_time);
            unread = view.findViewById(R.id.notification_unread);
        }
    }

    private static final DiffUtil.ItemCallback<AppNotification> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<AppNotification>() {
                @Override
                public boolean areItemsTheSame(@NonNull AppNotification a, @NonNull AppNotification b) {
                    return Objects.equals(a.getId(), b.getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull AppNotification a, @NonNull AppNotification b) {
                    return a.isRead() == b.isRead()
                            && Objects.equals(a.getCreatedAt(), b.getCreatedAt())
                            && Objects.equals(a.getMatchTitle(), b.getMatchTitle());
                }
            };
}
