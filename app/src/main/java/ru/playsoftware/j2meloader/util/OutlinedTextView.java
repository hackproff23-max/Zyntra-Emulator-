package ru.playsoftware.j2meloader.util;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextPaint;
import android.util.AttributeSet;

import androidx.appcompat.widget.AppCompatTextView;

import ru.playsoftware.j2meloader.R;

/** TextView com contorno nas letras. A cor vem de {@link OutlineSettings} (ou de setOutlineColor). */
public class OutlinedTextView extends AppCompatTextView {
	private float outlineWidth;
	private Integer outlineColor;
	private boolean drawing;

	public OutlinedTextView(Context context) {
		this(context, null);
	}

	public OutlinedTextView(Context context, AttributeSet attrs) {
		this(context, attrs, android.R.attr.textViewStyle);
	}

	public OutlinedTextView(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		float def = 2 * context.getResources().getDisplayMetrics().density;
		if (attrs != null) {
			TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.OutlinedTextView);
			outlineWidth = a.getDimension(R.styleable.OutlinedTextView_outlineWidth, def);
			a.recycle();
		} else {
			outlineWidth = def;
		}
	}

	/** Cor propria deste texto (usada na previa); null volta a usar a cor global. */
	public void setOutlineColor(Integer color) {
		outlineColor = color;
		invalidate();
	}

	@Override
	public void invalidate() {
		if (!drawing) {
			super.invalidate();
		}
	}

	@Override
	protected void onDraw(Canvas canvas) {
		if (outlineWidth <= 0) {
			super.onDraw(canvas);
			return;
		}
		ColorStateList fill = getTextColors();
		TextPaint paint = getPaint();
		drawing = true;
		try {
			paint.setStyle(Paint.Style.STROKE);
			paint.setStrokeWidth(outlineWidth);
			paint.setStrokeJoin(Paint.Join.ROUND);
			setTextColor(outlineColor != null ? outlineColor : OutlineSettings.getColor());
			super.onDraw(canvas);
			paint.setStyle(Paint.Style.FILL);
			setTextColor(fill);
		} finally {
			drawing = false;
		}
		super.onDraw(canvas);
	}
}
