package com.github.itsmichaelwang.strumline.app;

import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.w3c.dom.Text;

import java.util.concurrent.TimeUnit;

public class MainActivity extends ActionBarActivity implements View.OnClickListener{

    private static final int SELECT_FILE_REQUEST = 1;
    private static final int UPDATE_PLAYBACK_POSITION = 2;

    // UI ELEMENTS
    private Button btnSongSelect;
    private Button btnSetLoopStart;
    private Button btnSetLoopStop;

    private EditText txtCurPos;         // information fields
    private EditText txtLoopStart;
    private EditText txtLoopStop;

    private TextView txtViewCurPos;     // labels for the information fields
    private TextView txtViewLoopStart;
    private TextView txtViewLoopStop;

    private MediaPlayer mediaPlayer = null;
    private RangeSeekBar<Integer> seekBar = null;

    private Integer songLength; // song length in ms
    private Integer loopStart = -1;
    private Integer loopEnd = -1;

    private Handler mHandler;

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

        mHandler = new Handler() {
            @Override
            public void handleMessage(Message inputMessage) {
                if (inputMessage.what == UPDATE_PLAYBACK_POSITION) {
                    Integer currentPos = (Integer) inputMessage.obj;
                    txtCurPos.setText(
                        TimeUnit.MILLISECONDS.toMinutes(currentPos) + ":" +
                        String.format("%02d", TimeUnit.MILLISECONDS.toSeconds(currentPos) % 60));
                }
            }
        };
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
                int loopStart = mediaPlayer.getCurrentPosition();
                seekBar.setSelectedMinValue(loopStart);
                mediaPlayer.seekTo(loopStart);
                updateLoop(loopStart, this.loopEnd);
                break;
            case R.id.btn_set_loop_stop:
                int loopEnd = mediaPlayer.getCurrentPosition();
                seekBar.setSelectedMaxValue(loopEnd);
                updateLoop(this.loopStart, loopEnd);
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

    // Actual data handling
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case SELECT_FILE_REQUEST:
                if (resultCode == RESULT_OK) {
                    showInterface();

                    Context context = this.getApplicationContext();
                    Uri myUri = data.getData();
                    mediaPlayer = MediaPlayer.create(context, myUri);
                    mediaPlayer.start();

                    songLength = mediaPlayer.getDuration();
                    seekBar = new RangeSeekBar<Integer>(0, songLength, context);

                    RelativeLayout layout = (RelativeLayout) findViewById(R.id.rl1);
                    layout.addView(seekBar);
                    updateLoop(seekBar.getAbsoluteMinValue(), seekBar.getAbsoluteMaxValue());

                    seekBar.setOnRangeSeekBarChangeListener(new RangeSeekBar.OnRangeSeekBarChangeListener<Integer>() {
                        @Override
                        public void onRangeSeekBarValuesChanged(RangeSeekBar<?> bar, Integer minValue, Integer maxValue) {
                            // Update the song if the left slider has been moved
                            if (!loopStart.equals(minValue)) {
                                mediaPlayer.seekTo(minValue);
                            }

                            updateLoop(minValue, maxValue);
                        }
                    });

                    // Start the runnable from the handler
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            while (true) {
                                Integer currentPosition = mediaPlayer.getCurrentPosition();
                                // Check if song has run longer than allowed
                                if (currentPosition > loopEnd) {
                                    mediaPlayer.seekTo(loopStart);
                                }

                                // Send current time to UI
                                Message m = mHandler.obtainMessage(UPDATE_PLAYBACK_POSITION, currentPosition);
                                m.sendToTarget();

                                try {
                                    Thread.sleep(500);
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

    // update class variables and fill in text
    public void updateLoop(Integer minValue, Integer maxValue) {
        loopStart = minValue;   // in milliseconds
        loopEnd = maxValue;
        txtLoopStart.setText(
                TimeUnit.MILLISECONDS.toMinutes(minValue) + ":" +
                String.format("%02d", TimeUnit.MILLISECONDS.toSeconds(minValue) % 60) + "." +
                String.format("%03d", minValue % 1000));
        txtLoopStop.setText(
                TimeUnit.MILLISECONDS.toMinutes(maxValue) + ":" +
                String.format("%02d", TimeUnit.MILLISECONDS.toSeconds(maxValue) % 60) + "." +
                String.format("%03d", maxValue % 1000));
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
}
