package ru.okbit.ledclock

import android.app.Activity
import android.app.AlertDialog
import android.content.*
import android.content.res.Configuration
import android.graphics.Color
import android.hardware.*
import android.os.*
import android.view.*
import android.widget.*
import java.util.Calendar
import java.util.Locale
import kotlin.math.*

/** Lifecycle owner/controller; domain policy, persistence and rendering live separately. */
class ClockActivity : Activity(), SensorEventListener {
    lateinit var settings: ClockSettings
    private lateinit var store: SettingsStore
    private lateinit var root: android.widget.FrameLayout
    private lateinit var safeRoot: android.widget.FrameLayout
    lateinit var clock: ClockView
    private var preview: ClockView? = null
    private var panel: LinearLayout? = null
    private var settingsPanel: View? = null
    private var onboarding: View? = null
    private var dialog: android.app.Dialog? = null
    private var resumed = false
    private var registered = false
    private var locked = false
    private var swiped = false
    private var downX = 0f
    private var downY = 0f
    private var downBrightness = .5f
    private var overrideBrightness: Float? = null
    private var lux: Float? = null
    private var battery = -1
    private var charging = false
    private var night: Boolean? = null
    private var shownColor = 0
    private var shownBrightness = .5f
    private var transitionStart = 0L
    private var fromColor = 0
    private var fromBrightness = .5f
    private var targetColor = 0
    private var targetBrightness = .5f
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var sensorManager: SensorManager
    private var light: Sensor? = null
    private var ui: ClockSettingsUi? = null
    private val tick = object : Runnable { override fun run() { refresh(); scheduleTick() } }
    private val hidePanel = Runnable { hideControls() }
    private val oledShift = object : Runnable {
        override fun run() {
            if (!resumed || !settings.oled) return
            clock.shiftStep++; clock.invalidate()
            handler.postDelayed(this, 60000)
        }
    }
    private fun scheduleOled() {
        handler.removeCallbacks(oledShift)
        if (resumed && settings.oled) handler.postDelayed(oledShift, 60000)
    }
    private val unlock = Runnable {
        if (locked) { locked = false; clock.contentDescription = getString(R.string.app_name); toast(R.string.unlocked); hideControls() }
    }
    private val fade = object : Runnable {
        override fun run() {
            if (!resumed) return
            val t = ((SystemClock.uptimeMillis() - transitionStart) / 900f).coerceIn(0f, 1f)
            shownColor = ClockPolicy.colorBetween(fromColor, targetColor, t)
            shownBrightness = fromBrightness + (targetBrightness - fromBrightness) * t
            applyAppearance()
            if (t < 1) handler.postDelayed(this, 40)
        }
    }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                battery = if (level >= 0 && scale > 0) level * 100 / scale else -1
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            }
            if (intent?.action == Intent.ACTION_LOCALE_CHANGED) applyLanguage()
            refresh(); scheduleTick()
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SettingsStore(this); settings = store.load()
        applyLanguage()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        light = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        locked = savedInstanceState?.getBoolean("locked", false) ?: false
        root = FrameLayout(this); root.setBackgroundColor(Color.BLACK)
        safeRoot = FrameLayout(this); root.addView(safeRoot, FrameLayout.LayoutParams(-1, -1))
        clock = ClockView(this); clock.settings = settings
        clock.setOnClickListener { if (onboarding == null) { if (locked) showLockHint() else if (panel == null) showControls() else hideControls() } }
        clock.setOnTouchListener { _, event -> clockTouch(event) }
        safeRoot.addView(clock, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        if (Build.VERSION.SDK_INT >= 20) configureInsets()
        ui = ClockSettingsUi(this)
        if (settings.firstRun) showOnboarding()
        else if (savedInstanceState?.getBoolean("settingsOpen") == true) showSettings()
        fullscreen()
    }
    @android.annotation.TargetApi(20)
    private fun configureInsets() {
        root.setOnApplyWindowInsetsListener { _, insets ->
            var left = insets.systemWindowInsetLeft; var top = insets.systemWindowInsetTop
            var right = insets.systemWindowInsetRight; var bottom = insets.systemWindowInsetBottom
            if (Build.VERSION.SDK_INT >= 28) insets.displayCutout?.let {
                left = max(left, it.safeInsetLeft); right = max(right, it.safeInsetRight)
                top = max(top, it.safeInsetTop); bottom = max(bottom, it.safeInsetBottom)
            }
            // Root padding shrinks all interactive surfaces, including sticky preview.
            safeRoot.setPadding(left, top, right, bottom)
            insets
        }
        root.requestApplyInsets()
    }
    override fun onResume() {
        super.onResume(); resumed = true
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (!registered) {
            val f = IntentFilter().apply {
                addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED)
                addAction(Intent.ACTION_DATE_CHANGED); addAction(Intent.ACTION_LOCALE_CHANGED)
                addAction(Intent.ACTION_BATTERY_CHANGED)
            }
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, f, Context.RECEIVER_NOT_EXPORTED)
            else registerReceiver(receiver, f)
            registered = true
        }
        updateSensor(); refresh(); scheduleTick(); scheduleOled(); fullscreen()
        if (panel != null) handler.postDelayed(hidePanel, 5000)
    }
    override fun onPause() {
        resumed = false; handler.removeCallbacksAndMessages(null)
        sensorManager.unregisterListener(this)
        if (registered) { unregisterReceiver(receiver); registered = false }
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        persist(); super.onPause()
    }
    override fun onDestroy() { dialog?.dismiss(); handler.removeCallbacksAndMessages(null); super.onDestroy() }
    override fun onSaveInstanceState(out: Bundle) {
        out.putBoolean("locked", locked); out.putBoolean("settingsOpen", settingsPanel != null)
        super.onSaveInstanceState(out)
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) { super.onWindowFocusChanged(hasFocus); if (hasFocus) fullscreen() }
    private fun fullscreen() {
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = if (Build.VERSION.SDK_INT >= 19)
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        // Pre-KitKat HIDE_NAVIGATION consumes the first touch. Keep navigation dimmed
        // so clock gestures and the system exit remain available immediately.
        else if (Build.VERSION.SDK_INT >= 16) View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_LOW_PROFILE
        else View.SYSTEM_UI_FLAG_LOW_PROFILE
    }
    fun applyLanguage() {
        val locale = Locale(settings.language)
        val config = Configuration(resources.configuration)
        @Suppress("DEPRECATION")
        run { config.locale = locale; resources.updateConfiguration(config, resources.displayMetrics) }
    }
    fun updateSensor() {
        sensorManager.unregisterListener(this)
        if (resumed && settings.autoBrightness && light != null) sensorManager.registerListener(this, light, SensorManager.SENSOR_DELAY_NORMAL)
        if (!settings.autoBrightness || light == null) lux = null
    }
    fun hasLightSensor() = light != null
    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_LIGHT) return
        val value = event.values[0]
        if (value.isNaN() || value.isInfinite()) return
        // Low-pass filter: avoid visible flicker from minor sensor noise.
        lux = lux?.let { it * .85f + value * .15f } ?: value
        refresh()
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    private fun scheduleTick() {
        handler.removeCallbacks(tick)
        if (resumed) handler.postDelayed(tick, ClockPolicy.nextTick(System.currentTimeMillis(), settings.visual.seconds, settings.visual.blink, 60000))
    }
    fun refresh() {
        val now = Calendar.getInstance()
        val isNight = ClockPolicy.isNight(now, settings)
        // Manual adjustment is temporary for a night period, including after it ends.
        if (night != null && night != isNight) overrideBrightness = null
        val c = if (isNight) settings.nightColor else settings.visual.color
        val b = ClockPolicy.brightness(settings, isNight, lux, overrideBrightness)
        if (night == null) {
            shownColor = c; shownBrightness = b; targetColor = c; targetBrightness = b
        } else if (c != targetColor || abs(b - targetBrightness) > .008f) {
            fromColor = shownColor; fromBrightness = shownBrightness; targetColor = c; targetBrightness = b
            transitionStart = SystemClock.uptimeMillis(); handler.removeCallbacks(fade)
            if (resumed) handler.post(fade)
        }
        night = isNight
        clock.now = now; clock.settings = settings; clock.batteryPercent = battery; clock.charging = charging
        clock.contentDescription = if (locked) getString(R.string.locked) else ClockPolicy.time(now, settings.visual.hour24, settings.visual.seconds)
        preview?.let {
            it.settings = settings; it.now = now; it.batteryPercent = battery; it.charging = charging
            // Preview intentionally shows daytime visual edits while night schedule remains active on main clock.
            it.foregroundColor = settings.visual.color; it.invalidate()
        }
        applyAppearance()
    }
    private fun applyAppearance() {
        clock.foregroundColor = shownColor; clock.softwareDim = settings.dim; clock.invalidate()
        val p = window.attributes
        // Keep system controls usable: window brightness has a floor while software dim affects the clock only.
        val controlsOpen = settingsPanel != null || onboarding != null || panel != null || dialog?.isShowing == true
        val requested = max(if (controlsOpen) .08f else .01f, shownBrightness)
        p.screenBrightness = if (night == true) min(requested, settings.nightMax) else requested
        window.attributes = p
    }
    fun changed(sensorChanged: Boolean = false) {
        if (sensorChanged) updateSensor()
        refresh(); scheduleTick(); scheduleOled(); persist()
    }
    fun clearBrightnessOverride() { overrideBrightness = null }
    fun persist() { if (!store.save(settings)) toast(R.string.save_failed) }
    fun toast(id: Int) { Toast.makeText(this, id, Toast.LENGTH_LONG).show() }
    private fun clockTouch(e: MotionEvent): Boolean {
        if (onboarding != null) return true
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x; downY = e.y; swiped = false; downBrightness = overrideBrightness ?: shownBrightness
                if (locked) handler.postDelayed(unlock, 2000)
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(e.y - downY) > dp(12) || abs(e.x - downX) > dp(12)) {
                    swiped = true; handler.removeCallbacks(unlock)
                }
                if (swiped && !locked) {
                    val value = (downBrightness + (downY - e.y) / max(1, clock.height) * 1.3f).coerceIn(.01f, 1f)
                    if (night == true) overrideBrightness = min(value, settings.nightMax)
                    else { settings.brightness = value; settings.autoBrightness = false; updateSensor() }
                    refresh()
                }
            }
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(unlock)
                if (!swiped) {
                    clock.performClick()
                } else persist()
            }
            MotionEvent.ACTION_CANCEL -> handler.removeCallbacks(unlock)
        }
        return true
    }
    private fun showLockHint() { toast(R.string.locked) }
    private fun showControls() {
        if (locked) return
        val p = LinearLayout(this); p.orientation = LinearLayout.HORIZONTAL; p.gravity = Gravity.CENTER
        p.setPadding(dp(8), dp(4), dp(8), dp(4)); p.setBackgroundDrawable(ui!!.background(0xff192326.toInt()))
        p.addView(ui!!.button(getString(R.string.settings)) { showSettings() })
        p.addView(ui!!.button(getString(R.string.lock)) { locked = true; hideControls(); showLockHint() })
        p.addView(ui!!.button(getString(R.string.close_panel)) { hideControls() })
        panel = p
        for (i in 0 until p.childCount) p.getChildAt(i).layoutParams = LinearLayout.LayoutParams(0, -1, 1f)
        val params = FrameLayout.LayoutParams(-1, dp(60), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        params.setMargins(dp(8), 0, dp(8), dp(8)); safeRoot.addView(p, params)
        clock.bottomReserve = dp(76); refresh()
        handler.postDelayed(hidePanel, 5000)
    }
    private fun hideControls() {
        handler.removeCallbacks(hidePanel); panel?.let { safeRoot.removeView(it) }; panel = null
        clock.bottomReserve = 0; refresh()
    }
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (panel != null) { handler.removeCallbacks(hidePanel); handler.postDelayed(hidePanel, 5000) }
        return super.dispatchTouchEvent(ev)
    }
    fun showSettings() {
        hideControls(); if (settingsPanel != null) return
        val container = ui!!.buildSettings()
        settingsPanel = container; safeRoot.addView(container, FrameLayout.LayoutParams(-1, -1)); refresh()
    }
    fun closeSettings() {
        dialog?.dismiss(); dialog = null
        settingsPanel?.let { safeRoot.removeView(it) }; settingsPanel = null; preview = null
        persist(); refresh(); fullscreen()
    }
    fun rebuildSettings() { closeSettings(); applyLanguage(); ui = ClockSettingsUi(this); showSettings() }
    fun attachPreview(view: ClockView) { preview = view }
    private fun showOnboarding() {
        val view = ui!!.buildWelcome { finishOnboarding() }
        onboarding = view
        val cardWidth = min(dp(390), (resources.displayMetrics.widthPixels * .55f).toInt())
        val params = FrameLayout.LayoutParams(cardWidth, -1, Gravity.RIGHT)
        params.setMargins(dp(8), dp(8), dp(8), dp(8)); safeRoot.addView(view, params)
        // Keep visible clock beside the compact first-run card, even on small phones.
        clock.layoutParams = FrameLayout.LayoutParams(-1, -1).apply { rightMargin = cardWidth + dp(16) }
    }
    fun rebuildWelcome() {
        onboarding?.let { safeRoot.removeView(it) }; applyLanguage(); ui = ClockSettingsUi(this); showOnboarding(); refresh()
    }
    private fun finishOnboarding() {
        onboarding?.let { safeRoot.removeView(it) }; onboarding = null
        clock.layoutParams = FrameLayout.LayoutParams(-1, -1)
        settings.firstRun = false
        if (!settings.tipsShown) { toast(R.string.first_hint); settings.tipsShown = true }
        changed(); fullscreen()
    }
    fun trackDialog(d: android.app.Dialog) {
        dialog?.dismiss(); dialog = d
        d.setOnDismissListener { if (dialog === d) dialog = null; refresh(); fullscreen() }
        d.show(); refresh()
    }
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        when { dialog?.isShowing == true -> dialog?.dismiss(); settingsPanel != null -> closeSettings()
            onboarding != null -> finishOnboarding(); else -> super.onBackPressed() }
    }
    fun dp(n: Int) = (n * resources.displayMetrics.density + .5f).toInt()
}
