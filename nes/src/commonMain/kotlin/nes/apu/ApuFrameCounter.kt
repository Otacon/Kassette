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

import nes.cpu.ConsoleRegion
import nes.cpu.IRQSource

internal class ApuFrameCounter(private val apu: NesApu) {
    private val ntsc = arrayOf(intArrayOf(7457, 14913, 22371, 29828, 29829, 29830), intArrayOf(7457, 14913, 22371, 29829, 37281, 37282))
    private val pal = arrayOf(intArrayOf(8313, 16627, 24939, 33252, 33253, 33254), intArrayOf(8313, 16627, 24939, 33253, 41565, 41566))
    private val frameTypes = arrayOf(arrayOf(FrameType.QuarterFrame, FrameType.HalfFrame, FrameType.QuarterFrame, FrameType.None, FrameType.HalfFrame, FrameType.None), arrayOf(FrameType.QuarterFrame, FrameType.HalfFrame, FrameType.QuarterFrame, FrameType.None, FrameType.HalfFrame, FrameType.None))
    private var steps = ntsc
    private var previousCycle = 0
    private var currentStep = 0
    private var stepMode = 0
    private var inhibitIrq = false
    private var blockFrameCounterTick = 0
    private var newValue = 0
    private var writeDelayCounter = 3
    private var irqFlag = false
    private var irqFlagClearClock = 0L

    fun reset(softReset: Boolean) { previousCycle = 0; irqFlag = false; irqFlagClearClock = 0; if (!softReset) stepMode = 0; currentStep = 0; newValue = if (stepMode != 0) 0x80 else 0; writeDelayCounter = 3; inhibitIrq = false; blockFrameCounterTick = 0 }
    fun setRegion(region: ConsoleRegion) { steps = if (region == ConsoleRegion.Pal) pal else ntsc }
    fun run(cyclesToRunInput: Int): Int { var cyclesToRun = cyclesToRunInput; val cyclesRan: Int; if (previousCycle + cyclesToRun >= steps[stepMode][currentStep]) { if (stepMode == 0 && currentStep >= 3) { irqFlag = true; irqFlagClearClock = 0; if (!inhibitIrq) apu.setIrq(IRQSource.FrameCounter) else if (currentStep == 5) irqFlag = false }; val type = frameTypes[stepMode][currentStep]; if (type != FrameType.None && blockFrameCounterTick == 0) { apu.frameCounterTick(type); blockFrameCounterTick = 2 }; cyclesRan = if (steps[stepMode][currentStep] < previousCycle) 0 else steps[stepMode][currentStep] - previousCycle; cyclesToRun -= cyclesRan; currentStep++; if (currentStep == 6) { currentStep = 0; previousCycle = 0 } else previousCycle += cyclesRan } else { cyclesRan = cyclesToRun; cyclesToRun = 0; previousCycle += cyclesRan }; if (newValue >= 0) { writeDelayCounter--; if (writeDelayCounter == 0) { stepMode = if ((newValue and 0x80) != 0) 1 else 0; writeDelayCounter = -1; currentStep = 0; previousCycle = 0; newValue = -1; if (stepMode != 0 && blockFrameCounterTick == 0) { apu.frameCounterTick(FrameType.HalfFrame); blockFrameCounterTick = 2 } } }; if (blockFrameCounterTick > 0) blockFrameCounterTick--; return cyclesRan }
    fun needToRun(cyclesToRun: Int): Boolean = newValue >= 0 || blockFrameCounterTick > 0 || previousCycle + cyclesToRun >= steps[stepMode][currentStep] - 1
    fun writeRam(value: Int) { apu.run(); newValue = value; writeDelayCounter = if ((apu.cpuCycleCount() and 1L) != 0L) 4 else 3; inhibitIrq = (value and 0x40) != 0; if (inhibitIrq) { apu.clearIrq(IRQSource.FrameCounter); irqFlag = false; irqFlagClearClock = 0 } }
    fun getIrqFlag(): Boolean { if (irqFlag) { val clock = apu.masterClock(); if (irqFlagClearClock == 0L) irqFlagClearClock = clock + if ((clock and 1L) != 0L) 2 else 1 else if (clock >= irqFlagClearClock) { irqFlagClearClock = 0; irqFlag = false } }; return irqFlag }
    fun peekIrqFlag(): Boolean = irqFlag && (irqFlagClearClock == 0L || apu.masterClock() < irqFlagClearClock)
    fun getState(): ApuFrameCounterState = ApuFrameCounterState(!inhibitIrq, minOf(currentStep, if (stepMode != 0) 5 else 4), stepMode == 1)
    fun captureSnapshot(): ApuFrameCounterSnapshot = ApuFrameCounterSnapshot(previousCycle, currentStep, stepMode, inhibitIrq, blockFrameCounterTick, newValue, writeDelayCounter, irqFlag, irqFlagClearClock)

    fun restoreSnapshot(snapshot: ApuFrameCounterSnapshot) {
        previousCycle = snapshot.PreviousCycle
        currentStep = snapshot.CurrentStep.coerceIn(0, 5)
        stepMode = snapshot.StepMode and 1
        inhibitIrq = snapshot.InhibitIrq
        blockFrameCounterTick = snapshot.BlockFrameCounterTick and 0xFF
        newValue = snapshot.NewValue
        writeDelayCounter = snapshot.WriteDelayCounter
        irqFlag = snapshot.IrqFlag
        irqFlagClearClock = snapshot.IrqFlagClearClock
    }
}
