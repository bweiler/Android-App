package no.nordicsemi.android.blinky;


import android.Manifest;
import android.arch.lifecycle.ViewModelProviders;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognizerIntent;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

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
public class SubActivity extends AppCompatActivity {

    public static final int MY_PERMISSIONS_REQUEST_STORAGE = 100;
    private static final int SPEECH_REQUEST_CODE = 0;

    final private byte cmd_record = 0x30;
    final private byte cmd_record_pi = 0x33;
    final private byte cmd_stepmode = 0x16;
    final private byte cmd_con_dis	= 0x23;
    final private byte motors_speed = 0x50;
    final private byte cmd_dft = 0x51;

    final private byte cmd_right_30 = 0x08;
    final private byte cmd_left_30  = 0x09;
    final private byte cmd_forward = 0x12;
    final private byte cmd_back  = 0x13;
    final private byte cmd_stop  = 0x14;
    final private byte cmd_buzzer = 0x17;

    final private int total_20byte_packets = 1200;

    private AudioTrackPlayer myplayer;
    private FileOutputStream out = null;
    private File file = null;
    private int dataState = 0;
    private int runningtotal = 0;
    private int mSpeakInterval = 100;
    private boolean mSpeakReady = true;
    private int records_pi_ready = 0;
    private BlinkyViewModel viewModel = null;
    private int mInterval = 20; // 20 milliseconds for BLE connection interval
    private Handler mHandler = null;
    private Handler mSpeakHandler = null;
    private TextView textSpeed;
    private TextView dispText;
    private Button quitActivity;
    private Button saveSpeed;

    private ViewGroup controlsContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sub);

        textSpeed = findViewById(R.id.speedText);
        saveSpeed = findViewById(R.id.speed);
        dispText = findViewById(R.id.dispText);
        quitActivity = findViewById(R.id.quit);

        final Intent intent = getIntent();
        final ExtendedBluetoothDevice device = intent.getParcelableExtra(BlinkyActivity.EXTRA_DEVICE);
        final String deviceName = device.getName();
        final String deviceAddress = device.getAddress();
        viewModel = ViewModelProviders.of(this).get(BlinkyViewModel.class);
        viewModel.connect(device);

        final Button stepmode = findViewById(R.id.step_mode);
        final Button play = findViewById(R.id.play);
        final Button record = findViewById(R.id.record);
        final Button recordpi = findViewById(R.id.record_pi);
        final Button con_dis = findViewById(R.id.con_dis);
        final Button speak = findViewById(R.id.speech);
        final Button do_dft = findViewById(R.id.dft);

        Context context = getApplicationContext();
        file = new File(context.getFilesDir(), "speed.dat");
        FileInputStream in = null;
        byte[] sendspeed = new byte[4];
        int byte1, byte2;
        int bytesread = 0;
        try {
            in = new FileInputStream(file);
            if (in != null) {
                bytesread = in.read(sendspeed);
                in.close();
                if (bytesread == 4) {
                    byte1 = sendspeed[1]<<8;
                    byte2 = sendspeed[2];
                    if (byte1 < 0)
                        byte1 += 256;
                    if (byte2 < 0)
                        byte2 += 256;
                    int speed_val = byte1 + byte2;
                    textSpeed.setText(Integer.toString(speed_val));
                }else{
                    textSpeed.setText("1000");
                }
                file = null;
            }
            Log.d("SubActivity", String.format("Bytes read %d",bytesread));
        }
        catch (IOException e) {
            Log.d("SubActivity", "Failed to read speed file ");
        }

        con_dis.setOnClickListener(view -> viewModel.sendCMD(cmd_con_dis));
        do_dft.setOnClickListener(view -> viewModel.sendCMD(cmd_dft));

        saveSpeed.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                /*
                    I went into App for Skoobot permissions and checked storage
                    Here, thisActivity is the current activity
                */
                if (ContextCompat.checkSelfPermission(SubActivity.this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
                    // No explanation needed; request the permission
                    ActivityCompat.requestPermissions(SubActivity.this,
                            new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                            MY_PERMISSIONS_REQUEST_STORAGE);
                    ActivityCompat.requestPermissions(SubActivity.this,
                            new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                            MY_PERMISSIONS_REQUEST_STORAGE);
                }
                Context context = getApplicationContext();
                file = new File(context.getFilesDir(), "speed.dat");
                int speed_val = Integer.parseInt(textSpeed.getText().toString());
                if (speed_val <= 0 || speed_val > 5000)
                {
                    dispText.setText(String.format("Crazy speed, not saved .."));
                    return;
                }
                dispText.setText(String.format("Setting speed to %d",speed_val));
                byte[] sendspeed = new byte[4];
                sendspeed[0] = motors_speed;
                sendspeed[1] = (byte)(speed_val >> 8 & 0xff);
                sendspeed[2] = (byte)(speed_val & 0xff);
                sendspeed[3] = 0;
                viewModel.send4Byte(sendspeed);
                Log.d("SubActivity", String.format("Sending speed %d",speed_val));
                try {
                    out = new FileOutputStream(file);
                    if (out != null) {
                        out.write(sendspeed);
                        out.close();
                        out = null;
                        file = null;
                        Log.d("SubActivity", "Wrote speed");
                    }
                }
                catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
            }
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
            if (dataState == 1) {
                switch (val) {
                    case 0:
                        dispText.setText(String.format("Recording .."));
                        Log.d("BlinkyActivity", "Received 0 recording ");
                        break;
                    case 255:
                        dispText.setText(String.format("Uploading Sound File ..."));
                        records_pi_ready = 1;
                        Log.d("BlinkyActivity", "Received 255 uploading ");
                        break;
                    case 127:
                        dispText.setText(String.format("Upload Sound File Complete."));
                        dataState = 0;
                        runningtotal = 0;
                        Log.d("BlinkyActivity", "Received 127 upload complete ");
                        break;
                }
            }
            if (dataState == 2) {
                switch (val) {
                    case 0:
                        dispText.setText(String.format("Switch to Full Step"));
                        break;
                    case 1:
                        dispText.setText(String.format("Switch to Half Step"));
                        break;
                    case 2:
                        dispText.setText(String.format("Switch to Quarter Step"));
                        break;
                    case 3:
                        dispText.setText(String.format("Switch to 8th Stepping"));
                        break;
                    case 4:
                        dispText.setText(String.format("Switch to 16th Stepping"));
                        break;
                    case 5:
                        dispText.setText(String.format("Switch to 32th Stepping"));
                        break;
                    default:
                        dispText.setText(String.format("Oh no a bug in stepping mode, crap!"));
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
                        dispText.setText(String.format("DONE: Packets Uploaded %d, %d bytes", runningtotal, runningtotal * val.length));
                        records_pi_ready = 2;
                    } else {
                        if ((runningtotal % 10) == 0)
                            dispText.setText(String.format("Packets Uploaded %d, %d bytes", runningtotal, runningtotal * val.length));
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
                if (ContextCompat.checkSelfPermission(SubActivity.this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
                    // No explanation needed; request the permission
                    ActivityCompat.requestPermissions(SubActivity.this,
                            new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                            MY_PERMISSIONS_REQUEST_STORAGE);
                    ActivityCompat.requestPermissions(SubActivity.this,
                            new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                            MY_PERMISSIONS_REQUEST_STORAGE);
                }
                dataState = 1;
                viewModel.sendCMD(cmd_record);            //Does 2s record then ships data back through notification, saves to file sound.wav
                Context context = getApplicationContext();
                file = new File(context.getFilesDir(), "sound.wav");
                Log.d("SubActivity", "Sent record opened file");

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
                if (ContextCompat.checkSelfPermission(SubActivity.this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
                    // No explanation needed; request the permission
                    ActivityCompat.requestPermissions(SubActivity.this,
                            new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                            MY_PERMISSIONS_REQUEST_STORAGE);
                    ActivityCompat.requestPermissions(SubActivity.this,
                            new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                            MY_PERMISSIONS_REQUEST_STORAGE);
                }
                dataState = 1;
                records_pi_ready = 0;
                viewModel.sendCMD(cmd_record_pi);
                Context context = getApplicationContext();
                file = new File(context.getFilesDir(), "sound.wav");
                Log.d("SubActivity", "Sent record pi opened file");
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
		/*
			This will repeat asking for words every mSpeakInterval (currently 100ms).
			provided that it has previously returned a word and is ready.
			Saying "Quit" or hitting a button ends speech recognition.
		*/
        speak.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mSpeakHandler = new Handler();
                startRepeatingSpeech();
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
                        Log.d("SubActivity", "Closed file");
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
        quitActivity.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }
    // This callback is invoked when the Speech Recognizer returns.
    // This is where you process the intent and extract the speech text from the intent.
    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent data) {
        if (requestCode == SPEECH_REQUEST_CODE && resultCode == RESULT_OK) {
            List<String> results = data.getStringArrayListExtra(
                    RecognizerIntent.EXTRA_RESULTS);
            String spokenText = results.get(0);
            if (spokenText.equalsIgnoreCase(new String("Forward")))
            {
                viewModel.sendCMD(cmd_forward);
            }
            if (spokenText.equalsIgnoreCase(new String("Backward")))
            {
                viewModel.sendCMD(cmd_back);
            }
            if (spokenText.equalsIgnoreCase(new String("Left")))
            {
                viewModel.sendCMD(cmd_left_30);
            }
            if (spokenText.equalsIgnoreCase(new String("Right")))
            {
                viewModel.sendCMD(cmd_right_30);
            }
            if (spokenText.equalsIgnoreCase(new String("Stop")))
            {
                viewModel.sendCMD(cmd_stop);
            }
            if (spokenText.equalsIgnoreCase(new String("Buzzer")))
            {
                viewModel.sendCMD(cmd_buzzer);
            }
            if (spokenText.equalsIgnoreCase(new String("Quit")))
            {
                stopRepeatingSpeech();
                viewModel.sendCMD(cmd_stop);
            }
            Log.d("BlinkyActivitySpeech", spokenText);
        }
        super.onActivityResult(requestCode, resultCode, data);
        mSpeakReady = true;
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
                if (mHandler != null)
                    mHandler.postDelayed(mReadRepeat, mInterval);
            }
        }
    };

    public void startRepeatingReads() {
        mReadRepeat.run();
    }

    public void stopRepeatingTask() {
        if (mReadRepeat != null && mHandler != null)
            mHandler.removeCallbacks(mReadRepeat);
        mHandler = null;
    }

    Runnable mSpeakRepeat = new Runnable() {
        @Override
        public void run() {
            try {
                if (mSpeakReady == true) {
                    mSpeakReady = false;
                    // Create an intent that can start the Speech Recognizer activity
                    Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                    intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                    // Start the activity, the intent will be populated with the speech text
                    startActivityForResult(intent, SPEECH_REQUEST_CODE);
                }
            } finally {
                if (mSpeakHandler != null)
                    mSpeakHandler.postDelayed(mSpeakRepeat, mSpeakInterval);
            }
        }
    };

    public void startRepeatingSpeech() {
        mSpeakRepeat.run();
    }

    public void stopRepeatingSpeech() {
        if (mSpeakRepeat != null && mSpeakHandler != null)
            mSpeakHandler.removeCallbacks(mSpeakRepeat);
        mSpeakHandler = null;
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
