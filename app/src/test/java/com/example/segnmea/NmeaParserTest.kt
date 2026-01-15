package com.example.segnmea

import org.junit.Test
import org.junit.Assert.*

class NmeaParserTest {

    private val parser = NmeaParser()

    @Test
    fun testParseGPRMC() {
        // $GPRMC,123519,A,4807.038,N,01131.000,E,022.4,084.4,230394,003.1,W*6A
        // 4807.038 = 48 deg + 07.038 min = 48 + 0.1173 = 48.1173
        // 01131.000 = 11 deg + 31.000 min = 11 + 0.51666 = 11.51666
        val sentence = "\$GPRMC,123519,A,4807.038,N,01131.000,E,022.4,084.4,230394,003.1,W*6A"
        val data = parser.parse(sentence)

        assertNotNull(data.latitude)
        assertEquals(48.1173, data.latitude!!, 0.0001)
        assertEquals(11.5166, data.longitude!!, 0.0001)
        assertEquals(22.4, data.speed!!, 0.1)
        assertEquals(84.4, data.heading!!, 0.1)
    }

    @Test
    fun testParseCustomPitchRoll() {
        val sentence = "\$IIXDR,A,10.5,D,PITCH,A,-2.3,D,ROLL"
        val data = parser.parse(sentence)
        assertEquals(10.5, data.pitch!!, 0.1)
        assertEquals(-2.3, data.roll!!, 0.1)
    }

    @Test
    fun testParseCustomString() {
        val sentence = "SOMETHING,PITCH:5.5,ROLL:-1.2"
        val data = parser.parse(sentence)
        assertEquals(5.5, data.pitch!!, 0.1)
        assertEquals(-1.2, data.roll!!, 0.1)
    }
}
