package com.example.segnmea

import android.content.Context
import com.google.gson.Gson
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TrackManager(private val context: Context) {
    private val fileName = "daily_track.json"
    private val dateKey = "last_track_date"
    private val prefs = context.getSharedPreferences("TrackPrefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun savePoint(point: TrackPoint) {
        checkDateReset()
        val file = File(context.filesDir, fileName)
        // Use synchronized block if multiple threads access this (though UI thread is main user here)
        synchronized(this) {
            file.appendText(gson.toJson(point) + "\n")
        }
    }

    fun loadPoints(): List<TrackPoint> {
        checkDateReset()
        val file = File(context.filesDir, fileName)
        if (!file.exists()) return emptyList()

        val points = mutableListOf<TrackPoint>()
        synchronized(this) {
            file.forEachLine { line ->
                try {
                    if (line.isNotBlank()) {
                        points.add(gson.fromJson(line, TrackPoint::class.java))
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        return points
    }

    private fun checkDateReset() {
        val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val lastDate = prefs.getString(dateKey, "")

        if (currentDate != lastDate) {
            // Reset
            val file = File(context.filesDir, fileName)
            if (file.exists()) {
                file.delete()
            }
            prefs.edit().putString(dateKey, currentDate).apply()
        }
    }
}
