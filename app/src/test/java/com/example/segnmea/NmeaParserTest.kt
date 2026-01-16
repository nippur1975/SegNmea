package com.example.segnmea

import org.junit.Test
import org.junit.Assert.*

class NmeaParserTest {

    private val parser = NmeaParser()

    @Test
    fun testParseGPRMC() {
        // $GPRMC,123519,A,4807.038,N,01131.000,E,022.4,084.4,230394,003.1,W*6A
        // 48 degrees + 07.038 minutes = 48 + 0.1173 = 48.1173
        // 11 degrees + 31.000 minutes = 11 + 0.5166 = 11.5166
        val nmea = "\$GPRMC,123519,A,4807.038,N,01131.000,E,022.4,084.4,230394,003.1,W*6A"
        val data = parser.parse(nmea)
        assertNotNull(data)
        assertEquals(48.1173, data?.lat ?: 0.0, 0.001)
        assertEquals(11.5166, data?.lon ?: 0.0, 0.001)
        assertEquals(22.4f, data?.speed)
        assertEquals(84.4f, data?.heading)
    }

    @Test
    fun testParseCustomPitchRoll() {
        // $PITCH,10.5,ROLL,-2.3
        val nmea = "\$PITCH,10.5,ROLL,-2.3"
        val data = parser.parse(nmea)
        assertNotNull(data)
        assertEquals(10.5f, data?.pitch)
        assertEquals(-2.3f, data?.roll)
    }

    @Test
    fun testParseIIXDR() {
        // $IIXDR,A,10.5,D,PITCH,A,-2.3,D,ROLL*checksum
        val nmea = "\$IIXDR,A,10.5,D,PITCH,A,-2.3,D,ROLL*12"
        val data = parser.parse(nmea)
        assertNotNull(data)
        assertEquals(10.5f, data?.pitch)
        assertEquals(-2.3f, data?.roll)
    }
}
