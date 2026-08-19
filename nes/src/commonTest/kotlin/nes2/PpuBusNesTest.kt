package nes2

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import nes.cartridge.Mirroring
import nes2.fakes.FakeCartridgePort
import nes2.ppuBus.PpuBusNes
import nes2.ppuBus.PpuBusState

class PpuBusNesTest : FreeSpec({

    lateinit var state: PpuBusState
    lateinit var cartridge: FakeCartridgePort
    lateinit var bus: PpuBusNes

    beforeTest {
        state = PpuBusState(
            nametableRam = IntArray(0x800),
            paletteRam = IntArray(0x20),
        )

        cartridge = FakeCartridgePort()

        bus = PpuBusNes(
            state = state,
            cartridge = cartridge,
        )
    }

    "writing nametable RAM updates state" {
        bus.write(0x2000, 0x42)

        state.nametableRam[0x0000] shouldBe 0x42
    }

    "writing to the end of nametable RAM updates state" {
        bus.write(0x27FF, 0xAB)

        state.nametableRam[0x07FF] shouldBe 0xAB
    }

    "writing palette RAM updates state" {
        bus.write(0x3F00, 0x42)

        state.paletteRam[0x00] shouldBe 0x42
    }

    "nametable space currently mirrors every 0x800 bytes" {
        state.nametableRam[0x0000] = 0x42

        bus.read(0x2000) shouldBe 0x42
        bus.read(0x2800) shouldBe 0x42
        bus.read(0x3000) shouldBe 0x42
    }

    "0x3F10 mirrors 0x3F00" {
        bus.write(0x3F10, 0x42)

        state.paletteRam[0x00] shouldBe 0x42
    }

    "0x3F14 mirrors 0x3F04" {
        bus.write(0x3F14, 0x42)

        state.paletteRam[0x04] shouldBe 0x42
    }

    "0x3F18 mirrors 0x3F08" {
        bus.write(0x3F18, 0x42)

        state.paletteRam[0x08] shouldBe 0x42
    }

    "0x3F1C mirrors 0x3F0C" {
        bus.write(0x3F1C, 0x42)

        state.paletteRam[0x0C] shouldBe 0x42
    }

    "special palette mirrors are used when reading" {
        state.paletteRam[0x00] = 0x42
        state.paletteRam[0x04] = 0x43
        state.paletteRam[0x08] = 0x44
        state.paletteRam[0x0C] = 0x45

        bus.read(0x3F10) shouldBe 0x42
        bus.read(0x3F14) shouldBe 0x43
        bus.read(0x3F18) shouldBe 0x44
        bus.read(0x3F1C) shouldBe 0x45
    }

    "vertical mirroring maps nametable 0 and 2 together" {
        cartridge.mirroring = Mirroring.VERTICAL

        bus.write(0x2000, 0x11)

        bus.read(0x2800) shouldBe 0x11
    }

    "vertical mirroring maps nametable 1 and 3 together" {
        cartridge.mirroring = Mirroring.VERTICAL

        bus.write(0x2400, 0x22)

        bus.read(0x2C00) shouldBe 0x22
    }

    "vertical mirroring keeps nametable 0 and 1 separate" {
        cartridge.mirroring = Mirroring.VERTICAL

        bus.write(0x2000, 0x11)
        bus.write(0x2400, 0x22)

        bus.read(0x2000) shouldBe 0x11
        bus.read(0x2400) shouldBe 0x22
    }

    "horizontal mirroring maps nametable 0 and 1 together" {
        cartridge.mirroring = Mirroring.HORIZONTAL

        bus.write(0x2000, 0x11)

        bus.read(0x2400) shouldBe 0x11
    }

    "horizontal mirroring maps nametable 2 and 3 together" {
        cartridge.mirroring = Mirroring.HORIZONTAL

        bus.write(0x2800, 0x22)

        bus.read(0x2C00) shouldBe 0x22
    }

    "horizontal mirroring keeps upper and lower nametable pairs separate" {
        cartridge.mirroring = Mirroring.HORIZONTAL

        bus.write(0x2000, 0x11)
        bus.write(0x2800, 0x22)

        bus.read(0x2000) shouldBe 0x11
        bus.read(0x2800) shouldBe 0x22
    }

    "single screen lower maps all nametables to first kilobyte" {
        cartridge.mirroring = Mirroring.SINGLE_SCREEN_LOWER

        bus.write(0x2000, 0x42)

        bus.read(0x2000) shouldBe 0x42
        bus.read(0x2400) shouldBe 0x42
        bus.read(0x2800) shouldBe 0x42
        bus.read(0x2C00) shouldBe 0x42
    }

    "single screen upper maps all nametables to second kilobyte" {
        cartridge.mirroring = Mirroring.SINGLE_SCREEN_UPPER

        bus.write(0x2400, 0x42)

        bus.read(0x2000) shouldBe 0x42
        bus.read(0x2400) shouldBe 0x42
        bus.read(0x2800) shouldBe 0x42
        bus.read(0x2C00) shouldBe 0x42
    }

    "nametable mirroring preserves offset within table" {
        cartridge.mirroring = Mirroring.VERTICAL

        bus.write(0x2123, 0x55)

        bus.read(0x2923) shouldBe 0x55
    }

    "3000 through 3EFF mirrors 2000 through 2EFF" {
        cartridge.mirroring = Mirroring.VERTICAL

        bus.write(0x2123, 0x66)

        bus.read(0x3123) shouldBe 0x66
    }

    "writes through 3000 mirror are visible through 2000 range" {
        cartridge.mirroring = Mirroring.HORIZONTAL

        bus.write(0x3456, 0x77)

        bus.read(0x2456) shouldBe 0x77
    }

    "mirroring mode is read dynamically from cartridge" {
        cartridge.mirroring = Mirroring.VERTICAL

        bus.write(0x2000, 0x11)

        bus.read(0x2800) shouldBe 0x11

        cartridge.mirroring = Mirroring.HORIZONTAL

        bus.write(0x2000, 0x22)

        bus.read(0x2400) shouldBe 0x22
    }
})