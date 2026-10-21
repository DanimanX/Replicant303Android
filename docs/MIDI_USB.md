# USB MIDI

Replicant303 Android uses Android's `MidiManager`, which owns the USB host
permission flow. The app never talks to `/dev/bus/usb` directly.

## Devices and ports

- Devices are enumerated from `MidiManager.getDevicesForTransport(...)`.
- On Android 13+, both `TRANSPORT_MIDI_BYTE_STREAM` and
  `TRANSPORT_UNIVERSAL_MIDI_PACKETS` are queried; devices that expose UMP are
  marked `UMP` in the list.
- A device's **output port** is MIDI data flowing *to the app* (MIDI IN).
- A device's **input port** is MIDI data flowing *from the app* (MIDI OUT).
- Input and output can be connected independently, to the same or different
  devices.

## Permissions and reconnects

Opening a USB MIDI device may trigger Android's USB permission dialog. If the
dialog is denied, the app shows an actionable message and leaves the device
listed. When a device is removed, its ports are closed. On the next
enumeration, the app reconnects the last device by id, falling back to the
remembered device name because USB ids can change across replugs.

## MIDI 1.0 and MIDI 2.0

- **MIDI 1.0** is the default: raw bytes are parsed by `MidiStreamParser`,
  including running status, SysEx skipping and real-time bytes interleaved
  inside other messages.
- **MIDI 2.0 UMP** is opt-in. When enabled, incoming UMP packets are translated
  to MIDI 1.0 channel-voice records for the engine, and outgoing records are
  encoded as UMP MIDI 1.0 channel-voice messages. This is translation, not a
  native UMP synthesizer.
- UMP requires Android 13 (API 33) and a UMP-capable device.

## Clock and transport

- **Clock out** is 24 PPQN, generated in the audio callback and timestamped
  against the audio clock.
- **Transport out** sends start (`0xFA`) and stop (`0xFC`) when the transport
  changes and the option is enabled.
- **Clock in** makes the sequencer step from incoming `0xF8` pulses. The first
  pulse starts the pattern; start/stop messages control the transport. Tempo is
  estimated from the pulse interval and shown in the UI (`EXT WAIT` until the
  estimate is valid, then `LOCKED`). While clock receive is enabled, pressing
  Play arms the sequencer and waits for the external clock.

## Controller map

| CC | Parameter |
|---|---|
| 1 | Env mod |
| 71 | Resonance |
| 72 | Decay |
| 73 | Env mod |
| 74 | Cutoff |
| 75 | Accent |
| 76 | Distortion drive |
| 77 | Distortion tone |
| 78 | Distortion mix |
| 79 | Distortion on/off |
| 80 | Waveform saw/square |
| 120 | All sound off |
| 123 | All notes off |

Pitch bend has a ±2 semitone range. Note velocity ≥ 100 triggers the accent
envelope. The channel filter is OMNI by default; selecting a channel filters
incoming channel-voice messages and sets the outgoing channel.

## Troubleshooting

- **No devices listed**: confirm the controller is class-compliant and the
  USB-C adapter supports data (not charge-only). Tap the refresh icon.
- **Permission prompt denied**: unplug/replug the device, then connect again.
  Android may remember a denial until the device is re-enumerated.
- **Clock waiting**: enable Clock In only when a master is actually sending
  `0xF8`. The sequencer intentionally waits for the first pulse.
- **No MIDI out**: check that MIDI OUT is enabled in Sync & Routing and that the
  device exposes an input port to the app.
