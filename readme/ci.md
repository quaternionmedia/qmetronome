# CI

[← Root README](../README.md)

`.github/workflows/ci.yml` runs the Glyph SDK import-boundary check (enforcing
the isolation claimed in `governance/qm/adr/DRAFT-glyph-matrix-sdk-dependency.md`),
then a full `assembleDebug` + `testDebugUnitTest`, on every push to `main` and
every pull request.
