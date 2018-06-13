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

package no.nordicsemi.android.blinky;

import android.Manifest;
import android.arch.lifecycle.ViewModelProviders;
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
		final TextView DATAState = findViewById(R.id.button_state);
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
		final Button stopplay = findViewById(R.id.stopplay);
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
		stepmode.setOnClickListener(view -> viewModel.sendCMD(Byte.valueOf((byte)0x16)));
		buzzer.setOnClickListener(view -> viewModel.sendCMD(Byte.valueOf((byte)0x17)));
		distance.setOnClickListener(view -> viewModel.sendCMD(Byte.valueOf((byte)0x22)));
		ambient.setOnClickListener(view -> viewModel.sendCMD(Byte.valueOf((byte)0x21)));
		rover.setOnClickListener(view -> viewModel.sendCMD(Byte.valueOf((byte)0x40)));

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
			disText.setText(String.format("%x",mCMDState.byteValue()));
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
			ambText.setText(String.format("%d",valint));
			Log.d("BlinkyActivity", "Wrote notify bytes " + val.length);
		});
		viewModel.getDATAState().observe(this, mDATAState -> {
			int val = mDATAState.byteValue();
			if (val < 0)
				val += 256;
			DATAState.setText(String.format("%d",val));
		});

		viewModel.getByte128State().observe(this, mBYTE128State -> {
			byte[] val = mBYTE128State.clone();
			try {
				out.write(val);
				Log.d("BlinkyActivity", "Wrote notify bytes " + val.length);
			} catch (IOException e) {
				e.printStackTrace();
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
                viewModel.sendCMD(Byte.valueOf((byte) 0x30));            //Does 2s record then ships data back through notification, saves to file sound.wav
				file = new File("/sdcard/audio/sound.wav");
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
				myplayer = new AudioTrackPlayer();
				myplayer.prepare("/sdcard/Audio/sound.wav");
				myplayer.play();							//this loops
			}
		});
		stopplay.setOnClickListener( new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				myplayer.stop();
				myplayer = null;
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
