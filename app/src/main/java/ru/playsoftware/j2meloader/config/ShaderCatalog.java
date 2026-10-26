package ru.playsoftware.j2meloader.config;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import kotlin.io.FilesKt;
import ru.playsoftware.j2meloader.R;

/**
 * Lista todos os shaders da pasta de shaders (.ini nativo e os demais formatos reconhecidos
 * pelo {@link ShaderScanner}). Usado pela tela de configuracao e pelo menu dentro do jogo.
 */
public final class ShaderCatalog {
	private static final String TAG = ShaderCatalog.class.getSimpleName();

	private ShaderCatalog() {
	}

	/** Pasta de shaders do emulador (criada se nao existir). */
	public static File dir() {
		File dir = new File(Config.getEmulatorDir() + Config.SHADERS_DIR);
		if (!dir.exists()) {
			//noinspection ResultOfMethodCallIgnored
			dir.mkdirs();
		}
		return dir;
	}

	/**
	 * Varre a pasta e devolve os shaders ordenados por nome. O item 0 e sempre o filtro
	 * "sem shader" (fragment == null). Pode demorar: chame fora da thread principal.
	 */
	public static ArrayList<ShaderInfo> load(Context context, File dir) {
		ArrayList<ShaderInfo> shaders = new ArrayList<>();
		ShaderScanner.Result found = ShaderScanner.scan(dir);
		// 1) formato nativo .ini (pode estar em subpastas; caminhos relativos a propria pasta do .ini)
		for (File file : found.iniFiles) {
			String text;
			try {
				//noinspection CharsetObjectCanBeUsed
				text = FilesKt.readText(file, Charset.forName("UTF-8"));
			} catch (Exception e) {
				Log.e(TAG, "getText: " + file, e);
				continue;
			}
			String prefix = relativeParent(dir, file);
			String[] split = text.split("[\\n\\r]+");
			ShaderInfo info = null;
			for (String line : split) {
				if (line.startsWith("[")) {
					addIfValid(shaders, info, prefix);
					info = new ShaderInfo(line.replaceAll("[\\[\\]]", ""), "unknown");
				} else if (info != null) {
					try {
						info.set(line);
					} catch (Exception e) {
						Log.e(TAG, "load: ", e);
					}
				}
			}
			addIfValid(shaders, info, prefix);
		}
		// 2) demais formatos (.vsh/.fsh, .glsl, .shader, pares e fragments avulsos...)
		for (ShaderScanner.Entry e : found.entries) {
			ShaderInfo info = new ShaderInfo(e.name, "auto");
			info.fragment = e.fragment;
			info.vertex = e.vertex;
			shaders.add(info);
		}
		Collections.sort(shaders);
		shaders.add(0, new ShaderInfo(context.getString(R.string.identity_filter), "woesss"));
		return shaders;
	}

	/** Preenche os valores de ajuste com os padroes do shader (se ele tiver ajustes). */
	public static void ensureDefaultValues(ShaderInfo item) {
		if (item.values != null) {
			return;
		}
		float[] values = null;
		for (int i = 0; i < 4; i++) {
			if (item.settings[i] != null) {
				if (values == null) {
					values = new float[4];
				}
				values[i] = item.settings[i].def;
			}
		}
		item.values = values;
	}

	private static void addIfValid(List<ShaderInfo> list, ShaderInfo info, String prefix) {
		if (info == null || info.fragment == null) {
			return;
		}
		info.fragment = prefix + info.fragment;
		if (info.vertex != null) {
			info.vertex = prefix + info.vertex;
		}
		list.add(info);
	}

	/** Caminho da pasta do arquivo relativo a raiz de shaders, com "/" no final (ou vazio). */
	private static String relativeParent(File root, File file) {
		File parent = file.getParentFile();
		if (parent == null || parent.equals(root)) {
			return "";
		}
		String r = parent.getPath().substring(root.getPath().length()).replace('\\', '/');
		while (r.startsWith("/")) r = r.substring(1);
		return r.isEmpty() ? "" : r + "/";
	}
}
