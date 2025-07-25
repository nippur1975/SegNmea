package com.example.segnmea

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import androidx.core.content.ContextCompat
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.example.segnmea.databinding.ActivityMainBinding
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import org.json.JSONObject
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityMainBinding
    private lateinit var mMap: GoogleMap
    private val handler = Handler(Looper.getMainLooper())
    private var channel = "3002133"
    private val trackPoints = mutableListOf<LatLng>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = getString(R.string.app_name)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        binding.compassButton.setOnClickListener {
            startActivity(Intent(this, CompassActivity::class.java))
        }
        binding.clinometerButton.setOnClickListener {
            startActivity(Intent(this, ClinometerActivity::class.java))
        }
        binding.dataButton.setOnClickListener {
            startActivity(Intent(this, DataActivity::class.java))
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.setOnCameraIdleListener {
            fetchData()
        }
        fetchData()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun fetchData() {
        val sharedPreferences = getSharedPreferences("Settings", Context.MODE_PRIVATE)
        channel = sharedPreferences.getString("channel", "3002133") ?: "3002133"
        val queue = Volley.newRequestQueue(this)
        val url = "https://api.thingspeak.com/channels/$channel/feeds.json?results=1"

        val stringRequest = StringRequest(
            Request.Method.GET, url,
            { response ->
                val jsonObject = JSONObject(response)
                val feeds = jsonObject.getJSONArray("feeds")
                if (feeds.length() > 0) {
                    val lastFeed = feeds.getJSONObject(0)
                    val lat = lastFeed.getString("field3").toDouble()
                    val lon = lastFeed.getString("field4").toDouble()
                    val speed = lastFeed.getString("field5")
                    val heading = lastFeed.getString("field6").toFloat()
                    val pitch = lastFeed.getString("field1")
                    val roll = lastFeed.getString("field2")

                    binding.latTextView.text = "Lat: $lat"
                    binding.lonTextView.text = "Lon: $lon"
                    binding.speedTextView.text = "Speed: $speed Kn"
                    binding.headingTextView.text = "Heading: ${heading.toInt()}°"
                    binding.pitchTextView.text = "Pitch: $pitch°"
                    binding.rollTextView.text = "Roll: $roll°"

                    val position = LatLng(lat, lon)
                    trackPoints.add(position)
                    if (trackPoints.size > 1000) {
                        trackPoints.removeAt(0)
                    }

                    mMap.clear()
                    mMap.addPolyline(
                        PolylineOptions()
                            .addAll(trackPoints)
                            .color(android.graphics.Color.BLUE)
                            .width(5f)
                    )

                    val zoom = mMap.cameraPosition.zoom
                    val markerSize = (zoom * 5).toInt()

                    mMap.addMarker(
                        MarkerOptions()
                            .position(position)
                            .title("Current Position")
                            .snippet("Speed: $speed Kn, Heading: ${heading.toInt()}°")
                            .icon(bitmapDescriptorFromVector(this, R.drawable.ic_boat_marker, markerSize, markerSize))
                            .rotation(heading)
                            .anchor(0.5f, 0.5f)
                    )
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(position, 15f))
                }
            },
            { })

        queue.add(stringRequest)

        handler.postDelayed({ fetchData() }, 15000)
    }

    // --- Funciones auxiliares convertidas a Kotlin ---
    private fun formatDate(iso: String): String {
        return try {
            val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            isoFormat.timeZone = TimeZone.getTimeZone("UTC")
            val date = isoFormat.parse(iso)
            if (date != null) {
                val outFormat = SimpleDateFormat("HH:mm   dd-MM-yyyy", Locale.US)
                outFormat.timeZone = TimeZone.getDefault()
                outFormat.format(date)
            } else {
                iso
            }
        } catch (e: ParseException) {
            e.printStackTrace()
            iso
        }
    }

    private fun formatLat(lat: Double): String {
        val hemi = if (lat >= 0) "N" else "S"
        val absLat = kotlin.math.abs(lat)
        val grados = absLat.toInt()
        val minutos = (absLat - grados) * 60
        return String.format(Locale.US, "%02d° %.3f' %s", grados, minutos, hemi)
    }

    private fun formatLon(lon: Double): String {
        val hemi = if (lon >= 0) "E" else "W"
        val absLon = kotlin.math.abs(lon)
        val grados = absLon.toInt()
        val minutos = (absLon - grados) * 60
        return String.format(Locale.US, "%03d° %.3f' %s", grados, minutos, hemi)
    }

    private fun getBitmapDescriptor(id: Int): BitmapDescriptor {
        val vectorDrawable = ContextCompat.getDrawable(this, id)!!
        val h = vectorDrawable.intrinsicHeight
        val w = vectorDrawable.intrinsicWidth
        vectorDrawable.setBounds(0, 0, w, h)
        val bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bm)
        vectorDrawable.draw(canvas)
        return BitmapDescriptorFactory.fromBitmap(bm)
    }

    private fun bitmapDescriptorFromVector(context: Context, vectorResId: Int, width: Int, height: Int): BitmapDescriptor? {
        return ContextCompat.getDrawable(context, vectorResId)?.run {
            setBounds(0, 0, width, height)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            draw(Canvas(bitmap))
            BitmapDescriptorFactory.fromBitmap(bitmap)
        }
    }
}
