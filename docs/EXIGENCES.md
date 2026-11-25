# FootTogether : correspondance avec l'énoncé

Ce fichier relie chaque point demandé par l'énoncé aux fichiers qui l'implémentent, et explique comment le montrer dans l'app. C'est la base du rapport et de l'oral. Le déroulé de la démonstration est dans [`DEMO.md`](DEMO.md).

Légende : ✅ fait · 🟡 fait mais à vérifier · ⬜ à faire

| Point de l'énoncé | État | Fichiers / classes | Comment le montrer |
|---|---|---|---|
| Au moins 2 activités | ✅ | 10 activités déclarées dans `AndroidManifest.xml` : `SplashActivity`, `AuthActivity`, `MainActivity`, `MatchDetailActivity`, `EditMatchActivity`, `NotificationsActivity`, `SettingsActivity`, `FavoritesActivity`, `MiniGameActivity`, `LeaderboardActivity` ; données passées par `Intent` (`newIntent`) ; résultat renvoyé avec l'Activity Result API (`EditMatchActivity` → « Match modifié ») ; ouverture depuis une notification (`PendingIntent` + `TaskStackBuilder`) ; `parentActivityName` | Splash → Connexion → Accueil → carte → Détail. Profil → Mini-jeu / Classement. Cloche → Notifications → Détail |
| SharedPreferences | ✅ | `data/prefs/AppPrefs` (fichier privé `foottogether_prefs`) : langue, thème, ville par défaut, délai des rappels, **record du mini-jeu** ; jamais de mot de passe ; lus dans `FootTogetherApp`, `SettingsActivity`, `SearchFragment`, `CreateMatchFragment`, `ReminderScheduler`, `MiniGameActivity` | Paramètres → Thème sombre, fermer et rouvrir l'app : toujours sombre. Battre son record au mini-jeu, quitter, revenir : record conservé |
| Menu dans la barre | ✅ | `res/menu/menu_main.xml` ; `MainActivity.onCreateOptionsMenu` / `onPrepareOptionsMenu` (cloche avec pastille) / `onOptionsItemSelected` ; menus de toolbar dans Notifications (« Tout lire ») et Favoris (« Effacer l'historique ») | Menu ⋮ → Profil, Paramètres, Favoris, Déconnexion. Cloche → Notifications |
| Bundle (sauvegarde d'état) | ✅ | `onSaveInstanceState` dans : `MainActivity`, `MyMatchesFragment`, `FavoritesActivity`, `LeaderboardActivity` (onglet choisi) ; `CreateMatchFragment` (date, heure, joueurs, position) ; `SearchFragment` (liste/carte, filtres ouverts, centre et zoom de la carte) ; `HomeFragment` (position de défilement, remise quand les matchs arrivent) ; **`MiniGameActivity`** (état de la partie, score, temps restant) ; `RatePlayersSheet` (joueur en cours d'évaluation) ; arguments de `EditProfileSheet` ; état des vues restauré par Android (champs, chips, étoiles) | Mini-jeu : lancer une partie, tourner l'écran → le score et le chrono continuent. Créer : remplir puis tourner |
| Capteur(s) | ✅ | `ui/minigame/MiniGameActivity` : **accéléromètre** (`SensorManager`, `TYPE_ACCELEROMETER`) ; abonnement dans `onResume`, désabonnement dans `onPause` ; filtre passe-bas pour retirer la pesanteur, seuil de secousse, 250 ms minimum entre deux jongles ; partie de 30 s ; score en SQLite (`ScoreDao`), record en SharedPreferences ; toucher le ballon compte aussi (repli sans capteur) | Profil → Mini-jeu → Commencer → secouer le téléphone : le ballon saute et le score monte. Sur l'émulateur : ⋮ → Virtual sensors → Move, glisser vite |
| Localisation | ✅ | `util/LocationHelper` (FusedLocationProviderClient) ; permissions demandées pendant l'utilisation ; `util/DistanceUtils` ; `util/GeocoderHelper` ; repli si refus : recherche par ville (ville par défaut ou ville du profil) | Rechercher : distance, filtre « ≤ 10 km », point « Toi » sur la carte. Créer : « Utiliser ma position » |
| Appareil photo | ✅ | `ui/profile/ProfileFragment` : `TakePicturePreview()` + `PickVisualMedia` ; `util/ImageUtils` (256 px / 96 px, JPEG, Base64, EXIF) ; stockage dans Firestore | Profil → appareil photo → la photo apparaît dans le Profil, chez les participants et dans le Classement |
| Notifications | ✅ | `util/NotificationHelper` (2 canaux, permission `POST_NOTIFICATIONS` sur Android 13+, clic → Détail) ; notifications écrites **par l'auteur de l'action, dans la même transaction** (`MatchRepository`) ; « Me prévenir » pour un match complet ; `NotificationsActivity` ; rappels `ReminderScheduler` → `MatchReminderReceiver` | Mehdi rejoint le match d'Oussama → « Nouveau joueur » chez Oussama. Rappel 5 min avant un match |
| Réseau | ✅ | Firebase Auth + Cloud Firestore (`*Repository`, écoutes temps réel, **transactions**, `WriteBatch`) ; **HTTP explicite** : `util/WeatherClient` (Open-Meteo, `HttpURLConnection` + `org.json`, en arrière-plan) ; tuiles OpenStreetMap ; `util/NetworkMonitor` (écoute continue → bandeau hors ligne, boutons désactivés) ; Accueil depuis le cache SQLite hors ligne | **Démo à deux téléphones** : 1/10 → 2/10 sur les deux écrans. Détail : « Météo prévue ». Mode avion : bandeau orange |
| Service | ✅ | `service/NotificationSyncService` (service démarré, `START_NOT_STICKY`) : écoute les notifications non affichées, les affiche, met les rappels à jour | Se connecter après une action d'un autre joueur : les notifications en attente s'affichent |
| BroadcastReceiver | ✅ | `receiver/MatchReminderReceiver` (alarme → rappel) ; `receiver/BootReceiver` (`BOOT_COMPLETED` → reprogramme les rappels lus en SQLite, `goAsync()`) | Redémarrer l'émulateur : Logcat `BootReceiver` « 1 rappel(s) reprogrammé(s) » |
| Écran tactile | ✅ | onglets (`TabLayout`), barre du bas, FAB ; `RecyclerView` + `DiffUtil` ; `SwipeRefreshLayout` ; **glisser pour quitter** (`ItemTouchHelper`) ; chips de filtres ; carte osmdroid (glisser, pincer, épingles) ; feuilles du bas ; **étoiles `RatingBar`** ; toucher le ballon du mini-jeu ; retour haptique | Mes matchs → glisser une carte. Évaluer un joueur : toucher les étoiles |
| Fragments | ✅ | `LoginFragment`, `RegisterFragment` ; `HomeFragment`, `SearchFragment`, `CreateMatchFragment`, `MyMatchesFragment`, `ProfileFragment` ; `CreateMatchFragment` réutilisé dans `EditMatchActivity` ; `EditProfileSheet` et `RatePlayersSheet` (`BottomSheetDialogFragment` + Fragment Result API) ; sélecteurs date / heure | Le même formulaire sert à Créer et à Modifier un match |
| SQLite | ✅ | `data/local/FootDbHelper` (`SQLiteOpenHelper`, **version 3** ; `onUpgrade` ajoute les tables v2 puis v3 sans rien effacer) ; `ReminderDao`, `FavoritesDao`, `RecentDao`, `MatchCacheDao` (transaction, `JOIN`, index), **`ScoreDao`** (`AUTOINCREMENT`, `COUNT(*)`) ; accès en arrière-plan ; requêtes paramétrées | Favoris en mode avion. Mini-jeu : « Dernières parties : 12 · 8 · 15 » |
| Ressources | ✅ | `values/` (couleurs, thème, dimensions, textes, entiers), `values-night/`, `values-en/`, **`values-land/`**, **`values-sw600dp/`** (dimensions et colonnes tablette), **`values-sw600dp-land/`**, **`layout-land/`**, `color/`, `menu/`, polices `res/font/` (Inter, Barlow Condensed, OFL), vecteurs `drawable/` (logo, icônes, ballon, icône de notification) ; `<include>` (`view_setting_row`, `view_badge_row`) ; `<plurals>` | Logo, polices et couleurs de la maquette ; thème sombre |
| Internationalisation | ✅ | `values/strings.xml` (français, par défaut) et `values-en/strings.xml` (anglais) : **tous** les textes, vérifiés (mêmes clés dans les deux fichiers ; lint ne signale aucune traduction manquante) ; `<plurals>` (places, résultats, rappels, jongles, avis) ; choix de la langue dans Paramètres (`util/LocaleHelper.applyLanguage` = `AppCompatDelegate.setApplicationLocales`, `locales_config.xml`) ; **dates, heures, distances et moyennes au format de la langue de l'app** (`LocaleHelper.appLocale`, `DateFormatter`, `DistanceUtils`) ; notifications rédigées dans la langue de celui qui les reçoit | Paramètres → Langue → English : textes, dates (« Saturday 28 Nov »), distances (« 1.2 km ») en anglais |
| Orientation | ✅ | **`layout-land/`** : `activity_match_detail` (terrain à gauche, contenu à droite ; même liste d'ids que le portrait, vérifiée), `fragment_create_match` (deux colonnes de champs), `fragment_home` (en-tête sur une ligne) ; `values-land/integers.xml` (2 colonnes de matchs) ; décalage des barres système en paysage ; aucune perte de données à la rotation (voir Bundle) | Tourner le téléphone sur l'Accueil, le Détail et Créer |
| Portabilité | ✅ | `ConstraintLayout` / `LinearLayout` avec poids, tailles en `dp` et `sp` ; bord à bord (`WindowInsets`) ; **tablette** : `values-sw600dp/dimens.xml` (formulaires centrés, textes plus grands), 2 colonnes de matchs (3 en paysage) ; minSdk 24 : canaux de notification seulement dès API 26, permission de notification dès API 33, réglages Android adaptés à la version ; fonctionne sans accéléromètre, sans appareil photo, sans Geocoder, sans localisation | Émulateur tablette (Pixel Tablet) : grille de 2 ou 3 colonnes, formulaire Créer centré |

### Sécurité (`firestore.rules`)
- Mot de passe jamais stocké par l'app (Firebase Auth le hache côté serveur) ; SharedPreferences sans donnée sensible.
- Profil modifiable par son seul propriétaire ; match créé par l'organisateur seulement (1/N) ; participation : chacun n'ajoute ou ne retire **que son propre uid**, compteur cohérent, statut FULL exact ; organisateur seul pour modifier / annuler ; suivre un match complet : seulement son uid.
- Notifications : lues par le destinataire, créées signées de son uid (`senderId`).
- **Évaluations (lot 9)** : l'évaluateur est l'utilisateur connecté, ne s'évalue pas lui-même, les deux joueurs participent au match, le match a commencé et n'est pas annulé, notes entières de 1 à 5, une seule évaluation par paire et par match (identifiant imposé), ni modification ni suppression.
- Vérifications côté téléphone en plus (formulaires, transactions) ; SQLite en requêtes paramétrées.

### Lot 9 : fonctionnalités en plus de l'énoncé
- **Évaluations après match** : dès le coup d'envoi, un participant voit « Évaluer les joueurs » dans le Détail et note chaque autre joueur (fair-play, ponctualité, esprit d'équipe). Écrit en `WriteBatch` à deux endroits (`matches/{id}/ratings` et `users/{uid}/ratings`).
- **Badges** (Profil, `util/Badges`) : Joueur régulier (5 matchs joués), Organisateur actif (3 organisés), Fair-play (au moins 2 avis et 4/5 de moyenne) ; progression affichée.
- **Classement** (`LeaderboardActivity`) : top 20 par matchs joués ou organisés (tri Firestore sur un champ).
- **Mini-jeu** jongles (voir Capteur).

## Organisation du code

```
com.foottogether.app
├── FootTogetherApp      Application : thème et canaux de notification au démarrage
├── ui/
│   ├── splash/          SplashActivity
│   ├── auth/            AuthActivity, LoginFragment, RegisterFragment
│   ├── main/            MainActivity (bandeau hors ligne, cloche, menu ⋮, démarre le Service)
│   ├── home/            HomeFragment (cache SQLite puis temps réel)
│   ├── search/          SearchFragment (filtres, liste / carte osmdroid)
│   ├── create/          CreateMatchFragment (créer ET modifier), EditMatchActivity
│   ├── detail/          MatchDetailActivity, ParticipantAdapter, RatePlayersSheet
│   ├── mymatches/       MyMatchesFragment
│   ├── profile/         ProfileFragment (photo, badges, avis), EditProfileSheet
│   ├── notifications/   NotificationsActivity, NotificationAdapter
│   ├── settings/        SettingsActivity
│   ├── favorites/       FavoritesActivity
│   ├── minigame/        MiniGameActivity (accéléromètre)
│   ├── leaderboard/     LeaderboardActivity, LeaderboardAdapter
│   └── common/          MatchAdapter, ChipStyle
├── data/
│   ├── model/           User, Match, Participant, AppNotification, Rating
│   ├── remote/          AuthRepository, UserRepository, MatchRepository, NotificationRepository,
│   │                    RatingRepository, MatchActionException, ResultCallback
│   ├── local/           FootDbHelper, ReminderDao, FavoritesDao, RecentDao, MatchCacheDao, ScoreDao
│   └── prefs/           AppPrefs
├── service/             NotificationSyncService
├── receiver/            MatchReminderReceiver, BootReceiver
└── util/                NetworkMonitor, NotificationHelper, ReminderScheduler, LocaleHelper, Badges,
                         BackgroundTasks, LocationHelper, GeocoderHelper, DistanceUtils, WeatherClient,
                         ImageUtils, MapConfig, DateFormatter, MatchLabels, InputValidator, ErrorMessages
```

## Données

**Firestore**
- `users/{uid}` : pseudo, name, email, city, position, level, photoBase64, photoThumbBase64, matchesPlayed, matchesOrganized, createdAt. Aucun mot de passe.
- `users/{uid}/notifications/{id}` : type, matchId, matchTitle, senderId, senderPseudo, read, delivered, createdAt.
- `users/{uid}/ratings/{matchId}_{raterId}` : évaluations reçues (matchId, raterId, ratedId, fairPlay, punctuality, teamSpirit, createdAt).
- `matches/{matchId}` : title, description, dateTime, address, city, lat, lng, format, maxPlayers, level, organizerId, organizerPseudo, status (OPEN/FULL/CANCELLED), participantCount, participantIds, followerIds, createdAt, updatedAt.
- `matches/{matchId}/participants/{uid}` : pseudo, position, level, photoThumbBase64, joinedAt.
- `matches/{matchId}/ratings/{raterId}_{ratedId}` : mêmes champs que ci-dessus (qui j'ai déjà évalué).

**SQLite (`foottogether.db`, version 3)** : `reminders`, `favorites`, `recents`, `match_cache`, `scores` (_id, uid, score, played_at).

**SharedPreferences (`foottogether_prefs`)** : language, theme, default_city, reminder_minutes, minigame_record.

## Dépendances (toutes gratuites, déclarées dans `gradle/libs.versions.toml`)

| Bibliothèque | Rôle |
|---|---|
| Material Components | thème Material 3, boutons, cartes, chips, barre du bas, feuilles du bas, boîtes de dialogue |
| ConstraintLayout, RecyclerView, SwipeRefreshLayout | mises en page, listes, « tirer pour actualiser » |
| Firebase BoM + `firebase-auth` + `firebase-firestore` | comptes et base partagée en temps réel (forfait gratuit Spark) |
| `play-services-location` | position du téléphone |
| `osmdroid` | carte OpenStreetMap (sans clé ni facturation) |
| Open-Meteo (API web, pas de bibliothèque) | météo, appelée avec `HttpURLConnection` |

Aucune dépendance ajoutée depuis le lot 5 : notifications, alarmes, capteurs, SQLite et SharedPreferences font partie d'Android.

## Contrôle qualité (lot 10)
- `gradlew assembleDebug` : compile sans erreur après chaque lot.
- `gradlew lintDebug` : **0 erreur**. Corrigé : ressources inutilisées supprimées, pluriel ajouté (« 1 jongle »), `baselineAligned`. Avertissements restants assumés : attributs de police pour API 26+ (les deux versions `android:` et `app:` sont présentes, donc ça marche aussi en API 24), `ExifInterface` du système (suffisant dès API 24), petits conseils de performance de mise en page.
- Mêmes clés de texte en français et en anglais (vérifié par script).
- Layouts paysage : mêmes identifiants que les layouts portrait (vérifié par script), donc le code Java ne change pas.

## Limites connues (à dire honnêtement à l'oral)

- **Notifications sans serveur payant** : elles arrivent quand l'app est ouverte ou vient d'être quittée ; sinon à la réouverture (et dans l'écran Notifications). Un vrai « push » app fermée demanderait Cloud Functions (forfait payant Blaze).
- Les règles laissent un joueur connecté écrire une notification à un autre (signée de son uid) : envoi de notifications inutiles possible, usurpation impossible.
- Rappels `setAndAllowWhileIdle` : quelques minutes de décalage possibles. Un match modifié pendant que l'app est fermée a son rappel corrigé à la réouverture.
- Évaluations ouvertes dès le **coup d'envoi** (et non 90 min après) : plus pratique pour la démo ; en production on attendrait la fin du match.
- Badges et moyennes calculés sur le téléphone (pas de serveur) : le classement ne trie que par nombre de matchs, pas par note.
- Le record du mini-jeu est celui du téléphone (SharedPreferences), pas un record en ligne.
- Sur Android 12 et moins, une notification qui arrive quand aucun écran n'est ouvert est rédigée dans la langue du téléphone, pas forcément celle choisie dans l'app.
- La recherche filtre sur le téléphone les 50 prochains matchs ; Geocoder parfois indisponible (repli « Utiliser ma position ») ; météo seulement à 15 jours.
- **Test à deux appareils en même temps** (vrai téléphone + émulateur) : à faire avant l'oral, voir `DEMO.md`.

## Historique des lots

- **Lot 0 (préparation)** : dépendances, plugin Firebase, ressources, structure des paquets.
- **Lot 1 (authentification)** : Splash, Connexion/Inscription, profil, écran principal, menu ⋮, déconnexion.
- **Lot 2 (créer + lister)** : `MatchRepository`, formulaire de création, Accueil temps réel.
- **Lot 3 (détail + rejoindre / quitter)** : transactions, participants en temps réel, règles. Testé sur un émulateur en changeant de compte.
- **Lot 4 (profil, mes matchs, organisateur)** : photo, Mes matchs (glisser pour quitter), modifier / annuler.
- **Lot 5 (localisation, recherche, carte, météo)** : distance, Geocoder, filtres, carte osmdroid, météo HTTP.
- **Lot 6 (notifications)** : notifications dans les transactions, « Me prévenir », Service, Notifications, rappels, BootReceiver, NetworkMonitor.
- **Lot 7 (SQLite, préférences, paramètres)** : favoris, récents, cache, `AppPrefs`, Paramètres, menu complet.
- **Lot 8 (internationalisation, orientation, tablette)** : `LocaleHelper` (dates, heures et distances dans la langue de l'app), layouts paysage (Détail, Créer, Accueil), colonnes et dimensions tablette, position de défilement de l'Accueil dans le Bundle.
- **Lot 9 (mini-jeu, évaluations, badges, classement)** : `MiniGameActivity` (accéléromètre, `ScoreDao`, record), `RatePlayersSheet` + `RatingRepository` + règles, badges dans le Profil, `LeaderboardActivity`, base SQLite v3.
- **Lot 10 (relecture)** : lint, nettoyage, commentaires mis à jour, ce document complet, `DEMO.md`.
