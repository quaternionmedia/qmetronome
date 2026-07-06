package media.quaternion.qmetronome.ui

import media.quaternion.qmetronome.engine.MetronomeEngine

/**
 * The three ways a tempo can be read - matching [bpmDisplayValue]/[bpmDisplayUnit]'s own automatic
 * unit selection by value (BPM in the normal 1-400 range, BPH below it, BPS above it) - but made
 * an explicit, user-selectable choice rather than only a derived display. [BpmUnitEntryDialog] and
 * the matching Settings switcher both need to let someone pick a unit *before* landing on a raw
 * bpm value in it, which a purely value-derived display can't do.
 */
enum class BpmUnit(val label: String) {
    BPM("BPM"),
    BPH("BPH"),
    BPS("BPS"),
}

/** The natural unit for [bpm] - matches [bpmDisplayUnit]'s own thresholds exactly, so a dialog or
 * switcher opened against a live bpm always starts on the unit that's already showing. */
fun bpmUnitFor(bpm: Float): BpmUnit = when {
    bpm < MetronomeEngine.MIN_BPM -> BpmUnit.BPH
    bpm > MetronomeEngine.MAX_BPM -> BpmUnit.BPS
    else -> BpmUnit.BPM
}

/** Converts a raw bpm value into [unit]'s own terms - the inverse of [bpmFromUnitValue]. Matches
 * [bpmDisplayValue]'s own ×60/÷60 conversions exactly, just returning a `Float` instead of a
 * formatted string. */
fun bpmToUnitValue(bpm: Float, unit: BpmUnit): Float = when (unit) {
    BpmUnit.BPH -> bpm * 60f
    BpmUnit.BPS -> bpm / 60f
    BpmUnit.BPM -> bpm
}

/** Converts a value expressed in [unit]'s own terms back to raw bpm - the inverse of
 * [bpmToUnitValue], and the one function that actually needs calling before handing a
 * unit-entered number to [MetronomeEngine.setBpm]. */
fun bpmFromUnitValue(value: Float, unit: BpmUnit): Float = when (unit) {
    BpmUnit.BPH -> value / 60f
    BpmUnit.BPS -> value * 60f
    BpmUnit.BPM -> value
}

/** The valid entry range for [unit], expressed in that unit's own terms - derived from
 * [MetronomeEngine]'s canonical raw-bpm bounds via [bpmToUnitValue] rather than restating a bound
 * by hand in a second unit (which would drift the moment either side changed). */
fun bpmRangeFor(unit: BpmUnit): ClosedFloatingPointRange<Float> = when (unit) {
    BpmUnit.BPH -> bpmToUnitValue(MetronomeEngine.EXTENDED_MIN_BPM, unit)..bpmToUnitValue(MetronomeEngine.MIN_BPM, unit)
    BpmUnit.BPS -> bpmToUnitValue(MetronomeEngine.MAX_BPM, unit)..bpmToUnitValue(MetronomeEngine.EXTENDED_MAX_BPM, unit)
    BpmUnit.BPM -> MetronomeEngine.MIN_BPM..MetronomeEngine.MAX_BPM
}

/** A reasonable starting value for [unit], in that unit's own terms - used when switching *to* a
 * unit the current bpm isn't already naturally in (see [BpmUnitEntryDialog]) or when jumping
 * straight to a unit from the Settings switcher, where converting the *previous* unit's number
 * arithmetically would land somewhere nonsensical (e.g. 120 BPM read as "BPH" is 7200 BPH - a
 * real conversion, but nowhere near what anyone switching to BPH actually wants). */
fun bpmDefaultUnitValue(unit: BpmUnit): Float = when (unit) {
    BpmUnit.BPH -> 30f
    BpmUnit.BPS -> 10f
    BpmUnit.BPM -> 120f
}
