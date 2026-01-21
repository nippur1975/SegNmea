package com.example.segnmea

import org.junit.Assert.assertEquals
import org.junit.Test

class NmeaParserTest {

    private val parser = NmeaParser()

    @Test
    fun testParseRMC() {
        val rmc = "\$GPRMC,123519,A,4807.038,N,01131.000,E,022.4,084.4,230394,003.1,W*6A"
        val data = parser.parse(rmc)

        // 4807.038 -> 48 + 7.038/60 = 48.1173
        // 01131.000 -> 11 + 31.000/60 = 11.51666...

        assertEquals(48.1173, data.lat, 0.001)
        assertEquals(11.5166, data.lon, 0.001)
        assertEquals(22.4, data.speed, 0.01)
        assertEquals(84.4, data.heading, 0.01)
    }

    @Test
    fun testParseXDR() {
        val xdr = "\$IIXDR,A,10.5,D,PITCH,A,-2.3,D,ROLL*CS"
        val data = parser.parse(xdr)

        assertEquals(10.5, data.pitch, 0.01)
        assertEquals(-2.3, data.roll, 0.01)
    }
}
