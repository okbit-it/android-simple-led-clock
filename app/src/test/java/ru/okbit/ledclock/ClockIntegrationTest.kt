package ru.okbit.ledclock

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.concurrent.TimeUnit

@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk = [23, 26, 35], qualifiers = "land-hdpi")
open class ClockIntegrationTest {
    private fun context() = RuntimeEnvironment.getApplication()
    private fun readySettings(): ClockSettings {
        val s = ClockSettings(firstRun = false, nightEnabled = false)
        SettingsStore(context()).save(s); return s
    }
    @Test fun settingsAndCompleteVisualPresetSurviveRestart() {
        val s = readySettings()
        s.visual = VisualSettings(3, Color.CYAN, .95f, .1f, false, true, true, true, true, false)
        s.brightness = .73f; s.nightStart = 1234; s.language = "en"
        s.presets.add(Preset("Bedroom", s.visual.copy()))
        assertTrue(SettingsStore(context()).save(s))
        val restored = SettingsStore(context()).load()
        assertEquals(s, restored)
        restored.visual.style = 0
        assertEquals(3, restored.presets[0].visual.style)
    }
    @Test fun corruptedDataAndFutureSchemaHaveSafeDefaults() {
        val p = context().getSharedPreferences("clock_settings", Context.MODE_PRIVATE)
        p.edit().putString("settings", "broken").commit()
        assertEquals(ClockSettings(), SettingsStore(context()).load())
        p.edit().putString("settings", "{\"version\":99}").commit()
        val futureStore = SettingsStore(context())
        assertEquals(ClockSettings(), futureStore.load())
        assertFalse(futureStore.save(ClockSettings(firstRun = false)))
        assertEquals("{\"version\":99}", p.getString("settings", null))
        p.edit().putString("settings", "{\"version\":1,\"brightness\":8,\"visual\":{\"style\":42,\"size\":-1},\"presets\":[null,{}]}").commit()
        val s = SettingsStore(context()).load()
        assertEquals(1f, s.brightness, 0f); assertEquals(3, s.visual.style); assertEquals(.35f, s.visual.size, 0f)
        assertTrue(s.presets.isEmpty())
    }
    @Test fun everyStyleFitsSmallPhoneWithInsetsSecondsDateAndBattery() {
        for (style in 0..3) for (seconds in listOf(false, true)) {
            val v = ClockView(context())
            v.settings.visual = VisualSettings(style = style, size = 1f, seconds = seconds, hour24 = false, date = true, weekday = true)
            v.now = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 58); set(Calendar.SECOND, 58) }
            v.batteryPercent = 12; v.setPadding(28, 12, 35, 18)
            v.measure(View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(240, View.MeasureSpec.EXACTLY))
            v.layout(0, 0, 480, 240)
            for (shift in 0L..7L) {
                v.shiftStep = shift
                v.draw(Canvas(Bitmap.createBitmap(480, 240, Bitmap.Config.ARGB_8888)))
                assertTrue(v.compositionBounds.left >= 28); assertTrue(v.compositionBounds.top >= 12)
                assertTrue(v.compositionBounds.right <= 445); assertTrue(v.compositionBounds.bottom <= 222)
            }
        }
    }
    @Test fun foregroundOwnsKeepScreenOnAndNoSensorIsSafe() {
        readySettings()
        val controller = Robolectric.buildActivity(ClockActivity::class.java).setup()
        val a = controller.get()
        assertTrue(a.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0)
        a.settings.autoBrightness = true; a.changed(true)
        controller.pause()
        assertEquals(0, a.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        controller.stop().destroy()
    }
    @Test fun controlPanelTimeoutAndTwoSecondUnlock() {
        readySettings()
        val c = Robolectric.buildActivity(ClockActivity::class.java).setup()
        val a = c.get()
        fun event(action: Int) { a.clock.dispatchTouchEvent(MotionEvent.obtain(0, 0, action, 200f, 100f, 0)) }
        event(MotionEvent.ACTION_DOWN); event(MotionEvent.ACTION_UP)
        val lock = findText(a.window.decorView, a.getString(R.string.lock))!!
        lock.performClick()
        assertEquals(a.getString(R.string.locked), a.clock.contentDescription)
        event(MotionEvent.ACTION_DOWN)
        shadowOf(Looper.getMainLooper()).idleFor(1900, TimeUnit.MILLISECONDS)
        assertEquals(a.getString(R.string.locked), a.clock.contentDescription)
        shadowOf(Looper.getMainLooper()).idleFor(150, TimeUnit.MILLISECONDS)
        assertNotEquals(a.getString(R.string.locked), a.clock.contentDescription)
        event(MotionEvent.ACTION_UP)
        shadowOf(Looper.getMainLooper()).idleFor(5100, TimeUnit.MILLISECONDS)
        assertNull(findText(a.window.decorView, a.getString(R.string.lock)))
        c.pause().stop().destroy()
    }
    @Test fun settingsStickyPreviewAndChargingDisconnectDoNotExit() {
        readySettings()
        val c = Robolectric.buildActivity(ClockActivity::class.java).setup(); val a = c.get()
        a.showSettings()
        val scroll = findScroll(a.window.decorView)!!
        assertNotNull(scroll.parent)
        val preview = findPreview(a.window.decorView)!!
        assertNotSame(scroll, preview.parent)
        a.sendBroadcast(Intent(Intent.ACTION_BATTERY_CHANGED).putExtra("level", 12).putExtra("scale", 100).putExtra("status", 3))
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(a.isFinishing)
        a.closeSettings(); c.pause().stop().destroy()
    }
    @Test fun englishSettingsAndSkippedOnboardingPersist() {
        SettingsStore(context()).save(ClockSettings(language = "en"))
        val c = Robolectric.buildActivity(ClockActivity::class.java).setup(); val a = c.get()
        assertNotNull(findText(a.window.decorView, "Skip"))
        a.clock.performClick()
        assertNull(findText(a.window.decorView, a.getString(R.string.lock)))
        findText(a.window.decorView, "Skip")!!.performClick()
        assertFalse(SettingsStore(context()).load().firstRun)
        a.showSettings(); assertNotNull(findText(a.window.decorView, "Settings"))
        c.pause().stop().destroy()
    }
    @Test fun oledMovesAtSixtySecondsAndStopsWhenDisabledOrBackgrounded() {
        readySettings()
        val c = Robolectric.buildActivity(ClockActivity::class.java).setup(); val a = c.get()
        val initial = a.clock.shiftStep
        shadowOf(Looper.getMainLooper()).idleFor(59900, TimeUnit.MILLISECONDS)
        assertEquals(initial, a.clock.shiftStep)
        shadowOf(Looper.getMainLooper()).idleFor(200, TimeUnit.MILLISECONDS)
        assertEquals(initial + 1, a.clock.shiftStep)
        a.settings.oled = false; a.changed()
        shadowOf(Looper.getMainLooper()).idleFor(60000, TimeUnit.MILLISECONDS)
        assertEquals(initial + 1, a.clock.shiftStep)
        a.settings.oled = true; a.changed(); c.pause()
        shadowOf(Looper.getMainLooper()).idleFor(60000, TimeUnit.MILLISECONDS)
        assertEquals(initial + 1, a.clock.shiftStep)
        c.stop().destroy()
    }
    @Test fun nightWindowLimitHoldsWithControlsAndSwipeDoesNotPersist() {
        val s = readySettings().apply { nightEnabled = true; nightStart = 0; nightEnd = 0; nightMax = .02f; brightness = .7f }
        SettingsStore(context()).save(s)
        val c = Robolectric.buildActivity(ClockActivity::class.java).setup(); val a = c.get()
        assertTrue(a.window.attributes.screenBrightness <= .02f)
        a.showSettings(); assertTrue(a.window.attributes.screenBrightness <= .02f); a.closeSettings()
        a.clock.layout(0, 0, 480, 240)
        a.clock.dispatchTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 200f, 200f, 0))
        a.clock.dispatchTouchEvent(MotionEvent.obtain(0, 1, MotionEvent.ACTION_MOVE, 200f, 0f, 0))
        a.clock.dispatchTouchEvent(MotionEvent.obtain(0, 2, MotionEvent.ACTION_UP, 200f, 0f, 0))
        shadowOf(Looper.getMainLooper()).idleFor(1000, TimeUnit.MILLISECONDS)
        assertTrue(a.window.attributes.screenBrightness <= .02f)
        assertEquals(.7f, SettingsStore(context()).load().brightness, .001f)
        c.pause().stop().destroy()
    }
    @Test fun presetUiCreatesRenamesAppliesAndDeletes() {
        readySettings()
        val c = Robolectric.buildActivity(ClockActivity::class.java).setup(); val a = c.get()
        a.showSettings()
        findText(a.window.decorView, a.getString(R.string.save_preset))!!.performClick()
        var d = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()
        findInput(d.window!!.decorView)!!.setText("Bedroom")
        d.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick()
        assertEquals("Bedroom", a.settings.presets.single().name)
        findText(a.window.decorView, "Bedroom")!!.performClick()
        d = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()
        choose(d, 1)
        d = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()
        findInput(d.window!!.decorView)!!.setText("Nightstand")
        d.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick()
        assertEquals("Nightstand", a.settings.presets.single().name)
        a.settings.visual.style = 3; a.settings.brightness = .6f
        findText(a.window.decorView, "Nightstand")!!.performClick()
        choose(org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog(), 0)
        assertEquals(0, a.settings.visual.style); assertEquals(.6f, a.settings.brightness, 0f)
        findText(a.window.decorView, "Nightstand")!!.performClick()
        choose(org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog(), 2)
        assertTrue(SettingsStore(context()).load().presets.isEmpty())
        c.pause().stop().destroy()
    }
    private fun choose(d: android.app.AlertDialog, index: Int) {
        val list = d.listView
        list.performItemClick(list.adapter.getView(index, null, list), index, index.toLong())
    }
    private fun findInput(v: View): android.widget.EditText? {
        if (v is android.widget.EditText) return v
        if (v is android.view.ViewGroup) for (i in 0 until v.childCount) findInput(v.getChildAt(i))?.let { return it }
        return null
    }
    private fun findText(v: View, text: String): android.widget.TextView? {
        if (v is android.widget.TextView && v.text.toString() == text) return v
        if (v is android.view.ViewGroup) for (i in 0 until v.childCount) findText(v.getChildAt(i), text)?.let { return it }
        return null
    }
    private fun findScroll(v: View): android.widget.ScrollView? {
        if (v is android.widget.ScrollView) return v
        if (v is android.view.ViewGroup) for (i in 0 until v.childCount) findScroll(v.getChildAt(i))?.let { return it }
        return null
    }
    private fun findPreview(v: View): ClockView? {
        if (v is ClockView && v.preview) return v
        if (v is android.view.ViewGroup) for (i in 0 until v.childCount) findPreview(v.getChildAt(i))?.let { return it }
        return null
    }
}
