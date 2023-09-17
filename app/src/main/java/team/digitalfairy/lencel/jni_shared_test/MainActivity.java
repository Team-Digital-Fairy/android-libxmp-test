package team.digitalfairy.lencel.jni_shared_test;

import static android.content.Intent.ACTION_OPEN_DOCUMENT_TREE;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.media.session.MediaSession;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.view.Choreographer;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends AppCompatActivity implements Choreographer.FrameCallback {
    private final String MAINACTIVITY_LOGTAG = "MainActivity";
    private final ScheduledExecutorService ex = Executors.newScheduledThreadPool(3);
    private ScheduledFuture<?> sf = null;
    private final int viewLength = 6;

    private Choreographer choreographer;
    private IntentFilter intentFilter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);

    private ParcelFileDescriptor pfd;

    TextView titleText;
    TextView statusText;
    TextView debugText;
    LinearLayout channelListView;
    ScrollView channelListView_sv;
    ProgressBar timebar;

    Button pauseButton;
    Button changeViewButton;
    Button loadButton;
    Button stopButton;

    static int currentView = -1;
    boolean isScrollEnabled = true;
    static boolean isPaused = true;
    boolean mBound = false;

    private NotificationManager nm;
    private final String notificationId = "Notification1";

    private boolean isServiceRunning = false;
    private Intent sI;
    private MainService mService;

    public static BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String state = intent.getAction();
            Log.i("broadcast","Got state "+state);
        }
    };



    private void createNotificationChannel() {
        if(nm.getNotificationChannel(notificationId) == null) {
            NotificationChannel ch = new NotificationChannel(notificationId, "LibXMP Playback Service Notification", NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(ch);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // acquire media session
        //Log.i(MAINACTIVITY_LOGTAG,"getSystemSvc");
        //nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        //createNotificationChannel();
        choreographer = Choreographer.getInstance();

        statusText = findViewById(R.id.statusText);
        titleText = findViewById(R.id.titleText);
        channelListView = findViewById(R.id.channelListView);
        channelListView_sv = findViewById(R.id.channelListScrollView);
        //debugText = findViewById(R.id.debugTextView);
        loadButton = findViewById(R.id.loadButton);
        stopButton = findViewById(R.id.stopButton);

        channelListView_sv.setOnTouchListener((v, event) -> !isScrollEnabled);
        timebar = findViewById(R.id.runntingTimeBar);
        pauseButton = findViewById(R.id.pauseButton);
        changeViewButton = findViewById(R.id.changeViewButton);

        System.loadLibrary("jni_shared_test");
        Log.d(MAINACTIVITY_LOGTAG,LibXMP.getXMPVersion());

        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        Log.i(MAINACTIVITY_LOGTAG, "NSR:" + am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE) + " FPB:" + am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER));

        LibXMP.startOpenSLES(Integer.parseInt(am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)), Integer.parseInt(am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)));

        statusText.setText(getString(R.string.xmp_version,LibXMP.getXMPVersion()));

        loadButton.setOnClickListener(this::OnOpenFileClick);

        changeViewButton.setOnClickListener((v) -> {
            if(!isServiceRunning) return;
            if(isPaused) return;
            if(sf != null) sf.cancel(true);


            Toast t = Toast.makeText(this,String.format("View changed to %d",(currentView % viewLength)),Toast.LENGTH_SHORT);

            currentView++;
            switch(currentView % viewLength) {
                case 0:
                    runView();
                    break;
                case 1:
                    runView3(0);
                    break;
                case 2:
                    //runView2(0);
                    runView3(1);
                    break;
                case 3:
                    //runView2(1);
                    runView3(2);
                    break;
                case 4:
                    //runView2(2);
                    runView3(3);
                    break;
                case 5:
                    runCommentView();
                    break;
            }
            t.show();
        });

        pauseButton.setOnClickListener((v) -> {
            // make it so it doesn't call togglePause when it's not loaded
            isPaused = !isPaused;
            LibXMP.togglePause();
            if(isPaused) {
                MainService.mediaSession.setPlaybackState(
                        new PlaybackStateCompat.Builder()
                                .setState(PlaybackStateCompat.STATE_PAUSED, 100, 1.0f)
                                .setActions(PlaybackStateCompat.ACTION_PLAY |
                                        PlaybackStateCompat.ACTION_PAUSE |
                                        PlaybackStateCompat.ACTION_PLAY_PAUSE |
                                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)

                                .build()
                );
                MainService.mediaSession.setActive(false);

            } else {
                MainService.mediaSession.setPlaybackState(
                        new PlaybackStateCompat.Builder()
                                .setState(PlaybackStateCompat.STATE_PLAYING, 100, 1.0f)
                                .setActions(PlaybackStateCompat.ACTION_PLAY |
                                        PlaybackStateCompat.ACTION_PAUSE |
                                        PlaybackStateCompat.ACTION_PLAY_PAUSE |
                                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)

                                .build()
                );
                MainService.mediaSession.setActive(true);

            }

            choreographer.postFrameCallback(this);

        });

        stopButton.setOnClickListener((v) -> {
            if(!isServiceRunning) return;

            if(sf != null) sf.cancel(true);
            sf = null;

            stopService(sI);
            // and Clean Up
            LibXMP.togglePause();
            isPaused = true;
            LibXMP.unloadFile();
            // FIXME: PATCH->pfdclose
            try {
                pfd.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            isServiceRunning = false;
        });



    }

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MainService.LocalBinder b = (MainService.LocalBinder) service;
            mService = b.getService();
            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBound = false;
        }
    };

    @Override
    protected void onStart() {
        super.onStart();
        Log.d("MainActivity","onStart()");

        if(!isServiceRunning) {
            sI = new Intent(this, MainService.class);
            startForegroundService(sI);
            bindService(sI, connection, Context.BIND_AUTO_CREATE);
            isServiceRunning = true;
        }

        if(!isPaused) mService.updateTitle(LibXMP.getLoadedTitleOrFilename());

        if(sf == null && currentView != -1) {
            // if thread is running on Stop condition
            switch(currentView % viewLength) {
                case 0:
                    runView();
                    break;
                case 1:
                    runView3(0);
                    break;
                case 2:
                    //runView2(0);
                    runView3(1);
                    break;
                case 3:
                    //runView2(1);
                    runView3(2);
                    break;
                case 4:
                    //runView2(2);
                    runView3(3);
                    break;
                case 5:
                    runCommentView();
                    break;
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d("MainActivity","onStop()");
        //unbindService(connection);

        // Gotta end all bg threads; recover on onStart
        if(currentView != -1) {
            if(sf != null) sf.cancel(true);
            sf = null;

            // But remember last run
        }

        //isServiceRunning = false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(connection);
        // Stop all LibXMP
        LibXMP.unloadFile();
        try {
            pfd.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        // TODO: Implement close SLES
    }


    ActivityResultLauncher<Intent> readFile = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    if(result.getResultCode() == RESULT_OK) {
                        if(result.getData() != null) {
                            Intent resData = result.getData();
                            Log.i("Result", "OK " + resData.getData());
                            Uri uri = resData.getData();
                            Log.i("Result", "uri " + uri);

                            try {
                                pfd = getContentResolver().openFileDescriptor(uri,"r");
                            } catch (FileNotFoundException e) {
                                throw new RuntimeException(e);
                            }

                            int fd = pfd.getFd();
                            loadFile(fd);

                            MainService.mediaSession.setPlaybackState(
                                    new PlaybackStateCompat.Builder()
                                            .setState(PlaybackStateCompat.STATE_PLAYING, 100, 1.0f)
                                            .setActions(PlaybackStateCompat.ACTION_PLAY |
                                                    PlaybackStateCompat.ACTION_PAUSE |
                                                    PlaybackStateCompat.ACTION_PLAY_PAUSE |
                                                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                                                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)

                                            .build()
                            );

                        }
                    }
                }
            }
    );


    public void OnOpenFileClick(View view) {
        // Here, thisActivity is the current activity


        // Request ACTION_OPEN_DOCUMENT_TREE
        /*
        Intent dt = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        dt.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        OpenDialog.launch(dt);
        */

        Intent dt = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        dt.setType("*/*");

        readFile.launch(dt);
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            Log.i("PERM", "Permission Denied");
        } else {
            Log.i("PERM","permission granted");
        }
    }

    public void loadFile(int fd) {
        if (!isServiceRunning)
            startForegroundService(sI);

        isServiceRunning = true;

        if (sf != null) sf.cancel(true);

        boolean r = LibXMP.loadFile(fd);
        Log.i("LoadFile", "LibXMP Load File! ret "+r);
        isPaused = true;
        MainService.mediaSession.setActive(true);

        if (r) {
            if (currentView == -1) currentView = 0;
            LibXMP.togglePause();
            isPaused = false;

            switch (currentView % viewLength) {
                case 0:
                    runView();
                    break;

                case 1:
                    runView3(0);
                    break;

                case 2:
                    //runView2(0);
                    runView3(1);
                    break;
                case 3:
                    //runView2(1);
                    runView3(2);
                    break;
                case 4:
                    //runView2(2);
                    runView3(3);
                    break;
                case 5:
                    runCommentView();
                    break;
            }
            timebar.setMax((int) LibXMP.getTotalTime());
            //persistentStatusView();
            choreographer.postFrameCallback(this);

            Log.i(MAINACTIVITY_LOGTAG, String.format("TotalTime %d", LibXMP.getTotalTime()));
            titleText.setText(LibXMP.getLoadedTitleOrFilename());
            mService.updateTitle(LibXMP.getLoadedTitleOrFilename());
        }
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
            tvs[i].setTextSize(12.0F);
            tvs_ch[i].setTextSize(12.0F);
            channelListView.addView(tvs[i]);
            channelListView.addView(tvs_ch[i]);
        }


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
        },0,30, TimeUnit.MILLISECONDS);
    }

    void runView3(int size) {
        //
        isScrollEnabled = false;
        channelListView.setScrollY(0);

        channelListView.clearAnimation();
        channelListView.removeAllViews();

        int on_curptn =  LibXMP.getCurrentPattern();
        AtomicInteger on_row = new AtomicInteger(LibXMP.getCurrentRow());

        final int allocVal = 47;

        TextView[] tvs = new TextView[allocVal];
        for(int i=0; i<allocVal; i++) {
            tvs[i] = new TextView(this);
            tvs[i].setSingleLine(true);
            tvs[i].setTypeface(Typeface.MONOSPACE);
            tvs[i].setTextSize(10.0F);
            channelListView.addView(tvs[i]);
        }
        int middle = allocVal / 2;

        tvs[middle].setBackgroundColor(Color.GRAY);

        String[] cur_ptn = LibXMP.getRowString(on_curptn,size);
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

        sf = ex.scheduleAtFixedRate(() -> {
            try {
                // TODO: Write code that takes pattern once, then change what's rendered to 2nd time? querying every time this function run seems excessive?
                if (isPaused) return;
                int c = LibXMP.getCurrentPattern();
                int cr = LibXMP.getCurrentRow();
                //int ord = LibXMP.getOrdinal();
                int cur_ord = LibXMP.getOrdinal();
                // Reentry?
                if(on_row.get() == cr) {
                    //Log.d(MAINACTIVITY_LOGTAG, "Same Pattern");
                    return;
                }

                String[] ptn = LibXMP.getRowString(c, size);
                /*
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
                */

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
                    //on_row.set(cr);
                });

                    //gotRendered.set(false);
                    //needRender.set(false);
                on_row.set(cr);
            } catch (Exception e) {
                e.printStackTrace();
            }
        },0,30, TimeUnit.MILLISECONDS);

    }

    void runCommentView() {
        isScrollEnabled = false;
        channelListView.setScrollY(0);

        channelListView.clearAnimation();
        channelListView.removeAllViews();

        sf = null;
        String s = LibXMP.getComments();

        if(s != null) {
            if(s.length() != 0) {
                TextView tv = new TextView(this);
                tv.setTypeface(Typeface.MONOSPACE);
                channelListView.addView(tv);

                tv.setText(s);
                return;
            }
        }

        // Accumulate instruments
        int c = 0;
        TextView[] tvs = new TextView[LibXMP.getInstrumentCount()];
        for(TextView tv : tvs) {
            tv = new TextView(this);
            tv.setText(LibXMP.getInstrumentName(c));
            tv.setSingleLine(true);
            tv.setTypeface(Typeface.MONOSPACE);
            tv.setTextSize(10.0F);

            channelListView.addView(tv);
            c++;
        }
    }



    @Override
    public void doFrame(long l) {
        if(isPaused) return;
        String frameinfo_string = LibXMP.getFrameInfo();
        runOnUiThread(() -> {
            timebar.setProgress((int) LibXMP.getRunningTime());
            mService.updateTime((int)LibXMP.getTotalTime(),(int)LibXMP.getRunningTime());

            statusText.setText(frameinfo_string);
        });


        choreographer.postFrameCallback(this);

    }
}