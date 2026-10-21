package com.danimanx.replicant303.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danimanx.replicant303.BuildConfig
import com.danimanx.replicant303.MainViewModel
import com.danimanx.replicant303.ui.components.AppHeader
import com.danimanx.replicant303.ui.components.TempoDialog
import com.danimanx.replicant303.ui.screens.MidiScreen
import com.danimanx.replicant303.ui.screens.SequenceScreen
import com.danimanx.replicant303.ui.screens.SynthScreen
import com.danimanx.replicant303.ui.screens.VaultScreen
import com.danimanx.replicant303.ui.theme.LocalSkin

private enum class Tab(val label: String, val icon: ImageVector) {
    SYNTH("SYNTH", Icons.Filled.Tune),
    SEQUENCE("SEQUENCE", Icons.Filled.GridOn),
    VAULT("VAULT", Icons.Filled.LibraryMusic),
    MIDI("MIDI", Icons.Filled.Usb)
}

@Composable
fun ReplicantApp(
    windowSizeClass: WindowSizeClass,
    viewModel: MainViewModel,
    onExportPreset: () -> Unit,
    onImportPreset: () -> Unit
) {
    val state by viewModel.engineState.collectAsStateWithLifecycle()
    val scope by viewModel.scope.collectAsStateWithLifecycle()
    val midiState by viewModel.midiState.collectAsStateWithLifecycle()
    val skin by viewModel.skin.collectAsStateWithLifecycle()
    val palette = LocalSkin.current

    val expanded = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact
    var tab by rememberSaveable { mutableStateOf(Tab.SYNTH) }
    var tempoDialog by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(palette.backgroundGradient))
    ) {
        ProceduralBackground()

        if (!state.ready) {
            BootScreen()
        } else {
            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                snackbarHost = {
                    SnackbarHost(snackbar) { data ->
                        Snackbar(
                            snackbarData = data,
                            containerColor = palette.panel,
                            contentColor = palette.textPrimary,
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                },
                bottomBar = {
                    if (!expanded) {
                        BottomBar(tab) {
                            tab = it
                            if (it == Tab.MIDI) viewModel.midi.refreshDevices()
                        }
                    }
                }
            ) { padding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    if (expanded) {
                        NavigationRail(
                            tab = tab,
                            latencyMs = state.audio.latencyMs,
                            onSelect = {
                                tab = it
                                if (it == Tab.MIDI) viewModel.midi.refreshDevices()
                            }
                        )
                    }
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        AppHeader(
                            state = state,
                            midi = midiState,
                            skinName = skin,
                            compact = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Expanded,
                            onPlayToggle = { viewModel.engine.togglePlaying() },
                            onTempoClick = { tempoDialog = true },
                            onSkinClick = { viewModel.toggleSkin() },
                            onOpenMidi = {
                                tab = Tab.MIDI
                                viewModel.midi.refreshDevices()
                            }
                        )
                        if (state.engineError != null) {
                            Text(
                                state.engineError ?: "",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(palette.warn.copy(alpha = 0.12f))
                                    .padding(horizontal = 14.dp, vertical = 5.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.warn
                            )
                        }
                        Crossfade(targetState = tab, animationSpec = tween(160), label = "tab") { target ->
                            when (target) {
                                Tab.SYNTH -> SynthScreen(
                                    state = state,
                                    scope = scope,
                                    engine = viewModel.engine,
                                    expanded = expanded,
                                    onOpenMidi = { tab = Tab.MIDI }
                                )
                                Tab.SEQUENCE -> SequenceScreen(
                                    state = state,
                                    engine = viewModel.engine,
                                    expanded = expanded,
                                    onOpenVault = { tab = Tab.VAULT },
                                    onTempoClick = { tempoDialog = true }
                                )
                                Tab.VAULT -> VaultScreen(
                                    state = state,
                                    engine = viewModel.engine,
                                    onImport = onImportPreset,
                                    onExport = onExportPreset,
                                    onOpenSequence = { tab = Tab.SEQUENCE }
                                )
                                Tab.MIDI -> MidiScreen(
                                    state = state,
                                    midi = midiState,
                                    engine = viewModel.engine,
                                    controller = viewModel.midi
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (tempoDialog) {
        TempoDialog(
            tempo = state.tempo,
            onChange = { viewModel.engine.setTempo(it) },
            onDismiss = { tempoDialog = false }
        )
    }
}

@Composable
private fun ProceduralBackground() {
    val palette = LocalSkin.current
    Canvas(Modifier.fillMaxSize()) {
        val grid = palette.line.copy(alpha = 0.14f)
        val step = 52.dp.toPx()
        var x = 0f
        while (x < size.width) {
            drawLine(grid, Offset(x, 0f), Offset(x, size.height), 1f)
            x += step
        }
        var y = 0f
        while (y < size.height) {
            drawLine(grid.copy(alpha = 0.08f), Offset(0f, y), Offset(size.width, y), 1f)
            y += step
        }
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(palette.accent.copy(alpha = 0.07f), Color.Transparent),
                center = Offset(size.width * 0.48f, 0f),
                radius = size.height * 0.6f
            )
        )

        // PCB traces: a few right-angle runs and via pads, kept subtle.
        val trace = palette.silk.copy(alpha = 0.055f)
        val path = Path()
        path.moveTo(0f, size.height * 0.72f)
        path.lineTo(size.width * 0.18f, size.height * 0.72f)
        path.lineTo(size.width * 0.18f, size.height * 0.82f)
        path.lineTo(size.width * 0.42f, size.height * 0.82f)
        path.lineTo(size.width * 0.42f, size.height * 0.90f)
        path.lineTo(size.width, size.height * 0.90f)
        drawPath(path, trace, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))

        val path2 = Path()
        path2.moveTo(size.width, size.height * 0.28f)
        path2.lineTo(size.width * 0.72f, size.height * 0.28f)
        path2.lineTo(size.width * 0.72f, size.height * 0.18f)
        path2.lineTo(size.width * 0.52f, size.height * 0.18f)
        drawPath(path2, trace.copy(alpha = 0.04f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
    }
}

@Composable
private fun BootScreen() {
    val palette = LocalSkin.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "REPLICANT",
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 5.sp
                ),
                color = palette.textPrimary
            )
            Text(
                "303",
                style = MaterialTheme.typography.displayLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 72.sp
                ),
                color = palette.accent
            )
            Text(
                "ANDROID ACID ENGINE",
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 3.sp),
                color = palette.textSecondary
            )
            Spacer(Modifier.height(24.dp))
            CircularProgressIndicator(color = palette.accent, strokeWidth = 2.dp)
            Spacer(Modifier.height(12.dp))
            Text(
                "STARTING THE 303 CORE",
                style = MaterialTheme.typography.labelSmall,
                color = palette.textMuted
            )
        }
    }
}

@Composable
private fun BottomBar(tab: Tab, onSelect: (Tab) -> Unit) {
    val palette = LocalSkin.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp)
            .background(palette.panel.copy(alpha = 0.97f))
            .border(1.dp, palette.line, RoundedCornerShape(0.dp))
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Tab.entries.forEach { item ->
            val selected = tab == item
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) palette.accent.copy(alpha = 0.12f) else Color.Transparent)
                    .clickable { onSelect(item) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    item.icon,
                    contentDescription = item.label,
                    tint = if (selected) palette.accent else palette.textMuted,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    item.label,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp),
                    color = if (selected) palette.accent else palette.textMuted
                )
            }
        }
    }
}

@Composable
private fun NavigationRail(tab: Tab, latencyMs: Double, onSelect: (Tab) -> Unit) {
    val palette = LocalSkin.current
    Column(
        modifier = Modifier
            .width(88.dp)
            .fillMaxHeight()
            .background(palette.panel.copy(alpha = 0.92f))
            .border(1.dp, palette.line, RoundedCornerShape(0.dp))
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(palette.accent.copy(alpha = 0.10f))
                .border(1.dp, palette.accent.copy(alpha = 0.45f), RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "303",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = palette.accent
            )
        }
        Spacer(Modifier.height(10.dp))
        Tab.entries.forEach { item ->
            val selected = tab == item
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .padding(horizontal = 6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) palette.accent.copy(alpha = 0.12f) else Color.Transparent)
                    .clickable { onSelect(item) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    item.icon,
                    contentDescription = item.label,
                    tint = if (selected) palette.accent else palette.textMuted,
                    modifier = Modifier.size(21.dp)
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    item.label,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp),
                    color = if (selected) palette.accent else palette.textMuted
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            if (latencyMs > 0.1) "${latencyMs.toInt()}ms" else "—",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp),
            color = palette.textMuted
        )
        Text(
            "v${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp),
            color = palette.textMuted
        )
    }
}
