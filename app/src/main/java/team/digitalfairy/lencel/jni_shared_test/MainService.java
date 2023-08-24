package team.digitalfairy.lencel.jni_shared_test;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Binder;
import android.os.IBinder;
import android.os.SystemClock;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
//import android.R;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class MainService extends Service {
    private NotificationManager nm;
    private MediaSessionCompat mediaSession;
    private String notificationId = "Notification1";
    private NotificationCompat.Builder bb;

    private PlaybackState.Builder state;
    private final IBinder ib = new LocalBinder();


    public class LocalBinder extends Binder {
        MainService getService() {
            return MainService.this;
        }
    }

    public void updateTitle(String title) {
        // nothing to do
        Log.i("MainService", "Got title "+title);
        bb.setContentTitle(title);
        nm.notify(100,bb.build());
    }

    public void updateTime(int total, int time) {
        //Log.i("MainService", "Update time");


        //bb.setProgress(1000,500,true);
        //nm.notify(100,bb.build());
        
        state.setState(PlaybackState.STATE_PLAYING,0,1.0F);

        mediaSession.setPlaybackState(PlaybackStateCompat.fromPlaybackState(state.build()));

    }

    private void createNotificationChannel() {
        if(nm.getNotificationChannel(notificationId) == null) {
            NotificationChannel ch = new NotificationChannel(notificationId, "LibXMP Playback Service Notif", NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(ch);
        }
    }

    private void configureMediaSession() {

        mediaSession = new MediaSessionCompat(this, "MediaSession");
        mediaSession.setCallback(MainActivity.callback);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("jni_svc","onStartCommand()");
        nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        createNotificationChannel();
        configureMediaSession();
        /*
        Notification nn = new NotificationCompat.Builder(this,notificationId)
                .setContentText("Service is running.")
                .setContentTitle("サービス　実行中！ - リリィ")
                .setSmallIcon(R.drawable.ic_launcher_background)
                .setTicker("This is a ticker.")
                .setContentInfo("This is a content info.")
                .build();
        */

        Notification nn;

        Intent pauseButtonIntent = new Intent();
        pauseButtonIntent.setAction("PAUSE");
        intent.putExtra("ACTION","pause");
        PendingIntent pendingIntent1 = PendingIntent.getBroadcast(this,1,pauseButtonIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);


        bb = new NotificationCompat.Builder(this,notificationId)
                .addAction(android.R.drawable.ic_media_pause,"Pause",pendingIntent1)
                .setContentTitle("サービス実行中！ - リリィ")
                .setContentText("コンテンツ")
                .setContentInfo("AAAAAA")
                .setSmallIcon(R.drawable.ic_launcher_background)
                //.addAction(new NotificationCompat.Action(R))
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle().setMediaSession(mediaSession.getSessionToken()));


        state = new PlaybackState.Builder()
                .setActions(
                        PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PLAY_PAUSE |
                                PlaybackState.ACTION_PAUSE);
                //.setState(PlaybackState.STATE_PLAYING, 30, 1.0F, SystemClock.elapsedRealtime());

        mediaSession.setPlaybackState(PlaybackStateCompat.fromPlaybackState(state.build()));


        nn = bb.build();

        startForeground(100,nn);

        IntentFilter iF = new IntentFilter();
        iF.addAction("PAUSE");

        registerReceiver(MainActivity.broadcastReceiver,iF);


        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return ib;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        stopForeground(true);
        stopSelf();
    }
}
