/*
 * Imagens PNG personalizadas para os botoes do teclado virtual.
 *
 * Arquivos em <pasta do emulador>/buttons/:
 *   all.png, all_pressed.png      -> usados por todos os botoes sem imagem propria
 *   <botao>.png, <botao>_pressed.png (ex.: fire.png, up_pressed.png)
 */

package javax.microedition.lcdui.keyboard;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;

import javax.microedition.util.ContextHolder;

import ru.playsoftware.j2meloader.config.Config;

public final class ButtonImages {
	public static final String DIR = "/buttons/";
	public static final String SLOT_ALL = "all";

	/** Nomes de arquivo, na mesma ordem dos indices KEY_* do VirtualKeyboard. */
	public static final String[] KEY_SLOTS = {
			"num1", "num2", "num3", "num4", "num5", "num6", "num7", "num8", "num9",
			"num0", "star", "pound", "soft_left", "soft_right", "d", "c",
			"up_left", "up", "up_right", "left", "right",
			"down_left", "down", "down_right", "fire", "a", "b", "menu"
	};

	/** Rotulos mostrados na tela de configuracao, na mesma ordem de KEY_SLOTS. */
	public static final String[] KEY_LABELS = {
			"1", "2", "3", "4", "5", "6", "7", "8", "9",
			"0", "*", "#", "Soft L", "Soft R", "D", "C",
			"\u2196", "\u2191", "\u2197", "\u2190", "\u2192",
			"\u2199", "\u2193", "\u2198", "Fire (F)", "A", "B", "Menu (M)"
	};

	private final Bitmap[] normal = new Bitmap[KEY_SLOTS.length];
	private final Bitmap[] pressed = new Bitmap[KEY_SLOTS.length];
	private boolean any;

	private ButtonImages() {
		int maxSize = Math.max(128,
				Math.min(ContextHolder.getDisplayWidth(), ContextHolder.getDisplayHeight()) / 3);
		Bitmap allNormal = decode(file(SLOT_ALL, false), maxSize);
		Bitmap allPressed = decode(file(SLOT_ALL, true), maxSize);
		for (int i = 0; i < KEY_SLOTS.length; i++) {
			Bitmap ownNormal = decode(file(KEY_SLOTS[i], false), maxSize);
			Bitmap ownPressed = decode(file(KEY_SLOTS[i], true), maxSize);
			pressed[i] = ownPressed != null ? ownPressed : (ownNormal == null ? allPressed : null);
			normal[i] = ownNormal != null ? ownNormal : allNormal;
			if (normal[i] == null) {
				normal[i] = pressed[i];
			}
			if (normal[i] != null) {
				any = true;
			}
		}
	}

	/** Returns null when there are no images to use. */
	public static ButtonImages load() {
		ButtonImages images = new ButtonImages();
		return images.any ? images : null;
	}

	public static File file(String slot, boolean pressed) {
		return new File(Config.getEmulatorDir() + DIR + slot + (pressed ? "_pressed" : "") + ".png");
	}

	public Bitmap get(int index, boolean selected) {
		if (index < 0 || index >= normal.length) {
			return null;
		}
		if (selected && pressed[index] != null) {
			return pressed[index];
		}
		return normal[index];
	}

	/** True when the key is pressed but has no "pressed" image, so the normal one is darkened. */
	public boolean isDimmed(int index, boolean selected) {
		return selected && index >= 0 && index < pressed.length && pressed[index] == null;
	}

	private static Bitmap decode(File f, int maxSize) {
		if (!f.isFile()) {
			return null;
		}
		BitmapFactory.Options o = new BitmapFactory.Options();
		o.inJustDecodeBounds = true;
		BitmapFactory.decodeFile(f.getPath(), o);
		if (o.outWidth <= 0 || o.outHeight <= 0) {
			return null;
		}
		int side = Math.max(o.outWidth, o.outHeight);
		int sample = 1;
		while (side / (sample * 2) >= maxSize) {
			sample *= 2;
		}
		o.inJustDecodeBounds = false;
		o.inSampleSize = sample;
		return BitmapFactory.decodeFile(f.getPath(), o);
	}
}
