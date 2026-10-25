package ru.playsoftware.j2meloader.config;

import android.content.Context;

import androidx.appcompat.app.AlertDialog;

import javax.microedition.lcdui.keyboard.ButtonImages;

import ru.playsoftware.j2meloader.R;

/** Dialogos para importar/remover imagens PNG dos botoes, usados na tela principal. */
public final class ButtonImagesUi {

	public interface Picker {
		/** Abre o seletor de arquivos; o resultado deve ir para ButtonImagesManager.importPng. */
		void pick(String slot, boolean pressed);
	}

	private ButtonImagesUi() {
	}

	public static void showSlots(Context context, Picker picker) {
		int n = ButtonImages.KEY_SLOTS.length;
		String[] slots = new String[n + 1];
		String[] items = new String[n + 1];
		slots[0] = ButtonImages.SLOT_ALL;
		items[0] = context.getString(R.string.vk_img_all);
		for (int i = 0; i < n; i++) {
			slots[i + 1] = ButtonImages.KEY_SLOTS[i];
			items[i + 1] = ButtonImages.KEY_LABELS[i];
		}
		for (int i = 0; i <= n; i++) {
			if (ButtonImagesManager.has(slots[i], false) || ButtonImagesManager.has(slots[i], true)) {
				items[i] += "  ✓";
			}
		}
		new AlertDialog.Builder(context)
				.setTitle(R.string.vk_img_title)
				.setItems(items, (dialog, which) -> showActions(context, picker, slots[which], items[which]))
				.setNegativeButton(android.R.string.cancel, null)
				.show();
	}

	private static void showActions(Context context, Picker picker, String slot, String title) {
		String[] actions = {
				context.getString(R.string.vk_img_normal) + (ButtonImagesManager.has(slot, false) ? "  ✓" : ""),
				context.getString(R.string.vk_img_pressed) + (ButtonImagesManager.has(slot, true) ? "  ✓" : ""),
				context.getString(R.string.vk_img_remove)
		};
		new AlertDialog.Builder(context)
				.setTitle(title.replace("  ✓", ""))
				.setItems(actions, (dialog, which) -> {
					if (which == 2) {
						ButtonImagesManager.delete(slot);
						showSlots(context, picker);
					} else {
						picker.pick(slot, which == 1);
					}
				})
				.setNegativeButton(android.R.string.cancel, (dialog, which) -> showSlots(context, picker))
				.show();
	}
}
