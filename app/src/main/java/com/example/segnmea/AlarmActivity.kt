package com.example.segnmea

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.SeekBar
import com.example.segnmea.databinding.ActivityAlarmBinding

class AlarmActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlarmBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAlarmBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = getString(R.string.settings_alarms)

        val sharedPreferences = getSharedPreferences("Settings", Context.MODE_PRIVATE)
        val pitchAlarm = sharedPreferences.getInt("pitchAlarm", 30)
        val rollAlarm = sharedPreferences.getInt("rollAlarm", 30)

        binding.pitchSeekBar.progress = pitchAlarm
        binding.rollSeekBar.progress = rollAlarm
        binding.pitchValueTextView.text = pitchAlarm.toString()
        binding.rollValueTextView.text = rollAlarm.toString()

        binding.pitchSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                binding.pitchValueTextView.text = progress.toString()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.rollSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                binding.rollValueTextView.text = progress.toString()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.saveButton.setOnClickListener {
            val editor = sharedPreferences.edit()
            editor.putInt("pitchAlarm", binding.pitchSeekBar.progress)
            editor.putInt("rollAlarm", binding.rollSeekBar.progress)
            editor.apply()

            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
}
