package com.example.segnmea

import android.content.Context
import android.util.Log
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest

class ThingSpeakUploader(private val context: Context) {

    private var lastUploadTime = 0L
    private val MIN_INTERVAL = 15000L // 15 seconds

    fun upload(apiKey: String, trackPoint: TrackPoint) {
        if (apiKey.isEmpty()) return

        val now = System.currentTimeMillis()
        if (now - lastUploadTime < MIN_INTERVAL) {
            return
        }

        lastUploadTime = now

        val url = "https://api.thingspeak.com/update"

        val request = object : StringRequest(
            Request.Method.POST, url,
            { response -> Log.d("ThingSpeak", "Upload success: $response") },
            { error -> Log.e("ThingSpeak", "Upload error", error) }
        ) {
            override fun getParams(): MutableMap<String, String> {
                val params = HashMap<String, String>()
                params["api_key"] = apiKey
                params["field1"] = trackPoint.pitch
                params["field2"] = trackPoint.roll
                params["field3"] = trackPoint.lat.toString()
                params["field4"] = trackPoint.lon.toString()
                params["field5"] = trackPoint.speed
                params["field6"] = trackPoint.heading
                return params
            }
        }

        VolleySingleton.getInstance(context).addToRequestQueue(request)
    }
}
