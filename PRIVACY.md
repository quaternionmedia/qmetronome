# Privacy Policy

**Last updated: 2026-06-29**

qMetronome does not collect, store, transmit, or share any personal data,
usage data, or analytics. There are no accounts, no sign-in, no ads, and no
third-party SDKs that collect data.

## What the app stores, and where

Tempo, time signature, visualizer choice, click on/off, and MIDI clock-out
on/off are saved locally on your device (Android `SharedPreferences`) so
your settings persist between sessions. None of this ever leaves your
device - there is no server, and the app makes no network requests of its
own.

## Permissions

- **MIDI** (`android.software.midi`, optional) and USB device access: used
  only to send/receive MIDI Clock to/from other apps or hardware you
  explicitly connect to in Settings. This is local device communication,
  not data collection.
- **Glyph Matrix** (`com.nothing.ketchum.permission.ENABLE`): used only on
  supported Nothing phones to drive the Glyph Matrix LED display. No effect
  on other devices.
- A small set of permissions (`WAKE_LOCK`, `ACCESS_NETWORK_STATE`,
  `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE`) are declared by Android
  Jetpack libraries this app uses (WorkManager, via the home screen widget
  support library) for reliability purposes - the app does not use them to
  access the network or collect data, and none of them trigger a permission
  prompt.

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
