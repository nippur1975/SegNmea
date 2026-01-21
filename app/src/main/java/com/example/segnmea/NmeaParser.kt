package com.example.segnmea

data class NmeaData(
    var lat: Double = 0.0,
    var lon: Double = 0.0,
    var speed: Double = 0.0,
    var heading: Double = 0.0,
    var pitch: Double = 0.0,
    var roll: Double = 0.0,
    var timestamp: String = ""
)

class NmeaParser {

    private val currentData = NmeaData()

    fun parse(line: String): NmeaData {
        try {
            if (!line.startsWith("$") || !line.contains("*")) {
                return currentData
            }

            // Basic checksum verification could go here

            val content = line.substringBefore("*")
            val parts = content.split(",")

            if (parts.isEmpty()) return currentData

            when (parts[0]) {
                "\$GPRMC", "\$GNRMC" -> parseRMC(parts)
                "\$IIXDR" -> parseXDR(parts)
                // Add proprietary sentences if needed, e.g. $P...
            }
        } catch (e: Exception) {
            println("NmeaParser Error: $line - ${e.message}")
        }
        return currentData
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
        // 7: Speed (knots)
        // 8: Track angle (True)
        // 9: Date

        if (parts.size < 10) return

        if (parts[2] == "A") {
            // Lat
            val latRaw = parts[3].toDoubleOrNull()
            val latDir = parts[4]
            if (latRaw != null) {
                currentData.lat = convertToDegrees(latRaw, latDir)
            }

            // Lon
            val lonRaw = parts[5].toDoubleOrNull()
            val lonDir = parts[6]
            if (lonRaw != null) {
                currentData.lon = convertToDegrees(lonRaw, lonDir)
            }

            // Speed
            currentData.speed = parts[7].toDoubleOrNull() ?: 0.0

            // Heading
            currentData.heading = parts[8].toDoubleOrNull() ?: 0.0

            // Timestamp (just raw time for now or construct full date)
            currentData.timestamp = parts[9] + parts[1] // Date + Time
        }
    }

    private fun parseXDR(parts: List<String>) {
        // $IIXDR,A,10.5,D,PITCH,A,-2.3,D,ROLL*...
        // Format: Type, Data, Unit, Name, ... repeating group of 4

        for (i in 1 until parts.size step 4) {
            if (i + 3 >= parts.size) break

            val type = parts[i] // A = Angle
            val value = parts[i+1].toDoubleOrNull()
            // val unit = parts[i+2] // D = Degrees
            val name = parts[i+3] // PITCH or ROLL

            if (value != null) {
                when (name.uppercase()) {
                    "PITCH" -> currentData.pitch = value
                    "ROLL" -> currentData.roll = value
                }
            }
        }
    }

    private fun convertToDegrees(raw: Double, direction: String): Double {
        // NMEA format: DDMM.mmmm
        val degrees = (raw / 100).toInt()
        val minutes = raw - (degrees * 100)
        var decimal = degrees + (minutes / 60.0)

        if (direction == "S" || direction == "W") {
            decimal = -decimal
        }
        return decimal
    }
}
