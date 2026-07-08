# Setup notes

[← Root README](../README.md)

- `app/libs/glyph-matrix-sdk-2.0.aar` is the Glyph Matrix SDK from the Developer Kit.
- `minSdk` is 33, required by the SDK itself (the Glyph Matrix only exists on phones running
  recent Android anyway).
- The Glyph Toy preview icon (`drawable/toy_preview.xml`) is generated pixel art, not hand-drawn:
  it's produced directly from the same static-logo pose used by `ic_launcher_foreground.xml` and
  `BrandMarks.kt`'s `AppBrandMark` (see that file's doc), rasterized onto a matrix via
  `GlyphCanvas` so it reads as real Glyph Matrix pixel art rather than a smooth vector. It has
  **not** been checked against the Developer Kit's own spec images (`23112_spec.svg` /
  `25111_spec.svg`) for exact dimensions/format conventions - worth a look before shipping.
