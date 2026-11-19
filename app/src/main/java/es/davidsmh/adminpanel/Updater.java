package es.davidsmh.adminpanel;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

/** Comprueba la ultima Release publica en GitHub y ofrece instalarla. */
final class Updater {
    private static final String API = "https://api.github.com/repos/davidsmh23/admin-panel-android/releases/latest";

    static void check(Activity a) {
        new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(API).openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                c.setRequestProperty("Accept", "application/vnd.github+json");
                if (c.getResponseCode() != 200) return;
                String body;
                try (InputStream in = c.getInputStream(); Scanner s = new Scanner(in).useDelimiter("\\A")) {
                    body = s.hasNext() ? s.next() : "";
                }
                JSONObject rel = new JSONObject(body);
                String tag = rel.getString("tag_name");
                String current = a.getPackageManager().getPackageInfo(a.getPackageName(), 0).versionName;
                if (!isNewer(tag, current)) return;
                JSONArray assets = rel.getJSONArray("assets");
                String url = null;
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject o = assets.getJSONObject(i);
                    if (o.getString("name").endsWith(".apk")) { url = o.getString("browser_download_url"); break; }
                }
                if (url == null) return;
                final String dl = url;
                a.runOnUiThread(() -> prompt(a, tag, dl));
            } catch (Exception ignored) { /* sin red o sin releases: no molestar */ }
        }).start();
    }

    private static void prompt(Activity a, String tag, String url) {
        if (a.isFinishing()) return;
        new AlertDialog.Builder(a)
                .setTitle("Actualización disponible")
                .setMessage("Hay una nueva versión (" + tag + "). ¿Descargar e instalar?")
                .setNegativeButton("Más tarde", null)
                .setPositiveButton("Actualizar", (d, w) -> download(a, url))
                .show();
    }

    private static void download(Activity a, String url) {
        if (Build.VERSION.SDK_INT >= 26 && !a.getPackageManager().canRequestPackageInstalls()) {
            Toast.makeText(a, "Permite instalar apps desde Admin Panel y vuelve a pulsar Actualizar", Toast.LENGTH_LONG).show();
            a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + a.getPackageName())));
            return;
        }
        DownloadManager dm = (DownloadManager) a.getSystemService(Context.DOWNLOAD_SERVICE);
        DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url))
                .setTitle("Admin Panel")
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(a, Environment.DIRECTORY_DOWNLOADS, "AdminPanel-update.apk");
        final long id = dm.enqueue(r);
        Toast.makeText(a, "Descargando actualización…", Toast.LENGTH_SHORT).show();

        BroadcastReceiver rc = new BroadcastReceiver() {
            @Override public void onReceive(Context ctx, Intent i) {
                if (i.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) != id) return;
                ctx.getApplicationContext().unregisterReceiver(this);
                Uri u = dm.getUriForDownloadedFile(id);
                if (u == null) { Toast.makeText(ctx, "Descarga fallida", Toast.LENGTH_LONG).show(); return; }
                Intent inst = new Intent(Intent.ACTION_VIEW)
                        .setDataAndType(u, "application/vnd.android.package-archive")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(inst);
            }
        };
        IntentFilter f = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (Build.VERSION.SDK_INT >= 33) a.getApplicationContext().registerReceiver(rc, f, Context.RECEIVER_EXPORTED);
        else a.getApplicationContext().registerReceiver(rc, f);
    }

    /** Compara "v1.2.3" con "1.2.3" numericamente. */
    static boolean isNewer(String remote, String local) {
        int[] r = parse(remote), l = parse(local);
        for (int i = 0; i < Math.max(r.length, l.length); i++) {
            int x = i < r.length ? r[i] : 0, y = i < l.length ? l[i] : 0;
            if (x != y) return x > y;
        }
        return false;
    }

    private static int[] parse(String v) {
        String[] p = v.replaceFirst("^[vV]", "").split("[.\\-]");
        int[] out = new int[p.length];
        for (int i = 0; i < p.length; i++) {
            try { out[i] = Integer.parseInt(p[i].replaceAll("\\D", "")); } catch (Exception e) { out[i] = 0; }
        }
        return out;
    }
}
