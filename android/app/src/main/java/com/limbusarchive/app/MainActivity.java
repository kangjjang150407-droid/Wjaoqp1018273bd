package com.limbusarchive.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Limbus Archive for Android: a full-screen WebView over the web edition of the
 * desktop app (the same UI the desktop app serves from its local server).
 */
public class MainActivity extends Activity {
    static final String HOST = "limbus-archive.com";
    static final String BASE = "https://" + HOST + "/";
    static final int FILE_CHOOSER = 1;

    WebView web;
    FrameLayout root;
    View customView;
    WebChromeClient.CustomViewCallback customCallback;
    ValueCallback<Uri[]> fileCallback;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#0d0a09"));
        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#0d0a09"));
        root.addView(web, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setUserAgentString(s.getUserAgentString() + " LimbusArchiveAndroid/1.0");
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);

        web.addJavascriptInterface(new BlobSaver(), "LimbusAndroid");
        web.setWebViewClient(new Client());
        web.setWebChromeClient(new Chrome());
        web.setDownloadListener((url, ua, disposition, mime, length) -> download(url, ua, disposition, mime));

        if (state != null) web.restoreState(state);
        else web.loadUrl(startUrl(getIntent()));
    }

    /** limbusarchive://<page> links open "#/<page>", like the desktop app. */
    String startUrl(Intent intent) {
        Uri data = intent == null ? null : intent.getData();
        if (data != null && "limbusarchive".equals(data.getScheme())) {
            String rest = data.toString().replaceFirst("^limbusarchive:", "");
            Matcher m = Pattern.compile("/*(?:open/+)?#?/*([\\w.%~-]+(?:/[\\w.%~-]+)*)/?").matcher(rest);
            if (m.matches()) return BASE + "#/" + m.group(1);
        }
        return BASE;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent.getData() != null) web.loadUrl(startUrl(intent));
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    protected void onPause() { super.onPause(); web.onPause(); CookieManager.getInstance().flush(); }

    @Override
    protected void onResume() { super.onResume(); web.onResume(); }

    @Override
    public void onBackPressed() {
        if (customView != null) { ((Chrome) web.getWebChromeClient()).onHideCustomView(); return; }
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == FILE_CHOOSER && fileCallback != null) {
            fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, data));
            fileCallback = null;
            return;
        }
        super.onActivityResult(req, res, data);
    }

    void openExternal(Uri uri) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
        catch (ActivityNotFoundException e) { toast("No app can open this link"); }
    }

    void toast(String msg) { runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()); }

    void download(String url, String ua, String disposition, String mime) {
        if (url.startsWith("blob:")) {
            // blob: URLs live only inside the page; read them there and hand the bytes over.
            String name = URLUtil.guessFileName(url, disposition, mime);
            web.evaluateJavascript("(function(){fetch(" + quote(url) + ").then(r=>r.blob()).then(b=>{var f=new FileReader();"
                    + "f.onload=function(){LimbusAndroid.save(f.result," + quote(name) + "," + quote(mime == null ? "" : mime) + ")};f.readAsDataURL(b)})})()", null);
            return;
        }
        if (url.startsWith("data:")) { new BlobSaver().save(url, URLUtil.guessFileName(url, disposition, mime), mime); return; }
        try {
            DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url));
            String name = URLUtil.guessFileName(url, disposition, mime);
            r.setMimeType(mime);
            r.addRequestHeader("User-Agent", ua);
            String cookies = CookieManager.getInstance().getCookie(url);
            if (cookies != null) r.addRequestHeader("Cookie", cookies);
            r.setTitle(name);
            r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "LimbusArchive/" + name);
            ((DownloadManager) getSystemService(DOWNLOAD_SERVICE)).enqueue(r);
            toast("Downloading " + name);
        } catch (Exception e) {
            openExternal(Uri.parse(url));
        }
    }

    static String quote(String s) {
        return "'" + s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "") + "'";
    }

    /** Saves data: URLs (from blob downloads) to Downloads/LimbusArchive. */
    class BlobSaver {
        @JavascriptInterface
        public void save(String dataUrl, String name, String mime) {
            try {
                int comma = dataUrl.indexOf(',');
                String head = dataUrl.substring(0, comma);
                byte[] bytes = head.endsWith(";base64")
                        ? Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT)
                        : Uri.decode(dataUrl.substring(comma + 1)).getBytes("UTF-8");
                if ((mime == null || mime.isEmpty()) && head.startsWith("data:")) mime = head.substring(5).split(";")[0];
                if (mime == null || mime.isEmpty()) mime = "application/octet-stream";
                name = name.replaceAll("[\\\\/:*?\"<>|]", "_");
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.Downloads.DISPLAY_NAME, name);
                    v.put(MediaStore.Downloads.MIME_TYPE, mime);
                    v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/LimbusArchive");
                    Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                    try (OutputStream o = getContentResolver().openOutputStream(uri)) { o.write(bytes); }
                } else {
                    File dir = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "LimbusArchive");
                    dir.mkdirs();
                    try (FileOutputStream o = new FileOutputStream(new File(dir, name))) { o.write(bytes); }
                }
                toast("Saved to Downloads/LimbusArchive/" + name);
            } catch (Exception e) {
                toast("Could not save file");
            }
        }
    }

    class Client extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
            Uri u = req.getUrl();
            String scheme = u.getScheme();
            if ("limbusarchive".equals(scheme)) { view.loadUrl(startUrl(new Intent(Intent.ACTION_VIEW, u))); return true; }
            String host = u.getHost();
            boolean ours = host != null && (host.equals(HOST) || host.endsWith("." + HOST));
            if (("http".equals(scheme) || "https".equals(scheme)) && ours) return false;
            openExternal(u);  // other sites (YouTube, Discord, ...) and intent:/mailto: go to their apps
            return true;
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError err) {
            if (!req.isForMainFrame()) return;
            String html = "<html><body style='background:#0d0a09;color:#e8dccb;font-family:sans-serif;display:flex;"
                    + "flex-direction:column;align-items:center;justify-content:center;height:90vh;text-align:center'>"
                    + "<h2>Limbus Archive</h2><p>Can't reach the server. Check your internet connection.</p>"
                    + "<p style='opacity:.6'>" + err.getDescription() + "</p>"
                    + "<button style='padding:12px 28px;font-size:16px;background:#7a1f1f;color:#fff;border:0;border-radius:6px' "
                    + "onclick=\"location.href='" + BASE + "'\">Retry</button></body></html>";
            view.loadDataWithBaseURL(BASE, html, "text/html", "utf-8", BASE);
        }
    }

    class Chrome extends WebChromeClient {
        @Override
        public void onShowCustomView(View view, CustomViewCallback cb) {
            if (customView != null) { cb.onCustomViewHidden(); return; }
            customView = view;
            customCallback = cb;
            root.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            web.setVisibility(View.GONE);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }

        @Override
        public void onHideCustomView() {
            if (customView == null) return;
            root.removeView(customView);
            customView = null;
            web.setVisibility(View.VISIBLE);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
            if (customCallback != null) customCallback.onCustomViewHidden();
            customCallback = null;
        }

        @Override
        public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> cb, FileChooserParams params) {
            if (fileCallback != null) fileCallback.onReceiveValue(null);
            fileCallback = cb;
            try {
                startActivityForResult(params.createIntent(), FILE_CHOOSER);
            } catch (ActivityNotFoundException e) {
                fileCallback = null;
                return false;
            }
            return true;
        }
    }
}
