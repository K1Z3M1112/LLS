package com.firstt175.deepdrop.ui.screens
import com.firstt175.deepdrop.ui.produceConfigState
import com.firstt175.deepdrop.ui.refreshConfigState

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.firstt175.deepdrop.R
import com.firstt175.deepdrop.prefs.DrawerEdge
import com.firstt175.deepdrop.prefs.LsfgPreferences
import com.firstt175.deepdrop.prefs.PresentMode
import com.firstt175.deepdrop.session.NativeBridge
import com.firstt175.deepdrop.session.diagnostics.SoundTunerController
import com.firstt175.deepdrop.ui.components.CollapsibleSection
import com.firstt175.deepdrop.ui.components.IconBadge
import com.firstt175.deepdrop.ui.components.LsfgCard
import com.firstt175.deepdrop.ui.components.LsfgTopBar
import com.firstt175.deepdrop.ui.components.SectionHeader
import com.firstt175.deepdrop.ui.components.ToggleRow
import com.firstt175.deepdrop.ui.components.ValueSlider

// ----------------------------------------------------------------------------------------
// Frame generation & pacing — consolidated screen
// ----------------------------------------------------------------------------------------

// ----------------------------------------------------------------------------------------
// Frame generation & pacing — embedded inline in the Settings screen, not a
// separate destination. [nav] is only used to jump to the DLL picker / Legal screen.
// ----------------------------------------------------------------------------------------

// ----------------------------------------------------------------------------------------
// Overlay & Display — new screen consolidating overlay handle, HUD, present mode
// ----------------------------------------------------------------------------------------

@Composable
fun OverlayDisplayScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val prefs = remember { LsfgPreferences(ctx) }
    val state by produceConfigState(prefs).collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        LsfgTopBar(
            title = stringResource(R.string.nav_overlay_display),
            onBack = { nav.popBackStack() },
        )

        // ---- Drawer handle -----------------------------------------------------------
        LsfgCard {
            SectionHeader(eyebrow = stringResource(R.string.section_overlay_handle), title = null)
            Spacer(Modifier.height(4.dp))
            DrawerEdgeSelector(
                selected = state.drawerEdge,
                onSelected = {
                    prefs.setDrawerEdge(it)
                    refreshConfigState(prefs)
                    com.firstt175.deepdrop.session.service.LsfgForegroundService.updateDrawerEdge(it)
                },
            )
        }

        // ---- Trusted overlay (accessibility) ---------------------------------------
        // Optional accessibility-service feature, off by default — collapsed
        // unless already enabled so the choice stays visible once made.
        CollapsibleSection(
            title = stringResource(R.string.section_trusted_overlay),
            subtitle = if (state.trustedOverlay) "เปิดอยู่" else "ปิดอยู่ — ไม่บังคับ แตะเพื่อตั้งค่า",
            startExpanded = state.trustedOverlay,
        ) {
            ToggleRow(
                icon = Icons.Filled.TouchApp,
                title = stringResource(R.string.param_trusted_overlay),
                description = stringResource(R.string.param_trusted_overlay_desc),
                checked = state.trustedOverlay,
                onCheckedChange = {
                    prefs.setTrustedOverlay(it)
                    if (!it) prefs.setGestureForwardingEnabled(false)
                    refreshConfigState(prefs)
                },
            )
            if (state.trustedOverlay) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ToggleRow(
                    icon = Icons.Filled.Gesture,
                    title = "Forward touches",
                    description = "สำรองสำหรับเครื่องที่เข้มงวด: จำลองการแตะ/ปัดผ่าน Accessibility เมื่อ trusted overlay ยังโดนบล็อกอยู่",
                    checked = state.gestureForwardingEnabled,
                    onCheckedChange = {
                        prefs.setGestureForwardingEnabled(it)
                        refreshConfigState(prefs)
                    },
                )
            }
        }

        // ---- Present mode (from SettingsDrawerOverlay) --------------------------------
        LsfgCard {
            SectionHeader(eyebrow = "PRESENT MODE", title = null)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(
                    PresentMode.IMMEDIATE to "Immediate",
                    PresentMode.MAILBOX to "Mailbox",
                    PresentMode.FIFO to "FIFO",
                ).forEach { (mode, label) ->
                    FilterChip(
                        selected = state.presentMode == mode,
                        onClick = {
                            prefs.setPresentMode(mode)
                            runCatching { NativeBridge.setPresentMode(mode.vkValue) }
                            refreshConfigState(prefs)
                        },
                        label = { Text(label) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Immediate = หน่วงต่ำสุด อาจมีภาพฉีก. Mailbox = หน่วงต่ำ ไม่มีภาพฉีก (ค่าเริ่มต้น). FIFO = ซิงก์กับ vsync ไม่มีภาพฉีก แต่หน่วงมากที่สุด.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ---- Sound tuner (from SettingsDrawerOverlay) ----------------------------------
        SoundTunerSection(prefs = prefs)

        TailNote()
    }
}

// ----------------------------------------------------------------------------------------
// Sound tuner — system-wide EQ/BassBoost/Virtualizer/LoudnessEnhancer, ported from
// SettingsDrawerOverlay.buildSoundTunerSection. [appSoundTuner] is kept at file scope
// (rather than `remember`ed) so the attached AudioEffect handles outlive this composable's
// lifecycle the same way they outlive the drawer panel while a session is running.
// ----------------------------------------------------------------------------------------

private var appSoundTuner: SoundTunerController? = null

@Composable
private fun SoundTunerSection(prefs: LsfgPreferences) {
    val bandInfo = remember { SoundTunerController.queryBandInfo() }
    var enabled by remember { mutableStateOf(prefs.isSoundTunerEnabled()) }
    var bass by remember { mutableStateOf(prefs.getSoundTunerBass()) }
    var virtualizer by remember { mutableStateOf(prefs.getSoundTunerVirtualizer()) }
    var loudness by remember { mutableStateOf(prefs.getSoundTunerLoudness()) }
    val bandLevels = remember {
        val saved = prefs.getSoundTunerEqBands()
        mutableStateListOf<Int>().apply {
            when {
                bandInfo != null && saved.size == bandInfo.bandCount -> addAll(saved)
                bandInfo != null -> addAll(List(bandInfo.bandCount) { (bandInfo.levelRange[0] + bandInfo.levelRange[1]) / 2 })
            }
        }
    }

    fun controller(): SoundTunerController = appSoundTuner ?: SoundTunerController().also { appSoundTuner = it }

    fun applyAllToController() {
        val c = controller()
        c.setBassBoostStrength(bass)
        c.setVirtualizerStrength(virtualizer)
        c.setLoudnessGain(loudness)
        if (bandInfo != null && bandLevels.size == bandInfo.bandCount) {
            bandLevels.forEachIndexed { i, lvl -> c.setEqBandLevel(i, lvl) }
        }
    }

    // Re-attach immediately if the tuner was left on from a previous session, same as
    // how the drawer's buildSoundTunerSection restores prior on/off state.
    LaunchedEffect(Unit) {
        if (enabled) {
            controller().enable()
            applyAllToController()
        }
    }

    CollapsibleSection(
        title = "SOUND TUNER",
        subtitle = if (enabled) "เปิดอยู่" else "ปิดอยู่ — แตะเพื่อตั้งค่า",
        startExpanded = enabled,
    ) {
        ToggleRow(
            icon = Icons.Filled.GraphicEq,
            title = "Enable sound tuner",
            description = "อีควอไลเซอร์ระบบ + bass boost + virtualizer + loudness ที่ปรับเสียงของทุกแอปที่กำลังเล่นเสียงอยู่",
            checked = enabled,
            onCheckedChange = {
                enabled = it
                prefs.setSoundTunerEnabled(it)
                if (it) {
                    controller().enable()
                    applyAllToController()
                } else {
                    appSoundTuner?.disable()
                    appSoundTuner = null
                }
            },
        )

        Spacer(Modifier.height(10.dp))
        Text("Presets", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            SoundTunerController.PRESETS.forEach { preset ->
                FilterChip(
                    selected = false,
                    onClick = {
                        bass = preset.bass
                        virtualizer = preset.virtualizer
                        loudness = preset.loudness
                        prefs.setSoundTunerBass(preset.bass)
                        prefs.setSoundTunerVirtualizer(preset.virtualizer)
                        prefs.setSoundTunerLoudness(preset.loudness)
                        if (bandInfo != null && bandInfo.bandCount > 0 && bandLevels.size == bandInfo.bandCount) {
                            val range = bandInfo.levelRange
                            val newBands = bandInfo.centerFreqsHz.map { freq ->
                                SoundTunerController.presetLevelForBand(preset.eqShapeAt(freq), range)
                            }
                            prefs.setSoundTunerEqBands(newBands)
                            newBands.forEachIndexed { i, lvl -> bandLevels[i] = lvl }
                        }
                        if (enabled) applyAllToController()
                    },
                    label = { Text(preset.label) },
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        ValueSlider(
            title = "Bass boost",
            valueDisplay = "${bass / 10}%",
            description = "เสริมเสียงเบส",
            value = bass.toFloat(),
            range = 0f..1000f,
            steps = 99,
            enabled = enabled,
            onValueChange = {
                bass = it.toInt()
                prefs.setSoundTunerBass(bass)
                appSoundTuner?.setBassBoostStrength(bass)
            },
        )
        ValueSlider(
            title = "Virtualizer",
            valueDisplay = "${virtualizer / 10}%",
            description = "จำลองเสียงรอบทิศทาง",
            value = virtualizer.toFloat(),
            range = 0f..1000f,
            steps = 99,
            enabled = enabled,
            onValueChange = {
                virtualizer = it.toInt()
                prefs.setSoundTunerVirtualizer(virtualizer)
                appSoundTuner?.setVirtualizerStrength(virtualizer)
            },
        )
        ValueSlider(
            title = "Loudness",
            valueDisplay = "${loudness / 100}%",
            description = "เพิ่มความดังเป้าหมาย",
            value = loudness.toFloat(),
            range = 0f..2000f,
            steps = 99,
            enabled = enabled,
            onValueChange = {
                loudness = it.toInt()
                prefs.setSoundTunerLoudness(loudness)
                appSoundTuner?.setLoudnessGain(loudness)
            },
        )

        if (bandInfo != null && bandInfo.bandCount > 0 && bandLevels.size == bandInfo.bandCount) {
            Spacer(Modifier.height(8.dp))
            Text("Equalizer", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            bandInfo.centerFreqsHz.forEachIndexed { i, freq ->
                val freqLabel = if (freq >= 1000) "${freq / 1000} kHz" else "$freq Hz"
                ValueSlider(
                    title = freqLabel,
                    valueDisplay = "%.1f dB".format(bandLevels[i] / 100f),
                    description = null,
                    value = bandLevels[i].toFloat(),
                    range = bandInfo.levelRange[0].toFloat()..bandInfo.levelRange[1].toFloat(),
                    steps = 0,
                    enabled = enabled,
                    onValueChange = {
                        val level = it.toInt()
                        bandLevels[i] = level
                        appSoundTuner?.setEqBandLevel(i, level)
                        prefs.setSoundTunerEqBands(bandLevels.toList())
                    },
                )
            }
        } else {
            Spacer(Modifier.height(8.dp))
            Text(
                "อีควอไลเซอร์แบบราย band ไม่พร้อมใช้งานบนเครื่องนี้ — bass boost/virtualizer/loudness ด้านบนยังใช้งานได้ตามปกติ",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TailNote() {
    Text(
        text = "HUD preview uses the real assets/hud image. Image Enhancement is live and applies on the next displayed frame; frame-generation shader parameters still require their existing session reinitialization.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun DrawerEdgeSelector(
    selected: DrawerEdge,
    onSelected: (DrawerEdge) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp, horizontal = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon = Icons.Filled.OpenInFull, size = 36.dp)
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.param_drawer_edge),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        val options = listOf(
            DrawerEdge.LEFT to stringResource(R.string.drawer_edge_left),
            DrawerEdge.RIGHT to stringResource(R.string.drawer_edge_right),
            DrawerEdge.TOP to stringResource(R.string.drawer_edge_top),
            DrawerEdge.BOTTOM to stringResource(R.string.drawer_edge_bottom),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.take(2).forEach { (edge, label) ->
                FilterChip(
                    selected = selected == edge,
                    onClick = { onSelected(edge) },
                    label = { Text(label) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.drop(2).forEach { (edge, label) ->
                FilterChip(
                    selected = selected == edge,
                    onClick = { onSelected(edge) },
                    label = { Text(label) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
