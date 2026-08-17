package nes2

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import nes.cartridge.CartridgeSocket

class CpuBusNesTest : FreeSpec({

    lateinit var ram: IntArray
    lateinit var ppuRegisters: IntArray
    lateinit var ppu: FakePpu
    lateinit var cartridgeSocket: CartridgeSocket
    lateinit var bus: CpuBusNes

    beforeTest {
        ram = IntArray(0x800)
        ppuRegisters = IntArray(8)
        ppu = FakePpu(registers = ppuRegisters)
        cartridgeSocket = CartridgeSocket()

        bus = CpuBusNes(
            ram = ram,
            ppu = ppu,
            cartridge = cartridgeSocket,
        )
    }

    "can read and write RAM" {
        bus.write(0x0000, 0x42)

        bus.read(0x0000) shouldBe 0x42
    }

    "RAM ends at 0x07FF" {
        bus.write(0x07FF, 0xAB)

        bus.read(0x07FF) shouldBe 0xAB
    }

    "RAM is mirrored through 0x1FFF" {
        bus.write(0x0012, 0x42)

        bus.read(0x0012) shouldBe 0x42
        bus.read(0x0812) shouldBe 0x42
        bus.read(0x1012) shouldBe 0x42
        bus.read(0x1812) shouldBe 0x42
    }

    "writing to a RAM mirror writes to the underlying RAM" {
        bus.write(0x1842, 0xAB)

        bus.read(0x0042) shouldBe 0xAB
        bus.read(0x0842) shouldBe 0xAB
        bus.read(0x1042) shouldBe 0xAB
        bus.read(0x1842) shouldBe 0xAB
    }

    "can read PPU registers" {
        ppuRegisters[2] = 0x42

        bus.read(0x2002) shouldBe 0x42
    }

    "can write PPU registers" {
        bus.write(0x2007, 0xAB)

        ppuRegisters[7] shouldBe 0xAB
    }

    "PPU registers are mirrored through 0x3FFF" {
        ppuRegisters[2] = 0x42

        bus.read(0x2002) shouldBe 0x42
        bus.read(0x200A) shouldBe 0x42
        bus.read(0x2012) shouldBe 0x42
        bus.read(0x3FFA) shouldBe 0x42
    }

    "writing to a PPU register mirror writes to the underlying register" {
        bus.write(0x3FFF, 0xAB)

        ppuRegisters[7] shouldBe 0xAB
    }

    "cartridge space reads zero when no cartridge is inserted" {
        bus.read(0x8000) shouldBe 0
    }

    "writes to cartridge space are ignored when no cartridge is inserted" {
        bus.write(0x8000, 0x42)

        bus.read(0x8000) shouldBe 0
    }

    "unmapped addresses read open bus" {
        bus.read(0x4000) shouldBe 0
    }
})