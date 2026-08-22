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

import kotlinx.serialization.Serializable

@Serializable
data class NesApuSnapshot(
    val ApuEnabled: Boolean = true,
    val NeedToRun: Boolean = false,
    val PreviousCycle: Int = 0,
    val CurrentCycle: Int = 0,
    val ApuDisabledStamp: Long = 0,
    val Square1: SquareChannelSnapshot = SquareChannelSnapshot(),
    val Square2: SquareChannelSnapshot = SquareChannelSnapshot(),
    val Triangle: TriangleChannelSnapshot = TriangleChannelSnapshot(),
    val Noise: NoiseChannelSnapshot = NoiseChannelSnapshot(),
    val Dmc: DeltaModulationChannelSnapshot = DeltaModulationChannelSnapshot(),
    val FrameCounter: ApuFrameCounterSnapshot = ApuFrameCounterSnapshot(),
)

@Serializable
data class ApuTimerSnapshot(
    val PreviousCycle: Int = 0,
    val Timer: Int = 0,
    val Period: Int = 0,
    val LastOutput: Int = 0,
)

@Serializable
data class ApuLengthCounterSnapshot(
    val Enabled: Boolean = false,
    val Halt: Boolean = false,
    val Counter: Int = 0,
    val ReloadValue: Int = 0,
    val PreviousValue: Int = 0,
    val NewHaltValue: Boolean = false,
)

@Serializable
data class ApuEnvelopeSnapshot(
    val LengthCounter: ApuLengthCounterSnapshot = ApuLengthCounterSnapshot(),
    val ConstantVolume: Boolean = false,
    val Volume: Int = 0,
    val Start: Boolean = false,
    val Divider: Int = 0,
    val Counter: Int = 0,
)

@Serializable
data class SquareChannelSnapshot(
    val Envelope: ApuEnvelopeSnapshot = ApuEnvelopeSnapshot(),
    val Timer: ApuTimerSnapshot = ApuTimerSnapshot(),
    val Duty: Int = 0,
    val DutyPos: Int = 0,
    val SweepEnabled: Boolean = false,
    val SweepPeriod: Int = 0,
    val SweepNegate: Boolean = false,
    val SweepShift: Int = 0,
    val ReloadSweep: Boolean = false,
    val SweepDivider: Int = 0,
    val SweepTargetPeriod: Int = 0,
    val RealPeriod: Int = 0,
)

@Serializable
data class TriangleChannelSnapshot(
    val LengthCounter: ApuLengthCounterSnapshot = ApuLengthCounterSnapshot(),
    val Timer: ApuTimerSnapshot = ApuTimerSnapshot(),
    val LinearCounter: Int = 0,
    val LinearCounterReload: Int = 0,
    val LinearReloadFlag: Boolean = false,
    val LinearControlFlag: Boolean = false,
    val SequencePosition: Int = 0,
)

@Serializable
data class NoiseChannelSnapshot(
    val Envelope: ApuEnvelopeSnapshot = ApuEnvelopeSnapshot(),
    val Timer: ApuTimerSnapshot = ApuTimerSnapshot(),
    val ShiftRegister: Int = 1,
    val ModeFlag: Boolean = false,
)

@Serializable
data class DeltaModulationChannelSnapshot(
    val Timer: ApuTimerSnapshot = ApuTimerSnapshot(),
    val SampleAddr: Int = 0xC000,
    val SampleLength: Int = 1,
    val OutputLevel: Int = 0,
    val IrqEnabled: Boolean = false,
    val LoopFlag: Boolean = false,
    val CurrentAddr: Int = 0,
    val BytesRemaining: Int = 0,
    val ReadBuffer: Int = 0,
    val BufferEmpty: Boolean = true,
    val ShiftRegister: Int = 0,
    val BitsRemaining: Int = 8,
    val SilenceFlag: Boolean = true,
    val NeedToRun: Boolean = false,
    val DisableDelay: Int = 0,
    val TransferStartDelay: Int = 0,
    val LastValue4011: Int = 0,
)

@Serializable
data class ApuFrameCounterSnapshot(
    val PreviousCycle: Int = 0,
    val CurrentStep: Int = 0,
    val StepMode: Int = 0,
    val InhibitIrq: Boolean = false,
    val BlockFrameCounterTick: Int = 0,
    val NewValue: Int = 0,
    val WriteDelayCounter: Int = 3,
    val IrqFlag: Boolean = false,
    val IrqFlagClearClock: Long = 0,
)
