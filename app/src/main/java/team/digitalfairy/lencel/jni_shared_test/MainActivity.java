package team.digitalfairy.lencel.jni_shared_test;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
    Button loadButton;

    static int currentView = 0;
    boolean isScrollEnabled = true;
    boolean isPaused = true;

    private String m_lastPath = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.statusText);
        channelListView = findViewById(R.id.channelListView);
        channelListView_sv = findViewById(R.id.channelListScrollView);
        //debugText = findViewById(R.id.debugTextView);
        loadButton = findViewById(R.id.loadButton);

        channelListView_sv.setOnTouchListener((v, event) -> !isScrollEnabled);
        timebar = findViewById(R.id.runntingTimeBar);
        pauseButton = findViewById(R.id.pauseButton);
        changeViewButton = findViewById(R.id.changeViewButton);

        if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.P)
            m_lastPath = Environment.getExternalStorageDirectory().getPath();
        else
            m_lastPath = "/storage/emulated/0";

        System.loadLibrary("jni_shared_test");
        Log.d(MAINACTIVITY_LOGTAG,LibXMP.getXMPVersion());

        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        Log.i(MAINACTIVITY_LOGTAG, "NSR:" + am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE) + " FPB:" + am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER));

        LibXMP.startOpenSLES(Integer.parseInt(am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)), Integer.parseInt(am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)));

        loadButton.setOnClickListener(this::OnOpenFileClick);

        changeViewButton.setOnClickListener((v) -> {
            if(isPaused) return;
            if(sf != null) sf.cancel(true);
            currentView++;
            switch(currentView % viewLength) {
                case 0:
                    runView();
                    break;

                case 1:
                    runView3(0);
                    //runView2(0);
                    break;
                case 2:
                    runView3(1);

                    //runView2(1);
                    break;
                case 3:
                    runView3(2);

                    //runView2(2);

            }
        });

        pauseButton.setOnClickListener((v) -> {
            isPaused = !isPaused;
            LibXMP.togglePause();
        });



    }

    public void OnOpenFileClick(View view) {
        // Here, thisActivity is the current activity
        if (checkFilePermissions(2))
            return;
        openMusicFileDialog();
    }


    private boolean checkFilePermissions(int requestCode) {
        final int grant = PackageManager.PERMISSION_GRANTED;


        final String exStorage = Manifest.permission.READ_EXTERNAL_STORAGE;
        if (ContextCompat.checkSelfPermission(this, exStorage) == grant) {
            Log.d(MAINACTIVITY_LOGTAG, "File permission is granted");
        } else {
            Log.d(MAINACTIVITY_LOGTAG, "File permission is revoked");
        }


        if ((ContextCompat.checkSelfPermission(this, exStorage) == grant))
            return false;

        // Should we show an explanation?
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, exStorage)) {
            // Show an explanation to the user *asynchronously* -- don't block
            // this thread waiting for the user's response! After the user
            // sees the explanation, try again to request the permission.
            AlertDialog.Builder b = new AlertDialog.Builder(this);
            b.setTitle("Permission denied");
            b.setMessage("Sorry, but permission is denied!\n" +
                    "Please, check the Read Extrnal Storage permission to application!");
            b.setNegativeButton(android.R.string.ok, null);
            b.show();
            return true;
        } else {
            // No explanation needed, we can request the permission.
            ActivityCompat.requestPermissions(this, new String[]{exStorage}, requestCode);
            // MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE
            // MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE is an
            // app-defined int constant. The callback method gets the
            // result of the request.
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (grantResults.length > 0 &&
                permissions[0].equals(Manifest.permission.READ_EXTERNAL_STORAGE) &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            if (requestCode == 2) {
                openMusicFileDialog();
            }
        }
    }

    public void openMusicFileDialog() {
        OpenFileDialog fileDialog = new OpenFileDialog(this)
                .setFilter(".*")
                .setCurrentDirectory(m_lastPath)
                .setOpenDialogListener((fileName, lastPath) -> {
                    m_lastPath = lastPath;
                    if(sf != null) sf.cancel(true);

                    boolean r = LibXMP.loadFile(fileName);
                    isPaused = true;
                    if(r) {
                        LibXMP.togglePause();
                        isPaused = false;
                        switch (currentView % viewLength) {
                            case 0:
                                runView();
                                break;

                            case 1:
                                //runView2(0);
                                runView3(0);
                                break;
                            case 2:
                                //runView2(1);
                                runView3(1);
                                break;
                            case 3:
                                //runView2(2);
                                runView3(2);
                        }
                        persistentStatusView();
                    }
                });
        fileDialog.show();
    }


    void persistentStatusView() {
        ex.scheduleAtFixedRate(() -> {
            if(isPaused) return;
            String frameinfo_string = LibXMP.getFrameInfo();
            runOnUiThread(() -> {
                timebar.setProgress((int) LibXMP.getRunningTime());
                statusText.setText(frameinfo_string);

            });
        },0,32, TimeUnit.MILLISECONDS);
    }

    void runView() {
        //if(sf != null) sf.cancel(true);

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

    void runView3(int size) {
        //

        channelListView.clearAnimation();
        channelListView.removeAllViews();

        AtomicInteger on_curptn = new AtomicInteger(LibXMP.getCurrentPattern());
        AtomicInteger on_row = new AtomicInteger(LibXMP.getCurrentRow());

        final int allocVal = 31;

        TextView[] tvs = new TextView[allocVal];
        for(int i=0; i<allocVal; i++) {
            tvs[i] = new TextView(this);
            tvs[i].setSingleLine(true);
            tvs[i].setTypeface(Typeface.MONOSPACE);
            channelListView.addView(tvs[i]);
        }
        int middle = allocVal / 2;

        tvs[middle].setBackgroundColor(Color.GRAY);

        String[] cur_ptn = LibXMP.getRowString(on_curptn.get(),size);
        // and set current row, and 32 patterns over 32 lines of TextView
        for(int i=0; i<allocVal; i++) {
            int offset = on_row.get() + i - middle;
            if(offset <= 0) {
                tvs[i].setText("");
                continue;
            }
            if(offset >= cur_ptn.length) {
                tvs[i].setText("");
            } else
                tvs[i].setText(cur_ptn[offset]);
        }
        Log.i(MAINACTIVITY_LOGTAG, "Sched");

        AtomicInteger ord = new AtomicInteger(LibXMP.getOrdinal());
        AtomicBoolean gotRendered = new AtomicBoolean(false);
        AtomicBoolean needRender = new AtomicBoolean(false);



        sf = ex.scheduleAtFixedRate(() -> {
            try {
                //if (isPaused) return;
                // TODO: make thing render once; otherwise update?
                int c = LibXMP.getCurrentPattern();
                int cr = LibXMP.getCurrentRow();
                //int ord = LibXMP.getOrdinal();
                int cur_ord = LibXMP.getOrdinal();

                String[] ptn = LibXMP.getRowString(on_curptn.get(), size);
                //String[] ptn = cur_ptn;
                if (ord.get() != cur_ord) {
                    ord.set(cur_ord);
                    needRender.set(true);
                }

                //String[] ptn;
                if (on_curptn.get() != c || (on_curptn.get() == c && cr == 0 && !gotRendered.get())) {
                    //TODO: Optimize this function.
                    //ptn = LibXMP.getRowString(on_curptn.get(),0);
                    Log.i(MAINACTIVITY_LOGTAG, "Pattern Change");
                    on_curptn.set(c);
                    gotRendered.set(true);
                    return;
                }

                if (on_row.get() != cr || needRender.get() || gotRendered.get()) {
                    runOnUiThread(() -> {
                        // and set current row, and 32 patterns over 32 lines of TextView
                        for (int i = 0; i < allocVal; i++) {
                            int offset = cr + i - middle;
                            if (offset < 0) {
                                tvs[i].setText("");
                                continue;
                            }
                            if (offset >= ptn.length) {
                                tvs[i].setText("");
                            } else {
                                tvs[i].setText(ptn[offset]);
                            }
                        }
                        // Redraw
                        on_row.set(cr);
                    });
                    gotRendered.set(false);
                    needRender.set(false);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        },0,32, TimeUnit.MILLISECONDS);

    }

}