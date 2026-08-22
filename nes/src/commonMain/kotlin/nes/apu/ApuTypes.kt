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

data class ApuState(
    var Square1: ApuSquareState = ApuSquareState(),
    var Square2: ApuSquareState = ApuSquareState(),
    var Triangle: ApuTriangleState = ApuTriangleState(),
    var Noise: ApuNoiseState = ApuNoiseState(),
    var Dmc: ApuDmcState = ApuDmcState(),
    var FrameCounter: ApuFrameCounterState = ApuFrameCounterState(),
)

data class ApuLengthCounterState(
    var Counter: Int = 0,
    var Halt: Boolean = false,
    var ReloadValue: Int = 0,
)

data class ApuEnvelopeState(
    var ConstantVolume: Boolean = false,
    var Counter: Int = 0,
    var Divider: Int = 0,
    var Loop: Boolean = false,
    var StartFlag: Boolean = false,
    var Volume: Int = 0,
)

data class ApuSquareState(
    var Duty: Int = 0,
    var DutyPosition: Int = 0,
    var Enabled: Boolean = false,
    var Envelope: ApuEnvelopeState = ApuEnvelopeState(),
    var Frequency: Double = 0.0,
    var LengthCounter: ApuLengthCounterState = ApuLengthCounterState(),
    var OutputVolume: Int = 0,
    var Period: Int = 0,
    var Timer: Int = 0,
    var SweepEnabled: Boolean = false,
    var SweepNegate: Boolean = false,
    var SweepPeriod: Int = 0,
    var SweepShift: Int = 0,
)

data class ApuTriangleState(
    var Enabled: Boolean = false,
    var Frequency: Double = 0.0,
    var LengthCounter: ApuLengthCounterState = ApuLengthCounterState(),
    var OutputVolume: Int = 0,
    var Period: Int = 0,
    var Timer: Int = 0,
    var SequencePosition: Int = 0,
    var LinearCounterReload: Int = 0,
    var LinearCounter: Int = 0,
    var LinearReloadFlag: Boolean = false,
)

data class ApuNoiseState(
    var Enabled: Boolean = false,
    var Envelope: ApuEnvelopeState = ApuEnvelopeState(),
    var Frequency: Double = 0.0,
    var LengthCounter: ApuLengthCounterState = ApuLengthCounterState(),
    var ModeFlag: Boolean = false,
    var OutputVolume: Int = 0,
    var Period: Int = 0,
    var Timer: Int = 0,
    var ShiftRegister: Int = 1,
)

data class ApuDmcState(
    var BytesRemaining: Int = 0,
    var IrqEnabled: Boolean = false,
    var Loop: Boolean = false,
    var OutputVolume: Int = 0,
    var Period: Int = 0,
    var Timer: Int = 0,
    var SampleRate: Double = 0.0,
    var SampleAddr: Int = 0xC000,
    var NextSampleAddr: Int = 0,
    var SampleLength: Int = 1,
)

data class ApuFrameCounterState(
    var IrqEnabled: Boolean = true,
    var SequencePosition: Int = 0,
    var FiveStepMode: Boolean = false,
)

enum class FrameType {
    None,
    QuarterFrame,
    HalfFrame,
}
