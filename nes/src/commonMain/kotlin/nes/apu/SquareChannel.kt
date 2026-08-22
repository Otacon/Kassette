/*
 * This file is part of Kassette.
 *
 * This Kotlin implementation is ported and adapted from MesenCE
 * (https://github.com/nesdev-org/MesenCE). MesenCE is licensed under
 * the GNU General Public License version 3.
 *
 * This modified Kotlin port is distributed under the GNU General Public
 * License version 3. See the repository LICENSE file for details.
 */

package nes.apu

internal class SquareChannel(private val isChannel1: Boolean, private val apu: NesApu) {
    private val dutySequences = arrayOf(
        intArrayOf(0, 0, 0, 0, 0, 0, 0, 1),
        intArrayOf(0, 0, 0, 0, 0, 0, 1, 1),
        intArrayOf(0, 0, 0, 0, 1, 1, 1, 1),
        intArrayOf(1, 1, 1, 1, 1, 1, 0, 0),
    )
    private val envelope = ApuEnvelope(apu)
    private val timer = ApuTimer(if (isChannel1) ApuAudioChannel.Square1 else ApuAudioChannel.Square2, apu)
    private var duty = 0
    private var dutyPos = 0
    private var sweepEnabled = false
    private var sweepPeriod = 0
    private var sweepNegate = false
    private var sweepShift = 0
    private var reloadSweep = false
    private var sweepDivider = 0
    private var sweepTargetPeriod = 0
    private var realPeriod = 0
    val output: Int get() = timer.lastOutput

    fun writeRam(addr: Int, value: Int) {
        apu.run()
        when (addr and 3) {
            0 -> { envelope.initialize(value); duty = (value and 0xC0) shr 6; if (apu.swapDutyCycles() && duty == 1) duty = 2 else if (apu.swapDutyCycles() && duty == 2) duty = 1 }
            1 -> initializeSweep(value)
            2 -> setPeriod((realPeriod and 0x700) or value)
            3 -> { envelope.lengthCounter.load(value shr 3); setPeriod((realPeriod and 0xFF) or ((value and 7) shl 8)); dutyPos = 0; envelope.resetEnvelope() }
        }
        updateOutput()
    }

    fun run(targetCycle: Int) {
        while (timer.run(targetCycle)) {
            dutyPos = (dutyPos - 1) and 7
            updateOutput()
        }
    }

    fun reset(softReset: Boolean) {
        envelope.reset(softReset)
        timer.reset()
        duty = 0; dutyPos = 0; realPeriod = 0; sweepEnabled = false; sweepPeriod = 0
        sweepNegate = false; sweepShift = 0; reloadSweep = false; sweepDivider = 0; sweepTargetPeriod = 0
    }

    fun tickSweep() {
        sweepDivider--
        if (sweepDivider == 0) {
            if (sweepShift > 0 && sweepEnabled && realPeriod >= 8 && sweepTargetPeriod <= 0x7FF) setPeriod(sweepTargetPeriod)
            sweepDivider = sweepPeriod
        }
        if (reloadSweep) { sweepDivider = sweepPeriod; reloadSweep = false }
    }

    fun tickEnvelope() = envelope.tick()
    fun tickLengthCounter() = envelope.lengthCounter.tick()
    fun reloadLengthCounter() = envelope.lengthCounter.reload()
    fun endFrame() = timer.endFrame()
    fun setEnabled(enabled: Boolean) = envelope.lengthCounter.setEnabled(enabled)
    fun getStatus(): Boolean = envelope.lengthCounter.status()

    fun getState(): ApuSquareState = ApuSquareState(
        Duty = duty,
        DutyPosition = dutyPos,
        Enabled = envelope.lengthCounter.isEnabled(),
        Envelope = envelope.state(),
        Frequency = apu.clockRate() / 16.0 / (realPeriod + 1),
        LengthCounter = envelope.lengthCounter.state(),
        OutputVolume = output,
        Period = realPeriod,
        Timer = timer.timer / 2,
        SweepEnabled = sweepEnabled,
        SweepNegate = sweepNegate,
        SweepPeriod = sweepPeriod,
        SweepShift = sweepShift,
    )

    fun captureSnapshot(): SquareChannelSnapshot = SquareChannelSnapshot(
        Envelope = envelope.captureSnapshot(),
        Timer = timer.captureSnapshot(),
        Duty = duty,
        DutyPos = dutyPos,
        SweepEnabled = sweepEnabled,
        SweepPeriod = sweepPeriod,
        SweepNegate = sweepNegate,
        SweepShift = sweepShift,
        ReloadSweep = reloadSweep,
        SweepDivider = sweepDivider,
        SweepTargetPeriod = sweepTargetPeriod,
        RealPeriod = realPeriod,
    )

    fun restoreSnapshot(snapshot: SquareChannelSnapshot) {
        envelope.restoreSnapshot(snapshot.Envelope)
        timer.restoreSnapshot(snapshot.Timer)
        duty = snapshot.Duty and 3
        dutyPos = snapshot.DutyPos and 7
        sweepEnabled = snapshot.SweepEnabled
        sweepPeriod = snapshot.SweepPeriod and 0xFF
        sweepNegate = snapshot.SweepNegate
        sweepShift = snapshot.SweepShift and 7
        reloadSweep = snapshot.ReloadSweep
        sweepDivider = snapshot.SweepDivider and 0xFF
        sweepTargetPeriod = snapshot.SweepTargetPeriod
        realPeriod = snapshot.RealPeriod and 0x7FF
    }

    private fun initializeSweep(value: Int) {
        sweepEnabled = (value and 0x80) != 0
        sweepNegate = (value and 0x08) != 0
        sweepPeriod = ((value and 0x70) shr 4) + 1
        sweepShift = value and 7
        updateTargetPeriod()
        reloadSweep = true
    }

    private fun setPeriod(value: Int) {
        realPeriod = value and 0x7FF
        timer.period = realPeriod * 2 + 1
        updateTargetPeriod()
    }

    private fun updateTargetPeriod() {
        val shiftResult = realPeriod shr sweepShift
        sweepTargetPeriod = if (sweepNegate) {
            realPeriod - shiftResult - if (isChannel1) 1 else 0
        } else {
            realPeriod + shiftResult
        }
    }

    private fun updateOutput() {
        timer.addOutput(if (realPeriod < 8 || (!sweepNegate && sweepTargetPeriod > 0x7FF)) 0 else dutySequences[duty][dutyPos] * envelope.getVolume())
    }
}
