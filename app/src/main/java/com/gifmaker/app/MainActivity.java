package com.gifmaker.app;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ContentUris;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.util.Size;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.label.ImageLabel;
import com.google.mlkit.vision.label.ImageLabeler;
import com.google.mlkit.vision.label.ImageLabeling;
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {
    private static final int REQ = 1, PERM = 2, PERM2 = 3;
    private ImageLabeler labeler;
    private WebView web;
    private ValueCallback<Uri[]> pending;
    private String pb64, pname;
    private long exitAt;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        web = new WebView(this);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        if (Build.VERSION.SDK_INT >= 29) s.setForceDark(WebSettings.FORCE_DARK_OFF);
        web.addJavascriptInterface(new Bridge(), "AndroidBridge");
        web.setWebViewClient(new WebViewClient() {
            private boolean handle(Uri u) {
                if ("file".equals(u.getScheme())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception e) { }
                return true;
            }
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) { return handle(r.getUrl()); }
            @Override
            @SuppressWarnings("deprecation")
            public boolean shouldOverrideUrlLoading(WebView v, String url) { return handle(Uri.parse(url)); }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView w, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (pending != null) pending.onReceiveValue(null);
                pending = cb;
                String[] t = p.getAcceptTypes();
                boolean img = t != null && t.length > 0 && t[0] != null && t[0].startsWith("image");
                boolean multi = p.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE;
                try {
                    Intent i;
                    if (Build.VERSION.SDK_INT >= 33 && img && multi) {
                        i = new Intent("android.provider.action.PICK_IMAGES");
                        i.putExtra("android.provider.extra.PICK_IMAGES_MAX", 50);
                    } else {
                        i = p.createIntent();
                    }
                    startActivityForResult(i, REQ);
                } catch (Exception e) {
                    pending = null;
                    return false;
                }
                return true;
            }
        });
        web.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == REQ && pending != null) {
            pending.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, data));
            pending = null;
        } else {
            super.onActivityResult(req, res, data);
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        if (code == PERM && res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED && pb64 != null) {
            doSave(pb64, pname);
        } else if (code == PERM2) {
            if (res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) loadGallery();
            else toast("אין הרשאה לגשת לתמונות");
        } else if (code == PERM) {
            toast("אין הרשאה לשמירה בתיקיית ההורדות");
        }
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

    private void loadGallery() {
        new Thread(() -> {
            try (Cursor c = getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    new String[]{MediaStore.Images.Media._ID}, null, null, MediaStore.Images.Media.DATE_ADDED + " DESC")) {
                StringBuilder sb = new StringBuilder("[");
                int n = 0, tot = 0;
                while (c != null && c.moveToNext() && tot < 300) {
                    long id = c.getLong(0);
                    try {
                        Bitmap bm;
                        if (Build.VERSION.SDK_INT >= 29) {
                            bm = getContentResolver().loadThumbnail(ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id), new Size(160, 160), null);
                        } else {
                            bm = MediaStore.Images.Thumbnails.getThumbnail(getContentResolver(), id, MediaStore.Images.Thumbnails.MINI_KIND, null);
                        }
                        if (bm == null) continue;
                        ByteArrayOutputStream o = new ByteArrayOutputStream();
                        bm.compress(Bitmap.CompressFormat.JPEG, 60, o);
                        if (n++ > 0) sb.append(',');
                        sb.append("[").append(id).append(",\"data:image/jpeg;base64,").append(Base64.encodeToString(o.toByteArray(), Base64.NO_WRAP)).append("\"]");
                        tot++;
                        if (n == 40) { push(sb.append("]").toString()); sb = new StringBuilder("["); n = 0; }
                    } catch (Exception e) { }
                }
                if (n > 0) push(sb.append("]").toString());
            } catch (Exception e) {
                toast("טעינת הגלריה נכשלה: " + e.getMessage());
            }
        }).start();
    }

    class Bridge {
        @JavascriptInterface
        public void openGallery() {
            final String p = Build.VERSION.SDK_INT >= 33 ? "android.permission.READ_MEDIA_IMAGES" : Manifest.permission.READ_EXTERNAL_STORAGE;
            if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) {
                runOnUiThread(() -> requestPermissions(new String[]{p}, PERM2));
            } else {
                loadGallery();
            }
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
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                bm.compress(Bitmap.CompressFormat.JPEG, 88, out);
                return "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
            } catch (Exception e) {
                return "";
            }
        }

        @JavascriptInterface
        public void labelImage(String data, final String key) {
            try {
                byte[] b = Base64.decode(data.substring(data.indexOf(',') + 1), Base64.DEFAULT);
                Bitmap bm = BitmapFactory.decodeByteArray(b, 0, b.length);
                if (labeler == null) labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS);
                labeler.process(InputImage.fromBitmap(bm, 0)).addOnSuccessListener(ls -> {
                    StringBuilder sb = new StringBuilder("[");
                    for (ImageLabel l : ls) {
                        if (sb.length() > 1) sb.append(',');
                        sb.append('"').append(l.getText().replace("\"", "")).append('"');
                    }
                    sb.append("]");
                    web.evaluateJavascript("gLab('" + key + "'," + sb + ")", null);
                });
            } catch (Exception e) { }
        }

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
    }
}
