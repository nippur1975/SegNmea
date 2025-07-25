package com.example.segnmea

import android.content.Context
import android.content.Intent
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.view.animation.LinearInterpolator
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.example.segnmea.databinding.ActivityCompassBinding
import org.json.JSONObject

class CompassActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCompassBinding
    private val handler = Handler(Looper.getMainLooper())
    private var channel = "3002133"

    private var currentRotation = 0f
    private var lastHeadingValue = -1f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCompassBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = getString(R.string.compass)

        binding.mainButton.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
        binding.clinometerButton.setOnClickListener {
            startActivity(Intent(this, ClinometerActivity::class.java))
        }
        binding.dataButton.setOnClickListener {
            startActivity(Intent(this, DataActivity::class.java))
        }

        fetchData()
    }

    private fun fetchData() {
        val sharedPreferences = getSharedPreferences("Settings", Context.MODE_PRIVATE)
        channel = sharedPreferences.getString("channel", "3002133") ?: "3002133"
        val queue = Volley.newRequestQueue(this)
        val url = "https://api.thingspeak.com/channels/$channel/feeds.json?results=1"

        val stringRequest = StringRequest(
            Request.Method.GET, url,
            { response ->
                try {
                    val jsonObject = JSONObject(response)
                    val channelObject = jsonObject.getJSONObject("channel")
                    val channelName = channelObject.getString("name")
                    binding.channelNameTextView.text = channelName

                    val feeds = jsonObject.getJSONArray("feeds")
                    if (feeds.length() > 0) {
                        val lastFeed = feeds.getJSONObject(0)
                        val headingValue = lastFeed.optString("field6", "0").toFloatOrNull() ?: 0f

                        // Si el rumbo cambia, actualizar con animación
                        if (headingValue != lastHeadingValue) {
                            animateHeadingText("${headingValue.toInt()}°")
                            lastHeadingValue = headingValue
                        }

                        // Rotar suavemente la rosa
                        rotateCompass(-headingValue)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            },
            { error ->
                error.printStackTrace()
            }
        )

        queue.add(stringRequest)
        handler.postDelayed({ fetchData() }, 15000)
    }

    private fun rotateCompass(targetRotation: Float) {
        var normalizedTarget = targetRotation % 360
        var normalizedCurrent = currentRotation % 360

        var delta = normalizedTarget - normalizedCurrent
        if (delta > 180) delta -= 360
        if (delta < -180) delta += 360
        normalizedTarget = normalizedCurrent + delta

        val animator = ObjectAnimator.ofFloat(binding.compassRose, "rotation", currentRotation, normalizedTarget)
        animator.duration = 1000
        animator.interpolator = LinearInterpolator()
        animator.start()

        currentRotation = normalizedTarget
    }

    private fun animateHeadingText(newText: String) {
        // Animación combinada: escala + opacidad
        val scaleUpX = PropertyValuesHolder.ofFloat("scaleX", 1f, 1.3f, 1f)
        val scaleUpY = PropertyValuesHolder.ofFloat("scaleY", 1f, 1.3f, 1f)
        val fade = PropertyValuesHolder.ofFloat("alpha", 0f, 1f)

        binding.headingValueTextView.text = newText
        ObjectAnimator.ofPropertyValuesHolder(binding.headingValueTextView, scaleUpX, scaleUpY, fade).apply {
            duration = 500 // medio segundo
            start()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
