package com.example.segnmea

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.example.segnmea.databinding.ActivityMainBinding
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import org.json.JSONObject

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityMainBinding
    private lateinit var map: GoogleMap
    private var boatMarker: Marker? = null
    private var historicalMarkers = mutableListOf<Marker>()
    private lateinit var trackPolyline: Polyline
    private var rulerPolyline: Polyline? = null
    private var rulerMarkers = mutableListOf<Marker>()
    private var rulerPoints = mutableListOf<LatLng>()
    private var handler = Handler(Looper.getMainLooper())
    private val channels = listOf("3002133", "3007462", "3017966", "3017982")
    private val channelColors = listOf(
        0xFF800080.toInt(), // Purple
        0xFF0000FF.toInt(), // Blue
        0xFFFF0000.toInt(), // Red
        0xFFFFFF00.toInt()  // Yellow
    )
    private var boatMarkers = mutableMapOf<String, Marker>()
    private var historicalData = mutableMapOf<String, MutableList<TrackPoint>>()
    private var trackPolylines = mutableMapOf<String, Polyline>()
    private var markerToTrackPointMap = mutableMapOf<Marker, TrackPoint>()
    private var currentChannel = "3002133"
    private var channelName = "Vessel"
    private val refreshInterval = 15000L // 15 segundos

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = getString(R.string.app_name)

        val sharedPreferences = getSharedPreferences("Settings", Context.MODE_PRIVATE)
        currentChannel = sharedPreferences.getString("current_channel", "3002133") ?: "3002133"
        channelName = sharedPreferences.getString("current_channel_name", "Vessel") ?: "Vessel"

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Botones de navegación (inferiores)
        binding.compassButton.setOnClickListener {
            startActivity(Intent(this, CompassActivity::class.java))
        }
        binding.clinometerButton.setOnClickListener {
            startActivity(Intent(this, ClinometerActivity::class.java))
        }
        binding.dataButton.setOnClickListener {
            startActivity(Intent(this, DataActivity::class.java))
        }

        binding.trackSwitch.setOnCheckedChangeListener { _, isChecked ->
            markerToTrackPointMap.keys.forEach { it.isVisible = isChecked }
        }

        binding.rulerSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                map.setOnMapClickListener { latLng ->
                    addRulerPoint(latLng)
                }
            } else {
                map.setOnMapClickListener(null)
                clearRuler()
            }
        }

        startRepeatingTask()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        map.uiSettings.isZoomControlsEnabled = true

        channels.forEachIndexed { index, channel ->
            val polyline = map.addPolyline(
                PolylineOptions()
                    .width(5f)
                    .color(channelColors[index])
            )
            trackPolylines[channel] = polyline
        }

        binding.trackSwitch.isChecked = false
        trackPolylines.values.forEach { it.isVisible = true }
        historicalMarkers.forEach { it.isVisible = false }

        map.setOnMarkerClickListener { marker ->
            val trackPoint = markerToTrackPointMap[marker]
            if (trackPoint != null) {
                val latFormatted = formatCoordinate(trackPoint.lat.toString(), "N", "S")
                val lonFormatted = formatCoordinate(trackPoint.lon.toString(), "E", "W")
                val speedAbs = trackPoint.speed.toDoubleOrNull()?.let { Math.abs(it).toInt() }?.toString() ?: trackPoint.speed
                val headingAbs = trackPoint.heading.toDoubleOrNull()?.let { Math.abs(it).toInt() }?.toString() ?: trackPoint.heading

                val message = "Fecha: ${trackPoint.createdAt}\n" +
                              "Lat: $latFormatted\n" +
                              "Lon: $lonFormatted\n" +
                              "Speed: $speedAbs kn\n" +
                              "Heading: $headingAbs°\n" +
                              "Pitch: ${trackPoint.pitch}°\n" +
                              "Roll: ${trackPoint.roll}°"

                AlertDialog.Builder(this)
                    .setTitle("Datos del Punto Histórico")
                    .setMessage(message)
                    .setPositiveButton("Aceptar", null)
                    .show()
            }
            true
        }

        map.setPadding(0, 0, 0, binding.buttonContainer.height)

        map.setOnCameraIdleListener {
            val zoom = map.cameraPosition.zoom
            val scale = if (zoom >= map.maxZoomLevel) 0.5f else (zoom / 15f).coerceAtLeast(0.5f).coerceAtMost(2f)
            boatMarkers.values.forEach { marker ->
                val originalBitmap = marker.tag as? Bitmap
                if (originalBitmap != null) {
                    val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, (originalBitmap.width * scale).toInt(), (originalBitmap.height * scale).toInt(), false)
                    marker.setIcon(BitmapDescriptorFactory.fromBitmap(scaledBitmap))
                }
            }
        }
    }

    private val updateTask = object : Runnable {
        override fun run() {
            channels.forEach { channel ->
                fetchChannelData(channel)
            }
            handler.postDelayed(this, refreshInterval)
        }
    }

    private fun startRepeatingTask() {
        handler.post(updateTask)
    }

    private fun stopRepeatingTask() {
        handler.removeCallbacks(updateTask)
    }

    private fun fetchChannelData(channelId: String) {
        executor.execute {
            val response = fetchDataFromApi(channelId)
            if (response != null) {
                runOnUiThread {
                    processResponse(channelId, response)
                }
            }
        }
    }

    private fun fetchDataFromApi(channelId: String): String? {
        val url = "https://api.thingspeak.com/channels/$channelId/feeds.json?results=2000"
        try {
            return java.net.URL(url).readText()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun processResponse(channelId: String, response: String) {
        try {
            val jsonObject = JSONObject(response)
            val channelObject = jsonObject.getJSONObject("channel")
            val channelName = channelObject.getString("name")
            val feeds = jsonObject.getJSONArray("feeds")

            val points = mutableListOf<LatLng>()
            val historicalDataForChannel = historicalData[channelId] ?: mutableListOf()
            historicalDataForChannel.clear()
            markerToTrackPointMap.keys.filter { markerToTrackPointMap[it]?.let { channels.indexOf(channelId) } != -1 }.forEach {
                it.remove()
                markerToTrackPointMap.remove(it)
            }

            for (i in 0 until feeds.length()) {
                val feed = feeds.getJSONObject(i)
                val lat = feed.optString("field3", "0")
                val lon = feed.optString("field4", "0")
                val pitch = feed.optString("field1", "0")
                val roll = feed.optString("field2", "0")
                val speed = feed.optString("field5", "0")
                val heading = feed.optString("field6", "0")
                val createdAt = feed.optString("created_at", "")
                val latitude = lat.toDoubleOrNull() ?: 0.0
                val longitude = lon.toDoubleOrNull() ?: 0.0

                if (latitude != 0.0 && longitude != 0.0) {
                    val trackPoint = TrackPoint(latitude, longitude, pitch, roll, speed, heading, createdAt)
                    val position = trackPoint.getPosition()
                    points.add(position)
                    historicalDataForChannel.add(trackPoint)

                    if (i < feeds.length() - 1) { // No agregar marcador para el último punto
                        val historicalMarker = map.addMarker(
                            MarkerOptions()
                                .position(position)
                                .icon(BitmapDescriptorFactory.fromBitmap(getBitmap(R.drawable.ic_historical_marker, 0xFF0000FF.toInt())!!)) // Azul
                                .anchor(0.5f, 0.5f)
                        )
                        if (historicalMarker != null) {
                            markerToTrackPointMap[historicalMarker] = trackPoint
                            historicalMarker.isVisible = binding.trackSwitch.isChecked
                        }
                    }
                }
            }

            trackPolylines[channelId]?.points = points
            historicalData[channelId] = historicalDataForChannel

            if (feeds.length() > 0) {
                val lastFeed = feeds.getJSONObject(feeds.length() - 1)
                val pitch = lastFeed.optString("field1", "0")
                val roll = lastFeed.optString("field2", "0")
                val lat = lastFeed.optString("field3", "0")
                val lon = lastFeed.optString("field4", "0")
                val speed = lastFeed.optString("field5", "0")
                val heading = lastFeed.optString("field6", "0")
                val latitude = lat.toDoubleOrNull() ?: 0.0
                val longitude = lon.toDoubleOrNull() ?: 0.0
                val position = LatLng(latitude, longitude)

                if (channelId == currentChannel) {
                    this@MainActivity.channelName = channelName
                    updateUI(lat, lon, speed, heading, pitch, roll)
                }

                val speedValue = speed.toDoubleOrNull()
                val iconResId: Int
                val iconColor: Int

                when {
                    speedValue != null && speedValue > 2 -> {
                        iconResId = R.drawable.ic_navigation
                        iconColor = 0xFFd9534f.toInt()
                    }
                    speedValue != null && speedValue <= 1.5 -> {
                        iconResId = R.drawable.ic_square_rotated
                        iconColor = 0xFFf0ad4e.toInt()
                    }
                    else -> {
                        iconResId = R.drawable.ic_navigation
                        iconColor = channelColors[channels.indexOf(channelId)]
                    }
                }

                val rotation = if (iconResId == R.drawable.ic_square_rotated) 90f else heading.toFloat()
                val boatMarker = boatMarkers[channelId]
                val bitmap = getBitmap(iconResId, iconColor)
                if (boatMarker == null) {
                    val newBoatMarker = map.addMarker(
                        MarkerOptions()
                            .position(position)
                            .icon(BitmapDescriptorFactory.fromBitmap(bitmap!!))
                            .rotation(rotation)
                            .anchor(0.5f, 0.5f)
                    )
                    if (newBoatMarker != null) {
                        newBoatMarker.tag = bitmap
                        boatMarkers[channelId] = newBoatMarker
                    }
                    if (channelId == currentChannel) {
                        map.moveCamera(CameraUpdateFactory.newLatLngZoom(position, 15f))
                    }
                } else {
                    boatMarker.position = position
                    boatMarker.rotation = rotation
                    boatMarker.setIcon(BitmapDescriptorFactory.fromBitmap(bitmap!!))
                    boatMarker.tag = bitmap
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun fetchLastData() {
        val url = "https://api.thingspeak.com/channels/$currentChannel/feeds/last.json"
        val queue = Volley.newRequestQueue(this)

        val stringRequest = StringRequest(
            Request.Method.GET, url,
            { response ->
                try {
                    val jsonObject = JSONObject(response)
                    val feeds = jsonObject

                    // Mapeo de campos (según tu especificación)
                    val pitch = feeds.optString("field1", "0")  // PITCH
                    val roll = feeds.optString("field2", "0")   // ROLL
                    val lat = feeds.optString("field3", "0")    // LAT
                    val lon = feeds.optString("field4", "0")    // LON
                    val speed = feeds.optString("field5", "0")  // VELOCIDAD
                    val heading = feeds.optString("field6", "0")// RUMBO

                    val latitude = lat.toDoubleOrNull() ?: 0.0
                    val longitude = lon.toDoubleOrNull() ?: 0.0
                    val position = LatLng(latitude, longitude)

                    updateUI(lat, lon, speed, heading, pitch, roll)

                    if (latitude != 0.0 && longitude != 0.0) {

                        if (boatMarker == null) {
                            boatMarker = map.addMarker(
                                MarkerOptions()
                                    .position(position)
                                    .icon(BitmapDescriptorFactory.fromBitmap(getBitmap(R.drawable.ic_boat_marker, 0xFF000000.toInt())!!)) // Negro
                                    .rotation(heading.toFloat())
                                    .anchor(0.5f, 0.5f)
                            )
                            map.moveCamera(CameraUpdateFactory.newLatLngZoom(position, 15f))
                        } else {
                            boatMarker?.position = position
                            boatMarker?.rotation = heading.toFloat()
                        }
                    }

                } catch (e: Exception) {
                    e.printStackTrace()
                }
            },
            { error -> error.printStackTrace() })

        queue.add(stringRequest)
    }

    private fun fetchHistoricalData() {
        val url = "https://api.thingspeak.com/channels/$currentChannel/feeds.json?results=2000"
        val queue = Volley.newRequestQueue(this)

        val stringRequest = StringRequest(
            Request.Method.GET, url,
            { response ->
                try {
                    val jsonObject = JSONObject(response)
                    val feeds = jsonObject.getJSONArray("feeds")

                    val points = mutableListOf<LatLng>()
                    historicalMarkers.forEach { it.remove() }
                    historicalMarkers.clear()

                    for (i in 0 until feeds.length()) {
                        val feed = feeds.getJSONObject(i)
                        val lat = feed.optString("field3", "0")
                        val lon = feed.optString("field4", "0")
                        val latitude = lat.toDoubleOrNull() ?: 0.0
                        val longitude = lon.toDoubleOrNull() ?: 0.0

                        if (latitude != 0.0 && longitude != 0.0) {
                            val position = LatLng(latitude, longitude)
                            points.add(position)

                            val historicalMarker = map.addMarker(
                                MarkerOptions()
                                    .position(position)
                                    .icon(BitmapDescriptorFactory.fromBitmap(getBitmap(R.drawable.ic_historical_marker, 0xFF808080.toInt())!!)) // Gris
                                    .anchor(0.5f, 0.5f)
                            )
                            if (historicalMarker != null) {
                                historicalMarker.isVisible = binding.trackSwitch.isChecked
                                historicalMarkers.add(historicalMarker)
                            }
                        }
                    }
                    trackPolyline.points = points

                    if (feeds.length() > 0) {
                        val lastFeed = feeds.getJSONObject(feeds.length() - 1)
                        val pitch = lastFeed.optString("field1", "0")
                        val roll = lastFeed.optString("field2", "0")
                        val lat = lastFeed.optString("field3", "0")
                        val lon = lastFeed.optString("field4", "0")
                        val speed = lastFeed.optString("field5", "0")
                        val heading = lastFeed.optString("field6", "0")
                        val latitude = lat.toDoubleOrNull() ?: 0.0
                        val longitude = lon.toDoubleOrNull() ?: 0.0
                        val position = LatLng(latitude, longitude)

                        updateUI(lat, lon, speed, heading, pitch, roll)

                        if (boatMarker == null) {
                            boatMarker = map.addMarker(
                                MarkerOptions()
                                    .position(position)
                                    .icon(BitmapDescriptorFactory.fromBitmap(getBitmap(R.drawable.ic_boat_marker, 0xFF000000.toInt())!!)) // Negro
                                    .rotation(heading.toFloat())
                                    .anchor(0.5f, 0.5f)
                            )
                            map.moveCamera(CameraUpdateFactory.newLatLngZoom(position, 15f))
                        } else {
                            boatMarker?.position = position
                            boatMarker?.rotation = heading.toFloat()
                        }
                    }

                } catch (e: Exception) {
                    e.printStackTrace()
                }
            },
            { error -> error.printStackTrace() })

        queue.add(stringRequest)
    }

    private fun updateUI(lat: String, lon: String, speed: String, heading: String, pitch: String, roll: String) {
        // Formateo de coordenadas
        val latFormatted = formatCoordinate(lat, "N", "S")
        val lonFormatted = formatCoordinate(lon, "E", "W")
        val headingAbs = heading.toDoubleOrNull()?.let { Math.abs(it) }?.toInt()?.toString() ?: heading
        val speedAbs = speed.toDoubleOrNull()?.let { Math.abs(it).toInt() }?.toString() ?: speed

        // Obtener la configuración de idioma
        val sharedPreferences = getSharedPreferences("Settings", Context.MODE_PRIVATE)
        val language = sharedPreferences.getString("language", "en") ?: "en"

        // Orden correcto en pantalla
        binding.channelNameTextView.text = channelName
        if (language == "es") {
            binding.latTextView.text = "${getString(R.string.lat_es)} : $latFormatted"
            binding.lonTextView.text = "${getString(R.string.lon_es)} : $lonFormatted"
            binding.speedTextView.text = "${getString(R.string.speed_es)} : $speedAbs kn"
            binding.headingTextView.text = "${getString(R.string.heading_es)} : $headingAbs°"
            binding.pitchTextView.text = "${getString(R.string.pitch_es)} : $pitch°"
            binding.rollTextView.text = "${getString(R.string.roll_es)} : $roll°"
        } else {
            binding.latTextView.text = "${getString(R.string.lat)} : $latFormatted"
            binding.lonTextView.text = "${getString(R.string.lon)} : $lonFormatted"
            binding.speedTextView.text = "${getString(R.string.speed)} : $speedAbs kn"
            binding.headingTextView.text = "${getString(R.string.heading)} : $headingAbs°"
            binding.pitchTextView.text = "${getString(R.string.pitch)} : $pitch°"
            binding.rollTextView.text = "${getString(R.string.roll)} : $roll°"
        }
    }

    private fun formatCoordinate(coordinate: String, positiveDirection: String, negativeDirection: String): String {
        return try {
            val value = coordinate.toDouble()
            val degrees = Math.abs(value.toInt())
            val minutes = Math.abs(value - value.toInt()) * 60
            val direction = if (value >= 0) positiveDirection else negativeDirection
            "$degrees° ${"%.3f".format(minutes)}' $direction"
        } catch (e: NumberFormatException) {
            coordinate // Devuelve el original si no es un número
        }
    }

    private fun getBitmap(resId: Int, color: Int): Bitmap? {
        val drawable = ContextCompat.getDrawable(this, resId) ?: return null
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth * 2 else 100
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight * 2 else 100
        drawable.setBounds(0, 0, width, height)
        drawable.setTint(color)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.draw(canvas)
        return bitmap
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu) // menú superior (canal)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_alarm_settings -> {
                startActivity(Intent(this, AlarmActivity::class.java))
                true
            }
            R.id.action_channel_settings -> {
                startActivity(Intent(this, ChannelActivity::class.java))
                true
            }
            R.id.action_select_channel -> {
                showChannelSelectionDialog()
                true
            }
            R.id.action_language_settings -> {
                startActivity(Intent(this, LanguageActivity::class.java))
                true
            }
            R.id.action_about -> {
                val aboutDialog = AlertDialog.Builder(this)
                    .setTitle(getString(R.string.about_title))
                    .setMessage(getString(R.string.about_message))
                    .setPositiveButton("Aceptar", null)
                    .create()
                aboutDialog.show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRepeatingTask()
    }

    private fun showChannelSelectionDialog() {
        val sharedPreferences = getSharedPreferences("Settings", Context.MODE_PRIVATE)
        val channels = mutableListOf<String>()
        for (i in 1..8) {
            val channel = sharedPreferences.getString("channel$i", "")
            if (channel?.isNotEmpty() == true) {
                channels.add(channel)
            }
        }

        val builder = AlertDialog.Builder(this)
        builder.setTitle("Seleccionar Canal")
        val channelNames = channels.map { sharedPreferences.getString("channel_name_$it", "Canal ${channels.indexOf(it) + 1}") }
        builder.setSingleChoiceItems(channelNames.toTypedArray(), channels.indexOf(currentChannel)) { dialog, which ->
            currentChannel = channels[which]
            channelName = channelNames[which] ?: "Canal ${which + 1}"
            val editor = sharedPreferences.edit()
            editor.putString("current_channel", currentChannel)
            editor.putString("current_channel_name", channelName)
            editor.apply()
            dialog.dismiss()
            fetchChannelData(currentChannel)
            val boatMarker = boatMarkers[currentChannel]
            if (boatMarker != null) {
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(boatMarker.position, 15f))
            }
        }
        builder.setNegativeButton("Cancelar", null)
        builder.create().show()
    }

    private fun addRulerPoint(latLng: LatLng) {
        rulerPoints.add(latLng)

        val marker = map.addMarker(
            MarkerOptions()
                .position(latLng)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
        )
        if (marker != null) {
            rulerMarkers.add(marker)
        }

        if (rulerPoints.size >= 2) {
            if (rulerPolyline == null) {
                rulerPolyline = map.addPolyline(PolylineOptions().width(5f).color(ContextCompat.getColor(this, R.color.teal_700)))
            }
            rulerPolyline?.points = rulerPoints

            val (distance, bearing) = calculateDistance(rulerPoints[rulerPoints.size - 2], rulerPoints.last())
            Toast.makeText(this, "Distancia: %.2f mn, Rumbo: %.2f°".format(distance, bearing), Toast.LENGTH_SHORT).show()
        }
    }

    private fun clearRuler() {
        rulerPolyline?.remove()
        rulerPolyline = null
        rulerMarkers.forEach { it.remove() }
        rulerMarkers.clear()
        rulerPoints.clear()
    }

    private fun calculateDistance(point1: LatLng, point2: LatLng): Pair<Double, Double> {
        val R = 6371 // Radio de la Tierra en km
        val latDistance = Math.toRadians(point2.latitude - point1.latitude)
        val lonDistance = Math.toRadians(point2.longitude - point1.longitude)
        val a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2) +
                Math.cos(Math.toRadians(point1.latitude)) * Math.cos(Math.toRadians(point2.latitude)) *
                Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        val distance = R * c * 0.539957 // a millas náuticas

        val y = Math.sin(lonDistance) * Math.cos(Math.toRadians(point2.latitude))
        val x = Math.cos(Math.toRadians(point1.latitude)) * Math.sin(Math.toRadians(point2.latitude)) -
                Math.sin(Math.toRadians(point1.latitude)) * Math.cos(Math.toRadians(point2.latitude)) * Math.cos(lonDistance)
        val bearing = (Math.toDegrees(Math.atan2(y, x)) + 360) % 360

        return Pair(distance, bearing)
    }

    private val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
}
