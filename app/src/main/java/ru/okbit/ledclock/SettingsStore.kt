package ru.okbit.ledclock

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Versioned bounded data. Malformed presets are skipped individually. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("clock_settings", Context.MODE_PRIVATE)
    private var futureVersion = false
    fun load(): ClockSettings {
        futureVersion = false
        return try {
            val s = ClockSettings()
            val json = prefs.getString("settings", null) ?: return s
            val o = JSONObject(json)
            // Unknown future versions are left untouched, avoiding destructive migration.
            if (o.optInt("version", 1) != 1) { futureVersion = true; return s }
            s.visual = readVisual(o.optJSONObject("visual") ?: JSONObject())
            s.brightness = number(o, "brightness", .5f, .01f, 1f)
            s.autoBrightness = o.optBoolean("auto", false)
            s.dim = number(o, "dim", 0f, 0f, .9f)
            s.nightEnabled = o.optBoolean("night", true)
            s.nightStart = o.optInt("start", 1320).coerceIn(0, 1439)
            s.nightEnd = o.optInt("end", 420).coerceIn(0, 1439)
            s.nightColor = o.optInt("nightColor", s.nightColor) or 0xff000000.toInt()
            s.nightMax = number(o, "nightMax", .12f, .01f, 1f)
            s.oled = o.optBoolean("oled", true)
            s.language = if (o.optString("language") == "en") "en" else "ru"
            s.firstRun = o.optBoolean("firstRun", true)
            s.tipsShown = o.optBoolean("tips", false)
            val a = o.optJSONArray("presets") ?: JSONArray()
            for (i in 0 until minOf(a.length(), 5)) {
                val p = a.optJSONObject(i) ?: continue
                val v = p.optJSONObject("visual") ?: continue
                val name = p.optString("name").trim().take(40)
                if (name.isNotEmpty()) s.presets.add(Preset(name, readVisual(v)))
            }
            s
        } catch (_: Exception) { ClockSettings() }
    }
    @android.annotation.SuppressLint("ApplySharedPref") // Report write failures instead of silently losing edits.
    fun save(s: ClockSettings): Boolean = if (futureVersion) false else try {
        val a = JSONArray()
        s.presets.take(5).forEach { a.put(JSONObject().put("name", it.name.take(40)).put("visual", visualJson(it.visual))) }
        val o = JSONObject().put("version", 1).put("visual", visualJson(s.visual))
            .put("brightness", s.brightness.toDouble()).put("auto", s.autoBrightness).put("dim", s.dim.toDouble())
            .put("night", s.nightEnabled).put("start", s.nightStart).put("end", s.nightEnd)
            .put("nightColor", s.nightColor).put("nightMax", s.nightMax.toDouble()).put("oled", s.oled)
            .put("language", s.language).put("firstRun", s.firstRun).put("tips", s.tipsShown).put("presets", a)
        prefs.edit().putString("settings", o.toString()).commit()
    } catch (_: Exception) { false }
    private fun number(o: JSONObject, key: String, default: Float, low: Float, high: Float): Float {
        val n = o.optDouble(key, default.toDouble()).toFloat()
        return if (n.isNaN() || n.isInfinite()) default else n.coerceIn(low, high)
    }
    private fun readVisual(o: JSONObject) = VisualSettings(
        style = o.optInt("style", 0).coerceIn(0, 3), color = o.optInt("color", 0xff32d74b.toInt()) or 0xff000000.toInt(),
        size = number(o, "size", .86f, .35f, 1f), inactive = number(o, "inactive", .06f, 0f, .18f),
        hour24 = o.optBoolean("24", true), seconds = o.optBoolean("seconds", false), date = o.optBoolean("date", false),
        weekday = o.optBoolean("weekday", false), battery = o.optBoolean("battery", true), blink = o.optBoolean("blink", true))
    private fun visualJson(v: VisualSettings) = JSONObject().put("style", v.style).put("color", v.color)
        .put("size", v.size.toDouble()).put("inactive", v.inactive.toDouble()).put("24", v.hour24)
        .put("seconds", v.seconds).put("date", v.date).put("weekday", v.weekday).put("battery", v.battery).put("blink", v.blink)
}
