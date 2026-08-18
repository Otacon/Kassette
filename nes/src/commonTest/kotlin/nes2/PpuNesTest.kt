package nes2

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import nes2.fakes.FakePpuBus
import nes2.ppu.PpuNes
import nes2.ppu.PpuState

class PpuNesTest : FreeSpec({

    lateinit var ppu: PpuNes
    lateinit var state: PpuState
    lateinit var ppuBus: FakePpuBus

    beforeTest {
        state = PpuState()
        ppuBus = FakePpuBus()
        ppu = PpuNes(state, ppuBus = ppuBus)
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

    "can read OAM data" {
        state.oamAddress = 0x42
        state.oam[0x42] = 0xAB

        ppu.cpuReadRegister(0x2004) shouldBe 0xAB
    }

    "reading OAM data does not increment OAM address" {
        state.oamAddress = 0x42
        state.oam[0x42] = 0xAB

        ppu.cpuReadRegister(0x2004)

        state.oamAddress shouldBe 0x42
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

    "reading PPUSTATUS resets PPUSCROLL write sequence" {
        ppu.cpuWriteRegister(0x2005, 0x12)

        ppu.cpuReadRegister(0x2002)

        state.writeToggle shouldBe false
    }

    "reading PPUSTATUS resets PPUADDR write sequence" {
        ppu.cpuWriteRegister(0x2006, 0x23)

        ppu.cpuReadRegister(0x2002)

        state.writeToggle shouldBe false
    }

    "first PPUSCROLL write sets coarse X" {
        ppu.cpuWriteRegister(0x2005, 0b10101_000)

        state.t and 0x001F shouldBe 0b10101
    }

    "first PPUSCROLL write sets fine X" {
        ppu.cpuWriteRegister(0x2005, 0b00000_101)

        state.fineX shouldBe 0b101
    }

    "first PPUSCROLL write sets write toggle" {
        ppu.cpuWriteRegister(0x2005, 0x42)

        state.writeToggle shouldBe true
    }

    "second PPUSCROLL write sets coarse Y" {
        ppu.cpuWriteRegister(0x2005, 0x00)

        ppu.cpuWriteRegister(0x2005, 0b10110_000)

        (state.t shr 5) and 0x1F shouldBe 0b10110
    }

    "second PPUSCROLL write sets fine Y" {
        ppu.cpuWriteRegister(0x2005, 0x00)

        ppu.cpuWriteRegister(0x2005, 0b00000_110)

        (state.t shr 12) and 0x07 shouldBe 0b110
    }

    "second PPUSCROLL write clears write toggle" {
        ppu.cpuWriteRegister(0x2005, 0x12)

        ppu.cpuWriteRegister(0x2005, 0x34)

        state.writeToggle shouldBe false
    }

    "first PPUADDR write sets high address byte" {
        ppu.cpuWriteRegister(0x2006, 0x23)

        state.t shouldBe 0x2300
    }

    "first PPUADDR write sets write toggle" {
        ppu.cpuWriteRegister(0x2006, 0x23)

        state.writeToggle shouldBe true
    }

    "second PPUADDR write sets low address byte" {
        ppu.cpuWriteRegister(0x2006, 0x23)
        ppu.cpuWriteRegister(0x2006, 0x45)

        state.t shouldBe 0x2345
    }

    "second PPUADDR write copies temporary address to current VRAM address" {
        ppu.cpuWriteRegister(0x2006, 0x23)
        ppu.cpuWriteRegister(0x2006, 0x45)

        state.v shouldBe 0x2345
    }

    "second PPUADDR write clears write toggle" {
        ppu.cpuWriteRegister(0x2006, 0x23)
        ppu.cpuWriteRegister(0x2006, 0x45)

        state.writeToggle shouldBe false
    }

    "PPUADDR ignores upper two bits of first write" {
        ppu.cpuWriteRegister(0x2006, 0xFF)
        ppu.cpuWriteRegister(0x2006, 0x42)

        state.v shouldBe 0x3F42
    }

    "PPUDATA writes to current VRAM address" {
        state.v = 0x2345

        ppu.cpuWriteRegister(0x2007, 0xAB)

        ppuBus.memory[0x2345] shouldBe 0xAB
    }

    "PPUDATA increments VRAM address by 1 by default after write" {
        state.v = 0x2000

        ppu.cpuWriteRegister(0x2007, 0xAB)

        state.v shouldBe 0x2001
    }

    "PPUDATA increments VRAM address by 1 by default after read" {
        state.v = 0x2000

        ppu.cpuReadRegister(0x2007)

        state.v shouldBe 0x2001
    }

    "PPUDATA increments VRAM address by 32 when PPUCTRL bit 2 is set" {
        state.control = 0x04
        state.v = 0x2000

        ppu.cpuWriteRegister(0x2007, 0xAB)

        state.v shouldBe 0x2020
    }

    "PPUDATA VRAM address wraps at 0x3FFF" {
        state.v = 0x3FFF

        ppu.cpuWriteRegister(0x2007, 0xAB)

        state.v shouldBe 0x0000
    }

    "PPUDATA reads are buffered outside palette space" {
        state.v = 0x2000
        state.dataBuffer = 0x11
        ppuBus.memory[0x2000] = 0x42

        ppu.cpuReadRegister(0x2007) shouldBe 0x11
        state.dataBuffer shouldBe 0x42
    }

    "PPUDATA returns buffered value on subsequent read" {
        state.v = 0x2000
        ppuBus.memory[0x2000] = 0x42
        ppuBus.memory[0x2001] = 0xAB

        ppu.cpuReadRegister(0x2007) shouldBe 0x00
        ppu.cpuReadRegister(0x2007) shouldBe 0x42

        state.dataBuffer shouldBe 0xAB
    }

    "PPUDATA palette reads are not buffered" {
        state.v = 0x3F00
        state.dataBuffer = 0x11
        ppuBus.memory[0x3F00] = 0x42

        ppu.cpuReadRegister(0x2007) shouldBe 0x42
    }

    "PPUDATA palette read increments VRAM address" {
        state.v = 0x3F00
        ppuBus.memory[0x3F00] = 0x42

        ppu.cpuReadRegister(0x2007)

        state.v shouldBe 0x3F01
    }

    "PPUDATA reads from current VRAM address into buffer" {
        state.v = 0x2345
        state.dataBuffer = 0x11
        ppuBus.memory[0x2345] = 0xAB

        ppu.cpuReadRegister(0x2007) shouldBe 0x11
        state.dataBuffer shouldBe 0xAB
    }

    "PPUDATA palette reads refresh data buffer from mirrored nametable address" {
        state.v = 0x3F00
        state.dataBuffer = 0x11

        ppuBus.memory[0x3F00] = 0x42
        ppuBus.memory[0x2F00] = 0xAB

        ppu.cpuReadRegister(0x2007) shouldBe 0x42
        state.dataBuffer shouldBe 0xAB
    }

    "PPUDATA palette buffer refresh uses address minus 0x1000" {
        state.v = 0x3F12

        ppuBus.memory[0x3F12] = 0x55
        ppuBus.memory[0x2F12] = 0xAA

        ppu.cpuReadRegister(0x2007)

        state.dataBuffer shouldBe 0xAA
    }

    "PPUCTRL sets nametable bits in temporary VRAM address" {
        ppu.cpuWriteRegister(0x2000, 0x03)

        (state.t shr 10) and 0x03 shouldBe 0x03
    }

    "PPUCTRL can select each nametable" {
        ppu.cpuWriteRegister(0x2000, 0x00)
        (state.t shr 10) and 0x03 shouldBe 0

        ppu.cpuWriteRegister(0x2000, 0x01)
        (state.t shr 10) and 0x03 shouldBe 1

        ppu.cpuWriteRegister(0x2000, 0x02)
        (state.t shr 10) and 0x03 shouldBe 2

        ppu.cpuWriteRegister(0x2000, 0x03)
        (state.t shr 10) and 0x03 shouldBe 3
    }

    "PPUCTRL preserves other temporary VRAM address bits" {
        state.t = 0x7123

        ppu.cpuWriteRegister(0x2000, 0x02)

        state.t shouldBe 0x7923
    }

    "can write PPUMASK" {
        ppu.cpuWriteRegister(0x2001, 0x1E)

        state.mask shouldBe 0x1E
    }

    "PPUMASK only stores low 8 bits" {
        ppu.cpuWriteRegister(0x2001, 0x1234)

        state.mask shouldBe 0x34
    }

    "tick advances PPU dot" {
        ppu.tick()

        state.dot shouldBe 1
    }

    "PPU advances to next scanline after 341 dots" {
        repeat(341) {
            ppu.tick()
        }

        state.dot shouldBe 0
        state.scanline shouldBe 1
    }

    "PPU wraps to first scanline after a complete frame" {
        repeat(341 * 262) {
            ppu.tick()
        }

        state.dot shouldBe 0
        state.scanline shouldBe 0
    }

    "PPU stays on current scanline until final dot" {
        repeat(340) { ppu.tick() }

        state.dot shouldBe 340
        state.scanline shouldBe 0

        ppu.tick()

        state.dot shouldBe 0
        state.scanline shouldBe 1
    }

    "VBlank starts at scanline 241 dot 1" {
        state.scanline = 241
        state.dot = 1

        ppu.tick()

        state.status and 0x80 shouldBe 0x80
    }

    "VBlank does not start at scanline 241 dot 0" {
        state.scanline = 241
        state.dot = 0

        ppu.tick()

        state.status and 0x80 shouldBe 0x00
    }

    "VBlank is cleared at pre-render scanline dot 1" {
        state.status = 0x80
        state.scanline = 261
        state.dot = 1

        ppu.tick()

        state.status and 0x80 shouldBe 0x00
    }

    "clearing VBlank preserves other status flags" {
        state.status = 0xE0
        state.scanline = 261
        state.dot = 1

        ppu.tick()

        state.status shouldBe 0x60
    }
})