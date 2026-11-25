package com.foottogether.app.data.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.ServerTimestamp;

/**
 * Une notification, enregistrée dans Firestore : users/{destinataire}/notifications/{id}.
 *
 * Pas de serveur payant (Cloud Functions) : c'est le téléphone de l'AUTEUR de l'action
 * qui écrit la notification chez le destinataire (ex. Mehdi rejoint → notification chez
 * l'organisateur). Le téléphone du destinataire l'écoute et l'affiche (NotificationSyncService).
 *
 * On n'enregistre pas de phrase toute faite : seulement le type, le titre du match et le
 * pseudo de l'auteur. Chaque téléphone écrit la phrase dans SA langue (NotificationHelper).
 */
public class AppNotification {

    public static final String TYPE_JOIN_CONFIRMED = "JOIN_CONFIRMED";
    public static final String TYPE_NEW_PARTICIPANT = "NEW_PARTICIPANT";
    public static final String TYPE_MATCH_CANCELLED = "MATCH_CANCELLED";
    public static final String TYPE_MATCH_UPDATED = "MATCH_UPDATED";
    public static final String TYPE_SPOT_AVAILABLE = "SPOT_AVAILABLE";

    @DocumentId
    private String id;

    private String type;
    private String matchId;
    private String matchTitle;
    private String senderId;
    private String senderPseudo;
    /** Lue dans l'écran Notifications (le point vert disparaît). */
    private boolean read;
    /** Déjà affichée dans la barre de notifications d'Android (pour ne pas l'afficher deux fois). */
    private boolean delivered;
    @ServerTimestamp
    private Timestamp createdAt;

    /** Constructeur vide obligatoire pour Firestore. */
    public AppNotification() {
    }

    public AppNotification(String type, String matchId, String matchTitle,
                           String senderId, String senderPseudo) {
        this.type = type;
        this.matchId = matchId;
        this.matchTitle = matchTitle;
        this.senderId = senderId;
        this.senderPseudo = senderPseudo;
        this.read = false;
        this.delivered = false;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getMatchId() { return matchId; }
    public void setMatchId(String matchId) { this.matchId = matchId; }

    public String getMatchTitle() { return matchTitle; }
    public void setMatchTitle(String matchTitle) { this.matchTitle = matchTitle; }

    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public String getSenderPseudo() { return senderPseudo; }
    public void setSenderPseudo(String senderPseudo) { this.senderPseudo = senderPseudo; }

    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }

    public boolean isDelivered() { return delivered; }
    public void setDelivered(boolean delivered) { this.delivered = delivered; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}
