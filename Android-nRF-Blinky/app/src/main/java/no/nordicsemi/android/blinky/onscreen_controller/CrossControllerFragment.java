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
import android.widget.TextView;

import no.nordicsemi.android.blinky.R;

public class CrossControllerFragment extends Fragment {

    private static final boolean TREAT_OUT_OF_BOUNDS_AS_CENTER = true;
    private static final int CROSS_DEAD_ZONE_IN_DP  = 25; // 160 DIP is 1 in. 40 is around 1/4 in

    private static final boolean FILTER_OUT_DUPES = true;

    // the top
    private static final Double PI_SLICE_UP0 = 0d;
    private static final Double PI_SLICE_UP1 = -Math.PI / 8;
    //    private static final Double PI_SLICE_UP2 = -Math.PI / 4;
    private static final Double PI_SLICE_UP3 = -(3 * Math.PI) / 8;
    //    private static final Double PI_SLICE_UP4 = -Math.PI / 2;
    private static final Double PI_SLICE_UP5 = -(5 * Math.PI) / 8;
    //    private static final Double PI_SLICE_UP6 = -(6 * Math.PI) / 8;
    private static final Double PI_SLICE_UP7 = -(7 * Math.PI) / 8;
    private static final Double PI_SLICE_UP8 = -Math.PI;

    // the bottom
    private static final Double PI_SLICE_DN0 = 0d;
    private static final Double PI_SLICE_DN1 = Math.PI / 8;
    //    private static final Double PI_SLICE_DN2 = Math.PI / 4;
    private static final Double PI_SLICE_DN3 = (3 * Math.PI) / 8;
    //    private static final Double PI_SLICE_DN4 = Math.PI / 2;
    private static final Double PI_SLICE_DN5 = (5 * Math.PI) / 8;
    //    private static final Double PI_SLICE_DN6 = (6 * Math.PI) / 8;
    private static final Double PI_SLICE_DN7 = (7 * Math.PI) / 8;
    private static final Double PI_SLICE_DN8 = Math.PI;

    private static final int UNPRESSED_COLOR = 0xFF000000;
    private static final int PRESSED_COLOR   = 0xFF808080;

    private double stickCenterX = -1;
    private double stickCenterY = -1;
    private double crossDeadzoneInPx;

    private TextView textViewUp;
    private TextView textViewDn;
    private TextView textViewLt;
    private TextView textViewRt;

    private int lastDirection = -1;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_cross, container, false);

        textViewUp = view.findViewById(R.id.textViewUp);
        textViewDn = view.findViewById(R.id.textViewDn);
        textViewLt = view.findViewById(R.id.textViewLt);
        textViewRt = view.findViewById(R.id.textViewRt);

        Resources r = getResources();
        crossDeadzoneInPx  = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, CROSS_DEAD_ZONE_IN_DP, r.getDisplayMetrics());

        view.setOnTouchListener(onCrossTouched);
        updateTextViews(5);

        return view;
    }

    private View.OnTouchListener onCrossTouched = new View.OnTouchListener() {

        @Override
        public boolean onTouch(View v, MotionEvent event) {

            int action = event.getAction();
            int direction = 5;

            switch (action) {

                case MotionEvent.ACTION_DOWN:
                    direction = calculateDirection(event.getX(), event.getY(), v.getWidth(), v.getHeight());
                    break;

                case MotionEvent.ACTION_MOVE:
                    direction = calculateDirection(event.getX(), event.getY(), v.getWidth(), v.getHeight());
                    break;

                case MotionEvent.ACTION_UP:
                    direction = 5;
                    break;

                default:
                    return false;
            }

            updateTextViews(direction);
            Activity activity = getActivity();
            boolean transmit = true;
            if ( FILTER_OUT_DUPES ) {
                if ( lastDirection == direction ) {
                    transmit = false;
                }
                lastDirection = direction;
            }

            if ( transmit ) {
                if ( activity instanceof OnCrossControllerTouchedListener ) {
                    OnCrossControllerTouchedListener listener = (OnCrossControllerTouchedListener)activity;
                    listener.onCrossControllerMoved(CrossControllerFragment.this, direction);
                }
                else {
                    Log.w("CrossController", "Make your Activity implement OnControllerTouchedListener to get the controller callbacks");
                }
            }

            // avoids eating the event chain
            return true;
        }
    };

    /**
     * Calculates the direction
     *
     * @param x       The x position
     * @param y       The y position
     * @param width   The width
     * @param height  The height
     *
     * @return See the numeric keypad for direction. 5 = center. 1 = down-left; 9 is top-right; 7 is top-left; 3 is down-right. 8 is up, 2 is down, 4 is left, and 6 is right.
     */
    private int calculateDirection(double x, double y, double width, double height) {

        if (TREAT_OUT_OF_BOUNDS_AS_CENTER) {
            if ( (x < 0) || (y < 0) || (x > width) || ( y > height ) ) {
                return 5;
            }
        }

        // remember your trig? There might be a better way to fake the trig though
        if ( stickCenterX == -1 ) {
            stickCenterX = width / 2;
        }
        if ( stickCenterY == -1 ) {
            stickCenterY = height / 2;
        }

        double xPos = x - stickCenterX;
        double yPos = y - stickCenterY;

        if ( ( Math.abs(xPos) < crossDeadzoneInPx) && ( Math.abs(yPos) < crossDeadzoneInPx) ) {
            return 5;
        }

        // trigonometry time!
        double angle = Math.atan2(yPos, xPos);

        // the angle goes to the right. If it's negative, it's up. If it's Pi, it's left.
        // 0 is right. Pi is left. - Pi/2 is up. pi/2 is down

        // the left part is going to be the trickiest

        // Draw a square divided into 8 pieces. That's what this is doing
        if ( ( angle <= PI_SLICE_UP0) && ( angle >= PI_SLICE_UP1 ) ) {
            return 6;
        }
        if ( ( angle <= PI_SLICE_UP1) && ( angle >= PI_SLICE_UP3 ) ) {
            return 9;
        }
        if ( ( angle <= PI_SLICE_UP3) && ( angle >= PI_SLICE_UP5 ) ) {
            return 8;
        }
        if ( ( angle <= PI_SLICE_UP5) && ( angle >= PI_SLICE_UP7 ) ) {
            return 7;
        }
        if ( ( angle <= PI_SLICE_UP7) && ( angle >= PI_SLICE_UP8 ) ) {
            return 4;
        }

        if ( ( angle >= PI_SLICE_DN0) && ( angle <= PI_SLICE_DN1 ) ) {
            return 6;
        }
        if ( ( angle >= PI_SLICE_DN1) && ( angle <= PI_SLICE_DN3 ) ) {
            return 3;
        }
        if ( ( angle >= PI_SLICE_DN3) && ( angle <= PI_SLICE_DN5 ) ) {
            return 2;
        }
        if ( ( angle >= PI_SLICE_DN5) && ( angle <= PI_SLICE_DN7 ) ) {
            return 1;
        }
        if ( ( angle >= PI_SLICE_DN7) && ( angle <= PI_SLICE_DN8 ) ) {
            return 4;
        }

        return 5;
    }

    private void updateTextViews(int direction) {

        // left
        switch(direction) {
            case 7:
            case 4:
            case 1:
                textViewLt.setTextColor(PRESSED_COLOR);
                break;
            default:
                textViewLt.setTextColor(UNPRESSED_COLOR);
        }

        switch(direction) {
            case 7:
            case 8:
            case 9:
                textViewUp.setTextColor(PRESSED_COLOR);
                break;
            default:
                textViewUp.setTextColor(UNPRESSED_COLOR);
        }

        switch(direction) {
            case 9:
            case 6:
            case 3:
                textViewRt.setTextColor(PRESSED_COLOR);
                break;
            default:
                textViewRt.setTextColor(UNPRESSED_COLOR);
        }

        switch(direction) {
            case 1:
            case 2:
            case 3:
                textViewDn.setTextColor(PRESSED_COLOR);
                break;
            default:
                textViewDn.setTextColor(UNPRESSED_COLOR);
        }

    }

    public interface OnCrossControllerTouchedListener {

        /**
         * The controller got touched
         *
         * @param crossControllerFragment The fragment that got the input
         * @param whichDirection The direction. See your numeric keypad. Up-left is 7, Up is 8, Up-right is 9. Left is 4, Center is 5, Right is 6. Bottom-left to bottom-right: 1, 2, 3
         */
        void onCrossControllerMoved(CrossControllerFragment crossControllerFragment, int whichDirection);
    }
}
