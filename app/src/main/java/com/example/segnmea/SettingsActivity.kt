package com.example.segnmea

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import kotlinx.android.synthetic.main.activity_settings.*

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        title = getString(R.string.settings)

        val sharedPreferences = getSharedPreferences("Settings", Context.MODE_PRIVATE)
        val channel = sharedPreferences.getString("channel", "3002133")
        val pitchAlarm = sharedPreferences.getInt("pitchAlarm", 30)
        val rollAlarm = sharedPreferences.getInt("rollAlarm", 30)

        channelEditText.setText(channel)
        pitchSeekBar.progress = pitchAlarm
        rollSeekBar.progress = rollAlarm

        saveButton.setOnClickListener {
            val editor = sharedPreferences.edit()
            editor.putString("channel", channelEditText.text.toString())
            editor.putInt("pitchAlarm", pitchSeekBar.progress)
            editor.putInt("rollAlarm", rollSeekBar.progress)
            editor.apply()
            finish()
        }
    }
}