package com.example.segnmea

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TrackManager(private val context: Context) {

    private val fileName = "daily_track.json"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun savePoint(point: TrackPoint) {
        val today = dateFormat.format(Date())
        val file = File(context.filesDir, fileName)

        var jsonArray = JSONArray()
        var lastDate = ""

        if (file.exists()) {
            try {
                val content = file.readText()
                val jsonObject = JSONObject(content)
                lastDate = jsonObject.optString("date", "")

                if (lastDate == today) {
                    jsonArray = jsonObject.getJSONArray("points")
                } else {
                    // New day, start fresh (implicit reset)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Add new point
        val pointJson = JSONObject()
        pointJson.put("lat", point.lat)
        pointJson.put("lon", point.lon)
        pointJson.put("pitch", point.pitch)
        pointJson.put("roll", point.roll)
        pointJson.put("speed", point.speed)
        pointJson.put("heading", point.heading)
        pointJson.put("created_at", point.createdAt)

        jsonArray.put(pointJson)

        // Save back to file
        val rootObject = JSONObject()
        rootObject.put("date", today)
        rootObject.put("points", jsonArray)

        try {
            file.writeText(rootObject.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getDailyTrack(): List<TrackPoint> {
        val list = mutableListOf<TrackPoint>()
        val file = File(context.filesDir, fileName)
        val today = dateFormat.format(Date())

        if (file.exists()) {
            try {
                val content = file.readText()
                val jsonObject = JSONObject(content)
                val lastDate = jsonObject.optString("date", "")

                if (lastDate == today) {
                    val points = jsonObject.getJSONArray("points")
                    for (i in 0 until points.length()) {
                        val p = points.getJSONObject(i)
                        list.add(TrackPoint(
                            p.getDouble("lat"),
                            p.getDouble("lon"),
                            p.optString("pitch", "0"),
                            p.optString("roll", "0"),
                            p.optString("speed", "0"),
                            p.optString("heading", "0"),
                            p.optString("created_at", "")
                        ))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return list
    }
}
