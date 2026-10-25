package ru.woesss.j2me.installer;

import android.app.Activity;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.reactivex.Single;
import ru.playsoftware.j2meloader.applist.AppListModel;
import ru.playsoftware.j2meloader.config.Config;
import ru.playsoftware.j2meloader.util.FileUtils;

/**
 * Instala (converte) todos os jogos de uma pasta e aplica um perfil
 * (config.json + VirtualKeyboardLayout de templates/NOME) em cada um.
 */
public class BatchInstaller {
	private static final String TAG = "BatchInstaller";
	private static final String CONFIG = "config.json";
	private static final String LAYOUT = "VirtualKeyboardLayout";

	private BatchInstaller() {
	}

	/** Passo 1: escolher o perfil. Chamado depois que o usuario escolheu a pasta. */
	public static void chooseProfileAndStart(Activity activity, AppListModel model, File folder) {
		if (folder == null || !folder.isDirectory()) {
			Toast.makeText(activity, "Pasta invalida", Toast.LENGTH_LONG).show();
			return;
		}
		seedBuiltInProfiles(activity);
		File[] dirs = new File(Config.getProfilesDir()).listFiles();
		List<String> names = new ArrayList<>();
		if (dirs != null) {
			for (File d : dirs) {
				if (d.isDirectory() && new File(d, CONFIG).exists()) {
					names.add(d.getName());
				}
			}
		}
		Collections.sort(names);
		if (names.isEmpty()) {
			Toast.makeText(activity, "Nenhum perfil encontrado", Toast.LENGTH_LONG).show();
			return;
		}
		List<File> games = collect(folder);
		String[] items = names.toArray(new String[0]);
		new AlertDialog.Builder(activity)
				.setTitle("Perfil para " + games.size() + " jogo(s)")
				.setItems(items, (d, which) -> start(activity, model, games, items[which]))
				.setNegativeButton(android.R.string.cancel, null)
				.show();
	}

	private static void start(Activity activity, AppListModel model, List<File> games, String profile) {
		if (games.isEmpty()) {
			Toast.makeText(activity, "Nenhum .jar encontrado na pasta", Toast.LENGTH_LONG).show();
			return;
		}
		final boolean[] cancel = {false};
		AlertDialog dialog = new AlertDialog.Builder(activity)
				.setTitle("Instalando com perfil " + profile)
				.setMessage("Iniciando...")
				.setCancelable(false)
				.setNegativeButton(android.R.string.cancel, (d, w) -> cancel[0] = true)
				.create();
		dialog.show();
		new Thread(() -> run(activity, dialog, model, games, profile, cancel)).start();
	}

	private static void run(Activity activity, AlertDialog dialog, AppListModel model,
							List<File> games, String profile, boolean[] cancel) {
		int ok = 0;
		int failed = 0;
		StringBuilder errors = new StringBuilder();
		File profileDir = new File(Config.getProfilesDir(), profile);
		copyProfileShaders(profileDir);
		int total = games.size();
		for (int i = 0; i < total; i++) {
			if (cancel[0]) {
				break;
			}
			File jar = games.get(i);
			final String msg = (i + 1) + "/" + total + "\n" + jar.getName();
			activity.runOnUiThread(() -> dialog.setMessage(msg));
			AppInstaller installer = new AppInstaller(jar, Uri.fromFile(jar), model);
			try {
				int status = Single.<Integer>create(installer::loadInfo).blockingGet();
				if (status == AppInstaller.STATUS_UNMATCHED) {
					throw new Exception("jar nao confere com o jad");
				}
				if (status != AppInstaller.STATUS_SAME) {
					int result = Single.<Integer>create(installer::install).blockingGet();
					if (result != AppInstaller.STATUS_SUCCESS) {
						throw new Exception("instalacao nao concluida");
					}
				}
				String path = installer.getAppDirName();
				if (path == null) {
					throw new Exception("pasta do jogo nao encontrada");
				}
				applyProfile(profileDir, new File(Config.getConfigsDir(), path));
				ok++;
			} catch (Throwable e) {
				Log.e(TAG, "falha em " + jar, e);
				failed++;
				errors.append("- ").append(jar.getName()).append(": ")
						.append(e.getMessage()).append('\n');
				try {
					installer.deleteTemp();
					installer.clearCache();
				} catch (Throwable ignored) {
				}
			}
		}
		final String summary = (cancel[0] ? "Cancelado.\n" : "Concluido.\n")
				+ "Instalados com perfil: " + ok + "\nFalhas: " + failed
				+ (errors.length() > 0 ? "\n\n" + errors : "");
		activity.runOnUiThread(() -> {
			dialog.setTitle("Pronto");
			dialog.setMessage(summary);
			dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setText(android.R.string.ok);
		});
	}

	/** Copia config.json (sem shader inexistente) e o layout do teclado para a pasta do jogo. */
	private static void applyProfile(File profileDir, File gameConfigDir) throws Exception {
		if (!gameConfigDir.isDirectory() && !gameConfigDir.mkdirs()) {
			throw new Exception("nao deu para criar " + gameConfigDir);
		}
		File srcConfig = new File(profileDir, CONFIG);
		JsonObject json;
		try (FileReader reader = new FileReader(srcConfig)) {
			json = new Gson().fromJson(reader, JsonObject.class);
		}
		JsonElement shader = json.get("Shader");
		if (shader != null && shader.isJsonObject()) {
			JsonElement fragment = shader.getAsJsonObject().get("fragment");
			File shaders = new File(Config.getEmulatorDir() + Config.SHADERS_DIR);
			if (fragment == null || fragment.isJsonNull()
					|| !new File(shaders, fragment.getAsString()).exists()) {
				json.remove("Shader");
			}
		}
		try (FileWriter writer = new FileWriter(new File(gameConfigDir, CONFIG))) {
			new GsonBuilder().setPrettyPrinting().create().toJson(json, writer);
		}
		File srcLayout = new File(profileDir, LAYOUT);
		File dstLayout = new File(gameConfigDir, LAYOUT);
		if (srcLayout.exists()) {
			FileUtils.copyFileUsingChannel(srcLayout, dstLayout);
		} else if (dstLayout.exists()) {
			// perfil sem teclado proprio: remove o antigo para valer o padrao
			//noinspection ResultOfMethodCallIgnored
			dstLayout.delete();
		}
	}

	/** Shaders do perfil (templates/NOME/shaders/*) vao para a pasta global de shaders. */
	private static void copyProfileShaders(File profileDir) {
		File src = new File(profileDir, "shaders");
		File[] files = src.listFiles();
		if (files == null) {
			return;
		}
		File dst = new File(Config.getEmulatorDir() + Config.SHADERS_DIR);
		//noinspection ResultOfMethodCallIgnored
		dst.mkdirs();
		for (File f : files) {
			if (f.isFile()) {
				try {
					FileUtils.copyFileUsingChannel(f, new File(dst, f.getName()));
				} catch (Exception e) {
					Log.e(TAG, "shader " + f, e);
				}
			}
		}
	}

	/** .jar e .kjx da pasta e subpastas, em ordem alfabetica. */
	private static List<File> collect(File folder) {
		List<File> out = new ArrayList<>();
		collect(folder, out);
		Collections.sort(out, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
		return out;
	}

	private static void collect(File dir, List<File> out) {
		File[] list = dir.listFiles();
		if (list == null) {
			return;
		}
		Arrays.sort(list);
		for (File f : list) {
			if (f.isDirectory()) {
				collect(f, out);
			} else {
				String n = f.getName().toLowerCase();
				if (n.endsWith(".jar") || n.endsWith(".kjx")) {
					out.add(f);
				}
			}
		}
	}

	/** Copia os perfis embutidos (assets/profiles/*) para templates/ se ainda nao existirem. */
	private static void seedBuiltInProfiles(Activity activity) {
		try {
			String[] profiles = activity.getAssets().list("profiles");
			if (profiles == null) {
				return;
			}
			for (String p : profiles) {
				File dst = new File(Config.getProfilesDir(), p);
				if (dst.exists()) {
					continue;
				}
				//noinspection ResultOfMethodCallIgnored
				dst.mkdirs();
				String[] files = activity.getAssets().list("profiles/" + p);
				if (files == null) {
					continue;
				}
				for (String name : files) {
					try (InputStream in = activity.getAssets().open("profiles/" + p + "/" + name);
						 OutputStream out = new FileOutputStream(new File(dst, name))) {
						byte[] buf = new byte[4096];
						int n;
						while ((n = in.read(buf)) > 0) {
							out.write(buf, 0, n);
						}
					}
				}
			}
		} catch (Exception e) {
			Log.e(TAG, "seedBuiltInProfiles", e);
		}
	}
}
