package com.foottogether.app.util;

import androidx.annotation.StringRes;

import com.foottogether.app.R;
import com.foottogether.app.data.model.Match;
import com.foottogether.app.data.model.User;

/**
 * Correspondance entre les valeurs stockées dans Firestore (en anglais, ex. "FIVE", "BEGINNER")
 * et les textes affichés (strings.xml, donc traduits FR/EN).
 */
public final class MatchLabels {

    private MatchLabels() {
    }

    @StringRes
    public static int formatLabel(String format) {
        if (Match.FORMAT_SEVEN.equals(format)) return R.string.format_seven;
        if (Match.FORMAT_ELEVEN.equals(format)) return R.string.format_eleven;
        return R.string.format_five;
    }

    @StringRes
    public static int levelLabel(String level) {
        if (User.LEVEL_BEGINNER.equals(level)) return R.string.level_beginner;
        if (User.LEVEL_ADVANCED.equals(level)) return R.string.level_advanced;
        return R.string.level_intermediate;
    }

    @StringRes
    public static int positionLabel(String position) {
        if (User.POSITION_GOALKEEPER.equals(position)) return R.string.position_goalkeeper;
        if (User.POSITION_DEFENDER.equals(position)) return R.string.position_defender;
        if (User.POSITION_FORWARD.equals(position)) return R.string.position_forward;
        return R.string.position_midfielder;
    }

    /** Nombre de joueurs proposé par défaut pour un format (5 contre 5 = 10 joueurs...). */
    public static int defaultPlayersFor(String format) {
        if (Match.FORMAT_SEVEN.equals(format)) return 14;
        if (Match.FORMAT_ELEVEN.equals(format)) return 22;
        return 10;
    }
}
