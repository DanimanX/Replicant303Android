package com.danimanx.replicant303.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danimanx.replicant303.audio.EngineController
import com.danimanx.replicant303.model.BankLetters
import com.danimanx.replicant303.model.BankNames
import com.danimanx.replicant303.model.EngineUiState
import com.danimanx.replicant303.ui.components.ActionButton
import com.danimanx.replicant303.ui.components.Panel
import com.danimanx.replicant303.ui.components.SectionLabel
import com.danimanx.replicant303.ui.components.TinyTag
import com.danimanx.replicant303.ui.theme.LocalSkin
import kotlin.random.Random

@Composable
fun VaultScreen(
    state: EngineUiState,
    engine: EngineController,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onOpenSequence: () -> Unit
) {
    val skin = LocalSkin.current
    var bank by remember { mutableIntStateOf(state.presetBank) }
    LaunchedEffect(state.presetBank) { bank = state.presetBank }
    val selectedBank = bank.coerceIn(0, 7)
    val selectedSlot = if (selectedBank == state.presetBank) state.presetSlot else 0
    val presetName = engine.presetNameFor(selectedBank, selectedSlot)
    val userStored = engine.isOverridden(selectedBank, selectedSlot)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Panel(accent = skin.accent) {
            SectionLabel("PRESET VAULT // 8 × 64", accent = skin.accent) {
                TinyTag("512 PATCHES", skin.textMuted)
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                BankLetters.forEachIndexed { index, letter ->
                    val active = selectedBank == index
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(7.dp))
                            .background(if (active) skin.accent else skin.panelHigh)
                            .border(1.dp, if (active) skin.accent else skin.line, RoundedCornerShape(7.dp))
                            .clickable {
                                bank = index
                                engine.loadFactoryPreset(index, 0)
                            }
                            .padding(vertical = 5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            letter,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (active) skin.backgroundDeep else skin.textPrimary
                        )
                        Text(
                            BankNames[index].take(3),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 6.sp),
                            color = if (active) skin.backgroundDeep.copy(alpha = 0.7f) else skin.textMuted
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        presetName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = skin.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "BANK ${BankLetters[selectedBank]} // ${BankNames[selectedBank]} · " +
                            "SLOT ${(selectedSlot + 1).toString().padStart(2, '0')}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, letterSpacing = 0.8.sp),
                        color = skin.textMuted
                    )
                }
                TinyTag(if (userStored) "USER" else "FACTORY", if (userStored) skin.accentAlt else skin.textMuted)
            }
        }

        Panel(accent = skin.accentAlt) {
            SectionLabel("SLOTS // ${BankLetters[selectedBank]}", accent = skin.accentAlt) {
                TinyTag("PAGE 1-8", skin.textMuted)
            }
            Spacer(Modifier.height(6.dp))
            for (page in 0 until 8) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        "P${page + 1}",
                        modifier = Modifier.width(20.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp),
                        color = skin.textMuted
                    )
                    for (column in 0 until 8) {
                        val slot = page * 8 + column
                        val active = selectedBank == state.presetBank && slot == state.presetSlot
                        val stored = engine.isOverridden(selectedBank, slot)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1.45f)
                                .padding(1.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    when {
                                        active -> skin.accent
                                        stored -> skin.accentAlt.copy(alpha = 0.18f)
                                        else -> skin.panelHigh.copy(alpha = 0.75f)
                                    }
                                )
                                .border(
                                    1.dp,
                                    when {
                                        active -> skin.accent
                                        stored -> skin.accentAlt.copy(alpha = 0.6f)
                                        else -> skin.line.copy(alpha = 0.7f)
                                    },
                                    RoundedCornerShape(4.dp)
                                )
                                .clickable { engine.loadFactoryPreset(selectedBank, slot) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                (slot + 1).toString().padStart(2, '0'),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 7.sp,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (active) skin.backgroundDeep else if (stored) skin.accentAlt else skin.textSecondary
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(7.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                ActionButton("GEN", Modifier.weight(1f), skin.accent, height = 38.dp) {
                    engine.generatePreset(
                        selectedBank,
                        selectedSlot,
                        Random.nextInt(1, Int.MAX_VALUE)
                    )
                }
                ActionButton("STORE", Modifier.weight(1f), skin.accentAlt, height = 38.dp) {
                    engine.storeCurrentPreset(selectedBank, selectedSlot)
                }
                ActionButton("IMPORT", Modifier.weight(1f), skin.accentAlt, height = 38.dp, onClick = onImport)
                ActionButton("EXPORT", Modifier.weight(1f), skin.accent, height = 38.dp, onClick = onExport)
            }
        }

        Panel(accent = skin.accentAlt) {
            SectionLabel("HOW THE VAULT WORKS", accent = skin.accentAlt)
            Spacer(Modifier.height(5.dp))
            Text(
                "GEN writes a deterministic procedural variation into the selected slot and loads it. " +
                    "STORE captures the live knobs and edited line. IMPORT and EXPORT move .midi presets: " +
                    "the file plays in any DAW and carries an R303PRESET/1 metadata event that restores " +
                    "the complete patch.",
                style = MaterialTheme.typography.bodySmall,
                color = skin.textSecondary
            )
            Spacer(Modifier.height(7.dp))
            ActionButton("EDIT THIS LINE", accent = skin.accent, height = 34.dp, onClick = onOpenSequence)
        }
    }
}
