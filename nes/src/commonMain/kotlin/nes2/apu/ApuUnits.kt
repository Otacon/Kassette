package nes2.apu

internal val LENGTH_LOOKUP = intArrayOf(
    10, 254, 20, 2, 40, 4, 80, 6, 160, 8, 60, 10, 14, 12, 26, 14,
    12, 16, 24, 18, 48, 20, 96, 22, 192, 24, 72, 26, 16, 28, 32, 30,
)

internal enum class ApuAudioChannel { Square1, Square2, Triangle, Noise, Dmc }

internal class ApuTimer(private val channel: ApuAudioChannel, private val mixer: NesApu) {
    var previousCycle = 0
    var timer = 0
    var period = 0
    var lastOutput = 0

    fun reset() {
        previousCycle = 0
        timer = 0
        period = 0
        lastOutput = 0
    }

    fun addOutput(output: Int) {
        val newOutput = output and 0xFF
        if (newOutput != lastOutput) {
            mixer.addDelta(channel, previousCycle, newOutput - lastOutput)
            lastOutput = newOutput
        }
    }

    fun run(targetCycle: Int): Boolean {
        val cyclesToRun = targetCycle - previousCycle
        return if (cyclesToRun > timer) {
            previousCycle += timer + 1
            timer = period
            true
        } else {
            timer -= cyclesToRun
            previousCycle = targetCycle
            false
        }
    }

    fun endFrame() {
        previousCycle = 0
    }

    fun captureSnapshot(): ApuTimerSnapshot = ApuTimerSnapshot(previousCycle, timer, period, lastOutput)

    fun restoreSnapshot(snapshot: ApuTimerSnapshot) {
        previousCycle = snapshot.PreviousCycle
        timer = snapshot.Timer
        period = snapshot.Period
        lastOutput = snapshot.LastOutput and 0xFF
    }
}

internal class ApuLengthCounter(private val apu: NesApu, private val triangle: Boolean = false) {
    private var enabled = false
    private var halt = false
    private var counter = 0
    private var reloadValue = 0
    private var previousValue = 0
    private var newHaltValue = false

    fun initialize(haltFlag: Boolean) {
        apu.setNeedToRun()
        newHaltValue = haltFlag
    }

    fun load(value: Int) {
        if (enabled) {
            reloadValue = LENGTH_LOOKUP[value and 0x1F]
            previousValue = counter
            apu.setNeedToRun()
        }
    }

    fun reset(softReset: Boolean) {
        enabled = false
        if (!softReset || !triangle) {
            halt = false
            counter = 0
            reloadValue = 0
            previousValue = 0
            newHaltValue = false
        }
    }

    fun reload() {
        if (reloadValue != 0) {
            if (counter == previousValue) counter = reloadValue
            reloadValue = 0
        }
        halt = newHaltValue
    }

    fun tick() {
        if (counter > 0 && !halt) counter--
    }

    fun setEnabled(value: Boolean) {
        if (!value) counter = 0
        enabled = value
    }

    fun status(): Boolean = counter > 0
    fun halted(): Boolean = halt
    fun isEnabled(): Boolean = enabled
    fun state(): ApuLengthCounterState = ApuLengthCounterState(counter, halt, reloadValue)
    fun captureSnapshot(): ApuLengthCounterSnapshot = ApuLengthCounterSnapshot(enabled, halt, counter, reloadValue, previousValue, newHaltValue)

    fun restoreSnapshot(snapshot: ApuLengthCounterSnapshot) {
        enabled = snapshot.Enabled
        halt = snapshot.Halt
        counter = snapshot.Counter and 0xFF
        reloadValue = snapshot.ReloadValue and 0xFF
        previousValue = snapshot.PreviousValue and 0xFF
        newHaltValue = snapshot.NewHaltValue
    }
}

internal class ApuEnvelope(private val apu: NesApu, triangle: Boolean = false) {
    val lengthCounter = ApuLengthCounter(apu, triangle)
    private var constantVolume = false
    private var volume = 0
    private var start = false
    private var divider = 0
    private var counter = 0

    fun initialize(value: Int) {
        lengthCounter.initialize((value and 0x20) != 0)
        constantVolume = (value and 0x10) != 0
        volume = value and 0x0F
    }

    fun resetEnvelope() {
        start = true
    }

    fun getVolume(): Int = if (lengthCounter.status()) if (constantVolume) volume else counter else 0

    fun reset(softReset: Boolean) {
        lengthCounter.reset(softReset)
        constantVolume = false
        volume = 0
        start = false
        divider = 0
        counter = 0
    }

    fun tick() {
        if (!start) {
            divider--
            if (divider < 0) {
                divider = volume
                if (counter > 0) counter-- else if (lengthCounter.halted()) counter = 15
            }
        } else {
            start = false
            counter = 15
            divider = volume
        }
    }

    fun state(): ApuEnvelopeState = ApuEnvelopeState(constantVolume, counter, divider, lengthCounter.halted(), start, volume)
    fun captureSnapshot(): ApuEnvelopeSnapshot = ApuEnvelopeSnapshot(lengthCounter.captureSnapshot(), constantVolume, volume, start, divider, counter)

    fun restoreSnapshot(snapshot: ApuEnvelopeSnapshot) {
        lengthCounter.restoreSnapshot(snapshot.LengthCounter)
        constantVolume = snapshot.ConstantVolume
        volume = snapshot.Volume and 0x0F
        start = snapshot.Start
        divider = snapshot.Divider
        counter = snapshot.Counter and 0x0F
    }
}
