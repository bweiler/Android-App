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
import no.nordicsemi.android.blinky.onscreen_controller.AnalogControllerFragment;
import no.nordicsemi.android.blinky.onscreen_controller.CrossControllerFragment;
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
public class ControllerActivity extends AppCompatActivity  implements CrossControllerFragment.OnCrossControllerTouchedListener, AnalogControllerFragment.OnAnalogStickMoved {

    private static final String TAG = ControllerActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    @Override
    public void onAnalogStickMoved(AnalogControllerFragment analogControllerFragment, float x, float y) {
        Log.v(TAG, "Analog stick " + analogControllerFragment.getId() + " moved: " + x + ", " + y);
    }

    @Override
    public void onCrossControllerMoved(CrossControllerFragment crossControllerFragment, int whichDirection) {
        Log.v(TAG, "Cross controller with id " + crossControllerFragment.getId() + " had direction " + whichDirection + " pressed");
    }
}
