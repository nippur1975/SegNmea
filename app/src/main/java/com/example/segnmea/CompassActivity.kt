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
import kotlinx.android.synthetic.main.activity_compass.*
import org.json.JSONObject

class CompassActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var channel = "3002133"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_compass)
        title = getString(R.string.compass)

        mainButton.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
        clinometerButton.setOnClickListener {
            startActivity(Intent(this, ClinometerActivity::class.java))
        }
        dataButton.setOnClickListener {
            startActivity(Intent(this, DataActivity::class.java))
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
                channelNameTextView.text = channelName

                val feeds = jsonObject.getJSONArray("feeds")
                if (feeds.length() > 0) {
                    val lastFeed = feeds.getJSONObject(0)
                    val heading = lastFeed.getString("field6").toFloat()
                    headingValueTextView.text = heading.toInt().toString()
                    compassRose.rotation = -heading
                }
            },
            { })

        queue.add(stringRequest)

        handler.postDelayed({ fetchData() }, 15000)
    }
}