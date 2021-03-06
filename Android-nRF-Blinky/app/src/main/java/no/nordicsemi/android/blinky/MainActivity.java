package no.nordicsemi.android.blinky;


import android.arch.lifecycle.ViewModelProviders;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Button;

import no.nordicsemi.android.blinky.BlinkyActivity;
import no.nordicsemi.android.blinky.adapter.ExtendedBluetoothDevice;
import no.nordicsemi.android.blinky.viewmodels.BlinkyViewModel;

/**
 * Example of a cross controller
 *
 * Created by Joe Plante to figure out how to do this for the Blade Engine
 *
 * Copyright (c) 2018 by Joe Plante and Bill Weiler.
 *
 * Full license to both involved to modify this code and license as they see fit. Everyone else can suck it
 */
public class MainActivity extends AppCompatActivity {

    private static final boolean TREAT_OUT_OF_BOUNDS_AS_CENTER = true;

    private static final int CROSS_DEAD_ZONE_IN_DP = 25; // 160 DIP is 1 in. 40 is around 1/4 in
    private static final int ANALOG_DEAD_ZONE_IN_DP = 10;

    private static final int NOOP = 0;
    private static final int FORWARD = 1;
    private static final int BACKWARD = 2;
    private static final int RIGHT = 3;
    private static final int LEFT = 4;
    private static final int STOP = 5;
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

    private static final int UNPRESSED_COLOR = 0xFF808080;
    private static final int PRESSED_COLOR = 0xFF000000;

    private TextView textViewUp;
    private TextView textViewDn;
    private TextView textViewLt;
    private TextView textViewRt;
    private DotView dotView;
    private Button quitActivity;

    private ViewGroup controlsContainer;

    private double analogCenterX = -1;
    private double analogCenterY = -1;

    private double stickCenterX = -1;
    private double stickCenterY = -1;

    private double crossDeadzoneInPx;
    private double analogDeadzoneInPx;

    private BlinkyViewModel viewModel;

    private int robot_command = STOP;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cross);

        textViewUp = findViewById(R.id.textViewUp);
        textViewDn = findViewById(R.id.textViewDn);
        textViewLt = findViewById(R.id.textViewLt);
        textViewRt = findViewById(R.id.textViewRt);

        quitActivity = findViewById(R.id.quit);

        dotView = findViewById(R.id.dotView);

        controlsContainer = findViewById(R.id.crossContainer);
        controlsContainer.setOnTouchListener(onCrossTouched);

        final Intent intent = getIntent();
        final ExtendedBluetoothDevice device = intent.getParcelableExtra(BlinkyActivity.EXTRA_DEVICE);
        final String deviceName = device.getName();
        final String deviceAddress = device.getAddress();
        viewModel = ViewModelProviders.of(this).get(BlinkyViewModel.class);
        viewModel.connect(device);

        Resources r = getResources();
        crossDeadzoneInPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, CROSS_DEAD_ZONE_IN_DP, r.getDisplayMetrics());
        analogDeadzoneInPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, ANALOG_DEAD_ZONE_IN_DP, r.getDisplayMetrics());

        dotView.updateCoordinates(0, 0);
        dotView.setOnTouchListener(onAnalogTouched);

        quitActivity.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        updateTextViews(5);
    }

    private void robotForward() {
        viewModel.sendCMD(Byte.valueOf((byte) 0x12)); //forward
    }

    private void robotBackward() {
        viewModel.sendCMD(Byte.valueOf((byte) 0x13)); //back
    }

    private void robotStop() {
        viewModel.sendCMD(Byte.valueOf((byte) 0x14)); //stop
    }

    private void robotRight() {
        viewModel.sendCMD(Byte.valueOf((byte) 0x10));  //right
    }

    private void robotLeft() {
        viewModel.sendCMD(Byte.valueOf((byte) 0x11));  //left
    }


    /* This code makes the robot move about 5cm because it sends 1 direction command (touch arrow) then stop (ACTION_UP) a little while later */
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

            switch (direction) {
                case 8: // 8 is up
                    if (robot_command != FORWARD)
                    {
                        robot_command = FORWARD;
                        robotForward();
                    }
                    break;
                case 2: // 2 is down
                    if (robot_command != BACKWARD)
                    {
                        robot_command = BACKWARD;
                        robotBackward();
                    }
                    break;
                case 4: // 4 is left
                    if (robot_command != LEFT)
                    {
                        robot_command = LEFT;
                        robotLeft();
                    }
                    break;
                case 6: // 6 is right.
                    if (robot_command != RIGHT)
                    {
                        robot_command = RIGHT;
                        robotRight();
                    }
                    break;
                case 5: // 5 is center
                    if (robot_command != STOP)
                    {
                        robot_command = STOP;
                        robotStop();
                    }
                    break;
            }
            // avoids eating the event chain
            return true;
        }
    };

    /**
     * Calculates the direction
     *
     * @param x      The x position
     * @param y      The y position
     * @param width  The width
     * @param height The height
     * @return See the numeric keypad for direction. 5 = center. 1 = down-left; 9 is top-right; 7 is top-left; 3 is down-right. 8 is up, 2 is down, 4 is left, and 6 is right.
     */
    private int calculateDirection(double x, double y, double width, double height) {

        if (TREAT_OUT_OF_BOUNDS_AS_CENTER) {
            if ((x < 0) || (y < 0) || (x > width) || (y > height)) {
                return 5;
            }
        }

        // remember your trig? There might be a better way to fake the trig though
        if (stickCenterX == -1) {
            stickCenterX = width / 2;
        }
        if (stickCenterY == -1) {
            stickCenterY = height / 2;
        }

        double xPos = x - stickCenterX;
        double yPos = y - stickCenterY;

        if ((Math.abs(xPos) < crossDeadzoneInPx) && (Math.abs(yPos) < crossDeadzoneInPx)) {
            return 5;
        }

        // trigonometry time!
        double angle = Math.atan2(yPos, xPos);

        // the angle goes to the right. If it's negative, it's up. If it's Pi, it's left.
        // 0 is right. Pi is left. - Pi/2 is up. pi/2 is down

        // the left part is going to be the trickiest

        // Draw a square divided into 8 pieces. That's what this is doing
        if ((angle <= PI_SLICE_UP0) && (angle >= PI_SLICE_UP1)) {
            return 6;
        }
        if ((angle <= PI_SLICE_UP1) && (angle >= PI_SLICE_UP3)) {
            return 9;
        }
        if ((angle <= PI_SLICE_UP3) && (angle >= PI_SLICE_UP5)) {
            return 8;
        }
        if ((angle <= PI_SLICE_UP5) && (angle >= PI_SLICE_UP7)) {
            return 7;
        }
        if ((angle <= PI_SLICE_UP7) && (angle >= PI_SLICE_UP8)) {
            return 4;
        }

        if ((angle >= PI_SLICE_DN0) && (angle <= PI_SLICE_DN1)) {
            return 6;
        }
        if ((angle >= PI_SLICE_DN1) && (angle <= PI_SLICE_DN3)) {
            return 3;
        }
        if ((angle >= PI_SLICE_DN3) && (angle <= PI_SLICE_DN5)) {
            return 2;
        }
        if ((angle >= PI_SLICE_DN5) && (angle <= PI_SLICE_DN7)) {
            return 1;
        }
        if ((angle >= PI_SLICE_DN7) && (angle <= PI_SLICE_DN8)) {
            return 4;
        }

        return 5;
    }

    private void updateTextViews(int direction) {

        // left
        switch (direction) {
            case 7:
            case 4:
            case 1:
                textViewLt.setTextColor(0xFF000000);
                break;
            default:
                textViewLt.setTextColor(0xFF808080);
        }

        switch (direction) {
            case 7:
            case 8:
            case 9:
                textViewUp.setTextColor(0xFF000000);
                break;
            default:
                textViewUp.setTextColor(0xFF808080);
        }

        switch (direction) {
            case 9:
            case 6:
            case 3:
                textViewRt.setTextColor(0xFF000000);
                break;
            default:
                textViewRt.setTextColor(0xFF808080);
        }

        switch (direction) {
            case 1:
            case 2:
            case 3:
                textViewDn.setTextColor(0xFF000000);
                break;
            default:
                textViewDn.setTextColor(0xFF808080);
        }

    }

    /* The hacky hack is once updateDot() returns, x and y are set in DotView */
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
                    robotStop();
                    robot_command = STOP;
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
        dotView.updateCoordinatesViaStick(0f, 0f);
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

        int quadrant = 0;

        if (analogCenterX == -1) {
            analogCenterX = width / 2;
        }
        if (analogCenterY == -1) {
            analogCenterY = height / 2;
        }


        double xPos = x - analogCenterX;
        double yPos = y - analogCenterY;

        if ((Math.abs(xPos) < analogDeadzoneInPx) && (Math.abs(yPos) < analogDeadzoneInPx)) {
            centerStick();
            return;         //do nothing
        }

        double sx = xPos / analogCenterX;
        double sy = yPos / analogCenterY;

        double radians = Math.atan(Math.abs(sy/sx));

        if (xPos >= 0.0) {
            if (yPos >= 0.0)    //dot in 1st quadrant on circle
            {
                if (radians >=  Math.PI/4)      //robot forward area, dot > 45 degrees on circle
                {
                    if (robot_command != BACKWARD)   //not already going forward
                    {
                        robot_command = BACKWARD;
                        robotBackward();
                    }
                }
                else
                {
                    if (robot_command != RIGHT)   //not already going right
                    {
                        robot_command = RIGHT;
                        robotRight();
                    }
                }
            }
            else    //dot is in 4th quadrant
            {
                if (radians >=  Math.PI/4)      //robot forward area, dot > 45 degrees on circle
                {
                    if (robot_command != FORWARD)   //not already going backward
                    {
                        robot_command = FORWARD;
                        robotForward();
                    }
                }
                else
                {
                    if (robot_command != RIGHT)   //not already going right
                    {
                        robot_command = RIGHT;
                        robotRight();
                    }
                }
            }
        }else {
            if (yPos >= 0.0) {                       //dot is in 2nd quadrant
                if (radians >= Math.PI / 4)      //robot forward area, dot > 45 degrees on circle
                {
                    if (robot_command != BACKWARD)   //not already going forward
                    {
                        robot_command = BACKWARD;
                        robotBackward();
                    }
                } else {
                    if (robot_command != LEFT)   //not already going left
                    {
                        robot_command = LEFT;
                        robotLeft();
                    }
                }
            }else{                              //dot is in 3rd quadrant
                if (radians >=  Math.PI/4)      //robot forward area, dot > 45 degrees on circle
                {
                    if (robot_command != FORWARD)   //not already going forward
                    {
                        robot_command = FORWARD;
                        robotForward();
                    }
                }
                else
                {
                    if (robot_command != LEFT)   //not already going left
                    {
                        robot_command = LEFT;
                        robotLeft();
                    }
                }

            }
        }

        Log.v("---", sx + ", " + sy);

        dotView.updateCoordinatesViaStick((float) sx, (float) sy);

        return;
    }
}
