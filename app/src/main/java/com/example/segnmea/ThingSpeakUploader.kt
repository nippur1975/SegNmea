package com.example.segnmea

import android.content.Context
import android.util.Log
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest

class ThingSpeakUploader(private val context: Context) {

    // Default or stored API Key. For now, using a placeholder or one from settings.
    // The user needs a WRITE API KEY. The existing app used READ (public?) channel IDs.
    // I will add a method to set the key.
    private var writeApiKey: String = ""

    fun setWriteApiKey(key: String) {
        this.writeApiKey = key
    }

    fun upload(data: NmeaParser.ParsedData) {
        if (writeApiKey.isEmpty()) return

        // https://api.thingspeak.com/update?api_key=YOUR_CHANNEL_API_KEY&field1=0
        val url = StringBuilder("https://api.thingspeak.com/update?api_key=$writeApiKey")

        // Mapping fields based on previous usage:
        // field1: Pitch (from existing DataActivity reading)
        // field2: Roll
        // field3: Lat
        // field4: Lon
        // field5: Speed
        // field6: Heading

        url.append("&field1=${data.pitch}")
        url.append("&field2=${data.roll}")
        url.append("&field3=${data.lat}")
        url.append("&field4=${data.lon}")
        url.append("&field5=${data.speed}")
        url.append("&field6=${data.heading}")

        val stringRequest = StringRequest(
            Request.Method.POST, url.toString(),
            { response ->
                Log.d("ThingSpeak", "Upload success: $response")
            },
            { error ->
                Log.e("ThingSpeak", "Upload failed", error)
            }
        )

        VolleySingleton.getInstance(context).addToRequestQueue(stringRequest)
    }
}
