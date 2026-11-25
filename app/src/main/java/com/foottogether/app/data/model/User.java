package com.foottogether.app.data.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.ServerTimestamp;

/**
 * Un joueur, tel qu'il est enregistré dans Firestore : document users/{uid}.
 *
 * Firestore sait transformer automatiquement ce genre de classe en document (et l'inverse)
 * à condition d'avoir un constructeur vide et des getters/setters publics.
 *
 * IMPORTANT : aucun mot de passe ici. C'est Firebase Authentication qui le gère,
 * stocké haché sur ses serveurs ; on ne le voit jamais.
 */
public class User {

    // Valeurs possibles pour le poste et le niveau (stockées en texte dans Firestore)
    public static final String POSITION_GOALKEEPER = "GOALKEEPER";
    public static final String POSITION_DEFENDER = "DEFENDER";
    public static final String POSITION_MIDFIELDER = "MIDFIELDER";
    public static final String POSITION_FORWARD = "FORWARD";

    public static final String LEVEL_BEGINNER = "BEGINNER";
    public static final String LEVEL_INTERMEDIATE = "INTERMEDIATE";
    public static final String LEVEL_ADVANCED = "ADVANCED";

    /** Identifiant du document = uid Firebase Auth. Rempli à la lecture, pas écrit comme un champ. */
    @DocumentId
    private String uid;

    private String pseudo;
    private String name;
    private String email;
    private String city;
    private String position;
    private String level;
    /** Photo de profil : JPEG 256×256 encodé en Base64 (texte), stocké directement dans Firestore. */
    private String photoBase64;
    /** Même photo en miniature (96×96), recopiée dans les fiches participants des matchs. */
    private String photoThumbBase64;
    private long matchesPlayed;
    private long matchesOrganized;

    /** Date de création, remplie par l'horloge du serveur Firestore (pas celle du téléphone). */
    @ServerTimestamp
    private Timestamp createdAt;

    /** Constructeur vide obligatoire pour Firestore. */
    public User() {
    }

    /** Crée un nouveau joueur à l'inscription, avec des valeurs par défaut modifiables plus tard dans le profil. */
    public User(String uid, String pseudo, String name, String email) {
        this.uid = uid;
        this.pseudo = pseudo;
        this.name = name;
        this.email = email;
        this.city = "";
        this.position = POSITION_MIDFIELDER;
        this.level = LEVEL_INTERMEDIATE;
        this.matchesPlayed = 0;
        this.matchesOrganized = 0;
    }

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getPseudo() { return pseudo; }
    public void setPseudo(String pseudo) { this.pseudo = pseudo; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; }

    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }

    public String getPhotoBase64() { return photoBase64; }
    public void setPhotoBase64(String photoBase64) { this.photoBase64 = photoBase64; }

    public String getPhotoThumbBase64() { return photoThumbBase64; }
    public void setPhotoThumbBase64(String photoThumbBase64) { this.photoThumbBase64 = photoThumbBase64; }

    public long getMatchesPlayed() { return matchesPlayed; }
    public void setMatchesPlayed(long matchesPlayed) { this.matchesPlayed = matchesPlayed; }

    public long getMatchesOrganized() { return matchesOrganized; }
    public void setMatchesOrganized(long matchesOrganized) { this.matchesOrganized = matchesOrganized; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}
