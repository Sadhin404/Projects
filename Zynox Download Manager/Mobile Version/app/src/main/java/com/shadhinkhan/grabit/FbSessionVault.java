package com.shadhinkhan.grabit;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Optional Facebook session storage (user-authorized, off by default) -
 * the Facebook twin of SessionVault, added 2026-11-09 on the user's
 * request so videos from private Facebook groups/pages can be fetched
 * with his own logged-in session. Same guarantees: Netscape cookies
 * encrypted AES-256/GCM with an Android Keystore key, plaintext only
 * materialized into the app-private cache for an engine run, values
 * never logged.
 */
public class FbSessionVault {
    private static final String KEY_ALIAS = "zynox_fb_session";
    private static final String ENC_NAME = "fb_session.enc";
    private static final String RUNTIME_NAME = "fb_cookies.txt";
    private static final String PREFS = "grabit_prefs";

    private static File encFile(Context ctx) { return new File(ctx.getFilesDir(), ENC_NAME); }
    private static File runtimeFile(Context ctx) { return new File(ctx.getCacheDir(), RUNTIME_NAME); }
    private static SharedPreferences prefs(Context ctx) { return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    private static SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(KEY_ALIAS)) {
            return ((KeyStore.SecretKeyEntry) ks.getEntry(KEY_ALIAS, null)).getSecretKey();
        }
        KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        kg.init(new KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return kg.generateKey();
    }

    public static synchronized boolean save(Context ctx, String netscapeText) {
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key());
            byte[] iv = c.getIV();
            byte[] ct = c.doFinal(netscapeText.getBytes("UTF-8"));
            FileOutputStream out = new FileOutputStream(encFile(ctx));
            out.write(iv);
            out.write(ct);
            out.close();
            long expiry = parseExpiry(netscapeText);
            prefs(ctx).edit()
                    .putLong("fb_saved_at", System.currentTimeMillis())
                    .putLong("fb_expiry", expiry)
                    .putBoolean("fb_invalid", false)
                    .apply();
            return true;
        } catch (Throwable e) { return false; }
    }

    private static long parseExpiry(String netscapeText) {
        try {
            for (String line : netscapeText.split("\n")) {
                String[] p = line.split("\t");
                if (p.length >= 7 && p[5].equals("xs")) {
                    long exp = Long.parseLong(p[4].trim());
                    if (exp > 0) return exp * 1000L;
                }
            }
        } catch (Throwable ignored) {}
        return System.currentTimeMillis() + 30L * 24 * 3600 * 1000;
    }

    public static synchronized boolean hasSession(Context ctx) {
        return encFile(ctx).exists();
    }

    /** Decrypt the session into the app-private cache dir so the engine
     *  can read it as a --cookies file. Null when no session exists. */
    public static synchronized File materialize(Context ctx) {
        File enc = encFile(ctx);
        if (!enc.exists()) return null;
        try {
            FileInputStream in = new FileInputStream(enc);
            byte[] all = new byte[(int) enc.length()];
            int off = 0, n;
            while ((n = in.read(all, off, all.length - off)) > 0) off += n;
            in.close();
            byte[] iv = new byte[12];
            System.arraycopy(all, 0, iv, 0, 12);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            byte[] pt = c.doFinal(all, 12, all.length - 12);
            File rt = runtimeFile(ctx);
            FileOutputStream out = new FileOutputStream(rt);
            out.write(pt);
            out.close();
            return rt;
        } catch (Throwable e) { return null; }
    }

    public static synchronized void clear(Context ctx) {
        try { encFile(ctx).delete(); } catch (Throwable ignored) {}
        try { runtimeFile(ctx).delete(); } catch (Throwable ignored) {}
        prefs(ctx).edit().remove("fb_saved_at").remove("fb_expiry").remove("fb_invalid").apply();
    }

    public static synchronized void markInvalid(Context ctx) {
        prefs(ctx).edit().putBoolean("fb_invalid", true).apply();
    }

    /** Human-readable status for the Connection screen. Never includes
     *  cookie values - only presence, save date and estimated expiry. */
    public static synchronized String statusText(Context ctx) {
        if (!hasSession(ctx)) return "Facebook: not connected";
        SharedPreferences p = prefs(ctx);
        if (p.getBoolean("fb_invalid", false)) return "Facebook: session EXPIRED or rejected - reconnect";
        long exp = p.getLong("fb_expiry", 0);
        if (exp > 0 && System.currentTimeMillis() > exp) return "Facebook: session EXPIRED - reconnect";
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.US);
        String saved = p.getLong("fb_saved_at", 0) > 0 ? f.format(new java.util.Date(p.getLong("fb_saved_at", 0))) : "?";
        String expS = exp > 0 ? f.format(new java.util.Date(exp)) : "?";
        return "Facebook: connected (saved " + saved + ", valid until ~" + expS + ")";
    }
}
