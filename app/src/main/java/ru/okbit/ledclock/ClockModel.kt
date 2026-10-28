package ru.okbit.ledclock

import java.util.Calendar
import kotlin.math.*

/** Domain policy: independent of Android, UTC offsets and sensor availability. */
object ClockPolicy {
    fun isNight(minute: Int, start: Int, end: Int): Boolean = when {
        start == end -> true // Explicit full-day night profile.
        start < end -> minute >= start && minute < end
        else -> minute >= start || minute < end
    }
    fun isNight(now: Calendar, s: ClockSettings): Boolean = s.nightEnabled &&
        isNight(now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE), s.nightStart, s.nightEnd)
    fun sensorBrightness(lux: Float): Float =
        (0.015f + ln(1f + lux.coerceAtLeast(0f)) / ln(10001f) * 0.985f).coerceIn(0.01f, 1f)
    fun brightness(s: ClockSettings, night: Boolean, lux: Float?, override: Float?): Float {
        val base = override ?: if (s.autoBrightness && lux != null) sensorBrightness(lux) else s.brightness
        return base.coerceIn(0.01f, if (night) s.nightMax else 1f)
    }
    fun nextTick(now: Long, seconds: Boolean, blink: Boolean, nightBoundary: Long): Long {
        val interval = if (seconds || blink) 1000L else 60000L
        return min(interval - ((now % interval + interval) % interval), nightBoundary).coerceAtLeast(1L)
    }
    fun colorBetween(a: Int, b: Int, amount: Float): Int {
        val t = amount.coerceIn(0f, 1f)
        fun channel(shift: Int): Int = (((a ushr shift and 255) * (1 - t) + (b ushr shift and 255) * t).roundToInt()) shl shift
        return (0xff000000L.toInt() or channel(16) or channel(8) or channel(0))
    }
    fun time(now: Calendar, hour24: Boolean, seconds: Boolean): String {
        var h = now.get(Calendar.HOUR_OF_DAY)
        if (!hour24) h = (h % 12).let { if (it == 0) 12 else it }
        return "%02d:%02d".format(java.util.Locale.US, h, now.get(Calendar.MINUTE)) +
            if (seconds) ":%02d".format(java.util.Locale.US, now.get(Calendar.SECOND)) else ""
    }
}

data class VisualSettings(
    var style: Int = 0, var color: Int = 0xff32d74b.toInt(), var size: Float = 0.86f,
    var inactive: Float = 0.06f, var hour24: Boolean = true, var seconds: Boolean = false,
    var date: Boolean = false, var weekday: Boolean = false, var battery: Boolean = true,
    var blink: Boolean = true
)
data class Preset(var name: String, val visual: VisualSettings)
data class ClockSettings(
    var visual: VisualSettings = VisualSettings(), var brightness: Float = 0.5f,
    var autoBrightness: Boolean = false, var dim: Float = 0f,
    var nightEnabled: Boolean = true, var nightStart: Int = 22 * 60, var nightEnd: Int = 7 * 60,
    var nightColor: Int = 0xffffb340.toInt(), var nightMax: Float = 0.12f,
    var oled: Boolean = true, var language: String = "ru", var firstRun: Boolean = true,
    var tipsShown: Boolean = false, val presets: MutableList<Preset> = mutableListOf()
)
