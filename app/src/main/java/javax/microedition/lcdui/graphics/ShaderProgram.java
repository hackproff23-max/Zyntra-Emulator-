/*
 * Copyright 2020-2023 Yury Kharchenko
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package javax.microedition.lcdui.graphics;

import static android.opengl.GLES20.*;

import android.opengl.GLU;
import android.util.Log;

import java.nio.FloatBuffer;

import javax.microedition.util.ContextHolder;

import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.config.ShaderInfo;
import ru.playsoftware.j2meloader.util.FileUtils;

public class ShaderProgram {
	private static final String TAG = ShaderProgram.class.getName();
	private static final String VERTEX = "shaders/simple.vsh";
	private static final String FRAGMENT = "shaders/simple.fsh";

	private int programId = -1;

	public int aTexCoord;
	public int uTextureUnit;
	public int aPosition;
	public int uTexelDelta;
	public int uSetting;
	public int uPixelDelta;
	public int uMvp;
	public int uTextureSize;
	public int uInputSize;
	public int uOutputSize;

	public ShaderProgram(ShaderInfo shader) {
		if (shader != null) {
			String vertex = shader.vertex;
			String fragment = shader.fragment;
			if (fragment != null) {
				// sem vertex proprio (shader so de fragment): usa o vertex padrao do emulador
				String vertexCode = vertex != null ? FileUtils.getText(shader.dir + vertex)
						: ContextHolder.getAssetAsString(VERTEX);
				String fragmentCode = FileUtils.getText(shader.dir + fragment);
				vertexCode = prepare(vertexCode, "VERTEX");
				fragmentCode = prepare(fragmentCode, "FRAGMENT");
				programId = createProgram(vertexCode, fragmentCode);
				if (programId != -1) {
					glReleaseShaderCompiler();
					return;
				}
			}
			ContextHolder.getActivity().toast(R.string.msg_error_loading_shader);
		}
		String vertexCode = ContextHolder.getAssetAsString(VERTEX);
		String fragmentCode = ContextHolder.getAssetAsString(FRAGMENT);
		int program = createProgram(vertexCode, fragmentCode);
		programId = program;
		glReleaseShaderCompiler();
		if (program == -1) {
			throw new RuntimeException("Init shader program error: see log for detail");
		}
	}

	/**
	 * Adapts desktop/RetroArch style GLSL to GLSL ES 1.00 (GLES 2.0):
	 * removes #version lines and adds the VERTEX/FRAGMENT stage define
	 * used by single-file RetroArch shaders ("#if defined(VERTEX)").
	 */
	private static String prepare(String code, String stage) {
		if (code == null) {
			return "";
		}
		code = code.replaceAll("(?m)^\\s*#version.*$", "");
		return "#define " + stage + "\n" + code;
	}

	private static int attrib(int program, String... names) {
		for (String n : names) {
			int l = glGetAttribLocation(program, n);
			if (l >= 0) return l;
		}
		return -1;
	}

	private static int uniform(int program, String... names) {
		for (String n : names) {
			int l = glGetUniformLocation(program, n);
			if (l >= 0) return l;
		}
		return -1;
	}

	/** Libera o programa GL (chamar na thread do GL, depois de trocar para outro programa). */
	public void release() {
		if (programId > 0) {
			glDeleteProgram(programId);
		}
		programId = -1;
	}

	public void setOutputSize(float width, float height) {
		if (uOutputSize >= 0) {
			glUniform2f(uOutputSize, width, height);
		}
	}

	public int createProgram(String vertex, String fragment) {
		int vertexId = loadShader(GL_VERTEX_SHADER, vertex);
		int fragmentId = loadShader(GL_FRAGMENT_SHADER, fragment);

		int program = glCreateProgram();     // create empty OpenGL ShaderProgram
		glAttachShader(program, vertexId);   // add the vertex shader to program
		glAttachShader(program, fragmentId); // add the fragment shader to program

		glLinkProgram(program);              // create OpenGL program executables
		int[] status = new int[1];
		glGetProgramiv(program, GL_LINK_STATUS, status, 0);
		if (status[0] == 0) {
			String s = glGetProgramInfoLog(program);
			Log.e(TAG, "createProgram: " + s);
		}
		int error = glGetError();
		if (error != GL_NO_ERROR) {
			String errorString = GLU.gluErrorString(error);
			Log.e(TAG, "init program: glError " + errorString);
		}
		aPosition = attrib(program, "a_position", "VertexCoord");
		aTexCoord = attrib(program, "a_texcoord0", "TexCoord");
		uTextureUnit = uniform(program, "sampler0", "Texture");
		uTexelDelta = glGetUniformLocation(program, "u_texelDelta");
		uPixelDelta = glGetUniformLocation(program, "u_pixelDelta");
		uSetting = glGetUniformLocation(program, "u_setting");
		uMvp = glGetUniformLocation(program, "MVPMatrix");
		uTextureSize = glGetUniformLocation(program, "TextureSize");
		uInputSize = glGetUniformLocation(program, "InputSize");
		uOutputSize = glGetUniformLocation(program, "OutputSize");
		glUseProgram(program);
		if (uMvp >= 0) {
			glUniformMatrix4fv(uMvp, 1, false, new float[]{1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1}, 0);
		}
		int error1 = glGetError();
		if (error1 != GL_NO_ERROR) {
			String s1 = GLU.gluErrorString(error1);
			Log.e(TAG, "init program: glError " + s1);
			glDeleteShader(vertexId);
			glDeleteShader(fragmentId);
			glDeleteProgram(program);
			program = -1;
		}
		glDeleteShader(vertexId);
		glDeleteShader(fragmentId);
		return program;
	}

	private int loadShader(int type, String shaderCode) {

		// create a vertex shader type (GLES20.GL_VERTEX_SHADER)
		// or a fragment shader type (GLES20.GL_FRAGMENT_SHADER)
		int shader = glCreateShader(type);

		// add the source code to the shader and compile it
		glShaderSource(shader, shaderCode);
		glCompileShader(shader);
		int[] status = new int[1];
		glGetShaderiv(shader, GL_COMPILE_STATUS, status, 0);
		if (status[0] == 0) {
			String s = glGetShaderInfoLog(shader);
			Log.e(TAG, "loadShader: " + s);
			glDeleteShader(shader);
			return -1;
		}
		return shader;
	}

	public void loadVbo(FloatBuffer vbo, float width, float height) {
		vbo.rewind();
		if (aPosition >= 0) {
			glVertexAttribPointer(aPosition, 2, GL_FLOAT, false, 4 * 4, vbo);
			glEnableVertexAttribArray(aPosition);
		}

		// координаты текстур
		vbo.position(2);
		if (aTexCoord >= 0) {
			glVertexAttribPointer(aTexCoord, 2, GL_FLOAT, false, 4 * 4, vbo);
			glEnableVertexAttribArray(aTexCoord);
		}
		if (uTexelDelta >= 0) {
			glUniform2f(uTexelDelta, 1.0f / width, 1.0f / height);
		}
		if (uTextureSize >= 0) {
			glUniform2f(uTextureSize, width, height);
		}
		if (uInputSize >= 0) {
			glUniform2f(uInputSize, width, height);
		}
	}
}
