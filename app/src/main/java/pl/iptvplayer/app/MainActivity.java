package pl.iptvplayer.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * IPTV Player na Androida (telefon, tablet i Android TV).
 *
 * Cała aplikacja to ten sam plik index.html co wersja na Titan OS (folder assets),
 * wyświetlany w pełnoekranowym WebView. Dzięki temu wygląd i działanie są identyczne.
 *
 * Różnice względem przeglądarki:
 *  - brak blokad CORS i działają adresy http:// (wiele list IPTV i serwerów Xtream),
 *  - przyciski pilota Android TV (Wstecz, kolorowe, CH+/-, Info, Menu) są przekazywane do aplikacji,
 *  - podwójne Wstecz zamyka aplikację, ekran nie wygasza się podczas oglądania.
 */
public class MainActivity extends Activity {

    private WebView web;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        web = new WebView(this);
        web.setBackgroundColor(0xFF000000);
        setContentView(web);
        hideSystemUi();

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);                       // pamięć playlist, ulubionych i ustawień
        s.setMediaPlaybackRequiresUserGesture(false);       // autoodtwarzanie kanału po starcie
        s.setAllowFileAccess(true);
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);        // pobieranie list i EPG bez blokad CORS
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);   // strumienie http://
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);

        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new Bridge(), "Android");
        web.setFocusable(true);
        web.setFocusableInTouchMode(true);
        web.requestFocus();

        if (savedInstanceState != null) web.restoreState(savedInstanceState);
        else web.loadUrl("file:///android_asset/index.html");
    }

    /** Funkcje dostępne z JavaScriptu jako window.Android */
    private class Bridge {
        @JavascriptInterface
        public void exitApp() {
            runOnUiThread(new Runnable() {
                @Override public void run() { finish(); }
            });
        }
    }

    /** Kody przycisków pilota, które WebView sam nie przekazuje → kody znane aplikacji (jak w telewizorach). */
    private static int jsKeyCode(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:         return 461;
            case KeyEvent.KEYCODE_PROG_RED:     return 403;
            case KeyEvent.KEYCODE_PROG_GREEN:   return 404;
            case KeyEvent.KEYCODE_PROG_YELLOW:  return 405;
            case KeyEvent.KEYCODE_PROG_BLUE:    return 406;
            case KeyEvent.KEYCODE_INFO:         return 457;
            case KeyEvent.KEYCODE_CHANNEL_UP:   return 427;
            case KeyEvent.KEYCODE_CHANNEL_DOWN: return 428;
            case KeyEvent.KEYCODE_PAGE_UP:      return 427;
            case KeyEvent.KEYCODE_PAGE_DOWN:    return 428;
            case KeyEvent.KEYCODE_MENU:         return 1000;
            case KeyEvent.KEYCODE_GUIDE:        return 404;   // przycisk „Program” na niektórych pilotach
            default:                            return 0;
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int js = jsKeyCode(event.getKeyCode());
        if (js != 0) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                sendKey("keydown", js);
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                sendKey("keyup", js);
            }
            return true;   // obsługuje aplikacja (np. Wstecz nie zamyka jej od razu)
        }
        return super.dispatchKeyEvent(event);   // strzałki, OK i cyfry trafiają do WebView normalnie
    }

    private void sendKey(String type, int code) {
        if (web == null) return;
        web.evaluateJavascript("window.tvKey && window.tvKey('" + type + "'," + code + ")", null);
    }

    @SuppressWarnings("deprecation")
    private void hideSystemUi() {
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        if (Build.VERSION.SDK_INT >= 28) {
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (web != null) web.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.onResume();
        hideSystemUi();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (web != null) web.saveState(outState);
    }

    @Override
    protected void onDestroy() {
        if (web != null) { web.destroy(); web = null; }
        super.onDestroy();
    }
}
