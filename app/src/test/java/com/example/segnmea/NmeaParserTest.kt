package com.example.segnmea

import org.junit.Test
import org.junit.Assert.*

class NmeaParserTest {
    private val parser = NmeaParser()

    @Test
    fun testParseGPRMC() {
        val line = "\$GPRMC,123519,A,4807.038,N,01131.000,E,022.4,084.4,230394,003.1,W*6A"
        val data = parser.parse(line)
        assertEquals(48.1173, data.latitude!!, 0.0001)
        assertEquals(11.51666, data.longitude!!, 0.0001)
        assertEquals(22.4, data.speed!!, 0.1)
        assertEquals(84.4, data.heading!!, 0.1)
    }

    @Test
    fun testParseIIXDR() {
        // "IIXDR,A,10.5,D,PITCH,A,-2.3,D,ROLL" checksum is 0D
        val line = "\$IIXDR,A,10.5,D,PITCH,A,-2.3,D,ROLL*0D"
        val data = parser.parse(line)
        assertEquals(10.5, data.pitch!!, 0.1)
        assertEquals(-2.3, data.roll!!, 0.1)
    }
}
