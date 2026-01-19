package com.example.segnmea

import android.content.Context
import android.content.SharedPreferences
import com.google.android.gms.maps.model.LatLng
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TrackManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("DailyTrack", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private var currentTrack: MutableList<LatLng> = mutableListOf()
    private val executor = java.util.concurrent.Executors.newSingleThreadExecutor()

    init {
        loadTrack()
    }

    private var pendingChanges = 0
    private val SAVE_THRESHOLD = 50 // Save after 50 points

    fun addPoint(lat: Double, lon: Double) {
        val today = dateFormat.format(Date())
        val lastDate = prefs.getString("last_date", "")

        if (today != lastDate) {
            // New day, clear track
            currentTrack.clear()
            prefs.edit().putString("last_date", today).apply()
            pendingChanges = 0
        }

        val point = LatLng(lat, lon)
        // Simple filter to avoid duplicates or zero-zero
        if (lat != 0.0 && lon != 0.0) {
             if (currentTrack.isEmpty() || currentTrack.last() != point) {
                 currentTrack.add(point)
                 pendingChanges++

                 if (pendingChanges >= SAVE_THRESHOLD) {
                     saveTrack()
                     pendingChanges = 0
                 }
             }
        }
    }

    fun saveNow() {
        saveTrack()
    }

    fun getTrack(): List<LatLng> {
        return currentTrack
    }

    private fun saveTrack() {
        // Clone the list to avoid concurrent modification exception if addPoint is called during save
        val trackToSave = ArrayList(currentTrack)
        executor.execute {
            val json = gson.toJson(trackToSave)
            prefs.edit().putString("track_points", json).apply()
        }
    }

    private fun loadTrack() {
        val today = dateFormat.format(Date())
        val lastDate = prefs.getString("last_date", "")

        if (today != lastDate) {
            currentTrack = mutableListOf()
            prefs.edit().putString("last_date", today).remove("track_points").apply()
        } else {
            val json = prefs.getString("track_points", null)
            if (json != null) {
                val type = object : TypeToken<MutableList<LatLng>>() {}.type
                currentTrack = gson.fromJson(json, type)
            }
        }
    }

    fun clear() {
        currentTrack.clear()
        saveTrack()
    }
}
