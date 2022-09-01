package team.digitalfairy.lencel.jni_shared_test;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.session.MediaSession;
import android.os.IBinder;
import android.support.v4.media.session.MediaSessionCompat;
import android.util.Log;
//import android.R;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class MainService extends Service {
    private NotificationManager nm;
    private MediaSessionCompat mediaSession;

    private String notificationId = "Notification1";
    private void createNotificationChannel() {
        if(nm.getNotificationChannel(notificationId) == null) {
            NotificationChannel ch = new NotificationChannel(notificationId, "LibXMP Playback Service Notif", NotificationManager.IMPORTANCE_DEFAULT);
            nm.createNotificationChannel(ch);
        }
    }

    private void configureMediaSession() {
         mediaSession = new MediaSessionCompat(this, "MediaSession");
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

        Notification nn = new NotificationCompat.Builder(this,notificationId)
                .addAction(android.R.drawable.ic_media_pause,"Pause",null)
                .setContentTitle("サービス実行中！ - リリィ")
                .setContentText("コンテンツ")
                .setContentInfo("AAAAAA")
                .setSmallIcon(R.drawable.ic_launcher_background)
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle().setMediaSession(mediaSession.getSessionToken()))
                .build();



        startForeground(100,nn);

        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        stopSelf();
    }
}
