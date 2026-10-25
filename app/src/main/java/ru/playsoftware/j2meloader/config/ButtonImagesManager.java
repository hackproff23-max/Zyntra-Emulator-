package ru.playsoftware.j2meloader.config;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.microedition.lcdui.keyboard.ButtonImages;

/** Importa, remove e consulta as imagens dos botoes do teclado virtual. */
public final class ButtonImagesManager {
	public static final int OK = 0;
	/** Nao foi possivel gravar na pasta do emulador (permissao de armazenamento?). */
	public static final int ERR_STORAGE = 1;
	/** O arquivo escolhido nao e uma imagem valida. */
	public static final int ERR_INVALID = 2;

	private ButtonImagesManager() {
	}

	public static boolean has(String slot, boolean pressed) {
		return ButtonImages.file(slot, pressed).isFile();
	}

	/** Copies the chosen image into the buttons folder. Returns OK, ERR_STORAGE or ERR_INVALID. */
	public static int importPng(Context context, Uri uri, String slot, boolean pressed) {
		File dir = new File(Config.getEmulatorDir() + ButtonImages.DIR);
		if (!dir.isDirectory() && !dir.mkdirs()) {
			return ERR_STORAGE;
		}
		File tmp = new File(dir, ".import.tmp");
		try (InputStream in = context.getContentResolver().openInputStream(uri)) {
			if (in == null) {
				return ERR_INVALID;
			}
			try (OutputStream out = new FileOutputStream(tmp)) {
				byte[] buf = new byte[8192];
				int n;
				while ((n = in.read(buf)) > 0) {
					out.write(buf, 0, n);
				}
			}
		} catch (IOException e) {
			//noinspection ResultOfMethodCallIgnored
			tmp.delete();
			return ERR_STORAGE;
		}
		BitmapFactory.Options o = new BitmapFactory.Options();
		o.inJustDecodeBounds = true;
		BitmapFactory.decodeFile(tmp.getPath(), o);
		if (o.outWidth <= 0 || o.outHeight <= 0) {
			//noinspection ResultOfMethodCallIgnored
			tmp.delete();
			return ERR_INVALID;
		}
		File dst = ButtonImages.file(slot, pressed);
		//noinspection ResultOfMethodCallIgnored
		dst.delete();
		return tmp.renameTo(dst) ? OK : ERR_STORAGE;
	}

	public static void delete(String slot) {
		//noinspection ResultOfMethodCallIgnored
		ButtonImages.file(slot, false).delete();
		//noinspection ResultOfMethodCallIgnored
		ButtonImages.file(slot, true).delete();
	}
}
