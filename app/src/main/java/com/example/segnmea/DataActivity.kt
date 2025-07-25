package com.example.segnmea

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.example.segnmea.databinding.ActivityDataBinding
import org.json.JSONObject

class DataActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDataBinding
    private val handler = Handler(Looper.getMainLooper())
    private var channel = "3002133"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDataBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = getString(R.string.data)

        binding.mainButton.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
        binding.compassButton.setOnClickListener {
            startActivity(Intent(this, CompassActivity::class.java))
        }
        binding.clinometerButton.setOnClickListener {
            startActivity(Intent(this, ClinometerActivity::class.java))
        }

        fetchData()
    }

    private fun fetchData() {
        val sharedPreferences = getSharedPreferences("Settings", Context.MODE_PRIVATE)
        channel = sharedPreferences.getString("channel", "3002133") ?: "3002133"
        val queue = Volley.newRequestQueue(this)
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
                    val lat = lastFeed.getString("field3")
                    val lon = lastFeed.getString("field4")
                    val speed = lastFeed.getString("field5")
                    val heading = lastFeed.getString("field6")
                    val pitch = lastFeed.getString("field1")
                    val roll = lastFeed.getString("field2")

                    binding.latTextView.text = "Lat: $lat"
                    binding.lonTextView.text = "Lon: $lon"
                    binding.speedTextView.text = "Speed: $speed Kn"
                    binding.headingTextView.text = "Heading: $heading°"
                    binding.pitchTextView.text = "Pitch: $pitch°"
                    binding.rollTextView.text = "Roll: $roll°"
                }
            },
            { })

        queue.add(stringRequest)

        handler.postDelayed({ fetchData() }, 15000)
    }
}
