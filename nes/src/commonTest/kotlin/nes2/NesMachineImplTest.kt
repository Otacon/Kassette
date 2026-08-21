package nes2

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import nes.ConsoleRegion
import nes.cartridge.Cartridge
import nes.cartridge.Mirroring
import nes2.fakes.FakeCartridgePort
import nes2.fakes.FakeCpu
import nes2.fakes.FakeController
import nes2.fakes.FakeOamDma
import nes2.fakes.FakeMapper
import nes2.fakes.FakePpu

class NesMachineImplTest : FreeSpec({

    lateinit var cpu: FakeCpu
    lateinit var ppu: FakePpu
    lateinit var dma: FakeOamDma
    lateinit var cartridge: FakeCartridgePort
    lateinit var controller1: FakeController
    lateinit var controller2: FakeController
    lateinit var machine: NesMachineImpl

    beforeTest {
        cpu = FakeCpu()
        ppu = FakePpu()
        dma = FakeOamDma()
        cartridge = FakeCartridgePort()
        controller1 = FakeController()
        controller2 = FakeController()
        machine = NesMachineImpl(cpu, ppu, dma, cartridge, controller1, controller2)
    }

    "reset advances PPU by CPU reset timing" {
        cpu.resetCycles = 7

        machine.reset()

        cpu.resets shouldBe 1
        ppu.ticks shouldBe 21
    }

    "reset resets controller ports" {
        machine.reset()

        controller1.resets shouldBe 1
        controller2.resets shouldBe 1
    }

    "reset advances PPU using cartridge region timing" {
        machine.insertCartridge(
            Cartridge(
                mirroring = Mirroring.HORIZONTAL,
                prgRom = ByteArray(0),
                chr = ByteArray(0),
                isChrRam = true,
                trainerPresent = false,
                mapper = FakeMapper(),
                region = ConsoleRegion.PAL,
            )
        )
        cpu.resetCycles = 7

        machine.reset()

        ppu.scanlinesPerFrame shouldBe 312
        ppu.nmiScanline shouldBe 241
        ppu.skipsOddFrameDot shouldBe false
        ppu.ticks shouldBe 22
    }

    "insertCartridge applies cartridge region timing" {
        machine.insertCartridge(
            Cartridge(
                mirroring = Mirroring.HORIZONTAL,
                prgRom = ByteArray(0),
                chr = ByteArray(0),
                isChrRam = true,
                trainerPresent = false,
                mapper = FakeMapper(),
                region = ConsoleRegion.DENDY,
            )
        )

        ppu.scanlinesPerFrame shouldBe 312
        ppu.nmiScanline shouldBe 291
        ppu.skipsOddFrameDot shouldBe false
    }

    "PPU timing preserves fractional PAL cycles across CPU steps" {
        machine.insertCartridge(
            Cartridge(
                mirroring = Mirroring.HORIZONTAL,
                prgRom = ByteArray(0),
                chr = ByteArray(0),
                isChrRam = true,
                trainerPresent = false,
                mapper = FakeMapper(),
                region = ConsoleRegion.PAL,
            )
        )
        cpu.cycles = 1
        ppu.ticksUntilNextFrame = 25

        machine.runUntilFrame()

        cpu.steps shouldBe 8
        ppu.ticks shouldBe 25
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

    "asserts CPU IRQ line when mapper IRQ is pending" {
        cpu.cycles = 1
        cartridge.irqPending = true
        ppu.ticksUntilNextFrame = 3

        machine.runUntilFrame()

        cpu.irqLine shouldBe true
        cpu.steps shouldBe 1
    }

    "clears CPU IRQ line when mapper IRQ is not pending" {
        cpu.cycles = 1
        cpu.setIrqLine(true)
        cartridge.irqPending = false
        ppu.ticksUntilNextFrame = 3

        machine.runUntilFrame()

        cpu.irqLine shouldBe false
        cpu.steps shouldBe 1
    }
})
