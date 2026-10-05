package ru.tgws.proxy;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Prefs.autostart(context)) return;
        try {
            context.startForegroundService(new Intent(context, ProxyService.class));
        } catch (Exception ignored) {
            // Some firmwares block background starts; the user can start it manually.
        }
    }
}
