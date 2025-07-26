package com.example.segnmea

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.example.segnmea.databinding.ActivityDataBinding
import org.json.JSONObject

/**
 * Activity that displays the boat's data.
 */
class DataActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDataBinding
    private val handler = Handler(Looper.getMainLooper())
    private var channel = "3002133"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDataBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = getString(R.string.data)

        // Set up the button click listeners
        binding.mainButton.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
        binding.compassButton.setOnClickListener {
            startActivity(Intent(this, CompassActivity::class.java))
        }
        binding.clinometerButton.setOnClickListener {
            startActivity(Intent(this, ClinometerActivity::class.java))
        }

        // Fetch the initial data
        fetchData()
    }

    /**
     * Fetches the boat's data from the ThingSpeak API.
     */
    private fun fetchData() {
        val sharedPreferences = getSharedPreferences("Settings", Context.MODE_PRIVATE)
        channel = sharedPreferences.getString("channel", "3002133") ?: "3002133"
        val url = "https://api.thingspeak.com/channels/$channel/feeds.json?results=1"

        val stringRequest = StringRequest(
            Request.Method.GET, url,
            { response ->
                try {
                    val jsonObject = JSONObject(response)
                    val channelObject = jsonObject.getJSONObject("channel")
                    val channelName = channelObject.optString("name", "Unknown Channel")
                    binding.channelNameTextView.text = channelName

                    val feeds = jsonObject.optJSONArray("feeds")
                    if (feeds != null && feeds.length() > 0) {
                        val lastFeed = feeds.getJSONObject(0)

                        val pitch = lastFeed.optString("field1", "0").toFloatOrNull() ?: 0f
                        val roll = lastFeed.optString("field2", "0").toFloatOrNull() ?: 0f
                        val lat = lastFeed.optString("field3", "0").toDoubleOrNull() ?: 0.0
                        val lon = lastFeed.optString("field4", "0").toDoubleOrNull() ?: 0.0
                        val speed = lastFeed.optString("field5", "0").toFloatOrNull() ?: 0f
                        val heading = lastFeed.optString("field6", "0").toFloatOrNull() ?: 0f

                        binding.pitchTextView.text = "%.1f°".format(pitch)
                        binding.rollTextView.text = "%.1f°".format(roll)
                        binding.latTextView.text = formatLat(lat)
                        binding.lonTextView.text = formatLon(lon)
                        binding.speedTextView.text = "%.1f knots".format(speed)
                        binding.headingTextView.text = "${heading.toInt()}°"

                        val createdAt = lastFeed.optString("created_at", null)
                        if (createdAt != null) {
                            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                            sdf.timeZone = TimeZone.getTimeZone("UTC")
                            val date = sdf.parse(createdAt)
                            if (date != null) {
                                val outFormat = SimpleDateFormat("HH:mm   dd-MM-yyyy", Locale.US)
                                outFormat.timeZone = TimeZone.getDefault()
                                binding.realTimeDataTextView.text =
                                    "Real-Time Data: ${outFormat.format(date)}"
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            },
            { error ->
                error.printStackTrace()
            }
        )

        VolleySingleton.getInstance(this).addToRequestQueue(stringRequest)
        handler.postDelayed({ fetchData() }, 15000)
    }

    /**
     * Formats a latitude value to a string with degrees and minutes.
     */
    private fun formatLat(lat: Double): String {
        val hemi = if (lat >= 0) "N" else "S"
        val absLat = kotlin.math.abs(lat)
        val grados = absLat.toInt()
        val minutos = (absLat - grados) * 60
        return String.format(Locale.US, "%02d° %.3f' %s", grados, minutos, hemi)
    }

    /**
     * Formats a longitude value to a string with degrees and minutes.
     */
    private fun formatLon(lon: Double): String {
        val hemi = if (lon >= 0) "E" else "W"
        val absLon = kotlin.math.abs(lon)
        val grados = absLon.toInt()
        val minutos = (absLon - grados) * 60
        return String.format(Locale.US, "%03d° %.3f' %s", grados, minutos, hemi)
    }
}

