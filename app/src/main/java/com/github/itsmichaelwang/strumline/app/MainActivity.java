package com.github.itsmichaelwang.strumline.app;

import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.util.concurrent.TimeUnit;

public class MainActivity extends ActionBarActivity implements View.OnClickListener{

    private static final int SELECT_FILE_REQUEST = 1;

    // UI elements
    private Button btnSongSelect;
    private Button btnSetLoopStart;
    private Button btnSetLoopStop;

    private EditText txtCurPos;         // information fields
    private EditText txtLoopStart;
    private EditText txtLoopStop;

    private TextView txtViewCurPos;     // labels for the information fields
    private TextView txtViewLoopStart;
    private TextView txtViewLoopStop;

    private boolean firstLoad = true;   // keep track of first load for special instruction
    private MediaPlayer mediaPlayer = null;

    private RangeSeekBar<Integer> seekBar = null;
    private int loopStart = 0;              // start and stop time of the loop, in ms from song start
    private int loopStop;

    // Link handler to Main/UI thread for UI operations later
    private Handler mHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Instantiate UI elements
        btnSongSelect = (Button) findViewById(R.id.btn_song_select);
        btnSetLoopStart = (Button) findViewById(R.id.btn_set_loop_start);
        btnSetLoopStop = (Button) findViewById(R.id.btn_set_loop_stop);

        txtCurPos = (EditText) findViewById(R.id.txt_cur_pos);
        txtLoopStart = (EditText) findViewById(R.id.txt_loop_start);
        txtLoopStop = (EditText) findViewById(R.id.txt_loop_stop);

        txtViewCurPos = (TextView) findViewById(R.id.txtView_cur_position);
        txtViewLoopStart = (TextView) findViewById(R.id.txtView_loop_start);
        txtViewLoopStop = (TextView) findViewById(R.id.txtView_loop_stop);

        // Set button listeners (see onClick() below)
        btnSongSelect.setOnClickListener(this);
        btnSetLoopStart.setOnClickListener(this);
        btnSetLoopStop.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_song_select:
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("audio/*");
                startActivityForResult(intent, SELECT_FILE_REQUEST);
                break;
            case R.id.btn_set_loop_start:
                // Set current position as new loopStart
                int loopStart = mediaPlayer.getCurrentPosition();
                updateLoopBounds(loopStart, this.loopStop);
                seekBar.setSelectedMinValue(loopStart);
                break;
            case R.id.btn_set_loop_stop:
                // Set current position as new loopStop
                int loopStop = mediaPlayer.getCurrentPosition();
                updateLoopBounds(this.loopStart, loopStop);
                seekBar.setSelectedMaxValue(loopStop);
                break;
        }
    }

    // Actual data handling
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case SELECT_FILE_REQUEST:
                if (resultCode == RESULT_OK) {
                    if (firstLoad) {
                        showInterface();
                        firstLoad = false;
                    }

                    Context context = this.getApplicationContext();
                    Uri myUri = data.getData();
                    // If the mediaPlayer is already being used, reset it when selecting a new song
                    if (mediaPlayer != null) {
                        mediaPlayer.stop();
                        mediaPlayer.release();
                        mediaPlayer = null;
                    }
                    mediaPlayer = MediaPlayer.create(context, myUri);
                    mediaPlayer.start();

                    // Create a SeekBar with the width of the song's length
                    int songLength = mediaPlayer.getDuration();
                    seekBar = new RangeSeekBar<Integer>(0, songLength, context);
                    RelativeLayout layout = (RelativeLayout) findViewById(R.id.rl1);
                    layout.addView(seekBar);

                    updateLoopBounds(0, songLength);

                    seekBar.setOnRangeSeekBarChangeListener(new RangeSeekBar.OnRangeSeekBarChangeListener<Integer>() {
                        @Override
                        public void onRangeSeekBarValuesChanged(RangeSeekBar<?> bar, Integer minValue, Integer maxValue) {
                            updateLoopBounds(minValue, maxValue);
                        }
                    });

                    // Multi-threaded looping operation that tracks the song's current position and
                    // checks to see if song position has exceeded loopStop
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            while (true) {
                                int currentPosition = mediaPlayer.getCurrentPosition();
                                if (currentPosition > loopStop) {
                                    mediaPlayer.seekTo(loopStart);
                                }

                                mHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        int currentPosition = mediaPlayer.getCurrentPosition();
                                        txtCurPos.setText(
                                                TimeUnit.MILLISECONDS.toMinutes(currentPosition) + ":" +
                                                        String.format("%02d", TimeUnit.MILLISECONDS.toSeconds(currentPosition) % 60));

                                    }
                                });

                                try {
                                    Thread.sleep(250);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }).start();

                }
                break;
        }
    }

    // Make relevant buttons and fields appear in Activity
    private void showInterface() {
        btnSetLoopStart.setVisibility(View.VISIBLE);
        btnSetLoopStop.setVisibility(View.VISIBLE);
        txtCurPos.setVisibility(View.VISIBLE);
        txtLoopStart.setVisibility(View.VISIBLE);
        txtLoopStop.setVisibility(View.VISIBLE);
        txtViewCurPos.setVisibility(View.VISIBLE);
        txtViewLoopStart.setVisibility(View.VISIBLE);
        txtViewLoopStop.setVisibility(View.VISIBLE);
    }

    // update global variables for loopStart and loopStop, update the UI, and seek new position
    private void updateLoopBounds(int loopStart, int loopStop) {
        // If the left slider has been moved, re-seek the mediaPlayer
        if (this.loopStart != loopStart) {
            mediaPlayer.seekTo(loopStart);
            txtLoopStart.setText(
                TimeUnit.MILLISECONDS.toMinutes(loopStart) + ":" +
                String.format("%02d", TimeUnit.MILLISECONDS.toSeconds(loopStart) % 60) + "." +
                String.format("%03d", loopStart % 1000));
        } else if (this.loopStop != loopStop) {
            txtLoopStop.setText(
                TimeUnit.MILLISECONDS.toMinutes(loopStop) + ":" +
                String.format("%02d", TimeUnit.MILLISECONDS.toSeconds(loopStop) % 60) + "." +
                String.format("%03d", loopStop % 1000));
        }

        // Finally, update the stored values
        this.loopStart = loopStart;     // again, in milliseconds
        this.loopStop = loopStop;
    }

    // update the text field with the current position
    private void updateCurrentPosition() {

    }

    @Override
    public void onPause() {
        super.onPause();
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
            mediaPlayer.start();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
