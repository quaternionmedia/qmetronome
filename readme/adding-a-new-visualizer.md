# Adding a new visualizer

[← Root README](../README.md)

![Swiping the preview to cycle through the built-in visualizers, in motion](../docs/images/generated/videos/preview-swipe-visualizer.gif)

Implement `GlyphVisualizer`:

```kotlin
class MyVisualizer : GlyphVisualizer {
    override val id = "my_visualizer"
    override val displayName = "My Visualizer"

    override fun render(matrixSize: Int, beat: BeatPhase): IntArray {
        val canvas = GlyphCanvas(matrixSize)
        // beat.phase is 0..1 progress through the current beat, beat.isAccent marks beat 1
        canvas.filledCircle(canvas.center, canvas.center, matrixSize * 0.3f, 255)
        return canvas.toIntArray()
    }
}
```

Add an instance to `VisualizerRegistry.all` and it's done — it shows up in the in-app picker
and becomes selectable via Glyph Button long-press. No service, threading, or SDK code needed;
`render()` is called continuously by the engine and is a pure function of `BeatPhase`.

Two requirements every visualizer must meet, enforced by `VisualizerRenderTest` (see
`GlyphVisualizer`'s docs for the full rationale):

1. **The beat must read without audio** — more total light at `phase == 0` than mid-decay
   (e.g. `phase == 0.5`).
2. **Bar 1 must read distinctly from the other beats** — more total light when `isAccent` is
   true than at the same phase with `isAccent` false.

Brightness alone usually can't carry requirement 2, since it's already pushed near maximum at
`phase == 0` to satisfy requirement 1 — scale the *size* of whatever's flashing instead (see any
built-in visualizer's `accentScale` pattern). `GlyphCanvas.line()` is available alongside
`filledCircle()`/`ring()` for arm/pendulum-style visualizers.
