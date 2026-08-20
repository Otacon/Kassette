package nes2

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import nes2.fakes.FakeCpu
import nes2.fakes.FakePpu

class NesMachineImplTest : FreeSpec({

    lateinit var cpu: FakeCpu
    lateinit var ppu: FakePpu
    lateinit var machine: NesMachineImpl

    beforeTest {
        cpu = FakeCpu()
        ppu = FakePpu()
        machine = NesMachineImpl(cpu, ppu)
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
})