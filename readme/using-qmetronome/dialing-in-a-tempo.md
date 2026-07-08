# Dialing in a tempo

[← Using qMetronome](README.md) · [Root README](../../README.md)

![Dragging the BPM number to scrub tempo, in motion](../../docs/images/generated/videos/bpm-drag-scrub.gif)

Every tempo control writes to the same underlying value, so you can mix them freely - tap to get
roughly the right speed, then drag to fine-tune, without ever fighting the last method you used.

Before a set, or between songs, tap the **TAP** button or the BPM number itself in rhythm and
qMetronome derives a BPM from a rolling average of your last few taps. Tapping while stopped only
dials in a tempo - it doesn't start playback, so you can settle on the right number before
committing to a downbeat. The one exception is HOLD: latch it (below) and tapping out a tempo more
than once both commits the tapped value *and* starts playback immediately, at the current time
signature - a deliberate "count it in and go" gesture for starting a song cold.

Need more precision than your thumb can tap? Press and drag the BPM number left or right for
continuous fine adjustment, or long-press it to type an exact value. That same long-press dialog
is unit-aware: chips switch between BPM (1–400, or 0.1–12000 with Extended range on), BPH, and BPS
mid-entry, landing on a sensible value in the new unit rather than a literal, often-nonsensical
arithmetic conversion of what you'd typed - switching chips *is* the "convert between units"
gesture. Settings' own "Jump to unit" chip row is the same idea one level up, for jumping the
*live* tempo straight into BPH/BPS range without dragging all the way there. The step buttons
flanking the BPM number move it ±1 BPM per tap, or accelerate the longer you hold them, for coarse
adjustment without leaving the keyboard-free main screen.

HOLD itself is worth knowing well: press and hold it while adjusting BPM or beats-per-bar, and the
change stages instead of applying immediately - shown in "recording red" until you let go, at
which point everything you changed commits at once. That's useful mid-performance, for lining up
the next section without disturbing what's currently playing. Long-press or double-tap HOLD to
latch it instead, so staging stays active without holding a finger down, until a later tap on HOLD
flushes everything and unlatches. A latched beats-per-bar change specifically waits for the next
bar's downbeat before taking effect, rather than cutting the current bar short - the same courtesy
a live musician bringing in a meter change would extend the rest of the band.

Every gesture here also has its own screenshot/video page in
[the user guide](../../docs/user-guide/README.md#tempo).
