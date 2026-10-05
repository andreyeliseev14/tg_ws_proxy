package ru.tgws.proxy;

import android.content.Context;
import android.content.SharedPreferences;

import java.security.SecureRandom;

final class Prefs {
    static final int PORT = 1443;
    static final String AUTOSTART = "autostart";
    static final String DC4_ONLY = "dc4_only";
    private static final String SECRET = "secret";

    private Prefs() {}

    static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("tgws", Context.MODE_PRIVATE);
    }

    static synchronized String secret(Context c) {
        String s = sp(c).getString(SECRET, null);
        if (s == null || s.length() != 32) {
            byte[] b = new byte[16];
            new SecureRandom().nextBytes(b);
            StringBuilder sb = new StringBuilder();
            for (byte x : b) sb.append(String.format("%02x", x));
            s = sb.toString();
            sp(c).edit().putString(SECRET, s).commit();
        }
        return s;
    }

    static boolean autostart(Context c) {
        return sp(c).getBoolean(AUTOSTART, true);
    }

    static boolean dc4Only(Context c) {
        return sp(c).getBoolean(DC4_ONLY, false);
    }

    static String dcIps(Context c) {
        return dc4Only(c)
                ? "4:149.154.167.220"
                : "2:149.154.167.220,4:149.154.167.220";
    }

    static String link(Context c) {
        return "tg://proxy?server=127.0.0.1&port=" + PORT + "&secret=dd" + secret(c);
    }
}
