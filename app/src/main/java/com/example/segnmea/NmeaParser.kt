package com.example.segnmea

import android.util.Log

data class NmeaData(
    var latitude: Double? = null,
    var longitude: Double? = null,
    var speedKnots: Double? = null,
    var courseHeading: Double? = null,
    var pitch: Double? = null,
    var roll: Double? = null,
    var timestamp: Long = System.currentTimeMillis()
)

class NmeaParser {

    private val currentData = NmeaData()

    // Example GPRMC: $GPRMC,123519,A,4807.038,N,01131.000,E,022.4,084.4,230394,003.1,W*6A
    // Example IIXDR (Pitch/Roll): $IIXDR,A,10.5,D,PITCH,A,-2.3,D,ROLL*Checksum

    fun parse(line: String): NmeaData {
        if (!line.startsWith("$")) return currentData

        // Remove checksum if exists
        val cleanLine = if (line.contains("*")) line.split("*")[0] else line
        val parts = cleanLine.split(",")

        try {
            when (parts[0]) {
                "\$GPRMC", "\$GNRMC" -> parseRMC(parts)
                "\$GPGGA", "\$GNGGA" -> parseGGA(parts)
                "\$IIXDR" -> parseXDR(parts)
            }
        } catch (e: Exception) {
            Log.e("NmeaParser", "Error parsing line: $line", e)
        }

        return currentData
    }

    private fun parseRMC(parts: List<String>) {
        // Status A=Active, V=Void
        if (parts.size > 2 && parts[2] == "A") {
            // Latitude
            if (parts.size > 4 && parts[3].isNotEmpty()) {
                currentData.latitude = convertToDecimal(parts[3], parts[4])
            }
            // Longitude
            if (parts.size > 6 && parts[5].isNotEmpty()) {
                currentData.longitude = convertToDecimal(parts[5], parts[6])
            }
            // Speed
            if (parts.size > 7 && parts[7].isNotEmpty()) {
                currentData.speedKnots = parts[7].toDoubleOrNull()
            }
            // Course/Heading
            if (parts.size > 8 && parts[8].isNotEmpty()) {
                currentData.courseHeading = parts[8].toDoubleOrNull()
            }
        }
    }

    private fun parseGGA(parts: List<String>) {
        // Fix Quality: 0 = Invalid
        if (parts.size > 6 && parts[6] != "0") {
             // Latitude
            if (parts.size > 3 && parts[2].isNotEmpty()) {
                currentData.latitude = convertToDecimal(parts[2], parts[3])
            }
            // Longitude
            if (parts.size > 5 && parts[4].isNotEmpty()) {
                currentData.longitude = convertToDecimal(parts[4], parts[5])
            }
        }
    }

    private fun parseXDR(parts: List<String>) {
        // $IIXDR,A,x.x,D,PITCH,A,y.y,D,ROLL
        // Loop through parts to find PITCH and ROLL labels
        for (i in parts.indices) {
            if (parts[i] == "PITCH") {
                // Value is usually at i-2
                if (i >= 2) {
                    currentData.pitch = parts[i-2].toDoubleOrNull()
                }
            }
            if (parts[i] == "ROLL") {
                if (i >= 2) {
                    currentData.roll = parts[i-2].toDoubleOrNull()
                }
            }
        }
    }

    private fun convertToDecimal(value: String, direction: String): Double? {
        // Format: DDMM.MMMM
        if (value.length < 4) return null

        val decimalPointIndex = value.indexOf('.')
        if (decimalPointIndex == -1) return null

        // Degrees are everything before the last 2 digits of the integer part
        val degreesStr = value.substring(0, decimalPointIndex - 2)
        val minutesStr = value.substring(decimalPointIndex - 2)

        var degrees = degreesStr.toDoubleOrNull() ?: return null
        val minutes = minutesStr.toDoubleOrNull() ?: return null

        var decimal = degrees + (minutes / 60.0)

        if (direction == "S" || direction == "W") {
            decimal = -decimal
        }

        return decimal
    }
}
