package ru.okbit.ledclock

import android.app.AlertDialog
import android.app.TimePickerDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.InputFilter
import android.view.Gravity
import android.view.View
import android.widget.*
import java.util.Locale

/** Native controls and resource-based text keep the UI available on Android 4.0. */
class ClockSettingsUi(private val a: ClockActivity) {
    private val s get() = a.settings
    private val accent = 0xff4bd8cb.toInt()
    private val muted = 0xffa8bac0.toInt()
    private val styles = arrayOf("Classic LED", "Retro Digital", "Modern Digital", "Minimal")
    private val colors = intArrayOf(0xff32d74b.toInt(), 0xffff453a.toInt(), 0xffffb340.toInt(), 0xff64d2ff.toInt(), 0xffe5e5e5.toInt(), 0xffbf5af2.toInt())
    private val colorNames = intArrayOf(R.string.green, R.string.red, R.string.amber, R.string.blue, R.string.white, R.string.purple)
    fun background(color: Int) = GradientDrawable().apply { setColor(color); cornerRadius = a.dp(14).toFloat() }
    private fun label(text: String, size: Float = 15f, color: Int = Color.WHITE): TextView = TextView(a).apply {
        this.text = text; textSize = size; setTextColor(color); gravity = Gravity.CENTER_VERTICAL
        setPadding(a.dp(8), a.dp(6), a.dp(8), a.dp(6))
    }
    fun button(text: String, action: () -> Unit): Button = Button(a).apply {
        this.text = text; textSize = 13f; setTextColor(accent); minHeight = a.dp(48)
        isAllCaps = false; setOnClickListener { action() }
        setBackgroundDrawable(android.graphics.drawable.StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), background(0xff334b50.toInt()))
            addState(intArrayOf(), background(0xff253438.toInt()))
        })
        setPadding(a.dp(12), a.dp(4), a.dp(12), a.dp(4))
    }
    private fun column(): LinearLayout = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
    private fun section(parent: LinearLayout, title: Int): LinearLayout {
        parent.addView(label(a.getString(title), 17f, accent), LinearLayout.LayoutParams(-1, a.dp(48)))
        val c = column().apply { setBackgroundDrawable(background(0xff172125.toInt())); setPadding(a.dp(10), a.dp(6), a.dp(10), a.dp(10)) }
        val lp = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = a.dp(10) }; parent.addView(c, lp)
        return c
    }
    private fun note(parent: LinearLayout, textId: Int) { parent.addView(label(a.getString(textId), 13f, muted)) }
    private fun toggle(parent: LinearLayout, id: Int, value: Boolean, change: (Boolean) -> Unit) {
        parent.addView(CheckBox(a).apply {
            text = a.getString(id); textSize = 15f; setTextColor(Color.WHITE); minHeight = a.dp(48)
            setPadding(a.dp(if (Build.VERSION.SDK_INT < 17) 32 else 6), a.dp(4), a.dp(6), a.dp(4)); isChecked = value
            if (Build.VERSION.SDK_INT >= 21) buttonTintList = android.content.res.ColorStateList.valueOf(accent)
            else setButtonDrawable(android.graphics.drawable.StateListDrawable().apply {
                fun box(checked: Boolean) = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE; setSize(a.dp(20), a.dp(20)); cornerRadius = a.dp(4).toFloat()
                    setColor(if (checked) accent else Color.TRANSPARENT); setStroke(a.dp(2), accent)
                }
                addState(intArrayOf(android.R.attr.state_checked), box(true)); addState(intArrayOf(), box(false))
            })
            setOnCheckedChangeListener { _, checked -> change(checked); a.changed() }
        }, LinearLayout.LayoutParams(-1, -2))
    }
    private fun slider(parent: LinearLayout, id: Int, current: Float, low: Float = 0f, high: Float = 1f, change: (Float) -> Unit) {
        val title = label("${a.getString(id)}  ${(current * 100).toInt()}%")
        parent.addView(title)
        parent.addView(SeekBar(a).apply {
            max = 100; progress = ((current - low) / (high - low) * 100).toInt()
            minimumHeight = a.dp(48); contentDescription = a.getString(id)
            if (Build.VERSION.SDK_INT >= 21) { progressTintList = android.content.res.ColorStateList.valueOf(accent); thumbTintList = android.content.res.ColorStateList.valueOf(accent) }
            if (Build.VERSION.SDK_INT < 21) {
                val track = GradientDrawable().apply { setColor(0xff304145.toInt()); setSize(a.dp(200), a.dp(4)) }
                val fill = android.graphics.drawable.ClipDrawable(GradientDrawable().apply { setColor(accent) }, Gravity.LEFT, android.graphics.drawable.ClipDrawable.HORIZONTAL)
                progressDrawable = android.graphics.drawable.LayerDrawable(arrayOf(track, fill)).apply {
                    setId(0, android.R.id.background); setId(1, android.R.id.progress)
                    setLayerInset(0, 0, a.dp(20), 0, a.dp(20)); setLayerInset(1, 0, a.dp(20), 0, a.dp(20))
                }
                thumb = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(accent); setSize(a.dp(20), a.dp(20)) }
            }
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, p: Int, user: Boolean) {
                    if (!user) return
                    val v = low + p / 100f * (high - low)
                    title.text = "${a.getString(id)}  ${(v * 100).toInt()}%"; change(v); a.refresh()
                }
                override fun onStartTrackingTouch(bar: SeekBar?) {}
                override fun onStopTrackingTouch(bar: SeekBar?) { a.changed() }
            })
        }, LinearLayout.LayoutParams(-1, a.dp(48)))
    }
    private fun choice(parent: LinearLayout, id: Int, options: Array<String>, index: Int, change: (Int) -> Unit) {
        val b = button("${a.getString(id)} · ${options[index]}") {}
        b.gravity = Gravity.LEFT or Gravity.CENTER_VERTICAL
        b.setOnClickListener {
            a.trackDialog(AlertDialog.Builder(a).setTitle(id).setSingleChoiceItems(options, index) { d, i ->
                change(i); b.text = "${a.getString(id)} · ${options[i]}"; d.dismiss(); a.changed()
            }.setNegativeButton(R.string.cancel, null).create())
        }
        parent.addView(b, LinearLayout.LayoutParams(-1, -2))
    }
    private fun style(parent: LinearLayout) { choice(parent, R.string.style, styles, s.visual.style) { s.visual.style = it } }
    private fun color(parent: LinearLayout, night: Boolean = false) {
        val id = if (night) R.string.night_color else R.string.color
        val value = if (night) s.nightColor else s.visual.color
        val b = button("${a.getString(id)}  ${hex(value)}") {}
        b.setTextColor(value)
        b.setOnClickListener { colorDialog(value) { c ->
            if (night) s.nightColor = c else s.visual.color = c
            b.text = "${a.getString(id)}  ${hex(c)}"; b.setTextColor(c); a.changed()
        } }
        parent.addView(b, LinearLayout.LayoutParams(-1, -2))
    }
    private fun hex(c: Int) = String.format(Locale.US, "#%06X", c and 0xffffff)
    private fun colorDialog(current: Int, select: (Int) -> Unit) {
        val names = colorNames.map { a.getString(it) }.toTypedArray() + a.getString(R.string.custom_color)
        a.trackDialog(AlertDialog.Builder(a).setTitle(R.string.palette).setItems(names) { _, i ->
            if (i < colors.size) select(colors[i]) else customColor(current, select)
        }.setNegativeButton(R.string.cancel, null).create())
    }
    private fun customColor(current: Int, select: (Int) -> Unit) {
        val content = column().apply { setPadding(a.dp(16), a.dp(8), a.dp(16), a.dp(8)) }
        val sample = label(hex(current), 19f, current); content.addView(sample)
        var selected = current
        val bars = mutableListOf<SeekBar>()
        val input = EditText(a).apply {
            setSingleLine(true); hint = a.getString(R.string.hex); setText(hex(current)); filters = arrayOf(InputFilter.LengthFilter(7))
            contentDescription = a.getString(R.string.hex)
        }
        for ((index, name) in arrayOf("R", "G", "B").withIndex()) {
            content.addView(label(name))
            val shift = 16 - index * 8
            val bar = SeekBar(a).apply {
                max = 255; progress = (current ushr shift) and 255; minimumHeight = a.dp(48); contentDescription = name
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(bar: SeekBar?, p: Int, user: Boolean) {
                        if (user) {
                            selected = (selected and (255 shl shift).inv()) or (p shl shift)
                            sample.text = hex(selected); sample.setTextColor(selected); input.setText(hex(selected))
                        }
                    }
                    override fun onStartTrackingTouch(bar: SeekBar?) {}
                    override fun onStopTrackingTouch(bar: SeekBar?) {}
                })
            }; bars.add(bar); content.addView(bar, LinearLayout.LayoutParams(-1, a.dp(48)))
        }
        content.addView(input)
        val scroll = ScrollView(a); scroll.addView(content)
        val d = AlertDialog.Builder(a).setTitle(R.string.custom_color).setView(scroll)
            .setPositiveButton(R.string.apply, null).setNegativeButton(R.string.cancel, null).create()
        a.trackDialog(d)
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val text = input.text.toString().trim()
            if (!Regex("#[0-9a-fA-F]{6}").matches(text)) input.error = a.getString(R.string.invalid_color)
            else { select(Color.parseColor(text)); d.dismiss() }
        }
    }
    private fun time(parent: LinearLayout, id: Int, start: Boolean) {
        fun value() = if (start) s.nightStart else s.nightEnd
        fun title(): String = "${a.getString(id)}  %02d:%02d".format(Locale.US, value() / 60, value() % 60)
        val b = button(title()) {}
        b.setOnClickListener {
            a.trackDialog(TimePickerDialog(a, { _, h, m ->
                if (start) s.nightStart = h * 60 + m else s.nightEnd = h * 60 + m
                b.text = title(); a.changed()
            }, value() / 60, value() % 60, true))
        }; parent.addView(b, LinearLayout.LayoutParams(-1, -2))
    }
    fun buildSettings(): View {
        val page = column().apply { setBackgroundColor(0xff0e1619.toInt()); setPadding(a.dp(14), 0, a.dp(14), 0) }
        val header = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(label(a.getString(R.string.settings), 20f), LinearLayout.LayoutParams(0, a.dp(48), 1f))
        header.addView(button(a.getString(R.string.done)) { a.closeSettings() })
        page.addView(header)
        // Sibling of ScrollView, so it remains fixed while all setting groups scroll.
        val preview = ClockView(a).apply { this.preview = true; settings = s; contentDescription = a.getString(R.string.preview) }
        a.attachPreview(preview)
        val screenHeightDp = a.resources.displayMetrics.heightPixels / a.resources.displayMetrics.density
        page.addView(preview, LinearLayout.LayoutParams(-1, a.dp(if (screenHeightDp < 340) 64 else 88)))
        val scroll = ScrollView(a).apply { isFillViewport = true }
        page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val groups = column(); scroll.addView(groups)
        val display = section(groups, R.string.display)
        style(display)
        slider(display, R.string.size, s.visual.size, .35f) { s.visual.size = it }
        slider(display, R.string.inactive, s.visual.inactive, 0f, .18f) { s.visual.inactive = it }
        toggle(display, R.string.hour24, s.visual.hour24) { s.visual.hour24 = it }
        toggle(display, R.string.seconds, s.visual.seconds) { s.visual.seconds = it }
        toggle(display, R.string.date, s.visual.date) { s.visual.date = it }
        toggle(display, R.string.weekday, s.visual.weekday) { s.visual.weekday = it }
        toggle(display, R.string.battery, s.visual.battery) { s.visual.battery = it }
        toggle(display, R.string.blink, s.visual.blink) { s.visual.blink = it }
        val colorGroup = section(groups, R.string.colors); color(colorGroup)
        val presets = column(); colorGroup.addView(presets); fillPresets(presets)
        val brightness = section(groups, R.string.brightness)
        slider(brightness, R.string.manual_brightness, s.brightness, .01f) { s.brightness = it; a.clearBrightnessOverride() }
        toggle(brightness, R.string.auto, s.autoBrightness) { s.autoBrightness = it; a.updateSensor() }
        if (!a.hasLightSensor()) note(brightness, R.string.no_sensor)
        slider(brightness, R.string.dim, s.dim, 0f, .9f) { s.dim = it }
        val night = section(groups, R.string.night)
        toggle(night, R.string.night_on, s.nightEnabled) { s.nightEnabled = it }
        time(night, R.string.night_start, true); time(night, R.string.night_end, false); color(night, true)
        slider(night, R.string.night_max, s.nightMax, .01f) { s.nightMax = it; a.clearBrightnessOverride() }
        note(night, R.string.night_note)
        val oled = section(groups, R.string.oled); toggle(oled, R.string.oled_shift, s.oled) { s.oled = it }; note(oled, R.string.oled_note)
        note(section(groups, R.string.controls), R.string.gesture_note)
        val other = section(groups, R.string.other)
        choice(other, R.string.language, arrayOf("Русский", "English"), if (s.language == "en") 1 else 0) {
            s.language = if (it == 1) "en" else "ru"; a.persist(); a.rebuildSettings()
        }
        note(other, R.string.charging_note); note(other, R.string.about)
        return page
    }
    private fun fillPresets(parent: LinearLayout) {
        parent.removeAllViews()
        parent.addView(label(a.getString(R.string.presets), 15f, muted))
        s.presets.forEachIndexed { index, preset ->
            parent.addView(button(preset.name) {
                a.trackDialog(AlertDialog.Builder(a).setTitle(preset.name).setItems(arrayOf(a.getString(R.string.apply), a.getString(R.string.rename), a.getString(R.string.delete))) { _, which ->
                    when (which) {
                        0 -> { s.visual = preset.visual.copy(); a.changed(); a.rebuildSettings() }
                        1 -> editPresetName(preset.name) { name -> s.presets[index].name = name; a.changed(); fillPresets(parent) }
                        2 -> { s.presets.removeAt(index); a.changed(); fillPresets(parent) }
                    }
                }.setNegativeButton(R.string.cancel, null).create())
            }, LinearLayout.LayoutParams(-1, -2))
        }
        parent.addView(button(a.getString(R.string.save_preset)) {
            if (s.presets.size >= 5) a.toast(R.string.five_presets)
            else editPresetName("") { name -> s.presets.add(Preset(name, s.visual.copy())); a.changed(); fillPresets(parent) }
        }, LinearLayout.LayoutParams(-1, -2))
    }
    private fun editPresetName(name: String, action: (String) -> Unit) {
        val input = EditText(a).apply { setSingleLine(true); setText(name); hint = a.getString(R.string.preset_name); filters = arrayOf(InputFilter.LengthFilter(40)) }
        val d = AlertDialog.Builder(a).setTitle(R.string.preset_name).setView(input)
            .setPositiveButton(R.string.done, null).setNegativeButton(R.string.cancel, null).create()
        a.trackDialog(d)
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val value = input.text.toString().trim()
            if (value.isEmpty()) input.error = a.getString(R.string.empty_name)
            else { action(value); d.dismiss() }
        }
    }
    fun buildWelcome(finish: () -> Unit): View {
        val card = column().apply {
            setBackgroundDrawable(background(0xff172125.toInt())); setPadding(a.dp(12), a.dp(8), a.dp(12), a.dp(8))
        }
        card.addView(label(a.getString(R.string.welcome), 18f, accent))
        val scroll = ScrollView(a); card.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val body = column(); scroll.addView(body)
        style(body); color(body)
        slider(body, R.string.brightness, s.brightness, .01f) { s.brightness = it }
        toggle(body, R.string.night, s.nightEnabled) { s.nightEnabled = it }
        toggle(body, R.string.hour24, s.visual.hour24) { s.visual.hour24 = it }
        choice(body, R.string.language, arrayOf("Русский", "English"), if (s.language == "en") 1 else 0) {
            s.language = if (it == 1) "en" else "ru"; a.persist(); a.rebuildWelcome()
        }
        val actions = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(button(a.getString(R.string.skip), finish), LinearLayout.LayoutParams(0, -2, 1f))
        actions.addView(button(a.getString(R.string.start), finish), LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(actions)
        return card
    }
}
