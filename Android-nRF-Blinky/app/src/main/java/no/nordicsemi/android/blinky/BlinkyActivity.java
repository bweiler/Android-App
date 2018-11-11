/*
 * Copyright (c) 2015, Nordic Semiconductor
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 *  Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 *  Neither the name of copyright holder nor the names of its
 *   contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
/*
		Skoobot Android app. Repurposed form Nordic Semi Blinky app they wrote for their reference designs. My app and their app are no longer compatible, so
		you can't use their app with Skoobot, you have to use this one. Although nRFConnect will show you BLE characteristics and let you change them.

		Author: Bill Weiler

		Summary:

		1. This app sends commands to Skoobot and receives notifications
		2. I added reading characteristics, I guess Raspberry Pi won't accept notifications, I test it here using reads
		2. When you hit the record button for audio record, these is 2s of recording on the robot. Then it uploads to the cellphone over BLE.
		The total size of the 2s recording is 32k. After it uploads, pressing the play button will play it.
		3. Fotovore mode will cause the robot to go formward when shining a light on it
		4. Rover mode will ignore obstacles about 2.5 inches in front

		Details:

 */
package no.nordicsemi.android.blinky;

import android.Manifest;
import android.annotation.SuppressLint;
import android.arch.lifecycle.ViewModelProviders;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Button;
import android.os.Handler;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

import no.nordicsemi.android.log.LogContract;
import no.nordicsemi.android.blinky.adapter.ExtendedBluetoothDevice;
import no.nordicsemi.android.blinky.viewmodels.BlinkyViewModel;
import no.nordicsemi.android.log.Logger;

public class BlinkyActivity extends AppCompatActivity {
	public static final String EXTRA_DEVICE = "no.nordicsemi.android.blinky.EXTRA_DEVICE";

	final private byte cmd_right = 0x10;
	final private byte cmd_left  = 0x11;
	final private byte cmd_forward = 0x12;
	final private byte cmd_back  = 0x13;
	final private byte cmd_stop  = 0x14;
	final private byte cmd_stopturn = 0x15;
	final private byte cmd_buzzer = 0x17;
	final private byte cmd_distance = 0x22;
	final private byte cmd_ambient = 0x21;
	final private byte cmd_rover = 0x40;
	final private byte cmd_rover_rev = 0x42;
	final private byte cmd_photov = 0x41;
	final private byte motors_speed = 0x50;
	private int repeatingpress = 0;

	private int dataState = 0;
	private byte motors_forward = cmd_forward, motors_backward = cmd_back;
	private byte motors_left = cmd_left, motors_right = cmd_right;
	private byte rover_mode = cmd_rover, rover_mode_rev = cmd_rover_rev;
	private BlinkyViewModel viewModel = null;

	@SuppressLint("ClickableViewAccessibility")
    @Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_blinky);

		final Intent intent = getIntent();
		final ExtendedBluetoothDevice device = intent.getParcelableExtra(EXTRA_DEVICE);
		final String deviceName = device.getName();
		final String deviceAddress = device.getAddress();
		final Toolbar toolbar = findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);
		getSupportActionBar().setTitle(deviceName);
		getSupportActionBar().setSubtitle(deviceAddress);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);

		// Configure the view model
		viewModel = ViewModelProviders.of(this).get(BlinkyViewModel.class);
		viewModel.connect(device);

		// Set up views
		final TextView msgString = findViewById(R.id.button_state);
		final LinearLayout progressContainer = findViewById(R.id.progress_container);
		final View content = findViewById(R.id.device_container);

		final Button left = findViewById(R.id.left);
		final Button forward = findViewById(R.id.forward);
		final Button back = findViewById(R.id.backward);
		final Button right = findViewById(R.id.right);
		final Button stop = findViewById(R.id.stop);
		final Button distance = findViewById(R.id.distance);
		final Button ambient = findViewById(R.id.ambient);
		final Button rover = findViewById(R.id.rover);
		final Button roverrev = findViewById(R.id.roverrev);
		final Button submenu = findViewById(R.id.submenu);
		final Button photov = findViewById(R.id.photovore);
		final Button buzzer = findViewById(R.id.buzzer);
		final Button RC = findViewById(R.id.RC);
		final Switch dirsw = findViewById(R.id.switchmodes);
		final TextView disText = findViewById(R.id.distanceText);
		final TextView ambText = findViewById(R.id.ambientText);

		Context context = getApplicationContext();
		File file = new File(context.getFilesDir(), "speed.dat");
		FileInputStream in = null;
		byte[] sendspeed = new byte[4];
		int bytesread = 0;
		try {
			in = new FileInputStream(file);
			if (in != null) {
				bytesread = in.read(sendspeed);
				in.close();
			}
			if (sendspeed[0] == motors_speed && sendspeed[1] <= 0x0f)
				viewModel.send4Byte(sendspeed);
		}
		catch (IOException e) {
			Log.d("BlinkyActivity", "Failed to read speed file ");
		}
		Log.d("BlinkyActivity", String.format("Bytes read %d",bytesread));

		forward.setOnClickListener(view -> viewModel.sendCMD(motors_forward));
		back.setOnClickListener(view -> viewModel.sendCMD(motors_backward));
		stop.setOnClickListener(view -> viewModel.sendCMD(cmd_stop));
		buzzer.setOnClickListener(view -> viewModel.sendCMD(cmd_buzzer));
		distance.setOnClickListener(view -> viewModel.sendCMD(cmd_distance));
		ambient.setOnClickListener(view -> viewModel.sendCMD(cmd_ambient));
		rover.setOnClickListener(view -> viewModel.sendCMD(rover_mode));
		roverrev.setOnClickListener(view -> viewModel.sendCMD(rover_mode_rev));
		photov.setOnClickListener(view -> viewModel.sendCMD(cmd_photov));

		dirsw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				if (isChecked == true) {
					motors_forward = cmd_forward;		//these are old motors
					motors_backward = cmd_back;
					motors_right = cmd_right;
					motors_left = cmd_left;
					rover_mode = cmd_rover;
					rover_mode_rev = cmd_rover_rev;
				}else{
					motors_forward = cmd_back;			//new motors run in opposite directions
					motors_backward = cmd_forward;
					motors_right = cmd_left;
					motors_left = cmd_right;
					rover_mode = cmd_rover_rev;
					rover_mode_rev = cmd_rover;
				}
			}
		});
		viewModel.isDeviceReady().observe(this, deviceReady -> {
			progressContainer.setVisibility(View.GONE);
			content.setVisibility(View.VISIBLE);
		});
		//viewModel.getConnectionState().observe(this, connectionState::setText);
		viewModel.isConnected().observe(this, connected -> {
			if (!connected) {
				finish();
			}
		});
		viewModel.getCMDState().observe(this, mCMDState -> {
			switch (mCMDState.byteValue()) {
				case 0x10:
					disText.setText(String.format("Right Command"));
					break;
				case 0x11:
					disText.setText(String.format("Left Command"));
					break;
				case 0x12:
					disText.setText(String.format("Forward Command"));
					break;
				case 0x13:
					disText.setText(String.format("Back Command"));
					break;
				case 0x14:
					disText.setText(String.format("Stop Command"));
					break;
				case 0x16:
					disText.setText(String.format("Step Mode Increase Command"));
					break;
				case 0x17:
					disText.setText(String.format("Buzzer Command"));
					break;
				case 0x22:
					disText.setText(String.format("Distance Command"));
					break;
				case 0x21:
					disText.setText(String.format("Ambient Light Command"));
					break;
				case 0x40:
					disText.setText(String.format("Rover Mode Command"));
					break;
				case 0x42:
					disText.setText(String.format("Rover Mode Reverse Command"));
					break;
				case 0x41:
					disText.setText(String.format("Photovore Mode Command"));
					break;
				case 0x30:
					disText.setText(String.format("Record Sound Command"));
					break;
				default:
					disText.setText(String.format("Command %x", mCMDState.byteValue()));
					break;
			}
		});
		viewModel.getByte2State().observe(this, mBYTE2State -> {
			byte[] val = mBYTE2State.clone();
			int valint, valtemp = 0;
			if (val[1] < 0)
				valint = val[1] + 256;
			else
				valint = val[1];
			if (val[0] < 0) {
				valtemp = val[0] + 256;
			} else {
				valtemp = val[0];
			}
			valint += (valtemp << 8);
			ambText.setText(String.format("Ambient Light is %d LUX", valint));
			Log.d("BlinkyActivity", "Wrote notify bytes " + val.length);
		});
		/*	This is the single bytes characteristic
			It it used for the distance measurement, which is 0-255, this is active with dataState = 0
			It is also used as a flag for sound file upload, dataState = 1:
			0 - recording
			255 - recording done, ready for upload
			127 - upload complete
			It is also used to report the stepping mode - dataState = 2
		*/
		viewModel.getDATAState().observe(this, mDATAState -> {
			int val = mDATAState.byteValue();
			if (val < 0)
				val += 256;
			if (dataState == 0) {
				double inches = val / 20.0;
				msgString.setText(String.format("Distance raw is %d, %6.3f inches", val, inches));
			}
			if (dataState == 1) {
				double inches = val / 20.0;
				msgString.setText(String.format("Distance raw is %d, %6.3f inches", val, inches));
			}
			dataState = 0;
			Log.d("BlinkyActivity", "Received data " + String.format("val=%d,dataState=%d",val,dataState));
		});

	    submenu.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent myIntent = new Intent(BlinkyActivity.this, SubActivity.class);
				myIntent.putExtra(BlinkyActivity.EXTRA_DEVICE, device);
				BlinkyActivity.this.startActivity(myIntent);
			}
		});

		RC.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent myIntent = new Intent(BlinkyActivity.this, MainActivity.class);
				myIntent.putExtra(BlinkyActivity.EXTRA_DEVICE, device);
				BlinkyActivity.this.startActivity(myIntent);
			}
		});
	    left.setOnTouchListener(new View.OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent event) {

                int action = event.getAction();
                int direction = 5;

                switch (action) {

                    case MotionEvent.ACTION_DOWN:
                        if (repeatingpress == 0)
                        {
                            viewModel.sendCMD(motors_left);
                        }
                        repeatingpress = 1;
                        break;

                    case MotionEvent.ACTION_MOVE:
                        break;

                    case MotionEvent.ACTION_UP:
                        viewModel.sendCMD(cmd_stopturn);
                        repeatingpress = 0;
                        break;

                    default:
                        return false;
                }

                // avoids eating the event chain
                return true;
            }
        });
        right.setOnTouchListener(new View.OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent event) {

                int action = event.getAction();
                int direction = 5;

                switch (action) {

                    case MotionEvent.ACTION_DOWN:
                        if (repeatingpress == 0)
                        {
                            viewModel.sendCMD(motors_right);
                        }
                        repeatingpress = 1;
                        break;

                    case MotionEvent.ACTION_MOVE:
                        break;

                    case MotionEvent.ACTION_UP:
                        viewModel.sendCMD(cmd_stopturn);
                        repeatingpress = 0;
                        break;

                    default:
                        return false;
                }

                // avoids eating the event chain
                return true;
            }
        });
    }

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:
				onBackPressed();
				return true;
		}
		return false;
	}
}
