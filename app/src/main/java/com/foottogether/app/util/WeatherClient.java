package com.foottogether.app.util;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.foottogether.app.R;
import com.foottogether.app.data.remote.ResultCallback;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Météo prévue à l'heure du match, avec l'API gratuite Open-Meteo (sans clé).
 *
 * C'est l'appel HTTP « à la main » demandé par l'énoncé, en plus de Firebase :
 * 1. on construit l'URL avec les coordonnées et la date du match ;
 * 2. HttpURLConnection envoie une requête GET (en arrière-plan) ;
 * 3. on lit la réponse JSON avec org.json et on garde l'heure la plus proche du match.
 *
 * Exemple de réponse (simplifiée) :
 * {"hourly": {"time": [1764324000, ...], "temperature_2m": [9.4, ...], "weather_code": [61, ...]}}
 */
public final class WeatherClient {

    private static final String BASE_URL = "https://api.open-meteo.com/v1/forecast";
    /** Open-Meteo prévoit jusqu'à 16 jours : au-delà, pas de prévision. */
    public static final long MAX_FORECAST_MILLIS = 15L * 24 * 60 * 60 * 1000;
    private static final int TIMEOUT_MS = 8000;

    /** Une prévision : température (°C) et code météo WMO (0 = ciel clair, 61 = pluie...). */
    public static final class Forecast {
        public final double temperature;
        public final int code;

        Forecast(double temperature, int code) {
            this.temperature = temperature;
            this.code = code;
        }
    }

    private WeatherClient() {
    }

    /** onSuccess reçoit null si le match est trop loin dans le temps pour une prévision. */
    public static void fetch(double lat, double lng, Date matchDate, ResultCallback<Forecast> callback) {
        if (matchDate.getTime() - System.currentTimeMillis() > MAX_FORECAST_MILLIS) {
            callback.onSuccess(null);
            return;
        }
        BackgroundTasks.run(() -> fetchBlocking(lat, lng, matchDate), callback);
    }

    /** Appel réseau BLOQUANT : à n'appeler qu'en arrière-plan. */
    @Nullable
    private static Forecast fetchBlocking(double lat, double lng, Date matchDate) throws Exception {
        SimpleDateFormat day = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        day.setTimeZone(TimeZone.getTimeZone("UTC"));
        String date = day.format(matchDate);
        // Locale.US : un point décimal dans l'URL (« 50.3570 »), même sur un téléphone en français
        String url = String.format(Locale.US,
                "%s?latitude=%.4f&longitude=%.4f&hourly=temperature_2m,weather_code"
                        + "&timezone=GMT&timeformat=unixtime&start_date=%s&end_date=%s",
                BASE_URL, lat, lng, date, date);

        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        try {
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) throw new IOException("HTTP " + status);
            JSONObject json = new JSONObject(readAll(connection));
            return closestHour(json.getJSONObject("hourly"), matchDate.getTime() / 1000);
        } finally {
            connection.disconnect();
        }
    }

    private static String readAll(HttpURLConnection connection) throws IOException {
        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) body.append(line);
        }
        return body.toString();
    }

    /** Parcourt les heures de la journée et garde celle la plus proche du coup d'envoi. */
    @Nullable
    private static Forecast closestHour(JSONObject hourly, long matchSeconds) throws Exception {
        JSONArray times = hourly.getJSONArray("time");
        JSONArray temperatures = hourly.getJSONArray("temperature_2m");
        JSONArray codes = hourly.getJSONArray("weather_code");
        int best = -1;
        long bestGap = Long.MAX_VALUE;
        for (int i = 0; i < times.length(); i++) {
            long gap = Math.abs(times.getLong(i) - matchSeconds);
            if (gap < bestGap && !temperatures.isNull(i) && !codes.isNull(i)) {
                best = i;
                bestGap = gap;
            }
        }
        if (best < 0) return null;
        return new Forecast(temperatures.getDouble(best), codes.getInt(best));
    }

    /** Code météo WMO → texte traduit (strings.xml). */
    @StringRes
    public static int describe(int code) {
        if (code == 0) return R.string.weather_clear;
        if (code <= 2) return R.string.weather_partly_cloudy;
        if (code == 3) return R.string.weather_cloudy;
        if (code == 45 || code == 48) return R.string.weather_fog;
        if (code >= 51 && code <= 57) return R.string.weather_drizzle;
        if ((code >= 61 && code <= 67) || (code >= 80 && code <= 82)) return R.string.weather_rain;
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return R.string.weather_snow;
        if (code >= 95) return R.string.weather_storm;
        return R.string.weather_cloudy;
    }
}
