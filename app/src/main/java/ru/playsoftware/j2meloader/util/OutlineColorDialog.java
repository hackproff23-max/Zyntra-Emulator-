package ru.playsoftware.j2meloader.util;

import android.content.Context;
import android.graphics.Color;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import ru.playsoftware.j2meloader.R;

/** Dialogo para escolher livremente a cor do contorno (R, G, B ou codigo hexadecimal). */
public final class OutlineColorDialog {
	private OutlineColorDialog() {
	}

	public static void show(Context context, boolean forText, Runnable onSaved) {
		View v = LayoutInflater.from(context).inflate(R.layout.dialog_outline_color, null);
		OutlinedTextView preview = v.findViewById(R.id.outline_preview);
		SeekBar[] bars = {v.findViewById(R.id.seek_r), v.findViewById(R.id.seek_g), v.findViewById(R.id.seek_b)};
		TextView[] values = {v.findViewById(R.id.value_r), v.findViewById(R.id.value_g), v.findViewById(R.id.value_b)};
		EditText hex = v.findViewById(R.id.outline_hex);
		hex.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});

		int c = forText ? OutlineSettings.getTextColor() : OutlineSettings.getColor();
		final int[] rgb = {Color.red(c), Color.green(c), Color.blue(c)};
		final boolean[] busy = {false};

		Runnable refresh = () -> {
			busy[0] = true;
			if (forText) {
				preview.setFillColor(Color.rgb(rgb[0], rgb[1], rgb[2]));
			} else {
				preview.setOutlineColor(Color.rgb(rgb[0], rgb[1], rgb[2]));
			}
			for (int i = 0; i < 3; i++) {
				values[i].setText(String.valueOf(rgb[i]));
				if (bars[i].getProgress() != rgb[i]) {
					bars[i].setProgress(rgb[i]);
				}
			}
			String h = String.format("%02X%02X%02X", rgb[0], rgb[1], rgb[2]);
			if (!hex.getText().toString().equalsIgnoreCase(h)) {
				hex.setText(h);
				hex.setSelection(h.length());
			}
			busy[0] = false;
		};

		for (int i = 0; i < 3; i++) {
			final int idx = i;
			bars[i].setMax(255);
			bars[i].setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
				@Override
				public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
					if (fromUser && !busy[0]) {
						rgb[idx] = progress;
						refresh.run();
					}
				}

				@Override
				public void onStartTrackingTouch(SeekBar seekBar) {
				}

				@Override
				public void onStopTrackingTouch(SeekBar seekBar) {
				}
			});
		}

		hex.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
			}

			@Override
			public void afterTextChanged(Editable s) {
				if (busy[0] || s.length() != 6) {
					return;
				}
				try {
					int parsed = Integer.parseInt(s.toString(), 16);
					rgb[0] = (parsed >> 16) & 0xFF;
					rgb[1] = (parsed >> 8) & 0xFF;
					rgb[2] = parsed & 0xFF;
					refresh.run();
				} catch (NumberFormatException ignored) {
				}
			}
		});

		refresh.run();

		new AlertDialog.Builder(context)
				.setTitle(forText ? R.string.pref_text_color_title : R.string.pref_outline_color_title)
				.setView(v)
				.setNegativeButton(android.R.string.cancel, null)
				.setPositiveButton(android.R.string.ok, (d, w) -> {
					int picked = Color.rgb(rgb[0], rgb[1], rgb[2]);
					if (forText) {
						OutlineSettings.saveTextColor(context, picked);
					} else {
						OutlineSettings.save(context, picked);
					}
					if (onSaved != null) {
						onSaved.run();
					}
				})
				.show();
	}
}
