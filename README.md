# ⚽ FootTogether

**FootTogether** est une application Android qui met en relation les joueurs de football amateur : on crée un match près de chez soi, les autres joueurs le trouvent, le rejoignent, et tout le monde voit les inscriptions **en temps réel**.

> Projet réalisé dans le cadre du cours « Développement mobile ».
> Application **client-serveur** : plusieurs téléphones partagent les mêmes données grâce à Firebase.

**Exemple :** Oussama crée « Foot à 5, Valenciennes, samedi 15 h, 10 places ». Sur son téléphone, Mehdi voit le match apparaître immédiatement et le rejoint. Le compteur passe de **1/10 à 2/10 sur les deux écrans, automatiquement**.

---

## Sommaire

- [Fonctionnalités](#fonctionnalités)
- [Les écrans](#les-écrans)
- [Technologies](#technologies)
- [Lancer le projet](#lancer-le-projet)
- [Architecture du code](#architecture-du-code)
- [Sécurité](#sécurité)
- [Limites connues](#limites-connues)
- [Documentation](#documentation)

---

## Fonctionnalités

### Matchs
- **Créer un match** : titre, date et heure, lieu (adresse tapée ou « Utiliser ma position »), format (5, 7 ou 11 contre 11), nombre de joueurs (2 à 22), niveau recherché et description. L'organisateur est le premier inscrit.
- **Liste en temps réel** des matchs à venir, avec places restantes, distance et état (Ouvert, Complet, Annulé, Terminé).
- **Rejoindre / quitter** un match. Ces opérations passent par des **transactions Firestore** : deux joueurs ne peuvent jamais prendre la même dernière place.
- **Match complet** : bouton « Me prévenir » pour être averti si une place se libère.
- **Organisateur** : modifier ou annuler son match. Les participants sont prévenus.
- **Météo prévue** à l'heure et au lieu du match (Open-Meteo).

### Recherche et carte
- Recherche par ville, avec 5 filtres : distance, date, format, niveau et places disponibles.
- Affichage en **liste** ou sur une **carte OpenStreetMap**, avec une épingle par match et un point pour sa position.
- Si la localisation est refusée, l'app reste utilisable : la recherche se fait par ville.

### Joueur
- Inscription et connexion par e-mail et mot de passe, avec la session conservée.
- **Profil** : pseudo, nom, ville, poste et niveau. Photo prise avec l'appareil photo ou choisie dans la galerie.
- **Mes matchs** : onglets « Je participe » et « J'organise ». On peut **glisser une carte** pour quitter un match.
- **Favoris** et **matchs vus récemment**, disponibles même hors ligne.
- **Évaluations après le match** : fair-play, ponctualité et esprit d'équipe, de 1 à 5 étoiles.
- **Badges** (Joueur régulier, Organisateur actif, Fair-play) et **classement** des joueurs.
- **Mini-jeu « Jongles »** : on secoue le téléphone pour jongler pendant 30 secondes. Il utilise l'accéléromètre et garde un record.

### Notifications et confort
- Notifications quand un joueur rejoint votre match, quand un match est modifié ou annulé, ou quand une place se libère.
- **Rappel** avant chaque match, avec un délai réglable. Les rappels sont reprogrammés après un redémarrage du téléphone.
- Bandeau « Serveur momentanément inaccessible » hors ligne. L'Accueil affiche alors les derniers matchs enregistrés.
- **Paramètres** :
  - langue (français / anglais) ;
  - thème (clair / sombre) ;
  - ville par défaut ;
  - délai des rappels.
- S'adapte au **mode paysage** et aux **tablettes**.

---

## Les écrans

| Écran | Description |
|---|---|
| **Démarrage** | Logo sur fond vert. Ouvre directement l'Accueil si l'utilisateur est déjà connecté. |
| **Connexion / Inscription** | Deux onglets. Les erreurs (e-mail invalide, mot de passe trop court…) s'affichent sous chaque champ. |
| **Accueil** | « Bonjour, pseudo » puis les prochains matchs sous forme de cartes : badge de format, niveau, date, lieu, distance, barre des places restantes. Tirer vers le bas actualise la liste. |
| **Rechercher** | Champ ville, panneau de filtres repliable (« Filtres (2) »), bascule liste / carte, épingles cliquables. |
| **Créer un match** | Formulaire avec sélecteurs de date et d'heure, compteur de joueurs (− / +), chips de format et de niveau. Sur deux colonnes en paysage. |
| **Détail d'un match** | En-tête « terrain », infos, météo, distance, liste des participants en temps réel et bouton d'action en bas (Rejoindre, Quitter, Me prévenir, Évaluer les joueurs ; Modifier et Annuler pour l'organisateur). En paysage, le terrain est à gauche et le contenu à droite. |
| **Mes matchs** | Onglets « Je participe » / « J'organise ». Glisser une carte sur le côté propose de quitter le match. |
| **Profil** | En-tête foncé avec la photo, compteurs (matchs joués et organisés), badges, moyennes des avis reçus, boutons Classement et Mini-jeu. |
| **Notifications** | Historique : les non lues sont en gras avec un point vert, et « Tout lire » les marque comme lues. Toucher une notification ouvre le match. |
| **Favoris** | Onglets « Favoris » / « Vus récemment », lus depuis la base locale. |
| **Paramètres** | Langue, thème, ville par défaut, rappel avant un match, accès aux réglages de notifications d'Android. |
| **Classement** | Top 20 des joueurs (matchs joués) ou des organisateurs. |
| **Mini-jeu** | Chrono de 30 s, score, ballon qui saute à chaque secousse, record et dernières parties. |

> 📸 Pour ajouter des captures d'écran : placez-les dans `docs/screenshots/`, puis insérez-les ici, par exemple `![Accueil](docs/screenshots/accueil.png)`.

---

## Technologies

| Domaine | Choix |
|---|---|
| Langage et interface | **Java 11** + vues **XML**, Material Design 3 (pas de Kotlin, pas de Compose) |
| Android | minSdk **24** (Android 7.0), compileSdk / targetSdk 37, Gradle (Kotlin DSL) avec catalogue de versions |
| Serveur | **Firebase Authentication** (e-mail / mot de passe) et **Cloud Firestore**, forfait gratuit **Spark** |
| Carte | **osmdroid** (OpenStreetMap, sans clé) |
| Position | `play-services-location` (FusedLocationProvider) et `Geocoder` d'Android |
| Météo | API **Open-Meteo** (sans clé), appelée avec `HttpURLConnection` et lue avec `org.json` |
| Stockage local | **SQLite** (`SQLiteOpenHelper`) et **SharedPreferences** |
| Système Android | Service, BroadcastReceiver (`BOOT_COMPLETED`), AlarmManager, notifications avec canaux, accéléromètre |
| Polices | Inter et Barlow Condensed (licence SIL Open Font License) |

Le projet est **100 % gratuit** : pas de Firebase Storage, pas de Cloud Functions, pas de Google Maps, donc aucune carte bancaire nécessaire. Les photos sont compressées et stockées en Base64 dans Firestore.

---

## Lancer le projet

### 1. Prérequis
- **Android Studio** récent, avec le SDK Android 37 installé.
- Un **émulateur** (API 24 ou plus ; l'app a été testée sur « Medium Phone API 37 ») ou un téléphone Android avec le débogage USB activé.
- Un **compte Google** pour créer un projet Firebase gratuit.

### 2. Récupérer le code
```cmd
git clone <url-du-dépôt>
cd FootTogether
```
Ouvrez ensuite le dossier `FootTogether` dans Android Studio (**File → Open**). Attendez la fin de la synchronisation Gradle.

### 3. Créer votre propre projet Firebase
Le fichier de configuration Firebase n'est pas fourni : chacun utilise **son propre projet Firebase**.

1. Ouvrez la [console Firebase](https://console.firebase.google.com/), puis **Ajouter un projet**. Google Analytics n'est pas nécessaire.
2. Dans le projet, cliquez sur l'icône **Android** pour ajouter une application :
   - nom du package : **`com.foottogether.app`** (il doit être exactement celui-ci) ;
   - le certificat SHA-1 n'est pas nécessaire (connexion par e-mail / mot de passe).
3. Téléchargez le fichier **`google-services.json`** et placez-le dans le dossier **`app/`** :
   ```
   FootTogether/
   └── app/
       ├── google-services.json   ← ici
       ├── build.gradle.kts
       └── src/
   ```
4. **Authentication** → **Commencer** → onglet **Méthode de connexion** → activez **Adresse e-mail/Mot de passe**.
5. **Firestore Database** → **Créer une base de données** → choisissez une région (par exemple `eur3 (europe-west)`) et le **mode production**.

### 4. Publier les règles de sécurité Firestore
Les règles sont dans le fichier [`firestore.rules`](firestore.rules), à la racine du projet. Elles sont indispensables : sans elles, Firestore refuse toutes les lectures et écritures.

1. Console Firebase → **Firestore Database** → onglet **Règles**.
2. Remplacez tout le contenu par celui de `firestore.rules`.
3. Cliquez sur **Publier**.

> Les requêtes de l'app n'ont pas besoin d'index composite. Si Firestore en demandait un, un lien pour le créer apparaîtrait dans le **Logcat** d'Android Studio.

### 5. Lancer l'application
- Dans Android Studio, choisissez l'émulateur ou le téléphone, puis cliquez sur ▶ **Run**.
- Ou compilez en ligne de commande (Invite de commandes Windows) :
  ```cmd
  gradlew.bat assembleDebug
  ```
  L'APK est créé dans `app\build\outputs\apk\debug\`.

### 6. Conseils pour tester
- Créez **deux comptes** : sur deux appareils (émulateur + téléphone), on voit le temps réel. Sur un seul émulateur, on peut aussi changer de compte.
- **Position de l'émulateur** : bouton **⋮** à côté de l'émulateur → **Location** → choisir une ville → **Set location**.
- **Accéléromètre de l'émulateur** (mini-jeu) : **⋮** → **Virtual sensors**. On peut aussi toucher le ballon.
- Acceptez la permission de **notifications** au premier lancement (Android 13 et plus).

---

## Architecture du code

```
app/src/main/java/com/foottogether/app
├── ui/            un paquet par écran (Activities, Fragments, adapters)
├── data/
│   ├── model/     User, Match, Participant, AppNotification, Rating
│   ├── remote/    accès à Firebase (Auth, Firestore) : *Repository
│   ├── local/     SQLite : FootDbHelper + DAO (favoris, récents, cache, rappels, scores)
│   └── prefs/     SharedPreferences (AppPrefs)
├── service/       NotificationSyncService
├── receiver/      BootReceiver, MatchReminderReceiver
└── util/          réseau, localisation, images, météo, notifications, langue…
```

- Les écrans ne parlent jamais directement à Firebase : ils passent par les **repositories**.
- Les mises à jour en temps réel utilisent `addSnapshotListener`. Les écouteurs sont ouverts dans `onStart` et retirés dans `onStop`.
- Le travail lent (HTTP, SQLite, images) passe par un `ExecutorService`, puis le résultat revient sur le thread principal avec un `Handler`. Le projet n'utilise ni RxJava ni coroutines.
- Les textes sont dans `values/strings.xml` (français, langue par défaut) et `values-en/strings.xml` (anglais). Les layouts paysage sont dans `layout-land/`, les dimensions pour tablette dans `values-sw600dp/`.

---

## Sécurité

- **Aucun mot de passe n'est stocké par l'application** : Firebase Authentication le gère (haché côté serveur).
- Les **règles Firestore** (`firestore.rules`) revérifient tout côté serveur :
  - un joueur ne modifie que son propre profil ;
  - pour rejoindre ou quitter un match, il ne peut ajouter ou retirer **que son propre identifiant** ;
  - le compteur reste cohérent et ne dépasse jamais le maximum ;
  - seul l'organisateur modifie ou annule son match ;
  - les notes vont de 1 à 5, et on ne peut évaluer un joueur qu'une fois par match.
- Les requêtes SQLite sont paramétrées. Les SharedPreferences ne contiennent aucune donnée sensible.

---

## Limites connues

- Pour rester gratuit, il n'y a **pas de serveur de notifications push** : les notifications arrivent quand l'app est ouverte ou vient d'être quittée. Les autres s'affichent à la réouverture et restent dans l'écran Notifications.
- Les rappels utilisent `AlarmManager` sans alarme exacte : ils peuvent arriver avec quelques minutes de décalage.
- La météo n'est disponible que dans les 15 jours avant le match.
- Le `Geocoder` peut échouer. Dans ce cas, le match est publié sans coordonnées, et « Utiliser ma position » sert de solution de repli.

---

## Documentation

- [`docs/EXIGENCES.md`](docs/EXIGENCES.md) : correspondance entre l'énoncé du cours et les fichiers du projet.
- [`docs/DEMO.md`](docs/DEMO.md) : scénario de démonstration sur deux appareils.
