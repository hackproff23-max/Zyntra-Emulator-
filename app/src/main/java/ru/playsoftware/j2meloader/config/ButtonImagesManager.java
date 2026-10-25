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

/** Importa, remove e consulta os PNGs dos botoes do teclado virtual. */
public final class ButtonImagesManager {
	private ButtonImagesManager() {
	}

	public static boolean has(String slot, boolean pressed) {
		return ButtonImages.file(slot, pressed).isFile();
	}

	/** Copies the chosen PNG into the buttons folder. Returns false if it is not a valid PNG. */
	public static boolean importPng(Context context, Uri uri, String slot, boolean pressed) {
		File dir = new File(Config.getEmulatorDir() + ButtonImages.DIR);
		if (!dir.isDirectory() && !dir.mkdirs()) {
			return false;
		}
		File tmp = new File(dir, ".import.tmp");
		try (InputStream in = context.getContentResolver().openInputStream(uri);
			 OutputStream out = new FileOutputStream(tmp)) {
			if (in == null) {
				return false;
			}
			byte[] buf = new byte[8192];
			int n;
			while ((n = in.read(buf)) > 0) {
				out.write(buf, 0, n);
			}
		} catch (IOException e) {
			//noinspection ResultOfMethodCallIgnored
			tmp.delete();
			return false;
		}
		BitmapFactory.Options o = new BitmapFactory.Options();
		o.inJustDecodeBounds = true;
		BitmapFactory.decodeFile(tmp.getPath(), o);
		if (o.outWidth <= 0 || o.outHeight <= 0 || !"image/png".equals(o.outMimeType)) {
			//noinspection ResultOfMethodCallIgnored
			tmp.delete();
			return false;
		}
		File dst = ButtonImages.file(slot, pressed);
		//noinspection ResultOfMethodCallIgnored
		dst.delete();
		return tmp.renameTo(dst);
	}

	public static void delete(String slot) {
		//noinspection ResultOfMethodCallIgnored
		ButtonImages.file(slot, false).delete();
		//noinspection ResultOfMethodCallIgnored
		ButtonImages.file(slot, true).delete();
	}
}
