# Real-world USB MIDI clock test plan

For verifying the USB MIDI path (`UsbMidiConnector` + `MidiClockSource`/`MidiClockSender`, plus
`MidiActionSender` for per-beat-type Note/CC - section 7) against actual hardware — this is the
part of the MIDI work I can't verify myself without the gear in hand, so this is written to be
self-contained for you to run. Covers both clock directions: following a device's clock (section
2) and sending our clock to a device (section 5) — they're independent connections, so test them
separately before trying both at once. MIDI Actions (section 7) rides the same "Send to" USB
connection as outgoing clock, so get that working first.

## What you'll need

- A USB-C OTG adapter (if your MIDI gear/interface is USB-A or 5-pin DIN), or a USB-C MIDI
  interface/cable if your gear is already USB-C / class-compliant.
- For following (section 2): a clock source — a hardware sequencer/groovebox/drum machine that
  sends MIDI Clock over USB, a USB MIDI keyboard/controller plugged into a computer running a
  DAW that's transmitting clock out over USB to the phone, or any class-compliant MIDI interface
  fed by clock from elsewhere.
- For sending (section 5): something that can show you received clock — a hardware sequencer set
  to "external clock" / "MIDI sync" mode, a DAW set to sync to incoming MIDI clock, or (ideally,
  for the same-device test in 5.2) a device with a MIDI Thru/echo setting you can toggle.

## 1. Sanity check without hardware (optional, do this first)

If you have any other MIDI-capable app on the phone (even a free MIDI monitor/utility app),
check that qMetronome shows up as a MIDI device named **"qMetronome Clock"** with both an input
and an output port, and that:

- sending clock to its input switches the in-app "Clock" status from `Internal` to `Following
  MIDI clock` (tests the receiving side), and
- with "Send MIDI clock" enabled in Settings, picking qMetronome's output as that other app's
  MIDI input shows clock bytes arriving there (tests the sending side).

This isolates whether the byte-parsing/generation logic itself works before involving USB at all.

## 2. Following a USB device's clock

1. Launch qMetronome, plug in the USB MIDI device via the OTG adapter/cable.
2. Tap **"Scan USB MIDI"** on the main screen. Your device should appear in the list below the
   button (the name comes from the device's own USB MIDI descriptor, so it should look like
   whatever your gear calls itself).
   - If nothing shows up: unplug/replug, then scan again — USB device enumeration can lag the
     physical connection by a second or two.
3. Tap **"Follow"** next to the device. The first time, Android should show a system USB
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
2. **Device appears but "Follow"/"Send to" does nothing / no permission dialog** — check logcat
   for `MidiManager`/`UsbManager` errors; this would point at `UsbMidiConnector`'s
   `connectForFollowing()`/`connectForSending()` not reaching `openDevice()`, or the device
   exposing zero MIDI ports in that direction.
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

## 5. Sending clock to a USB device

This is the direction that's never run against real hardware - everything below is genuinely
unverified, not just "should work in theory."

1. In Settings → Clock, turn on **"Send MIDI clock"** (this is the master on/off for sending;
   without it, nothing goes out regardless of what's connected below).
2. Scan for USB MIDI as in section 2, then tap **"Send to"** next to your device instead of
   "Follow". Same permission-dialog behavior as following.
3. Set your hardware/DAW to follow external MIDI clock (terminology varies - "external sync",
   "MIDI sync", "slave mode").
4. Press play in qMetronome (or tap-tempo/start it any way you like).

**What should happen:** your hardware should start in time with qMetronome and track its tempo,
including BPM changes made via the new +/- buttons or drag-to-scrub. Stopping qMetronome should
stop your hardware's transport too (`0xFC`).

**If it doesn't:** check whether your hardware actually distinguishes "external clock present"
from "no clock" - some gear needs the clock running *before* you arm external sync, not after.
If qMetronome's own UI looks correct (engine playing, "Send MIDI clock" on, device shows
"Stop sending" available) but nothing arrives, logcat for `MidiClockSender` will show every byte
send attempt and any exceptions from a destination that's gone away.

### 5.1 Following one device while sending to a different one

If you have two MIDI-capable things handy (e.g. a DAW on a computer as the clock source, and a
hardware groovebox as the clock destination), connect one for "Follow" and the other for "Send
to" simultaneously. This is the configuration `MidiClockSender`'s design assumes is the common
case - qMetronome as a relay/repeater between two things that can't talk to each other directly.
Confirm the groovebox's tempo tracks the DAW's, including tempo changes made on the DAW.

### 5.2 Following and sending to the *same* device

This is the combination flagged in the MIDI ADR as a real, unverified risk: if your device has a
MIDI Thru/echo setting, sending it clock while also reading its output could create a loop
(it echoes our clock back to us, we echo that back out, ...). Test deliberately:

1. With the device's Thru/echo setting **off** (if it has one), tap both "Follow" and "Send to"
   for the same device. The settings screen should show a heads-up text under that device's row.
   Confirm nothing misbehaves - tempo should stay stable, not run away or stutter.
2. If the device *has* a Thru/echo setting, turn it **on** and repeat. Watch for: tempo climbing
   on its own, the BPM display jumping erratically, or clock activity that doesn't match what
   either side is actually generating. If you see any of that, it confirms the loop risk the ADR
   predicted - report back what happened and which device, since that's the trigger for adding
   an actual guard instead of just the heads-up text.

Whatever you find, send me the device name/model and what step it broke at — that's the
real-world data the original feasibility doc flagged as unverifiable without hardware in hand.

## 6. Starring a device for auto-reconnect

This is the other thing that's never run against real hardware - the auto-reconnect logic
compiles and is unit-tested at the pure-key-generation level (`UsbMidiConnectorTest`), but the
actual hotplug callback (`MidiManager.DeviceCallback.onDeviceAdded`) and `openDevice()` reconnect
flow need a real unplug/replug to verify.

1. Scan and "Follow" (or "Send to") a device as in section 2 or 5.
2. Tap the star icon next to that device's name. It should switch to filled and show "Starred -
   will auto-reconnect..." underneath.
3. Unplug the device. The "Follow"/"Send to" button for it should clear back to its default
   state (or the row disappear entirely, depending on whether you also re-scan).
4. Plug the device back in **without** tapping "Scan USB MIDI" or "Follow" again. Within a
   second or two it should reappear in the list already connected (the Follow/Send button should
   read "Stop following"/"Stop sending"), and the "Clock" status line should resume showing a
   measured BPM if you were following it - all without touching Settings.
5. Repeat with Settings closed entirely after step 2 (back out, then unplug/replug) - the
   reconnect is driven by `UsbMidiConnector.attach()`'s app-wide callback, not anything the
   Settings screen itself sets up, so it should behave identically with Settings closed.
6. Tap the star again to unstar, then unplug/replug once more - it should **not** auto-reconnect
   this time, confirming the desired state is actually being forgotten on unstar, not just
   hidden in the UI.

If reconnect doesn't happen automatically, logcat for `MidiManager` (`onDeviceAdded` firing at
all is the first thing to confirm) and for `UsbMidiConnector` would point at whether the callback
never fired, fired but didn't recognize the device as starred (worth checking whether the
device's `PROPERTY_SERIAL_NUMBER` is null/empty and it's falling back to display name - two
identical-model devices with no serial would then collide on the same key), or fired and tried to
reconnect but `openDevice()` failed.

## 7. Sending MIDI Note/CC actions per beat type

Also never run against real hardware - `MidiActionSender` is unit-tested at the byte-construction
level (`MidiActionSenderTest`, a fake `MidiReceiver`), but not against anything that actually
interprets Note On/Off or CC as a synth or a lighting rig would.

1. Get section 5 (sending clock) working first, or skip clock entirely and just connect via
   "Send to" - Actions doesn't require clock-out to also be on, they're independent switches.
2. In Settings → MIDI Actions, turn on the section's own switch (separate from "Send MIDI clock").
3. Pick a beat type - **Bar** is the easiest first test, since it fires on every single beat with
   `beatCount` set to 1 (Settings → Tempo & Bars, or just watch beat 1 of a normal bar). Set it to
   **Note**, a channel, a note number, a velocity, and a short duration (20-50ms is a reasonable
   start).
4. Point your synth/DAW/MIDI monitor at whatever you connected in step 1, and press play in
   qMetronome.

**What should happen:** a Note On at the configured note/velocity/channel on every Bar beat,
followed by a Note Off roughly `durationMs` later - on a synth, this should sound a distinct,
short note in time with the downbeat; on a MIDI monitor, you should see matched On/Off pairs, not
Note Ons with no matching Off (which would mean a receiver is holding a stuck note).

Once Bar works, mark some beats **Accent**/**Strong Accent**/**Custom** (long-press the
beats-per-bar number, tap a beat's chip to cycle it - see the in-app Help screen or
`docs/user-guide/marking-beat-accents.md`) and configure a different Note or CC for each in
Settings → MIDI Actions' per-sound tabs. Confirm each type actually fires only on beats marked
that way, and that switching a beat type's action to **CC** sends a single 3-byte CC message with
no Note Off follow-up (there's nothing to release).

**Also worth checking specifically:**

- **MIDI Actions still fires with Click off, or during a randomly-muted beat.** This is
  deliberate, not a bug - see `MetronomeEngine.beatTypeFor`'s own kdoc. Turn Click off in
  Settings, confirm the configured beat action still fires on your synth/monitor.
- **Velocity 0 never arrives as a de-facto Note Off.** Set a beat's velocity to its minimum in the
  UI and confirm the monitor shows a real Note On (velocity 1, not 0) - some receivers treat a
  velocity-0 Note On as an implicit Note Off, which `MidiActionSender` deliberately floors against.
- **Multiple beat types configured at once don't interfere with each other or with outgoing
  clock**, if you also have "Send MIDI clock" on to the same device - Bar's Note shouldn't delay
  or garble a clock tick landing at nearly the same moment, and vice versa.

**If it doesn't work:** logcat for `MidiActionSender` will show every send attempt and any
exception from a destination that's gone away, the same shape `MidiClockSender`'s own logging
already has. If bytes never leave the phone at all, first confirm the MIDI Actions section's own
switch is on (separate from "Send MIDI clock") and that the beat type you're testing isn't set to
**None**.

### 7.1 Per-beat overrides across any phrase/bar

In Settings → Beat Overrides, browse to a specific phrase and bar - the same dot pickers the main
screen's own phrase/bar queues use, shown only once there's more than one to choose from - then
step to a beat within it (the +/- stepper is scoped to whichever bar is currently selected, not
necessarily the one actually playing) and assign it a Note/CC distinct from that beat's type-level
default configured in section 7 above.

**What should happen:** during playback, that one beat sends its own override, while every other
beat of the same type still sends the type's own default - confirm both on a monitor across a
few bars. Also confirm the override lands where you actually browsed to, not wherever playback
happens to be: with a second bar or phrase queued, pick a *non-active* one, set an override there,
and check it does **not** fire while the truly-active bar is playing - only once you navigate
playback to that bar/phrase does it start firing. The section's own summary text shows "N set"
across every phrase/bar combined once any beat anywhere has an override; clearing one via "Clear
override" should drop that count and revert the beat back to following its type's default (shown
as "Following &lt;Type&gt;'s own default").

### 7.2 Phrase actions

With more than one phrase queued (see `docs/user-guide/phrase-queue-management.md` for the
gesture), open Settings → Phrase Actions, pick a phrase via its dot, and assign it a Note/CC.

**What should happen:** the action fires exactly once every time that phrase becomes active -
tapping its dot on the main screen, arriving via the automatic Once-mode cascade at a phrase
boundary, and even tapping the dot of the phrase that's *already* active (re-entering fires again,
it's not gated on a genuine transition). Confirm a phrase with no action configured (the default)
stays silent - only phrases you've explicitly assigned one to should ever send anything here.

### 7.3 The main screen's manual Trigger action (TAP, once latched)

With MIDI Actions on (Settings → MIDI Actions), latch HOLD (long-press or double-tap it). TAP
switches from tap-tempo to a lightning-bolt Trigger button for as long as the latch holds - tapping
it fires whatever's actually configured for the engine's *live* current beat position (its type's
default, or a per-beat override if one applies - same resolution `onBeat` itself uses), without
unlatching or starting playback.

**What should happen:** confirm it works both while stopped (a one-shot send with no playback
running) and while playing (fires the same thing `onBeat` would have sent for that beat,
independent of the live beat-firing loop, so pressing it mid-beat shouldn't double up or otherwise
disrupt the running click), and that it can be tapped repeatedly without dropping the latch.
Confirm TAP reverts to tap-tempo the moment either the latch ends (a later tap on HOLD) or MIDI
Actions is turned back off, and that with MIDI Actions off, tapping TAP while latched still does
its original job (commits the staged tempo, starts playing, unlatches).
