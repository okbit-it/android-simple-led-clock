package ru.okbit.ledclock

import android.content.Context
import android.graphics.*
import android.view.View
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.*

/** Renders only. No timer, subscriptions, global brightness or persistence inside a View. */
class ClockView(context: Context) : View(context) {
    var settings = ClockSettings()
    var now: Calendar = Calendar.getInstance()
    var foregroundColor = settings.visual.color
    var softwareDim = 0f
    var batteryPercent = -1
    var charging = false
    var preview = false
    var shiftStep = 0L
    var bottomReserve = 0
    val compositionBounds = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val segmentMasks = intArrayOf(0x3f, 0x06, 0x5b, 0x4f, 0x66, 0x6d, 0x7d, 0x07, 0x7f, 0x6f)
    private val retroPatterns = arrayOf(
        "111101101101111", "010110010010111", "111001111100111", "111001111001111", "101101111001001",
        "111100111001111", "111100111101111", "111001001001001", "111101111101111", "111101111001111")
    private val segmentShapes = arrayOf(
            floatArrayOf(10f,0f,54f,0f,61f,6f,54f,12f,10f,12f,3f,6f),
            floatArrayOf(57f,14f,64f,7f,64f,47f,58f,53f,52f,47f,52f,20f),
            floatArrayOf(58f,57f,64f,63f,64f,103f,57f,96f,52f,90f,52f,63f),
            floatArrayOf(10f,98f,54f,98f,61f,104f,54f,110f,10f,110f,3f,104f),
            floatArrayOf(0f,63f,6f,57f,12f,63f,12f,90f,7f,96f,0f,103f),
            floatArrayOf(0f,7f,7f,14f,12f,20f,12f,47f,6f,53f,0f,47f),
            floatArrayOf(10f,49f,54f,49f,60f,55f,54f,61f,10f,61f,4f,55f))
    private val segmentPaths = segmentShapes.map { p ->
        Path().apply {
            moveTo(p[0], p[1])
            for (j in 2 until p.size step 2) lineTo(p[j], p[j + 1])
            close()
        }
    }.toTypedArray()
    private val shifts = arrayOf(-1 to -1, 0 to -1, 1 to -1, 1 to 0, 1 to 1, 0 to 1, -1 to 1, -1 to 0)
    private var detailLanguage = ""
    private var weekdayFormat = SimpleDateFormat("EEEE", Locale("ru"))
    private var dateFormat = SimpleDateFormat("d MMM yyyy", Locale("ru"))
    override fun performClick(): Boolean = super.performClick()
    init { setBackgroundColor(Color.BLACK); if (android.os.Build.VERSION.SDK_INT >= 16) importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val v = settings.visual
        val color = ClockPolicy.colorBetween(Color.BLACK, foregroundColor, 1f - softwareDim)
        val w = (width - paddingLeft - paddingRight).toFloat()
        val h = (height - paddingTop - paddingBottom - bottomReserve).toFloat()
        if (w < 4 || h < 4) return
        val time = ClockPolicy.time(now, v.hour24, v.seconds)
        val blinkVisible = !v.blink || now.get(Calendar.SECOND) % 2 == 0
        val details = mutableListOf<String>()
        if (detailLanguage != settings.language) {
            detailLanguage = settings.language
            weekdayFormat = SimpleDateFormat("EEEE", Locale(detailLanguage))
            dateFormat = SimpleDateFormat("d MMM yyyy", Locale(detailLanguage))
        }
        weekdayFormat.timeZone = now.timeZone; dateFormat.timeZone = now.timeZone
        if (v.weekday) details.add(weekdayFormat.format(now.time))
        if (v.date) details.add(dateFormat.format(now.time))
        if (v.battery) details.add(if (batteryPercent < 0) context.getString(R.string.battery_unknown) else
            "$batteryPercent%${if (charging) " +" else ""}")
        val detail = details.joinToString("   ·   ")
        val hasAm = !v.hour24
        val unitWidth = time.fold(0f) { acc, ch -> acc + if (ch == ':') 28f else 72f } + if (hasAm) 60 else 0
        val warnBattery = !preview && batteryPercent in 0..15 && !charging
        val totalHeight = 110f + (if (detail.isNotEmpty()) 38 else 0) + (if (warnBattery) 25 else 0)
        val margin = if (preview) 6f else 12f
        val scale = min((w - 2 * margin) / unitWidth, (h - 2 * margin) / totalHeight) * v.size
        if (scale <= 0) return
        val shift = shifts[((shiftStep % 8 + 8) % 8).toInt()]
        val shiftMax = min(4f * resources.displayMetrics.density, margin / 2)
        val dx = if (settings.oled && !preview) shift.first * shiftMax else 0f
        val dy = if (settings.oled && !preview) shift.second * shiftMax else 0f
        val x = paddingLeft + (w - unitWidth * scale) / 2 + dx
        val y = paddingTop + (h - totalHeight * scale) / 2 + dy
        compositionBounds.set(x, y, x + unitWidth * scale, y + totalHeight * scale)
        canvas.save(); canvas.translate(x, y); canvas.scale(scale, scale)
        var pos = 0f
        for (char in time) {
            if (char == ':') {
                if (blinkVisible) {
                    paint.color = color
                    if (v.style == 1) { canvas.drawRect(pos + 10, 30f, pos + 17, 37f, paint); canvas.drawRect(pos + 10, 71f, pos + 17, 78f, paint) }
                    else { canvas.drawCircle(pos + 14, 34f, 4f, paint); canvas.drawCircle(pos + 14, 74f, 4f, paint) }
                }
                pos += 28
            } else {
                canvas.save(); canvas.translate(pos, 0f)
                when (v.style) {
                    0 -> drawSegment(canvas, char - '0', color, v.inactive)
                    1 -> drawRetro(canvas, char - '0', color)
                    2 -> drawModern(canvas, char, color)
                    else -> drawMinimal(canvas, char, color)
                }
                canvas.restore(); pos += 72
            }
        }
        if (hasAm) {
            paint.color = color; paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL); paint.textSize = 22f
            canvas.drawText(if (now.get(Calendar.HOUR_OF_DAY) < 12) "AM" else "PM", pos + 3, 103f, paint)
        }
        if (detail.isNotEmpty()) {
            paint.color = color; paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            paint.textSize = 18f
            if (paint.measureText(detail) > unitWidth) paint.textSize *= unitWidth / paint.measureText(detail)
            canvas.drawText(detail, (unitWidth - paint.measureText(detail)) / 2, 140f, paint)
        }
        if (warnBattery) {
            paint.typeface = Typeface.DEFAULT; paint.color = color; paint.textSize = 16f
            val message = context.getString(R.string.low_battery) + " $batteryPercent%"
            if (paint.measureText(message) > unitWidth) paint.textSize *= unitWidth / paint.measureText(message)
            canvas.drawText(message, (unitWidth - paint.measureText(message)) / 2, totalHeight - 5, paint)
        }
        canvas.restore()
    }

    private fun drawSegment(c: Canvas, digit: Int, color: Int, inactive: Float) {
        // Clockwise six-point polygons with deliberate gaps. Bit order: A B C D E F G.
        for (i in segmentPaths.indices) {
            val on = segmentMasks[digit] and (1 shl i) != 0
            if (!on && inactive == 0f) continue
            paint.color = if (on) color else ClockPolicy.colorBetween(Color.BLACK, color, inactive)
            c.drawPath(segmentPaths[i], paint)
        }
    }

    private fun drawRetro(c: Canvas, digit: Int, color: Int) {
        paint.color = color
        val pattern = retroPatterns[digit]
        for (row in 0..4) for (col in 0..2) if (pattern[row * 3 + col] == '1')
            c.drawRect(col * 22f, row * 22f, col * 22f + 17, row * 22f + 17, paint)
    }
    private fun drawModern(c: Canvas, digit: Char, color: Int) {
        paint.color = color; paint.typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD); paint.textSize = 134f
        c.drawText(digit.toString(), (64 - paint.measureText(digit.toString())) / 2, 104f, paint)
    }
    private fun drawMinimal(c: Canvas, digit: Char, color: Int) {
        paint.color = color; paint.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL); paint.textSize = 124f
        c.drawText(digit.toString(), (64 - paint.measureText(digit.toString())) / 2, 103f, paint)
    }
}
