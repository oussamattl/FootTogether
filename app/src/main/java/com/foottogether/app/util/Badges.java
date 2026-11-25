package com.foottogether.app.util;

import com.foottogether.app.data.model.Rating;
import com.foottogether.app.data.model.User;

import java.util.List;

/**
 * Badges du joueur (lot 9), calculés sur le téléphone à partir de son profil et des évaluations reçues :
 * - Joueur régulier : au moins 5 matchs rejoints ;
 * - Organisateur actif : au moins 3 matchs organisés ;
 * - Fair-play : au moins 2 évaluations, avec une moyenne de fair-play d'au moins 4 sur 5.
 */
public final class Badges {

    public static final int REGULAR_MIN_MATCHES = 5;
    public static final int ORGANIZER_MIN_MATCHES = 3;
    public static final int FAIR_PLAY_MIN_RATINGS = 2;
    public static final double FAIR_PLAY_MIN_AVERAGE = 4.0;

    private Badges() {
    }

    /** Moyennes des évaluations reçues par un joueur. */
    public static class Summary {
        public final int count;
        public final double fairPlay;
        public final double punctuality;
        public final double teamSpirit;

        private Summary(int count, double fairPlay, double punctuality, double teamSpirit) {
            this.count = count;
            this.fairPlay = fairPlay;
            this.punctuality = punctuality;
            this.teamSpirit = teamSpirit;
        }

        public static Summary of(List<Rating> ratings) {
            if (ratings.isEmpty()) return new Summary(0, 0, 0, 0);
            double fair = 0, punctual = 0, spirit = 0;
            for (Rating r : ratings) {
                fair += r.getFairPlay();
                punctual += r.getPunctuality();
                spirit += r.getTeamSpirit();
            }
            int n = ratings.size();
            return new Summary(n, fair / n, punctual / n, spirit / n);
        }
    }

    public static boolean isRegular(User user) {
        return user.getMatchesPlayed() >= REGULAR_MIN_MATCHES;
    }

    public static boolean isActiveOrganizer(User user) {
        return user.getMatchesOrganized() >= ORGANIZER_MIN_MATCHES;
    }

    public static boolean isFairPlay(Summary summary) {
        return summary.count >= FAIR_PLAY_MIN_RATINGS && summary.fairPlay >= FAIR_PLAY_MIN_AVERAGE;
    }
}
