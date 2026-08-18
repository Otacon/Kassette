package nes2

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import nes2.ppuBus.PpuBusNes
import nes2.ppuBus.PpuBusState

class PpuBusNesTest : FreeSpec({

    lateinit var state: PpuBusState
    lateinit var bus: PpuBusNes

    beforeTest {
        state = PpuBusState(
            nametableRam = IntArray(0x800),
            paletteRam = IntArray(0x20),
        )

        bus = PpuBusNes(state)
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
})