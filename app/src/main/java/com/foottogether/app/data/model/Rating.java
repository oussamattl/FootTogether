package com.foottogether.app.data.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.ServerTimestamp;

/**
 * Évaluation d'un joueur par un autre joueur après un match (notes de 1 à 5).
 *
 * Elle est enregistrée à deux endroits (même WriteBatch) :
 * - matches/{matchId}/ratings/{raterId}_{ratedId} : pour savoir qui j'ai déjà évalué dans ce match ;
 * - users/{ratedId}/ratings/{matchId}_{raterId}   : pour calculer les moyennes d'un joueur (badges),
 *   sans Cloud Function ni index spécial.
 * L'identifiant du document est fixé : on ne peut évaluer un même joueur qu'une fois par match.
 */
public class Rating {

    public static final int MIN = 1;
    public static final int MAX = 5;

    private String matchId;
    private String raterId;
    private String ratedId;
    private int fairPlay;
    private int punctuality;
    private int teamSpirit;
    @ServerTimestamp
    private Timestamp createdAt;

    /** Constructeur vide obligatoire pour Firestore. */
    public Rating() {
    }

    public Rating(String matchId, String raterId, String ratedId, int fairPlay, int punctuality, int teamSpirit) {
        this.matchId = matchId;
        this.raterId = raterId;
        this.ratedId = ratedId;
        this.fairPlay = fairPlay;
        this.punctuality = punctuality;
        this.teamSpirit = teamSpirit;
    }

    public String getMatchId() { return matchId; }
    public void setMatchId(String matchId) { this.matchId = matchId; }

    public String getRaterId() { return raterId; }
    public void setRaterId(String raterId) { this.raterId = raterId; }

    public String getRatedId() { return ratedId; }
    public void setRatedId(String ratedId) { this.ratedId = ratedId; }

    public int getFairPlay() { return fairPlay; }
    public void setFairPlay(int fairPlay) { this.fairPlay = fairPlay; }

    public int getPunctuality() { return punctuality; }
    public void setPunctuality(int punctuality) { this.punctuality = punctuality; }

    public int getTeamSpirit() { return teamSpirit; }
    public void setTeamSpirit(int teamSpirit) { this.teamSpirit = teamSpirit; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}
