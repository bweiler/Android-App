package no.nordicsemi.android.blinky;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

public class DotView extends View {

    public static final int OFF_SCREEN = Integer.MIN_VALUE;
    private static final float CIRCLE_RADIUS_DP = 20;

    public int x = OFF_SCREEN;
    public int y = OFF_SCREEN;

    public int centerX = 0;
    public int centerY = 0;

    private Paint circlePaint  = makeCirclePaint();
    private float circleRadiusPx;

    public DotView(Context context) {
        super(context);
        init();
    }

    public DotView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DotView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public DotView(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    private void init() {

        Resources r = getResources();
        circleRadiusPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, CIRCLE_RADIUS_DP, r.getDisplayMetrics());

    }

    private static final Paint makeCirclePaint() {
        Paint paint = new Paint();
        paint.setColor(0xFFFFFFFF);

        return paint;
    }

    /**
     * Removes the dot from the screen
     */
    public void removeDot() {
        x = OFF_SCREEN;
        y = OFF_SCREEN;
    }

    /**
     * Updates the coordinates
     *
     * @param x the X value.
     * @param y the Y value
     */
    public void updateCoordinates(int x, int y) {
        this.x = x;
        this.y = y;
        invalidate();
    }

    /**
     * Updates the coordinates using stick coordinates
     *
     * @param sx the X value from -1 to 1.
     * @param sy the Y value from -1 to 1
     */
    public void updateCoordinatesViaStick(float sx, float sy) {

        float nx = centerX;
        float ny = centerY;

        nx *= sx;
        ny *= sy;

        updateCoordinates((int)nx, (int)ny);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.drawColor(0xFF303030, PorterDuff.Mode.SRC);

        if ( ( x == OFF_SCREEN) && (y == OFF_SCREEN ) ) {
            return;
        }

        float xPos = x + centerX;
        float yPos = y + centerY;
        canvas.drawCircle(xPos, yPos, circleRadiusPx, circlePaint);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        centerX = w / 2;
        centerY = h / 2;
    }
}
