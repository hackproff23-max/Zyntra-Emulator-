package ru.playsoftware.j2meloader.util;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/** Imagem preenchendo toda a area (center crop) com uma camada escura por cima para as letras ficarem legiveis. */
public class ImageBackgroundDrawable extends Drawable {
	private final Bitmap bitmap;
	private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
	private final Paint dim = new Paint();
	private final Matrix matrix = new Matrix();

	public ImageBackgroundDrawable(Bitmap bitmap, int dimColor) {
		this.bitmap = bitmap;
		dim.setColor(dimColor);
	}

	@Override
	protected void onBoundsChange(Rect bounds) {
		float w = bounds.width();
		float h = bounds.height();
		float bw = bitmap.getWidth();
		float bh = bitmap.getHeight();
		if (w <= 0 || h <= 0 || bw <= 0 || bh <= 0) {
			return;
		}
		float scale = Math.max(w / bw, h / bh);
		matrix.reset();
		matrix.setScale(scale, scale);
		matrix.postTranslate(bounds.left + (w - bw * scale) / 2f, bounds.top + (h - bh * scale) / 2f);
	}

	@Override
	public void draw(Canvas canvas) {
		Rect b = getBounds();
		canvas.save();
		canvas.clipRect(b);
		canvas.drawBitmap(bitmap, matrix, paint);
		canvas.drawRect(b, dim);
		canvas.restore();
	}

	@Override
	public void setAlpha(int alpha) {
		paint.setAlpha(alpha);
	}

	@Override
	public void setColorFilter(ColorFilter colorFilter) {
		paint.setColorFilter(colorFilter);
	}

	@Override
	public int getOpacity() {
		return PixelFormat.OPAQUE;
	}
}
