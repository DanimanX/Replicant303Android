package com.danimanx.replicant303

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.danimanx.replicant303.audio.EngineHolder
import com.danimanx.replicant303.midi.MidiController
import com.danimanx.replicant303.preset.PresetFileIO
import com.danimanx.replicant303.ui.theme.Skin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val engine = EngineHolder.get(application)
    val midi: MidiController = EngineHolder.getMidi(application)

    val engineState = engine.state
    val scope = engine.scope
    val midiState = midi.state

    private val prefs = application.getSharedPreferences("ui", android.content.Context.MODE_PRIVATE)

    private val _skin = MutableStateFlow(
        runCatching { Skin.valueOf(prefs.getString(KEY_SKIN, Skin.GREEN.name) ?: Skin.GREEN.name) }
            .getOrDefault(Skin.GREEN)
    )
    val skin: StateFlow<Skin> = _skin

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    fun setSkin(skin: Skin) {
        _skin.value = skin
        prefs.edit().putString(KEY_SKIN, skin.name).apply()
    }

    fun toggleSkin() {
        setSkin(if (_skin.value == Skin.GREEN) Skin.RED else Skin.GREEN)
    }

    fun saveSession() = engine.saveSession()

    fun exportPreset(uri: Uri, done: (String) -> Unit = {}) {
        viewModelScope.launch {
            val preset = engine.capturePreset()
            val bytes = withContext(Dispatchers.IO) {
                runCatching {
                    PresetFileIO.writeMidi(preset)
                }.getOrNull()
            }
            val message = if (bytes == null) {
                "Could not encode the preset."
            } else {
                val written = withContext(Dispatchers.IO) {
                    runCatching {
                        getApplication<Application>().contentResolver
                            .openOutputStream(uri, "wt")?.use { it.write(bytes) }
                    }.isSuccess
                }
                if (written) "Exported ${PresetFileIO.suggestedFileName(preset)}"
                else "Could not write the selected file."
            }
            _messages.emit(message)
            done(message)
        }
    }

    fun importPreset(uri: Uri, done: (String) -> Unit = {}) {
        viewModelScope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }.getOrNull()
            }
            if (bytes == null) {
                _messages.emit("Could not read the selected file.")
                return@launch
            }

            val preset = withContext(Dispatchers.IO) {
                val first = bytes.firstOrNull { !it.toInt().toChar().isWhitespace() }
                if (first != null && first.toInt().toChar() == '{') {
                    PresetFileIO.fromJson(String(bytes, Charsets.UTF_8))
                } else {
                    PresetFileIO.readMidi(bytes)
                }
            }

            if (preset == null) {
                _messages.emit("That file is not a Replicant 303 preset or a readable MIDI pattern.")
                return@launch
            }

            engine.importPreset(preset)
            val message = "Imported ${preset.name}"
            _messages.emit(message)
            done(message)
        }
    }

    fun engineToggle() = engine.togglePlaying()

    private companion object {
        const val KEY_SKIN = "skin"
    }
}
