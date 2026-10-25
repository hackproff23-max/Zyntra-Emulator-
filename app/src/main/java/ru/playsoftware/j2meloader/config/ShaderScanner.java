package ru.playsoftware.j2meloader.config;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Procura shaders numa pasta (com subpastas) e reconhece varios formatos:
 * <ul>
 *   <li>.ini  - formato nativo (tratado por quem chama; aqui so listamos os arquivos);</li>
 *   <li>pares .vsh/.fsh (tambem .vert/.frag e .vs/.fs) com o mesmo nome;</li>
 *   <li>fragment avulso (.fsh/.frag/.fs/.glsl) - usa o vertex padrao do emulador;</li>
 *   <li>.glsl de arquivo unico com blocos VERTEX/FRAGMENT (estilo libretro);</li>
 *   <li>.shader / .xml no formato bsnes/higan (&lt;shader&gt;&lt;vertex&gt;&lt;fragment&gt;);</li>
 *   <li>qualquer outro arquivo de texto, classificado pelo conteudo (gl_Position = vertex,
 *       gl_FragColor = fragment).</li>
 * </ul>
 * Arquivos que precisam de adaptacao sao convertidos para a pasta ".converted".
 * Nao usa nenhuma classe do Android (pode ser testado em Java puro).
 */
public final class ShaderScanner {
	public static final String CONVERTED_DIR = ".converted";
	private static final int MAX_DEPTH = 6;
	private static final int MAX_FILES = 3000;
	private static final long MAX_SIZE = 768 * 1024;
	private static final Charset UTF8 = Charset.forName("UTF-8");

	/** Um shader encontrado. Caminhos relativos a pasta raiz de shaders. vertex == null: padrao. */
	public static final class Entry {
		public final String name;
		public final String vertex;
		public final String fragment;

		Entry(String name, String vertex, String fragment) {
			this.name = name;
			this.vertex = vertex;
			this.fragment = fragment;
		}
	}

	public static final class Result {
		/** Arquivos .ini (formato nativo), com caminho absoluto. */
		public final List<File> iniFiles = new ArrayList<>();
		public final List<Entry> entries = new ArrayList<>();
	}

	private final File root;
	private final Result result = new Result();
	private final List<File> files = new ArrayList<>();
	private final Set<String> referenced = new HashSet<>();

	private ShaderScanner(File root) {
		this.root = root;
	}

	public static Result scan(File root) {
		ShaderScanner s = new ShaderScanner(root);
		s.walk(root, 0);
		s.process();
		return s.result;
	}

	private void walk(File dir, int depth) {
		File[] list = dir.listFiles();
		if (list == null || depth > MAX_DEPTH) {
			return;
		}
		for (File f : list) {
			if (files.size() >= MAX_FILES) {
				return;
			}
			String n = f.getName();
			if (n.startsWith(".")) {
				continue;
			}
			if (f.isDirectory()) {
				walk(f, depth + 1);
			} else if (f.length() <= MAX_SIZE) {
				files.add(f);
			}
		}
	}

	private void process() {
		// 1) .ini: guarda quais arquivos eles ja referenciam
		for (File f : files) {
			if (ext(f).equals("ini")) {
				result.iniFiles.add(f);
				String text = readText(f);
				File dir = f.getParentFile();
				for (String line : text.split("[\\n\\r]+")) {
					int eq = line.indexOf('=');
					if (eq < 4) continue;
					String key = line.substring(0, eq).trim();
					if (key.equals("Fragment") || key.equals("Vertex")) {
						referenced.add(rel(new File(dir, line.substring(eq + 1).trim())));
					}
				}
			}
		}
		// 2) classifica o resto
		Map<String, File> vertexByKey = new HashMap<>();
		Map<String, File> fragmentByKey = new HashMap<>();
		List<String> order = new ArrayList<>();
		for (File f : files) {
			String ext = ext(f);
			if (ext.equals("ini") || isIgnoredExt(ext) || referenced.contains(rel(f))) {
				continue;
			}
			String text = readTextIfText(f);
			if (text == null) {
				continue;
			}
			if (ext.equals("shader") || ext.equals("xml")) {
				if (text.contains("<shader") || text.contains("<fragment") || text.contains("<vertex")) {
					convertXml(f, text);
				}
				continue;
			}
			Kind kind = classify(ext, text);
			String key = rel(f);
			key = key.substring(0, key.length() - (ext.isEmpty() ? 0 : ext.length() + 1));
			switch (kind) {
				case COMBINED:
					convertCombined(f, text);
					break;
				case VERTEX:
					vertexByKey.put(key, f);
					break;
				case FRAGMENT:
					fragmentByKey.put(key, f);
					order.add(key);
					break;
				default:
					break;
			}
		}
		for (String key : order) {
			File frag = fragmentByKey.get(key);
			File vert = vertexByKey.get(key);
			result.entries.add(new Entry(displayName(key), vert == null ? null : rel(vert), rel(frag)));
		}
	}

	private enum Kind {VERTEX, FRAGMENT, COMBINED, SKIP}

	private static Kind classify(String ext, String text) {
		boolean hasVertexMacro = text.contains("VERTEX");
		boolean hasFragmentMacro = text.contains("FRAGMENT");
		if (hasVertexMacro && hasFragmentMacro
				&& (text.contains("#if defined(VERTEX)") || text.contains("#ifdef VERTEX")
				|| text.contains("#if defined (VERTEX)") || text.contains("defined(VERTEX)"))) {
			return Kind.COMBINED;
		}
		if (text.contains("gl_Position")) {
			return Kind.VERTEX;
		}
		if (text.contains("gl_FragColor") || text.contains("gl_FragData") || text.contains("void main")) {
			return Kind.FRAGMENT;
		}
		switch (ext) {
			case "vsh": case "vert": case "vs":
				return Kind.VERTEX;
			case "fsh": case "frag": case "fs": case "glsl":
				return Kind.FRAGMENT;
			default:
				return Kind.SKIP;
		}
	}

	// ---- conversoes -------------------------------------------------------------------------

	private static final String COMPAT_COMMON =
			"uniform vec2 u_texelDelta;\n"
					+ "uniform vec2 u_pixelDelta;\n"
					+ "#define TextureSize (1.0/u_texelDelta)\n"
					+ "#define InputSize (1.0/u_texelDelta)\n"
					+ "#define OutputSize (1.0/u_pixelDelta)\n"
					+ "#define FrameCount 0\n"
					+ "#define FrameDirection 1\n";

	private void convertCombined(File src, String text) {
		String body = stripVersion(text);
		String vertex = "#define VERTEX 1\n#define COMPAT_VARYING varying\n#define COMPAT_ATTRIBUTE attribute\n"
				+ "#define COMPAT_TEXTURE texture2D\n#define COMPAT_PRECISION\n"
				+ COMPAT_COMMON
				+ "#define MVPMatrix mat4(1.0)\n"
				+ "#define VertexCoord a_position\n#define TexCoord a_texcoord0\n"
				+ "attribute vec4 a_position;\nattribute vec2 a_texcoord0;\n"
				+ body;
		String fragment = "#ifdef GL_ES\nprecision mediump float;\n#endif\n"
				+ "#define FRAGMENT 1\n#define COMPAT_VARYING varying\n#define COMPAT_ATTRIBUTE attribute\n"
				+ "#define COMPAT_TEXTURE texture2D\n#define COMPAT_PRECISION\n#define FragColor gl_FragColor\n"
				+ COMPAT_COMMON
				+ "#define Source sampler0\n#define Texture sampler0\n"
				+ "uniform sampler2D sampler0;\n"
				+ body;
		writeConverted(src, vertex, fragment);
	}

	private static final Pattern TAG_VERTEX = Pattern.compile("<vertex([^>]*)>(.*?)</vertex>", Pattern.DOTALL);
	private static final Pattern TAG_FRAGMENT = Pattern.compile("<fragment([^>]*)>(.*?)</fragment>", Pattern.DOTALL);
	private static final Pattern HREF = Pattern.compile("href\\s*=\\s*\"([^\"]+)\"");
	private static final Pattern CDATA = Pattern.compile("<!\\[CDATA\\[(.*?)\\]\\]>", Pattern.DOTALL);

	private void convertXml(File src, String text) {
		String vertex = xmlBlock(TAG_VERTEX, src, text);
		String fragment = xmlBlock(TAG_FRAGMENT, src, text);
		if (fragment == null) {
			return;
		}
		fragment = stripVersion(fragment);
		// Nomes antigos do bsnes/higan ("ruby*") -> variaveis do emulador
		fragment = fragment.replaceAll("uniform\\s+\\w+\\s+ruby\\w+\\s*;", "");
		fragment = fragment.replace("rubyTextureSize", "(1.0/u_texelDelta)")
				.replace("rubyInputSize", "(1.0/u_texelDelta)")
				.replace("rubyOutputSize", "(1.0/u_pixelDelta)")
				.replace("rubyTexture", "sampler0");
		String pre = "#ifdef GL_ES\nprecision mediump float;\n#endif\n"
				+ "uniform vec2 u_texelDelta;\nuniform vec2 u_pixelDelta;\n"
				+ "uniform sampler2D sampler0;\n";
		if (!fragment.contains("varying") ) {
			pre += "varying vec2 v_texcoord0;\n";
		}
		if (vertex == null) {
			fragment = fragment.replace("gl_TexCoord[0]", "vec4(v_texcoord0, 0.0, 1.0)");
		} else {
			vertex = stripVersion(vertex);
		}
		writeConverted(src, vertex, pre + fragment);
	}

	/** Conteudo de <vertex>/<fragment>: inline (com ou sem CDATA) ou href para outro arquivo. */
	private String xmlBlock(Pattern tag, File src, String text) {
		Matcher m = tag.matcher(text);
		if (!m.find()) {
			return null;
		}
		String attrs = m.group(1);
		String inner = m.group(2);
		Matcher h = HREF.matcher(attrs);
		if (h.find()) {
			File f = new File(src.getParentFile(), h.group(1));
			return f.isFile() ? readText(f) : null;
		}
		Matcher c = CDATA.matcher(inner);
		StringBuilder sb = new StringBuilder();
		if (c.find()) {
			do {
				sb.append(c.group(1));
			} while (c.find());
			inner = sb.toString();
		}
		inner = inner.trim();
		return inner.isEmpty() ? null : inner + "\n";
	}

	private void writeConverted(File src, String vertex, String fragment) {
		String key = rel(src).replace('/', '_').replace('\\', '_');
		int dot = key.lastIndexOf('.');
		if (dot > 0) key = key.substring(0, dot);
		File dir = new File(root, CONVERTED_DIR);
		if (!dir.isDirectory() && !dir.mkdirs()) {
			return;
		}
		String vRel = null;
		try {
			if (vertex != null) {
				File v = new File(dir, key + ".vsh");
				write(v, vertex);
				vRel = rel(v);
			}
			File f = new File(dir, key + ".fsh");
			write(f, fragment);
			String name = src.getName();
			int d = name.lastIndexOf('.');
			if (d > 0) name = name.substring(0, d);
			File parent = src.getParentFile();
			String shown = parent != null && !parent.equals(root) ? parent.getName() + "/" + name : name;
			result.entries.add(new Entry(shown, vRel, rel(f)));
		} catch (IOException ignored) {
		}
	}

	private static void write(File f, String s) throws IOException {
		try (FileOutputStream out = new FileOutputStream(f)) {
			out.write(s.getBytes(UTF8));
		}
	}

	// ---- utilitarios ------------------------------------------------------------------------

	private String displayName(String key) {
		return key;
	}

	private static String stripVersion(String s) {
		return s.replaceAll("(?m)^\\s*#version[^\\n]*\\n?", "");
	}

	private String rel(File f) {
		String r = f.getPath();
		String base = root.getPath();
		if (r.startsWith(base)) {
			r = r.substring(base.length());
		}
		r = r.replace('\\', '/');
		while (r.startsWith("/")) {
			r = r.substring(1);
		}
		return normalize(r);
	}

	/** Resolve "a/../b" e "./". */
	private static String normalize(String path) {
		List<String> out = new ArrayList<>();
		for (String p : path.split("/")) {
			if (p.isEmpty() || p.equals(".")) continue;
			if (p.equals("..")) {
				if (!out.isEmpty()) out.remove(out.size() - 1);
			} else {
				out.add(p);
			}
		}
		StringBuilder sb = new StringBuilder();
		for (String p : out) {
			if (sb.length() > 0) sb.append('/');
			sb.append(p);
		}
		return sb.toString();
	}

	private static String ext(File f) {
		String n = f.getName();
		int i = n.lastIndexOf('.');
		return i < 0 ? "" : n.substring(i + 1).toLowerCase(Locale.ROOT);
	}

	private static boolean isIgnoredExt(String ext) {
		switch (ext) {
			case "png": case "jpg": case "jpeg": case "gif": case "bmp": case "webp": case "zip": case "7z":
			case "rar": case "apk": case "so": case "jar": case "txt": case "md": case "pdf": case "json":
			case "cgp": case "glslp": case "slangp": case "cg": case "slang": case "lut": case "bin":
				return true;
			default:
				return false;
		}
	}

	private static String readTextIfText(File f) {
		try (InputStream in = new FileInputStream(f)) {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] buf = new byte[8192];
			int n;
			boolean first = true;
			while ((n = in.read(buf)) > 0) {
				if (first) {
					for (int i = 0; i < Math.min(n, 1024); i++) {
						if (buf[i] == 0) return null;
					}
					first = false;
				}
				out.write(buf, 0, n);
			}
			return new String(out.toByteArray(), UTF8);
		} catch (IOException e) {
			return null;
		}
	}

	private static String readText(File f) {
		String s = readTextIfText(f);
		return s == null ? "" : s;
	}
}
