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
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.segnmea.databinding.ActivityMainBinding
import com.google.android.gms.maps.GoogleMapOptions
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
import androidx.collection.LruCache
import android.bluetooth.BluetoothDevice
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import android.widget.EditText

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityMainBinding
    private val bitmapCache = LruCache<String, Bitmap>(1024)
    private lateinit var map: GoogleMap

    // Bluetooth & NMEA
    private var bluetoothService: BluetoothService? = null
    private val nmeaParser = NmeaParser()
    private var lastUploadTime = 0L
    private val uploadInterval = 20000L // 20s to be safe with ThingSpeak limits
    private var dailyTrackPoints = mutableListOf<LatLng>()
    private var currentDay: String = ""
    private var dailyPolyline: Polyline? = null
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

        // IMPORTANT: Replace "YOUR_MAP_ID" with your actual Map ID
        val mapOptions = GoogleMapOptions().mapId("YOUR_MAP_ID")
        val mapFragment = SupportMapFragment.newInstance(mapOptions)
        supportFragmentManager.beginTransaction()
            .replace(R.id.map, mapFragment)
            .commit()
        mapFragment.getMapAsync(this)

        // Botones de navegación (inferiores)
        binding.compassButton.setOnClickListener {
            val intent = Intent(this, CompassActivity::class.java)
            intent.putExtra("channel_id", currentChannel)
            startActivity(intent)
        }
        binding.clinometerButton.setOnClickListener {
            val intent = Intent(this, ClinometerActivity::class.java)
            intent.putExtra("channel_id", currentChannel)
            startActivity(intent)
        }
        binding.dataButton.setOnClickListener {
            val intent = Intent(this, DataActivity::class.java)
            intent.putExtra("channel_id", currentChannel)
            startActivity(intent)
        }

        binding.trackSwitch.setOnCheckedChangeListener { _, _ ->
            updateHistoricalMarkers()
        }

        binding.rulerSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                binding.rulerInfoTextView.visibility = View.VISIBLE
                map.setOnMapClickListener { latLng ->
                    addRulerPoint(latLng)
                }
            } else {
                binding.rulerInfoTextView.visibility = View.GONE
                map.setOnMapClickListener(null)
                clearRuler()
            }
        }

        binding.connectBluetoothButton.setOnClickListener {
             checkPermissionsAndShowDialog()
        }

        // Initialize Bluetooth Service
        bluetoothService = BluetoothService(this) { nmeaSentence ->
            handleNmeaData(nmeaSentence)
        }

        // Initialize Date for track reset
        currentDay = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        startRepeatingTask()
    }

    private fun checkPermissionsAndShowDialog() {
        val permissions = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), 101)
        } else {
            showBluetoothDeviceDialog()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                showBluetoothDeviceDialog()
            } else {
                Toast.makeText(this, "Permisos necesarios para Bluetooth", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showBluetoothDeviceDialog() {
        val devices = bluetoothService?.getPairedDevices()?.toList() ?: emptyList()
        if (devices.isEmpty()) {
            Toast.makeText(this, "No se encontraron dispositivos emparejados", Toast.LENGTH_SHORT).show()
            return
        }

        val deviceNames = devices.map { "${it.name} (${it.address})" }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Conectar dispositivo Bluetooth")
            .setItems(deviceNames) { _, which ->
                val device = devices[which]
                bluetoothService?.connect(device)
                Toast.makeText(this, "Conectando a ${device.name}...", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun handleNmeaData(sentence: String) {
        val data = nmeaParser.parse(sentence)

        // Check if day changed to reset track
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        if (today != currentDay) {
            currentDay = today
            dailyTrackPoints.clear()
            dailyPolyline?.remove()
            dailyPolyline = null
        }

        runOnUiThread {
            // Update UI Labels
            data.latitude?.let { lat ->
                data.longitude?.let { lon ->
                    // Update Text Views
                    updateUI(lat.toString(), lon.toString(),
                            data.speedKnots?.toString() ?: "0.0",
                            data.courseHeading?.toString() ?: "0.0",
                            data.pitch?.toString() ?: "0.0",
                            data.roll?.toString() ?: "0.0")

                    val pos = LatLng(lat, lon)

                    // Update Local Boat Marker
                    updateLocalBoatMarker(pos, data.courseHeading?.toFloat() ?: 0f)

                    // Update Daily Track
                    dailyTrackPoints.add(pos)
                    updateDailyTrack()

                    // Upload to ThingSpeak
                    uploadToThingSpeak(data)
                }
            }
        }
    }

    private fun updateLocalBoatMarker(position: LatLng, heading: Float) {
        if (!::map.isInitialized) return

        if (boatMarker == null) {
            val bitmap = getBitmap(R.drawable.ic_navigation, 0xFF00FF00.toInt()) // Green for local
            boatMarker = map.addMarker(
                MarkerOptions()
                    .position(position)
                    .icon(BitmapDescriptorFactory.fromBitmap(bitmap!!))
                    .rotation(heading)
                    .anchor(0.5f, 0.5f)
                    .title("Mi Barco (Bluetooth)")
            )
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(position, 17f))
        } else {
            boatMarker?.position = position
            boatMarker?.rotation = heading
        }
    }

    private fun updateDailyTrack() {
        if (!::map.isInitialized) return

        if (dailyPolyline == null) {
            dailyPolyline = map.addPolyline(
                PolylineOptions()
                    .width(8f)
                    .color(0xFF00FF00.toInt()) // Green track
                    .addAll(dailyTrackPoints)
            )
        } else {
            dailyPolyline?.points = dailyTrackPoints
        }
    }

    private fun uploadToThingSpeak(data: NmeaData) {
        val now = System.currentTimeMillis()
        if (now - lastUploadTime < uploadInterval) return

        // Need Write API Key. For now we assume the current channel has one or user enters it.
        // Since the app currently only reads, we need to know WHERE to write.
        // I will assume a hardcoded key or fetch from settings.
        // For this task, I will add a placeholder "WRITE_API_KEY" logic.

        val sharedPreferences = getSharedPreferences("Settings", Context.MODE_PRIVATE)
        val writeKey = sharedPreferences.getString("write_api_key", "") ?: return

        if (writeKey.isEmpty()) return

        lastUploadTime = now

        val url = "https://api.thingspeak.com/update?api_key=$writeKey" +
                  "&field1=${data.pitch ?: 0}" +
                  "&field2=${data.roll ?: 0}" +
                  "&field3=${data.latitude ?: 0}" +
                  "&field4=${data.longitude ?: 0}" +
                  "&field5=${data.speedKnots ?: 0}" +
                  "&field6=${data.courseHeading ?: 0}"

        val request = com.android.volley.toolbox.StringRequest(
            com.android.volley.Request.Method.GET, url,
            { response -> Log.d("ThingSpeak", "Upload success: $response") },
            { error -> Log.e("ThingSpeak", "Upload failed", error) }
        )

        VolleySingleton.getInstance(this).addToRequestQueue(request)
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
            val newHistoricalData = mutableListOf<TrackPoint>()
            val startIndex = if (feeds.length() > 1000) feeds.length() - 1000 else 0
            for (i in startIndex until feeds.length()) {
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
                    newHistoricalData.add(trackPoint)
                    points.add(trackPoint.getPosition())
                }
            }

            // Remove old markers for this channel
            val markersToRemove = mutableListOf<Marker>()
            markerToTrackPointMap.forEach { (marker, trackPoint) ->
                if (historicalData[channelId]?.contains(trackPoint) == true && !newHistoricalData.contains(trackPoint)) {
                    markersToRemove.add(marker)
                }
            }
            markersToRemove.forEach {
                it.remove()
                markerToTrackPointMap.remove(it)
            }

            // Add new markers
            newHistoricalData.forEach { trackPoint ->
                if (!markerToTrackPointMap.containsValue(trackPoint)) {
                    val historicalMarker = map.addMarker(
                        MarkerOptions()
                            .position(trackPoint.getPosition())
                            .icon(BitmapDescriptorFactory.fromBitmap(getBitmap(R.drawable.ic_historical_marker, 0xFF0000FF.toInt())!!))
                            .anchor(0.5f, 0.5f)
                            .visible(false) // Initially hidden
                    )
                    if (historicalMarker != null) {
                        markerToTrackPointMap[historicalMarker] = trackPoint
                    }
                }
            }
            historicalData[channelId] = newHistoricalData
            updateHistoricalMarkers()

            trackPolylines[channelId]?.points = points

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
        val cacheKey = "$resId-$color"
        var bitmap = bitmapCache.get(cacheKey)
        if (bitmap == null) {
            val drawable = ContextCompat.getDrawable(this, resId) ?: return null
            val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth * 2 else 100
            val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight * 2 else 100
            drawable.setBounds(0, 0, width, height)
            drawable.setTint(color)
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.draw(canvas)
            bitmapCache.put(cacheKey, bitmap)
        }
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
    R.id.action_ts_key -> {
        showApiKeyDialog()
        true
    }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRepeatingTask()
    }

    private fun showApiKeyDialog() {
        val sharedPreferences = getSharedPreferences("Settings", Context.MODE_PRIVATE)
        val currentKey = sharedPreferences.getString("write_api_key", "")

        val input = EditText(this)
        input.setText(currentKey)
        input.hint = "ThingSpeak Write API Key"

        AlertDialog.Builder(this)
            .setTitle("Configurar API Key de ThingSpeak")
            .setView(input)
            .setPositiveButton("Guardar") { _, _ ->
                val newKey = input.text.toString().trim()
                sharedPreferences.edit().putString("write_api_key", newKey).apply()
                Toast.makeText(this, "API Key guardada", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
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
            val oldChannel = currentChannel
            currentChannel = channels[which]
            channelName = channelNames[which] ?: "Canal ${which + 1}"
            val editor = sharedPreferences.edit()
            editor.putString("current_channel", currentChannel)
            editor.putString("current_channel_name", channelName)
            editor.apply()
            dialog.dismiss()

            updateHistoricalMarkers()
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
            binding.rulerInfoTextView.text = "Distancia: %.2f mn, Rumbo: %.2f°".format(distance, bearing)
        }
    }

    private fun clearRuler() {
        rulerPolyline?.remove()
        rulerPolyline = null
        rulerMarkers.forEach { it.remove() }
        rulerMarkers.clear()
        rulerPoints.clear()
        binding.rulerInfoTextView.text = ""
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

    private fun updateHistoricalMarkers() {
        markerToTrackPointMap.forEach { (marker, trackPoint) ->
            val belongingToCurrentChannel = historicalData[currentChannel]?.contains(trackPoint) == true
            marker.isVisible = binding.trackSwitch.isChecked && belongingToCurrentChannel
        }
    }
}
