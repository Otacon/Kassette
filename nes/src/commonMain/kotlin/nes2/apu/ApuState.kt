package nes2.apu

data class ApuState(
    val pulse1: PulseState = PulseState(),
    val pulse2: PulseState = PulseState(),
    val triangle: TriangleState = TriangleState(),
    val noise: NoiseState = NoiseState(),
    val dmc: DmcState = DmcState(),

    var frameCycle: Int = 0,
    var frameStep: Int = 0,
    var frameMode: Int = 0,
    var frameIrqInhibit: Boolean = false,
    var frameIrqPending: Boolean = false,

    var evenCycle: Boolean = false,
    var samplePhase: Int = 0,

    var highPass90Input: Double = 0.0,
    var highPass90Output: Double = 0.0,
    var highPass440Input: Double = 0.0,
    var highPass440Output: Double = 0.0,
    var lowPass14kOutput: Double = 0.0,
)

data class PulseState(
    var enabled: Boolean = false,
    var lengthCounter: Int = 0,
    var duty: Int = 0,
    var timer: Int = 0,
    var timerCounter: Int = 0,
    var sequence: Int = 0,
    var volume: Int = 0,
    var envelopeDivider: Int = 0,
    var envelopeDecay: Int = 0,
    var envelopeLoop: Boolean = false,
    var constantVolume: Boolean = false,
    var envelopeStart: Boolean = false,
    var sweepEnabled: Boolean = false,
    var sweepNegate: Boolean = false,
    var sweepReload: Boolean = false,
    var sweepPeriod: Int = 0,
    var sweepShift: Int = 0,
    var sweepDivider: Int = 0,
)

data class TriangleState(
    var enabled: Boolean = false,
    var lengthCounter: Int = 0,

    var control: Boolean = false,
    var reloadValue: Int = 0,
    var reloadFlag: Boolean = false,
    var linearCounter: Int = 0,

    var timer: Int = 0,
    var timerCounter: Int = 0,
    var sequence: Int = 0,
    var outputLevel: Int = 0,
)

data class NoiseState(
    var enabled: Boolean = false,
    var lengthCounter: Int = 0,

    var envelopeLoop: Boolean = false,
    var constantVolume: Boolean = false,
    var envelopeStart: Boolean = false,
    var volume: Int = 0,
    var envelopeDivider: Int = 0,
    var envelopeDecay: Int = 0,

    var mode: Boolean = false,
    var timer: Int = 0,
    var timerCounter: Int = 0,
    var shiftRegister: Int = 1,
)

data class DmcState(
    var enabled: Boolean = false,

    var irqEnabled: Boolean = false,
    var irqPending: Boolean = false,
    var loop: Boolean = false,

    var period: Int = 0,
    var timerCounter: Int = 0,

    var outputLevel: Int = 0,

    var sampleAddress: Int = 0xC000,
    var sampleLength: Int = 1,

    var currentAddress: Int = 0xC000,
    var bytesRemaining: Int = 0,

    var sampleBuffer: Int = 0,
    var sampleBufferFull: Boolean = false,

    var shiftRegister: Int = 0,
    var bitsRemaining: Int = 8,
    var silence: Boolean = true,
)

internal fun ApuState.reset() {
    pulse1.reset()
    pulse2.reset()
    triangle.reset()
    noise.reset()
    dmc.reset()
    frameCycle = 0
    frameStep = 0
    frameMode = 0
    frameIrqInhibit = false
    frameIrqPending = false
    evenCycle = false
    samplePhase = 0
    highPass90Input = 0.0
    highPass90Output = 0.0
    highPass440Input = 0.0
    highPass440Output = 0.0
    lowPass14kOutput = 0.0
}

internal fun PulseState.reset() {
    enabled = false
    lengthCounter = 0
    duty = 0
    timer = 0
    timerCounter = 0
    sequence = 0
    volume = 0
    envelopeDivider = 0
    envelopeDecay = 0
    envelopeLoop = false
    constantVolume = false
    envelopeStart = false
    sweepEnabled = false
    sweepNegate = false
    sweepReload = false
    sweepPeriod = 0
    sweepShift = 0
    sweepDivider = 0
}

internal fun TriangleState.reset() {
    enabled = false
    lengthCounter = 0
    control = false
    reloadValue = 0
    reloadFlag = false
    linearCounter = 0
    timer = 0
    timerCounter = 0
    sequence = 0
    outputLevel = 0
}

internal fun NoiseState.reset() {
    enabled = false
    lengthCounter = 0
    envelopeLoop = false
    constantVolume = false
    envelopeStart = false
    volume = 0
    envelopeDivider = 0
    envelopeDecay = 0
    mode = false
    timer = 0
    timerCounter = 0
    shiftRegister = 1
}

internal fun DmcState.reset() {
    enabled = false
    irqEnabled = false
    irqPending = false
    loop = false
    period = 0
    timerCounter = 0
    outputLevel = 0
    sampleAddress = 0xC000
    sampleLength = 1
    currentAddress = 0xC000
    bytesRemaining = 0
    sampleBuffer = 0
    sampleBufferFull = false
    shiftRegister = 0
    bitsRemaining = 8
    silence = true
}