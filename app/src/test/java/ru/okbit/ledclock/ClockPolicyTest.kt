package ru.okbit.ledclock

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ClockPolicyTest {
    @Test fun nightBoundariesAndMidnight() {
        for (minute in 0..1439) assertEquals("minute $minute", minute >= 1320 || minute < 420, ClockPolicy.isNight(minute, 1320, 420))
        assertFalse(ClockPolicy.isNight(419, 420, 1320))
        assertTrue(ClockPolicy.isNight(420, 420, 1320))
        assertFalse(ClockPolicy.isNight(1320, 420, 1320))
        assertTrue(ClockPolicy.isNight(0, 0, 0))
    }
    @Test fun nightCapsSensorManualAndOverride() {
        val s = ClockSettings(nightMax = .12f, brightness = .8f, autoBrightness = true)
        for (lux in listOf(null, 0f, 50f, 10000f, 100000f))
            for (override in listOf(null, .05f, .5f, 1f))
                assertTrue(ClockPolicy.brightness(s, true, lux, override) <= .12f)
        assertEquals(.8f, ClockPolicy.brightness(s, false, null, null), .0001f)
    }
    @Test fun sensorCurveIsBoundedMonotonic() {
        var previous = 0f
        for (i in 0..10000 step 50) {
            val result = ClockPolicy.sensorBrightness(i.toFloat())
            assertTrue(result in .01f..1f); assertTrue(result >= previous); previous = result
        }
    }
    @Test fun clockFormattingNoonAndMidnight() {
        val c = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 7); set(Calendar.SECOND, 3) }
        assertEquals("00:07", ClockPolicy.time(c, true, false))
        assertEquals("12:07:03", ClockPolicy.time(c, false, true))
        c.set(Calendar.HOUR_OF_DAY, 12); assertEquals("12:07", ClockPolicy.time(c, false, false))
        c.set(Calendar.HOUR_OF_DAY, 23); assertEquals("11:07", ClockPolicy.time(c, false, false))
    }
    @Test fun dstUsesLocalWallTime() {
        val tz = TimeZone.getTimeZone("America/New_York")
        val c = Calendar.getInstance(tz).apply { clear(); set(2026, Calendar.NOVEMBER, 1, 1, 30) }
        val s = ClockSettings()
        assertTrue(ClockPolicy.isNight(c, s)); c.add(Calendar.HOUR_OF_DAY, 1); assertTrue(ClockPolicy.isNight(c, s))
        c.set(Calendar.HOUR_OF_DAY, 7); c.set(Calendar.MINUTE, 0); assertFalse(ClockPolicy.isNight(c, s))
    }
    @Test fun tickSchedulingDoesNotPollUnnecessarily() {
        assertEquals(60000L, ClockPolicy.nextTick(120000, false, false, 60000))
        assertEquals(1000L, ClockPolicy.nextTick(120000, true, false, 60000))
        assertEquals(877L, ClockPolicy.nextTick(120123, false, true, 60000))
        assertEquals(50L, ClockPolicy.nextTick(120123, false, false, 50))
    }
}
