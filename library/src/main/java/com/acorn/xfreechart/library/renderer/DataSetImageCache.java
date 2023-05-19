package com.acorn.xfreechart.library.renderer;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorSpace;
import android.graphics.Paint;
import android.graphics.Path;

import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import androidx.annotation.ColorInt;

class DataSetImageCache {

    private Path mCirclePathBuffer = new Path();

    private Bitmap[] circleBitmaps;
    private Bitmap[] highlightBitmaps;
    private final Paint mRenderPaint;
    private final Paint mCirclePaintInner;

    public DataSetImageCache(Paint mRenderPaint, Paint mCirclePaintInner) {
        this.mRenderPaint = mRenderPaint;
        this.mCirclePaintInner = mCirclePaintInner;
    }

    /**
     * Sets up the cache, returns true if a change of cache was required.
     *
     * @param set
     * @return
     */
    protected boolean init(ILineDataSet set) {

        int size = set.getCircleColorCount();
        boolean changeRequired = false;

        if (circleBitmaps == null) {
            circleBitmaps = new Bitmap[size];
            highlightBitmaps = new Bitmap[size];
            changeRequired = true;
        } else if (circleBitmaps.length != size) {
            circleBitmaps = new Bitmap[size];
            highlightBitmaps = new Bitmap[size];
            changeRequired = true;
        }

        return changeRequired;
    }

    /**
     * Fills the cache with bitmaps for the given dataset.
     *
     * @param set
     * @param drawCircleHole
     * @param drawTransparentCircleHole
     */
    protected void fill(ILineDataSet set, boolean drawCircleHole, boolean drawTransparentCircleHole) {

        int colorCount = set.getCircleColorCount();
        float circleRadius = set.getCircleRadius();
        float circleHoleRadius = set.getCircleHoleRadius();
        float highlightRadius = set.getHighlightCircleRadius();


        for (int i = 0; i < colorCount; i++) {

            Bitmap.Config conf = Bitmap.Config.ARGB_4444;
            Bitmap circleBitmap = Bitmap.createBitmap((int) (circleRadius * 2.1), (int) (circleRadius * 2.1), conf);

            Canvas canvas = new Canvas(circleBitmap);
            circleBitmaps[i] = circleBitmap;
            mRenderPaint.setColor(set.getCircleColor(i));

            if (drawTransparentCircleHole) {
                // Begin path for circle with hole
                mCirclePathBuffer.reset();

                mCirclePathBuffer.addCircle(
                        circleRadius,
                        circleRadius,
                        circleRadius,
                        Path.Direction.CW);

                // Cut hole in path
                mCirclePathBuffer.addCircle(
                        circleRadius,
                        circleRadius,
                        circleHoleRadius,
                        Path.Direction.CCW);

                // Fill in-between
                canvas.drawPath(mCirclePathBuffer, mRenderPaint);
            } else {

                canvas.drawCircle(
                        circleRadius,
                        circleRadius,
                        circleRadius,
                        mRenderPaint);

                if (drawCircleHole) {
                    canvas.drawCircle(
                            circleRadius,
                            circleRadius,
                            circleHoleRadius,
                            mCirclePaintInner);
                }
            }

            //highlight bitmap
            Bitmap highlightBitmap = Bitmap.createBitmap((int) (highlightRadius * 2.1), (int) (highlightRadius * 2.1), conf);
            Canvas highlightCanvas = new Canvas(highlightBitmap);
            highlightBitmaps[i] = highlightBitmap;
            mRenderPaint.setAlpha(76);
            highlightCanvas.drawCircle(
                    highlightRadius,
                    highlightRadius,
                    highlightRadius,
                    mRenderPaint
            );
            mRenderPaint.setAlpha(255);
            highlightCanvas.drawCircle(
                    highlightRadius,
                    highlightRadius,
                    circleHoleRadius,
                    mRenderPaint
            );
        }
    }

    /**
     * 30%透明度原色
     *
     * @param color
     * @return
     */
    private int getHighlightColor(int color) {
        float r = ((color >> 16) & 0xff) / 255.0f;
        float g = ((color >> 8) & 0xff) / 255.0f;
        float b = ((color) & 0xff) / 255.0f;
        return argb(30, r, g, b);
    }

    @ColorInt
    private static int argb(float alpha, float red, float green, float blue) {
        return ((int) (alpha * 255.0f + 0.5f) << 24) |
                ((int) (red * 255.0f + 0.5f) << 16) |
                ((int) (green * 255.0f + 0.5f) << 8) |
                (int) (blue * 255.0f + 0.5f);
    }

    /**
     * Returns the cached Bitmap at the given index.
     *
     * @param index
     * @return
     */
    protected Bitmap getBitmap(int index) {
        return circleBitmaps[index % circleBitmaps.length];
    }

    protected Bitmap getHighlightBitmap(int index) {
        return highlightBitmaps[index % circleBitmaps.length];
    }
}