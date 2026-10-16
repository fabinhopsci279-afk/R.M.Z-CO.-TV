package co.rmz.tv;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * R.M.Z CO. TV - WebView wrapper (celular + Android TV).
 * Carrega o app local em file:///android_asset/www/index.html
 * D-pad da TV funciona: setas = CH+/CH-, Enter = guia, Voltar = fecha guia/volta.
 */
public class MainActivity extends Activity {

    private WebView wv;
    private ValueCallback<Uri[]> fileCb;
    private static final int FILE_REQ = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        wv = new WebView(this);
        setContentView(wv);

        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);

        wv.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String u = request.getUrl().toString();
                // Pagina externa / youtube / vlc / rtsp: abre fora do app
                if (u.startsWith("vlc://") || u.startsWith("intent://")
                        || u.startsWith("rtsp://") || u.startsWith("rtmp://")
                        || u.contains("youtube.com") || u.contains("youtu.be")) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(u)));
                    } catch (ActivityNotFoundException ignored) {}
                    return true;
                }
                if (u.startsWith("http") && !u.contains(" ")) {
                    // deixa o player (hls.js) tratar; so abre fora se for pagina obvia
                    if (u.contains("pluto.tv")) {
                        try {
                            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(u)));
                        } catch (ActivityNotFoundException ignored) {}
                        return true;
                    }
                }
                return false;
            }
        });

        wv.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> cb,
                                             FileChooserParams params) {
                if (fileCb != null) fileCb.onReceiveValue(null);
                fileCb = cb;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                String[] mime = {"audio/mpegurl", "audio/x-mpegurl", "application/octet-stream", "application/zip"};
                i.putExtra(Intent.EXTRA_MIME_TYPES, mime);
                try {
                    startActivityForResult(Intent.createChooser(i, "Abrir lista (.m3u/.m3u8/.zip)"), FILE_REQ);
                } catch (ActivityNotFoundException e) {
                    fileCb = null;
                    return false;
                }
                return true;
            }
        });

        if (savedInstanceState != null) {
            wv.restoreState(savedInstanceState);
        } else {
            wv.loadUrl("file:///android_asset/www/index.html");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_REQ && fileCb != null) {
            Uri[] res = null;
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                res = new Uri[]{data.getData()};
            }
            fileCb.onReceiveValue(res);
            fileCb = null;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (wv != null) wv.saveState(outState);
    }

    @Override
    public void onBackPressed() {
        if (wv != null && wv.canGoBack()) wv.goBack();
        else super.onBackPressed();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Deixa o WebView/D-pad tratar setas e Enter (guia do app usa isso).
        // Botao VOLTAR do controle: fecha guia primeiro via JS, senao sai.
        if (keyCode == KeyEvent.KEYCODE_BACK && wv != null) {
            wv.evaluateJavascript(
                    "(function(){var g=document.getElementById('guia');" +
                    "if(g&&g.classList.contains('on')){g.classList.remove('on');return 'closed';}" +
                    "return 'back';})();",
                    v -> {
                        if (v != null && !v.contains("closed")) MainActivity.super.onBackPressed();
                    });
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        if (wv != null) wv.destroy();
        super.onDestroy();
    }
}
