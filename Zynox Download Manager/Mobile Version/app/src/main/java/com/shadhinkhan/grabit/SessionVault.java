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
 * Optional Instagram session storage (user-authorized, off by default).
 *
 * The Netscape cookies text is encrypted with AES-256/GCM using a key that
 * lives in the Android Keystore and never leaves it. Plaintext cookies are
 * only materialized into the app-private cache dir for the duration of an
 * engine run. Cookie values are never written to logs or diagnostics.
 */
public class SessionVault {
    private static final String KEY_ALIAS = "dhorefel_ig_session";
    private static final String ENC_NAME = "ig_session.enc";
    private static final String RUNTIME_NAME = "ig_cookies.txt";
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

    /** Save a Netscape-format cookie file's text, encrypted. Also records when
     *  it was saved and the sessionid expiry when the file carries one. */
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
            long expiry = parseSessionExpiry(netscapeText);
            prefs(ctx).edit()
                    .putLong("ig_saved_at", System.currentTimeMillis())
                    .putLong("ig_expiry", expiry)
                    .putBoolean("ig_invalid", false)
                    .apply();
            return true;
        } catch (Throwable e) { return false; }
    }

    private static long parseSessionExpiry(String netscapeText) {
        try {
            for (String line : netscapeText.split("\n")) {
                String[] p = line.split("\t");
                if (p.length >= 7 && p[5].equals("sessionid")) {
                    long exp = Long.parseLong(p[4].trim());
                    if (exp > 0) return exp * 1000L;
                }
            }
        } catch (Throwable ignored) {}
        return System.currentTimeMillis() + 30L * 24 * 3600 * 1000;
    }

    public static synchronized boolean hasSession(Context ctx) {
        migrateLegacy(ctx);
        return encFile(ctx).exists();
    }

    /** Decrypt the session into the app-private cache dir so the engine can
     *  read it as a --cookies file. Returns null when no session exists.
     *  Also migrates a legacy plaintext cookies.txt (pre-vault builds). */
    public static synchronized File materialize(Context ctx) {
        migrateLegacy(ctx);
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

    private static boolean migrating = false;
    private static synchronized void migrateLegacy(Context ctx) {
        if (migrating) return;
        migrating = true;
        try {
            File legacy = new File(ctx.getFilesDir(), "cookies.txt");
            if (legacy.exists() && !encFile(ctx).exists()) {
                java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(legacy));
                StringBuilder sb = new StringBuilder();
                String ln; while ((ln = br.readLine()) != null) sb.append(ln).append("\n");
                br.close();
                if (sb.toString().contains("instagram.com")) {
                    save(ctx, sb.toString());
                }
            }
            if (legacy.exists() && encFile(ctx).exists()) legacy.delete();
        } catch (Throwable ignored) {}
    }

    public static synchronized void clear(Context ctx) {
        try { encFile(ctx).delete(); } catch (Throwable ignored) {}
        try { runtimeFile(ctx).delete(); } catch (Throwable ignored) {}
        try { new File(ctx.getFilesDir(), "cookies.txt").delete(); } catch (Throwable ignored) {}
        prefs(ctx).edit().remove("ig_saved_at").remove("ig_expiry").remove("ig_invalid").apply();
    }

    public static synchronized void markInvalid(Context ctx) {
        prefs(ctx).edit().putBoolean("ig_invalid", true).apply();
    }

    /** Human-readable status for the diagnostics screen. Never includes
     *  cookie values - only presence, save date and estimated expiry. */
    public static synchronized String statusText(Context ctx) {
        if (!hasSession(ctx)) return "Instagram session: not connected (optional)";
        SharedPreferences p = prefs(ctx);
        if (p.getBoolean("ig_invalid", false)) return "Instagram session: EXPIRED or rejected - reconnect";
        long exp = p.getLong("ig_expiry", 0);
        if (exp > 0 && System.currentTimeMillis() > exp) return "Instagram session: EXPIRED - reconnect";
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.US);
        String saved = p.getLong("ig_saved_at", 0) > 0 ? f.format(new java.util.Date(p.getLong("ig_saved_at", 0))) : "?";
        String expS = exp > 0 ? f.format(new java.util.Date(exp)) : "?";
        return "Instagram session: connected (saved " + saved + ", valid until ~" + expS + ")";
    }
}
