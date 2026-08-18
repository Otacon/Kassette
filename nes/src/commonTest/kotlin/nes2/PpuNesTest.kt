package nes2

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import nes2.ppu.PpuNes
import nes2.ppu.PpuState

class PpuNesTest : FreeSpec({

    lateinit var ppu: PpuNes
    lateinit var state: PpuState

    beforeTest {
        state = PpuState()
        ppu = PpuNes(state)
    }

    "can write PPUCTRL" {
        ppu.cpuWriteRegister(0x2000, 0x84)

        state.control shouldBe 0x84
    }

    "can set OAM address" {
        ppu.cpuWriteRegister(0x2003, 0x42)

        state.oamAddress shouldBe 0x42
    }

    "writing OAM data stores value in OAM" {
        ppu.cpuWriteRegister(0x2003, 0x42)

        ppu.cpuWriteRegister(0x2004, 0xAB)

        state.oam[0x42] shouldBe 0xAB
    }

    "writing OAM data increments OAM address" {
        ppu.cpuWriteRegister(0x2003, 0x42)

        ppu.cpuWriteRegister(0x2004, 0xAB)

        state.oamAddress shouldBe 0x43
    }

    "OAM address wraps from 0xFF to 0x00" {
        ppu.cpuWriteRegister(0x2003, 0xFF)

        ppu.cpuWriteRegister(0x2004, 0xAB)

        state.oamAddress shouldBe 0x00
    }

    "can read PPUSTATUS" {
        state.status = 0x80

        ppu.cpuReadRegister(0x2002) shouldBe 0x80
    }

    "reading PPUSTATUS clears VBlank flag" {
        state.status = 0x80

        ppu.cpuReadRegister(0x2002)

        state.status shouldBe 0x00
    }

    "reading PPUSTATUS preserves other status flags" {
        state.status = 0xE0

        ppu.cpuReadRegister(0x2002)

        state.status shouldBe 0x60
    }

    "reading PPUSTATUS resets write toggle" {
        state.writeToggle = true

        ppu.cpuReadRegister(0x2002)

        state.writeToggle shouldBe false
    }
})