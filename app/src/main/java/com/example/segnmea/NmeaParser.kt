package com.example.segnmea

import android.util.Log

class NmeaParser {

    data class ParsedData(
        var lat: Double = 0.0,
        var lon: Double = 0.0,
        var speed: Double = 0.0,
        var heading: Double = 0.0,
        var pitch: Double = 0.0,
        var roll: Double = 0.0
    )

    private val currentData = ParsedData()
    private var buffer = ""

    fun parse(data: String): Boolean {
        buffer += data
        var updated = false
        if (buffer.contains("\n")) {
            val lines = buffer.split("\n")
            // Process all complete lines
            for (i in 0 until lines.size - 1) {
                if (processLine(lines[i].trim())) {
                    updated = true
                }
            }
            // Keep the last fragment
            buffer = lines.last()
        }
        return updated
    }

    fun getData(): ParsedData {
        return currentData
    }

    private fun processLine(line: String): Boolean {
        if (!line.startsWith("$")) return

        // Basic checksum validation could be added here

        val parts = line.split(",") // NMEA fields are comma separated
        if (parts.isEmpty()) return false

        try {
            when {
                // $GPRMC,123519,A,4807.038,N,01131.000,E,022.4,084.4,230394,003.1,W*6A
                line.startsWith("\$GPRMC") || line.startsWith("\$GNRMC") -> {
                    if (parts.size > 8 && parts[2] == "A") { // Status A = Active
                        val rawLat = parts[3].toDoubleOrNull()
                        val latDir = parts[4]
                        val rawLon = parts[5].toDoubleOrNull()
                        val lonDir = parts[6]
                        val speedKnots = parts[7].toDoubleOrNull()
                        val trackAngle = parts[8].toDoubleOrNull()

                        if (rawLat != null && rawLon != null) {
                            currentData.lat = convertToDecimalDegrees(rawLat, latDir)
                            currentData.lon = convertToDecimalDegrees(rawLon, lonDir)
                        }
                        if (speedKnots != null) currentData.speed = speedKnots
                        if (trackAngle != null) currentData.heading = trackAngle
                    }
                }
                // Custom Sentence for Pitch/Roll
                // Assuming format: $IIXDR,A,10.5,D,PITCH,A,-2.3,D,ROLL,... or similar
                // Or user provided: "nmea data from compas to esp32"
                // Common proprietary: $PFEC,GPatt,pitch,roll,heading*CS
                // Or simply $IIXDR (Transducer measurement)
                line.contains("XDR") -> {
                    var found = false
                    // Try to find PITCH and ROLL labels and get the preceding value
                    // Example: $IIXDR,A,-10.5,D,PITCH,A,2.3,D,ROLL*hh
                    for (i in 0 until parts.size) {
                        if (parts[i] == "PITCH" && i >= 2) {
                             currentData.pitch = parts[i-2].toDoubleOrNull() ?: 0.0
                             found = true
                        }
                        if (parts[i] == "ROLL" && i >= 2) {
                             currentData.roll = parts[i-2].toDoubleOrNull() ?: 0.0
                             found = true
                        }
                    }
                    if (found) return true
                }
                // Fallback or other formats.
                // Sometimes pitch/roll comes in $P... proprietary sentences.
                // Let's assume a simple custom one if XDR fails or generic $PITCH,val,ROLL,val
                line.startsWith("\$PITCH") -> {
                    // hypothetical $PITCH,10.5,ROLL,-2.3
                    if (parts.size >= 2) currentData.pitch = parts[1].toDoubleOrNull() ?: 0.0
                    if (parts.size >= 4 && parts[2] == "ROLL") currentData.roll = parts[3].toDoubleOrNull() ?: 0.0
                    return true
                }
            }
        } catch (e: Exception) {
            Log.e("NmeaParser", "Error parsing line: $line", e)
        }
        return false
    }

    private fun convertToDecimalDegrees(nmeaPos: Double, quadrant: String): Double {
        val degrees = (nmeaPos / 100).toInt()
        val minutes = nmeaPos - (degrees * 100)
        var decimal = degrees + (minutes / 60)
        if (quadrant == "S" || quadrant == "W") {
            decimal = -decimal
        }
        return decimal
    }
}
