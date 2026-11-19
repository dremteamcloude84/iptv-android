package pl.iptvplayer.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import java.util.Locale;

/**
 * IPTV Player na Androida (telefon, tablet i Android TV).
 *
 * Interfejs to ten sam plik index.html co wersja na Titan OS (folder assets), w przezroczystym WebView.
 * Obraz odtwarza pod spodem sprzętowy odtwarzacz ExoPlayer (Media3) – jak w TiviMate i innych topowych
 * aplikacjach – dzięki czemu kanały HD/4K, HEVC i strumienie .ts startują szybko i płynnie.
 *
 * Inne różnice względem przeglądarki:
 *  - brak blokad CORS i działają adresy http:// (wiele list IPTV i serwerów Xtream),
 *  - przyciski pilota Android TV (Wstecz, kolorowe, CH+/-, Info, Menu) są przekazywane do aplikacji,
 *  - klawiatura ekranowa dla pól tekstowych, podwójne Wstecz zamyka aplikację, ekran nie gaśnie.
 */
public class MainActivity extends Activity {

    private WebView web;
    private PlayerView playerView;
    private ExoPlayer player;
    private int playerBufferSec = -1;
    private String currentUrl = null;
    private int retries = 0;
    private boolean hadPlayed = false;
    private final Handler main = new Handler(Looper.getMainLooper());

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        // pod spodem: sprzętowy odtwarzacz
        playerView = new PlayerView(this);
        playerView.setUseController(false);
        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        playerView.setShutterBackgroundColor(Color.BLACK);
        playerView.setKeepContentOnPlayerReset(false);
        playerView.setFocusable(false);
        playerView.setFocusableInTouchMode(false);
        root.addView(playerView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // na wierzchu: przezroczysty interfejs aplikacji
        web = new WebView(this);
        web.setBackgroundColor(Color.TRANSPARENT);
        root.addView(web, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
        hideSystemUi();

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);                       // pamięć playlist, ulubionych i ustawień
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);        // pobieranie list i EPG bez blokad CORS
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);   // adresy http://
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

    // =====================================================================
    //  Sprzętowy odtwarzacz (ExoPlayer / Media3)
    // =====================================================================
    private void ensurePlayer(int bufferSec) {
        if (player != null && bufferSec == playerBufferSec) return;
        releasePlayer();

        int minMs = Math.max(10, bufferSec) * 1000;
        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(minMs, minMs * 2, 1500, 3000)   // start już po 1,5 s danych
                .build();

        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setUserAgent("IPTVPlayer/1.0 (Linux; Android " + Build.VERSION.RELEASE + ") ExoPlayer")
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(20000);

        DefaultRenderersFactory renderers = new DefaultRenderersFactory(this)
                .setEnableDecoderFallback(true);

        player = new ExoPlayer.Builder(this, renderers)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(this).setDataSourceFactory(http))
                .setLoadControl(loadControl)
                .build();
        player.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true);
        player.addListener(listener);
        playerView.setPlayer(player);
        playerBufferSec = bufferSec;
    }

    private final Player.Listener listener = new Player.Listener() {
        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            if (isPlaying) {
                hadPlayed = true;
                retries = 0;
                sendEvent("playing", "");
            }
        }

        @Override
        public void onPlaybackStateChanged(int state) {
            if (state == Player.STATE_BUFFERING) sendEvent("buffering", "");
            else if (state == Player.STATE_ENDED && currentUrl != null) retryOrFail("ENDED");
        }

        @Override
        public void onPlayerError(PlaybackException error) {
            if (player == null) return;
            if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                player.seekToDefaultPosition();
                player.prepare();
                return;
            }
            retryOrFail(error.getErrorCodeName());
        }
    };

    private void retryOrFail(String reason) {
        final String url = currentUrl;
        if (url != null && hadPlayed && retries < 3) {          // przerwa w strumieniu – połącz ponownie
            retries++;
            sendEvent("retry", String.valueOf(retries));
            main.postDelayed(new Runnable() {
                @Override public void run() {
                    if (player != null && url.equals(currentUrl)) startPlayback(url, playerBufferSec, true);
                }
            }, 2000);
            return;
        }
        sendEvent("error", reason);
    }

    private void startPlayback(String url, int bufferSec, boolean isRetry) {
        ensurePlayer(bufferSec);
        currentUrl = url;
        if (!isRetry) { retries = 0; hadPlayed = false; }
        MediaItem.Builder item = new MediaItem.Builder().setUri(Uri.parse(url));
        if (url.toLowerCase(Locale.ROOT).contains("m3u8")) item.setMimeType(MimeTypes.APPLICATION_M3U8);
        player.setMediaItem(item.build());
        player.prepare();
        player.setPlayWhenReady(true);
    }

    private void stopPlayback() {
        currentUrl = null;
        main.removeCallbacksAndMessages(null);
        if (player != null) {
            player.stop();
            player.clearMediaItems();
        }
    }

    private void releasePlayer() {
        if (player != null) {
            player.removeListener(listener);
            player.release();
            player = null;
        }
        playerBufferSec = -1;
    }

    private void sendEvent(String event, String info) {
        if (web == null) return;
        String safe = info == null ? "" : info.replaceAll("[^A-Za-z0-9_ ./-]", "");
        web.evaluateJavascript("window.nativePlayer && window.nativePlayer('" + event + "','" + safe + "')", null);
    }

    // =====================================================================
    //  Funkcje dostępne z JavaScriptu jako window.Android
    // =====================================================================
    private class Bridge {
        @JavascriptInterface
        public void play(final String url, final int bufferSec) {
            runOnUiThread(new Runnable() { @Override public void run() { startPlayback(url, bufferSec, false); } });
        }

        @JavascriptInterface
        public void stop() {
            runOnUiThread(new Runnable() { @Override public void run() { stopPlayback(); } });
        }

        @JavascriptInterface
        public void showKeyboard() {
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    web.requestFocus();
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) imm.showSoftInput(web, InputMethodManager.SHOW_IMPLICIT);
                }
            });
        }

        @JavascriptInterface
        public void hideKeyboard() {
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) imm.hideSoftInputFromWindow(web.getWindowToken(), 0);
                }
            });
        }

        @JavascriptInterface
        public void exitApp() {
            runOnUiThread(new Runnable() { @Override public void run() { finish(); } });
        }
    }

    // =====================================================================
    //  Pilot: przyciski, których WebView sam nie przekazuje → kody znane aplikacji (jak w telewizorach)
    // =====================================================================
    private static int jsKeyCode(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:         return 461;
            case KeyEvent.KEYCODE_PROG_RED:     return 403;
            case KeyEvent.KEYCODE_PROG_GREEN:   return 404;
            case KeyEvent.KEYCODE_PROG_YELLOW:  return 405;
            case KeyEvent.KEYCODE_PROG_BLUE:    return 406;
            case KeyEvent.KEYCODE_INFO:         return 457;
            case KeyEvent.KEYCODE_GUIDE:        return 404;   // „Program”/„Guide” na niektórych pilotach
            case KeyEvent.KEYCODE_CHANNEL_UP:   return 427;
            case KeyEvent.KEYCODE_CHANNEL_DOWN: return 428;
            case KeyEvent.KEYCODE_PAGE_UP:      return 427;
            case KeyEvent.KEYCODE_PAGE_DOWN:    return 428;
            case KeyEvent.KEYCODE_MENU:         return 1000;
            default:                            return 0;
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int js = jsKeyCode(event.getKeyCode());
        if (js != 0) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) sendKey("keydown", js);
            else if (event.getAction() == KeyEvent.ACTION_UP) sendKey("keyup", js);
            return true;
        }
        // OK na pilocie (DPAD_CENTER) → Enter, żeby działało też na pilotach, które WebView mapuje inaczej
        if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_CENTER || event.getKeyCode() == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            KeyEvent enter = new KeyEvent(event.getDownTime(), event.getEventTime(), event.getAction(),
                    KeyEvent.KEYCODE_ENTER, event.getRepeatCount(), event.getMetaState());
            return super.dispatchKeyEvent(enter);
        }
        return super.dispatchKeyEvent(event);   // strzałki, Enter i cyfry trafiają do WebView normalnie
    }

    private void sendKey(String type, int code) {
        if (web == null) return;
        web.evaluateJavascript("window.tvKey && window.tvKey('" + type + "'," + code + ")", null);
    }

    // =====================================================================
    //  Pełny ekran i cykl życia
    // =====================================================================
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
    protected void onResume() {
        super.onResume();
        if (web != null) web.onResume();
        if (player != null && currentUrl != null) {
            player.prepare();               // po powrocie do aplikacji wznów transmisję na żywo
            player.seekToDefaultPosition();
            player.setPlayWhenReady(true);
        }
        hideSystemUi();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (web != null) web.onPause();
        if (player != null) player.setPlayWhenReady(false);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (player != null) player.stop();  // zwolnij dekoder, gdy aplikacja jest w tle
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (web != null) web.saveState(outState);
    }

    @Override
    protected void onDestroy() {
        main.removeCallbacksAndMessages(null);
        releasePlayer();
        if (web != null) { web.destroy(); web = null; }
        super.onDestroy();
    }
}
