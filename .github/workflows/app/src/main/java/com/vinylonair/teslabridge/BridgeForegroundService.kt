package com.vinylonair.teslabridge

import android.app.*
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject

class BridgeForegroundService : Service() {

    private lateinit var webView: WebView
    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false
    private var roomId = "TESLA-DAVIDE"

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return
            readTidalAndSend()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        roomId = intent?.getStringExtra("ROOM_ID") ?: "TESLA-DAVIDE"
        startForegroundNotification()
        setupHiddenWebView()
        isRunning = true
        handler.post(pollRunnable)
        return START_STICKY
    }

    private fun startForegroundNotification() {
        val channelId = "voa_bridge_channel"
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(channelId, "VOA Tesla Bridge", NotificationManager.IMPORTANCE_LOW)
        mgr.createNotificationChannel(channel)

        val notif = Notification.Builder(this, channelId)
            .setContentTitle("VinylOnAir Tesla Bridge")
            .setContentText("Sincronizzazione Tidal attiva ($roomId)")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()

        startForeground(101, notif)
    }

    private fun setupHiddenWebView() {
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = WebViewClient()
        }

        val targetId = "VOA-" + roomId.uppercase().replace(Regex("[^A-Z0-9-]"), "")
        val html = """
            <!DOCTYPE html>
            <html>
            <head><script src="https://unpkg.com/peerjs@1.5.4/dist/peerjs.min.js"></script></head>
            <body>
            <script>
              const peer = new Peer();
              let conn = null;
              function connectToTesla() {
                conn = peer.connect('$targetId', { reliable: true });
                conn.on('close', () => setTimeout(connectToTesla, 2000));
                conn.on('error', () => setTimeout(connectToTesla, 2000));
              }
              peer.on('open', connectToTesla);
              setInterval(() => {
                if (!conn || !conn.open) connectToTesla();
              }, 4000);

              window.sendTrack = function(jsonStr) {
                if (conn && conn.open) {
                  conn.send(JSON.parse(jsonStr));
                }
              };
            </script>
            </body>
            </html>
        """.trimIndent()

        webView.loadDataWithBaseURL("https://vinylonair.com", html, "text/html", "UTF-8", null)
    }

    private fun readTidalAndSend() {
        try {
            val msm = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val listener = ComponentName(this, TidalListenerService::class.java)
            val controllers: List<MediaController> = msm.getActiveSessions(listener)

            val target = controllers.firstOrNull { it.packageName.contains("tidal", ignoreCase = true) }
                ?: controllers.firstOrNull()
                ?: return

            val meta = target.metadata ?: return
            val state = target.playbackState ?: return

            val title = meta.getString(MediaMetadata.METADATA_KEY_TITLE) ?: return
            val artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: ""
            val durationSec = (meta.getLong(MediaMetadata.METADATA_KEY_DURATION) / 1000.0)
            val posSec = (state.position / 1000.0)
            val isPlaying = (state.state == PlaybackState.STATE_PLAYING)

            val payload = JSONObject().apply {
                put("artist", artist)
                put("title", title)
                put("position", posSec)
                put("duration", if (durationSec > 0) durationSec else 210.0)
                put("isPlaying", isPlaying)
            }

            val escaped = payload.toString().replace("\\", "\\\\").replace("'", "\\'")
            webView.evaluateJavascript("if(window.sendTrack) window.sendTrack('$escaped');", null)
        } catch (e: SecurityException) {
        }
    }

    override fun onDestroy() {
        isRunning = false
        handler.removeCallbacks(pollRunnable)
        if (::webView.isInitialized) webView.destroy()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
