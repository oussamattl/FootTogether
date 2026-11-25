package com.foottogether.app.ui.common;

import android.content.Context;
import android.content.res.ColorStateList;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;

import com.foottogether.app.R;
import com.foottogether.app.data.model.Match;
import com.foottogether.app.util.MatchLabels;

/**
 * Colore les petites étiquettes (style Widget.FootTogether.SmallChip) :
 * même rendu sur les cartes de la liste et sur l'écran Détail.
 */
public final class ChipStyle {

    private ChipStyle() {
    }

    public static void apply(TextView chip, @StringRes int text,
                             @ColorRes int background, @ColorRes int textColor) {
        Context context = chip.getContext();
        chip.setText(text);
        ViewCompat.setBackgroundTintList(chip,
                ColorStateList.valueOf(ContextCompat.getColor(context, background)));
        chip.setTextColor(ContextCompat.getColor(context, textColor));
    }

    /** Étiquette d'état : Annulé / Terminé (gris), Complet (orange), sinon le niveau (vert). */
    public static void applyStatusOrLevel(TextView chip, Match match) {
        if (match.isCancelled()) {
            apply(chip, R.string.status_cancelled, R.color.neutral_chip_bg, R.color.text_muted);
        } else if (match.isFinished()) {
            apply(chip, R.string.status_finished, R.color.neutral_chip_bg, R.color.text_muted);
        } else if (match.isFull()) {
            apply(chip, R.string.status_full, R.color.warning_bg, R.color.warning_text);
        } else {
            apply(chip, MatchLabels.levelLabel(match.getLevel()), R.color.green_soft, R.color.on_green_soft);
        }
    }
}
