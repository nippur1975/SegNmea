package com.example.segnmea

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import com.example.segnmea.databinding.ActivitySettingsBinding

/**
 * Activity that displays the settings screen.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = getString(R.string.settings)

        val prefs = getSharedPreferences("Settings", Context.MODE_PRIVATE)
        binding.apiKeyEditText.setText(prefs.getString("write_api_key", ""))

        // Set up the button click listeners
        binding.languageButton.setOnClickListener {
            startActivity(Intent(this, LanguageActivity::class.java))
        }

        binding.channelButton.setOnClickListener {
            startActivity(Intent(this, ChannelActivity::class.java))
        }

        binding.alarmButton.setOnClickListener {
            startActivity(Intent(this, AlarmActivity::class.java))
        }

        binding.saveApiKeyButton.setOnClickListener {
            prefs.edit().putString("write_api_key", binding.apiKeyEditText.text.toString()).apply()
            Toast.makeText(this, "API Key Saved", Toast.LENGTH_SHORT).show()
        }
    }
}
