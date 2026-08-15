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
    var pendingFrameCounterValue: Int = -1,
    var frameCounterWriteDelay: Int = 0,
    var blockFrameCounterTicks: Int = 0,
    var dmcDma: DmcDmaState = DmcDmaState(),
    var frameIrqFlag: Boolean = false,
    var frameIrqClearDelay: Int = 0,
) {
    override fun equals(other: Any?): Boolean = other is ApuState &&
        pulse1 == other.pulse1 && pulse2 == other.pulse2 && triangle == other.triangle &&
        noise == other.noise && dmc == other.dmc && frameCycle == other.frameCycle &&
        frameEventIndex == other.frameEventIndex && frameMode == other.frameMode &&
        frameIrqInhibit == other.frameIrqInhibit && frameIrqPending == other.frameIrqPending &&
        apuCycle == other.apuCycle && samplePhase == other.samplePhase &&
        pendingFrameCounterValue == other.pendingFrameCounterValue &&
        frameCounterWriteDelay == other.frameCounterWriteDelay &&
        blockFrameCounterTicks == other.blockFrameCounterTicks && dmcDma == other.dmcDma &&
        frameIrqFlag == other.frameIrqFlag && frameIrqClearDelay == other.frameIrqClearDelay &&
        filters.contentEquals(other.filters)

    override fun hashCode(): Int {
        var result = pulse1.hashCode()
        result = 31 * result + pulse2.hashCode()
        result = 31 * result + triangle.hashCode()
        result = 31 * result + noise.hashCode()
        result = 31 * result + dmc.hashCode()
        result = 31 * result + frameCycle
        result = 31 * result + frameEventIndex
        result = 31 * result + frameMode
        result = 31 * result + frameIrqInhibit.hashCode()
        result = 31 * result + frameIrqPending.hashCode()
        result = 31 * result + apuCycle.hashCode()
        result = 31 * result + samplePhase
        result = 31 * result + filters.contentHashCode()
        result = 31 * result + pendingFrameCounterValue
        result = 31 * result + frameCounterWriteDelay
        result = 31 * result + blockFrameCounterTicks
        result = 31 * result + dmcDma.hashCode()
        result = 31 * result + frameIrqFlag.hashCode()
        result = 31 * result + frameIrqClearDelay
        return result
    }
}

data class PulseState(
    var enabled: Boolean = false,
    var lengthCounter: Int = 0,
    var values: IntArray = IntArray(12),
    var flags: BooleanArray = BooleanArray(8),
) {
    override fun equals(other: Any?): Boolean = other is PulseState &&
        enabled == other.enabled && lengthCounter == other.lengthCounter &&
        values.contentEquals(other.values) && flags.contentEquals(other.flags)

    override fun hashCode(): Int = 31 * (31 * enabled.hashCode() + lengthCounter) +
        31 * values.contentHashCode() + flags.contentHashCode()
}

data class TriangleState(
    var enabled: Boolean = false,
    var lengthCounter: Int = 0,
    var values: IntArray = IntArray(8),
    var flags: BooleanArray = BooleanArray(4),
) {
    override fun equals(other: Any?): Boolean = other is TriangleState &&
        enabled == other.enabled && lengthCounter == other.lengthCounter &&
        values.contentEquals(other.values) && flags.contentEquals(other.flags)

    override fun hashCode(): Int = 31 * (31 * enabled.hashCode() + lengthCounter) +
        31 * values.contentHashCode() + flags.contentHashCode()
}

data class NoiseState(
    var enabled: Boolean = false,
    var lengthCounter: Int = 0,
    var values: IntArray = intArrayOf(0, 0, 0, 4, 0, 1, 0, 0),
    var flags: BooleanArray = BooleanArray(6),
) {
    override fun equals(other: Any?): Boolean = other is NoiseState &&
        enabled == other.enabled && lengthCounter == other.lengthCounter &&
        values.contentEquals(other.values) && flags.contentEquals(other.flags)

    override fun hashCode(): Int = 31 * (31 * enabled.hashCode() + lengthCounter) +
        31 * values.contentHashCode() + flags.contentHashCode()
}

data class DmcState(
    var values: IntArray = intArrayOf(428, 427, 0, 0xC000, 1, 0xC000, 0, 0, 8, 0, 0, 0),
    var flags: BooleanArray = BooleanArray(7),
) {
    override fun equals(other: Any?): Boolean = other is DmcState &&
        values.contentEquals(other.values) && flags.contentEquals(other.flags)

    override fun hashCode(): Int = 31 * values.contentHashCode() + flags.contentHashCode()
}

data class DmcDmaState(
    var address: Int = -1,
    var result: Int = -1,
    var phase: Int = 0,
)
