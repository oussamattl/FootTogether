package com.foottogether.app.util;

import android.content.Context;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.ConfigurationCompat;
import androidx.core.os.LocaleListCompat;

import com.foottogether.app.data.prefs.AppPrefs;

import java.util.Locale;

/**
 * Langue de l'application (internationalisation).
 *
 * L'utilisateur peut choisir une langue pour l'app différente de celle du téléphone (Paramètres).
 * Attention : Locale.getDefault() donne la langue du TÉLÉPHONE sur les anciens Android.
 * Pour formater dates et nombres, on demande donc la langue de l'écran affiché (appLocale).
 */
public final class LocaleHelper {

    private LocaleHelper() {
    }

    /**
     * Applique une langue à toute l'app : Android choisit alors values/strings.xml (français)
     * ou values-en/strings.xml (anglais), et recrée les écrans ouverts.
     *
     * @param language AppPrefs.SYSTEM (comme le téléphone), "fr" ou "en"
     */
    public static void applyLanguage(String language) {
        LocaleListCompat locales = AppPrefs.SYSTEM.equals(language)
                ? LocaleListCompat.getEmptyLocaleList()
                : LocaleListCompat.forLanguageTags(language);
        AppCompatDelegate.setApplicationLocales(locales);
    }

    /** Langue réellement utilisée par cet écran (celle choisie dans l'app, sinon celle du téléphone). */
    public static Locale appLocale(Context context) {
        Locale locale = ConfigurationCompat.getLocales(context.getResources().getConfiguration()).get(0);
        return locale != null ? locale : Locale.getDefault();
    }
}
