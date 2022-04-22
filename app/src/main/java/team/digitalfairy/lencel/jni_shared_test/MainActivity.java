package team.digitalfairy.lencel.jni_shared_test;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private final String MAINACTIVITY_LOGTAG = "MainActivity";
    private final ScheduledExecutorService ex = Executors.newScheduledThreadPool(2);
    private ScheduledFuture<?> sf = null;

    TextView statusText;
    LinearLayout channelListView;
    ProgressBar timebar;

    Button pauseButton;
    Button changeViewButton;

    static int currentView = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.statusText);
        channelListView = findViewById(R.id.channelListView);
        timebar = findViewById(R.id.runntingTimeBar);

        pauseButton = findViewById(R.id.pauseButton);
        changeViewButton = findViewById(R.id.changeViewButton);

        System.loadLibrary("jni_shared_test");
        Log.d(MAINACTIVITY_LOGTAG,LibXMP.getXMPVersion());

        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        Log.i(MAINACTIVITY_LOGTAG, "NSR:" + am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE) + " FPB:" + am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER));

        LibXMP.startOpenSLES(Integer.parseInt(am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)), Integer.parseInt(am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)));

        boolean r = LibXMP.loadFile("/sdcard/mod/AFFLICT.S3M");
        if(r)
            LibXMP.togglePause();

        changeViewButton.setOnClickListener((v) -> {
            currentView++;
            switch(currentView % 2) {
                case 0:
                    runView();
                    break;

                case 1:
                    runView2();
                    break;
            }
        });

        pauseButton.setOnClickListener((v) -> LibXMP.togglePause());

        switch(currentView % 2) {
            case 0:
                runView();
                break;

            case 1:
                runView2();
                break;
        }
        persistentStatusView();

    }

    void persistentStatusView() {
        ex.scheduleAtFixedRate(() -> {
            String frameinfo_string = LibXMP.getFrameInfo();
            runOnUiThread(() -> {
                timebar.setProgress((int) LibXMP.getRunningTime());
                statusText.setText(frameinfo_string);

            });
        },0,32, TimeUnit.MILLISECONDS);
    }

    void runView() {
        if(sf != null) sf.cancel(true);

        channelListView.clearAnimation();
        channelListView.removeAllViews();

        int channels = LibXMP.getChannels();
        TextView[] tvs = new TextView[channels];
        TextView[] tvs_ch = new TextView[channels];
        String[] channelString = new String[channels];
        /*
        for(TextView tv: tvs) {
            tv = new TextView(this);
            channelListView.addView(tv);
        }
         */
        for(int i=0; i<channels; i++) {
            tvs[i] = new TextView(this);
            tvs_ch[i] = new TextView(this);
            tvs[i].setTypeface(Typeface.MONOSPACE);
            tvs_ch[i].setTypeface(Typeface.MONOSPACE);
            channelListView.addView(tvs[i]);
            channelListView.addView(tvs_ch[i]);
        }
        timebar.setMax((int) LibXMP.getTotalTime());

        sf = ex.scheduleAtFixedRate(() -> {
            //String frameinfo_string = LibXMP.getFrameInfo();
            for(int i=0; i<channels; i++) {
                channelString[i] = LibXMP.getChannelInfo(i);
            }

            runOnUiThread(() -> {
                //timebar.setProgress((int) LibXMP.getRunningTime());

                //statusText.setText(frameinfo_string);
                for(int i=0; i<channels; i++) {
                    tvs[i].setText(channelString[i]);
                    tvs_ch[i].setText(LibXMP.getRowEvt(LibXMP.getCurrentRow(),i));
                }
            });
        },0,32, TimeUnit.MILLISECONDS);
    }

    void runView2() {
        if(sf != null) sf.cancel(true);

        channelListView.clearAnimation();
        channelListView.removeAllViews();

        sf = ex.scheduleAtFixedRate(() -> {

            runOnUiThread(() -> {

            });
        },0,32, TimeUnit.MILLISECONDS);
    }
}