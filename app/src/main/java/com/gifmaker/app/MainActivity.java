package com.gifmaker.app;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.ClipData;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Size;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import androidx.webkit.WebViewAssetLoader;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {
    private static final int PERM = 2, PERM2 = 3;
    private static final String HOST = "appassets.androidplatform.net";
    private WebView web;
    private String pb64, pname;
    private long exitAt;
    private volatile long gtoken;
    private MediaMetadataRetriever mmr;
    private int vw, vh;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        web = new WebView(this);
        setContentView(web);
        setTask();
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        if (Build.VERSION.SDK_INT >= 29) s.setForceDark(WebSettings.FORCE_DARK_OFF);
        web.addJavascriptInterface(new Bridge(), "AndroidBridge");
        web.setWebChromeClient(new WebChromeClient());
        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this)).build();
        web.setWebViewClient(new WebViewClient() {
            private boolean handle(Uri u) {
                if (HOST.equals(u.getHost())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception e) { }
                return true;
            }
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                return loader.shouldInterceptRequest(r.getUrl());
            }
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) { return handle(r.getUrl()); }
            @Override
            @SuppressWarnings("deprecation")
            public boolean shouldOverrideUrlLoading(WebView v, String url) { return handle(Uri.parse(url)); }
        });
        web.loadUrl("https://" + HOST + "/assets/index.html");
    }

    private void setTask() {
        try {
            int color = 0xFF0D1B6B;
            if (Build.VERSION.SDK_INT >= 28) {
                setTaskDescription(new ActivityManager.TaskDescription("גיף סטייל", R.drawable.task_icon, color));
            } else {
                setTaskDescription(new ActivityManager.TaskDescription("גיף סטייל", BitmapFactory.decodeResource(getResources(), R.drawable.task_icon), color));
            }
        } catch (Exception e) { }
    }

    @Override
    protected void onResume() {
        super.onResume();
        setTask();
    }

    @Override
    public void onBackPressed() {
        web.evaluateJavascript("window.goBack&&goBack()", v -> {
            if ("true".equals(v)) return;
            if (exitAt + 2000 > System.currentTimeMillis()) { finish(); return; }
            exitAt = System.currentTimeMillis();
            toast("לחצו שוב כדי לצאת");
        });
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        boolean ok = res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED;
        if (code == PERM && ok && pb64 != null) doSave(pb64, pname);
        else if (code == PERM2 && ok) startGallery();
        else toast("אין הרשאה");
    }

    private void toast(final String m) {
        runOnUiThread(() -> Toast.makeText(MainActivity.this, m, Toast.LENGTH_LONG).show());
    }

    private void doSave(String b64, String name) {
        try {
            byte[] data = Base64.decode(b64, Base64.DEFAULT);
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues v = new ContentValues();
                v.put(MediaStore.Downloads.DISPLAY_NAME, name);
                v.put(MediaStore.Downloads.MIME_TYPE, "image/gif");
                v.put(MediaStore.Downloads.RELATIVE_PATH, "Download");
                Uri u = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                try (OutputStream o = getContentResolver().openOutputStream(u)) { o.write(data); }
            } else {
                File d = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                d.mkdirs();
                File f = new File(d, name);
                try (FileOutputStream o = new FileOutputStream(f)) { o.write(data); }
                sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(f)));
            }
            toast("נשמר בתיקיית ההורדות: " + name);
        } catch (Exception e) {
            toast("השמירה נכשלה: " + e.getMessage());
        }
    }

    private void push(final String json) {
        runOnUiThread(() -> web.evaluateJavascript("gAdd(" + json + ")", null));
    }

    private String jpeg(Bitmap bm, int q) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        bm.compress(Bitmap.CompressFormat.JPEG, q, o);
        return "data:image/jpeg;base64," + Base64.encodeToString(o.toByteArray(), Base64.NO_WRAP);
    }

    private String thumbUri(long id, boolean video) {
        try {
            Uri base = video ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI : MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            Bitmap bm;
            if (Build.VERSION.SDK_INT >= 29) {
                bm = getContentResolver().loadThumbnail(ContentUris.withAppendedId(base, id), new Size(200, 200), null);
            } else if (video) {
                bm = MediaStore.Video.Thumbnails.getThumbnail(getContentResolver(), id, MediaStore.Video.Thumbnails.MINI_KIND, null);
            } else {
                bm = MediaStore.Images.Thumbnails.getThumbnail(getContentResolver(), id, MediaStore.Images.Thumbnails.MINI_KIND, null);
            }
            return bm == null ? "" : jpeg(bm, 60);
        } catch (Exception e) {
            return "";
        }
    }

    private void startGallery() {
        listFolders();
        loadMedia("i", "");
    }

    private void fold(Uri uri, int vid, java.util.Map<String, Object[]> m) {
        String bc = vid == 1 ? MediaStore.Video.Media.BUCKET_ID : MediaStore.Images.Media.BUCKET_ID;
        String bn = vid == 1 ? MediaStore.Video.Media.BUCKET_DISPLAY_NAME : MediaStore.Images.Media.BUCKET_DISPLAY_NAME;
        try (Cursor c = getContentResolver().query(uri, new String[]{bc, bn, "_id", "date_added"}, null, null, "date_added DESC")) {
            while (c != null && c.moveToNext()) {
                String id = c.getString(0);
                Object[] o = m.get(id);
                if (o == null) {
                    m.put(id, new Object[]{c.getString(1) == null ? "?" : c.getString(1), 1, c.getLong(2), vid, c.getLong(3)});
                } else {
                    o[1] = (Integer) o[1] + 1;
                    if (c.getLong(3) > (Long) o[4]) { o[2] = c.getLong(2); o[3] = vid; o[4] = c.getLong(3); }
                }
            }
        } catch (Exception e) { }
    }

    private void listFolders() {
        new Thread(() -> {
            try {
                java.util.LinkedHashMap<String, Object[]> m = new java.util.LinkedHashMap<>();
                fold(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, 0, m);
                fold(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, 1, m);
                StringBuilder sb = new StringBuilder("[");
                for (java.util.Map.Entry<String, Object[]> e : m.entrySet()) {
                    Object[] o = e.getValue();
                    if (sb.length() > 1) sb.append(',');
                    sb.append("[\"").append(e.getKey()).append("\",\"")
                      .append(String.valueOf(o[0]).replace("\"", "").replace("\\", ""))
                      .append("\",").append(o[1]).append(",\"")
                      .append(thumbUri((Long) o[2], (Integer) o[3] == 1)).append("\"]");
                }
                final String json = sb.append("]").toString();
                runOnUiThread(() -> web.evaluateJavascript("gFolders(" + json + ")", null));
            } catch (Exception e) { }
        }).start();
    }

    private void collect(Uri uri, String bucketCol, String bucket, int vid, java.util.List<long[]> out) {
        String[] proj = vid == 1 ? new String[]{"_id", "date_added", "duration"} : new String[]{"_id", "date_added"};
        String sel = (bucket == null || bucket.isEmpty()) ? null : bucketCol + "=?";
        String[] args = sel == null ? null : new String[]{bucket};
        try (Cursor c = getContentResolver().query(uri, proj, sel, args, "date_added DESC")) {
            while (c != null && c.moveToNext()) out.add(new long[]{c.getLong(0), vid, c.getLong(1), vid == 1 ? c.getLong(2) : 0});
        } catch (Exception e) { }
    }

    private void loadMedia(final String kind, final String bucket) {
        final long token = ++gtoken;
        new Thread(() -> {
            try {
                java.util.ArrayList<long[]> list = new java.util.ArrayList<>();
                if (!kind.equals("v")) collect(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, MediaStore.Images.Media.BUCKET_ID, bucket, 0, list);
                if (!kind.equals("i")) collect(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, MediaStore.Video.Media.BUCKET_ID, bucket, 1, list);
                java.util.Collections.sort(list, (a, b) -> Long.compare(b[2], a[2]));
                StringBuilder sb = new StringBuilder("[");
                int n = 0;
                for (int i = 0; i < list.size() && i < 400; i++) {
                    if (token != gtoken) return;
                    long[] r = list.get(i);
                    String t = thumbUri(r[0], r[1] == 1);
                    if (t.isEmpty()) continue;
                    if (n++ > 0) sb.append(',');
                    sb.append('[').append(r[0]).append(",\"").append(t).append("\",").append(r[1]).append(',').append(r[3]).append(']');
                    if (n == 30) { push(sb.append(']').toString()); sb = new StringBuilder("["); n = 0; }
                }
                if (n > 0 && token == gtoken) push(sb.append(']').toString());
            } catch (Exception e) {
                toast("טעינת הגלריה נכשלה: " + e.getMessage());
            }
        }).start();
    }

    class Bridge {
        @JavascriptInterface
        public void saveGif(String b64, String name) {
            if (Build.VERSION.SDK_INT >= 23 && Build.VERSION.SDK_INT < 29
                    && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                pb64 = b64;
                pname = name;
                runOnUiThread(() -> requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERM));
                return;
            }
            doSave(b64, name);
        }

        @JavascriptInterface
        public void shareGif(String b64, String name) {
            try {
                File f = new File(getCacheDir(), name);
                try (FileOutputStream o = new FileOutputStream(f)) { o.write(Base64.decode(b64, Base64.DEFAULT)); }
                final Uri u = FileProvider.getUriForFile(MainActivity.this, "com.gifmaker.app.fp", f);
                runOnUiThread(() -> {
                    Intent i = new Intent(Intent.ACTION_SEND);
                    i.setType("image/gif");
                    i.putExtra(Intent.EXTRA_STREAM, u);
                    i.setClipData(ClipData.newRawUri("", u));
                    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(i, "שיתוף גיף"));
                });
            } catch (Exception e) {
                toast("השיתוף נכשל: " + e.getMessage());
            }
        }

        @JavascriptInterface
        public void openGallery() {
            final String[] p = Build.VERSION.SDK_INT >= 33
                    ? new String[]{"android.permission.READ_MEDIA_IMAGES", "android.permission.READ_MEDIA_VIDEO"}
                    : new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
            if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(p[0]) != PackageManager.PERMISSION_GRANTED) {
                runOnUiThread(() -> requestPermissions(p, PERM2));
            } else {
                startGallery();
            }
        }

        @JavascriptInterface
        public void loadMedia(String kind, String bucket) {
            MainActivity.this.loadMedia(kind, bucket);
        }

        @JavascriptInterface
        public String getPhoto(String id) {
            try {
                Uri u = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, Long.parseLong(id));
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                try (InputStream in = getContentResolver().openInputStream(u)) { BitmapFactory.decodeStream(in, null, o); }
                int ss = 1;
                while (Math.max(o.outWidth, o.outHeight) / ss > 1600) ss *= 2;
                o.inJustDecodeBounds = false;
                o.inSampleSize = ss;
                Bitmap bm;
                try (InputStream in = getContentResolver().openInputStream(u)) { bm = BitmapFactory.decodeStream(in, null, o); }
                int rot = 0;
                if (Build.VERSION.SDK_INT >= 24) {
                    try (InputStream in = getContentResolver().openInputStream(u)) {
                        int ori = new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1);
                        rot = ori == 6 ? 90 : ori == 3 ? 180 : ori == 8 ? 270 : 0;
                    } catch (Exception e) { }
                }
                if (rot != 0) {
                    Matrix m = new Matrix();
                    m.postRotate(rot);
                    bm = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), m, true);
                }
                return jpeg(bm, 88);
            } catch (Exception e) {
                return "";
            }
        }

        @JavascriptInterface
        public String videoOpen(String id) {
            try {
                videoClose();
                mmr = new MediaMetadataRetriever();
                mmr.setDataSource(MainActivity.this, ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, Long.parseLong(id)));
                vw = Integer.parseInt(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
                vh = Integer.parseInt(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
                String rot = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
                if ("90".equals(rot) || "270".equals(rot)) { int t = vw; vw = vh; vh = t; }
                return "ok";
            } catch (Exception e) {
                return "";
            }
        }

        @JavascriptInterface
        public String videoFrame(String ms) {
            try {
                long us = Long.parseLong(ms) * 1000L;
                float sc = Math.min(1f, 480f / Math.max(vw, vh));
                int w = Math.max(2, Math.round(vw * sc)), h = Math.max(2, Math.round(vh * sc));
                Bitmap b = Build.VERSION.SDK_INT >= 27
                        ? mmr.getScaledFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST, w, h)
                        : mmr.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST);
                return b == null ? "" : jpeg(b, 85);
            } catch (Exception e) {
                return "";
            }
        }

        @JavascriptInterface
        public void videoClose() {
            try { if (mmr != null) mmr.release(); } catch (Exception e) { }
            mmr = null;
        }
    }
}
