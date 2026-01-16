package com.example.segnmea

import java.util.Locale

data class NmeaData(
    val lat: Double? = null,
    val lon: Double? = null,
    val speed: Float? = null,
    val heading: Float? = null,
    val pitch: Float? = null,
    val roll: Float? = null,
    val timestamp: Long? = null
)

class NmeaParser {

    fun parse(line: String): NmeaData? {
        if (!line.startsWith("$")) return null

        // Remove checksum if present
        val content = if (line.contains("*")) line.split("*")[0] else line
        val parts = content.trim().split(",")

        if (parts.isEmpty()) return null

        return when (parts[0]) {
            "\$GPRMC", "\$GNRMC" -> parseRMC(parts)
            "\$IIXDR", "\$PITCH" -> parseXDR(parts) // Added PITCH as potential custom tag fallback
            else -> null
        }
    }

    private fun parseRMC(parts: List<String>): NmeaData? {
        // $GPRMC,time,status,lat,ns,lon,ew,spd,cog,date,mv,mv_ew,mode*cs
        try {
            if (parts.size < 9) return null
            // We allow parsing even if status is V (warning) for testing, but typically A is valid.
            // But let's check basic validity.

            val latRaw = parts[3].toDoubleOrNull()
            val ns = parts[4]
            val lonRaw = parts[5].toDoubleOrNull()
            val ew = parts[6]
            val speedKnots = parts[7].toFloatOrNull()
            val course = parts[8].toFloatOrNull()

            if (latRaw == null || lonRaw == null) return null

            val lat = convertDmToDd(latRaw, ns)
            val lon = convertDmToDd(lonRaw, ew)

            return NmeaData(lat = lat, lon = lon, speed = speedKnots, heading = course, timestamp = System.currentTimeMillis())
        } catch (e: Exception) {
            return null
        }
    }

    private fun parseXDR(parts: List<String>): NmeaData? {
        // Standard: $IIXDR,A,x.x,D,PITCH,A,y.y,D,ROLL*cs
        // Custom simplified: $PITCH,x.x,ROLL,y.y (Hypothetical, handled by loose logic below)

        var pitch: Float? = null
        var roll: Float? = null

        try {
            // Check for custom simplified format first if it starts with $PITCH
            if (parts[0] == "\$PITCH" && parts.size >= 4) {
                 // Assume $PITCH,val,ROLL,val
                 pitch = parts[1].toFloatOrNull()
                 if (parts[2] == "ROLL") {
                     roll = parts[3].toFloatOrNull()
                 }
                 return NmeaData(pitch = pitch, roll = roll)
            }

            // Standard XDR loop
            for (i in 1 until parts.size step 4) {
                if (i + 3 >= parts.size) break
                // val type = parts[i] // A = Angle
                val value = parts[i+1].toFloatOrNull()
                // val units = parts[i+2] // D = Degrees
                val id = parts[i+3]

                if (value != null) {
                    if (id.contains("PITCH", ignoreCase = true) || id == "PTCH") {
                        pitch = value
                    } else if (id.contains("ROLL", ignoreCase = true)) {
                        roll = value
                    }
                }
            }

            if (pitch != null || roll != null) {
                return NmeaData(pitch = pitch, roll = roll)
            }
        } catch (e: Exception) {
            // Ignore
        }
        return null
    }

    private fun convertDmToDd(dm: Double, hemisphere: String): Double {
        val degrees = (dm / 100).toInt()
        val minutes = dm - (degrees * 100)
        val dd = degrees + (minutes / 60.0)
        return if (hemisphere == "S" || hemisphere == "W") -dd else dd
    }
}
