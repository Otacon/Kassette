package nes2.ppuBus

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import nes2.fakes.FakeCartridgePort

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

    "CHR read is routed through cartridge" {
        cartridge.memory[0x1234] = 0x42

        bus.read(0x1234) shouldBe 0x42

        cartridge.lastReadAddress shouldBe 0x1234
    }

    "CHR read includes end of pattern table space" {
        cartridge.memory[0x1FFF] = 0xAB

        bus.read(0x1FFF) shouldBe 0xAB

        cartridge.lastReadAddress shouldBe 0x1FFF
    }

    "CHR write is routed through cartridge" {
        bus.write(0x1234, 0x42)

        cartridge.lastWriteAddress shouldBe 0x1234
        cartridge.lastWriteValue shouldBe 0x42
    }

    "PPU address is normalized before CHR access" {
        cartridge.memory[0x0000] = 0x55

        bus.read(0x4000) shouldBe 0x55

        cartridge.lastReadAddress shouldBe 0x0000
    }
})