package ru.tgws.proxy;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;

import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

import java.io.File;
import java.io.FileWriter;

/** Runs the Flowseal TG WS Proxy core (Python) in its own process. */
public class ProxyService extends Service {
    static final String ACTION_STOP = "ru.tgws.proxy.STOP";
    private static final String CHANNEL = "proxy";
    private static final int NOTIFICATION_ID = 1;

    private volatile boolean started;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        goForeground();
        if (!started) {
            started = true;
            Thread t = new Thread(this::runProxy, "tgws-python");
            t.setDaemon(true);
            t.start();
        }
        return START_STICKY;
    }

    private void goForeground() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL, "Прокси", NotificationManager.IMPORTANCE_LOW));

        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, ProxyService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE);

        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("TG WS Proxy работает")
                .setContentText("Telegram подключается через 127.0.0.1:" + Prefs.PORT)
                .setContentIntent(open)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(this, R.drawable.ic_stat), "Выключить", stop).build())
                .build();

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, n);
        }
    }

    private void runProxy() {
        File log = new File(getFilesDir(), "proxy.log");
        try {
            if (!Python.isStarted()) Python.start(new AndroidPlatform(this));
            Python.getInstance().getModule("runner").callAttr("run",
                    Prefs.PORT,
                    Prefs.secret(this),
                    Prefs.dcIps(this),
                    log.getAbsolutePath(),
                    getApplicationInfo().nativeLibraryDir);
        } catch (Throwable t) {
            try (FileWriter w = new FileWriter(log, true)) {
                w.write("\nFATAL: " + t + "\n");
            } catch (Exception ignored) { }
        }
        // The proxy loop only returns on failure.
        new Handler(Looper.getMainLooper()).post(this::stopSelf);
    }

    @Override
    public void onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
        // The asyncio loop can't be stopped cleanly from Java; this process only hosts the proxy.
        Process.killProcess(Process.myPid());
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
