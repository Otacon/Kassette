package nes.apu

data class ApuState(
    var pulse1: PulseState = PulseState(),
    var pulse2: PulseState = PulseState(),
    var triangle: TriangleState = TriangleState(),
    var noise: NoiseState = NoiseState(),
    var dmc: DmcState = DmcState(),
    var frameCycle: Int = 0,
    var frameEventIndex: Int = 0,
    var frameMode: Int = 0,
    var frameIrqInhibit: Boolean = false,
    var frameIrqPending: Boolean = false,
    var apuCycle: Boolean = false,
    var samplePhase: Int = 0,
    var filters: DoubleArray = DoubleArray(5),
) {
    override fun equals(other: Any?): Boolean = other is ApuState &&
        pulse1 == other.pulse1 && pulse2 == other.pulse2 && triangle == other.triangle &&
        noise == other.noise && dmc == other.dmc && filters.contentEquals(other.filters)

    override fun hashCode(): Int = filters.contentHashCode()
}

data class PulseState(
    var enabled: Boolean = false,
    var lengthCounter: Int = 0,
    var values: IntArray = IntArray(10),
    var flags: BooleanArray = BooleanArray(6),
) {
    override fun equals(other: Any?): Boolean = other is PulseState &&
        enabled == other.enabled && lengthCounter == other.lengthCounter &&
        values.contentEquals(other.values) && flags.contentEquals(other.flags)

    override fun hashCode(): Int = values.contentHashCode()
}

data class TriangleState(
    var enabled: Boolean = false,
    var lengthCounter: Int = 0,
    var values: IntArray = IntArray(6),
    var flags: BooleanArray = BooleanArray(2),
) {
    override fun equals(other: Any?): Boolean = other is TriangleState &&
        enabled == other.enabled && lengthCounter == other.lengthCounter &&
        values.contentEquals(other.values) && flags.contentEquals(other.flags)

    override fun hashCode(): Int = values.contentHashCode()
}

data class NoiseState(
    var enabled: Boolean = false,
    var lengthCounter: Int = 0,
    var values: IntArray = IntArray(6),
    var flags: BooleanArray = BooleanArray(4),
) {
    override fun equals(other: Any?): Boolean = other is NoiseState &&
        enabled == other.enabled && lengthCounter == other.lengthCounter &&
        values.contentEquals(other.values) && flags.contentEquals(other.flags)

    override fun hashCode(): Int = values.contentHashCode()
}

data class DmcState(
    var values: IntArray = intArrayOf(0, -1, 0, 0xC000, 1, 0xC000, 0, 0, 8, 0),
    var flags: BooleanArray = BooleanArray(7),
) {
    override fun equals(other: Any?): Boolean = other is DmcState &&
        values.contentEquals(other.values) && flags.contentEquals(other.flags)

    override fun hashCode(): Int = values.contentHashCode()
}
