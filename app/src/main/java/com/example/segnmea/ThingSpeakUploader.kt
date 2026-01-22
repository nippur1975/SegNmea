package com.example.segnmea

import android.content.Context
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest

class ThingSpeakUploader(private val context: Context) {

    private var lastUploadTime = 0L
    private val MIN_INTERVAL = 15000L // 15 seconds

    fun uploadData(apiKey: String, lat: Double, lon: Double, speed: Double, heading: Double, pitch: Double, roll: Double) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastUploadTime < MIN_INTERVAL) {
            return
        }

        if (apiKey.isEmpty()) return

        // Field mapping:
        // field1: Pitch
        // field2: Roll
        // field3: Lat
        // field4: Lon
        // field5: Speed
        // field6: Heading

        val url = "https://api.thingspeak.com/update?api_key=$apiKey" +
                "&field1=$pitch" +
                "&field2=$roll" +
                "&field3=$lat" +
                "&field4=$lon" +
                "&field5=$speed" +
                "&field6=$heading"

        val request = StringRequest(Request.Method.GET, url,
            { response ->
                // Success
                lastUploadTime = System.currentTimeMillis()
            },
            { error ->
                // Error
                error.printStackTrace()
            }
        )

        VolleySingleton.getInstance(context).addToRequestQueue(request)
    }
}
