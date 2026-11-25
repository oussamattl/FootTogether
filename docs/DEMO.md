# FootTogether : scénario de démonstration

Durée visée : 8 à 10 minutes. Téléphone A = **Oussama** (émulateur sur le PC), téléphone B = **Mehdi** (vrai téléphone).
À répéter au moins une fois en entier avant l'oral.

## Préparation (la veille)

1. **Installer l'app sur le vrai téléphone** :
   - sur le téléphone : Paramètres → À propos du téléphone → toucher 7 fois « Numéro de build » (options pour les développeurs) ;
   - Paramètres → Options pour les développeurs → activer **Débogage USB** ;
   - brancher le téléphone en USB, accepter « Autoriser le débogage USB » ;
   - dans Android Studio, choisir le téléphone dans la liste des appareils, puis ▶ Run.
2. Les deux appareils doivent avoir **Internet** (même Firebase, peu importe le réseau).
3. Comptes prêts : Oussama (émulateur) et Mehdi (téléphone), et un 3ᵉ compte (Lina) pour « Me prévenir ».
4. Règles `firestore.rules` publiées dans la console (dernière version).
5. Émulateur : position réglée sur Valenciennes (⋮ → Location), notifications autorisées.
6. Mini-jeu : sur le vrai téléphone (l'accéléromètre réel est plus parlant que l'émulateur).
7. Supprimer les vieux matchs de test gênants n'est pas possible depuis l'app (on annule) : en créer de propres le jour même.

## Déroulé

| # | Qui | Action | Ce qu'on montre / dit |
|---|---|---|---|
| 1 | A | Lancer l'app | Splash, session conservée (Firebase Auth). 2 activités minimum : on en a 10 |
| 2 | A | Créer « Foot à 5 », Valenciennes, samedi 15 h, 10 places, « Utiliser ma position » | Fragment réutilisé, validation, Geocoder / localisation, `WriteBatch`. Rotation au milieu du formulaire : rien n'est perdu (Bundle, layout paysage) |
| 3 | B | Accueil | **Le match apparaît immédiatement** (écoute temps réel Firestore), distance affichée |
| 4 | B | Ouvrir le match → Rejoindre | Transaction Firestore ; « Inscription confirmée » seulement après l'accord du serveur ; notification Android sur B |
| 5 | A + B | Regarder les deux écrans | **Le compteur passe de 1/10 à 2/10 sur les deux téléphones, tout seul.** Notification « Nouveau joueur » sur A (Service) |
| 6 | B | Détail : météo, cœur favori ; mode avion | Appel HTTP Open-Meteo ; bandeau hors ligne, bouton grisé ; Favoris lisibles hors ligne (SQLite) |
| 7 | A | Modifier le match (titre, heure) | Seul l'organisateur ; règles serveur ; notification « Match modifié » sur B |
| 8 | B | Mes matchs → glisser la carte pour quitter | Écran tactile (`ItemTouchHelper`), transaction inverse, 1/10 sur A |
| 9 | A | Rechercher → filtres → carte | Chips, carte osmdroid (pincer, glisser, épingle), Bundle à la rotation |
| 10 | B | Profil → photo, badges → Classement → Mini-jeu | Appareil photo, badges, classement Firestore, **accéléromètre** (secouer), score en SQLite, record en SharedPreferences |
| 11 | A | Paramètres → English, puis Thème sombre | Internationalisation (dates et distances aussi), SharedPreferences ; remettre en français |
| 12 | — | Montrer `firestore.rules` et `docs/EXIGENCES.md` | Sécurité côté serveur ; correspondance avec l'énoncé |

Préparer en plus, si on a le temps : un match qui **a déjà commencé** où Oussama et Mehdi sont inscrits (créé 10 min avant l'oral, pour 5 min plus tard), afin de montrer « Évaluer les joueurs » (étoiles, `RatingBar`) puis le badge Fair-play.

## Si quelque chose ne marche pas pendant la démo

- **Pas de réseau sur le vrai téléphone** : faire la démo sur l'émulateur seul en changeant de compte (Oussama → Mehdi), comme pendant les tests.
- **Le match n'apparaît pas sur B** : tirer l'Accueil vers le bas (« tirer pour actualiser ») ; vérifier que les deux appareils sont en ligne.
- **PERMISSION_DENIED dans le Logcat** : les règles de la console ne sont pas les dernières ; recoller `firestore.rules` et Publier.
- **Notification absente** : vérifier qu'elle est autorisée (Paramètres → Notifications Android) ; elle est de toute façon dans la cloche.

## Phrases utiles pour l'oral

- « L'architecture est client-serveur : Firestore est le serveur partagé, chaque téléphone écoute les changements en temps réel avec `addSnapshotListener`. »
- « Rejoindre passe par une transaction : si deux joueurs prennent la dernière place en même temps, Firestore relance la seconde transaction, qui voit le match complet. »
- « Le serveur ne fait pas confiance au téléphone : les règles Firestore revérifient tout (uid, compteur, organisateur, notes de 1 à 5). »
- « Tout est gratuit : Firebase Spark, OpenStreetMap, Open-Meteo. C'est pour ça que les notifications passent par un Service et pas par un serveur de push payant : c'est une limite que j'assume. »
