package ru.okbit.ledclock

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar

@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "land-hdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NativeRenderTest {
    @Test fun renderActualClockAndSettingsWithNativeCanvas() {
        val context = RuntimeEnvironment.getApplication()
        SettingsStore(context).save(ClockSettings(firstRun = false, nightEnabled = false))
        val c = Robolectric.buildActivity(ClockActivity::class.java).setup(); val a = c.get()
        a.clock.now = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 22); set(Calendar.MINUTE, 48); set(Calendar.SECOND, 0) }
        a.clock.settings.visual.apply { date = true; weekday = true }
        a.clock.batteryPercent = 76
        val bitmap = render(a.clock)
        assertEquals(Color.BLACK, bitmap.getPixel(0, 0))
        var litPixels = 0
        for (y in 0 until bitmap.height step 4) for (x in 0 until bitmap.width step 4)
            if (bitmap.getPixel(x, y) and 0xffffff != 0) litPixels++
        assertTrue("Canvas must contain lit segments", litPixels > 100)
        save(bitmap, "clock")
        a.showSettings(); save(render(a.window.decorView), "settings")
        c.pause().stop().destroy()
    }
    private fun render(v: View): Bitmap {
        v.measure(View.MeasureSpec.makeMeasureSpec(960, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(540, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, 960, 540)
        return Bitmap.createBitmap(960, 540, Bitmap.Config.ARGB_8888).also { v.draw(Canvas(it)) }
    }
    private fun save(bitmap: Bitmap, name: String) {
        val output = System.getProperty("clock.artifactDir") ?: return
        val dir = File(output).apply { mkdirs() }
        FileOutputStream(File(dir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val thumb = Bitmap.createScaledBitmap(bitmap, 512, 288, true)
        FileOutputStream(File(dir, "$name-thumb.png")).use { thumb.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
