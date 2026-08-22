package nes2.apu

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import nes.ConsoleRegion

class ApuNesTest : FreeSpec({

    lateinit var apu: ApuNes
    lateinit var state: ApuState

    beforeTest {
        state = ApuState()
        apu = ApuNes(state)
    }

    "reset restores APU state" {
        dirty(state)

        apu.reset()

        state shouldBe ApuState()
    }

    "reset clears pending IRQs" {
        state.frameIrqPending = true
        state.dmc.irqPending = true

        apu.reset()

        apu.irqPending shouldBe false
    }

    "reset clears generated samples" {
        dirty(state)
        apu.beginFrame()

        apu.reset()

        apu.sampleCount shouldBe 0
    }

    "reset keeps the preallocated sample buffer" {
        val samples = apu.samples

        apu.reset()

        apu.samples shouldBeSameInstanceAs samples
    }

    "reads channel status from 0x4015" {
        state.pulse1.lengthCounter = 1
        state.pulse2.lengthCounter = 1
        state.triangle.lengthCounter = 1
        state.noise.lengthCounter = 1
        state.dmc.bytesRemaining = 1

        apu.read(0x4015) shouldBe 0x1F
    }

    "status read clears frame IRQ but keeps DMC IRQ" {
        state.frameIrqPending = true
        state.dmc.irqPending = true

        apu.read(0x4015) shouldBe 0xC0

        state.frameIrqPending shouldBe false
        state.dmc.irqPending shouldBe true
        apu.irqPending shouldBe true
    }

    "writes channel enables from 0x4015" {
        apu.write(0x4015, 0x1F)

        state.pulse1.enabled shouldBe true
        state.pulse2.enabled shouldBe true
        state.triangle.enabled shouldBe true
        state.noise.enabled shouldBe true
        state.dmc.enabled shouldBe true
    }

    "disabling channels clears length counters and DMC bytes remaining" {
        state.pulse1.lengthCounter = 1
        state.pulse2.lengthCounter = 2
        state.triangle.lengthCounter = 3
        state.noise.lengthCounter = 4
        state.dmc.bytesRemaining = 5

        apu.write(0x4015, 0x00)

        state.pulse1.lengthCounter shouldBe 0
        state.pulse2.lengthCounter shouldBe 0
        state.triangle.lengthCounter shouldBe 0
        state.noise.lengthCounter shouldBe 0
        state.dmc.bytesRemaining shouldBe 0
    }

    "status write clears DMC IRQ" {
        state.dmc.irqPending = true

        apu.write(0x4015, 0x10)

        state.dmc.irqPending shouldBe false
    }

    "four-step frame counter raises IRQ" {
        apu.tick(29_829)

        state.frameIrqPending shouldBe true
        apu.irqPending shouldBe true
    }

    "frame counter write resets timing" {
        apu.tick(10)

        apu.write(0x4017, 0x00)

        state.frameCycle shouldBe 0
        state.frameStep shouldBe 0
    }

    "frame IRQ inhibit clears pending IRQ" {
        state.frameIrqPending = true

        apu.write(0x4017, 0x40)

        state.frameIrqInhibit shouldBe true
        state.frameIrqPending shouldBe false
        apu.irqPending shouldBe false
    }

    "five-step frame counter does not raise IRQ" {
        apu.write(0x4017, 0x80)

        apu.tick(37_282)

        state.frameMode shouldBe 1
        state.frameIrqPending shouldBe false
    }

    "configure timing changes frame counter events" {
        apu.configureTiming(
            apuFourStepEvents = ConsoleRegion.PAL.timing.apuFourStepEvents,
            apuFiveStepEvents = ConsoleRegion.PAL.timing.apuFiveStepEvents,
        )

        apu.tick(29_829)
        state.frameIrqPending shouldBe false

        apu.tick(3_424)
        state.frameIrqPending shouldBe true
    }
})

private fun dirty(state: ApuState) {
    dirty(state.pulse1)
    dirty(state.pulse2)
    dirty(state.triangle)
    dirty(state.noise)
    dirty(state.dmc)

    state.frameCycle = 1
    state.frameStep = 2
    state.frameMode = 1
    state.frameIrqInhibit = true
    state.frameIrqPending = true
    state.evenCycle = true
    state.samplePhase = 3
    state.highPass90Input = 1.0
    state.highPass90Output = 2.0
    state.highPass440Input = 3.0
    state.highPass440Output = 4.0
    state.lowPass14kOutput = 5.0
}

private fun dirty(pulse: PulseState) {
    pulse.enabled = true
    pulse.lengthCounter = 1
    pulse.duty = 2
    pulse.timer = 3
    pulse.timerCounter = 4
    pulse.sequence = 5
    pulse.volume = 6
    pulse.envelopeDivider = 7
    pulse.envelopeDecay = 8
    pulse.envelopeLoop = true
    pulse.constantVolume = true
    pulse.envelopeStart = true
    pulse.sweepEnabled = true
    pulse.sweepNegate = true
    pulse.sweepReload = true
    pulse.sweepPeriod = 9
    pulse.sweepShift = 10
    pulse.sweepDivider = 11
}

private fun dirty(triangle: TriangleState) {
    triangle.enabled = true
    triangle.lengthCounter = 1
    triangle.control = true
    triangle.reloadValue = 2
    triangle.reloadFlag = true
    triangle.linearCounter = 3
    triangle.timer = 4
    triangle.timerCounter = 5
    triangle.sequence = 6
    triangle.outputLevel = 7
}

private fun dirty(noise: NoiseState) {
    noise.enabled = true
    noise.lengthCounter = 1
    noise.envelopeLoop = true
    noise.constantVolume = true
    noise.envelopeStart = true
    noise.volume = 2
    noise.envelopeDivider = 3
    noise.envelopeDecay = 4
    noise.mode = true
    noise.timer = 5
    noise.timerCounter = 6
    noise.shiftRegister = 7
}

private fun dirty(dmc: DmcState) {
    dmc.enabled = true
    dmc.irqEnabled = true
    dmc.irqPending = true
    dmc.loop = true
    dmc.period = 1
    dmc.timerCounter = 2
    dmc.outputLevel = 3
    dmc.sampleAddress = 0xC040
    dmc.sampleLength = 4
    dmc.currentAddress = 0xC080
    dmc.bytesRemaining = 5
    dmc.sampleBuffer = 6
    dmc.sampleBufferFull = true
    dmc.shiftRegister = 7
    dmc.bitsRemaining = 1
    dmc.silence = false
}
