# Known limitations / next steps

[← Root README](../README.md)

- Phone (4a) Pro (`DEVICE_25111p`) is AOD-only per the kit, which only refreshes once a minute —
  not useful for live tempo. The toy currently isn't registered with `aod_support`, so on that
  device it just won't show up in the toy list; full AOD support is future work.
- External MIDI clock sync is implemented for virtual (inter-app) and USB transports, in both
  directions (following, and sending our own clock out). Following and sending to the *same* USB
  device simultaneously is allowed rather than blocked, with a UI heads-up about devices that
  echo MIDI Thru - that combination hasn't been verified against real hardware yet. MIDI Song
  Position Pointer / proper Continue support is not implemented (Continue currently behaves like
  Start - resets to beat 0). Bluetooth LE MIDI is not implemented yet (see
  `docs/external-midi-clock.md`).
- The home screen widget is BPM + play/stop only, by design - no matrix-preview thumbnail (see
  [`docs/home-screen-widget.md`](../docs/home-screen-widget.md) for why a live-pulsing widget was
  ruled out, and the manual test checklist, since a placed widget isn't unit-testable here).
- USB MIDI only scans `TRANSPORT_MIDI_BYTE_STREAM` devices; a USB-MIDI 2.0/UMP-only device
  wouldn't show up in the scan yet (see the troubleshooting section in
  `docs/usb-midi-test-plan.md`).
