package com.vinylonair.teslabridge

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.*

class MainActivity : Activity() {

    private var active = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("voa_prefs", MODE_PRIVATE)
        val savedRoom = prefs.getString("room", "TESLA-DAVIDE") ?: "TESLA-DAVIDE"

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
            setPadding(64, 64, 64, 64)
        }

        val title = TextView(this).apply {
            text = "VinylOnAir • Tesla Bridge"
            textSize = 22f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        val input = EditText(this).apply {
            setText(savedRoom)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            gravity = Gravity.CENTER
            textSize = 18f
        }

        val btnPerm = Button(this).apply {
            text = "1. Abilita Lettura Tidal (Permesso)"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        }

        val btnToggle = Button(this).apply {
            text = "2. AVVIA SYNC ORA"
            textSize = 16f
            setOnClickListener {
                val room = input.text.toString().trim().uppercase().ifEmpty { "TESLA-DAVIDE" }
                prefs.edit().putString("room", room).apply()

                val svc = Intent(this@MainActivity, BridgeForegroundService::class.java)
                if (!active) {
                    svc.putExtra("ROOM_ID", room)
                    startForegroundService(svc)
                    active = true
                    text = "SYNC ATTIVO (Tocca per fermare)"
                } else {
                    stopService(svc)
                    active = false
                    text = "2. AVVIA SYNC ORA"
                }
            }
        }

        root.addView(title)
        root.addView(input, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 40 })
        root.addView(btnPerm, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 40 })
        root.addView(btnToggle, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 24 })

        setContentView(root)
    }
}
