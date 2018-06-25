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

		1. This app sends commands to Skoobot and receives notifications over BLE
		2. When you hit the record button for audio record, these is 2s of recording on the robot. Then it uploads to the cellphone over BLE.
		The total size of the 2s recording is 32k. After it uploads, pressing the play button will play it.
		3. Fotovore mode will cause the robot to go formward when shining a light on it
		4. Rover mode will ignore obstacles about 2.5 inches in front

		Details:

 */
package no.nordicsemi.android.blinky;

import android.Manifest;
import android.arch.lifecycle.ViewModelProviders;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Button;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import no.nordicsemi.android.log.LogContract;
import no.nordicsemi.android.blinky.adapter.ExtendedBluetoothDevice;
import no.nordicsemi.android.blinky.viewmodels.BlinkyViewModel;
import no.nordicsemi.android.log.Logger;

public class BlinkyActivity extends AppCompatActivity {
	public static final String EXTRA_DEVICE = "no.nordicsemi.android.blinky.EXTRA_DEVICE";
	public static final int MY_PERMISSIONS_REQUEST_STORAGE = 100;

    AudioTrackPlayer myplayer;
	FileOutputStream out = null;
	File file = null;
	int dataState = 0;
	int runningtotal = 0;

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
		final BlinkyViewModel viewModel = ViewModelProviders.of(this).get(BlinkyViewModel.class);
		viewModel.connect(device);

		// Set up views
		final TextView msgString = findViewById(R.id.button_state);
		final LinearLayout progressContainer = findViewById(R.id.progress_container);
		final TextView connectionState = findViewById(R.id.connection_state);
		final View content = findViewById(R.id.device_container);

		final Button left = findViewById(R.id.left);
		final Button forward = findViewById(R.id.forward);
		final Button back = findViewById(R.id.backward);
		final Button right = findViewById(R.id.right);
		final Button stop = findViewById(R.id.stop);
		final Button distance = findViewById(R.id.distance);
		final Button ambient = findViewById(R.id.ambient);
		final Button rover = findViewById(R.id.rover);
		final Button stepmode = findViewById(R.id.step_mode);
		final Button play = findViewById(R.id.play);
		final Button photov = findViewById(R.id.photovore);
		final Button buzzer = findViewById(R.id.buzzer);
		final Button record = findViewById(R.id.record);
		final Button RC = findViewById(R.id.RC);
		final TextView disText = findViewById(R.id.distanceText);
		final TextView ambText = findViewById(R.id.ambientText);

		right.setOnClickListener(view -> viewModel.sendCMD(Byte.valueOf((byte)0x10)));
		left.setOnClickListener(view -> viewModel.sendCMD(Byte.valueOf((byte)0x11)));
		forward.setOnClickListener(view -> viewModel.sendCMD(Byte.valueOf((byte)0x12)));
		back.setOnClickListener(view -> viewModel.sendCMD(Byte.valueOf((byte)0x13)));
		stop.setOnClickListener(view -> viewModel.sendCMD(Byte.valueOf((byte)0x14)));
		buzzer.setOnClickListener(view -> viewModel.sendCMD(Byte.valueOf((byte)0x17)));
		distance.setOnClickListener(view -> viewModel.sendCMD(Byte.valueOf((byte)0x22)));
		ambient.setOnClickListener(view -> viewModel.sendCMD(Byte.valueOf((byte)0x21)));
		rover.setOnClickListener(view -> viewModel.sendCMD(Byte.valueOf((byte)0x40)));
		photov.setOnClickListener(view -> viewModel.sendCMD(Byte.valueOf((byte)0x41)));

		viewModel.isDeviceReady().observe(this, deviceReady -> {
			progressContainer.setVisibility(View.GONE);
			content.setVisibility(View.VISIBLE);
		});
		viewModel.getConnectionState().observe(this, connectionState::setText);
		viewModel.isConnected().observe(this, connected -> {
			if (!connected) {
				finish();
			}
		});
		viewModel.getCMDState().observe(this, mCMDState -> {
			switch(mCMDState.byteValue())
			{
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
				case 0x41:
					disText.setText(String.format("Photovore Mode Command"));
					break;
				case 0x30:
					disText.setText(String.format("Record Sound Command"));
					break;
				default:
					disText.setText(String.format("Command %x",mCMDState.byteValue()));
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
			}else{
				valtemp = val[0];
			}
			valint += (valtemp << 8);
			ambText.setText(String.format("Ambient Light is %d LUX",valint));
			Log.d("BlinkyActivity", "Wrote notify bytes " + val.length);
		});
		viewModel.getDATAState().observe(this, mDATAState -> {
			int val = mDATAState.byteValue();
			if (val < 0)
				val += 256;
			if (dataState == 0) {
				double inches = val / 20.0;
				msgString.setText(String.format("Distance raw is %d, %6.3f inches", val, inches));
			}
			if (dataState == 1)
			{
				switch(val) {
					case 0:
						msgString.setText(String.format("Recording .."));
						break;
					case 255:
						msgString.setText(String.format("Uploading Sound File ..."));
						break;
					case 127:
						msgString.setText(String.format("Upload Sound File Complete."));
						dataState = 0;
						runningtotal = 0;
						break;
					default:
						double inches = val / 20.0;
						msgString.setText(String.format("Distance raw is %d, %6.3f inches", val, inches));
						break;
				}
			}
			if (dataState == 2)
			{
				switch(val)
				{
					case 0:
						msgString.setText(String.format("Switch to Full Step"));
						break;
					case 1:
						msgString.setText(String.format("Switch to Half Step"));
						break;
					case 2:
						msgString.setText(String.format("Switch to Quarter Step"));
						break;
					case 3:
						msgString.setText(String.format("Switch to 8th Stepping"));
						break;
					case 4:
						msgString.setText(String.format("Switch to 16th Stepping"));
						break;
					case 5:
						msgString.setText(String.format("Switch to 32th Stepping"));
						break;
					default:
						msgString.setText(String.format("Oh no a bug in stepping mode, crap!"));
						break;
				}
				dataState = 0;
			}
		});

		viewModel.getByte128State().observe(this, mBYTE128State -> {
			byte[] val = mBYTE128State.clone();
			try {
				out.write(val);
				++runningtotal;
				if ((runningtotal % 10) == 0)
					ambText.setText(String.format("Packets Uploaded %d, %d bytes",runningtotal,runningtotal*val.length));
				Log.d("BlinkyActivity", "Wrote notify bytes " + val.length);
			} catch (IOException e) {
				e.printStackTrace();
			}
		});

		stepmode.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				dataState = 2;
				viewModel.sendCMD(Byte.valueOf((byte)0x16));
			}
		});

		RC.setOnClickListener( new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent myIntent = new Intent(BlinkyActivity.this, MainActivity.class);
				myIntent.putExtra(BlinkyActivity.EXTRA_DEVICE, device);
				BlinkyActivity.this.startActivity(myIntent);
			}
		});
		record.setOnClickListener( new View.OnClickListener() {
			@Override
			public void onClick(View v) {
            /*
            I went into App for Skoobot permissions and checked storage
            Here, thisActivity is the current activity
            */
                if (ContextCompat.checkSelfPermission(BlinkyActivity.this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
                        // No explanation needed; request the permission
                    ActivityCompat.requestPermissions(BlinkyActivity.this,
                            new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                            MY_PERMISSIONS_REQUEST_STORAGE);
                    ActivityCompat.requestPermissions(BlinkyActivity.this,
                            new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                            MY_PERMISSIONS_REQUEST_STORAGE);
                }
                dataState = 1;
                viewModel.sendCMD(Byte.valueOf((byte) 0x30));            //Does 2s record then ships data back through notification, saves to file sound.wav
				Context context = getApplicationContext();
				file = new File(context.getFilesDir(), "sound.wav");
				Log.d("BlinkyActivity", "Sent record opened file");

				try {
					out = new FileOutputStream(file);
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				}
			}
		});
		play.setOnClickListener( new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				dataState = 0;
				try {
					if (out != null) {
						out.close();
						out = null;
						file = null;
						Log.d("BlinkyActivity", "Closed file");
					}
				} catch (IOException e) {
					e.getMessage();
				}
				Context context = getApplicationContext();
				file = new File(context.getFilesDir(), "sound.wav");
				if (file.exists()) {
					myplayer = new AudioTrackPlayer();
					myplayer.prepare(file);
					myplayer.play();                            //this loops
				}
			}
		});
  	}

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_STORAGE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return;
            }

            // other 'case' lines to check for other
            // permissions this app might request.
        }
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
