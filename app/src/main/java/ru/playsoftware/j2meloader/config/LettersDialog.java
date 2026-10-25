package ru.playsoftware.j2meloader.config;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;

import javax.microedition.lcdui.keyboard.VirtualKeyboard;

import ru.playsoftware.j2meloader.R;

/** Dialogo para escolher a letra enviada por cada tecla (global, vale para todos os jogos). */
public final class LettersDialog {
	private static final String[] KEY_NAMES = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "*", "#"};

	private LettersDialog() {
	}

	public static void show(Context context) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		String letters = VirtualKeyboard.loadLetters(prefs);
		String[] items = new String[KEY_NAMES.length];
		for (int i = 0; i < KEY_NAMES.length; i++) {
			items[i] = KEY_NAMES[i] + "  →  " + Character.toUpperCase(letters.charAt(i));
		}
		new AlertDialog.Builder(context)
				.setTitle(R.string.vk_letters_title)
				.setItems(items, (dialog, which) -> pick(context, prefs, letters, which))
				.setNeutralButton(R.string.vk_letters_reset, (dialog, which) -> {
					prefs.edit().remove(VirtualKeyboard.PREF_LETTERS).apply();
					show(context);
				})
				.setPositiveButton(android.R.string.ok, null)
				.show();
	}

	private static void pick(Context context, SharedPreferences prefs, String letters, int slot) {
		String[] abc = new String[36];
		for (int i = 0; i < 26; i++) {
			abc[i] = String.valueOf((char) ('A' + i));
		}
		for (int i = 0; i < 10; i++) {
			abc[26 + i] = String.valueOf((char) ('0' + i));
		}
		int checked = -1;
		char cur = Character.toUpperCase(letters.charAt(slot));
		for (int i = 0; i < abc.length; i++) {
			if (abc[i].charAt(0) == cur) {
				checked = i;
				break;
			}
		}
		new AlertDialog.Builder(context)
				.setTitle(context.getString(R.string.vk_letters_pick, KEY_NAMES[slot]))
				.setSingleChoiceItems(abc, checked, (dialog, which) -> {
					StringBuilder sb = new StringBuilder(letters);
					sb.setCharAt(slot, Character.toLowerCase(abc[which].charAt(0)));
					prefs.edit().putString(VirtualKeyboard.PREF_LETTERS, sb.toString()).apply();
					dialog.dismiss();
					show(context);
				})
				.setNegativeButton(android.R.string.cancel, (dialog, which) -> show(context))
				.show();
	}
}
