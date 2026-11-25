package com.foottogether.app.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Outils pour la photo de profil.
 *
 * Firebase Storage demande le forfait payant : on stocke donc la photo DANS le document
 * Firestore, sous forme de texte Base64. Un document est limité à 1 Mo, alors on réduit
 * la photo à 256×256 en JPEG (environ 20 à 40 Ko) avant de l'encoder.
 *
 * Ces méthodes peuvent être lentes (décodage d'une grosse photo) : on les appelle
 * dans un thread d'arrière-plan, jamais sur le thread de l'interface.
 */
public final class ImageUtils {

    public static final int PHOTO_SIZE_PX = 256;
    public static final int THUMB_SIZE_PX = 96;
    private static final int JPEG_QUALITY = 80;

    private ImageUtils() {
    }

    /** Recadre l'image en carré (au centre), la réduit à sizePx et la renvoie en JPEG Base64. */
    @NonNull
    public static String toSquareBase64(@NonNull Bitmap source, int sizePx) {
        int side = Math.min(source.getWidth(), source.getHeight());
        int x = (source.getWidth() - side) / 2;
        int y = (source.getHeight() - side) / 2;
        Bitmap square = Bitmap.createBitmap(source, x, y, side, side);
        Bitmap scaled = Bitmap.createScaledBitmap(square, sizePx, sizePx, true);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, bytes);
        // NO_WRAP : pas de retours à la ligne dans le texte produit
        return Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP);
    }

    /** Texte Base64 → image affichable (null si le texte est vide ou abîmé). */
    @Nullable
    public static Bitmap fromBase64(@Nullable String base64) {
        if (base64 == null || base64.isEmpty()) return null;
        try {
            byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Lit une image de la galerie sans charger toute sa taille en mémoire (inSampleSize),
     * puis la remet à l'endroit si le téléphone l'a enregistrée tournée (données EXIF).
     */
    @Nullable
    public static Bitmap decodeFromUri(@NonNull Context context, @NonNull Uri uri, int targetPx)
            throws IOException {
        // 1. Lire seulement les dimensions
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, bounds);
        }
        // 2. Choisir un facteur de réduction (puissance de 2) pour rester proche de targetPx
        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= targetPx && bounds.outHeight / (sample * 2) >= targetPx) {
            sample *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        Bitmap bitmap;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            bitmap = BitmapFactory.decodeStream(in, null, options);
        }
        if (bitmap == null) return null;

        // 3. Rotation éventuelle indiquée dans les données EXIF de la photo
        int degrees = 0;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in != null) {
                int orientation = new ExifInterface(in).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                if (orientation == ExifInterface.ORIENTATION_ROTATE_90) degrees = 90;
                else if (orientation == ExifInterface.ORIENTATION_ROTATE_180) degrees = 180;
                else if (orientation == ExifInterface.ORIENTATION_ROTATE_270) degrees = 270;
            }
        } catch (IOException ignored) {
            // pas de données EXIF : on garde l'image telle quelle
        }
        if (degrees == 0) return bitmap;
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }
}
