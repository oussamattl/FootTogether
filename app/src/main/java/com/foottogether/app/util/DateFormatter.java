package com.foottogether.app.util;

import android.content.Context;
import android.text.format.DateFormat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Mise en forme des dates pour l'affichage, dans la langue de l'app (LocaleHelper).
 * Exemple en français : « Samedi 28 nov. · 15:00 » ; en anglais : « Saturday 28 Nov · 3:00 PM ».
 */
public final class DateFormatter {

    private DateFormatter() {
    }

    /** Date + heure d'un match, pour les cartes et le détail. */
    public static String formatMatchDate(Context context, Date date) {
        Locale locale = LocaleHelper.appLocale(context);
        return capitalize(new SimpleDateFormat("EEEE d MMM", locale).format(date), locale)
                + " · " + formatTime(context, date);
    }

    /** Jour seul, ex. « sam. 28 nov. 2026 ». */
    public static String formatShortDay(Context context, Date date) {
        return new SimpleDateFormat("EEE d MMM yyyy", LocaleHelper.appLocale(context)).format(date);
    }

    /** Heure seule, au format 24 h ou 12 h selon le réglage du téléphone. */
    public static String formatTime(Context context, Date date) {
        return DateFormat.getTimeFormat(context).format(date);
    }

    private static String capitalize(String text, Locale locale) {
        if (text.isEmpty()) return text;
        return text.substring(0, 1).toUpperCase(locale) + text.substring(1);
    }
}
