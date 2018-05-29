package com.princessblade.controllers;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;

public class MainActivityOld extends AppCompatActivity {

    private Button buttonLeft;
    private Button buttonRight;
    private Button buttonTop;
    private Button buttonBottom;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        buttonLeft   = findViewById(R.id.buttonLeft);
        buttonRight  = findViewById(R.id.buttonRight);
        buttonTop    = findViewById(R.id.buttonTop);
        buttonBottom = findViewById(R.id.buttonBottom);

        buttonLeft  .setOnTouchListener(onLeftButtonTouched);
        buttonRight .setOnTouchListener(onRightButtonTouched);
        buttonTop   .setOnTouchListener(onTopButtonTouched);
        buttonBottom.setOnTouchListener(onBottomButtonTouched);
    }

    private View.OnTouchListener onLeftButtonTouched = new View.OnTouchListener() {

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            Log.v("---", "left");
            return false;
        }
    };
    private View.OnTouchListener onRightButtonTouched = new View.OnTouchListener() {

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            Log.v("---", "right");
            return false;
        }
    };

    private View.OnTouchListener onTopButtonTouched = new View.OnTouchListener() {

        @Override
        public boolean onTouch(View v, MotionEvent event) {

            Log.v("---", "top");
            return false;
        }
    };

    private View.OnTouchListener onBottomButtonTouched = new View.OnTouchListener() {

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            Log.v("---", "bottom");
            return false;
        }
    };





}
