package nes2.apu

import nes2.cpu.ConsoleRegion

internal class NoiseChannel(private val apu: NesApu) {
    private val ntsc = intArrayOf(4, 8, 16, 32, 64, 96, 128, 160, 202, 254, 380, 508, 762, 1016, 2034, 4068)
    private val pal = intArrayOf(4, 8, 14, 30, 60, 88, 118, 148, 188, 236, 354, 472, 708, 944, 1890, 3778)
    private val envelope = ApuEnvelope(apu)
    private val timer = ApuTimer()
    private var periods = ntsc
    private var shiftRegister = 1
    private var modeFlag = false
    val output: Int get() = timer.lastOutput

    fun setRegion(region: ConsoleRegion) { periods = if (region == ConsoleRegion.Pal) pal else ntsc; timer.period = periods[0] - 1 }
    fun writeRam(addr: Int, value: Int) { apu.run(); when (addr and 3) { 0 -> envelope.initialize(value); 2 -> { timer.period = periods[value and 0x0F] - 1; modeFlag = (value and 0x80) != 0 }; 3 -> { envelope.lengthCounter.load(value shr 3); envelope.resetEnvelope() } } }
    fun run(targetCycle: Int) { while (timer.run(targetCycle)) { val feedback = (shiftRegister and 1) xor ((shiftRegister shr if (modeFlag) 6 else 1) and 1); shiftRegister = (shiftRegister shr 1) or (feedback shl 14); timer.addOutput(if ((shiftRegister and 1) != 0) 0 else envelope.getVolume()) } }
    fun reset(softReset: Boolean) { envelope.reset(softReset); timer.reset(); timer.period = periods[0] - 1; shiftRegister = 1; modeFlag = false }
    fun tickEnvelope() = envelope.tick()
    fun tickLengthCounter() = envelope.lengthCounter.tick()
    fun reloadLengthCounter() = envelope.lengthCounter.reload()
    fun endFrame() = timer.endFrame()
    fun setEnabled(enabled: Boolean) = envelope.lengthCounter.setEnabled(enabled)
    fun getStatus(): Boolean = envelope.lengthCounter.status()
    fun getState(): ApuNoiseState = ApuNoiseState(envelope.lengthCounter.isEnabled(), envelope.state(), apu.clockRate().toDouble() / (timer.period + 1) / if (modeFlag) 93.0 else 1.0, envelope.lengthCounter.state(), modeFlag, output, timer.period, timer.timer, shiftRegister)
    fun captureSnapshot(): NoiseChannelSnapshot = NoiseChannelSnapshot(envelope.captureSnapshot(), timer.captureSnapshot(), shiftRegister, modeFlag)

    fun restoreSnapshot(snapshot: NoiseChannelSnapshot) {
        envelope.restoreSnapshot(snapshot.Envelope)
        timer.restoreSnapshot(snapshot.Timer)
        shiftRegister = snapshot.ShiftRegister and 0x7FFF
        modeFlag = snapshot.ModeFlag
    }
}
