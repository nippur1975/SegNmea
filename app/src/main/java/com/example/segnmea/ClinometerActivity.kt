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
import com.example.segnmea.databinding.ActivityClinometerBinding
import org.json.JSONObject
import java.util.*

/**
 * Activity that displays the clinometer.
 */
class ClinometerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityClinometerBinding
    private val handler = Handler(Looper.getMainLooper())
    private var channel = "3002133"
    private var rollAlarm = 30f  // Solo alarma de roll

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityClinometerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = getString(R.string.clinometer)

        // Set up the button click listeners
        binding.mainButton.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
        binding.compassButton.setOnClickListener {
            startActivity(Intent(this, CompassActivity::class.java))
        }
        binding.dataButton.setOnClickListener {
            startActivity(Intent(this, DataActivity::class.java))
        }

        // Fetch the initial data
        fetchData()
    }

    /**
     * Fetches the clinometer data from the ThingSpeak API.
     */
    private fun fetchData() {
        val sharedPreferences = getSharedPreferences("Settings", Context.MODE_PRIVATE)
        channel = sharedPreferences.getString("channel", "3002133") ?: "3002133"
        rollAlarm = sharedPreferences.getInt("rollAlarm", 30).toFloat()
        val url = "https://api.thingspeak.com/channels/$channel/feeds.json?results=1"

        val stringRequest = StringRequest(
            Request.Method.GET, url,
            { response ->
                val jsonObject = JSONObject(response)
                val channelObject = jsonObject.getJSONObject("channel")
                val channelName = channelObject.getString("name")
                binding.channelNameTextView.text = channelName

                val feeds = jsonObject.getJSONArray("feeds")
                if (feeds.length() > 0) {
                    val lastFeed = feeds.getJSONObject(0)
                    val pitch = lastFeed.optString("field1", "0").toFloat()
                    val roll = lastFeed.optString("field2", "0").toFloat()

                    // Update the views
                    (binding.pitchImageView as? PitchView)?.pitch = pitch
                    (binding.rollImageView as? RollView)?.roll = roll

                    checkAlarms(roll)
                }
            },
            { })

        VolleySingleton.getInstance(this).addToRequestQueue(stringRequest)
        handler.postDelayed({ fetchData() }, 15000)
    }

    /**
     * Checks if the roll value exceeds the alarm threshold and plays an alarm sound if it does.
     */
    private fun checkAlarms(roll: Float) {
        val language = Locale.getDefault().language

        if (roll > rollAlarm) {
            val sound = if (language == "es") R.raw.alarma_estribor else R.raw.starboard_alarm
            MediaPlayer.create(this, sound).start()
        } else if (roll < -rollAlarm) {
            val sound = if (language == "es") R.raw.alarma_babor else R.raw.port_alarm
            MediaPlayer.create(this, sound).start()
        }
    }
}
