# Real-world USB MIDI clock test plan

For verifying the USB MIDI path (`UsbMidiConnector` + `MidiClockSource`) against actual
hardware — this is the one part of the MIDI clock work I can't verify myself without the gear
in hand, so this is written to be self-contained for you to run.

## What you'll need

- A USB-C OTG adapter (if your MIDI gear/interface is USB-A or 5-pin DIN), or a USB-C MIDI
  interface/cable if your gear is already USB-C / class-compliant.
- A clock source: a hardware sequencer/groovebox/drum machine that sends MIDI Clock over USB, a
  USB MIDI keyboard/controller plugged into a computer running a DAW that's transmitting clock
  out over USB to the phone, or any class-compliant MIDI interface fed by clock from elsewhere.

## 1. Sanity check without hardware (optional, do this first)

If you have any other MIDI-capable app on the phone (even a free MIDI monitor/utility app),
check that qMetronome shows up as a MIDI destination named **"qMetronome Clock In"** and that
sending clock to it switches the in-app "Clock" status from `Internal` to `Following MIDI
clock`. This isolates whether the byte-parsing logic itself works before involving USB at all.

## 2. Connect the USB hardware

1. Launch qMetronome, plug in the USB MIDI device via the OTG adapter/cable.
2. Tap **"Scan USB MIDI"** on the main screen. Your device should appear in the list below the
   button (the name comes from the device's own USB MIDI descriptor, so it should look like
   whatever your gear calls itself).
   - If nothing shows up: unplug/replug, then scan again — USB device enumeration can lag the
     physical connection by a second or two.
3. Tap **"Connect"** next to the device. The first time, Android should show a system USB
   permission dialog ("Allow qMetronome to access this USB device?") — accept it. This dialog
   is handled by the platform automatically; nothing in the app code triggers it manually, so if
   it never appears, that itself is a useful data point (see Troubleshooting).
4. Start your hardware's transport (press play/start on the sequencer, or hit play in the DAW).

## 3. What should happen

- The "Clock" status line should change from `Internal` to `Following MIDI clock`, and within
  a beat or two show a measured BPM that matches your hardware's tempo.
- The metronome should start automatically on MIDI Start (no need to also press play in the
  app) — that's the `0xFA` → `MetronomeEngine.start()` mapping.
- The Glyph Matrix / on-screen preview should pulse in time with your hardware's clock, not the
  phone's own internal timer.
- Stopping the hardware's transport (MIDI Stop, `0xFC`) should stop qMetronome too.
- Changing tempo on the hardware mid-session should smoothly update the displayed/followed BPM
  within roughly a beat, without the visualizer stuttering badly (some jitter is normal and
  expected — the smoothing window is 24 clock ticks, i.e. one quarter note, so very fast tempo
  ramps will lag slightly behind).
- Unplugging the device (or stopping clock without sending `0xFC`) should fall back to
  `Internal` clock automatically after ~4 silent beats, rather than freezing.

## 4. If something doesn't work

Capture a logcat while reproducing, filtered to this app and the platform MIDI service:

```
adb logcat | grep -iE "qmetronome|midi"
```

Things worth checking, in order:

1. **Device never appears after "Scan USB MIDI"** — likely a USB host-mode/cabling issue, not
   app logic. Confirm the phone enumerates *any* USB device via OTG (e.g. a USB drive) first.
2. **Device appears but "Connect" does nothing / no permission dialog** — check logcat for
   `MidiManager`/`UsbManager` errors; this would point at `UsbMidiConnector.connect()` not
   reaching `openDevice()`, or the device exposing zero MIDI ports.
3. **Connects, but BPM never appears ("waiting for clock…" forever)** — your hardware may be
   sending MIDI over a transport other than the legacy byte-stream protocol (e.g. only Universal
   MIDI Packets / USB-MIDI 2.0). `UsbMidiConnector` currently only scans
   `TRANSPORT_MIDI_BYTE_STREAM`; report back the device model and I'll add UMP support.
4. **Connects and shows BPM, but the Glyph/preview doesn't visibly sync** — that would point at
   `MetronomeEngine`'s beat-handling rather than the MIDI parsing itself, since BPM display
   reads from the same `onBeat` callback that drives the visualizer.
5. **Beat/click fires noticeably faster than the displayed BPM** — this exact symptom showed up
   in round 1, caused by a virtual MIDI cable and USB both feeding clock at once with no
   isolation between them. That's now fixed by source arbitration in `MidiClockSource` (only
   one source drives the clock; a second one is ignored unless the first goes quiet for 500ms).
   If you hit this again with only *one* source connected, that's a different, more interesting
   bug — grab logcat with `adb logcat | grep MidiClockSource`, which logs every source switch
   and every beat fired (with tick count and measured BPM), and send that over.

Whatever you find, send me the device name/model and what step it broke at — that's the
real-world data the original feasibility doc flagged as unverifiable without hardware in hand.
