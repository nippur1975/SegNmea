package com.example.segnmea

import java.util.Locale

data class NmeaData(
    var lat: Double = 0.0,
    var lon: Double = 0.0,
    var speed: Double = 0.0,
    var heading: Double = 0.0,
    var pitch: Double = 0.0,
    var roll: Double = 0.0,
    var hasValidPosition: Boolean = false
)

class NmeaParser {

    private val currentData = NmeaData()
    private val buffer = StringBuilder()

    fun parse(data: String): NmeaData? {
        buffer.append(data)

        var newlineIndex = buffer.indexOf('\n')
        while (newlineIndex != -1) {
            val line = buffer.substring(0, newlineIndex).trim()
            buffer.delete(0, newlineIndex + 1)

            if (line.isNotEmpty()) {
                parseSentence(line)
            }

            newlineIndex = buffer.indexOf('\n')
        }

        // Return a copy of current data if it has minimal validity,
        // effectively providing the latest state.
        return currentData.copy()
    }

    private fun parseSentence(sentence: String) {
        if (!sentence.startsWith("$")) return
        // Basic checksum validation could be added here

        val parts = sentence.split(",")
        if (parts.isEmpty()) return

        when {
            sentence.startsWith("\$GPRMC") || sentence.startsWith("\$GNRMC") -> parseRMC(parts)
            sentence.startsWith("\$IIXDR") -> parseXDR(parts)
            sentence.startsWith("\$PITCH") -> parseCustomPitchRoll(parts) // Custom fallback
        }
    }

    private fun parseRMC(parts: List<String>) {
        // $GPRMC,123519,A,4807.038,N,01131.000,E,022.4,084.4,230394,003.1,W*6A
        // 0: ID
        // 1: Time
        // 2: Status (A=Active, V=Void)
        // 3: Lat
        // 4: N/S
        // 5: Lon
        // 6: E/W
        // 7: Speed in knots
        // 8: Track angle
        try {
            if (parts.size > 8 && parts[2] == "A") {
                val latRaw = parts[3].toDoubleOrNull()
                val latDir = parts[4]
                val lonRaw = parts[5].toDoubleOrNull()
                val lonDir = parts[6]
                val speed = parts[7].toDoubleOrNull() ?: 0.0
                val heading = parts[8].toDoubleOrNull() ?: 0.0

                if (latRaw != null && lonRaw != null) {
                    currentData.lat = convertNmeaToDecimal(latRaw, latDir)
                    currentData.lon = convertNmeaToDecimal(lonRaw, lonDir)
                    currentData.speed = speed
                    currentData.heading = heading
                    currentData.hasValidPosition = true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun parseXDR(parts: List<String>) {
        // $IIXDR,A,10.5,D,PITCH,A,-2.3,D,ROLL,*CS
        // XDR can contain multiple measurements. We scan 4-tuples: Type, Data, Unit, ID.
        try {
            var i = 1
            while (i + 3 < parts.size) {
                // val type = parts[i] // e.g., 'A' for Angle
                val valueStr = parts[i+1]
                // val unit = parts[i+2] // e.g., 'D' for Degrees
                val id = parts[i+3]

                val value = valueStr.toDoubleOrNull()
                if (value != null) {
                    if (id.contains("PITCH", ignoreCase = true)) {
                        currentData.pitch = value
                    } else if (id.contains("ROLL", ignoreCase = true)) {
                        currentData.roll = value
                    }
                }
                i += 4
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Fallback for simple custom sentences like $PITCH,10.5,ROLL,-2.1
    private fun parseCustomPitchRoll(parts: List<String>) {
        try {
            for (i in 0 until parts.size - 1) {
                if (parts[i].contains("PITCH", ignoreCase = true)) {
                    parts[i+1].toDoubleOrNull()?.let { currentData.pitch = it }
                }
                if (parts[i].contains("ROLL", ignoreCase = true)) {
                    parts[i+1].toDoubleOrNull()?.let { currentData.roll = it }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun convertNmeaToDecimal(raw: Double, direction: String): Double {
        val degrees = (raw / 100).toInt()
        val minutes = raw - (degrees * 100)
        var decimal = degrees + (minutes / 60)
        if (direction == "S" || direction == "W") {
            decimal *= -1
        }
        return decimal
    }
}
