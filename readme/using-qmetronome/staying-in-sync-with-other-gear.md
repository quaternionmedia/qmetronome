# Staying in sync with other gear

[← Using qMetronome](README.md) · [Root README](../../README.md)

![Opening Settings from the running app, then toggling Mechanical/Organic clock feel, in motion](../../docs/images/generated/videos/settings-clock-feel.gif)

Settings → Clock → "Send MIDI clock" turns qMetronome into a MIDI clock source (24 ppqn) for other
apps or USB gear - the mirror image of following an external clock, which happens automatically
the instant MIDI Clock activity arrives from another app or a connected USB device, falling back to
internal timing if that feed goes quiet. USB devices get their own row in Settings → MIDI, with
independent "Follow clock" and "Send clock" toggles per device, so a device can be followed, sent
to, both, or neither; long-press a device to star it, and it reconnects automatically - restoring
whichever connections were active - the next time it's plugged in, whether or not Settings happens
to be open at the time. "Outgoing clock feel" (Mechanical vs. Organic), shown above, only affects
the clock *sent* to other gear, not this app's own click or flash: Mechanical actively corrects it
for the truest, most locked-in beat; Organic lets a followed clock's own natural timing variance
through unfiltered. See [`docs/external-midi-clock.md`](../../docs/external-midi-clock.md) for the
full design rationale behind both directions.

Every gesture here also has its own screenshot/video page in
[the user guide](../../docs/user-guide/README.md#midi-clock).
