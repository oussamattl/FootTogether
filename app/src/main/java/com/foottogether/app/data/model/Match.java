package com.foottogether.app.data.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.ServerTimestamp;

import java.util.ArrayList;
import java.util.List;

/**
 * Un match, tel qu'il est enregistré dans Firestore : document matches/{matchId}.
 *
 * Les méthodes marquées @Exclude sont des calculs pratiques pour l'interface :
 * Firestore ne les enregistre pas comme des champs.
 */
public class Match {

    // Statut stocké dans Firestore
    public static final String STATUS_OPEN = "OPEN";
    public static final String STATUS_FULL = "FULL";
    public static final String STATUS_CANCELLED = "CANCELLED";

    // Formats
    public static final String FORMAT_FIVE = "FIVE";
    public static final String FORMAT_SEVEN = "SEVEN";
    public static final String FORMAT_ELEVEN = "ELEVEN";

    // Limites vérifiées dans le formulaire ET dans les règles Firestore
    public static final int MIN_PLAYERS = 2;
    public static final int MAX_PLAYERS = 22;

    /** Durée d'un match : « terminé » = début + 90 min dépassé (calculé côté téléphone). */
    public static final long DURATION_MILLIS = 90L * 60 * 1000;

    @DocumentId
    private String id;

    private String title;
    private String description;
    private Timestamp dateTime;
    private String address;
    private String city;
    private Double lat; // null si le lieu n'a pas pu être géolocalisé (Geocoder)
    private Double lng;
    private String format;
    private long maxPlayers;
    private String level;
    private String organizerId;
    private String organizerPseudo;
    private String status;
    private long participantCount;
    private List<String> participantIds = new ArrayList<>();
    private List<String> followerIds = new ArrayList<>();

    @ServerTimestamp
    private Timestamp createdAt;
    @ServerTimestamp
    private Timestamp updatedAt;

    /** Constructeur vide obligatoire pour Firestore. */
    public Match() {
    }

    // ===== Calculs pour l'interface (non enregistrés) =====

    @Exclude
    public boolean isCancelled() {
        return STATUS_CANCELLED.equals(status);
    }

    @Exclude
    public boolean isFull() {
        return STATUS_FULL.equals(status) || participantCount >= maxPlayers;
    }

    @Exclude
    public boolean isStarted() {
        return dateTime != null && dateTime.toDate().getTime() <= System.currentTimeMillis();
    }

    @Exclude
    public boolean isFinished() {
        return dateTime != null
                && dateTime.toDate().getTime() + DURATION_MILLIS < System.currentTimeMillis();
    }

    @Exclude
    public int getPlacesLeft() {
        return (int) Math.max(0, maxPlayers - participantCount);
    }

    // ===== Getters / setters (utilisés par Firestore) =====

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Timestamp getDateTime() { return dateTime; }
    public void setDateTime(Timestamp dateTime) { this.dateTime = dateTime; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public Double getLat() { return lat; }
    public void setLat(Double lat) { this.lat = lat; }

    public Double getLng() { return lng; }
    public void setLng(Double lng) { this.lng = lng; }

    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }

    public long getMaxPlayers() { return maxPlayers; }
    public void setMaxPlayers(long maxPlayers) { this.maxPlayers = maxPlayers; }

    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }

    public String getOrganizerId() { return organizerId; }
    public void setOrganizerId(String organizerId) { this.organizerId = organizerId; }

    public String getOrganizerPseudo() { return organizerPseudo; }
    public void setOrganizerPseudo(String organizerPseudo) { this.organizerPseudo = organizerPseudo; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public long getParticipantCount() { return participantCount; }
    public void setParticipantCount(long participantCount) { this.participantCount = participantCount; }

    public List<String> getParticipantIds() { return participantIds; }
    public void setParticipantIds(List<String> participantIds) { this.participantIds = participantIds; }

    public List<String> getFollowerIds() { return followerIds; }
    public void setFollowerIds(List<String> followerIds) { this.followerIds = followerIds; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}
