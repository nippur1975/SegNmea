package com.example.segnmea

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class NmeaData(
    var latitude: Double? = null,
    var longitude: Double? = null,
    var speed: Double? = null,
    var heading: Double? = null,
    var pitch: Double? = null,
    var roll: Double? = null,
    var timestamp: String? = null
)

class NmeaParser {

    fun parse(sentence: String): NmeaData {
        val data = NmeaData()
        if (!sentence.startsWith("$")) return data

        // Remove checksum if present
        val cleanSentence = if (sentence.contains("*")) sentence.split("*")[0] else sentence
        val parts = cleanSentence.split(",")

        try {
            when (parts[0]) {
                "\$GPRMC", "\$GNRMC" -> parseRMC(parts, data)
                "\$GPGGA", "\$GNGGA" -> parseGGA(parts, data)
                "\$HCHDG", "\$HCHDT" -> parseHeading(parts, data)
                "\$IIXDR" -> parseXDR(parts, data)
                // Custom formats or others can be added here
            }

            // Fallback/Custom parsing for Pitch/Roll if not standard
            // Expecting something like "$...PITCH,10.5,ROLL,-2.3..." if not XDR
            if (data.pitch == null || data.roll == null) {
                 parseCustomPitchRoll(cleanSentence, data)
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
        return data
    }

    private fun parseRMC(parts: List<String>, data: NmeaData) {
        // $GPRMC,123519,A,4807.038,N,01131.000,E,022.4,084.4,230394,003.1,W*6A
        // 1: Time, 2: Status, 3: Lat, 4: N/S, 5: Lon, 6: E/W, 7: Speed, 8: Track, 9: Date
        if (parts.size > 9 && parts[2] == "A") {
            data.latitude = convertToDecimal(parts[3], parts[4])
            data.longitude = convertToDecimal(parts[5], parts[6])
            data.speed = parts[7].toDoubleOrNull()
            data.heading = parts[8].toDoubleOrNull()

            // Construct Timestamp
            val time = parts[1]
            val date = parts[9]
            if (time.length >= 6 && date.length == 6) {
                // simple construction, ignoring ms
                // Date: DDMMYY, Time: HHMMSS
                // Output format expected by App: YYYY-MM-DD HH:MM:SS (ThingSpeak format usually)
                // But TrackPoint seems to just hold a string.
                // ThingSpeak format: 2023-10-27T10:00:00Z
                try {
                     val day = date.substring(0, 2)
                     val month = date.substring(2, 4)
                     val year = "20" + date.substring(4, 6)
                     val hour = time.substring(0, 2)
                     val min = time.substring(2, 4)
                     val sec = time.substring(4, 6)
                     data.timestamp = "$year-$month-${day}T$hour:$min:${sec}Z"
                } catch (e: Exception) {}
            }
        }
    }

    private fun parseGGA(parts: List<String>, data: NmeaData) {
        // $GPGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,*47
        // 2: Lat, 3: N/S, 4: Lon, 5: E/W
        if (parts.size > 5) {
             val lat = convertToDecimal(parts[2], parts[3])
             val lon = convertToDecimal(parts[4], parts[5])
             if (lat != 0.0 || lon != 0.0) {
                 data.latitude = lat
                 data.longitude = lon
             }
        }
    }

    private fun parseHeading(parts: List<String>, data: NmeaData) {
         // $HCHDT,123.4,T
         if (parts.size > 1) {
             data.heading = parts[1].toDoubleOrNull()
         }
    }

    private fun parseXDR(parts: List<String>, data: NmeaData) {
        // $IIXDR,A,10.5,D,PITCH,A,-2.3,D,ROLL
        // Look for "PITCH" and "ROLL" in the parts and take the value before it (or associated with it)
        // Standard XDR: Type, Data, Units, Name
        for (i in 0 until parts.size - 3 step 4) {
             if (i + 4 < parts.size) {
                 val name = parts[i + 4]
                 val value = parts[i + 2].toDoubleOrNull()
                 if (name.contains("PITCH", ignoreCase = true)) {
                     data.pitch = value
                 } else if (name.contains("ROLL", ignoreCase = true)) {
                     data.roll = value
                 }
             }
        }
    }

    private fun parseCustomPitchRoll(sentence: String, data: NmeaData) {
        // Naive parsing for "PITCH:x.x" or "ROLL:y.y"
        // Regex might be cleaner
        val pitchRegex = Regex("PITCH[:= ]?([0-9.-]+)", RegexOption.IGNORE_CASE)
        val rollRegex = Regex("ROLL[:= ]?([0-9.-]+)", RegexOption.IGNORE_CASE)

        pitchRegex.find(sentence)?.groups?.get(1)?.value?.toDoubleOrNull()?.let {
            data.pitch = it
        }
        rollRegex.find(sentence)?.groups?.get(1)?.value?.toDoubleOrNull()?.let {
            data.roll = it
        }
    }

    private fun convertToDecimal(coordinate: String, direction: String): Double {
        if (coordinate.isEmpty()) return 0.0
        try {
            // format ddmm.mmmm
            val decimalPointIndex = coordinate.indexOf('.')
            if (decimalPointIndex == -1 || decimalPointIndex < 2) return 0.0

            val degreesStr = coordinate.substring(0, decimalPointIndex - 2)
            val minutesStr = coordinate.substring(decimalPointIndex - 2)

            val degrees = degreesStr.toDouble()
            val minutes = minutesStr.toDouble()

            var decimal = degrees + (minutes / 60.0)
            if (direction == "S" || direction == "W") {
                decimal = -decimal
            }
            return decimal
        } catch (e: Exception) {
            return 0.0
        }
    }
}
