package com.example.segnmea

import android.content.Context
import android.util.Log
import com.google.android.gms.maps.model.LatLng
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class TrackManager(private val context: Context) {

    private val FILENAME = "daily_track.json"
    private var currentTrack: MutableList<TrackPoint> = Collections.synchronizedList(mutableListOf())
    private var currentDate: String = ""
    private val ioExecutor = Executors.newSingleThreadExecutor()

    init {
        loadTrack()
    }

    fun addPoint(point: TrackPoint) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        synchronized(currentTrack) {
            if (currentDate != today) {
                // New day, reset
                currentTrack.clear()
                currentDate = today
                // Trigger save to clear file
                saveTrackAsync()
            }
            currentTrack.add(point)
        }

        saveTrackAsync()
    }

    fun getTrack(): List<TrackPoint> {
        synchronized(currentTrack) {
            return ArrayList(currentTrack)
        }
    }

    fun getPolylinePoints(): List<LatLng> {
        synchronized(currentTrack) {
            return currentTrack.map { it.getPosition() }
        }
    }

    private fun saveTrackAsync() {
        ioExecutor.execute {
            saveTrack()
        }
    }

    private fun saveTrack() {
        try {
            // Create snapshot for saving
            val trackCopy = getTrack()

            val root = JSONObject()
            root.put("date", currentDate)
            val array = JSONArray()
            trackCopy.forEach { pt ->
                val obj = JSONObject()
                obj.put("lat", pt.lat)
                obj.put("lon", pt.lon)
                obj.put("pitch", pt.pitch)
                obj.put("roll", pt.roll)
                obj.put("speed", pt.speed)
                obj.put("heading", pt.heading)
                obj.put("createdAt", pt.createdAt)
                array.put(obj)
            }
            root.put("points", array)

            val file = File(context.filesDir, FILENAME)
            file.writeText(root.toString())
        } catch (e: Exception) {
            Log.e("TrackManager", "Error saving track", e)
        }
    }

    private fun loadTrack() {
        try {
            val file = File(context.filesDir, FILENAME)
            if (!file.exists()) {
                currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                return
            }

            val jsonStr = file.readText()
            val root = JSONObject(jsonStr)
            val savedDate = root.optString("date", "")

            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

            if (savedDate != today) {
                currentDate = today
                return
            }

            currentDate = savedDate
            val array = root.optJSONArray("points")
            if (array != null) {
                synchronized(currentTrack) {
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        val pt = TrackPoint(
                            lat = obj.optDouble("lat"),
                            lon = obj.optDouble("lon"),
                            pitch = obj.optString("pitch"),
                            roll = obj.optString("roll"),
                            speed = obj.optString("speed"),
                            heading = obj.optString("heading"),
                            createdAt = obj.optString("createdAt")
                        )
                        currentTrack.add(pt)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("TrackManager", "Error loading track", e)
            currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        }
    }
}
