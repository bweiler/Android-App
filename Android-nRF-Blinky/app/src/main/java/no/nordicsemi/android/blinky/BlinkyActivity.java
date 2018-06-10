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

import android.arch.lifecycle.ViewModelProviders;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Button;

import no.nordicsemi.android.blinky.adapter.ExtendedBluetoothDevice;
import no.nordicsemi.android.blinky.viewmodels.BlinkyViewModel;

public class BlinkyActivity extends AppCompatActivity {
	public static final String EXTRA_DEVICE = "no.nordicsemi.android.blinky.EXTRA_DEVICE";
	AudioTrackPlayer myplayer;

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
		final TextView buttonState = findViewById(R.id.button_state);
		final LinearLayout progressContainer = findViewById(R.id.progress_container);
		final TextView connectionState = findViewById(R.id.connection_state);
		final View content = findViewById(R.id.device_container);

		final Button left = findViewById(R.id.left);
		final Button forward = findViewById(R.id.forward);
		final Button back = findViewById(R.id.backward);
		final Button right = findViewById(R.id.right);
		final Button stop = findViewById(R.id.stop);
		final Button distance = findViewById(R.id.distance);
		final Button rover = findViewById(R.id.rover);
		final Button stepmode = findViewById(R.id.step_mode);
		final Button play = findViewById(R.id.play);
		final Button stopplay = findViewById(R.id.stopplay);
		final Button buzzer = findViewById(R.id.buzzer);
		final Button record = findViewById(R.id.record);
		final Button RC = findViewById(R.id.RC);
		final TextView disText = findViewById(R.id.distanceText);

		right.setOnClickListener(view -> viewModel.sendCMD(Byte.valueOf((byte)0x10)));
		left.setOnClickListener(view -> viewModel.sendCMD(Byte.valueOf((byte)0x11)));
		forward.setOnClickListener(view -> viewModel.sendCMD(Byte.valueOf((byte)0x12)));
		back.setOnClickListener(view -> viewModel.sendCMD(Byte.valueOf((byte)0x13)));
		stop.setOnClickListener(view -> viewModel.sendCMD(Byte.valueOf((byte)0x14)));
		stepmode.setOnClickListener(view -> viewModel.sendCMD(Byte.valueOf((byte)0x16)));
		buzzer.setOnClickListener(view -> viewModel.sendCMD(Byte.valueOf((byte)0x17)));
		distance.setOnClickListener(view -> viewModel.sendCMD(Byte.valueOf((byte)0x22)));
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
		    int val = mCMDState.byteValue();
		    if (val < 0)
		        val += 256;
		    disText.setText(String.format("%d",val));
		});
		viewModel.getDATAState().observe(this, mDATAState -> { buttonState.setText(String.format("%d",mDATAState.byteValue())); });

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
				viewModel.sendCMD(Byte.valueOf((byte)0x30));			//Does 2s record then ships data back through notification, saves to file sound.wav
			}
		});
		play.setOnClickListener( new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				myplayer = new AudioTrackPlayer();
				myplayer.prepare("sound.wav");
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
	public boolean onOptionsItemSelected(final MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:
				onBackPressed();
				return true;
		}
		return false;
	}
}
