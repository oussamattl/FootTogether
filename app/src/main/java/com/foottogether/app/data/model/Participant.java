package com.foottogether.app.data.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.ServerTimestamp;

/**
 * Un joueur inscrit à un match : document matches/{matchId}/participants/{uid}.
 * On recopie ici le pseudo, le poste et le niveau pour afficher la liste des participants
 * sans devoir relire le profil de chacun.
 */
public class Participant {

    @DocumentId
    private String uid;

    private String pseudo;
    private String position;
    private String level;
    private String photoThumbBase64;

    @ServerTimestamp
    private Timestamp joinedAt;

    /** Constructeur vide obligatoire pour Firestore. */
    public Participant() {
    }

    /** Crée la fiche participant à partir du profil du joueur. */
    public static Participant fromUser(User user) {
        Participant participant = new Participant();
        participant.uid = user.getUid();
        participant.pseudo = user.getPseudo();
        participant.position = user.getPosition();
        participant.level = user.getLevel();
        participant.photoThumbBase64 = user.getPhotoThumbBase64(); // miniature de la photo (ou null)
        return participant;
    }

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getPseudo() { return pseudo; }
    public void setPseudo(String pseudo) { this.pseudo = pseudo; }

    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; }

    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }

    public String getPhotoThumbBase64() { return photoThumbBase64; }
    public void setPhotoThumbBase64(String photoThumbBase64) { this.photoThumbBase64 = photoThumbBase64; }

    public Timestamp getJoinedAt() { return joinedAt; }
    public void setJoinedAt(Timestamp joinedAt) { this.joinedAt = joinedAt; }
}
