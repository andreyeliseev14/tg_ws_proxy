package ru.tgws.proxy;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private TextView power, status, battery;
    private boolean running;

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            render(isServiceRunning());
            ui.postDelayed(this, 1500);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);

        Prefs.secret(this); // generate once, so the Telegram link never changes

        power = findViewById(R.id.power);
        status = findViewById(R.id.status);
        battery = findViewById(R.id.battery);
        Switch autostart = findViewById(R.id.autostart);
        Switch dc4 = findViewById(R.id.dc4);

        autostart.setChecked(Prefs.autostart(this));
        autostart.setOnCheckedChangeListener((v, on) ->
                Prefs.sp(this).edit().putBoolean(Prefs.AUTOSTART, on).apply());

        dc4.setChecked(Prefs.dc4Only(this));
        dc4.setOnCheckedChangeListener((v, on) -> {
            Prefs.sp(this).edit().putBoolean(Prefs.DC4_ONLY, on).commit();
            if (running) restartProxy();
        });

        power.setOnClickListener(v -> {
            if (running) stopProxy(); else startProxy();
        });
        findViewById(R.id.connect).setOnClickListener(v -> openTelegram());
        battery.setOnClickListener(v -> requestBattery());
        findViewById(R.id.log).setOnClickListener(v -> showLog());

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                   != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        ui.post(poll);
        updateBattery();
    }

    @Override
    protected void onPause() {
        super.onPause();
        ui.removeCallbacks(poll);
    }

    private void startProxy() {
        startForegroundService(new Intent(this, ProxyService.class));
        status.setText("Запуск…");
    }

    private void stopProxy() {
        stopService(new Intent(this, ProxyService.class));
        status.setText("Остановка…");
    }

    private void restartProxy() {
        stopProxy();
        ui.postDelayed(this::startProxy, 1200);
    }

    @SuppressWarnings("deprecation")
    private boolean isServiceRunning() {
        ActivityManager am = getSystemService(ActivityManager.class);
        for (ActivityManager.RunningServiceInfo s : am.getRunningServices(Integer.MAX_VALUE)) {
            if (ProxyService.class.getName().equals(s.service.getClassName())) return true;
        }
        return false;
    }

    private void render(boolean on) {
        running = on;
        power.setText(on ? "ВКЛ" : "ВЫКЛ");
        power.setBackgroundResource(on ? R.drawable.circle_on : R.drawable.circle_off);
        status.setText(on ? "Работает · 127.0.0.1:" + Prefs.PORT : "Выключен");
    }

    private void openTelegram() {
        if (!running) startProxy();
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Prefs.link(this))));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Telegram не найден", Toast.LENGTH_LONG).show();
        }
    }

    private boolean ignoringBattery() {
        PowerManager pm = getSystemService(PowerManager.class);
        return pm.isIgnoringBatteryOptimizations(getPackageName());
    }

    private void updateBattery() {
        battery.setVisibility(ignoringBattery() ? View.GONE : View.VISIBLE);
    }

    private void requestBattery() {
        try {
            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        }
    }

    private String readLogTail() {
        File f = new File(getFilesDir(), "proxy.log");
        if (!f.exists()) return "Лог пуст. Включите прокси.";
        try (RandomAccessFile r = new RandomAccessFile(f, "r")) {
            long len = r.length();
            long from = Math.max(0, len - 8000);
            byte[] b = new byte[(int) (len - from)];
            r.seek(from);
            r.readFully(b);
            return new String(b, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "Не удалось прочитать лог: " + e;
        }
    }

    private void showLog() {
        String text = readLogTail();
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextIsSelectable(true);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setTextSize(11);
        int p = (int) (16 * getResources().getDisplayMetrics().density);
        tv.setPadding(p, p, p, p);
        ScrollView sv = new ScrollView(this);
        sv.addView(tv);
        sv.post(() -> sv.fullScroll(View.FOCUS_DOWN));

        new AlertDialog.Builder(this)
                .setTitle("Лог прокси")
                .setView(sv)
                .setPositiveButton("Закрыть", null)
                .setNeutralButton("Копировать", (d, w) -> {
                    ClipboardManager cm = getSystemService(ClipboardManager.class);
                    cm.setPrimaryClip(ClipData.newPlainText("tgws log", text));
                    Toast.makeText(this, "Скопировано", Toast.LENGTH_SHORT).show();
                })
                .show();
    }
}
