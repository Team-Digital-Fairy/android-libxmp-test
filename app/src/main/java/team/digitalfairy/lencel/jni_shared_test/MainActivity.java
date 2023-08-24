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
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.support.v4.media.session.MediaSessionCompat;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends AppCompatActivity {
    private final String MAINACTIVITY_LOGTAG = "MainActivity";
    private final ScheduledExecutorService ex = Executors.newScheduledThreadPool(3);
    private ScheduledFuture<?> sf = null;
    private final int viewLength = 6;

    private IntentFilter intentFilter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);


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
    boolean isPaused = true;
    boolean mBound = false;

    private String m_lastPath = "";
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

    public static MediaSessionCompat.Callback callback = new MediaSessionCompat.Callback() {
        @Override
        public void onPause() {
            super.onPause();
            LibXMP.togglePause();
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
        Log.i(MAINACTIVITY_LOGTAG,"getSystemSvc");
        //nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        //createNotificationChannel();

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

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P)
            m_lastPath = Environment.getExternalStorageDirectory().getPath();
        else
            m_lastPath = "/storage/emulated/0";

        // check for isExternalStorameManager()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if(Environment.isExternalStorageManager()) {
                Log.d(MAINACTIVITY_LOGTAG, ">=R, it is ExternalStorageManager!");
            }
        }

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

        // Check for permission
        int permission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE);
        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    5
            );
        }

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
        // TODO: Implement close SLES
    }

    ActivityResultLauncher<Intent> OpenDialog = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    if(result.getResultCode() == RESULT_OK) {
                        Intent resData = result.getData();
                        if(resData != null) {
                            Uri uri = resData.getData();
                            Log.i("Dialog", "onActivityResult: "+uri.getPath());
                            String[] pt = uri.getPath().split(":");
                            if(pt[0].equals("/tree/primary")) {
                                if(pt.length > 1) {
                                    Log.i("Dialog","PathRes = "+Environment.getExternalStorageDirectory().toString() + "/" + pt[1]);
                                    openMusicFileDialog(Environment.getExternalStorageDirectory().toString() + "/" + pt[1]);
                                }
                            }
                            //openMusicFileDialog(uri);
                        }
                    }
                }
            }
    );

    ActivityResultLauncher<Intent> readFile = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    if(result.getResultCode() == RESULT_OK) {
                        Intent resData = result.getData();
                        if(resData != null) {
                            Uri uri = resData.getData();
                            Log.i("Dialog", "onActivityResult: " + uri.getPath());
                            String[] pt = uri.getPath().split(":");
                            if(pt[0].equals("/document/primary")) {
                                if(pt.length > 1) {
                                    Log.i("Dialog","PathRes = "+Environment.getExternalStorageDirectory().toString() + "/" + pt[1]);
                                    loadFile(Environment.getExternalStorageDirectory().toString() + "/" + pt[1]);
                                }
                            }
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



    private boolean checkFilePermissions(int requestCode) {
        /*
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Check for write External Storage


            if(!Environment.isExternalStorageManager()) {
                // if I don't have the MANAGE_EXTERNAL_STORAGE
                AlertDialog.Builder b = new AlertDialog.Builder(this);
                b.setTitle("Permission denied");
                b.setMessage("Sorry, but permission is denied!\n" +
                        "Please, check the Read Extrnal Storage permission to application!");
                b.setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    Uri ri = Uri.parse("package:"+BuildConfig.APPLICATION_ID);
                    Log.d(MAINACTIVITY_LOGTAG,"ri "+ri);
                    Intent in = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    startActivity(in);
                });
                b.setNegativeButton(android.R.string.cancel, null);
                b.show();
            } else return false;

            return true;
        }*/

        // TODO: properly do this to request MANAGE_EXTERNAL_STORAGE!
        final int grant = PackageManager.PERMISSION_GRANTED;
        String exStorage = Manifest.permission.READ_EXTERNAL_STORAGE;

        // TODO: Seems A13 now requires more "Better" permissions...
        /*
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            exStorage = Manifest.permission.READ_MEDIA_AUDIO;
        }*/

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

                // TODO: do request a Unlimited permission; since it's impossible to access to random data that isn't in the list of allowed file.


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
        if(grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            Log.i("PERM", "Permission Denied");
        } else {
            Log.i("PERM","permission granted");
        }
    }

    public void loadFile(String filename) {
        if (!isServiceRunning)
            startForegroundService(sI);

        isServiceRunning = true;

        if (sf != null) sf.cancel(true);

        boolean r = LibXMP.loadFile(filename);
        Log.i("LoadFile", "LibXMP Load File! ret"+r);
        isPaused = true;
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
            persistentStatusView();

            Log.i(MAINACTIVITY_LOGTAG, String.format("TotalTime %d", LibXMP.getTotalTime()));
            titleText.setText(LibXMP.getLoadedTitleOrFilename());
            mService.updateTitle(LibXMP.getLoadedTitleOrFilename());
        }
    }

    public void openMusicFileDialog(String ss) {
        OpenFileDialog fileDialog = new OpenFileDialog(this)
                .setFilter(".*")
                .setCurrentDirectory(ss==null?m_lastPath:ss)
                .setOpenDialogListener((fileName, lastPath) -> {
                    m_lastPath = lastPath;
                    loadFile(fileName);
                });
        fileDialog.show();
    }


    void persistentStatusView() {
        ex.scheduleAtFixedRate(() -> {
            if(isPaused) return;
            String frameinfo_string = LibXMP.getFrameInfo();
            runOnUiThread(() -> {
                timebar.setProgress((int) LibXMP.getRunningTime());
                mService.updateTime((int)LibXMP.getTotalTime(),(int)LibXMP.getRunningTime());

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

}