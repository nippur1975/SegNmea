package com.example.segnmea

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class BoatData(
    var latitude: Double? = null,
    var longitude: Double? = null,
    var speed: Double? = null,
    var heading: Double? = null,
    var pitch: Double? = null,
    var roll: Double? = null,
    var timestamp: Long = System.currentTimeMillis()
)

class NmeaParser {

    private val boatData = BoatData()

    fun parse(line: String): BoatData {
        if (!validateChecksum(line)) {
            return boatData
        }

        val cleanLine = if (line.contains("*")) line.substringBefore("*") else line
        val parts = cleanLine.split(",")

        if (parts.isEmpty()) return boatData

        val type = parts[0]

        // Handle GPRMC or GNRMC
        if (type.endsWith("RMC")) {
            parseRMC(parts)
        }
        // Handle IIXDR (Transducer measurements) for Pitch/Roll
        else if (type.endsWith("XDR")) {
            parseXDR(parts)
        }

        return boatData
    }

    private fun parseRMC(parts: List<String>) {
        // $GPRMC,123519,A,4807.038,N,01131.000,E,022.4,084.4,230394,003.1,W*6A
        // 0      1      2 3        4 5         6 7     8     9      10    11
        if (parts.size < 10) return

        val status = parts[2]
        if (status == "A") {
            // Latitude
            val latRaw = parts[3].toDoubleOrNull()
            val latDir = parts[4]
            if (latRaw != null) {
                boatData.latitude = convertToDecimalDegrees(latRaw, latDir)
            }

            // Longitude
            val lonRaw = parts[5].toDoubleOrNull()
            val lonDir = parts[6]
            if (lonRaw != null) {
                boatData.longitude = convertToDecimalDegrees(lonRaw, lonDir)
            }

            // Speed (Knots)
            boatData.speed = parts[7].toDoubleOrNull()

            // Heading (Track angle)
            boatData.heading = parts[8].toDoubleOrNull()

            // Date/Time parsing could be added here if needed to sync clock
        }
    }

    private fun parseXDR(parts: List<String>) {
        // $IIXDR,A,10.5,D,PITCH,A,-2.3,D,ROLL
        // Structure: Type, Data, Unit, ID (Repeatable)
        // Start from index 1
        var i = 1
        while (i + 3 < parts.size) {
            val type = parts[i] // Transducer type (A=Angle)
            val data = parts[i+1].toDoubleOrNull()
            val unit = parts[i+2] // D=Degrees
            val id = parts[i+3] // ID (PITCH, ROLL)

            if (data != null) {
                if (id.equals("PITCH", ignoreCase = true)) {
                    boatData.pitch = data
                } else if (id.equals("ROLL", ignoreCase = true)) {
                    boatData.roll = data
                }
            }
            i += 4
        }
    }

    private fun convertToDecimalDegrees(raw: Double, direction: String): Double {
        // raw format: DDMM.MMMM
        val degrees = (raw / 100).toInt()
        val minutes = raw - (degrees * 100)
        var decimal = degrees + (minutes / 60.0)

        if (direction == "S" || direction == "W") {
            decimal = -decimal
        }
        return decimal
    }

    private fun validateChecksum(sentence: String): Boolean {
        if (!sentence.startsWith("$") || !sentence.contains("*")) return false
        val sumPart = sentence.substring(1, sentence.indexOf("*"))
        val checkPart = sentence.substring(sentence.indexOf("*") + 1).trim()

        var calculated = 0
        for (char in sumPart) {
            calculated = calculated xor char.code
        }

        return try {
            val given = checkPart.take(2).toInt(16)
            calculated == given
        } catch (e: Exception) {
            false
        }
    }
}
