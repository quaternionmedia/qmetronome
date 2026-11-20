# License

[← Root README](../README.md)

qmetronome's own source is MIT-licensed (see [`LICENSE`](../LICENSE)). The
Glyph Matrix SDK the app links, `glyph-matrix-sdk-2.0.aar`, is not in this
repository: it is a closed-source binary of Nothing Technology Limited, under
the Glyph SDK End User License Agreement in
[Nothing's Developer Kit](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit),
and the build downloads it from there (see [`governance/qm/adr/DRAFT-glyph-matrix-sdk-dependency.md`](../governance/qm/adr/DRAFT-glyph-matrix-sdk-dependency.md)
for why that dependency exists and how it's isolated). Third-party
dependencies pulled in via Gradle (AndroidX, Kotlin, Robolectric, etc.) remain
under their own licenses, not relicensed by this project's MIT grant.

The app's privacy policy is [`PRIVACY.md`](../PRIVACY.md) — short version: no data
collection, no analytics, no network calls, settings stay on-device. See
[`docs/app-store-checklist.md`](../docs/app-store-checklist.md) for what's left
before this can actually be submitted to Google Play and what's confirmed
vs. genuinely unverified about Nothing's distribution channel.
