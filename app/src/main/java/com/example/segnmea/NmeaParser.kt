package com.example.segnmea

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class NmeaData(
    val time: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val speed: Double = 0.0, // Knots
    val heading: Double = 0.0,
    val date: String = "",
    val pitch: Double = 0.0,
    val roll: Double = 0.0,
    val isValid: Boolean = false
)

class NmeaParser {

    private var currentPitch = 0.0
    private var currentRoll = 0.0

    // Parses a GPRMC sentence
    // $GPRMC,123519,A,4807.038,N,01131.000,E,022.4,084.4,230394,003.1,W*6A
    fun parseGPRMC(sentence: String): NmeaData? {
        val parts = sentence.split(",")
        if (parts.size < 10) return null

        try {
            val status = parts[2]
            if (status != "A") return null // A = Active/Valid, V = Void

            val time = parts[1] // HHMMSS
            val latRaw = parts[3]
            val latDir = parts[4]
            val lonRaw = parts[5]
            val lonDir = parts[6]
            val speedRaw = parts[7]
            val headingRaw = parts[8]
            val date = parts[9] // DDMMYY

            val lat = convertToDecimalDegrees(latRaw, latDir)
            val lon = convertToDecimalDegrees(lonRaw, lonDir)
            val speed = speedRaw.toDoubleOrNull() ?: 0.0
            val heading = headingRaw.toDoubleOrNull() ?: 0.0

            return NmeaData(
                time = time,
                latitude = lat,
                longitude = lon,
                speed = speed,
                heading = heading,
                date = date,
                pitch = currentPitch,
                roll = currentRoll,
                isValid = true
            )

        } catch (e: Exception) {
            Log.e("NmeaParser", "Error parsing GPRMC: ${e.message}")
            return null
        }
    }

    // Parses IIXDR or custom sentence for Pitch and Roll
    // Example: $IIXDR,A,10.5,D,PITCH,A,-5.2,D,ROLL*Checksum
    // Or maybe the user has a custom format. "enviado desde datos nmea desde compas a un esp32"
    // Assuming standard XDR or a simple format.
    // If the memory says "custom/IIXDR", I'll try to handle IIXDR.
    // XDR format: $IIXDR,T,x.x,U,ID,T,x.x,U,ID...
    // T=Type (A=Angular displacement), x.x=Data, U=Units (D=Degrees), ID=Id of sensor
    fun parseIIXDR(sentence: String): Boolean {
        // $IIXDR,A,10.0,D,PITCH,A,5.0,D,ROLL*CS
        val parts = sentence.split(",")
        var updated = false
        try {
            for (i in 1 until parts.size step 4) {
                if (i + 3 >= parts.size) break
                val type = parts[i]
                val value = parts[i+1].toDoubleOrNull()
                // val unit = parts[i+2]
                val id = parts[i+3]

                if (value != null && type == "A") {
                   if (id.contains("PITCH")) {
                       currentPitch = value
                       updated = true
                   } else if (id.contains("ROLL")) {
                       currentRoll = value
                       updated = true
                   }
                }
            }
        } catch (e: Exception) {
            Log.e("NmeaParser", "Error parsing IIXDR: ${e.message}")
        }
        return updated
    }

    private fun convertToDecimalDegrees(raw: String, direction: String): Double {
        if (raw.isEmpty()) return 0.0
        // raw format: DDMM.MMMM or DDDMM.MMMM
        val decimalPointIndex = raw.indexOf('.')
        if (decimalPointIndex == -1) return 0.0

        // Degrees are everything before the last 2 digits before the decimal point
        val degreesEndIndex = decimalPointIndex - 2
        val degrees = raw.substring(0, degreesEndIndex).toDoubleOrNull() ?: 0.0
        val minutes = raw.substring(degreesEndIndex).toDoubleOrNull() ?: 0.0

        var decimal = degrees + (minutes / 60.0)
        if (direction == "S" || direction == "W") {
            decimal = -decimal
        }
        return decimal
    }
}
