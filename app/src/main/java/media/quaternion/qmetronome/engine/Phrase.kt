package media.quaternion.qmetronome.engine

/**
 * One entry in [MetronomeEngine]'s phrase queue - a song-form section ("Verse", "Chorus") carrying
 * its own full bar queue, exactly the shape [MetronomeEngine.timeSignatureQueue] already is for
 * the single-phrase case. [barQueueMode] is this phrase's own [MetronomeEngine.QueueMode], governing how
 * *its* bars advance into each other - independent of [MetronomeEngine.phraseQueueMode], which
 * governs how *phrases* advance into each other once a phrase's own bars are done (see
 * [MetronomeEngine.beatTypeFor]'s sibling, the phrase-boundary cascade in
 * `MetronomeEngine.advanceQueueAtBarBoundary`). A single-entry [phrases] list (today's default)
 * behaves exactly like a plain, unchanging bar queue - there is nothing about "phrases" to notice
 * until a second one is added.
 *
 * [action] is this phrase's own MIDI action, fired once whenever [MetronomeEngine.goToPhrase]
 * resolves to it (see that function's own kdoc) - defaults to [MidiBeatAction]'s own NONE type, so
 * a phrase nobody has configured this for fires nothing, the same "opt-in, silent by default"
 * shape [TimeSignature.midiOverrides] already establishes one level down.
 */
data class Phrase(
    val bars: List<TimeSignature> = listOf(TimeSignature.DEFAULT),
    val barQueueMode: MetronomeEngine.QueueMode = MetronomeEngine.QueueMode.LOOP,
    val action: MidiBeatAction = MidiBeatAction(),
)
