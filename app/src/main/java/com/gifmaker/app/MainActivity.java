package com.gifmaker.app;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.OutputStream;

public class MainActivity extends Activity {
    private static final int REQ = 1;
    private WebView web;
    private ValueCallback<Uri[]> pending;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        web = new WebView(this);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setForceDark(WebSettings.FORCE_DARK_OFF);
        web.addJavascriptInterface(new Bridge(), "AndroidBridge");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if ("file".equals(u.getScheme())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception e) { }
                return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView w, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (pending != null) pending.onReceiveValue(null);
                pending = cb;
                try {
                    startActivityForResult(p.createIntent(), REQ);
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

    private void toast(final String m) {
        runOnUiThread(() -> Toast.makeText(MainActivity.this, m, Toast.LENGTH_LONG).show());
    }

    Uri store(String b64, String name) throws Exception {
        byte[] data = Base64.decode(b64, Base64.DEFAULT);
        ContentValues v = new ContentValues();
        v.put(MediaStore.Downloads.DISPLAY_NAME, name);
        v.put(MediaStore.Downloads.MIME_TYPE, "image/gif");
        v.put(MediaStore.Downloads.RELATIVE_PATH, "Download");
        Uri u = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
        try (OutputStream o = getContentResolver().openOutputStream(u)) {
            o.write(data);
        }
        return u;
    }

    class Bridge {
        @JavascriptInterface
        public void saveGif(String b64, String name) {
            try {
                store(b64, name);
                toast("נשמר בתיקיית ההורדות: " + name);
            } catch (Exception e) {
                toast("השמירה נכשלה: " + e.getMessage());
            }
        }

        @JavascriptInterface
        public void shareGif(String b64, String name) {
            try {
                final Uri u = store(b64, name);
                runOnUiThread(() -> {
                    Intent i = new Intent(Intent.ACTION_SEND);
                    i.setType("image/gif");
                    i.putExtra(Intent.EXTRA_STREAM, u);
                    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(i, "שיתוף גיף"));
                });
            } catch (Exception e) {
                toast("השיתוף נכשל: " + e.getMessage());
            }
        }
    }
}
