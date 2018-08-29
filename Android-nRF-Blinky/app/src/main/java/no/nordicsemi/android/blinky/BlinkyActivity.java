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

import no.nordicsemi.android.log.LogContract;
import no.nordicsemi.android.blinky.adapter.ExtendedBluetoothDevice;
import no.nordicsemi.android.blinky.viewmodels.BlinkyViewModel;
import no.nordicsemi.android.log.Logger;

public class BlinkyActivity extends AppCompatActivity {
	public static final String EXTRA_DEVICE = "no.nordicsemi.android.blinky.EXTRA_DEVICE";
	public static final int MY_PERMISSIONS_REQUEST_STORAGE = 100;

	final private byte cmd_right = 0x10;
	final private byte cmd_left  = 0x11;
	final private byte cmd_forward = 0x12;
	final private byte cmd_back  = 0x13;
	final private byte cmd_stop  = 0x14;
	final private byte cmd_stopturn = 0x15;
	final private byte cmd_buzzer = 0x17;
	final private byte cmd_distance = 0x22;
	final private byte cmd_ambient = 0x21;
	final private byte cmd_record = 0x30;
	final private byte cmd_record_pi = 0x33;
	final private byte cmd_stepmode = 0x16;
	final private byte cmd_rover = 0x40;
	final private byte cmd_rover_rev = 0x42;
	final private byte cmd_photov = 0x41;

	final private int total_20byte_packets = 1200;

    private AudioTrackPlayer myplayer;
    private FileOutputStream out = null;
    private File file = null;
    private int dataState = 0;
    private int runningtotal = 0;
    private int repeatingpress = 0;
	private byte motors_forward = cmd_forward, motors_backward = cmd_back;
	private byte motors_left = cmd_left, motors_right = cmd_right;
	private byte rover_mode = cmd_rover, rover_mode_rev = cmd_rover_rev;
	private int records_pi_ready = 0;
	private BlinkyViewModel viewModel = null;
	private int mInterval = 20; // 20 milliseconds for BLE connection interval
	private Handler mHandler;

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
		final Button roverrev = findViewById(R.id.roverrev);
		final Button stepmode = findViewById(R.id.step_mode);
		final Button play = findViewById(R.id.play);
		final Button photov = findViewById(R.id.photovore);
		final Button buzzer = findViewById(R.id.buzzer);
		final Button record = findViewById(R.id.record);
		final Button recordpi = findViewById(R.id.record_pi);
		final Button RC = findViewById(R.id.RC);
		final Switch dirsw = findViewById(R.id.switchmodes);
		final TextView disText = findViewById(R.id.distanceText);
		final TextView ambText = findViewById(R.id.ambientText);

		//right.setOnClickListener(view -> viewModel.sendCMD(Byte.valueOf((byte) 0x10)));
		//left.setOnClickListener(view -> viewModel.sendCMD(Byte.valueOf((byte) 0x11)));
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
		viewModel.getConnectionState().observe(this, connectionState::setText);
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
				switch (val) {
					case 0:
						msgString.setText(String.format("Recording .."));
						Log.d("BlinkyActivity", "Received 0 recording ");
						break;
					case 255:
						msgString.setText(String.format("Uploading Sound File ..."));
						records_pi_ready = 1;
						Log.d("BlinkyActivity", "Received 255 uploading ");
						break;
					case 127:
						msgString.setText(String.format("Upload Sound File Complete."));
						dataState = 0;
						runningtotal = 0;
						Log.d("BlinkyActivity", "Received 127 upload complete ");
						break;
					default:
						double inches = val / 20.0;
						msgString.setText(String.format("Distance raw is %d, %6.3f inches", val, inches));
						break;
				}
			}
			if (dataState == 2) {
				switch (val) {
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
			Log.d("BlinkyActivity", "Received data " + String.format("val=%d,dataState=%d",val,dataState));
		});

		//dataState must be 1 for notifications and reads- to be written to the sound file- on this characteristic
		//The firmware is 16k 16-bit values, or 32k bytes. This is 1638.4 packets, or 1638 packets
		viewModel.getByte128State().observe(this, mBYTE128State -> {
			byte[] val = mBYTE128State.clone();
			try {
				if (out != null && dataState == 1) {
					out.write(val);
					++runningtotal;
					if (runningtotal == 1638) {
						ambText.setText(String.format("DONE: Packets Uploaded %d, %d bytes", runningtotal, runningtotal * val.length));
						records_pi_ready = 2;
					} else {
						if ((runningtotal % 10) == 0)
							ambText.setText(String.format("Packets Uploaded %d, %d bytes", runningtotal, runningtotal * val.length));
					}
					Log.d("BlinkyActivity", String.format("20: %x %x %x %x %x %x len = %d total=%d",val[0],val[1],val[2],val[3],val[4],val[5],val.length,runningtotal));
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		});

		stepmode.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				dataState = 2;
				viewModel.sendCMD(cmd_stepmode);
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
		/*  This is the original record that uses notifications

			The single byte characteristic is used as a flag, this is also a notification
			0 - recording
			255 - recording done, ready for upload
			127 - upload complete, no more data

			The sound file itself is read 20 byte at a time. It is a total of 1638 20byte notifies
		*/
		record.setOnClickListener(new View.OnClickListener() {
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
				viewModel.sendCMD(cmd_record);            //Does 2s record then ships data back through notification, saves to file sound.wav
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
		/*  The point of record pi is not to use notifications, but to use reads

			The single byte characteristic is used as a flag
			0 - recording
			255 - recording done, ready for upload
			127 - upload complete, no more data

			The sound file itself is read 20 byte at a time. It is a total of 1638 20byte reads
		*/
		recordpi.setOnClickListener(new View.OnClickListener() {
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
				records_pi_ready = 0;
				viewModel.sendCMD(cmd_record_pi);
				Context context = getApplicationContext();
				file = new File(context.getFilesDir(), "sound.wav");
				Log.d("BlinkyActivity", "Sent record pi opened file");
				int i;
				try {
					out = new FileOutputStream(file);
					mHandler = new Handler();
					startRepeatingReads();
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				}
			}
		});
		play.setOnClickListener(new View.OnClickListener() {
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
	public void onDestroy() {
		super.onDestroy();
		stopRepeatingTask();
	}

	Runnable mReadRepeat = new Runnable() {
		@Override
		public void run() {
			try {
				if (records_pi_ready == 0)
					viewModel.readdata();
				if (records_pi_ready == 1) {        //ready to upload
					viewModel.read20bytedata();
					viewModel.readdata();
				}
				if (records_pi_ready == 2)
					stopRepeatingTask();
			} finally {
				if (mReadRepeat != null)
					mHandler.postDelayed(mReadRepeat, mInterval);
			}
		}
	};

	public void startRepeatingReads() {
		mReadRepeat.run();
	}

	public void stopRepeatingTask() {
		if (mReadRepeat != null)
			mHandler.removeCallbacks(mReadRepeat);
		mReadRepeat = null;
	}

    public void readpi()
	{
		while(true)
		{
			viewModel.readdata();
			if (records_pi_ready == 1)			//ready to upload
				break;
		}
		while(true)
		{
			viewModel.read20bytedata();			//submit reads of sound data file
			if (records_pi_ready == 2) {        //flag set when finished uploading
				viewModel.readdata();			//read to trigger finish in data
				break;
			}
		}
		return;
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
