package no.nordicsemi.android.blinky.onscreen_controller;

import android.app.Activity;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import no.nordicsemi.android.blinky.R;

public class AnalogControllerFragment extends Fragment {

    private static final int ANALOG_DEAD_ZONE_IN_DP = 10;

    private DotView dotView;

    private double  analogCenterX = -1;
    private double  analogCenterY = -1;
    private double  analogDeadzoneInPx;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        dotView = (DotView)inflater.inflate(R.layout.fragment_analog_stick, container, false);

        Resources r = getResources();
        analogDeadzoneInPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, ANALOG_DEAD_ZONE_IN_DP, r.getDisplayMetrics());

        dotView.updateCoordinates(0,0);
        dotView.setOnTouchListener(onAnalogTouched);

        return dotView;
    }

    private View.OnTouchListener onAnalogTouched = new View.OnTouchListener() {

        @Override
        public boolean onTouch(View v, MotionEvent event) {

            int action = event.getAction();

            switch (action) {

                case MotionEvent.ACTION_DOWN:
                    updateDot(event.getX(), event.getY(), v.getWidth(), v.getHeight());
                    break;

                case MotionEvent.ACTION_MOVE:
                    updateDot(event.getX(), event.getY(), v.getWidth(), v.getHeight());
                    break;

                case MotionEvent.ACTION_UP:
                    centerStick();
                    break;

                default:
                    return false;
            }

            // avoids eating the event chain
            return true;
        }
    };

    /**
     * Centers the stick
     */
    private void centerStick() {
        updateStickCoordinates(0f, 0f);
    }

    /**
     * Updates the stick coordinates
     *
     * @param x The x position from -1 to 1
     * @param y The y position from -1 to 1
     */
    private void updateStickCoordinates(float x, float y) {

        dotView.updateCoordinatesViaStick(x, y);

        Activity act = getActivity();
        if (act instanceof OnAnalogStickMoved ) {
            OnAnalogStickMoved onAnalogStickMoved = (OnAnalogStickMoved)act;
            onAnalogStickMoved.onAnalogStickMoved(this, x, y);
        }
        else {
            Log.w("AnalogController", "If you want to receive movements, make your activity extend OnAnalogStickMoved");
        }
    }

    /**
     * Updates the dot
     *
     * @param x      x coordinate
     * @param y      y coordinate
     * @param width  the width
     * @param height the height
     */
    private void updateDot(float x, float y, int width, int height) {

//        if (TREAT_OUT_OF_BOUNDS_AS_CENTER) {
//            if ( (x < 0) || (y < 0) || (x > width) || ( y > height ) ) {
//                centerStick();
//                return;
//            }
//        }

        if ( analogCenterX == -1 ) {
            analogCenterX = width / 2;
        }
        if ( analogCenterY == -1 ) {
            analogCenterY = height / 2;
        }

        double xPos = x - analogCenterX;
        double yPos = y - analogCenterY;

        if ( ( Math.abs(xPos) < analogDeadzoneInPx ) && ( Math.abs(yPos) < analogDeadzoneInPx ) ) {
            centerStick();
            return;
        }

        // a.k.a. distance in pixels
        double hypotenuseSquared = (xPos * xPos) + (yPos * yPos);
        double hypotenuse = 0;
        if ( hypotenuseSquared > 0 ) {
            hypotenuse = Math.sqrt(hypotenuseSquared);
            if ( hypotenuse > analogCenterX ) {
                hypotenuse = analogCenterX;
            }
        }

        // trigonometry time!
        double angle = Math.atan2(yPos, xPos);

        xPos = hypotenuse * Math.cos(angle);
        yPos = hypotenuse * Math.sin(angle);

        double sx = xPos / analogCenterX;
        double sy = yPos / analogCenterY;

        updateStickCoordinates((float)sx, (float)sy);
    }

    /**
     * Interface that handles analog stick movement
     */
    public interface OnAnalogStickMoved {

        /**
         * The Analog stick moved
         *
         * @param analogControllerFragment The analog controller fragment. You can use the Id from this
         * @param x The x position from -1 to 1
         * @param y The y position from -1 to 1
         */
        void onAnalogStickMoved(AnalogControllerFragment analogControllerFragment, float x, float y);
    }
}
