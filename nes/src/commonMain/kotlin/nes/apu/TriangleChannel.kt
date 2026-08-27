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

internal class TriangleChannel(private val apu: NesApu) {
    private val sequence = intArrayOf(15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15)
    private val lengthCounter = ApuLengthCounter(apu, triangle = true)
    private val timer = ApuTimer(ApuAudioChannel.Triangle, apu)
    private var linearCounter = 0
    private var linearCounterReload = 0
    private var linearReloadFlag = false
    private var linearControlFlag = false
    private var sequencePosition = 0
    val output: Int get() = timer.lastOutput

    fun writeRam(addr: Int, value: Int) {
        apu.run()
        when (addr and 3) {
            0 -> { linearControlFlag = (value and 0x80) != 0; linearCounterReload = value and 0x7F; lengthCounter.initialize(linearControlFlag) }
            2 -> timer.period = (timer.period and 0x700) or value
            3 -> { lengthCounter.load(value shr 3); timer.period = (timer.period and 0xFF) or ((value and 7) shl 8); linearReloadFlag = true }
        }
    }

    fun run(targetCycle: Int) {
        while (timer.run(targetCycle)) {
            if (lengthCounter.status() && linearCounter > 0) {
                sequencePosition = (sequencePosition + 1) and 0x1F
                if (!apu.silenceTriangleHighFrequency() || timer.period >= 2) {
                    timer.addOutput(sequence[sequencePosition])
                }
            }
        }
    }

    fun reset(softReset: Boolean) { timer.reset(); lengthCounter.reset(softReset); linearCounter = 0; linearCounterReload = 0; linearReloadFlag = false; linearControlFlag = false; sequencePosition = 0 }
    fun tickLinearCounter() { if (linearReloadFlag) linearCounter = linearCounterReload else if (linearCounter > 0) linearCounter--; if (!linearControlFlag) linearReloadFlag = false }
    fun tickLengthCounter() = lengthCounter.tick()
    fun reloadLengthCounter() = lengthCounter.reload()
    fun endFrame() = timer.endFrame()
    fun setEnabled(enabled: Boolean) = lengthCounter.setEnabled(enabled)
    fun getStatus(): Boolean = lengthCounter.status()
    fun getState(): ApuTriangleState = ApuTriangleState(lengthCounter.isEnabled(), apu.clockRate() / 32.0 / (timer.period + 1), lengthCounter.state(), output, timer.period, timer.timer, sequencePosition, linearCounterReload, linearCounter, linearReloadFlag)
    fun captureSnapshot(): TriangleChannelSnapshot = TriangleChannelSnapshot(lengthCounter.captureSnapshot(), timer.captureSnapshot(), linearCounter, linearCounterReload, linearReloadFlag, linearControlFlag, sequencePosition)

    fun restoreSnapshot(snapshot: TriangleChannelSnapshot) {
        lengthCounter.restoreSnapshot(snapshot.LengthCounter)
        timer.restoreSnapshot(snapshot.Timer)
        linearCounter = snapshot.LinearCounter and 0xFF
        linearCounterReload = snapshot.LinearCounterReload and 0x7F
        linearReloadFlag = snapshot.LinearReloadFlag
        linearControlFlag = snapshot.LinearControlFlag
        sequencePosition = snapshot.SequencePosition and 0x1F
    }
}
