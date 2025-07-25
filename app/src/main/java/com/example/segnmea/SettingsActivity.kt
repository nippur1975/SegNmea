package com.example.segnmea

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.example.segnmea.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = getString(R.string.settings)

        val sharedPreferences = getSharedPreferences("Settings", Context.MODE_PRIVATE)
        val channel = sharedPreferences.getString("channel", "3002133")
        val pitchAlarm = sharedPreferences.getInt("pitchAlarm", 30)
        val rollAlarm = sharedPreferences.getInt("rollAlarm", 30)

        // Asignar valores iniciales a los controles
        binding.channelEditText.setText(channel)
        binding.pitchSeekBar.progress = pitchAlarm
        binding.rollSeekBar.progress = rollAlarm

        // Guardar al hacer clic en "Guardar"
        binding.saveButton.setOnClickListener {
            val editor = sharedPreferences.edit()
            editor.putString("channel", binding.channelEditText.text.toString())
            editor.putInt("pitchAlarm", binding.pitchSeekBar.progress)
            editor.putInt("rollAlarm", binding.rollSeekBar.progress)
            editor.apply()
            finish()
        }
    }
}
