# Privacy Policy

**Last updated: 2026-07-06**

qMetronome does not collect, store, transmit, or share any personal data,
usage data, or analytics. There are no accounts, no sign-in, no ads, and no
third-party SDKs that collect data.

## What the app stores, and where

Tempo, time signature, visualizer choice, click on/off, MIDI clock-out on/off,
and persistent-playback on/off are saved locally on your device (Android
`SharedPreferences`) so your settings persist between sessions. None of this
ever leaves your device - there is no server, and the app makes no network
requests of its own. Long-pressing either of the small brand marks in the app
opens a GitHub page in your own browser - an explicit, user-initiated link,
not a network call the app makes on your behalf.

## Permissions

- **MIDI** (`android.software.midi`, optional) and USB device access: used
  only to send/receive MIDI Clock to/from other apps or hardware you
  explicitly connect to in Settings. This is local device communication,
  not data collection.
- **Glyph Matrix** (`com.nothing.ketchum.permission.ENABLE`): used only on
  supported Nothing phones to drive the Glyph Matrix LED display. No effect
  on other devices.
- **Notifications** (`POST_NOTIFICATIONS`) - the one permission prompt this
  app can show, and only if you turn on Settings → Playback → "Persistent
  playback" (off by default). It's used solely to show a local, ongoing
  notification ("qMetronome running - N BPM") while that mode keeps the
  metronome going in the background; nothing about the notification is sent
  anywhere. Declining this prompt does not disable the feature - the
  metronome keeps running either way, just without a visible notification.
- **Battery optimization exemption** (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) -
  also only requested if you turn on persistent playback, and also only a
  nudge: Android shows its own system dialog, and declining it leaves
  persistent playback working, just with standard (rather than the
  strongest OEM-level) protection from being paused in the background.
- **Foreground service** (`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`) -
  what actually keeps the metronome's timing running in the background while
  persistent playback is on. Declared and used directly by the app for this
  one purpose; not a data-collection mechanism.
- A small set of additional permissions (`WAKE_LOCK`, `ACCESS_NETWORK_STATE`,
  `RECEIVE_BOOT_COMPLETED`) are declared by Android Jetpack libraries this app
  uses (WorkManager, via the home screen widget support library) for
  reliability purposes - the app does not use them to access the network or
  collect data, and none of them trigger a permission prompt.

## Open source

qMetronome's source code is publicly available at
[github.com/quaternionmedia/qmetronome](https://github.com/quaternionmedia/qmetronome)
under the MIT License, so this policy can be verified directly against what
the app actually does.

## Contact

Questions about this policy: open an issue at
[github.com/quaternionmedia/qmetronome/issues](https://github.com/quaternionmedia/qmetronome/issues).

## Changes

If this policy ever changes (e.g. a future feature adds analytics or a
network call), this file will be updated and the "Last updated" date above
will change accordingly.
