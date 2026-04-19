package com.emergency.patient.ocr;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;

public class ImageHelper {

    public static Bitmap toGrayscale(Bitmap src) {
        if (src == null) return null;
        int width = src.getWidth();
        int height = src.getHeight();
        Bitmap dest = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        
        Canvas canvas = new Canvas(dest);
        ColorMatrix colorMatrix = new ColorMatrix();
        colorMatrix.setSaturation(0); // Grayscale
        Paint paint = new Paint();
        paint.setColorFilter(new ColorMatrixColorFilter(colorMatrix));
        canvas.drawBitmap(src, 0, 0, paint);
        
        return dest;
    }

    public static Bitmap increaseContrast(Bitmap src) {
        if (src == null) return null;
        int width = src.getWidth();
        int height = src.getHeight();
        Bitmap dest = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        
        Canvas canvas = new Canvas(dest);
        
        // Increase contrast by 1.5x (stretch scale to 1.5, offset -128 * 0.5 to prevent blowout)
        float contrast = 1.5f;
        float translate = (-.5f * contrast + .5f) * 255f;
        
        ColorMatrix colorMatrix = new ColorMatrix(new float[] {
            contrast, 0, 0, 0, translate,
            0, contrast, 0, 0, translate,
            0, 0, contrast, 0, translate,
            0, 0, 0, 1, 0
        });
        
        Paint paint = new Paint();
        paint.setColorFilter(new ColorMatrixColorFilter(colorMatrix));
        canvas.drawBitmap(src, 0, 0, paint);
        
        return dest;
    }

    public static Bitmap cropCenter(Bitmap src) {
        if (src == null) return null;
        int w = src.getWidth();
        int h = src.getHeight();
        
        // Crop 60% of the center where the text usually lives
        int xOffset = (int) (w * 0.2);
        int yOffset = (int) (h * 0.2);
        int cw = (int) (w * 0.6);
        int ch = (int) (h * 0.6);
        
        return Bitmap.createBitmap(src, xOffset, yOffset, cw, ch);
    }
}
