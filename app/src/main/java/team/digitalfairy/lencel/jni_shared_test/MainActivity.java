package team.digitalfairy.lencel.jni_shared_test;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends AppCompatActivity {

    private final String MAINACTIVITY_LOGTAG = "MainActivity";
    private final ScheduledExecutorService ex = Executors.newScheduledThreadPool(2);
    private ScheduledFuture<?> sf = null;
    private final int viewLength = 4;

    TextView statusText;
    TextView debugText;
    LinearLayout channelListView;
    ScrollView channelListView_sv;
    ProgressBar timebar;

    Button pauseButton;
    Button changeViewButton;

    static int currentView = 0;
    boolean isScrollEnabled = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.statusText);
        channelListView = findViewById(R.id.channelListView);
        channelListView_sv = findViewById(R.id.channelListScrollView);
        debugText = findViewById(R.id.debugTextView);
        channelListView_sv.setOnTouchListener((v, event) -> !isScrollEnabled);


        timebar = findViewById(R.id.runntingTimeBar);

        pauseButton = findViewById(R.id.pauseButton);
        changeViewButton = findViewById(R.id.changeViewButton);

        System.loadLibrary("jni_shared_test");
        Log.d(MAINACTIVITY_LOGTAG,LibXMP.getXMPVersion());

        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        Log.i(MAINACTIVITY_LOGTAG, "NSR:" + am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE) + " FPB:" + am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER));

        LibXMP.startOpenSLES(Integer.parseInt(am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)), Integer.parseInt(am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)));

        boolean r = LibXMP.loadFile("/sdcard/mod/h0ffman_-_eon.mod");
        if(r)
            LibXMP.togglePause();

        changeViewButton.setOnClickListener((v) -> {
            currentView++;
            switch(currentView % viewLength) {
                case 0:
                    runView();
                    break;

                case 1:
                    runView2(0);
                    break;
                case 2:
                    runView2(1);
                    break;
                case 3:
                    runView2(2);

            }
        });

        pauseButton.setOnClickListener((v) -> LibXMP.togglePause());

        switch(currentView % viewLength) {
            case 0:
                runView();
                break;

            case 1:
                runView2(0);
                break;
            case 2:
                runView2(1);
                break;
            case 3:
                runView2(2);
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

        Log.d(MAINACTIVITY_LOGTAG,"View1: W"+channelListView.getWidth()+" H"+channelListView.getHeight());


        isScrollEnabled = true;
        channelListView.setScrollY(0);
        //channelListView.requestDisallowInterceptTouchEvent(true);


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

    void runView2(int size) {
        // TODO: Figure out how to allow horizontal overflow scroll?
        if(sf != null) sf.cancel(true);

        // Calculate sp -> px
        float f = getResources().getDisplayMetrics().scaledDensity;
        final int txsz = 10;

        Log.d(MAINACTIVITY_LOGTAG,"sp ="+ f +" px="+f*txsz);
        AtomicInteger current_showing_pattern = new AtomicInteger(LibXMP.getCurrentPattern());
        String[] ve = LibXMP.getRowString(current_showing_pattern.get(),size);

        channelListView.clearAnimation();
        channelListView.removeAllViews();

        //channelListView.requestDisallowInterceptTouchEvent(false);

        Log.d(MAINACTIVITY_LOGTAG,"View2: W"+channelListView.getWidth()+" H"+channelListView.getHeight());
        int middle = channelListView.getHeight() / 2;

        //channelListView.setScrollY((-1 * middle) + (int)(txsz*f)*LibXMP.getCurrentRow());
        channelListView.scrollTo(0,(-1 * middle));
        isScrollEnabled = false;

        TextView[] tvs = new TextView[256];
        for(int i=0; i<256; i++) {
            tvs[i] = new TextView(this);
            tvs[i].setSingleLine(true);
            tvs[i].setTypeface(Typeface.MONOSPACE);
            tvs[i].setTextSize(txsz);
        }

        for(int i=0; i<LibXMP.getTotalRows(); i++) {
            // TODO: Temporary
            tvs[i].setText(ve[i]);
            channelListView.addView(tvs[i]);
        }
        // Initally populate
        // TODO: Fix garbage scrolling. Probably need to math per-line scrolling and such... plz help
        AtomicInteger current_row = new AtomicInteger(LibXMP.getCurrentRow());
        sf = ex.scheduleAtFixedRate(() -> {
            int c = LibXMP.getCurrentPattern();
            int cr = LibXMP.getCurrentRow();
            runOnUiThread(() -> {
                if(current_showing_pattern.get() != c) {
                    // remove all views
                    channelListView.removeAllViews();
                    // Query new string
                    String[] cur_ptn = LibXMP.getRowString(c,size);

                    for(int i=0; i<LibXMP.getTotalRows(); i++) {
                        // TODO: Temporary
                        tvs[i].setText(cur_ptn[i]);

                        channelListView.addView(tvs[i]);
                    }
                    current_showing_pattern.set(c);
                    channelListView.scrollTo(0,(-1 * middle));

                }
                if(current_row.get() != cr) {
                    channelListView.scrollBy(0,(int)(txsz*f));
                    debugText.setText("SCRY:"+channelListView.getScrollY());
                    if(cr != 0)
                        tvs[cr - 1].setBackgroundColor(Color.TRANSPARENT);
                    tvs[cr].setBackgroundColor(Color.GRAY);
                    current_row.set(cr);
                }

                //channelListView.setScrollY((-1 * middle) + (int)(txsz*f)*cr);


            });
        },0,32, TimeUnit.MILLISECONDS);
    }
}