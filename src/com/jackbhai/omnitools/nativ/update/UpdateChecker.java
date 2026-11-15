package com.jackbhai.omnitools.nativ.update;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.widget.Toast;

import com.jackbhai.omnitools.nativ.Net;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * In-app update check against this app's own GitHub Releases
 * (Pranshu198/omnitools-native). Mirrors the old WebView app's UpdateCheck:
 * a silent once-a-day automatic check plus a manual entry point.
 *
 * <p>Best-effort by design: offline, a GitHub hiccup, or any parse failure
 * is swallowed silently on the automatic path. The manual path
 * ({@link #checkNow}) additionally tells the user the outcome by Toast.
 * Nothing is ever downloaded automatically — the dialog's Download button
 * opens the APK asset in the browser, exactly like the old app.
 */
public final class UpdateChecker {

    private static final String PREFS = "omni_update";
    private static final String KEY_LAST = "last_check_ms";
    private static final long DAY_MS = 24L * 60 * 60 * 1000;
    private static final String LATEST =
            "https://api.github.com/repos/Pranshu198/omnitools-native/releases/latest";
    private static final int TIMEOUT_MS = 8000;
    private static final int NOTES_MAX = 600;

    private UpdateChecker() {}

    /** Automatic check: at most once per day, fully silent on failure. */
    public static void maybeCheck(Activity activity) {
        SharedPreferences sp = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        if (now - sp.getLong(KEY_LAST, 0) < DAY_MS) return;
        sp.edit().putLong(KEY_LAST, now).apply();
        run(activity, false);
    }

    /** Manual "Check for updates": ignores the throttle, reports by Toast. */
    public static void checkNow(Activity activity) {
        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putLong(KEY_LAST, System.currentTimeMillis()).apply();
        run(activity, true);
    }

    private static void run(final Activity activity, final boolean manual) {
        final String current = currentVersion(activity);
        if (current == null) {
            if (manual) toast(activity, "Could not read the current version.");
            return;
        }
        Thread t = new Thread(() -> {
            try {
                JSONObject rel = new JSONObject(Net.get(LATEST, TIMEOUT_MS));
                String latest = rel.optString("tag_name", "").replaceFirst("^[vV]", "");
                if (latest.isEmpty()) throw new Exception("no tag_name");
                if (!Versions.isNewer(latest, current)) {
                    if (manual) toast(activity, "You are up to date (" + current + ").");
                    return;
                }
                String apkUrl = null;
                JSONArray assets = rel.optJSONArray("assets");
                if (assets != null) {
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject a = assets.optJSONObject(i);
                        if (a != null && a.optString("name", "").endsWith(".apk")) {
                            apkUrl = a.optString("browser_download_url", null);
                            break;
                        }
                    }
                }
                final String download = apkUrl != null
                        ? apkUrl : rel.optString("html_url", "");
                if (download.isEmpty()) throw new Exception("no download url");
                final String ver = latest;
                final String notes = trimNotes(rel.optString("body", ""));
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing()) return;
                    String msg = "OmniTools Native " + ver + " is ready. You have "
                            + current + ". The update downloads as an APK and "
                            + "installs over this app — your data stays."
                            + (notes.isEmpty() ? "" : "\n\nWhat's new:\n" + notes);
                    new AlertDialog.Builder(activity)
                            .setTitle("Update available")
                            .setMessage(msg)
                            .setPositiveButton("Download", (d, w) -> {
                                try {
                                    activity.startActivity(new Intent(
                                            Intent.ACTION_VIEW, Uri.parse(download)));
                                } catch (Exception ignored) {}
                            })
                            .setNegativeButton("Later", null)
                            .show();
                });
            } catch (Exception ignored) {
                // Offline or GitHub unreachable: silent on the auto path.
                if (manual) toast(activity, "Update check failed. Check the connection and try again.");
            }
        }, "omni-update-check");
        t.setDaemon(true);
        t.start();
    }

    private static String trimNotes(String body) {
        if (body == null) return "";
        String s = body.trim();
        if (s.length() > NOTES_MAX) s = s.substring(0, NOTES_MAX).trim() + "…";
        return s;
    }

    private static void toast(final Activity a, final String msg) {
        a.runOnUiThread(() -> {
            if (!a.isFinishing()) Toast.makeText(a, msg, Toast.LENGTH_SHORT).show();
        });
    }

    private static String currentVersion(Activity a) {
        try {
            PackageInfo pi = a.getPackageManager().getPackageInfo(a.getPackageName(), 0);
            return pi.versionName;
        } catch (Exception e) {
            return null;
        }
    }
}
