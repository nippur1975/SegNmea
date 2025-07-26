package com.example.segnmea

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.location.Location
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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
import com.google.maps.android.clustering.ClusterManager
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
    private var currentZoom = 15f
    private var currentMarker: Marker? = null
    private lateinit var clusterManager: ClusterManager<MyClusterItem>
    private var rulerMode = false
    private val rulerPoints = mutableListOf<LatLng>()
    private var rulerLine: Polyline? = null
    private var rulerMarkers = mutableListOf<Marker>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sharedPreferences = getSharedPreferences("Settings", Context.MODE_PRIVATE)
        val language = sharedPreferences.getString("language", "en")
        setLocale(language!!)
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

        binding.zoomInButton.setOnClickListener {
            mMap.animateCamera(CameraUpdateFactory.zoomIn())
        }

        binding.zoomOutButton.setOnClickListener {
            mMap.animateCamera(CameraUpdateFactory.zoomOut())
        }

        binding.channelButton.setOnClickListener {
            val sharedPreferences = getSharedPreferences("Settings", Context.MODE_PRIVATE)
            val channels = arrayOf(
                sharedPreferences.getString("channel1", "3002133")!!,
                sharedPreferences.getString("channel2", "3007462")!!,
                sharedPreferences.getString("channel3", "3017966")!!,
                sharedPreferences.getString("channel4", "3017982")!!
            )
            var checkedItem = 0
            when (channel) {
                channels[0] -> checkedItem = 0
                channels[1] -> checkedItem = 1
                channels[2] -> checkedItem = 2
                channels[3] -> checkedItem = 3
            }

            AlertDialog.Builder(this)
                .setTitle("Select Active Channel")
                .setSingleChoiceItems(channels, checkedItem) { _, which ->
                    val editor = sharedPreferences.edit()
                    editor.putString("channel", channels[which])
                    editor.apply()
                    fetchData()
                }
                .setPositiveButton("OK") { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.setInfoWindowAdapter(CustomInfoWindowAdapter(this))
        mMap.uiSettings.isZoomControlsEnabled = false

        clusterManager = ClusterManager(this, mMap)
        mMap.setOnCameraIdleListener(clusterManager)
        mMap.setOnMarkerClickListener(clusterManager)

        mMap.setOnMapClickListener { latLng ->
            if (rulerMode) {
                if (rulerPoints.size < 2) {
                    rulerPoints.add(latLng)
                    val marker = mMap.addMarker(
                        MarkerOptions()
                            .position(latLng)
                            .title("Point ${rulerPoints.size}")
                    )
                    if (marker != null) {
                        rulerMarkers.add(marker)
                    }

                    if (rulerPoints.size == 2) {
                        val distance = FloatArray(1)
                        Location.distanceBetween(
                            rulerPoints[0].latitude, rulerPoints[0].longitude,
                            rulerPoints[1].latitude, rulerPoints[1].longitude,
                            distance
                        )
                        val start = Location("")
                        start.latitude = rulerPoints[0].latitude
                        start.longitude = rulerPoints[0].longitude
                        val end = Location("")
                        end.latitude = rulerPoints[1].latitude
                        end.longitude = rulerPoints[1].longitude
                        val bearing = start.bearingTo(end)
                        val info = "Distance: ${"%.2f".format(distance[0] / 1852)} NM\\nBearing: ${"%.1f".format(bearing)}°"

                        rulerLine = mMap.addPolyline(
                            PolylineOptions()
                                .add(rulerPoints[0], rulerPoints[1])
                                .color(android.graphics.Color.YELLOW)
                                .width(5f)
                        )
                        Toast.makeText(this, info, Toast.LENGTH_LONG).show()

                        handler.postDelayed({
                            rulerLine?.remove()
                            rulerMarkers.forEach { it.remove() }
                            rulerPoints.clear()
                            rulerMarkers.clear()
                            rulerMode = false
                        }, 30000)
                    }
                }
            }
        }

        binding.compassButton.post {
            val buttonContainerHeight = binding.compassButton.height
            mMap.setPadding(0, 0, 0, buttonContainerHeight)
        }

        binding.trackSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                fetchAllChannelsData()
            } else {
                clusterManager.clearItems()
                clusterManager.cluster()
            }
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
            R.id.action_about -> {
                AlertDialog.Builder(this)
                    .setTitle(R.string.about)
                    .setMessage("Programa de seguimiento barco - desarrollado por Hdelacruz")
                    .setPositiveButton("Cerrar") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
                true
            }
            R.id.action_ruler -> {
                rulerMode = true
                Toast.makeText(this, "Ruler mode activated. Tap on the map to select two points.", Toast.LENGTH_SHORT).show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun fetchData() {
        val sharedPreferences = getSharedPreferences("Settings", Context.MODE_PRIVATE)
        channel = sharedPreferences.getString("channel", "3002133") ?: "3002133"
        fetchAllChannelsData()
        handler.postDelayed({ fetchData() }, 15000)
    }

    private fun fetchAllChannelsData() {
        val sharedPreferences = getSharedPreferences("Settings", Context.MODE_PRIVATE)
        val channels = arrayOf(
            sharedPreferences.getString("channel1", "3002133")!!,
            sharedPreferences.getString("channel2", "3007462")!!,
            sharedPreferences.getString("channel3", "3017966")!!,
            sharedPreferences.getString("channel4", "3017982")!!
        )
        val icons = arrayOf(R.drawable.purple_triangle, R.drawable.blue_triangle, R.drawable.red_triangle, R.drawable.yellow_triangle)
        val trackColors = arrayOf(0xFF800080.toInt(), android.graphics.Color.BLUE, android.graphics.Color.RED, android.graphics.Color.YELLOW)

        mMap.clear()
        clusterManager.clearItems()

        for (i in channels.indices) {
            val channel = channels[i]
            val icon = icons[i]
            val trackColor = trackColors[i]
            val results = if (channel == this.channel) 2000 else 1
            val url = "https://api.thingspeak.com/channels/$channel/feeds.json?results=$results"

            val stringRequest = StringRequest(
                Request.Method.GET, url,
                { response ->
                    val jsonObject = JSONObject(response)
                    val feeds = jsonObject.getJSONArray("feeds")
                    if (feeds.length() > 0) {
                        val channelTrackPoints = mutableListOf<LatLng>()
                        for (j in 0 until feeds.length()) {
                            val feed = feeds.getJSONObject(j)
                            val lat = feed.getString("field3").toDouble()
                            val lon = feed.getString("field4").toDouble()
                            val position = LatLng(lat, lon)
                            channelTrackPoints.add(position)

                            if (binding.trackSwitch.isChecked) {
                                val markerData = "Channel: $channel\\nDate: ${formatDate(feed.getString("created_at"))}\\nLat: ${formatLat(lat)}\\nLon: ${formatLon(lon)}"
                                val clusterItem = MyClusterItem(lat, lon, "Channel $channel", markerData)
                                clusterManager.addItem(clusterItem)
                            }
                        }

                        mMap.addPolyline(
                            PolylineOptions()
                                .addAll(channelTrackPoints)
                                .color(trackColor)
                                .width(5f)
                        )

                        val lastFeed = feeds.getJSONObject(feeds.length() - 1)
                        val lat = lastFeed.getString("field3").toDouble()
                        val lon = lastFeed.getString("field4").toDouble()
                        val position = LatLng(lat, lon)
                        val heading = lastFeed.getString("field6").toFloat()

                        mMap.addMarker(
                            MarkerOptions()
                                .position(position)
                                .title("Channel $channel")
                                .icon(bitmapDescriptorFromVector(this, icon, 52, 52))
                                .rotation(heading)
                                .anchor(0.5f, 0.5f)
                        )

                        if (channel == this.channel) {
                            val channelName = jsonObject.getJSONObject("channel").getString("name")
                            binding.channelNameTextView.text = channelName
                            val speed = lastFeed.getString("field5")
                            val pitch = lastFeed.getString("field1")
                            val roll = lastFeed.getString("field2")

                            binding.latTextView.text = "${getString(R.string.lat)} : ${formatLat(lat)}"
                            binding.lonTextView.text = "${getString(R.string.lon)} : ${formatLon(lon)}"
                            binding.speedTextView.text = "${getString(R.string.speed)} : ${"%.1f".format(speed.toFloat())} Kn ."
                            binding.headingTextView.text = "${getString(R.string.heading)} : ${heading.toInt()}°"
                            binding.pitchTextView.text = "${getString(R.string.pitch)} : ${"%.1f".format(pitch.toFloat())}°"
                            binding.rollTextView.text = "${getString(R.string.roll)} : ${"%.1f".format(roll.toFloat())}°"
                            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(position, currentZoom))

                            val createdAt = lastFeed.getString("created_at")
                            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                            sdf.timeZone = TimeZone.getTimeZone("UTC")
                            val date = sdf.parse(createdAt)
                            val now = Date()
                            val diff = now.time - date.time
                            if (diff < 60000) {
                                binding.ledView.setBackgroundResource(R.drawable.green_dot)
                            } else {
                                binding.ledView.setBackgroundResource(R.drawable.red_dot)
                            }
                        }
                    }
                },
                { })
            VolleySingleton.getInstance(this).addToRequestQueue(stringRequest)
        }
        clusterManager.cluster()
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

    private fun setLocale(languageCode: String) {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)
        val config = resources.configuration
        config.setLocale(locale)
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
