package ru.playsoftware.j2meloader.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

/** Cor do contorno das letras (escolhida livremente em RGB nas configuracoes). */
public final class OutlineSettings {
	public static final String PREF_KEY = "pref_outline_color";
	public static final int DEFAULT_COLOR = 0xFF1E90FF;

	private static volatile int color = DEFAULT_COLOR;
	private static volatile int version = 0;

	private OutlineSettings() {
	}

	public static void load(Context context) {
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
		color = sp.getInt(PREF_KEY, DEFAULT_COLOR);
	}

	public static int getColor() {
		return color;
	}

	/** Muda a cada vez que a cor e salva; as telas comparam para saber se precisam redesenhar. */
	public static int getVersion() {
		return version;
	}

	public static void save(Context context, int newColor) {
		color = 0xFF000000 | newColor;
		version++;
		PreferenceManager.getDefaultSharedPreferences(context).edit().putInt(PREF_KEY, color).apply();
	}
}
