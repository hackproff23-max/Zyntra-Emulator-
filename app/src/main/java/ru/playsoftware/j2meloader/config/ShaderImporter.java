package ru.playsoftware.j2meloader.config;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.widget.Toast;

import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import ru.playsoftware.j2meloader.R;

/** Importa shaders (arquivos soltos ou uma pasta inteira) para a pasta de shaders do emulador. */
public final class ShaderImporter {
	private ShaderImporter() {
	}

	/** Copia os arquivos escolhidos (.ini, .fsh, .vsh...) e mostra o resultado. */
	public static void importFiles(Context context, List<Uri> uris) {
		importFiles(context, uris, null);
	}

	/** Igual a importFiles, mas chama onImported (na thread principal) se algo foi copiado. */
	public static void importFiles(Context context, List<Uri> uris, Runnable onImported) {
		int copied = copyFiles(context, uris);
		report(context, copied);
		if (copied > 0 && onImported != null) {
			onImported.run();
		}
	}

	private static int copyFiles(Context context, List<Uri> uris) {
		File dir = new File(Config.getEmulatorDir() + Config.SHADERS_DIR);
		if (!dir.isDirectory() && !dir.mkdirs()) {
			return 0;
		}
		int count = 0;
		for (Uri uri : uris) {
			String name = null;
			try (Cursor c = context.getContentResolver()
					.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
				if (c != null && c.moveToFirst()) {
					name = c.getString(0);
				}
			} catch (Exception ignored) {
			}
			if (name == null || name.isEmpty()) {
				name = uri.getLastPathSegment();
			}
			if (name == null) {
				continue;
			}
			name = new File(name).getName();
			try (InputStream in = context.getContentResolver().openInputStream(uri);
				 OutputStream out = new FileOutputStream(new File(dir, name))) {
				if (in == null) {
					continue;
				}
				byte[] buf = new byte[8192];
				int n;
				while ((n = in.read(buf)) > 0) {
					out.write(buf, 0, n);
				}
				count++;
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return count;
	}

	/** Copia uma pasta inteira (com subpastas) para shaders/<nome da pasta>/ em segundo plano. */
	public static void importTree(Activity activity, Uri treeUri) {
		importTree(activity, treeUri, null);
	}

	/** Igual a importTree, mas chama onImported (na thread principal) se algo foi copiado. */
	public static void importTree(Activity activity, Uri treeUri, Runnable onImported) {
		Context appContext = activity.getApplicationContext();
		new Thread(() -> {
			int count = 0;
			try {
				DocumentFile root = DocumentFile.fromTreeUri(appContext, treeUri);
				if (root != null) {
					String name = root.getName() == null ? "shaders" : new File(root.getName()).getName();
					File dest = new File(Config.getEmulatorDir() + Config.SHADERS_DIR, name);
					if (dest.isDirectory() || dest.mkdirs()) {
						count = copyTree(appContext, root, dest, 0, new int[]{0});
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			int copied = count;
			activity.runOnUiThread(() -> {
				report(activity, copied);
				if (copied > 0 && onImported != null) {
					onImported.run();
				}
			});
		}, "ShaderImport").start();
	}

	private static int copyTree(Context context, DocumentFile dir, File dest, int depth, int[] total) {
		int count = 0;
		for (DocumentFile child : dir.listFiles()) {
			if (total[0] >= 3000 || depth > 6) {
				break;
			}
			String name = child.getName();
			if (name == null || name.startsWith(".")) {
				continue;
			}
			name = new File(name).getName();
			if (child.isDirectory()) {
				File sub = new File(dest, name);
				if (sub.isDirectory() || sub.mkdirs()) {
					count += copyTree(context, child, sub, depth + 1, total);
				}
			} else if (child.isFile() && child.length() <= 5L * 1024 * 1024) {
				try (InputStream in = context.getContentResolver().openInputStream(child.getUri());
					 OutputStream out = new FileOutputStream(new File(dest, name))) {
					if (in == null) {
						continue;
					}
					byte[] buf = new byte[8192];
					int n;
					while ((n = in.read(buf)) > 0) {
						out.write(buf, 0, n);
					}
					count++;
					total[0]++;
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		return count;
	}

	private static void report(Context context, int copied) {
		if (copied <= 0) {
			Toast.makeText(context, R.string.shaders_import_error, Toast.LENGTH_LONG).show();
			return;
		}
		ShaderScanner.Result r = ShaderScanner.scan(new File(Config.getEmulatorDir() + Config.SHADERS_DIR));
		int shaders = r.entries.size() + r.iniFiles.size();
		Toast.makeText(context, context.getString(R.string.shaders_imported_found, copied, shaders),
				Toast.LENGTH_LONG).show();
	}
}
