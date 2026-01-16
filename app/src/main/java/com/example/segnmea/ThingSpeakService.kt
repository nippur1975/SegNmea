package com.example.segnmea

import android.util.Log
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class ThingSpeakService {

    fun updateChannel(apiKey: String, lat: Double?, lon: Double?, speed: Float?, heading: Float?, pitch: Float?, roll: Float?) {
        if (apiKey.isBlank()) return

        Thread {
            try {
                val urlObj = URL("https://api.thingspeak.com/update")
                val conn = urlObj.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.connectTimeout = 5000
                conn.readTimeout = 5000

                val params = StringBuilder()
                params.append("api_key=").append(URLEncoder.encode(apiKey, "UTF-8"))

                if (pitch != null) params.append("&field1=").append(pitch)
                if (roll != null) params.append("&field2=").append(roll)
                if (lat != null) params.append("&field3=").append(lat)
                if (lon != null) params.append("&field4=").append(lon)
                if (speed != null) params.append("&field5=").append(speed)
                if (heading != null) params.append("&field6=").append(heading)

                val writer = OutputStreamWriter(conn.outputStream)
                writer.write(params.toString())
                writer.flush()

                val responseCode = conn.responseCode
                // Log.d("ThingSpeak", "Update response: $responseCode")

                writer.close()
                conn.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }
}
