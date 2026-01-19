package com.example.segnmea

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.segnmea.databinding.ActivityMainBinding
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import androidx.collection.LruCache

class MainActivity : AppCompatActivity(), OnMapReadyCallback, BluetoothService.BluetoothListener {

    private lateinit var binding: ActivityMainBinding
    private val bitmapCache = LruCache<String, Bitmap>(1024)
    private lateinit var map: GoogleMap
    private var boatMarker: Marker? = null
    private lateinit var trackPolyline: Polyline
    private var handler = Handler(Looper.getMainLooper())

    // Core Components
    private lateinit var bluetoothService: BluetoothService
    private val nmeaParser = NmeaParser()
    private lateinit var trackManager: TrackManager
    private lateinit var thingSpeakUploader: ThingSpeakUploader

    // Bluetooth
    private var bluetoothAdapter: BluetoothAdapter? = null
    private val REQUEST_ENABLE_BT = 1
    private val PERMISSION_REQUEST_CODE = 2

    // State
    private var isConnected = false
    private var writeApiKey = "YOUR_WRITE_API_KEY" // Should be in settings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = getString(R.string.app_name)

        // Initialize Managers
        bluetoothService = BluetoothService(handler)
        bluetoothService.setListener(this)
        trackManager = TrackManager(this)
        thingSpeakUploader = ThingSpeakUploader(this)

        // Load Settings
        val sharedPreferences = getSharedPreferences("Settings", Context.MODE_PRIVATE)
        writeApiKey = sharedPreferences.getString("write_api_key", "") ?: ""
        if (writeApiKey.isEmpty()) {
            Toast.makeText(this, "Set ThingSpeak Write API Key in Settings", Toast.LENGTH_LONG).show()
        }
        thingSpeakUploader.setWriteApiKey(writeApiKey)

        // Setup Map
        val mapOptions = GoogleMapOptions().mapId("YOUR_MAP_ID")
        val mapFragment = SupportMapFragment.newInstance(mapOptions)
        supportFragmentManager.beginTransaction()
            .replace(R.id.map, mapFragment)
            .commit()
        mapFragment.getMapAsync(this)

        // Setup UI Buttons
        binding.compassButton.setOnClickListener {
            val intent = Intent(this, CompassActivity::class.java)
            startActivity(intent)
        }
        binding.clinometerButton.setOnClickListener {
            val intent = Intent(this, ClinometerActivity::class.java)
            startActivity(intent)
        }
        binding.dataButton.setOnClickListener {
            val intent = Intent(this, DataActivity::class.java)
            startActivity(intent)
        }

        binding.trackSwitch.setOnCheckedChangeListener { _, isChecked ->
             if (::trackPolyline.isInitialized) {
                 trackPolyline.isVisible = isChecked
             }
        }

        // Bluetooth Setup
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        // Initial UI State
        binding.channelNameTextView.text = "Disconnected"
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        map.uiSettings.isZoomControlsEnabled = true

        // Setup Track Polyline
        trackPolyline = map.addPolyline(
            PolylineOptions()
                .width(5f)
                .color(0xFF0000FF.toInt()) // Blue
                .addAll(trackManager.getTrack())
        )
        trackPolyline.isVisible = binding.trackSwitch.isChecked

        // Setup default position if track exists
        val track = trackManager.getTrack()
        if (track.isNotEmpty()) {
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(track.last(), 15f))
        }
    }

    // --- Bluetooth Logic ---

    private fun connectBluetooth() {
        if (!checkPermissions()) {
            requestPermissions()
            return
        }

        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
            return
        }

        showDeviceSelectionDialog()
    }

    @SuppressLint("MissingPermission")
    private fun showDeviceSelectionDialog() {
        val pairedDevices = bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
        val deviceNames = pairedDevices.map { "${it.name} (${it.address})" }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Select Bluetooth Device")
            .setItems(deviceNames) { _, which ->
                val device = pairedDevices[which]
                bluetoothService.connect(device)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun disconnectBluetooth() {
        bluetoothService.stop()
    }

    // --- BluetoothService Listener ---

    override fun onConnected(deviceName: String) {
        isConnected = true
        runOnUiThread {
            binding.channelNameTextView.text = "Connected: $deviceName"
            Toast.makeText(this, "Connected to $deviceName", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onStatusChange(status: String) {
        runOnUiThread {
            if (!isConnected) {
                binding.channelNameTextView.text = status
            }
        }
    }

    private var lastUploadTime = 0L
    private val UPLOAD_INTERVAL = 15000L // 15 seconds

    override fun onDataReceived(data: String) {
        // Parse
        val updated = nmeaParser.parse(data)
        if (!updated) return

        val parsedData = nmeaParser.getData()

        // Update UI
        runOnUiThread {
            updateUI(parsedData)
        }

        // Update Track and Map
        if (parsedData.lat != 0.0 && parsedData.lon != 0.0) {
            trackManager.addPoint(parsedData.lat, parsedData.lon)
            runOnUiThread {
                 trackPolyline.points = trackManager.getTrack()
                 updateBoatMarker(parsedData)
            }

            // Upload to ThingSpeak (Throttled)
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastUploadTime >= UPLOAD_INTERVAL) {
                thingSpeakUploader.upload(parsedData)
                lastUploadTime = currentTime
            }
        }
    }

    private fun updateUI(data: NmeaParser.ParsedData) {
        val latFormatted = formatCoordinate(data.lat, "N", "S")
        val lonFormatted = formatCoordinate(data.lon, "E", "W")

        binding.latTextView.text = "Lat: $latFormatted"
        binding.lonTextView.text = "Lon: $lonFormatted"
        binding.speedTextView.text = "Speed: %.1f kn".format(data.speed)
        binding.headingTextView.text = "Hdg: %.0f°".format(data.heading)
        binding.pitchTextView.text = "Pitch: %.1f°".format(data.pitch)
        binding.rollTextView.text = "Roll: %.1f°".format(data.roll)
    }

    private fun updateBoatMarker(data: NmeaParser.ParsedData) {
        val pos = LatLng(data.lat, data.lon)
        val rotation = data.heading.toFloat()

        if (boatMarker == null) {
             boatMarker = map.addMarker(
                MarkerOptions()
                    .position(pos)
                    .icon(BitmapDescriptorFactory.fromBitmap(getBoatBitmap()))
                    .rotation(rotation)
                    .anchor(0.5f, 0.5f)
            )
            map.animateCamera(CameraUpdateFactory.newLatLng(pos))
        } else {
            boatMarker?.position = pos
            boatMarker?.rotation = rotation
        }
    }

    private fun getBoatBitmap(): Bitmap {
        // Use existing resource or default
        return getBitmap(R.drawable.ic_navigation, 0xFFd9534f.toInt())!!
    }

    // --- Helpers ---

    private fun formatCoordinate(value: Double, posDir: String, negDir: String): String {
        val degrees = Math.abs(value.toInt())
        val minutes = Math.abs(value - value.toInt()) * 60
        val direction = if (value >= 0) posDir else negDir
        return "$degrees° ${"%.3f".format(minutes)}' $direction"
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

    // --- Permissions ---

    private fun checkPermissions(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                   ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }
        ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE)
    }

    // --- Menu ---

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        // Add Connect/Disconnect option dynamically or reused existing
        menu?.add(0, 101, 0, "Connect Bluetooth")
        menu?.add(0, 102, 0, "Disconnect Bluetooth")
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            101 -> {
                connectBluetooth()
                true
            }
            102 -> {
                disconnectBluetooth()
                true
            }
            R.id.action_alarm_settings -> {
                startActivity(Intent(this, AlarmActivity::class.java))
                true
            }
            R.id.action_channel_settings -> {
                // Reuse this for Settings where API Key can be input
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.action_select_channel -> {
                // Not relevant for single boat source, but kept to avoid crash
                true
            }
            R.id.action_language_settings -> {
                startActivity(Intent(this, LanguageActivity::class.java))
                true
            }
            R.id.action_about -> {
                val aboutDialog = AlertDialog.Builder(this)
                    .setTitle(getString(R.string.about_title))
                    .setMessage("App modified to receive NMEA via Bluetooth and upload to ThingSpeak.")
                    .setPositiveButton("Ok", null)
                    .create()
                aboutDialog.show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onStop() {
        super.onStop()
        trackManager.saveNow()
    }

    override fun onDestroy() {
        super.onDestroy()
        bluetoothService.stop()
    }
}
