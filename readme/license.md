# License

[← Root README](../README.md)

Copyright (c) 2026 Quaternion Media.

| What | Licence |
|---|---|
| The app: source, resources, build scripts and tooling | GPL-3.0-or-later, with the additional permission below ([`LICENSE`](../LICENSE)) |
| Prose and its pictures: `README.md`, `CONTRIBUTING.md`, `CHANGELOG.md`, `PRIVACY.md`, `docs/`, `readme/` | CC-BY-SA-4.0 |
| Files copied from the QM constitution's project seed: `AGENTS.md` and `CLAUDE.md` | CC0-1.0 |
| Files copied from the same seed: its workflows under `.github/workflows/`, and `.vscode/` | Apache-2.0 |
| The Gradle wrapper: `gradlew`, `gradlew.bat`, `gradle/wrapper/` | Apache-2.0, the Gradle authors' |

[`REUSE.toml`](../REUSE.toml) declares the same per path, and
[`LICENSES/`](../LICENSES) holds each licence's text. `reuse lint` checks that
every file is covered.

## The Glyph Matrix SDK

The app links `glyph-matrix-sdk-2.0.aar`, a closed-source binary of Nothing
Technology Limited under the Glyph SDK End User License Agreement in
[Nothing's Developer Kit](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit).
It is not in this repository and not under any licence above: the build
downloads it from the Developer Kit (see
[`governance/qm/adr/DRAFT-glyph-matrix-sdk-dependency.md`](../governance/qm/adr/DRAFT-glyph-matrix-sdk-dependency.md)
for why that dependency exists and how it's isolated).

The GPL alone does not permit conveying the app combined with a library under
those terms, so the app's licence carries this additional permission:

> Additional permission under GNU GPL version 3 section 7
>
> If you modify this Program, or any covered work, by linking or combining it
> with the Glyph Matrix SDK of Nothing Technology Limited (or a modified
> version of that library), containing parts covered by the terms of the Glyph
> SDK End User License Agreement, the licensors of this Program grant you
> additional permission to convey the resulting work.

The permission is the app's licensors'. What may be done with the SDK itself
is Nothing's agreement to say, and that agreement restricts redistribution and
commercial use.

## Dependencies

Third-party dependencies pulled in via Gradle (AndroidX, Kotlin, Robolectric,
etc.) remain under their own licenses, not relicensed by this project.

The app's privacy policy is [`PRIVACY.md`](../PRIVACY.md) — short version: no data
collection, no analytics, no network calls, settings stay on-device. See
[`docs/app-store-checklist.md`](../docs/app-store-checklist.md) for what's left
before this can actually be submitted to Google Play and what's confirmed
vs. genuinely unverified about Nothing's distribution channel.
