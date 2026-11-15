package com.limbusarchive.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
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

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Limbus Archive for Android.
 * - PC mode: the desktop app's own server on the user's PC (every feature), through {@link Relay}.
 * - Web mode: the web edition at limbus-archive.com (no PC needed, public pages only).
 */
public class MainActivity extends Activity {
    static final String WEB_HOST = "limbus-archive.com";
    static final String WEB = "https://" + WEB_HOST + "/";
    static final String START = "file:///android_asset/start.html";
    static final int FILE_CHOOSER = 1;
    static final String BG = "#0d0a09";

    WebView web;
    FrameLayout root;
    View customView;
    WebChromeClient.CustomViewCallback customCallback;
    ValueCallback<Uri[]> fileCallback;
    SharedPreferences prefs;
    Relay relay;
    String home = START;        // the current mode's front page
    String pendingHash = null;  // "#/page" from a limbusarchive:// link
    String startError = null;
    boolean webFallback = false; // a shortcut whose page the web edition has too

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("limbus", MODE_PRIVATE);
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor(BG));
        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor(BG));
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
        s.setAllowFileAccess(false);
        s.setUserAgentString(s.getUserAgentString() + " LimbusArchiveAndroid/2.0");
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);

        web.addJavascriptInterface(new Native(), "Native");
        web.addJavascriptInterface(new Native(), "LimbusAndroid");
        web.setWebViewClient(new Client());
        web.setWebChromeClient(new Chrome());
        web.setDownloadListener((url, ua, disposition, mime, length) -> download(url, ua, disposition, mime));

        pendingHash = linkHash(getIntent());
        if (prefs.getBoolean("auto", false) && !prefs.getString("ip", "").isEmpty()) {
            showStart();
            connectPc(prefs.getString("ip", ""), prefs.getInt("relay", 47822), prefs.getInt("port", 47821), true, true);
        } else {
            showStart();
        }
    }

    // ---------------------------------------------------------------- modes

    void showStart() {
        home = START;
        web.loadUrl(START);
        web.clearHistory();
    }

    void openWeb() {
        stopRelay();
        home = WEB;
        web.loadUrl(WEB + (pendingHash != null ? pendingHash : ""));
        pendingHash = null;
        clearHistorySoon();
    }

    void stopRelay() {
        if (relay != null) { relay.stop(); relay = null; }
    }

    /** Opens the relay, checks the desktop app answers through it, then loads its pages. */
    void connectPc(String ip, int relayPort, int pcPort, boolean auto, boolean silent) {
        prefs.edit().putString("ip", ip).putInt("relay", relayPort).putInt("port", pcPort).putBoolean("auto", auto).apply();
        new Thread(() -> {
            String err = null;
            try {
                stopRelay();
                Relay r = new Relay(pcPort, ip, relayPort);
                r.start();
                relay = r;
                HttpURLConnection c = (HttpURLConnection) new URL("http://127.0.0.1:" + pcPort + "/").openConnection();
                c.setConnectTimeout(6000);
                c.setReadTimeout(10000);
                int code = c.getResponseCode();
                InputStream in = code < 400 ? c.getInputStream() : c.getErrorStream();
                byte[] buf = new byte[4096];
                int n = in == null ? -1 : in.read(buf);
                String body = n > 0 ? new String(buf, 0, n, StandardCharsets.UTF_8) : "";
                c.disconnect();
                if (code != 200 || !body.contains("Limbus")) err = "PC가 응답했지만 Limbus Archive가 아닙니다 (HTTP " + code + ").";
            } catch (java.net.BindException e) {
                err = "폰의 " + pcPort + " 포트를 열 수 없습니다. 앱을 완전히 종료한 뒤 다시 시도하세요.";
            } catch (Exception e) {
                err = "PC(" + ip + ":" + relayPort + ")에 연결하지 못했습니다.\n"
                        + "· PC에서 LimbusArchive.exe가 켜져 있는지\n· pc/enable-mobile.bat을 실행했는지\n· 같은 Wi‑Fi인지 확인하세요.";
            }
            final String error = err;
            runOnUiThread(() -> {
                if (error == null) {
                    home = "http://127.0.0.1:" + pcPort + "/";
                    web.loadUrl(home + (pendingHash != null ? pendingHash : ""));
                    pendingHash = null;
                    webFallback = false;
                    clearHistorySoon();
                    toast("PC에 연결됨: " + ip);
                } else {
                    stopRelay();
                    if (webFallback && pendingHash != null) {
                        webFallback = false;
                        toast("PC에 연결할 수 없어 웹 버전으로 엽니다");
                        openWeb();
                        return;
                    }
                    pendingHash = null;
                    if (silent) { startError = error; showStart(); }
                    else js("onConnect(false," + JSONObject.quote(error) + ")");
                }
            });
        }).start();
    }

    void clearHistorySoon() {
        web.postDelayed(() -> web.clearHistory(), 1500);
    }

    /** Finds PCs on this network whose relay port answers as Limbus Archive. */
    void scan(int relayPort) {
        new Thread(() -> {
            List<String> hosts = new ArrayList<>();
            try {
                for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                    if (!ni.isUp() || ni.isLoopback()) continue;
                    for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                        if (!(a instanceof Inet4Address) || a.isLinkLocalAddress()) continue;
                        byte[] b = a.getAddress();
                        if ((b[0] & 0xff) == 100) continue;  // Tailscale/CGNAT: too wide to sweep
                        String p = (b[0] & 0xff) + "." + (b[1] & 0xff) + "." + (b[2] & 0xff) + ".";
                        for (int i = 1; i < 255; i++) if (i != (b[3] & 0xff)) hosts.add(p + i);
                    }
                }
            } catch (Exception ignored) { }
            List<String> found = Collections.synchronizedList(new ArrayList<>());
            int pcPort = prefs.getInt("port", 47821);
            ExecutorService pool = Executors.newFixedThreadPool(48);
            for (String h : hosts) pool.execute(() -> {
                try (Socket so = new Socket()) {
                    so.connect(new InetSocketAddress(h, relayPort), 500);
                    so.setSoTimeout(2000);
                    so.getOutputStream().write(("GET / HTTP/1.0\r\nHost: 127.0.0.1:" + pcPort + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                    byte[] buf = new byte[8192];
                    int n, total = 0;
                    StringBuilder sb = new StringBuilder();
                    InputStream in = so.getInputStream();
                    while (total < 8192 && (n = in.read(buf)) > 0) { sb.append(new String(buf, 0, n, StandardCharsets.UTF_8)); total += n; }
                    if (sb.indexOf("Limbus") >= 0) {
                        found.add(h);
                        String list = new JSONArray(found).toString();
                        runOnUiThread(() -> js("onScan(" + list + ",false)"));
                    }
                } catch (Exception ignored) { }
            });
            pool.shutdown();
            try { pool.awaitTermination(60, TimeUnit.SECONDS); } catch (InterruptedException ignored) { }
            String list = new JSONArray(found).toString();
            runOnUiThread(() -> js("onScan(" + list + ",true)"));
        }).start();
    }

    void js(String code) { web.evaluateJavascript(code, null); }

    /** JS bridge (start page, and blob downloads from any page). */
    class Native {
        @JavascriptInterface
        public String saved() {
            try {
                JSONObject o = new JSONObject();
                o.put("ip", prefs.getString("ip", ""));
                o.put("relay", prefs.getInt("relay", 47822));
                o.put("port", prefs.getInt("port", 47821));
                o.put("auto", prefs.getBoolean("auto", false));
                if (startError != null) { o.put("error", startError); startError = null; }
                return o.toString();
            } catch (Exception e) { return "{}"; }
        }

        @JavascriptInterface
        public void connectPc(String ip, int relayPort, int pcPort, boolean auto) {
            runOnUiThread(() -> MainActivity.this.connectPc(ip, relayPort, pcPort, auto, false));
        }

        @JavascriptInterface
        public void scan(int relayPort) { MainActivity.this.scan(relayPort); }

        @JavascriptInterface
        public void target(String hash, boolean fallback) { pendingHash = hash; webFallback = fallback; }

        @JavascriptInterface
        public void openWeb() { runOnUiThread(MainActivity.this::openWeb); }

        @JavascriptInterface
        public void home() { runOnUiThread(() -> { stopRelay(); showStart(); }); }

        @JavascriptInterface
        public void retry() { runOnUiThread(() -> web.loadUrl(home)); }

        /** data: URL (a blob read in the page) -> Downloads/LimbusArchive. */
        @JavascriptInterface
        public void save(String dataUrl, String name, String mime) {
            try {
                int comma = dataUrl.indexOf(',');
                String head = dataUrl.substring(0, comma);
                byte[] bytes = head.endsWith(";base64")
                        ? Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT)
                        : Uri.decode(dataUrl.substring(comma + 1)).getBytes(StandardCharsets.UTF_8);
                if ((mime == null || mime.isEmpty()) && head.startsWith("data:")) mime = head.substring(5).split(";")[0];
                saveStream(new ByteArrayInputStream(bytes), name, mime);
            } catch (Exception e) {
                toast("파일을 저장하지 못했습니다");
            }
        }
    }

    // ---------------------------------------------------------------- links, lifecycle

    /** limbusarchive://<page> -> "#/<page>", like the desktop app. */
    static String linkHash(Intent intent) {
        Uri data = intent == null ? null : intent.getData();
        if (data == null || !"limbusarchive".equals(data.getScheme())) return null;
        String rest = data.toString().replaceFirst("^limbusarchive:", "");
        Matcher m = Pattern.compile("/*(?:open/+)?#?/*([\\w.%~-]+(?:/[\\w.%~-]+)*)/?").matcher(rest);
        return m.matches() ? "#/" + m.group(1) : null;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String h = linkHash(intent);
        if (h == null) return;
        if (home.equals(START)) pendingHash = h;
        else web.loadUrl(home + h);
    }

    @Override
    protected void onPause() { super.onPause(); web.onPause(); CookieManager.getInstance().flush(); }

    @Override
    protected void onResume() { super.onResume(); web.onResume(); }

    @Override
    protected void onDestroy() { stopRelay(); web.destroy(); super.onDestroy(); }

    @Override
    public void onBackPressed() {
        if (customView != null) { ((Chrome) web.getWebChromeClient()).onHideCustomView(); return; }
        if (web.canGoBack()) { web.goBack(); return; }
        if (!home.equals(START)) { stopRelay(); showStart(); return; }  // back to the mode picker
        super.onBackPressed();
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
        catch (ActivityNotFoundException e) { toast("이 링크를 열 수 있는 앱이 없습니다"); }
    }

    void toast(String msg) { runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()); }

    // ---------------------------------------------------------------- downloads

    void download(String url, String ua, String disposition, String mime) {
        String name = URLUtil.guessFileName(url, disposition, mime);
        if (url.startsWith("blob:")) {
            js("(function(){fetch(" + JSONObject.quote(url) + ").then(r=>r.blob()).then(b=>{var f=new FileReader();"
                    + "f.onload=function(){Native.save(f.result," + JSONObject.quote(name) + "," + JSONObject.quote(mime == null ? "" : mime) + ")};f.readAsDataURL(b)})})()");
            return;
        }
        if (url.startsWith("data:")) { new Native().save(url, name, mime); return; }
        toast("다운로드 중: " + name);
        new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setRequestProperty("User-Agent", ua);
                String cookies = CookieManager.getInstance().getCookie(url);
                if (cookies != null) c.setRequestProperty("Cookie", cookies);
                c.setConnectTimeout(15000);
                c.setReadTimeout(60000);
                try (InputStream in = c.getInputStream()) {
                    String m = mime != null && !mime.isEmpty() ? mime : c.getContentType();
                    saveStream(in, name, m);
                }
            } catch (Exception e) {
                toast("다운로드 실패: " + name);
            }
        }).start();
    }

    void saveStream(InputStream in, String name, String mime) throws Exception {
        if (mime == null || mime.isEmpty()) mime = "application/octet-stream";
        name = name.replaceAll("[\\\\/:*?\"<>|]", "_");
        OutputStream out;
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues v = new ContentValues();
            v.put(MediaStore.Downloads.DISPLAY_NAME, name);
            v.put(MediaStore.Downloads.MIME_TYPE, mime.split(";")[0]);
            v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/LimbusArchive");
            Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            out = getContentResolver().openOutputStream(uri);
        } else {
            File dir = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "LimbusArchive");
            dir.mkdirs();
            out = new FileOutputStream(new File(dir, name));
        }
        try (OutputStream o = out) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) >= 0) o.write(buf, 0, n);
        }
        toast("저장됨: Download/LimbusArchive/" + name);
    }

    // ---------------------------------------------------------------- WebView clients

    class Client extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
            Uri u = req.getUrl();
            String scheme = u.getScheme(), host = u.getHost();
            if ("limbusarchive".equals(scheme)) {
                String h = linkHash(new Intent(Intent.ACTION_VIEW, u));
                if (h != null && !home.equals(START)) view.loadUrl(home + h);
                return true;
            }
            if ("file".equals(scheme)) return !u.toString().startsWith("file:///android_asset/");
            boolean ours = host != null && (host.equals(WEB_HOST) || host.endsWith("." + WEB_HOST)
                    || host.equals("127.0.0.1") || host.equals("localhost"));
            if (("http".equals(scheme) || "https".equals(scheme)) && ours) return false;
            openExternal(u);  // other sites and intent:/mailto: go to their apps
            return true;
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError err) {
            if (!req.isForMainFrame() || home.equals(START)) return;
            boolean pc = !home.equals(WEB);
            String text = pc ? "PC의 Limbus Archive에 연결할 수 없습니다.<br>PC에서 앱이 켜져 있는지 확인하세요."
                    : "서버에 연결할 수 없습니다. 인터넷 연결을 확인하세요.";
            String html = "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'></head>"
                    + "<body style='background:" + BG + ";color:#e8dccb;font-family:sans-serif;display:flex;"
                    + "flex-direction:column;align-items:center;justify-content:center;height:90vh;text-align:center;padding:0 20px'>"
                    + "<h2>Limbus Archive</h2><p>" + text + "</p><p style='opacity:.5;font-size:13px'>" + err.getDescription() + "</p>"
                    + "<button style='padding:12px 28px;font-size:16px;background:#7a1f1f;color:#fff;border:0;border-radius:6px;margin:6px' onclick='Native.retry()'>다시 시도</button>"
                    + "<button style='padding:12px 28px;font-size:16px;background:#3a2b24;color:#fff;border:0;border-radius:6px;margin:6px' onclick='Native.home()'>처음 화면</button>"
                    + "</body></html>";
            view.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);
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
