// SPDX-License-Identifier: AGPL-3.0-only
// UI copy and visual language adapted from DiAuto. See docs/THIRD_PARTY_NOTICES.md.
package com.shilapi.xcertplay

import android.Manifest
import android.app.AlertDialog
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioFormat
import android.media.AudioTrack
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.orchestration.WirelessHotspotMode
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** DiAuto's visual language, with a connection flow for an independent CarPlay receiver. */
class DiPlayActivity : ComponentActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private var page = "home"
    private var pendingCarHotspotSetup = false
    private var setupError: String? = null
    private var status: TextView? = null
    private var connectButton: Button? = null
    private var disconnectButton: Button? = null
    private var lastRunning: Boolean? = null
    private var pendingWireless = false
    private var initialLaunch = true
    private var notificationTransport = true
    private var exportInProgress = false
    private var navigationStreamType = 14
    private var testToneTrack: AudioTrack? = null
    private var toneStop: Runnable? = null
    private var exportButton: Button? = null
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        connect(notificationTransport)
    }
    private val tick = object : Runnable {
        override fun run() { refreshStatus(); handler.postDelayed(this, 1000) }
    }
    private val bluetoothPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) choosePhone() else permissionHelp(getString(R.string.nearby_devices), getString(R.string.allow_nearby_devices_so_diplay_can_connect_to_your_paired))
    }
    private val export = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) exportDiagnostics(uri)
    }

    private var languagePreferenceAtCreate = AppLocale.SYSTEM

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        languagePreferenceAtCreate = AppLocale.preference(this)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = BG
            window.navigationBarColor = BG
        }
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            hide(WindowInsetsCompat.Type.statusBars())
        }
        setupError = runCatching { DiPlayBootstrap.ensure(this) }.exceptionOrNull()?.let {
            android.util.Log.e("EadoPlaySetup", "CarPlay authentication could not be loaded", it)
            getString(R.string.setup_error_auth)
        }
        navigationStreamType = AirPlayPersistence.loadNavigationStreamType(this)
        pendingCarHotspotSetup = savedInstanceState?.getBoolean("pending_car_hotspot") ?: false
        page = savedInstanceState?.getString("page") ?: intent.getStringExtra("page") ?: "home"
        render()
        handleWirelessRecovery()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (page != "home") { page = "home"; render() }
                else { isEnabled = false; onBackPressedDispatcher.onBackPressed(); isEnabled = true }
            }
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); setIntent(intent)
        page = intent.getStringExtra("page") ?: "home"; render()
        handleWirelessRecovery()
    }
    override fun onSaveInstanceState(outState: Bundle) { outState.putString("page", page); outState.putBoolean("pending_car_hotspot", pendingCarHotspotSetup); super.onSaveInstanceState(outState) }
    override fun onConfigurationChanged(newConfig: Configuration) { super.onConfigurationChanged(newConfig); render() }
    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT < 33 && AppLocale.preference(this) != languagePreferenceAtCreate) {
            recreate()
            return
        }
        handler.removeCallbacks(tick); handler.post(tick)
        // Back from the car settings: refresh the car hotspot reminder on the home page.
        if (!initialLaunch && (page == "home" || page == "settings" || page == "connection")) render()
        if (initialLaunch) {
            initialLaunch = false
            if (setupError == null && !CarPlayBackgroundSession.hasSession() &&
                DiPlayPreferences.autoConnect(this) && intent.getStringExtra("page") == null) {
                handler.post { connect(AirPlayPersistence.loadWirelessEnabled(this)) }
            }
        }
    }
    override fun onPause() { handler.removeCallbacks(tick); super.onPause() }

    private fun render() {
        if (page == "settings") {
            renderSettingsPage()
            return
        }
        status = null; connectButton = null; disconnectButton = null; lastRunning = null
        val wide = resources.configuration.screenWidthDp >= 850
        val scroll = ScrollView(this).apply { setBackgroundColor(BG); isFillViewport = true; clipToPadding = false }
        val content = column().apply {
            val padH = if (wide) dp(28) else dp(32)
            val padV = if (wide) dp(16) else dp(24)
            setPadding(padH, padV, padH, padV)
        }
        scroll.addView(content)
        val header = row().apply { gravity = Gravity.CENTER_VERTICAL }
        val logoSize = if (wide) dp(32) else dp(36)
        val btnH = if (wide) dp(46) else dp(56)
        val btnW = if (wide) dp(116) else dp(130)
        header.addView(ImageView(this).apply { setImageResource(R.drawable.ic_carplay); contentDescription = getString(R.string.carplay) }, LinearLayout.LayoutParams(logoSize, logoSize))
        header.addView(label(getString(R.string.diplay), if (wide) 22 else 26, TEXT, true).apply { setPadding(dp(10), 0, 0, 0) }, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(button(if (page == "home") getString(R.string.car_home) else getString(R.string.back), false) {
            if (page == "home") startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
            else { page = "home"; render() }
        }, LinearLayout.LayoutParams(btnW, btnH))
        content.addView(header)
        content.addView(space(if (wide) 14 else 24))
        when (page) {
            "connection" -> connectionSetup(content)
            "settings" -> { renderSettingsPage(); return }
            "about" -> about(content)
            else -> home(content)
        }
        setContentView(scroll)
        refreshStatus()
    }

    private fun home(content: LinearLayout) {
        val wide = resources.configuration.screenWidthDp >= 850
        val body = column()

        // 左侧卡片：无线 CarPlay
        val card = card()
        card.addView(label(getString(R.string.wireless_carplay), 12, ACCENT, true).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) letterSpacing = .12f
        })
        status = label(getString(R.string.ready_when_you_are), 20, TEXT, true).apply { setPadding(0, dp(6), 0, dp(10)) }
        card.addView(status)
        connectButton = button(getString(R.string.connect_phone), true) {
            if (CarPlayBackgroundSession.hasSession()) openProjection()
            else connect(true)
        }
        card.addView(connectButton, matchButton(0, 50))
        val connectionHint = when (AirPlayPersistence.loadWirelessHotspotMode(this)) {
            WirelessHotspotMode.MANUAL -> getString(R.string.hotspot_hint_manual)
            WirelessHotspotMode.LOCAL_ONLY_HOTSPOT -> getString(R.string.hotspot_hint_local)
            else -> getString(R.string.hotspot_hint_p2p)
        }
        card.addView(label(connectionHint, 13, MUTED).apply { setPadding(0, dp(8), 0, 0) })
        if (carHotspotOff()) {
            card.addView(label(getString(R.string.msg_car_hotspot_off, AirPlayPersistence.loadManualHotspotSsid(this)), 13, WARNING).apply { setPadding(0, dp(8), 0, 0) })
            card.addView(button(getString(R.string.open_car_hotspot_settings), false) { openCarWifiSettings() }, matchButton(8, 46))
        }
        card.addView(button(getString(R.string.choose_iphone), false) { choosePhone() }, matchButton(10, 46))
        disconnectButton = button(getString(R.string.disconnect), false) {
            disconnectButton?.isEnabled = false
            CarPlayBackgroundSession.stop { runOnUiThread { refreshStatus() } }
        }.apply { visibility = View.GONE }
        card.addView(disconnectButton, matchButton(8, 46))

        // 右侧卡片：有线连接与设置
        val rightCard = card()
        rightCard.addView(label(getString(R.string.connect_with_usb), 12, ACCENT, true).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) letterSpacing = .12f
        })
        rightCard.addView(button(getString(R.string.connect_with_usb), false) { connect(false) }, matchButton(6, 50))
        rightCard.addView(label(getString(R.string.plug_your_iphone_into_a_usb_data_port_allow_carplay_when_y), 12, MUTED).apply {
            setPadding(0, dp(4), 0, dp(10))
        })
        rightCard.addView(button(getString(R.string.settings), false) { page = "settings"; render() }, matchButton(0, 50))
        rightCard.addView(label(getString(R.string.make_diplay_feel_right_for_your_car), 12, MUTED).apply {
            setPadding(0, dp(4), 0, dp(10))
        })
        rightCard.addView(label("${getString(R.string.home_public_preview)}${version()}", 11, MUTED).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) letterSpacing = .08f
        })

        if (wide) {
            val dualRow = row().apply {
                gravity = Gravity.TOP
                addView(card, LinearLayout.LayoutParams(0, -2, 1.15f))
                addView(space(24), LinearLayout.LayoutParams(dp(24), 1))
                addView(rightCard, LinearLayout.LayoutParams(0, -2, 1f))
            }
            body.addView(dualRow)
        } else {
            body.addView(card)
            body.addView(space(16))
            body.addView(rightCard)
        }
        setupError?.let { body.addView(label(it, 16, WARNING).apply { setPadding(0, dp(16), 0, 0) }) }
        content.addView(body)
    }

    private var settingsCategory = "overview"

    private fun renderSettingsPage() {
        status = null; connectButton = null; disconnectButton = null; lastRunning = null

        val root = column().apply {
            setBackgroundColor(BG)
            layoutParams = LinearLayout.LayoutParams(-1, -1)
        }

        // 顶部栏
        val topBar = row().apply {
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.rgb(11, 16, 27))
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        val backBtn = Button(this).apply {
            text = getString(R.string.back)
            isAllCaps = false
            textSize = 15f
            setTextColor(TEXT)
            background = rounded(SURFACE, BORDER)
            minHeight = dp(40)
            setPadding(dp(20), 0, dp(20), 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) stateListAnimator = null
            setOnClickListener { page = "home"; render() }
        }
        topBar.addView(backBtn, LinearLayout.LayoutParams(dp(84), dp(40)))

        val titleView = label(getString(R.string.settings), 22, TEXT, true).apply {
            setPadding(dp(16), 0, 0, 0)
        }
        topBar.addView(titleView, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(topBar)

        val divider = View(this).apply {
            setBackgroundColor(BORDER)
            layoutParams = LinearLayout.LayoutParams(-1, dp(1))
        }
        root.addView(divider)

        // 左右主分栏 Master-Detail
        val body = row().apply {
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
        }

        // 左侧分类侧边栏
        val sidebarScroll = ScrollView(this).apply {
            setBackgroundColor(Color.rgb(14, 20, 31))
            isFillViewport = true
            clipToPadding = false
        }
        val sidebarList = column().apply {
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        sidebarScroll.addView(sidebarList)
        body.addView(sidebarScroll, LinearLayout.LayoutParams(dp(220), -1))

        // 右侧详情区
        val detailScroll = ScrollView(this).apply {
            setBackgroundColor(BG)
            isFillViewport = true
            clipToPadding = false
        }
        val detailContent = column().apply {
            setPadding(dp(24), dp(16), dp(28), dp(28))
        }
        detailScroll.addView(detailContent)
        body.addView(detailScroll, LinearLayout.LayoutParams(0, -1, 1f))

        root.addView(body)
        setContentView(root)

        data class SettingsNavTab(val id: String, val titleRes: Int, val iconRes: Int)
        val tabs = listOf(
            SettingsNavTab("overview", R.string.settings_nav_overview, R.drawable.ic_dp_about),
            SettingsNavTab("connection", R.string.settings_nav_connection, R.drawable.ic_dp_connection),
            SettingsNavTab("display", R.string.settings_nav_display, R.drawable.ic_dp_display),
            SettingsNavTab("audio", R.string.settings_nav_audio, R.drawable.ic_dp_audio),
            SettingsNavTab("diagnostics", R.string.settings_nav_diagnostics, R.drawable.ic_dp_diagnostics),
            SettingsNavTab("advanced", R.string.settings_nav_advanced, R.drawable.ic_dp_automation),
        )

        val navViews = mutableListOf<Pair<String, View>>()

        fun updateSidebar() {
            navViews.forEach { (id, view) ->
                val isSelected = id == settingsCategory
                val bar = view.findViewWithTag<View>("indicator")
                val icon = view.findViewWithTag<ImageView>("icon")
                val text = view.findViewWithTag<TextView>("text")
                bar?.visibility = if (isSelected) View.VISIBLE else View.INVISIBLE
                view.background = if (isSelected) {
                    rounded(Color.rgb(27, 45, 71), Color.rgb(45, 75, 115))
                } else {
                    GradientDrawable().apply { setColor(Color.TRANSPARENT) }
                }
                text?.setTextColor(if (isSelected) Color.WHITE else Color.rgb(155, 170, 190))
                icon?.drawable?.let {
                    DrawableCompat.setTint(DrawableCompat.wrap(it), if (isSelected) Color.WHITE else Color.rgb(155, 170, 190))
                }
            }
        }

        fun showCategory(targetCategory: String) {
            settingsCategory = targetCategory
            updateSidebar()
            detailContent.removeAllViews()
            when (settingsCategory) {
                "connection" -> renderConnectionCategory(detailContent)
                "display" -> renderDisplayCategory(detailContent)
                "audio" -> renderAudioCategory(detailContent)
                "diagnostics" -> renderDiagnosticsCategory(detailContent)
                "advanced" -> renderAdvancedCategory(detailContent)
                else -> renderOverviewCategory(detailContent) { showCategory(it) }
            }
            detailScroll.scrollTo(0, 0)
        }

        tabs.forEach { tab ->
            val itemView = row().apply {
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(46)
                setPadding(dp(8), 0, dp(12), 0)
                isClickable = true
                isFocusable = true
            }
            val indicator = View(this).apply {
                tag = "indicator"
                background = GradientDrawable().apply {
                    setColor(Color.rgb(78, 155, 250))
                    cornerRadius = dp(2).toFloat()
                }
            }
            itemView.addView(indicator, LinearLayout.LayoutParams(dp(3), dp(18)).apply { marginEnd = dp(8) })

            val iconView = ImageView(this).apply {
                tag = "icon"
                AppCompatResources.getDrawable(this@DiPlayActivity, tab.iconRes)?.let {
                    setImageDrawable(DrawableCompat.wrap(it.mutate()))
                }
            }
            itemView.addView(iconView, LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginEnd = dp(10) })

            val textView = TextView(this).apply {
                tag = "text"
                text = getString(tab.titleRes)
                textSize = 15f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            }
            itemView.addView(textView, LinearLayout.LayoutParams(0, -2, 1f))

            itemView.setOnClickListener { showCategory(tab.id) }
            sidebarList.addView(itemView, LinearLayout.LayoutParams(-1, dp(46)).apply { bottomMargin = dp(4) })
            navViews.add(tab.id to itemView)
        }

        updateSidebar()
        showCategory(settingsCategory)
        refreshStatus()
    }

    private fun renderOverviewCategory(content: LinearLayout, onNavigate: (String) -> Unit) {
        content.addView(label(getString(R.string.settings_nav_overview), 28, TEXT, true))
        content.addView(label(getString(R.string.settings_overview_desc), 14, MUTED).apply { setPadding(0, dp(4), 0, dp(18)) })

        section(content, getString(R.string.settings_overview_iphone_title), R.drawable.ic_carplay) { card ->
            card.addView(label(getString(R.string.settings_overview_iphone_desc), 14, MUTED))
            val currentPhone = DiPlayPreferences.phoneName(this).ifEmpty { getString(R.string.choose_iphone) }
            card.addView(button("${getString(R.string.choose_iphone_prefix)}$currentPhone", false) { choosePhone() }, matchButton(12, 54))
        }

        section(content, getString(R.string.settings_overview_your_settings), R.drawable.ic_dp_about) { card ->
            fun quickRow(title: String, desc: String, target: String) {
                val rowView = row().apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(10), 0, dp(10))
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { onNavigate(target) }
                }
                val textCol = column()
                textCol.addView(label(title, 16, TEXT, true))
                textCol.addView(label(desc, 13, MUTED).apply { setPadding(0, dp(3), 0, 0) })
                rowView.addView(textCol, LinearLayout.LayoutParams(0, -2, 1f))
                rowView.addView(label(">", 18, MUTED, true).apply { setPadding(dp(8), 0, dp(8), 0) })
                card.addView(rowView)
            }
            quickRow(getString(R.string.settings_nav_connection), getString(R.string.settings_overview_quick_conn_desc), "connection")
            card.addView(View(this).apply { setBackgroundColor(BORDER) }, LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(4); bottomMargin = dp(4) })
            quickRow(getString(R.string.settings_nav_display), getString(R.string.settings_overview_quick_disp_desc), "display")
            card.addView(View(this).apply { setBackgroundColor(BORDER) }, LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(4); bottomMargin = dp(4) })
            quickRow(getString(R.string.settings_nav_audio), getString(R.string.settings_overview_quick_audio_desc), "audio")
        }
    }

    private fun renderConnectionCategory(content: LinearLayout) {
        content.addView(label(getString(R.string.connection_setup), 28, TEXT, true))
        content.addView(label(getString(R.string.set_up_once_your_details_stay_saved_for_the_next_drive_cha), 14, MUTED).apply { setPadding(0, dp(4), 0, dp(18)) })

        section(content, getString(R.string.connection_setup), R.drawable.ic_dp_connection) { card ->
            card.addView(label(getString(R.string.choose_how_to_connect_follow_the_setup_steps_and_save_your), 15, MUTED))
            card.addView(button(getString(R.string.open_connection_setup), false) { page = "connection"; render() }, matchButton(12, 54))
        }

        section(content, getString(R.string.automatic_connection), R.drawable.ic_dp_automation) { card ->
            toggle(card, getString(R.string.connect_when_diplay_opens), getString(R.string.use_your_last_connection_type_and_selected_iphone), DiPlayPreferences.autoConnect(this)) { DiPlayPreferences.saveAutoConnect(this, it) }
            toggle(card, getString(R.string.open_after_the_car_starts), getString(R.string.availability_depends_on_your_head_unit_s_startup_settings), AirPlayPersistence.loadAutoStartOnBoot(this)) { AirPlayPersistence.saveAutoStartOnBoot(this, it) }
            card.addView(button("${getString(R.string.choose_iphone_prefix)}${DiPlayPreferences.phoneName(this)}", false) { choosePhone() }, matchButton(12, 54))
        }

        section(content, getString(R.string.built_in_car_hotspot), R.drawable.ic_dp_connection) { card ->
            val ssid = AirPlayPersistence.loadManualHotspotSsid(this)
            card.addView(label("${getString(R.string.hotspot_wireless_prefix)} ${if (ssid.isEmpty()) getString(R.string.preview_empty) else ssid}", 15, TEXT))
            card.addView(button(getString(R.string.open_car_hotspot_settings), false) { openCarWifiSettings() }, matchButton(12, 54))
        }
    }

    private fun renderDisplayCategory(content: LinearLayout) {
        content.addView(label(getString(R.string.display_and_performance), 28, TEXT, true))
        content.addView(label(getString(R.string.apply_reconnects_carplay_for_size_resolution_music_buffer), 14, MUTED).apply { setPadding(0, dp(4), 0, dp(18)) })

        section(content, getString(R.string.display_and_performance), R.drawable.ic_dp_display) { card ->
            carPlaySizeControl(card)
            choice(card, getString(R.string.resolution), listOf(getString(R.string.resolution_native), getString(R.string.s_80_lighter_load), getString(R.string.s_60_lightest_load)), listOf(10, 8, 6).indexOf(AirPlayPersistence.loadDisplayScaleTenths(this)).coerceAtLeast(0)) { AirPlayPersistence.saveDisplayScaleTenths(this, listOf(10, 8, 6)[it]) }
            choice(card, getString(R.string.frame_rate), listOf(getString(R.string.s_30_fps_lighter_load), getString(R.string.s_60_fps_smoother_motion)), if (AirPlayPersistence.loadFps(this) == 60) 1 else 0) { AirPlayPersistence.saveFps(this, if (it == 1) 60 else 30) }
            choice(
                card,
                getString(R.string.video_renderer),
                listOf(getString(R.string.renderer_surface_view), getString(R.string.renderer_texture_view)),
                if (AirPlayPersistence.loadUseSurfaceView(this)) 0 else 1,
            ) { AirPlayPersistence.saveUseSurfaceView(this, it == 0) }
            toggle(card, getString(R.string.efficient_video), getString(R.string.use_hevc_leave_off_for_the_widest_head_unit_compatibility), AirPlayPersistence.loadHevcEnabled(this)) { AirPlayPersistence.saveHevcEnabled(this, it) }
            toggle(card, getString(R.string.right_hand_drive), getString(R.string.place_carplay_s_controls_closer_to_the_driver), AirPlayPersistence.loadRightHandDrive(this)) { AirPlayPersistence.saveRightHandDrive(this, it) }
            toggle(card, getString(R.string.full_screen), getString(R.string.hide_the_car_s_system_bars_while_carplay_is_open), AirPlayPersistence.loadHideTopBar(this) && AirPlayPersistence.loadHideBottomBar(this)) {
                AirPlayPersistence.saveHideTopBar(this, it); AirPlayPersistence.saveHideBottomBar(this, it)
            }
        }
    }

    private fun renderAudioCategory(content: LinearLayout) {
        content.addView(label(getString(R.string.audio_routing), 28, TEXT, true))
        content.addView(label(getString(R.string.audio_settings_description), 14, MUTED).apply { setPadding(0, dp(4), 0, dp(18)) })

        section(content, getString(R.string.playback_volume), R.drawable.ic_dp_audio) { card ->
            card.addView(label(getString(R.string.playback_volume_description), 14, MUTED).apply {
                setPadding(0, 0, 0, dp(12))
            })
            val percentages = listOf(20, 30, 40, 50, 60, 70, 75, 80, 90, 100)
            fun selectedIndex(percent: Int): Int = percentages.indexOf(percent)
                .takeIf { it >= 0 }
                ?: percentages.lastIndex
            choice(
                card,
                getString(R.string.music_volume),
                percentages.map { "$it%" },
                selectedIndex(AirPlayPersistence.loadMediaVolumePercent(this)),
            ) { AirPlayPersistence.saveMediaVolumePercent(this, percentages[it]) }
            choice(
                card,
                getString(R.string.navigation_volume),
                percentages.map { "$it%" },
                selectedIndex(AirPlayPersistence.loadNavigationVolumePercent(this)),
            ) { AirPlayPersistence.saveNavigationVolumePercent(this, percentages[it]) }
        }

        section(content, getString(R.string.music_buffer), R.drawable.ic_dp_audio) { card ->
            val bufferPresets = com.shilapi.xcertplay.media.MediaAudioBuffer.presets
            choice(card, getString(R.string.music_buffer), listOf(getString(R.string.s_300_ms_default), getString(R.string.s_500_ms), getString(R.string.s_1000_ms_most_stable)),
                bufferPresets.indexOf(AirPlayPersistence.loadMediaBufferMillis(this)).coerceAtLeast(0)) {
                AirPlayPersistence.saveMediaBufferMillis(this, bufferPresets[it])
            }
        }

        section(content, getString(R.string.navigation_stream_type), R.drawable.ic_dp_audio) { card ->
            val channelTitle = label(getString(R.string.navigation_stream_type), 18, TEXT, true)
            val channelHint = label(getString(R.string.tap_a_number_to_test_vehicle_speakers), 14, MUTED)
            val grid = channelSelector()
            val saveButton = button(getString(R.string.save), true) {
                AirPlayPersistence.saveNavigationStreamType(this, navigationStreamType)
                toast(getString(R.string.audio_saved_value, navigationStreamType))
            }
            fun applyChannelEnabled(enabled: Boolean) {
                val alpha = if (enabled) 1f else 0.4f
                listOf(channelTitle, channelHint, saveButton).forEach {
                    it.isEnabled = enabled
                    it.alpha = alpha
                }
                for (i in 0 until grid.childCount) {
                    grid.getChildAt(i).let { child ->
                        child.isEnabled = enabled
                        child.alpha = alpha
                    }
                }
            }
            val aaosSupported = resources.getBoolean(R.bool.config_advanced_audio_channel_mapping)
            if (aaosSupported) {
                toggle(card, getString(R.string.advanced_audio_channel_mapping),
                    getString(R.string.use_usage_content_type_routing_instead_of_stream_type),
                    AirPlayPersistence.loadAdvancedAudioChannelMapping(this)) {
                    AirPlayPersistence.saveAdvancedAudioChannelMapping(this, it)
                    applyChannelEnabled(!it)
                }
            }
            card.addView(channelTitle)
            card.addView(channelHint)
            card.addView(grid)
            card.addView(saveButton, matchButton(12, 56))
            if (aaosSupported) applyChannelEnabled(!AirPlayPersistence.loadAdvancedAudioChannelMapping(this))
        }
    }

    private fun renderDiagnosticsCategory(content: LinearLayout) {
        content.addView(label(getString(R.string.diagnostics), 28, TEXT, true))
        content.addView(label("导出连接诊断日志，为排查问题提供依据。", 14, MUTED).apply { setPadding(0, dp(4), 0, dp(18)) })

        section(content, getString(R.string.diagnostics), R.drawable.ic_dp_diagnostics) { card ->
            exportButton = button(if (exportInProgress) getString(R.string.saving_report) else getString(R.string.save_diagnostic_report), false) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) exportDiagnostics()
                else chooseReportDestination()
            }.apply { isEnabled = !exportInProgress }
            card.addView(exportButton, matchButton(10, 56))
            card.addView(button(getString(R.string.choose_save_location), false) { chooseReportDestination() }, matchButton(10, 56))
            val destination = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) getString(R.string.reports_save_to_downloads_diplay) else getString(R.string.choose_where_to_save_your_report)
            card.addView(label(destination + getString(R.string.nothing_is_sent_automatically_protocol_payloads_and_creden), 14, MUTED).apply { setPadding(0, dp(12), 0, 0) })
        }
    }

    private fun renderAdvancedCategory(content: LinearLayout) {
        content.addView(label(getString(R.string.settings_nav_advanced), 28, TEXT, true))
        content.addView(label("系统设置与 EadoPlay 专版信息。", 14, MUTED).apply { setPadding(0, dp(4), 0, dp(18)) })

        section(content, getString(R.string.language_section_title), R.drawable.ic_dp_about) { card ->
            card.addView(label(getString(R.string.language_hint), 14, MUTED))
            val current = AppLocale.preference(this)
            val languageButton = button("${getString(R.string.language_app_language)} · ${AppLocale.displayName(this, current)}", false) { }
            languageButton.setOnClickListener { AppLocale.showPicker(this) }
            card.addView(languageButton, matchButton(12, 56))
        }

        section(content, getString(R.string.permissions_and_connection_help), R.drawable.ic_dp_permissions) { card ->
            card.addView(label(getString(R.string.nearby_devices_connects_your_iphone_microphone_enables_sir), 15, MUTED))
            card.addView(button(getString(R.string.app_permissions), false) { openSystem(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }, matchButton(12, 56))
            card.addView(button(getString(R.string.bluetooth_settings), false) { openSystem(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }, matchButton(10, 56))
        }

        section(content, "${getString(R.string.about_public_preview_prefix)}${version()}", R.drawable.ic_eado) { card ->
            card.addView(label("专为 2018 长安逸动深度调优的独立 CarPlay 接收端。", 16, TEXT))
            card.addView(label(getString(R.string.receiver_based_on_xcertplay_licensed_under_gpl_3_0_diplay), 14, MUTED).apply { setPadding(0, dp(8), 0, 0) })
        }
    }

    private fun about(content: LinearLayout) {
        content.addView(label(getString(R.string.diplay), 40, TEXT, true))
        content.addView(label(getString(R.string.carplay_at_home_in_your_car), 20, MUTED).apply { setPadding(0, dp(8), 0, dp(24)) })
        section(content, "${getString(R.string.about_public_preview_prefix)}${version()}") { card ->
            card.addView(label(getString(R.string.an_independent_carplay_receiver_for_android_head_units_wir), 17, TEXT))
        }
        section(content, getString(R.string.made_possible_by_open_source)) { card ->
            card.addView(label(getString(R.string.receiver_based_on_xcertplay_licensed_under_gpl_3_0_diplay), 16, MUTED))
        }
    }

    // The car hotspot link needs the hotspot on; EadoPlay only checks it (turning it on needs ADB-only permission).
    private fun carHotspotOff(): Boolean =
        AirPlayPersistence.loadWirelessHotspotMode(this) == WirelessHotspotMode.MANUAL &&
            com.shilapi.xcertplay.network.CarHotspotStatus.isEnabled(this) == false

    private fun carHotspotOffDialog() {
        AlertDialog.Builder(this).setTitle(getString(R.string.car_hotspot_is_off))
            .setMessage(getString(R.string.msg_car_hotspot_connect, AirPlayPersistence.loadManualHotspotSsid(this)))
            .setPositiveButton(getString(R.string.open_car_settings)) { _, _ -> openCarWifiSettings() }
            .setNeutralButton(getString(R.string.connect)) { _, _ -> connect(true) }
            .setNegativeButton(getString(R.string.cancel), null).show()
    }

    private fun openCarWifiSettings() {
        val hotspot = Intent("com.android.settings.WIFI_TETHER_SETTINGS")
        if (packageManager.resolveActivity(hotspot, 0) == null) {
            openSystem(Intent(Settings.ACTION_WIRELESS_SETTINGS))
            return
        }
        if (runCatching { startActivity(hotspot) }.isSuccess) return
        openSystem(Intent(Settings.ACTION_WIRELESS_SETTINGS))
    }

    private fun openCarClientWifiSettings() {
        openSystem(Intent(Settings.ACTION_WIFI_SETTINGS))
    }

    private fun connectionSetup(content: LinearLayout) {
        content.addView(label(getString(R.string.connection_setup), 34, TEXT, true))
        content.addView(label(getString(R.string.set_up_once_your_details_stay_saved_for_the_next_drive_cha), 17, MUTED).apply { setPadding(0, dp(8), 0, dp(24)) })
        section(content, getString(R.string.s_1_choose_your_connection)) { card -> wirelessLinkControls(card) }
        section(content, getString(R.string.s_2_pair_your_iphone)) { card ->
            card.addView(label(getString(R.string.keep_bluetooth_and_wi_fi_on_your_iphone_pair_with_the_car), 16, MUTED))
            card.addView(button("${getString(R.string.choose_iphone_prefix)}${DiPlayPreferences.phoneName(this)}", false) { choosePhone() }, matchButton(12, 60))
            card.addView(button(getString(R.string.review_app_permissions), false) {
                openSystem(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            }, matchButton(12, 60))
        }
        section(content, getString(R.string.s_3_connect)) { card ->
            card.addView(label(getString(R.string.return_from_car_settings_to_diplay_then_connect_accept_the), 16, MUTED))
            card.addView(button(getString(R.string.connect_phone), true) { connect(true) }, matchButton(12, 60))
        }
        section(content, getString(R.string.prefer_a_cable)) { card ->
            card.addView(label(getString(R.string.use_a_usb_data_cable_and_the_car_s_usb_data_port_unlock_yo), 16, MUTED))
            card.addView(button(getString(R.string.connect_with_usb), false) { connect(false) }, matchButton(12, 60))
        }
    }

    private fun wirelessLinkControls(parent: LinearLayout) {
        val mode = if (pendingCarHotspotSetup) WirelessHotspotMode.MANUAL else AirPlayPersistence.loadWirelessHotspotMode(this)
        val modes = mutableListOf(
            Triple(
                WirelessHotspotMode.MANUAL,
                getString(R.string.built_in_car_hotspot),
                getString(R.string.hotspot_mode_manual_desc),
            ),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            modes += Triple(
                WirelessHotspotMode.WIFI_P2P,
                getString(R.string.wifi_direct),
                getString(R.string.hotspot_mode_p2p_desc),
            )
        }
        val wide = resources.configuration.screenWidthDp >= 850
        val choices = if (wide) row().apply { gravity = Gravity.TOP } else column()
        parent.addView(choices)
        modes.forEachIndexed { index, (candidate, title, description) ->
            val option = column()
            choices.addView(option, if (wide) LinearLayout.LayoutParams(0, -2, 1f).apply {
                if (index > 0) marginStart = dp(16)
            } else LinearLayout.LayoutParams(-1, -2))
            option.addView(button("${if (mode == candidate) "✓  " else ""}$title", mode == candidate) {
                if (candidate == WirelessHotspotMode.MANUAL) {
                    pendingCarHotspotSetup = true
                    render()
                } else {
                    pendingCarHotspotSetup = false
                    applyWirelessLink(candidate)
                }
            }, matchButton(12, 60))
            option.addView(label(description, 15, MUTED).apply { setPadding(0, dp(6), 0, dp(12)) })
        }
        if (mode == WirelessHotspotMode.MANUAL) {
            parent.addView(label(getString(R.string.hotspot_setup), 22, TEXT, true))
            parent.addView(label(getString(R.string.s_1_open_car_hotspot_settings_turn_the_hotspot_on_and_sele), 16, MUTED).apply { setPadding(0, dp(8), 0, dp(12)) })
            parent.addView(button(getString(R.string.open_car_hotspot_settings), false) { openCarWifiSettings() }, matchButton(0, 60))
            parent.addView(button(if (pendingCarHotspotSetup) getString(R.string.save_hotspot_details_and_use_this_mode) else "${getString(R.string.edit_saved_hotspot_prefix)}${storedSsid()}", false) {
                askHotspotCredentials { ssid, password ->
                    saveHotspotCredentials(ssid, password)
                    pendingCarHotspotSetup = false
                    applyWirelessLink(WirelessHotspotMode.MANUAL)
                }
            }, matchButton(12, 60))
            parent.addView(label(if (pendingCarHotspotSetup) getString(R.string.finish_setup_save_your_hotspot_details_to_use_this_mode) else if (carHotspotOff()) getString(R.string.hotspot_details_off) else getString(R.string.hotspot_details_saved), 15, if (carHotspotOff()) WARNING else MUTED).apply { setPadding(0, dp(12), 0, 0) })
        } else {
            parent.addView(label(getString(R.string.turn_the_car_s_wi_fi_switch_on_allow_location_nearby_devic), 16, MUTED))
            parent.addView(button(getString(R.string.open_car_wi_fi_settings), false) { openCarClientWifiSettings() }, matchButton(12, 60))
        }
    }

    private fun storedSsid() = AirPlayPersistence.loadManualHotspotSsid(this)
    private fun storedPassword() = AirPlayPersistence.loadManualHotspotPassphrase(this)
    private fun hotspotError(ssid: String, password: String) =
        com.shilapi.xcertplay.orchestration.ManualHotspotValidation.error(ssid, password)?.let { getString(it.messageResource()) }

    private fun saveHotspotCredentials(ssid: String, password: String) {
        AirPlayPersistence.saveManualHotspotSsid(this, ssid)
        AirPlayPersistence.saveManualHotspotPassphrase(this, password)
        AirPlayPersistence.saveManualHotspotSecurity(this,
            com.shilapi.xcertplay.orchestration.ManualHotspotValidation.securityFor(password))
        AirPlayPersistence.saveManualHotspotBand(this, com.shilapi.xcertplay.orchestration.ManualHotspotBand.AUTO)
        AirPlayPersistence.saveManualHotspotChannel(this, 0)
    }

    private fun askHotspotCredentials(done: (String, String) -> Unit) {
        val fields = column().apply { setPadding(dp(24), dp(12), dp(24), dp(12)) }
        fields.addView(label(getString(R.string.copy_these_from_the_car_s_hotspot_settings_use_5_ghz_if_av), 16, MUTED))
        val ssid = EditText(this).apply { hint = getString(R.string.hotspot_name); setText(storedSsid()); setSingleLine() }
        val password = EditText(this).apply {
            hint = getString(R.string.hotspot_password); setText(storedPassword()); setSingleLine()
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        ssid.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_NEXT or android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI
        password.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE or android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI
        fun hideKeyboard() {
            val token = password.windowToken ?: ssid.windowToken
            (this.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                .hideSoftInputFromWindow(token, 0)
            ssid.clearFocus(); password.clearFocus()
        }
        ssid.setOnEditorActionListener { _, action, _ ->
            if (action == android.view.inputmethod.EditorInfo.IME_ACTION_NEXT) { password.requestFocus(); true } else false
        }
        password.setOnEditorActionListener { _, action, _ ->
            if (action == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) { hideKeyboard(); true } else false
        }
        fields.addView(ssid); fields.addView(password)
        fields.addView(CheckBox(this).apply {
            text = getString(R.string.show_password)
            setOnCheckedChangeListener { _, checked ->
                password.transformationMethod = if (checked) null else android.text.method.PasswordTransformationMethod.getInstance()
                password.setSelection(password.text.length)
            }
        })
        val error = label("", 14, WARNING)
        error.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        fields.addView(error)
        val dialog = AlertDialog.Builder(this).setTitle(getString(R.string.car_hotspot_details))
            .setView(ScrollView(this).apply { addView(fields) })
            .setPositiveButton(getString(R.string.save_details), null).setNegativeButton(getString(R.string.cancel)) { _, _ -> hideKeyboard() }
            .setNeutralButton(getString(R.string.hide_keyboard), null).create()
        dialog.setOnShowListener {
            dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener { hideKeyboard() }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = ssid.text.toString().trim()
                val secret = password.text.toString()
                val problem = hotspotError(name, secret)
                if (problem != null) error.text = problem
                else { hideKeyboard(); dialog.dismiss(); done(name, secret) }
            }
        }
        dialog.show()
    }

    private fun applyWirelessLink(mode: WirelessHotspotMode) {
        AirPlayPersistence.saveWirelessHotspotMode(this, mode)
        render()
        toast(getString(R.string.saved_for_your_next_connection))
    }

    private fun textInput(title: String, current: String, secret: Boolean, save: (String) -> Unit) {
        val input = EditText(this).apply {
            setText(current)
            setSingleLine()
            inputType = if (secret) {
                android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            } else {
                android.text.InputType.TYPE_CLASS_TEXT
            }
        }
        AlertDialog.Builder(this).setTitle(title).setView(input)
            .setPositiveButton(getString(R.string.save)) { _, _ -> save(input.text.toString().let { if (secret) it else it.trim() }) }
            .setNegativeButton(getString(R.string.cancel), null).show()
    }

    private fun carPlaySizeControl(parent: LinearLayout) {
        val sizes = com.shilapi.xcertplay.airplay.CarPlaySize.entries
        val current = com.shilapi.xcertplay.airplay.CarPlaySize.fromWidthMillimeters(AirPlayPersistence.loadWidthPhysicalMm(this))
        choice(parent, getString(R.string.carplay_size), sizes.map { it.localizedLabel(this) }, sizes.indexOf(current)) {
            AirPlayPersistence.saveWidthPhysicalMm(this, sizes[it].widthMillimeters)
        }
        parent.addView(label(getString(R.string.changes_the_size_of_carplay_icons_and_text_applying_a_size), 14, MUTED).apply {
            setPadding(0, 0, 0, dp(18))
        })
    }

    private fun connect(wireless: Boolean) {
        if (wireless && pendingCarHotspotSetup) { toast(getString(R.string.save_your_hotspot_details_in_connection_setup_first)); page = "connection"; render(); return }
        if (setupError != null) { toast(setupError!!); return }
        if (wireless && AirPlayPersistence.loadWirelessHotspotMode(this) == WirelessHotspotMode.MANUAL &&
            hotspotError(storedSsid(), storedPassword()) != null) {
            pendingCarHotspotSetup = true
            page = "connection"
            render()
            toast(getString(R.string.save_the_name_and_password_from_the_car_s_hotspot_settings))
            return
        }
        if (wireless && carHotspotOff()) { carHotspotOffDialog(); return }
        if (wireless && DiPlayPreferences.phoneAddress(this) == null) {
            pendingWireless = true; choosePhone(); return
        }
        val preferences = getSharedPreferences("diplay", MODE_PRIVATE)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED && !preferences.getBoolean("notification_asked", false)) {
            preferences.edit().putBoolean("notification_asked", true).apply()
            notificationTransport = wireless
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        val open = {
            AirPlayPersistence.saveWirelessEnabled(this, wireless)
            openProjection()
        }
        if (CarPlayBackgroundSession.hasSession()) CarPlayBackgroundSession.stop { runOnUiThread { open() } }
        else open()
    }
    private fun openProjection() {
        startActivity(Intent(this, CarPlayHostActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
    }
    @android.annotation.SuppressLint("MissingPermission")
    private fun choosePhone() {
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            bluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT); return
        }
        val adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        if (adapter == null || !adapter.isEnabled) {
            AlertDialog.Builder(this).setTitle(getString(R.string.turn_on_bluetooth))
                .setMessage(getString(R.string.enable_the_car_s_bluetooth_and_pair_your_iphone_first))
                .setPositiveButton(getString(R.string.open_bluetooth)) { _, _ -> openSystem(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
                .setNegativeButton(getString(R.string.later), null).show(); return
        }
        val devices = runCatching { adapter.bondedDevices.sortedBy { it.name ?: "" } }.getOrDefault(emptyList())
        if (devices.isEmpty()) {
            AlertDialog.Builder(this).setTitle(getString(R.string.pair_your_iphone))
                .setMessage(getString(R.string.on_your_iphone_open_settings_bluetooth_and_pair_with_the_c))
                .setPositiveButton(getString(R.string.open_bluetooth)) { _, _ -> openSystem(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
                .setNegativeButton(getString(R.string.got_it), null).show(); return
        }
        AlertDialog.Builder(this).setTitle(getString(R.string.choose_your_iphone))
            .setItems(devices.map { device ->
                val name = device.name ?: getString(R.string.paired_device)
                if (devices.count { it.name == device.name } > 1) "$name · ${device.address.takeLast(5)}" else name
            }.toTypedArray()) { _, index ->
                val device = devices[index]
                DiPlayPreferences.savePhone(this, device.address, device.name ?: "iPhone")
                val start = pendingWireless; pendingWireless = false
                render()
                if (start) connect(true)
            }.setNeutralButton(getString(R.string.pair_another)) { _, _ -> openSystem(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
            .setNegativeButton(getString(R.string.cancel)) { _, _ -> pendingWireless = false }.show()
    }

    private fun wirelessHelp() {
        AlertDialog.Builder(this).setTitle(getString(R.string.wireless_connection_help))
            .setMessage(getString(R.string.pair_your_iphone_with_the_car_s_bluetooth_keep_wi_fi_on_an))
            .setPositiveButton(getString(R.string.got_it), null)
            .setNeutralButton(getString(R.string.reset_carplay_wi_fi)) { _, _ ->
                confirmWirelessReset()
            }.show()
    }

    private fun handleWirelessRecovery() {
        if (page != "wireless-recovery") return
        page = "home"; render()
        confirmWirelessReset()
    }

    private fun confirmWirelessReset() {
        AlertDialog.Builder(this).setTitle(getString(R.string.reset_carplay_wi_fi_2))
            .setMessage(getString(R.string.this_ends_the_existing_wi_fi_direct_connection_including_o))
            .setPositiveButton(getString(R.string.reset_and_connect)) { _, _ ->
                CarPlayBackgroundSession.stop { runOnUiThread { resetWirelessGroup() } }
            }.setNegativeButton(getString(R.string.cancel), null).show()
    }

    private fun resetWirelessGroup() {
        val manager = getSystemService(Context.WIFI_P2P_SERVICE) as? android.net.wifi.p2p.WifiP2pManager
        if (manager == null) { toast(getString(R.string.this_head_unit_does_not_support_wi_fi_direct)); return }
        val channel = manager.initialize(this, mainLooper, null)
        try {
            manager.requestGroupInfo(channel) { group ->
                if (group == null) { closeP2pChannel(channel); connect(true); return@requestGroupInfo }
                manager.removeGroup(channel, object : android.net.wifi.p2p.WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        val deadline = android.os.SystemClock.elapsedRealtime() + 4000
                        fun waitUntilRemoved() {
                            manager.requestGroupInfo(channel) { remaining ->
                                when {
                                    remaining == null -> { closeP2pChannel(channel); if (!isFinishing && (Build.VERSION.SDK_INT < 17 || !isDestroyed)) connect(true) }
                                    android.os.SystemClock.elapsedRealtime() >= deadline -> {
                                        closeP2pChannel(channel); toast(getString(R.string.wi_fi_direct_is_still_busy_close_the_other_projection_app))
                                    }
                                    else -> handler.postDelayed({ waitUntilRemoved() }, 200)
                                }
                            }
                        }
                        waitUntilRemoved()
                    }
                    override fun onFailure(reason: Int) { closeP2pChannel(channel); toast(getString(R.string.could_not_reset_wi_fi_direct_close_the_other_projection_ap)) }
                })
            }
        } catch (_: SecurityException) {
            closeP2pChannel(channel); permissionHelp(getString(R.string.wireless_permissions), getString(R.string.allow_nearby_devices_and_on_older_android_versions_locatio))
        }
    }

    private fun closeP2pChannel(channel: android.net.wifi.p2p.WifiP2pManager.Channel) {
        if (Build.VERSION.SDK_INT >= 27) channel.close()
    }

    private fun refreshStatus() {
        val running = CarPlayBackgroundSession.hasSession()
        status?.text = when {
            setupError != null -> getString(R.string.setup_needs_attention)
            CarPlayBackgroundSession.active -> getString(R.string.carplay_connected)
            running -> getString(R.string.connecting_to_your_iphone)
            DiPlayPreferences.phoneAddress(this) != null -> "${getString(R.string.status_ready_for_prefix)}${DiPlayPreferences.phoneName(this)}"
            else -> getString(R.string.ready_when_you_are)
        }
        if (lastRunning != running) {
            connectButton?.text = if (running) getString(R.string.open_carplay) else getString(R.string.connect_phone)
            disconnectButton?.visibility = if (running) View.VISIBLE else View.GONE
            disconnectButton?.isEnabled = true
            lastRunning = running
        }
        connectButton?.isEnabled = setupError == null
    }
    private fun reportFileName() = "EadoPlay-${SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date())}.txt"

    private fun chooseReportDestination() {
        // Some head units omit or disable DocumentsUI. Launch itself can throw, before
        // the result callback and the background writer's exception handler ever run.
        runCatching { export.launch(reportFileName()) }.onFailure {
            toast(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                getString(R.string.this_head_unit_could_not_open_a_save_location_please_try_s)
                else getString(R.string.this_head_unit_has_no_available_file_picker_to_save_the_re))
        }
    }

    private fun exportDiagnostics(uri: Uri? = null) {
        if (exportInProgress) return
        exportInProgress = true
        exportButton?.apply { isEnabled = false; text = getString(R.string.saving_report) }
        val appContext = applicationContext
        val fileName = reportFileName()
        Thread({
            val result = runCatching {
                val report = buildString {
                    appendLine("EadoPlay ${version()} · private beta diagnostic report")
                    appendLine("Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
                    appendLine("Head unit: ${Build.MANUFACTURER} ${Build.MODEL}")
                    appendLine("Connection: ${if (AirPlayPersistence.loadWirelessEnabled(appContext)) "wireless" else "USB"}")
                    appendLine("Authentication: local experimental beta identity; no remote fallback")
                    appendLine("CarPlay setup: ${if (setupError == null) "ready" else "authentication unavailable"}")
                    appendLine("Saved video preference (may differ from active session): ${if (AirPlayPersistence.loadHevcEnabled(appContext)) "HEVC" else "H.264"}; ${AirPlayPersistence.loadFps(appContext)} fps")
                    appendLine("CarPlay size: ${com.shilapi.xcertplay.airplay.CarPlaySize.fromWidthMillimeters(AirPlayPersistence.loadWidthPhysicalMm(appContext)).label}")
                    appendLine("Saved resolution preference (may differ from active session): ${AirPlayPersistence.loadDisplayScaleTenths(appContext) * 10}%")
                    appendLine("Session: ${if (CarPlayBackgroundSession.active) "active" else if (CarPlayBackgroundSession.hasSession()) "connecting" else "stopped"}")
                    appendLine("Head-unit board: ${Build.BOARD}; hardware: ${Build.HARDWARE}; build: ${Build.DISPLAY}")
                    appendLine()
                    appendLine("--- Last display negotiation (timestamps distinguish it from current settings) ---")
                    appendLine(DisplayDiagnosticSnapshot.report(appContext))
                    appendLine()
                    for (name in SessionLogFile.REPORT_NAMES) {
                        val file = File(appContext.filesDir, "logs/$name")
                        if (file.isFile) {
                            appendLine("--- $name ---")
                            file.useLines { lines -> lines.forEach { line -> DiagnosticRedactor.redact(line)?.let { appendLine(it) } } }
                        }
                    }
                }
                if (uri != null) { DiagnosticExportStore.write(appContext.contentResolver, uri, report); uri }
                else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    DiagnosticExportStore.saveToDownloads(appContext.contentResolver, fileName, report)
                } else error("A save location is required")
            }
            runOnUiThread {
                exportInProgress = false
                if (isFinishing || isDestroyed) return@runOnUiThread
                exportButton?.apply { isEnabled = true; text = getString(R.string.save_diagnostic_report) }
                if (result.isSuccess) {
                    val savedUri = result.getOrThrow()
                    AlertDialog.Builder(this).setTitle(getString(R.string.diagnostic_report_saved))
                        .setMessage(if (uri == null) "Downloads/EadoPlay/$fileName" else getString(R.string.your_report_was_saved_to_the_selected_location))
                        .setPositiveButton(getString(R.string.done), null)
                        .setNeutralButton(getString(R.string.share)) { _, _ ->
                            runCatching {
                                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"; putExtra(Intent.EXTRA_STREAM, savedUri)
                                    clipData = android.content.ClipData.newRawUri(getString(R.string.report_clip_label), savedUri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }, getString(R.string.share_diagnostic_report)))
                            }.onFailure { toast(getString(R.string.report_saved_open_it_from_your_file_manager_to_share_it)) }
                        }.show()
                } else {
                    AlertDialog.Builder(this).setTitle(getString(R.string.could_not_save_the_report))
                        .setMessage(getString(R.string.check_that_storage_is_available_or_choose_another_save_loc))
                        .setPositiveButton(getString(R.string.choose_location)) { _, _ -> chooseReportDestination() }
                        .setNegativeButton(getString(R.string.close), null).show()
                }
            }
        }, "diplay-export").start()
    }
    private fun permissionHelp(title: String, body: String) {
        AlertDialog.Builder(this).setTitle(title).setMessage(body).setPositiveButton(getString(R.string.app_settings)) { _, _ ->
            openSystem(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }.setNegativeButton(getString(R.string.later), null).show()
    }
    private fun openSystem(intent: Intent) { runCatching { startActivity(intent) }.onFailure { toast(getString(R.string.open_this_setting_from_your_car_s_settings_app)) } }
    private fun toast(message: String) { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }

    private fun playTestTone(streamType: Int) {
        toneStop?.let { handler.removeCallbacks(it) }
        toneStop = null
        testToneTrack?.let { runCatching { it.stop(); it.release() } }
        testToneTrack = null
        var candidate: AudioTrack? = null
        val track = try {
            val pcm = assets.open("navigation_test.pcm").use { it.readBytes() }
            AudioTrack(streamType, 44100, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT, pcm.size, AudioTrack.MODE_STREAM).also {
                candidate = it
                check(it.state == AudioTrack.STATE_INITIALIZED)
                check(it.write(pcm, 0, pcm.size) == pcm.size)
                it.play()
            }
        } catch (error: Exception) {
            val state = candidate?.state ?: AudioTrack.STATE_UNINITIALIZED
            candidate?.let { runCatching { it.release() } }
            Log.w("EadoPlay", "playTestTone streamType=$streamType unavailable", error)
            toast(getString(R.string.audio_stream_unavailable, streamType, state.toString()))
            return
        }
        Log.i("EadoPlay", "playTestTone streamType=$streamType state=${track.state} playState=${track.playState}")
        testToneTrack = track
        val stop = Runnable {
            track.stop()
            track.release()
            if (testToneTrack === track) testToneTrack = null
            toneStop = null
        }
        toneStop = stop
        handler.postDelayed(stop, 4500)
    }

    private val channelButtons = mutableListOf<Button>()

    private fun paintChannel(index: Int, selected: Boolean) {
        val target = channelButtons.getOrNull(index) ?: return
        target.isSelected = selected
        target.setTextColor(if (selected) BG else TEXT)
        val content = rounded(if (selected) ACCENT else SURFACE, if (selected) ACCENT else BORDER)
        target.background = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(0x336F9FD9), content, null)
        } else content
    }

    private fun channelSelector(): ViewGroup {
        channelButtons.clear()
        val grid = GridLayout(this).apply {
            columnCount = 7
            rowCount = 3
            setPadding(0, dp(8), 0, dp(8))
        }
        for (i in 0..20) {
            val btn = Button(this).apply {
                text = i.toString()
                isAllCaps = false
                textSize = 16f
                minHeight = dp(48)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) stateListAnimator = null
                setOnClickListener {
                    val previous = navigationStreamType
                    navigationStreamType = i
                    if (previous != i) {
                        paintChannel(previous, false)
                        paintChannel(i, true)
                    }
                    playTestTone(i)
                }
            }
            val params = GridLayout.LayoutParams().apply {
                width = 0
                height = dp(48)
                val availableWidth = (resources.displayMetrics.widthPixels - dp(340)).coerceAtLeast(dp(280))
                width = availableWidth / 7
                setMargins(dp(4), dp(4), dp(4), dp(4))
            }
            grid.addView(btn, params)
            channelButtons.add(btn)
            paintChannel(i, i == navigationStreamType)
        }
        return grid
    }
    private fun version() = packageManager.getPackageInfo(packageName, 0).versionName ?: "0.1.0-beta.1"
    private fun languageSettings(content: LinearLayout) {
        section(content, getString(R.string.language_section_title)) { card ->
            card.addView(label(getString(R.string.language_hint), 14, MUTED))
            val current = AppLocale.preference(this)
            val languageButton = button("${getString(R.string.language_app_language)} · ${AppLocale.displayName(this, current)}", false) { }
            languageButton.setOnClickListener { AppLocale.showPicker(this) }
            card.addView(languageButton, matchButton(12, 60))
        }
    }

    private fun section(parent: LinearLayout, title: String, icon: Int? = null, build: (LinearLayout) -> Unit) {
        val card = card()
        val heading = row().apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, 0, dp(16)) }
        if (icon != null) heading.addView(ImageView(this).apply {
            setImageDrawable(AppCompatResources.getDrawable(this@DiPlayActivity, icon)?.let {
                DrawableCompat.wrap(it.mutate()).apply { DrawableCompat.setTint(this, ACCENT) }
            })
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(28), dp(28)).apply { marginEnd = dp(12) })
        heading.addView(label(title, 22, TEXT, true), LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(heading)
        build(card)
        parent.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(18) })
    }
    private fun toggle(parent: LinearLayout, title: String, description: String, value: Boolean, save: (Boolean) -> Unit) {
        val line = row().apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, dp(12))
            isClickable = true
            isFocusable = true
        }
        val text = column(); text.addView(label(title, 18, TEXT, true)); text.addView(label(description, 14, MUTED).apply { setPadding(0, dp(6), dp(16), 0) })
        line.addView(text, LinearLayout.LayoutParams(0, -2, 1f))

        var checked = value
        val control = TextView(this).apply {
            contentDescription = title
            gravity = Gravity.CENTER
            textSize = 15f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            minWidth = dp(88)
            minHeight = dp(48)
            setPadding(dp(14), 0, dp(14), 0)
            isClickable = true
            isFocusable = true
        }
        fun render() {
            control.text = getString(if (checked) R.string.setting_enabled else R.string.setting_disabled)
            control.setTextColor(if (checked) BG else TEXT)
            control.background = rounded(if (checked) ACCENT else SURFACE, if (checked) ACCENT else BORDER)
            control.contentDescription = "$title, ${control.text}"
        }
        fun toggleValue() {
            checked = !checked
            render()
            save(checked)
        }
        render()
        control.setOnClickListener { toggleValue() }
        line.addView(control, LinearLayout.LayoutParams(dp(96), dp(52)))
        line.setOnClickListener { toggleValue() }
        parent.addView(line)
    }
    private fun choice(parent: LinearLayout, title: String, options: List<String>, current: Int, reconnects: Boolean = true, save: (Int) -> Unit) {
        var selection = current
        val button = button("$title · ${options[selection]}", false) {}
        button.setOnClickListener {
            var pendingSelection = selection
            AlertDialog.Builder(this).setTitle(title)
                .setSingleChoiceItems(options.toTypedArray(), selection) { _, index -> pendingSelection = index }
                .setPositiveButton(getString(if (reconnects && CarPlayBackgroundSession.hasSession()) R.string.apply_and_reconnect else R.string.save)) { _, _ ->
                    if (pendingSelection != selection) {
                        selection = pendingSelection
                        save(selection)
                        button.text = "$title · ${options[selection]}"
                        if (reconnects && CarPlayBackgroundSession.hasSession()) {
                            connect(AirPlayPersistence.loadWirelessEnabled(this))
                        }
                    }
                }.setNegativeButton(getString(R.string.cancel), null).show()
        }
        parent.addView(button, matchButton(0, 60)); parent.addView(space(12))
    }
    private fun card() = column().apply { background = rounded(SURFACE, BORDER); setPadding(dp(24), dp(24), dp(24), dp(24)) }
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(-1, -2) }
    private fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutParams = LinearLayout.LayoutParams(-1, -2) }
    private fun label(value: String, size: Int, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size.toFloat(); setTextColor(color); gravity = Gravity.CENTER_VERTICAL
        typeface = if (bold) Typeface.create("sans-serif-medium", Typeface.NORMAL) else Typeface.create("sans-serif", Typeface.NORMAL)
        setLineSpacing(dp(3).toFloat(), 1f)
    }
    private fun button(title: String, primary: Boolean, click: () -> Unit) = Button(this).apply {
        text = title; isAllCaps = false; textSize = 18f; setTextColor(if (primary) BG else TEXT)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        val content = rounded(if (primary) ACCENT else SURFACE, if (primary) ACCENT else BORDER)
        background = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(0x336F9FD9), content, null)
        } else content
        setPadding(dp(16), 0, dp(16), 0); minHeight = dp(56)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) stateListAnimator = null
        setOnClickListener { click() }
    }
    private fun rounded(color: Int, stroke: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(20).toFloat(); setStroke(dp(1), stroke) }
    private fun matchButton(top: Int = 0, height: Int = 68) = LinearLayout.LayoutParams(-1, dp(height)).apply { topMargin = dp(top) }
    private fun space(height: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(height)) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    companion object {
        private val BG = Color.rgb(12, 17, 27)
        private val SURFACE = Color.rgb(21, 30, 44)
        private val BORDER = Color.rgb(42, 56, 75)
        private val ACCENT = Color.rgb(166, 200, 255)
        private val TEXT = Color.rgb(241, 245, 252)
        private val MUTED = Color.rgb(168, 182, 202)
        private val WARNING = Color.rgb(255, 196, 128)
    }
}
