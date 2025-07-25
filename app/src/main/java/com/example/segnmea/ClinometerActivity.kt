package com.example.segnmea

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import kotlinx.android.synthetic.main.activity_clinometer.*
import org.json.JSONObject
import java.util.*

class ClinometerActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var channel = "3002133"
    private var pitchAlarm = 30f
    private var rollAlarm = 30f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_clinometer)
        title = getString(R.string.clinometer)

        mainButton.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
        compassButton.setOnClickListener {
            startActivity(Intent(this, CompassActivity::class.java))
        }
        dataButton.setOnClickListener {
            startActivity(Intent(this, DataActivity::class.java))
        }

        fetchData()
    }

    private fun fetchData() {
        val sharedPreferences = getSharedPreferences("Settings", Context.MODE_PRIVATE)
        channel = sharedPreferences.getString("channel", "3002133") ?: "3002133"
        pitchAlarm = sharedPreferences.getInt("pitchAlarm", 30).toFloat()
        rollAlarm = sharedPreferences.getInt("rollAlarm", 30).toFloat()
        val queue = Volley.newRequestQueue(this)
        val url = "https://api.thingspeak.com/channels/$channel/feeds.json?results=1"

        val stringRequest = StringRequest(
            Request.Method.GET, url,
            { response ->
                val jsonObject = JSONObject(response)
                val channelObject = jsonObject.getJSONObject("channel")
                val channelName = channelObject.getString("name")
                channelNameTextView.text = channelName

                val feeds = jsonObject.getJSONArray("feeds")
                if (feeds.length() > 0) {
                    val lastFeed = feeds.getJSONObject(0)
                    val pitch = lastFeed.getString("field1").toFloat()
                    val roll = lastFeed.getString("field2").toFloat()

                    pitchValueTextView.text = "$pitch°"
                    rollValueTextView.text = "$roll°"

                    pitchImageView.rotation = pitch
                    rollImageView.rotation = roll

                    checkAlarms(pitch, roll)
                }
            },
            { })

        queue.add(stringRequest)

        handler.postDelayed({ fetchData() }, 15000)
    }

    private fun checkAlarms(pitch: Float, roll: Float) {
        val language = Locale.getDefault().language
        if (pitch > pitchAlarm) {
            val sound = if (language == "es") R.raw.alarma_encabuzado else R.raw.head_alarm
            MediaPlayer.create(this, sound).start()
        } else if (pitch < -pitchAlarm) {
            val sound = if (language == "es") R.raw.alarma_sentado else R.raw.stern_alarm
            MediaPlayer.create(this, sound).start()
        }

        if (roll > rollAlarm) {
            val sound = if (language == "es") R.raw.alarma_estribor else R.raw.starboard_alarm
            MediaPlayer.create(this, sound).start()
        } else if (roll < -rollAlarm) {
            val sound = if (language == "es") R.raw.alarma_babor else R.raw.port_alarm
            MediaPlayer.create(this, sound).start()
        }
    }
}