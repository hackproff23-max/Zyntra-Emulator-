package ru.playsoftware.j2meloader.util;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.Log;
import android.util.TypedValue;

import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import javax.microedition.shell.MicroActivity;

/** Imagem de fundo geral do app (importada pelo usuario nas configuracoes). */
public final class BackgroundManager {
	private static final String TAG = "BackgroundManager";
	private static final String FILE_NAME = "background_general";
	private static final int MAX_SIDE = 2000;
	public static final int DIM_WINDOW = 0x66000000;
	public static final int DIM_POPUP = 0x99000000;

	private static Bitmap cached;
	private static long cachedStamp = -1;

	private BackgroundManager() {
	}

	private static File file(Context context) {
		return new File(context.getFilesDir(), FILE_NAME);
	}

	public static synchronized Bitmap getBitmap(Context context) {
		File f = file(context);
		if (!f.exists()) {
			cached = null;
			cachedStamp = -1;
			return null;
		}
		long stamp = f.lastModified() ^ f.length();
		if (cached == null || stamp != cachedStamp) {
			cached = BitmapFactory.decodeFile(f.getAbsolutePath());
			cachedStamp = stamp;
		}
		return cached;
	}

	/** Le a imagem escolhida, reduz se for enorme e guarda dentro do app. */
	public static synchronized boolean importFrom(Context context, Uri uri) {
		try {
			BitmapFactory.Options opts = new BitmapFactory.Options();
			opts.inJustDecodeBounds = true;
			try (InputStream in = context.getContentResolver().openInputStream(uri)) {
				BitmapFactory.decodeStream(in, null, opts);
			}
			if (opts.outWidth <= 0 || opts.outHeight <= 0) {
				return false;
			}
			int sample = 1;
			while (opts.outWidth / sample > MAX_SIDE || opts.outHeight / sample > MAX_SIDE) {
				sample *= 2;
			}
			BitmapFactory.Options decode = new BitmapFactory.Options();
			decode.inSampleSize = sample;
			Bitmap bmp;
			try (InputStream in = context.getContentResolver().openInputStream(uri)) {
				bmp = BitmapFactory.decodeStream(in, null, decode);
			}
			if (bmp == null) {
				return false;
			}
			File tmp = new File(context.getFilesDir(), FILE_NAME + ".tmp");
			try (OutputStream os = new FileOutputStream(tmp)) {
				if (bmp.hasAlpha()) {
					bmp.compress(Bitmap.CompressFormat.PNG, 100, os);
				} else {
					bmp.compress(Bitmap.CompressFormat.JPEG, 92, os);
				}
			}
			File dest = file(context);
			if (dest.exists() && !dest.delete()) {
				return false;
			}
			if (!tmp.renameTo(dest)) {
				return false;
			}
			cached = null;
			cachedStamp = -1;
			return true;
		} catch (Exception e) {
			Log.e(TAG, "importFrom", e);
			return false;
		}
	}

	public static synchronized void remove(Context context) {
		//noinspection ResultOfMethodCallIgnored
		file(context).delete();
		cached = null;
		cachedStamp = -1;
	}

	/** Drawable com a imagem de fundo, ou null se nao houver imagem importada. */
	public static Drawable createDrawable(Context context, int dimColor) {
		Bitmap b = getBitmap(context);
		return b == null ? null : new ImageBackgroundDrawable(b, dimColor);
	}

	/** Poe a imagem como fundo da janela (ou volta ao fundo padrao do tema se nao houver). */
	public static void applyTo(Activity activity) {
		if (activity instanceof MicroActivity
				|| activity.getClass().getName().contains("FilePicker")) {
			return;
		}
		Drawable d = createDrawable(activity, DIM_WINDOW);
		if (d == null) {
			TypedValue tv = new TypedValue();
			activity.getTheme().resolveAttribute(android.R.attr.windowBackground, tv, true);
			if (tv.type >= TypedValue.TYPE_FIRST_COLOR_INT && tv.type <= TypedValue.TYPE_LAST_COLOR_INT) {
				d = new ColorDrawable(tv.data);
			} else if (tv.resourceId != 0) {
				d = ContextCompat.getDrawable(activity, tv.resourceId);
			}
		}
		if (d != null) {
			activity.getWindow().setBackgroundDrawable(d);
		}
	}
}
