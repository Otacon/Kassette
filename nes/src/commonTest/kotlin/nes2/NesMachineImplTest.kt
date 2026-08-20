package nes2

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import nes2.fakes.FakeCpu
import nes2.fakes.FakeOamDma
import nes2.fakes.FakePpu

class NesMachineImplTest : FreeSpec({

    lateinit var cpu: FakeCpu
    lateinit var ppu: FakePpu
    lateinit var dma: FakeOamDma
    lateinit var machine: NesMachineImpl

    beforeTest {
        cpu = FakeCpu()
        ppu = FakePpu()
        dma = FakeOamDma()
        machine = NesMachineImpl(cpu, ppu, dma)
    }

    "reset advances PPU by CPU reset timing" {
        cpu.resetCycles = 7

        machine.reset()

        cpu.resets shouldBe 1
        ppu.ticks shouldBe 21
    }

    "DMA after reset uses odd CPU cycle parity" {
        cpu.resetCycles = 7

        machine.reset()
        ppu.ticksUntilNextFrame = ppu.ticks + (514 * 3)
        dma.start(0x02)

        machine.runUntilFrame()

        dma.transfers shouldBe 1
        cpu.steps shouldBe 0
        ppu.ticks shouldBe 21 + (514 * 3)
    }

    "runs CPU steps and advances PPU until next frame" {
        cpu.cycles = 4
        ppu.ticksUntilNextFrame = 24

        machine.runUntilFrame()

        cpu.steps shouldBe 2
        ppu.ticks shouldBe 24
        ppu.frame shouldBe 1
    }

    "runs until the next frame" {
        cpu.cycles = 1
        ppu.ticksUntilNextFrame = 9

        machine.runUntilFrame()

        cpu.steps shouldBe 3
        ppu.ticks shouldBe 9
        ppu.frame shouldBe 1
    }

    "runs CPU and PPU until next frame" {
        cpu.cycles = 4
        ppu.ticksUntilNextFrame = 24

        machine.runUntilFrame()

        cpu.steps shouldBe 2
        ppu.ticks shouldBe 24
    }

    "stalls CPU for 513 cycles when DMA starts on an even CPU cycle" {
        dma.start(0x02)
        ppu.ticksUntilNextFrame = 513 * 3

        machine.runUntilFrame()

        dma.transfers shouldBe 1
        cpu.steps shouldBe 0
        ppu.ticks shouldBe 1539
    }

    "stalls CPU for 514 cycles when DMA starts on an odd CPU cycle" {
        cpu.cycles = 3
        ppu.ticksUntilNextFrame = 9

        machine.runUntilFrame()

        val ticksBeforeDma = ppu.ticks

        ppu.ticksUntilNextFrame = ticksBeforeDma + (514 * 3)
        dma.start(0x02)

        machine.runUntilFrame()

        dma.transfers shouldBe 1
        cpu.steps shouldBe 1
        (ppu.ticks - ticksBeforeDma) shouldBe 1542
    }
})
