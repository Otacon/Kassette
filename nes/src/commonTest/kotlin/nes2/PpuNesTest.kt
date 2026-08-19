package nes2

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import nes2.fakes.FakeFrameBuffer
import nes2.fakes.FakePpuBus
import nes2.ppu.PpuNes
import nes2.ppu.PpuState

class PpuNesTest : FreeSpec({

    lateinit var ppu: PpuNes
    lateinit var state: PpuState
    lateinit var ppuBus: FakePpuBus
    lateinit var frameBuffer: FakeFrameBuffer
    var nmiRequested = false

    beforeTest {
        state = PpuState()
        ppuBus = FakePpuBus()
        nmiRequested = false
        frameBuffer = FakeFrameBuffer()
        ppu = PpuNes(
            state = state,
            ppuBus = ppuBus,
            onNmi = { nmiRequested = true },
            frameBuffer = frameBuffer
        )
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

    "NMI is requested when VBlank starts and NMI is enabled" {
        state.control = 0x80
        state.scanline = 241
        state.dot = 1

        ppu.tick()

        nmiRequested shouldBe true
    }

    "NMI is not requested when VBlank starts and NMI is disabled" {
        state.control = 0x00
        state.scanline = 241
        state.dot = 1

        ppu.tick()

        nmiRequested shouldBe false
    }

    "NMI is not requested again during VBlank" {
        state.control = 0x80
        state.scanline = 241
        state.dot = 2

        ppu.tick()

        nmiRequested shouldBe false
    }

    "enabling NMI during VBlank requests NMI" {
        state.control = 0x00
        state.status = 0x80

        ppu.cpuWriteRegister(0x2000, 0x80)

        nmiRequested shouldBe true
    }

    "enabling NMI outside VBlank does not request NMI" {
        state.control = 0x00
        state.status = 0x00

        ppu.cpuWriteRegister(0x2000, 0x80)

        nmiRequested shouldBe false
    }

    "writing enabled NMI again during VBlank does not request another NMI" {
        state.control = 0x80
        state.status = 0x80

        ppu.cpuWriteRegister(0x2000, 0x80)

        nmiRequested shouldBe false
    }

    "disabling NMI during VBlank does not request NMI" {
        state.control = 0x80
        state.status = 0x80

        ppu.cpuWriteRegister(0x2000, 0x00)

        nmiRequested shouldBe false
    }

    "pre-render scanline clears VBlank" {
        state.status = 0x80
        state.scanline = 261
        state.dot = 1

        ppu.tick()

        state.status and 0x80 shouldBe 0
    }

    "pre-render scanline clears sprite zero hit" {
        state.status = 0x40
        state.scanline = 261
        state.dot = 1

        ppu.tick()

        state.status and 0x40 shouldBe 0
    }

    "pre-render scanline clears sprite overflow" {
        state.status = 0x20
        state.scanline = 261
        state.dot = 1

        ppu.tick()

        state.status and 0x20 shouldBe 0
    }

    "pre-render scanline clears all PPU status flags" {
        state.status = 0xE0
        state.scanline = 261
        state.dot = 1

        ppu.tick()

        state.status shouldBe 0x00
    }

    "background fetch reads nametable byte at dot 1" {
        state.scanline = 0
        state.dot = 1
        state.mask = 0x08
        state.v = 0x0123

        ppuBus.memory[0x2123] = 0x42

        ppu.tick()

        state.nametableByte shouldBe 0x42
    }

    "background fetch reads attribute byte at dot 3" {
        state.scanline = 0
        state.dot = 3
        state.mask = 0x08
        state.v = 0x0000

        ppuBus.memory[0x23C0] = 0xAB

        ppu.tick()

        state.attributeByte shouldBe 0xAB
    }

    "background fetch reads low pattern byte at dot 5" {
        state.scanline = 0
        state.dot = 5
        state.mask = 0x08
        state.nametableByte = 0x02
        state.v = 0x0000

        ppuBus.memory[0x0020] = 0xAA

        ppu.tick()

        state.patternLowByte shouldBe 0xAA
    }

    "background fetch reads high pattern byte at dot 7" {
        state.scanline = 0
        state.dot = 7
        state.mask = 0x08
        state.nametableByte = 0x02
        state.v = 0x0000

        ppuBus.memory[0x0028] = 0x55

        ppu.tick()

        state.patternHighByte shouldBe 0x55
    }

    "background pattern fetch can use pattern table 1" {
        state.scanline = 0
        state.dot = 5
        state.control = 0x10
        state.nametableByte = 0x02
        state.mask = 0x08

        ppuBus.memory[0x1020] = 0xAA

        ppu.tick()

        state.patternLowByte shouldBe 0xAA
    }

    "background fetch does not happen outside fetch dots" {
        state.scanline = 0
        state.dot = 257
        state.nametableByte = 0x11

        ppu.tick()

        state.nametableByte shouldBe 0x11
    }

    "background low pattern fetch uses pattern table 1 when PPUCTRL bit 4 is set" {
        state.scanline = 0
        state.dot = 5
        state.control = 0x10
        state.nametableByte = 0x02
        state.v = 0x0000
        state.mask = 0x08

        ppuBus.memory[0x1020] = 0xAA

        ppu.tick()

        state.patternLowByte shouldBe 0xAA
    }

    "background high pattern fetch uses pattern table 1 when PPUCTRL bit 4 is set" {
        state.scanline = 0
        state.dot = 7
        state.control = 0x10
        state.nametableByte = 0x02
        state.v = 0x0000
        state.mask = 0x08

        ppuBus.memory[0x1028] = 0x55

        ppu.tick()

        state.patternHighByte shouldBe 0x55
    }

    "background fetch increments coarse X at dot 8" {
        state.scanline = 0
        state.dot = 8
        state.mask = 0x08
        state.v = 0x0005

        ppu.tick()

        state.v and 0x001F shouldBe 0x06
    }

    "coarse X wraps from 31 to 0" {
        state.scanline = 0
        state.dot = 8
        state.v = 0x001F
        state.mask = 0x08

        ppu.tick()

        state.v and 0x001F shouldBe 0x00
    }

    "coarse X wrap switches horizontal nametable" {
        state.scanline = 0
        state.dot = 8
        state.v = 0x001F
        state.mask = 0x08

        ppu.tick()

        state.v and 0x0400 shouldBe 0x0400
    }

    "coarse X wrap toggles horizontal nametable" {
        state.scanline = 0
        state.dot = 8
        state.v = 0x041F
        state.mask = 0x08

        ppu.tick()

        state.v and 0x0400 shouldBe 0x0000
    }

    "incrementing coarse X preserves other VRAM address bits" {
        state.scanline = 0
        state.dot = 8
        state.v = 0x7125
        state.mask = 0x08

        ppu.tick()

        state.v shouldBe 0x7126
    }

    "vertical scroll increments fine Y at dot 256" {
        state.scanline = 0
        state.dot = 256
        state.v = 0x0000
        state.mask = 0x08

        ppu.tick()

        (state.v shr 12) and 0x07 shouldBe 1
    }

    "vertical scroll increments coarse Y when fine Y wraps" {
        state.scanline = 0
        state.dot = 256
        state.mask = 0x08

        // fine Y = 7, coarse Y = 5
        state.v = (7 shl 12) or (5 shl 5)

        ppu.tick()

        (state.v shr 12) and 0x07 shouldBe 0
        (state.v shr 5) and 0x1F shouldBe 6
    }

    "coarse Y 29 wraps to 0 and switches vertical nametable" {
        state.scanline = 0
        state.dot = 256
        state.mask = 0x08

        state.v = (7 shl 12) or (29 shl 5)

        ppu.tick()

        (state.v shr 12) and 0x07 shouldBe 0
        (state.v shr 5) and 0x1F shouldBe 0
        state.v and 0x0800 shouldBe 0x0800
    }

    "coarse Y 29 toggles vertical nametable" {
        state.scanline = 0
        state.dot = 256
        state.mask = 0x08

        state.v = 0x0800 or (7 shl 12) or (29 shl 5)

        ppu.tick()

        state.v and 0x0800 shouldBe 0x0000
    }

    "coarse Y 31 wraps to 0 without switching vertical nametable" {
        state.scanline = 0
        state.dot = 256
        state.mask = 0x08

        state.v = 0x0800 or (7 shl 12) or (31 shl 5)

        ppu.tick()

        (state.v shr 5) and 0x1F shouldBe 0
        state.v and 0x0800 shouldBe 0x0800
    }

    "dot 257 copies coarse X from temporary VRAM address" {
        state.scanline = 0
        state.dot = 257
        state.mask = 0x08

        state.v = 0x0000
        state.t = 0x0015

        ppu.tick()

        state.v and 0x001F shouldBe 0x15
    }

    "dot 257 copies horizontal nametable from temporary VRAM address" {
        state.scanline = 0
        state.dot = 257
        state.mask = 0x08

        state.v = 0x0000
        state.t = 0x0400

        ppu.tick()

        state.v and 0x0400 shouldBe 0x0400
    }

    "dot 257 can clear horizontal scroll bits" {
        state.scanline = 0
        state.dot = 257
        state.mask = 0x08

        state.v = 0x041F
        state.t = 0x0000

        ppu.tick()

        state.v and 0x041F shouldBe 0x0000
    }

    "dot 257 preserves vertical scroll bits" {
        state.scanline = 0
        state.dot = 257
        state.mask = 0x08

        state.v = 0x7380
        state.t = 0x0415

        ppu.tick()

        state.v and 0x7BE0 shouldBe 0x7380
    }

    "pre-render scanline copies coarse Y from temporary VRAM address" {
        state.scanline = 261
        state.dot = 280
        state.mask = 0x08

        state.v = 0x0000
        state.t = 5 shl 5

        ppu.tick()

        (state.v shr 5) and 0x1F shouldBe 5
    }

    "pre-render scanline copies fine Y from temporary VRAM address" {
        state.scanline = 261
        state.dot = 280
        state.mask = 0x08

        state.v = 0x0000
        state.t = 6 shl 12

        ppu.tick()

        (state.v shr 12) and 0x07 shouldBe 6
    }

    "pre-render scanline copies vertical nametable from temporary VRAM address" {
        state.scanline = 261
        state.dot = 280
        state.mask = 0x08

        state.v = 0x0000
        state.t = 0x0800

        ppu.tick()

        state.v and 0x0800 shouldBe 0x0800
    }

    "vertical scroll copy preserves horizontal scroll bits" {
        state.scanline = 261
        state.dot = 280

        state.v = 0x0415
        state.t = 0x7380

        ppu.tick()

        state.v and 0x041F shouldBe 0x0415
    }

    "vertical scroll is copied through pre-render dots 280 to 304" {
        state.scanline = 261
        state.dot = 304
        state.t = 0x7380
        state.mask = 0x08

        ppu.tick()

        state.v and 0x7BE0 shouldBe 0x7380
    }

    "dot 8 loads low pattern byte into shift register" {
        state.scanline = 0
        state.dot = 8
        state.mask = 0x08

        state.patternLowByte = 0xAB

        ppu.tick()

        state.patternLowShift and 0x00FF shouldBe 0xAB
    }

    "dot 8 loads high pattern byte into shift register" {
        state.scanline = 0
        state.dot = 8
        state.mask = 0x08

        state.patternHighByte = 0xCD

        ppu.tick()

        state.patternHighShift and 0x00FF shouldBe 0xCD
    }

    "dot 8 shifts existing pattern data before loading new tile data" {
        state.scanline = 0
        state.dot = 8
        state.mask = 0x08

        state.patternLowShift = 0xAB00
        state.patternLowByte = 0x42

        ppu.tick()

        state.patternLowShift shouldBe 0x5642
    }

    "attribute palette 0 loads zeroes into attribute shift registers" {
        state.scanline = 0
        state.dot = 8

        state.attributeByte = 0x00

        ppu.tick()

        state.attributeLowShift and 0x00FF shouldBe 0x00
        state.attributeHighShift and 0x00FF shouldBe 0x00
    }

    "attribute palette 1 loads low attribute shift bits" {
        state.scanline = 0
        state.dot = 8
        state.mask = 0x08

        // top-left quadrant = palette 1
        state.attributeByte = 0b00000001
        state.v = 0x0000

        ppu.tick()

        state.attributeLowShift and 0x00FF shouldBe 0xFF
        state.attributeHighShift and 0x00FF shouldBe 0x00
    }

    "attribute palette 2 loads high attribute shift bits" {
        state.scanline = 0
        state.dot = 8
        state.mask = 0x08

        // top-left quadrant = palette 2
        state.attributeByte = 0b00000010
        state.v = 0x0000

        ppu.tick()

        state.attributeLowShift and 0x00FF shouldBe 0x00
        state.attributeHighShift and 0x00FF shouldBe 0xFF
    }

    "attribute palette 3 loads both attribute shift bits" {
        state.scanline = 0
        state.dot = 8
        state.mask = 0x08

        // top-left quadrant = palette 3
        state.attributeByte = 0b00000011
        state.v = 0x0000

        ppu.tick()

        state.attributeLowShift and 0x00FF shouldBe 0xFF
        state.attributeHighShift and 0x00FF shouldBe 0xFF
    }

    "attribute palette is selected from current tile quadrant" {
        state.scanline = 0
        state.dot = 8
        state.mask = 0x08

        // top-right quadrant = palette 3
        state.attributeByte = 0b00001100

        // coarse X bit 1 set => right quadrant
        state.v = 0x0002

        ppu.tick()

        state.attributeLowShift and 0x00FF shouldBe 0xFF
        state.attributeHighShift and 0x00FF shouldBe 0xFF
    }

    "background pattern shift registers shift left" {
        state.scanline = 0
        state.dot = 1
        state.mask = 0x08

        state.patternLowShift = 0x1234
        state.patternHighShift = 0x5678

        ppu.tick()

        state.patternLowShift shouldBe 0x2468
        state.patternHighShift shouldBe 0xACF0
    }

    "background attribute shift registers shift left" {
        state.scanline = 0
        state.dot = 1
        state.mask = 0x08

        state.attributeLowShift = 0x1234
        state.attributeHighShift = 0x5678

        ppu.tick()

        state.attributeLowShift shouldBe 0x2468
        state.attributeHighShift shouldBe 0xACF0
    }

    "background shift registers stay 16 bit" {
        state.scanline = 0
        state.dot = 1
        state.mask = 0x08

        state.patternLowShift = 0x8001

        ppu.tick()

        state.patternLowShift shouldBe 0x0002
    }

    "background shift registers do not shift outside rendering dots" {
        state.scanline = 0
        state.dot = 300

        state.patternLowShift = 0x1234

        ppu.tick()

        state.patternLowShift shouldBe 0x1234
    }

    "background shift registers do not shift during VBlank" {
        state.scanline = 241
        state.dot = 100

        state.patternLowShift = 0x1234

        ppu.tick()

        state.patternLowShift shouldBe 0x1234
    }

    "renders visible pixel" {
        state.scanline = 10
        state.dot = 20
        state.mask = 0x08

        state.fineX = 0
        state.patternLowShift = 0x8000
        state.patternHighShift = 0x0000

        ppuBus.memory[0x3F01] = 0x21

        ppu.tick()

        frameBuffer.writtenPixels shouldBe listOf(
            FakeFrameBuffer.WrittenPixel(
                x = 19,
                y = 10,
                color = 0x21,
            )
        )
    }

    "first visible dot renders pixel at 0,0" {
        state.scanline = 0
        state.dot = 1

        ppu.tick()

        frameBuffer.writtenPixels.single().x shouldBe 0
        frameBuffer.writtenPixels.single().y shouldBe 0
    }

    "last visible dot renders pixel at 255,239" {
        state.scanline = 239
        state.dot = 256

        ppu.tick()

        frameBuffer.writtenPixels.single().x shouldBe 255
        frameBuffer.writtenPixels.single().y shouldBe 239
    }

    "does not render outside visible scanlines" {
        state.scanline = 240
        state.dot = 1

        ppu.tick()

        frameBuffer.writtenPixels.size shouldBe 0
    }

    "does not render before first visible dot" {
        state.scanline = 0
        state.dot = 0

        ppu.tick()

        frameBuffer.writtenPixels.size shouldBe 0
    }

    "does not render after last visible dot" {
        state.scanline = 0
        state.dot = 257

        ppu.tick()

        frameBuffer.writtenPixels.size shouldBe 0
    }

    "renders background pattern 1" {
        state.scanline = 0
        state.dot = 9
        state.fineX = 0
        state.mask = 0x08

        state.patternLowShift = 0x8000
        state.patternHighShift = 0x0000

        ppuBus.memory[0x3F01] = 0x21

        ppu.tick()

        frameBuffer.writtenPixels.single().color shouldBe 0x21
    }

    "renders background pattern 2" {
        state.scanline = 0
        state.dot = 9
        state.fineX = 0
        state.mask = 0x08

        state.patternLowShift = 0x0000
        state.patternHighShift = 0x8000

        ppuBus.memory[0x3F02] = 0x22

        ppu.tick()

        frameBuffer.writtenPixels.single().color shouldBe 0x22
    }

    "renders background pattern 3" {
        state.scanline = 0
        state.dot = 9
        state.fineX = 0
        state.mask = 0x08

        state.patternLowShift = 0x8000
        state.patternHighShift = 0x8000

        ppuBus.memory[0x3F03] = 0x23

        ppu.tick()

        frameBuffer.writtenPixels.single().color shouldBe 0x23
    }

    "fine X selects background pattern bit" {
        state.scanline = 0
        state.dot = 9
        state.fineX = 3
        state.mask = 0x08

        state.patternLowShift = 0x1000
        state.patternHighShift = 0x0000

        ppuBus.memory[0x3F01] = 0x21

        ppu.tick()

        frameBuffer.writtenPixels.single().color shouldBe 0x21
    }

    "background pattern zero uses universal background color" {
        state.scanline = 0
        state.dot = 1

        state.patternLowShift = 0x0000
        state.patternHighShift = 0x0000

        ppuBus.memory[0x3F00] = 0x2A

        ppu.tick()

        frameBuffer.writtenPixels.single().color shouldBe 0x2A
    }

    "background pattern uses palette RAM color" {
        state.scanline = 0
        state.dot = 9
        state.mask = 0x08
        state.fineX = 0

        state.patternLowShift = 0x8000
        state.patternHighShift = 0x0000

        // palette 0, pattern 1 -> $3F01
        ppuBus.memory[0x3F01] = 0x12

        ppu.tick()

        frameBuffer.writtenPixels.single().color shouldBe 0x12
    }

    "background attribute selects palette" {
        state.scanline = 0
        state.dot = 9
        state.mask = 0x08
        state.fineX = 0

        // pattern = 1
        state.patternLowShift = 0x8000
        state.patternHighShift = 0x0000

        // palette = 2
        state.attributeLowShift = 0x0000
        state.attributeHighShift = 0x8000

        // palette 2, pattern 1 -> $3F09
        ppuBus.memory[0x3F09] = 0x2C

        ppu.tick()

        frameBuffer.writtenPixels.single().color shouldBe 0x2C
    }

    "background palette and pattern select palette RAM entry" {
        state.scanline = 0
        state.dot = 9
        state.mask = 0x08
        state.fineX = 0

        // pattern = 3
        state.patternLowShift = 0x8000
        state.patternHighShift = 0x8000

        // palette = 3
        state.attributeLowShift = 0x8000
        state.attributeHighShift = 0x8000

        // $3F00 + 3 * 4 + 3 = $3F0F
        ppuBus.memory[0x3F0F] = 0x30

        ppu.tick()

        frameBuffer.writtenPixels.single().color shouldBe 0x30
    }

    "disabled background uses universal background color" {
        state.scanline = 0
        state.dot = 20
        state.mask = 0x00

        state.patternLowShift = 0x8000
        state.patternHighShift = 0x8000

        ppuBus.memory[0x3F00] = 0x2A
        ppuBus.memory[0x3F03] = 0x10

        ppu.tick()

        frameBuffer.writtenPixels.single().color shouldBe 0x2A
    }

    "enabled background renders background pixel" {
        state.scanline = 0
        state.dot = 20
        state.mask = 0x08

        state.patternLowShift = 0x8000
        state.patternHighShift = 0x0000

        ppuBus.memory[0x3F01] = 0x12

        ppu.tick()

        frameBuffer.writtenPixels.single().color shouldBe 0x12
    }

    "background is hidden in leftmost 8 pixels when clipping is enabled" {
        state.scanline = 0
        state.dot = 1
        state.mask = 0x08

        state.patternLowShift = 0x8000

        ppuBus.memory[0x3F00] = 0x2A
        ppuBus.memory[0x3F01] = 0x12

        ppu.tick()

        frameBuffer.writtenPixels.single().color shouldBe 0x2A
    }

    "background can be rendered in leftmost 8 pixels" {
        state.scanline = 0
        state.dot = 1
        state.mask = 0x0A

        state.patternLowShift = 0x8000

        ppuBus.memory[0x3F01] = 0x12

        ppu.tick()

        frameBuffer.writtenPixels.single().color shouldBe 0x12
    }

    "background clipping only affects first 8 pixels" {
        state.scanline = 0
        state.dot = 9
        state.mask = 0x08

        state.patternLowShift = 0x8000

        ppuBus.memory[0x3F01] = 0x12

        ppu.tick()

        frameBuffer.writtenPixels.single().color shouldBe 0x12
    }

    "background pipeline does not shift when rendering is disabled" {
        state.scanline = 0
        state.dot = 1
        state.mask = 0x00

        state.patternLowShift = 0x1234

        ppu.tick()

        state.patternLowShift shouldBe 0x1234
    }

    "background pipeline shifts when background rendering is enabled" {
        state.scanline = 0
        state.dot = 1
        state.mask = 0x08

        state.patternLowShift = 0x1234

        ppu.tick()

        state.patternLowShift shouldBe 0x2468
    }

    "background pipeline runs when only sprite rendering is enabled" {
        state.scanline = 0
        state.dot = 1
        state.mask = 0x10

        state.patternLowShift = 0x1234

        ppu.tick()

        state.patternLowShift shouldBe 0x2468
    }

    "horizontal scroll is not copied when rendering is disabled" {
        state.scanline = 0
        state.dot = 257
        state.mask = 0x00

        state.v = 0x0000
        state.t = 0x0415

        ppu.tick()

        state.v shouldBe 0x0000
    }

    "horizontal scroll is copied when rendering is enabled" {
        state.scanline = 0
        state.dot = 257
        state.mask = 0x08

        state.v = 0x0000
        state.t = 0x0415

        ppu.tick()

        state.v and 0x041F shouldBe 0x0415
    }

    "even frame does not skip pre-render dot" {
        state.oddFrame = false
        state.scanline = 261
        state.dot = 339
        state.mask = 0x08

        ppu.tick()

        state.scanline shouldBe 261
        state.dot shouldBe 340
    }

    "odd frame skips final pre-render dot when rendering is enabled" {
        state.oddFrame = true
        state.scanline = 261
        state.dot = 339
        state.mask = 0x08

        ppu.tick()

        state.scanline shouldBe 0
        state.dot shouldBe 0
        state.oddFrame shouldBe false
    }

    "odd frame does not skip dot when rendering is disabled" {
        state.oddFrame = true
        state.scanline = 261
        state.dot = 339
        state.mask = 0x00

        ppu.tick()

        state.scanline shouldBe 261
        state.dot shouldBe 340
    }

    "frame parity toggles after complete frame" {
        state.oddFrame = false
        state.scanline = 261
        state.dot = 340

        ppu.tick()

        state.scanline shouldBe 0
        state.dot shouldBe 0
        state.oddFrame shouldBe true
    }

    "background fetch loads shift registers at dot 8" {
        state.scanline = 0
        state.dot = 8
        state.mask = 0x08

        state.patternLowByte = 0x12
        state.patternHighByte = 0x34

        ppu.tick()

        state.patternLowShift and 0x00FF shouldBe 0x12
        state.patternHighShift and 0x00FF shouldBe 0x34
    }

    "background fetch increments vertical scroll at dot 256" {
        state.scanline = 0
        state.dot = 256
        state.mask = 0x08
        state.v = 0x0000

        ppu.tick()

        (state.v shr 12) and 0x07 shouldBe 1
    }

    "secondary OAM clear starts at dot 1" {
        state.scanline = 0
        state.dot = 1
        state.mask = 0x08

        state.secondaryOam[0] = 0x42

        ppu.tick()

        state.secondaryOam[0] shouldBe 0xFF
    }

    "secondary OAM clear advances every two dots" {
        state.scanline = 0
        state.dot = 3
        state.mask = 0x08

        state.secondaryOam[1] = 0x42

        ppu.tick()

        state.secondaryOam[1] shouldBe 0xFF
    }

    "secondary OAM clear reaches last byte" {
        state.scanline = 0
        state.dot = 63
        state.mask = 0x08

        state.secondaryOam[31] = 0x42

        ppu.tick()

        state.secondaryOam[31] shouldBe 0xFF
    }

    "secondary OAM is not cleared after dot 64" {
        state.scanline = 0
        state.dot = 65
        state.mask = 0x08

        state.secondaryOam[0] = 0x42

        ppu.tick()

        state.secondaryOam[0] shouldBe 0x42
    }

    "secondary OAM is not cleared when rendering is disabled" {
        state.scanline = 0
        state.dot = 1
        state.mask = 0x00

        state.secondaryOam[0] = 0x42

        ppu.tick()

        state.secondaryOam[0] shouldBe 0x42
    }

})