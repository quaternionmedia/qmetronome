# License

[← Root README](../README.md)

qmetronome's own source is MIT-licensed (see [`LICENSE`](../LICENSE)). That
covers everything in this repository except `app/libs/glyph-matrix-sdk-2.0.aar`,
which is a closed-source binary distributed by Nothing Technology Limited
under its own terms (see [`governance/qm/adr/DRAFT-glyph-matrix-sdk-dependency.md`](../governance/qm/adr/DRAFT-glyph-matrix-sdk-dependency.md)
for why that dependency exists and how it's isolated). Third-party
dependencies pulled in via Gradle (AndroidX, Kotlin, Robolectric, etc.) remain
under their own licenses, not relicensed by this project's MIT grant.

The app's privacy policy is [`PRIVACY.md`](../PRIVACY.md) — short version: no data
collection, no analytics, no network calls, settings stay on-device. See
[`docs/app-store-checklist.md`](../docs/app-store-checklist.md) for what's left
before this can actually be submitted to Google Play and what's confirmed
vs. genuinely unverified about Nothing's distribution channel.
