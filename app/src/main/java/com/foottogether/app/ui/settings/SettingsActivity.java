package com.foottogether.app.ui.settings;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.foottogether.app.BuildConfig;
import com.foottogether.app.R;
import com.foottogether.app.data.prefs.AppPrefs;
import com.foottogether.app.data.remote.ResultCallback;
import com.foottogether.app.util.BackgroundTasks;
import com.foottogether.app.util.LocaleHelper;
import com.foottogether.app.util.ReminderScheduler;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;

/**
 * Écran Paramètres (menu ⋮ → Paramètres). Chaque réglage est enregistré dans
 * SharedPreferences (AppPrefs) et appliqué tout de suite :
 * - langue (français / anglais / comme le téléphone) ;
 * - thème (clair / sombre / comme le téléphone) ;
 * - ville par défaut (pré-remplie dans Rechercher et Créer) ;
 * - délai des rappels avant un match (les rappels déjà programmés sont recalculés).
 */
public class SettingsActivity extends AppCompatActivity {

    // Valeurs enregistrées et textes affichés, dans le même ordre
    private static final String[] LANGUAGE_VALUES = {AppPrefs.SYSTEM, AppPrefs.LANGUAGE_FR, AppPrefs.LANGUAGE_EN};
    private static final int[] LANGUAGE_LABELS = {R.string.settings_follow_system, R.string.language_fr, R.string.language_en};

    private static final String[] THEME_VALUES = {AppPrefs.SYSTEM, AppPrefs.THEME_LIGHT, AppPrefs.THEME_DARK};
    private static final int[] THEME_LABELS = {R.string.settings_follow_system, R.string.theme_light, R.string.theme_dark};

    private static final int[] REMINDER_VALUES = {0, 5, 15, 30, 60, 180, 1440};
    private static final int[] REMINDER_LABELS = {R.string.reminder_off, R.string.reminder_5min,
            R.string.reminder_15min, R.string.reminder_30min, R.string.reminder_1h,
            R.string.reminder_3h, R.string.reminder_1day};

    private AppPrefs prefs;
    private View root;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new AppPrefs(this);
        WindowCompat.enableEdgeToEdge(getWindow());
        setContentView(R.layout.activity_settings);
        root = findViewById(R.id.settings_root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        MaterialToolbar toolbar = findViewById(R.id.settings_toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        setRowTitle(R.id.settings_language, R.string.settings_language);
        setRowTitle(R.id.settings_theme, R.string.settings_theme);
        setRowTitle(R.id.settings_city, R.string.settings_default_city);
        setRowTitle(R.id.settings_reminder, R.string.settings_reminder);
        setRowTitle(R.id.settings_system_notifications, R.string.settings_system_notifications);
        setRowValue(R.id.settings_system_notifications, getString(R.string.settings_system_notifications_hint));

        findViewById(R.id.settings_language).setOnClickListener(v -> chooseLanguage());
        findViewById(R.id.settings_theme).setOnClickListener(v -> chooseTheme());
        findViewById(R.id.settings_city).setOnClickListener(v -> editCity());
        findViewById(R.id.settings_reminder).setOnClickListener(v -> chooseReminder());
        findViewById(R.id.settings_system_notifications).setOnClickListener(v -> openSystemNotificationSettings());

        TextView version = findViewById(R.id.settings_version);
        version.setText(getString(R.string.settings_version, BuildConfig.VERSION_NAME));

        refreshValues();
    }

    /** Affiche sous chaque titre la valeur actuellement enregistrée. */
    private void refreshValues() {
        setRowValue(R.id.settings_language, getString(labelFor(LANGUAGE_VALUES, LANGUAGE_LABELS, prefs.getLanguage())));
        setRowValue(R.id.settings_theme, getString(labelFor(THEME_VALUES, THEME_LABELS, prefs.getTheme())));
        String city = prefs.getDefaultCity();
        setRowValue(R.id.settings_city, city.isEmpty() ? getString(R.string.settings_no_city) : city);
        setRowValue(R.id.settings_reminder, getString(REMINDER_LABELS[indexOf(REMINDER_VALUES, prefs.getReminderMinutes())]));
    }

    // ===== Langue et thème : la nouvelle valeur s'applique tout de suite (l'écran est recréé) =====

    private void chooseLanguage() {
        int checked = indexOf(LANGUAGE_VALUES, prefs.getLanguage());
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_language)
                .setSingleChoiceItems(labels(LANGUAGE_LABELS), checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == checked) return;
                    prefs.setLanguage(LANGUAGE_VALUES[which]);
                    LocaleHelper.applyLanguage(LANGUAGE_VALUES[which]);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void chooseTheme() {
        int checked = indexOf(THEME_VALUES, prefs.getTheme());
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_theme)
                .setSingleChoiceItems(labels(THEME_LABELS), checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == checked) return;
                    prefs.setTheme(THEME_VALUES[which]);
                    AppPrefs.applyTheme(THEME_VALUES[which]);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ===== Ville par défaut =====

    private void editCity() {
        View content = getLayoutInflater().inflate(R.layout.dialog_city, null);
        TextInputEditText input = content.findViewById(R.id.dialog_city);
        input.setText(prefs.getDefaultCity());
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_default_city)
                .setView(content)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.action_save, (dialog, which) -> {
                    prefs.setDefaultCity(input.getText() == null ? "" : input.getText().toString());
                    refreshValues();
                    showMessage(R.string.settings_saved);
                })
                .show();
    }

    // ===== Rappels =====

    private void chooseReminder() {
        int checked = indexOf(REMINDER_VALUES, prefs.getReminderMinutes());
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_reminder)
                .setSingleChoiceItems(labels(REMINDER_LABELS), checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == checked) return;
                    prefs.setReminderMinutes(REMINDER_VALUES[which]);
                    refreshValues();
                    rescheduleReminders();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Le délai a changé : on recalcule l'heure de chaque rappel enregistré en SQLite. */
    private void rescheduleReminders() {
        BackgroundTasks.run(() -> ReminderScheduler.rescheduleAllBlocking(this),
                new ResultCallback<Integer>() {
                    @Override
                    public void onSuccess(Integer count) {
                        if (isFinishing()) return;
                        if (prefs.getReminderMinutes() == 0) {
                            showMessage(R.string.reminders_disabled);
                        } else {
                            Snackbar.make(root, getResources().getQuantityString(
                                    R.plurals.reminders_rescheduled, count, count), Snackbar.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onError(Exception e) {
                        if (!isFinishing()) showMessage(R.string.error_generic);
                    }
                });
    }

    /** Ouvre la page « Notifications » de FootTogether dans les Paramètres d'Android (canaux). */
    private void openSystemNotificationSettings() {
        Intent intent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        } else {
            intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", getPackageName(), null));
        }
        startActivity(intent);
    }

    // ===== Petits outils =====

    private void setRowTitle(int rowId, @StringRes int title) {
        ((TextView) findViewById(rowId).findViewById(R.id.setting_title)).setText(title);
    }

    private void setRowValue(int rowId, String value) {
        ((TextView) findViewById(rowId).findViewById(R.id.setting_value)).setText(value);
    }

    private CharSequence[] labels(int[] labelRes) {
        CharSequence[] labels = new CharSequence[labelRes.length];
        for (int i = 0; i < labelRes.length; i++) labels[i] = getString(labelRes[i]);
        return labels;
    }

    @StringRes
    private static int labelFor(String[] values, int[] labels, String value) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(value)) return labels[i];
        }
        return labels[0];
    }

    private static int indexOf(String[] values, String value) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(value)) return i;
        }
        return 0;
    }

    private static int indexOf(int[] values, int value) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == value) return i;
        }
        return 0;
    }

    private void showMessage(@StringRes int message) {
        Snackbar.make(root, message, Snackbar.LENGTH_SHORT).show();
    }
}
