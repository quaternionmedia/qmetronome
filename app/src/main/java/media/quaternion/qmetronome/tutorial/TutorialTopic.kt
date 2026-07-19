package media.quaternion.qmetronome.tutorial

/**
 * One demonstrable, user-facing behavior - the single shared source of *content* for both the
 * generated end-user doc (`docs/user-guide/`, built by the `generateUserGuide` Gradle task from
 * this list) and the in-app Help screen (`ui/HelpScreen.kt`). Each topic also has exactly one
 * Compose UI test (a `*ScreenshotTest.kt` file under `app/src/test/java/.../ui/`) that both asserts the real
 * behavior and captures the illustrative screenshot the doc embeds - [id] is that screenshot's
 * filename (via `screenshotPath(topic.id)` in `ComposeTestSupport.kt`), so a topic and its test
 * can never silently drift apart in naming.
 *
 * [hasVideo] additionally means a `*VideoTest.kt` file exists too, `recordRoboVideo`-capturing an
 * animated GIF (via `videoPath(topic.id)`) of the same gesture in motion - reserved for topics
 * where a single before/after screenshot pair genuinely loses information a static image can't
 * convey (a drag, a swipe, HOLD's timing-gated latch promotion), not applied blanket to every
 * topic. Both files use the *same* [id], so the two can never drift apart either.
 *
 * The in-app Help screen deliberately does *not* read a screenshot or video for [id] - it renders
 * the real, live composable inline instead (richer than either, and automatically theme-correct) -
 * see `HelpScreen.kt`. Only the external doc needs the generated image/video.
 */
data class TutorialTopic(
    val id: String,
    val title: String,
    val description: String,
    val category: TutorialCategory,
    val hasVideo: Boolean = false,
)

/** Groups topics for both the generated doc's section headers and the Help screen's navigation. */
enum class TutorialCategory(val displayName: String) {
    TEMPO("Tempo"),
    TIME_SIGNATURE("Time Signature"),
    BAR_QUEUE("Bar Queue"),
    MIDI("MIDI"),
    GLYPH_TOY("Glyph Matrix"),
    SETTINGS("Settings & Layout"),
}

/**
 * The registry - every topic this app has a Help entry and doc section for. Order here is the
 * order both the doc and the Help screen present them in, so it's deliberately curated (roughly
 * "the things you'd want to learn first" before "settings and layout tweaks"), not alphabetical.
 */
object TutorialTopics {
    val all: List<TutorialTopic> = listOf(
        TutorialTopic(
            id = "bpm-drag-scrub",
            title = "Drag to fine-tune tempo",
            description = "Drag the BPM number left or right to adjust tempo continuously, " +
                "instead of tapping the +/- steppers one beat at a time. Works while stopped or " +
                "playing.",
            category = TutorialCategory.TEMPO,
            hasVideo = true,
        ),
        TutorialTopic(
            id = "bpm-drag-scrub-boundary",
            title = "Scrubbing into BPH/BPS territory",
            description = "With Extended range on, dragging the tempo below 1 BPM or above 400 " +
                "BPM switches to beats-per-hour or beats-per-second automatically - the same " +
                "drag gesture, just a wider range. Dragging back the other way returns to " +
                "ordinary BPM.",
            category = TutorialCategory.TEMPO,
            hasVideo = true,
        ),
        TutorialTopic(
            id = "bpm-unit-entry-dialog",
            title = "Type an exact tempo, in any unit",
            description = "Long-press the BPM number to type an exact value. Chips let you pick " +
                "which unit you're typing in - BPM, BPH, or BPS - converting automatically " +
                "rather than making you do the math.",
            category = TutorialCategory.TEMPO,
            hasVideo = true,
        ),
        TutorialTopic(
            id = "tap-tempo",
            title = "Tap out a tempo",
            description = "Tap the BPM number in rhythm to set the tempo by ear - the first tap " +
                "just starts timing, the second and later taps derive a BPM from the interval " +
                "between taps.",
            category = TutorialCategory.TEMPO,
            hasVideo = true,
        ),
        TutorialTopic(
            id = "hold-momentary-staging",
            title = "Hold to stage a change",
            description = "Press and hold HOLD, then adjust tempo or beats-per-bar - the change " +
                "is staged, not applied, until you release. Release to commit it all at once, " +
                "rather than the engine reacting to every intermediate value.",
            category = TutorialCategory.TEMPO,
            hasVideo = true,
        ),
        TutorialTopic(
            id = "hold-sticky-latch",
            title = "Latch HOLD for sticky staging",
            description = "Long-press or double-tap HOLD to latch it - staging stays active " +
                "without holding the button down, until a later tap on HOLD flushes everything " +
                "and unlatches.",
            category = TutorialCategory.TEMPO,
            hasVideo = true,
        ),
        TutorialTopic(
            id = "time-signature-drag-scrub",
            title = "Drag time signature numbers",
            description = "The beats-per-bar and note-value numbers scrub the same way the BPM " +
                "number does - drag left or right for continuous adjustment, long-press to type " +
                "an exact value.",
            category = TutorialCategory.TIME_SIGNATURE,
            hasVideo = true,
        ),
        TutorialTopic(
            id = "bar-queue-management",
            title = "Build a queue of bars",
            description = "Add a bar to line up a sequence of differently-metered bars - each " +
                "one remembers its own beats-per-bar, note value, and tempo. Tap a bar to jump " +
                "to it; long-press to remove it.",
            category = TutorialCategory.BAR_QUEUE,
            hasVideo = true,
        ),
        TutorialTopic(
            id = "bar-queue-mode-cycling",
            title = "Choose how the queue advances",
            description = "Tap the queue mode icon to cycle between Loop (wraps back to the " +
                "first bar), Once (stops advancing at the last bar), and Manual (only moves when " +
                "you tap a bar directly).",
            category = TutorialCategory.BAR_QUEUE,
            hasVideo = true,
        ),
        TutorialTopic(
            id = "phrase-queue-management",
            title = "Group bars into phrases",
            description = "Below the bar queue, a small icon adds a phrase - a song-form section " +
                "(\"Verse\", \"Chorus\") with its own full bar queue. Invisible until you add a " +
                "second phrase; tap a phrase to jump to it, long-press to remove it.",
            category = TutorialCategory.BAR_QUEUE,
            hasVideo = true,
        ),
        TutorialTopic(
            id = "phrase-queue-mode-cycling",
            title = "Choose how phrases advance",
            description = "Once a second phrase exists, tap the phrase mode icon to cycle Loop, " +
                "Once, and Manual - the same three modes the bar queue uses, one level up, " +
                "governing how playback flows from one phrase into the next.",
            category = TutorialCategory.BAR_QUEUE,
            hasVideo = true,
        ),
        TutorialTopic(
            id = "marking-beat-accents",
            title = "Mark a beat as accented",
            description = "Long-press the beats-per-bar number to open time signature entry, " +
                "then tap any beat's chip to cycle it through Accent, Strong Accent, Custom, and " +
                "back to unmarked. These are the same beat types the audible click and MIDI " +
                "Actions can each be tuned per-beat for.",
            category = TutorialCategory.TIME_SIGNATURE,
        ),
        TutorialTopic(
            id = "settings-jump-to-unit",
            title = "Jump straight to BPM/BPH/BPS",
            description = "In Settings, tap a unit chip to jump the live tempo straight into that " +
                "range - a quick shortcut instead of dragging or typing an exact value.",
            category = TutorialCategory.SETTINGS,
            hasVideo = true,
        ),
        TutorialTopic(
            id = "settings-clock-feel",
            title = "Mechanical vs Organic outgoing clock",
            description = "Mechanical actively corrects the outgoing MIDI clock for the truest, " +
                "most locked-in beat. Organic lets a followed clock's own natural timing " +
                "variance through unfiltered. Only affects clock sent to other apps/gear, not " +
                "this app's own click or flash.",
            category = TutorialCategory.MIDI,
            hasVideo = true,
        ),
        TutorialTopic(
            id = "midi-actions",
            title = "Send MIDI notes or CC per beat type",
            description = "In Settings -> MIDI Actions, turn on beat actions and pick Note or CC " +
                "for any beat type (Bar, Beat, Accent, Strong Accent, Custom) - sent over the " +
                "same virtual/USB connections \"Send clock\" already reaches, independent of " +
                "whether the audible click is on.",
            category = TutorialCategory.MIDI,
        ),
        TutorialTopic(
            id = "beat-overrides",
            title = "Give one beat its own MIDI action",
            description = "In Settings -> Beat Overrides, step to any beat and assign it its own " +
                "MIDI action, overriding its type's default for that beat only. The Trigger " +
                "button fires whatever's configured for the engine's current beat position, for " +
                "one-shot testing without starting playback.",
            category = TutorialCategory.MIDI,
        ),
        TutorialTopic(
            id = "phrase-actions",
            title = "Give a phrase its own MIDI action",
            description = "In Settings -> Phrase Actions, step to any phrase and assign it its " +
                "own MIDI action, fired once whenever you jump to that phrase - tapping its dot " +
                "on the main screen, or arriving there automatically as the queue advances.",
            category = TutorialCategory.MIDI,
        ),
        TutorialTopic(
            id = "preview-swipe-visualizer",
            title = "Swipe to cycle visualizers",
            description = "Swipe the Glyph Matrix preview left or right to cycle through " +
                "available visualizers.",
            category = TutorialCategory.GLYPH_TOY,
            hasVideo = true,
        ),
        TutorialTopic(
            id = "preview-double-tap-play",
            title = "Double-tap to play/stop",
            description = "Double-tap the preview to toggle playback without reaching for the " +
                "play/stop button.",
            category = TutorialCategory.GLYPH_TOY,
            hasVideo = true,
        ),
        TutorialTopic(
            id = "preview-long-press-settings",
            title = "Long-press to open Settings",
            description = "Long-press the preview as a shortcut to Settings, in addition to the " +
                "dedicated settings button.",
            category = TutorialCategory.GLYPH_TOY,
            hasVideo = true,
        ),
        TutorialTopic(
            id = "compact-landscape-layout",
            title = "Compact landscape layout",
            description = "In Settings -> Layout, enable Compact landscape layout so rotating " +
                "the phone puts the preview and controls side-by-side instead of overflowing.",
            category = TutorialCategory.SETTINGS,
            hasVideo = true,
        ),
        TutorialTopic(
            id = "symbol-only-controls",
            title = "Symbol-only controls",
            description = "In Settings -> Layout, enable Symbol-only controls to drop text " +
                "labels from the main screen's tempo/transport controls in favor of icons and " +
                "dots.",
            category = TutorialCategory.SETTINGS,
            hasVideo = true,
        ),
        TutorialTopic(
            id = "unit-symbols",
            title = "Unit symbols",
            description = "In Settings -> Layout, Unit symbols (on by default) shows a small mark " +
                "next to BPM, beats, beat type, bar, and phrase controls, naming what each one is " +
                "at a glance. Turn off for a cleaner, symbol-free look.",
            category = TutorialCategory.SETTINGS,
            hasVideo = true,
        ),
    )
}
