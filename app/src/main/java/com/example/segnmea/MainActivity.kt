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
import org.json.JSONObject

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityMainBinding
    private lateinit var map: GoogleMap
    private var boatMarker: Marker? = null
    private var handler = Handler(Looper.getMainLooper())
    private var currentChannel = "3002133"
    private var channelName = "Vessel"
    private val refreshInterval = 15000L // 15 segundos

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = getString(R.string.app_name)

        val sharedPreferences = getSharedPreferences("Settings", Context.MODE_PRIVATE)
        currentChannel = sharedPreferences.getString("channel1", "3002133") ?: "3002133"

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

        // Zoom
        binding.zoomInButton.setOnClickListener { map.animateCamera(CameraUpdateFactory.zoomIn()) }
        binding.zoomOutButton.setOnClickListener { map.animateCamera(CameraUpdateFactory.zoomOut()) }

        startRepeatingTask()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        map.uiSettings.isZoomControlsEnabled = false
    }

    private val updateTask = object : Runnable {
        override fun run() {
            fetchChannelData()
            handler.postDelayed(this, refreshInterval)
        }
    }

    private fun startRepeatingTask() {
        handler.post(updateTask)
    }

    private fun stopRepeatingTask() {
        handler.removeCallbacks(updateTask)
    }

    private fun fetchChannelData() {
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
                                    .icon(getBitmapDescriptor(R.drawable.ic_boat_marker))
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
        // Orden correcto en pantalla
        binding.channelNameTextView.text = "Name: $channelName"
        binding.latTextView.text = "Lat: $lat"
        binding.lonTextView.text = "Lon: $lon"
        binding.speedTextView.text = "Speed: $speed kn"
        binding.headingTextView.text = "Heading: $heading°"
        binding.pitchTextView.text = "Pitch: $pitch°"
        binding.rollTextView.text = "Roll: $roll°"
    }

    private fun getBitmapDescriptor(resId: Int): BitmapDescriptor? {
        val drawable = ContextCompat.getDrawable(this, resId) ?: return null
        drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
        val bitmap = Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.draw(canvas)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu) // menú superior (canal)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_channel_settings -> {
                startActivity(Intent(this, ChannelActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRepeatingTask()
    }
}
