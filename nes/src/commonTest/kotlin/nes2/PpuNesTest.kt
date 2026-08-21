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
    var mapperScanlineClocks = 0

    beforeTest {
        state = PpuState()
        ppuBus = FakePpuBus()
        nmiRequested = false
        mapperScanlineClocks = 0
        frameBuffer = FakeFrameBuffer()
        ppu = PpuNes(
            state = state,
            ppuBus = ppuBus,
            onMapperScanline = { mapperScanlineClocks++ },
            frameBuffer = frameBuffer
        )
        ppu.onNmi = { nmiRequested = true }
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

        ppuBus.memory[0x3F00] = 0x22

        ppu.cpuReadRegister(0x2007) shouldBe 0x22
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

        ppuBus.memory[0x3F00] = 0x22
        ppuBus.memory[0x2F00] = 0xAB

        ppu.cpuReadRegister(0x2007) shouldBe 0x22
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

    "PPU uses PAL scanline count when PAL timing is selected" {
        ppu.configureTiming(
            scanlinesPerFrame = 312,
            nmiScanline = 241,
            skipsOddFrameDot = false,
        )

        repeat(341 * 311) {
            ppu.tick()
        }

        state.dot shouldBe 0
        state.scanline shouldBe 311

        repeat(341) {
            ppu.tick()
        }

        state.dot shouldBe 0
        state.scanline shouldBe 0
    }

    "VBlank starts at region NMI scanline" {
        ppu.configureTiming(
            scanlinesPerFrame = 312,
            nmiScanline = 291,
            skipsOddFrameDot = false,
        )
        state.scanline = 291
        state.dot = 1

        ppu.tick()

        state.status and 0x80 shouldBe 0x80
    }

    "PPU stays on current scanline until final dot" {
        repeat(340) { ppu.tick() }

        state.dot shouldBe 340
        state.scanline shouldBe 0

        ppu.tick()

        state.dot shouldBe 0
        state.scanline shouldBe 1
    }

    "clocks mapper scanline at dot 260 while rendering" {
        state.mask = 0x08
        state.scanline = 10
        state.dot = 260

        ppu.tick()

        mapperScanlineClocks shouldBe 1
    }

    "does not clock mapper scanline when rendering is disabled" {
        state.scanline = 10
        state.dot = 260

        ppu.tick()

        mapperScanlineClocks shouldBe 0
    }

    "does not clock mapper scanline outside rendering scanlines" {
        state.mask = 0x08
        state.scanline = 241
        state.dot = 260

        ppu.tick()

        mapperScanlineClocks shouldBe 0
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

    "secondary OAM clearing stops after dot 64" {
        state.scanline = 0
        state.dot = 65
        state.mask = 0x08

        state.secondaryOam[31] = 0x42

        ppu.tick()

        state.secondaryOam[31] shouldBe 0x42
    }

    "secondary OAM is not cleared when rendering is disabled" {
        state.scanline = 0
        state.dot = 1
        state.mask = 0x00

        state.secondaryOam[0] = 0x42

        ppu.tick()

        state.secondaryOam[0] shouldBe 0x42
    }

    "sprite evaluation copies in-range sprite into secondary OAM" {
        state.scanline = 10
        state.dot = 65
        state.mask = 0x10

        state.oam[0] = 10
        state.oam[1] = 0x42
        state.oam[2] = 0x03
        state.oam[3] = 0x80

        ppu.tick()

        state.secondaryOam[0] shouldBe 10
        state.secondaryOam[1] shouldBe 0x42
        state.secondaryOam[2] shouldBe 0x03
        state.secondaryOam[3] shouldBe 0x80
    }

    "sprite evaluation does not copy out-of-range sprite" {
        state.scanline = 20
        state.dot = 65
        state.mask = 0x10

        state.oam[0] = 10
        state.oam[1] = 0x42
        state.oam[2] = 0x03
        state.oam[3] = 0x80

        ppu.tick()

        state.secondaryOam[0] shouldBe 0xFF
        state.secondaryOam[1] shouldBe 0xFF
        state.secondaryOam[2] shouldBe 0xFF
        state.secondaryOam[3] shouldBe 0xFF
    }

    "sprite evaluation advances to next primary OAM sprite" {
        state.scanline = 10
        state.dot = 65
        state.mask = 0x10

        state.spriteEvaluationIndex = 3

        ppu.tick()

        state.spriteEvaluationIndex shouldBe 4
    }

    "copying sprite advances secondary OAM index by four" {
        state.scanline = 10
        state.dot = 65
        state.mask = 0x10

        state.oam[0] = 10

        ppu.tick()

        state.secondaryOamIndex shouldBe 4
    }

    "out-of-range sprite does not advance secondary OAM index" {
        state.scanline = 20
        state.dot = 65
        state.mask = 0x10

        state.oam[0] = 10

        ppu.tick()

        state.secondaryOamIndex shouldBe 0
    }

    "sprite evaluation does not copy more than eight sprites" {
        state.scanline = 10
        state.dot = 65
        state.mask = 0x10
        state.evaluatedSpriteCount = 8
        state.spriteCount = 8
        state.secondaryOamIndex = 32

        state.oam[0] = 10

        ppu.tick()

        state.spriteCount shouldBe 8
        state.secondaryOamIndex shouldBe 32
    }

    "sprite evaluation advances to next sprite" {
        state.scanline = 10
        state.dot = 65
        state.mask = 0x10
        state.spriteEvaluationIndex = 3

        ppu.tick()

        state.spriteEvaluationIndex shouldBe 4
    }

    "sprite evaluation increments selected sprite count" {
        state.scanline = 10
        state.dot = 65
        state.mask = 0x10

        state.oam[0] = 10

        ppu.tick()

        state.evaluatedSpriteCount shouldBe 1
    }

    "out-of-range sprite does not increment selected sprite count" {
        state.scanline = 20
        state.dot = 65
        state.mask = 0x10

        state.oam[0] = 10

        ppu.tick()

        state.spriteCount shouldBe 0
    }

    "sprite zero is marked when selected" {
        state.scanline = 10
        state.dot = 65
        state.mask = 0x10

        state.spriteEvaluationIndex = 0
        state.oam[0] = 10

        ppu.tick()

        state.evaluatedSpriteZeroSelected shouldBe true
    }

    "non-zero sprite does not mark sprite zero as selected" {
        state.scanline = 10
        state.dot = 65
        state.mask = 0x10

        state.spriteEvaluationIndex = 4

        val offset = 4 * 4
        state.oam[offset] = 10

        ppu.tick()

        state.spriteZeroSelected shouldBe false
    }

    "sprite evaluation selects at most eight sprites" {
        state.scanline = 10
        state.dot = 65
        state.mask = 0x10

        state.evaluatedSpriteCount = 8
        state.secondaryOamIndex = 32

        state.oam[0] = 10

        ppu.tick()

        state.evaluatedSpriteCount shouldBe 8
        state.secondaryOamIndex shouldBe 32
    }

    "sprite low pattern byte is fetched for first sprite" {
        state.scanline = 12
        state.dot = 261
        state.mask = 0x10
        state.spriteCount = 1

        state.secondaryOam[0] = 10
        state.secondaryOam[1] = 0x02
        state.secondaryOam[2] = 0x00

        // tile 2 -> $20, row 2 -> $22
        ppuBus.memory[0x0022] = 0xAA

        ppu.tick()

        state.spritePatternLow[0] shouldBe 0xAA
    }

    "sprite high pattern byte is fetched for first sprite" {
        state.scanline = 12
        state.dot = 263
        state.mask = 0x10
        state.spriteCount = 1

        state.secondaryOam[0] = 10
        state.secondaryOam[1] = 0x02
        state.secondaryOam[2] = 0x00

        // tile 2 -> $20, row 2 -> $22, high plane -> +8
        ppuBus.memory[0x002A] = 0x55

        ppu.tick()

        state.spritePatternHigh[0] shouldBe 0x55
    }

    "sprite pattern fetch uses correct sprite slot" {
        state.scanline = 12
        state.dot = 269
        state.mask = 0x10
        state.spriteCount = 2

        val offset = 4
        state.secondaryOam[offset] = 10
        state.secondaryOam[offset + 1] = 0x03
        state.secondaryOam[offset + 2] = 0x00

        // Second sprite:
        // dots 265..272
        // dot 269 -> low pattern fetch
        // tile 3 -> $30, row 2 -> $32
        ppuBus.memory[0x0032] = 0xAB

        ppu.tick()

        state.spritePatternLow[1] shouldBe 0xAB
    }

    "sprite high pattern fetch uses correct sprite slot" {
        state.scanline = 12
        state.dot = 271
        state.mask = 0x10
        state.spriteCount = 2

        val offset = 4
        state.secondaryOam[offset] = 10
        state.secondaryOam[offset + 1] = 0x03
        state.secondaryOam[offset + 2] = 0x00

        // tile 3 -> $30, row 2 -> $32, high plane -> +8
        ppuBus.memory[0x003A] = 0xCD

        ppu.tick()

        state.spritePatternHigh[1] shouldBe 0xCD
    }

    "sprite pattern fetch uses current sprite row" {
        state.scanline = 15
        state.dot = 261
        state.mask = 0x10
        state.spriteCount = 1

        state.secondaryOam[0] = 10
        state.secondaryOam[1] = 0x02
        state.secondaryOam[2] = 0x00

        // row = 15 - 10 = 5
        // tile 2 -> $20 + 5 = $25
        ppuBus.memory[0x0025] = 0x42

        ppu.tick()

        state.spritePatternLow[0] shouldBe 0x42
    }

    "sprite pattern fetch uses pattern table 1 when PPUCTRL bit 3 is set" {
        state.scanline = 12
        state.dot = 261
        state.mask = 0x10
        state.control = 0x08
        state.spriteCount = 1

        state.secondaryOam[0] = 10
        state.secondaryOam[1] = 0x02
        state.secondaryOam[2] = 0x00

        // pattern table 1 -> $1000
        // tile 2 -> $20
        // row 2 -> $02
        ppuBus.memory[0x1022] = 0x66

        ppu.tick()

        state.spritePatternLow[0] shouldBe 0x66
    }

    "sprite high pattern fetch uses pattern table 1 when PPUCTRL bit 3 is set" {
        state.scanline = 12
        state.dot = 263
        state.mask = 0x10
        state.control = 0x08
        state.spriteCount = 1

        state.secondaryOam[0] = 10
        state.secondaryOam[1] = 0x02
        state.secondaryOam[2] = 0x00

        ppuBus.memory[0x102A] = 0x77

        ppu.tick()

        state.spritePatternHigh[0] shouldBe 0x77
    }

    "empty sprite slot clears low pattern byte" {
        state.scanline = 12
        state.dot = 269
        state.mask = 0x10
        state.spriteCount = 1

        state.spritePatternLow[1] = 0xAB

        ppu.tick()

        state.spritePatternLow[1] shouldBe 0x00
    }

    "empty sprite slot clears high pattern byte" {
        state.scanline = 12
        state.dot = 271
        state.mask = 0x10
        state.spriteCount = 1

        state.spritePatternHigh[1] = 0xAB

        ppu.tick()

        state.spritePatternHigh[1] shouldBe 0x00
    }

    "sprite pattern fetch does not happen before dot 257" {
        state.scanline = 0
        state.dot = 256
        state.mask = 0x10
        state.spriteCount = 1

        state.secondaryOam[0] = 0
        state.secondaryOam[1] = 0x02

        state.spriteXCounter[0] = 1
        state.spritePatternLow[0] = 0x11

        ppuBus.memory[0x0020] = 0xAA

        ppu.tick()

        state.spritePatternLow[0] shouldBe 0x11
    }

    "sprite pattern fetch does not happen after dot 320" {
        state.scanline = 0
        state.dot = 321
        state.mask = 0x10
        state.spriteCount = 1

        state.secondaryOam[0] = 0
        state.secondaryOam[1] = 0x02

        state.spritePatternLow[0] = 0x11
        ppuBus.memory[0x0020] = 0xAA

        ppu.tick()

        state.spritePatternLow[0] shouldBe 0x11
    }

    "sprite pattern fetch does not happen during VBlank" {
        state.scanline = 241
        state.dot = 261
        state.mask = 0x10
        state.spriteCount = 1

        state.secondaryOam[0] = 10
        state.secondaryOam[1] = 0x02

        state.spritePatternLow[0] = 0x11
        ppuBus.memory[0x0022] = 0xAA

        ppu.tick()

        state.spritePatternLow[0] shouldBe 0x11
    }

    "sprite pattern fetch does not happen when rendering is disabled" {
        state.scanline = 12
        state.dot = 261
        state.mask = 0x00
        state.spriteCount = 1

        state.secondaryOam[0] = 10
        state.secondaryOam[1] = 0x02

        state.spritePatternLow[0] = 0x11
        ppuBus.memory[0x0022] = 0xAA

        ppu.tick()

        state.spritePatternLow[0] shouldBe 0x11
    }

    "vertically flipped sprite fetches mirrored row" {
        state.scanline = 12
        state.dot = 261
        state.mask = 0x10
        state.spriteCount = 1

        state.secondaryOam[0] = 10
        state.secondaryOam[1] = 0x02
        state.secondaryOam[2] = 0x80

        // Normal row would be 2.
        // Vertical flip makes it 7 - 2 = 5.
        // Tile 2 -> $20 + row 5 -> $25.
        ppuBus.memory[0x0025] = 0xAB

        ppu.tick()

        state.spritePatternLow[0] shouldBe 0xAB
    }

    "vertically flipped sprite high plane uses mirrored row" {
        state.scanline = 12
        state.dot = 263
        state.mask = 0x10
        state.spriteCount = 1

        state.secondaryOam[0] = 10
        state.secondaryOam[1] = 0x02
        state.secondaryOam[2] = 0x80

        // $20 + row 5 + high plane 8 = $2D
        ppuBus.memory[0x002D] = 0xCD

        ppu.tick()

        state.spritePatternHigh[0] shouldBe 0xCD
    }

    "horizontally flipped sprite reverses low pattern byte" {
        state.scanline = 12
        state.dot = 261
        state.mask = 0x10
        state.spriteCount = 1

        state.secondaryOam[0] = 10
        state.secondaryOam[1] = 0x02
        state.secondaryOam[2] = 0x40

        // 10000001 reversed is still 10000001, so use an asymmetric value.
        ppuBus.memory[0x0022] = 0b10010000

        ppu.tick()

        state.spritePatternLow[0] shouldBe 0b00001001
    }

    "horizontally flipped sprite reverses high pattern byte" {
        state.scanline = 12
        state.dot = 263
        state.mask = 0x10
        state.spriteCount = 1

        state.secondaryOam[0] = 10
        state.secondaryOam[1] = 0x02
        state.secondaryOam[2] = 0x40

        ppuBus.memory[0x002A] = 0b11000010

        ppu.tick()

        state.spritePatternHigh[0] shouldBe 0b01000011
    }

    "sprite pattern byte is not reversed without horizontal flip" {
        state.scanline = 12
        state.dot = 261
        state.mask = 0x10
        state.spriteCount = 1

        state.secondaryOam[0] = 10
        state.secondaryOam[1] = 0x02
        state.secondaryOam[2] = 0x00

        ppuBus.memory[0x0022] = 0b10010000

        ppu.tick()

        state.spritePatternLow[0] shouldBe 0b10010000
    }

    "sprite X counter is loaded from secondary OAM" {
        state.scanline = 0
        state.dot = 264
        state.mask = 0x10
        state.spriteCount = 1

        state.secondaryOam[3] = 0x42

        ppu.tick()

        state.spriteXCounter[0] shouldBe 0x42
    }

    "sprite X counter is loaded for correct sprite slot" {
        state.scanline = 0
        state.dot = 272
        state.mask = 0x10
        state.spriteCount = 2

        val offset = 4
        state.secondaryOam[offset + 3] = 0x24

        ppu.tick()

        state.spriteXCounter[1] shouldBe 0x24
    }

    "empty sprite slot clears X counter" {
        state.scanline = 0
        state.dot = 272
        state.mask = 0x10
        state.spriteCount = 1

        state.spriteXCounter[1] = 0x42

        ppu.tick()

        state.spriteXCounter[1] shouldBe 0
    }

    "sprite X counter decrements on visible dot" {
        state.scanline = 0
        state.dot = 1
        state.mask = 0x10
        state.spriteCount = 1

        state.spriteXCounter[0] = 3

        ppu.tick()

        state.spriteXCounter[0] shouldBe 2
    }

    "sprite pattern does not shift while X counter is greater than zero" {
        state.scanline = 0
        state.dot = 1
        state.mask = 0x10
        state.spriteCount = 1

        state.spriteXCounter[0] = 3
        state.spritePatternLow[0] = 0b10000001
        state.spritePatternHigh[0] = 0b01000010

        ppu.tick()

        state.spritePatternLow[0] shouldBe 0b10000001
        state.spritePatternHigh[0] shouldBe 0b01000010
    }

    "sprite pattern shifts when X counter is zero" {
        state.scanline = 0
        state.dot = 1
        state.mask = 0x10
        state.spriteCount = 1

        state.spriteXCounter[0] = 0
        state.spritePatternLow[0] = 0b10000001
        state.spritePatternHigh[0] = 0b01000010

        ppu.tick()

        state.spritePatternLow[0] shouldBe 0b00000010
        state.spritePatternHigh[0] shouldBe 0b10000100
    }

    "sprite pattern stays 8 bit while shifting" {
        state.scanline = 0
        state.dot = 1
        state.mask = 0x10
        state.spriteCount = 1

        state.spriteXCounter[0] = 0
        state.spritePatternLow[0] = 0b10000000

        ppu.tick()

        state.spritePatternLow[0] shouldBe 0
    }

    "sprite shifting handles multiple selected sprites" {
        state.scanline = 0
        state.dot = 1
        state.mask = 0x10
        state.spriteCount = 2

        state.spriteXCounter[0] = 0
        state.spritePatternLow[0] = 0b10000000

        state.spriteXCounter[1] = 2
        state.spritePatternLow[1] = 0b01000000

        ppu.tick()

        state.spritePatternLow[0] shouldBe 0
        state.spriteXCounter[0] shouldBe 0

        state.spritePatternLow[1] shouldBe 0b01000000
        state.spriteXCounter[1] shouldBe 1
    }

    "sprite X counters do not advance outside visible dots" {
        state.scanline = 0
        state.dot = 257
        state.mask = 0x10
        state.spriteCount = 1

        state.spriteXCounter[0] = 3

        ppu.tick()

        state.spriteXCounter[0] shouldBe 3
    }

    "sprite patterns do not shift outside visible dots" {
        state.scanline = 0
        state.dot = 257
        state.mask = 0x10
        state.spriteCount = 1

        state.spriteXCounter[0] = 0
        state.spritePatternLow[0] = 0b10000000

        ppu.tick()

        state.spritePatternLow[0] shouldBe 0b10000000
    }

    "sprite shifting does not happen during VBlank" {
        state.scanline = 241
        state.dot = 1
        state.mask = 0x10
        state.spriteCount = 1

        state.spriteXCounter[0] = 0
        state.spritePatternLow[0] = 0b10000000

        ppu.tick()

        state.spritePatternLow[0] shouldBe 0b10000000
    }

    "sprite shifting does not happen when rendering is disabled" {
        state.scanline = 0
        state.dot = 1
        state.mask = 0x00
        state.spriteCount = 1

        state.spriteXCounter[0] = 0
        state.spritePatternLow[0] = 0b10000000

        ppu.tick()

        state.spritePatternLow[0] shouldBe 0b10000000
    }

    "transparent background and transparent sprite use universal background color" {
        state.scanline = 0
        state.dot = 9
        state.mask = 0x18

        ppuBus.memory[0x3F00] = 0x2A

        ppu.tick()

        frameBuffer.writtenPixels.single().color shouldBe 0x2A
    }

    "opaque background is rendered when sprite is transparent" {
        state.scanline = 0
        state.dot = 9
        state.mask = 0x18

        // Background pattern 1, palette 0.
        state.patternLowShift = 0x8000
        state.patternHighShift = 0x0000

        state.spriteCount = 1
        state.spriteXCounter[0] = 0
        state.spritePatternLow[0] = 0x00
        state.spritePatternHigh[0] = 0x00

        ppuBus.memory[0x3F01] = 0x11

        ppu.tick()

        frameBuffer.writtenPixels.single().color shouldBe 0x11
    }

    "opaque sprite is rendered when background is transparent" {
        state.scanline = 0
        state.dot = 9
        state.mask = 0x18

        state.spriteCount = 1
        state.spriteXCounter[0] = 0

        // Sprite pattern 1.
        state.spritePatternLow[0] = 0x80
        state.spritePatternHigh[0] = 0x00

        // Sprite palette 0.
        state.secondaryOam[2] = 0x00

        ppuBus.memory[0x3F11] = 0x22

        ppu.tick()

        frameBuffer.writtenPixels.single().color shouldBe 0x22
    }

    "sprite in front wins over opaque background" {
        state.scanline = 0
        state.dot = 9
        state.mask = 0x18

        // Background pattern 1.
        state.patternLowShift = 0x8000
        state.patternHighShift = 0x0000

        state.spriteCount = 1
        state.spriteXCounter[0] = 0

        // Sprite pattern 2.
        state.spritePatternLow[0] = 0x00
        state.spritePatternHigh[0] = 0x80

        // Priority bit clear -> sprite in front.
        state.secondaryOam[2] = 0x00

        ppuBus.memory[0x3F01] = 0x11
        ppuBus.memory[0x3F12] = 0x22

        ppu.tick()

        frameBuffer.writtenPixels.single().color shouldBe 0x22
    }

    "sprite behind background loses to opaque background" {
        state.scanline = 0
        state.dot = 9
        state.mask = 0x18

        // Background pattern 1.
        state.patternLowShift = 0x8000
        state.patternHighShift = 0x0000

        state.spriteCount = 1
        state.spriteXCounter[0] = 0

        // Sprite pattern 2.
        state.spritePatternLow[0] = 0x00
        state.spritePatternHigh[0] = 0x80

        // Bit 5 -> behind background.
        state.spriteAttributes[0] = 0x20

        ppuBus.memory[0x3F01] = 0x11
        ppuBus.memory[0x3F12] = 0x22

        ppu.tick()

        frameBuffer.writtenPixels.single().color shouldBe 0x11
    }

    "sprite priority does not hide sprite behind transparent background" {
        state.scanline = 0
        state.dot = 9
        state.mask = 0x18

        state.spriteCount = 1
        state.spriteXCounter[0] = 0

        state.spritePatternLow[0] = 0x80
        state.spritePatternHigh[0] = 0x00

        // Behind-background flag is set, but background is transparent.
        state.secondaryOam[2] = 0x20

        ppuBus.memory[0x3F11] = 0x22

        ppu.tick()

        frameBuffer.writtenPixels.single().color shouldBe 0x22
    }

    "sprite palette selects sprite palette RAM entry" {
        state.scanline = 0
        state.dot = 9
        state.mask = 0x18

        state.spriteCount = 1
        state.spriteXCounter[0] = 0

        // Pattern 3.
        state.spritePatternLow[0] = 0x80
        state.spritePatternHigh[0] = 0x80

        // Palette 2.
        state.spriteAttributes[0] = 0x02

        // $3F10 + palette 2 * 4 + pattern 3
        // = $3F1B
        ppuBus.memory[0x3F1B] = 0x33

        ppu.tick()

        frameBuffer.writtenPixels.single().color shouldBe 0x33
    }

    "sprite is ignored when sprite rendering is disabled" {
        state.scanline = 0
        state.dot = 9

        // Background only.
        state.mask = 0x08

        state.patternLowShift = 0x8000

        state.spriteCount = 1
        state.spriteXCounter[0] = 0
        state.spritePatternLow[0] = 0x80

        ppuBus.memory[0x3F01] = 0x11
        ppuBus.memory[0x3F11] = 0x22

        ppu.tick()

        frameBuffer.writtenPixels.single().color shouldBe 0x11
    }

    "transparent sprite does not affect background pixel" {
        state.scanline = 0
        state.dot = 9
        state.mask = 0x18

        // Background pattern 1.
        state.patternLowShift = 0x8000

        state.spriteCount = 1
        state.spriteXCounter[0] = 0
        state.spritePatternLow[0] = 0x00
        state.spritePatternHigh[0] = 0x00

        ppuBus.memory[0x3F01] = 0x11

        ppu.tick()

        frameBuffer.writtenPixels.single().color shouldBe 0x11
    }

    "sprite pattern low bit selects sprite palette color" {
        state.scanline = 0
        state.dot = 9
        state.mask = 0x10

        state.spriteCount = 1
        state.spriteXCounter[0] = 0
        state.spritePatternLow[0] = 0x80
        state.spritePatternHigh[0] = 0x00
        state.secondaryOam[2] = 0x00

        ppuBus.memory[0x3F11] = 0x21

        ppu.tick()

        frameBuffer.writtenPixels.single().color shouldBe 0x21
    }

    "sprite pattern high bit selects sprite palette color" {
        state.scanline = 0
        state.dot = 9
        state.mask = 0x10

        state.spriteCount = 1
        state.spriteXCounter[0] = 0
        state.spritePatternLow[0] = 0x00
        state.spritePatternHigh[0] = 0x80
        state.secondaryOam[2] = 0x00

        ppuBus.memory[0x3F12] = 0x22

        ppu.tick()

        frameBuffer.writtenPixels.single().color shouldBe 0x22
    }

    "sprite pattern combines both pattern bits" {
        state.scanline = 0
        state.dot = 9
        state.mask = 0x10

        state.spriteCount = 1
        state.spriteXCounter[0] = 0
        state.spritePatternLow[0] = 0x80
        state.spritePatternHigh[0] = 0x80
        state.secondaryOam[2] = 0x00

        ppuBus.memory[0x3F13] = 0x23

        ppu.tick()

        frameBuffer.writtenPixels.single().color shouldBe 0x23
    }

    "transparent first sprite allows following sprite to render" {
        state.scanline = 0
        state.dot = 9
        state.mask = 0x10
        state.spriteCount = 2

        state.spriteXCounter[0] = 0
        state.spritePatternLow[0] = 0x00
        state.spritePatternHigh[0] = 0x00

        state.spriteXCounter[1] = 0
        state.spritePatternLow[1] = 0x80
        state.spritePatternHigh[1] = 0x00
        state.secondaryOam[6] = 0x00

        ppuBus.memory[0x3F11] = 0x31

        ppu.tick()

        frameBuffer.writtenPixels.single().color shouldBe 0x31
    }

    "first opaque sprite has priority over later sprites" {
        state.scanline = 0
        state.dot = 9
        state.mask = 0x10
        state.spriteCount = 2

        state.spriteXCounter[0] = 0
        state.spritePatternLow[0] = 0x80
        state.secondaryOam[2] = 0x00

        state.spriteXCounter[1] = 0
        state.spritePatternHigh[1] = 0x80
        state.secondaryOam[6] = 0x00

        ppuBus.memory[0x3F11] = 0x11
        ppuBus.memory[0x3F12] = 0x22

        ppu.tick()

        frameBuffer.writtenPixels.single().color shouldBe 0x11
    }

    "sprite attributes select sprite palette" {
        state.scanline = 0
        state.dot = 9
        state.mask = 0x10

        state.spriteCount = 1
        state.spriteXCounter[0] = 0

        // Pattern 1.
        state.spritePatternLow[0] = 0x80

        // Palette 3.
        state.spriteAttributes[0] = 0x03

        // $3F10 + (3 * 4) + 1 = $3F1D
        ppuBus.memory[0x3F1D] = 0x2A

        ppu.tick()

        frameBuffer.writtenPixels.single().color shouldBe 0x2A
    }

    "sprite is hidden in leftmost 8 pixels by default" {
        state.scanline = 0
        state.dot = 1

        // Sprite rendering enabled, leftmost sprite rendering disabled.
        state.mask = 0x10

        state.spriteCount = 1
        state.spriteXCounter[0] = 0
        state.spritePatternLow[0] = 0x80

        ppuBus.memory[0x3F00] = 0x10
        ppuBus.memory[0x3F11] = 0x20

        ppu.tick()

        frameBuffer.writtenPixels.single().color shouldBe 0x10
    }

    "sprite can render in leftmost 8 pixels when enabled" {
        state.scanline = 0
        state.dot = 1

        // Sprite rendering + sprite left-edge rendering.
        state.mask = 0x14

        state.spriteCount = 1
        state.spriteXCounter[0] = 0
        state.spritePatternLow[0] = 0x80
        state.spritePatternHigh[0] = 0x00

        // No palette offset, no priority, no flipping.
        state.secondaryOam[2] = 0x00

        ppuBus.memory[0x3F11] = 0x20

        ppu.tick()

        frameBuffer.writtenPixels.single().color shouldBe 0x20
    }

    "sprite zero hit is set when sprite zero overlaps opaque background" {
        state.scanline = 10
        state.dot = 20
        state.mask = 0x18

        // Opaque background.
        state.patternLowShift = 0x8000

        // Opaque sprite 0.
        state.spriteCount = 1
        state.spriteZeroSelected = true
        state.spriteXCounter[0] = 0
        state.spritePatternLow[0] = 0x80

        ppu.tick()

        state.status and 0x40 shouldBe 0x40
    }

    "sprite zero hit is not set when background is transparent" {
        state.scanline = 10
        state.dot = 20
        state.mask = 0x18

        state.spriteCount = 1
        state.spriteZeroSelected = true
        state.spriteXCounter[0] = 0
        state.spritePatternLow[0] = 0x80

        ppu.tick()

        state.status and 0x40 shouldBe 0x00
    }

    "sprite zero hit is not set when sprite zero is transparent" {
        state.scanline = 10
        state.dot = 20
        state.mask = 0x18

        state.patternLowShift = 0x8000

        state.spriteCount = 1
        state.spriteZeroSelected = true
        state.spriteXCounter[0] = 0

        ppu.tick()

        state.status and 0x40 shouldBe 0x00
    }

    "sprite zero hit is not set for non-zero sprite" {
        state.scanline = 10
        state.dot = 20
        state.mask = 0x18

        state.patternLowShift = 0x8000

        state.spriteCount = 1
        state.spriteZeroSelected = false
        state.spriteXCounter[0] = 0
        state.spritePatternLow[0] = 0x80

        ppu.tick()

        state.status and 0x40 shouldBe 0x00
    }

    "sprite zero hit is not set at x 255" {
        state.scanline = 10
        state.dot = 256
        state.mask = 0x18

        state.patternLowShift = 0x8000

        state.spriteCount = 1
        state.spriteZeroSelected = true
        state.spriteXCounter[0] = 0
        state.spritePatternLow[0] = 0x80

        ppu.tick()

        state.status and 0x40 shouldBe 0x00
    }

    "sprite zero hit respects background left-edge masking" {
        state.scanline = 0
        state.dot = 1

        // Background + sprite enabled, sprite left-edge enabled,
        // background left-edge disabled.
        state.mask = 0x1C

        state.patternLowShift = 0x8000

        state.spriteCount = 1
        state.spriteZeroSelected = true
        state.spriteXCounter[0] = 0
        state.spritePatternLow[0] = 0x80

        ppu.tick()

        state.status and 0x40 shouldBe 0x00
    }

    "sprite zero hit respects sprite left-edge masking" {
        state.scanline = 0
        state.dot = 1

        // Background + sprite enabled, background left-edge enabled,
        // sprite left-edge disabled.
        state.mask = 0x1A

        state.patternLowShift = 0x8000

        state.spriteCount = 1
        state.spriteZeroSelected = true
        state.spriteXCounter[0] = 0
        state.spritePatternLow[0] = 0x80

        ppu.tick()

        state.status and 0x40 shouldBe 0x00
    }

    "sprite zero hit can occur in leftmost 8 pixels when both are enabled" {
        state.scanline = 0
        state.dot = 1

        // Background + sprite + both left-edge enables.
        state.mask = 0x1E

        state.patternLowShift = 0x8000

        state.spriteCount = 1
        state.spriteZeroSelected = true
        state.spriteXCounter[0] = 0
        state.spritePatternLow[0] = 0x80

        ppu.tick()

        state.status and 0x40 shouldBe 0x40
    }

    "pre-render sprite fetch does not promote stale evaluated sprites" {
        state.scanline = 261
        state.dot = 257
        state.mask = 0x10

        state.evaluatedSpriteCount = 3
        state.evaluatedSpriteZeroSelected = true

        ppu.tick()

        state.spriteCount shouldBe 0
        state.spriteZeroSelected shouldBe false
    }

    "sprite overflow is set when ninth sprite is in range" {
        state.scanline = 10
        state.dot = 65
        state.mask = 0x10

        state.evaluatedSpriteCount = 8
        state.secondaryOamIndex = 32

        state.oam[0] = 10

        ppu.tick()

        state.status and 0x20 shouldBe 0x20
    }

    "sprite overflow is not set when ninth sprite is out of range" {
        state.scanline = 10
        state.dot = 65
        state.mask = 0x10

        state.evaluatedSpriteCount = 8
        state.secondaryOamIndex = 32

        state.oam[0] = 20

        ppu.tick()

        state.status and 0x20 shouldBe 0x00
    }

    "ninth sprite is not copied to secondary OAM" {
        state.scanline = 10
        state.dot = 65
        state.mask = 0x10

        state.evaluatedSpriteCount = 8
        state.secondaryOamIndex = 32

        state.oam[0] = 10
        state.oam[1] = 0x42
        state.oam[2] = 0x03
        state.oam[3] = 0x80

        val secondaryOamBefore = state.secondaryOam.copyOf()

        ppu.tick()

        state.secondaryOam.toList() shouldBe secondaryOamBefore.toList()
        state.evaluatedSpriteCount shouldBe 8
        state.secondaryOamIndex shouldBe 32
    }

    "sprite overflow remains set after later sprite evaluation" {
        state.scanline = 10
        state.dot = 65
        state.mask = 0x10

        state.status = state.status or 0x20

        state.evaluatedSpriteCount = 8
        state.spriteEvaluationIndex = 1

        // Out of range.
        state.oam[4] = 20

        ppu.tick()

        state.status and 0x20 shouldBe 0x20
    }

    "sprite overflow is cleared at pre-render dot 1" {
        state.scanline = 261
        state.dot = 1
        state.status = state.status or 0x20

        ppu.tick()

        state.status and 0x20 shouldBe 0x00
    }

    "8x16 sprite evaluation includes rows 8 through 15" {
        state.scanline = 15
        state.dot = 65
        state.mask = 0x10

        // 8x16 sprite mode.
        state.control = 0x20

        state.oam[0] = 0

        ppu.tick()

        state.evaluatedSpriteCount shouldBe 1
    }

    "8x8 sprite evaluation excludes rows 8 through 15" {
        state.scanline = 15
        state.dot = 65
        state.mask = 0x10

        // 8x8 sprite mode.
        state.control = 0x00

        state.oam[0] = 0

        ppu.tick()

        state.evaluatedSpriteCount shouldBe 0
    }

    "8x16 sprite uses tile bit zero to select pattern table zero" {
        state.scanline = 5
        state.dot = 261
        state.mask = 0x10
        state.control = 0x20

        state.spriteCount = 1

        state.secondaryOam[0] = 0
        state.secondaryOam[1] = 0x02
        state.secondaryOam[2] = 0x00

        // Tile $02, row 5:
        // $0000 + ($02 * 16) + 5 = $0025
        ppuBus.memory[0x0025] = 0xAA

        ppu.tick()

        state.spritePatternLow[0] shouldBe 0xAA
    }

    "8x16 sprite uses tile bit zero to select pattern table one" {
        state.scanline = 5
        state.dot = 261
        state.mask = 0x10
        state.control = 0x20

        state.spriteCount = 1

        state.secondaryOam[0] = 0
        state.secondaryOam[1] = 0x03
        state.secondaryOam[2] = 0x00

        // Tile $03 selects table $1000,
        // but the tile pair starts at tile $02.
        //
        // $1000 + ($02 * 16) + 5 = $1025
        ppuBus.memory[0x1025] = 0xAA

        ppu.tick()

        state.spritePatternLow[0] shouldBe 0xAA
    }

    "8x16 sprite uses second tile for bottom eight rows" {
        state.scanline = 10
        state.dot = 261
        state.mask = 0x10
        state.control = 0x20

        state.spriteCount = 1

        state.secondaryOam[0] = 0
        state.secondaryOam[1] = 0x03
        state.secondaryOam[2] = 0x00

        // Row 10 is row 2 of the bottom tile.
        //
        // Table $1000
        // top tile = $02
        // bottom tile = $03
        //
        // $1000 + ($03 * 16) + 2 = $1032
        ppuBus.memory[0x1032] = 0xBB

        ppu.tick()

        state.spritePatternLow[0] shouldBe 0xBB
    }

    "8x16 sprite high plane uses correct bottom tile address" {
        state.scanline = 10
        state.dot = 263
        state.mask = 0x10
        state.control = 0x20

        state.spriteCount = 1

        state.secondaryOam[0] = 0
        state.secondaryOam[1] = 0x03
        state.secondaryOam[2] = 0x00

        // Bottom tile row 2 + high plane:
        // $1032 + 8 = $103A
        ppuBus.memory[0x103A] = 0xCC

        ppu.tick()

        state.spritePatternHigh[0] shouldBe 0xCC
    }

    "8x16 vertically flipped sprite flips across all sixteen rows" {
        state.scanline = 2
        state.dot = 261
        state.mask = 0x10
        state.control = 0x20

        state.spriteCount = 1

        state.secondaryOam[0] = 0
        state.secondaryOam[1] = 0x03

        // Vertical flip.
        state.secondaryOam[2] = 0x80

        // Normal row 2 becomes row 13.
        // Row 13 = row 5 of the bottom tile ($03).
        //
        // $1000 + ($03 * 16) + 5 = $1035
        ppuBus.memory[0x1035] = 0xDD

        ppu.tick()

        state.spritePatternLow[0] shouldBe 0xDD
    }

    "8x16 vertically flipped bottom row uses top tile" {
        state.scanline = 15
        state.dot = 261
        state.mask = 0x10
        state.control = 0x20

        state.spriteCount = 1

        state.secondaryOam[0] = 0
        state.secondaryOam[1] = 0x03
        state.secondaryOam[2] = 0x80

        // Normal row 15 becomes row 0.
        // That is row 0 of the top tile ($02).
        //
        // $1000 + ($02 * 16) = $1020
        ppuBus.memory[0x1020] = 0xEE

        ppu.tick()

        state.spritePatternLow[0] shouldBe 0xEE
    }

    "8x16 sprite row 7 uses top tile" {
        state.scanline = 7
        state.dot = 261
        state.mask = 0x10
        state.control = 0x20

        state.spriteCount = 1

        state.secondaryOam[0] = 0
        state.secondaryOam[1] = 0x03
        state.secondaryOam[2] = 0x00

        // Table $1000, top tile $02, row 7.
        // $1000 + ($02 * 16) + 7 = $1027
        ppuBus.memory[0x1027] = 0xAA

        ppu.tick()

        state.spritePatternLow[0] shouldBe 0xAA
    }

    "8x16 sprite row 8 uses bottom tile" {
        state.scanline = 8
        state.dot = 261
        state.mask = 0x10
        state.control = 0x20

        state.spriteCount = 1

        state.secondaryOam[0] = 0
        state.secondaryOam[1] = 0x03
        state.secondaryOam[2] = 0x00

        // Table $1000, bottom tile $03, row 0.
        // $1000 + ($03 * 16) = $1030
        ppuBus.memory[0x1030] = 0xBB

        ppu.tick()

        state.spritePatternLow[0] shouldBe 0xBB
    }

    "palette color is limited to six bits" {
        state.scanline = 0
        state.dot = 9
        state.mask = 0x08

        state.patternLowShift = 0x8000

        ppuBus.memory[0x3F01] = 0xE5

        ppu.tick()

        frameBuffer.writtenPixels.single().color shouldBe 0x25
    }

    "grayscale masks palette color to luminance bits" {
        state.scanline = 0
        state.dot = 9

        // Background enabled + grayscale enabled.
        state.mask = 0x09

        state.patternLowShift = 0x8000

        ppuBus.memory[0x3F01] = 0x2D

        ppu.tick()

        frameBuffer.writtenPixels.single().color shouldBe 0x20
    }

    "grayscale also applies to sprite colors" {
        state.scanline = 0
        state.dot = 9

        // Sprite enabled + grayscale enabled.
        state.mask = 0x11

        state.spriteCount = 1
        state.spriteXCounter[0] = 0
        state.spritePatternLow[0] = 0x80
        state.spriteAttributes[0] = 0x00

        ppuBus.memory[0x3F11] = 0x3D

        ppu.tick()

        frameBuffer.writtenPixels.single().color shouldBe 0x30
    }

    "writing PPU register updates IO latch" {
        ppu.cpuWriteRegister(0x2000, 0x42)

        state.ioLatch shouldBe 0x42
    }

    "writing read-only PPUSTATUS still updates IO latch" {
        ppu.cpuWriteRegister(0x2002, 0x57)

        state.ioLatch shouldBe 0x57
    }

    "reading write-only register returns IO latch" {
        ppu.cpuWriteRegister(0x2000, 0x42)

        ppu.cpuReadRegister(0x2001) shouldBe 0x42
    }

    "PPUSTATUS lower five bits come from IO latch" {
        state.status = 0xA0
        state.ioLatch = 0x13

        val value = ppu.cpuReadRegister(0x2002)

        value shouldBe 0xB3
    }

    "PPUSTATUS ignores lower five bits stored in status state" {
        state.status = 0xBF
        state.ioLatch = 0x04

        val value = ppu.cpuReadRegister(0x2002)

        value shouldBe 0xA4
    }

    "reading PPUSTATUS refreshes IO latch with returned value" {
        state.status = 0x80
        state.ioLatch = 0x15

        ppu.cpuReadRegister(0x2002)

        state.ioLatch shouldBe 0x95
    }

    "reading OAMDATA refreshes IO latch" {
        state.oamAddress = 0x20
        state.oam[0x20] = 0x67
        state.ioLatch = 0x11

        val value = ppu.cpuReadRegister(0x2004)

        value shouldBe 0x67
        state.ioLatch shouldBe 0x67
    }

    "buffered PPUDATA read refreshes IO latch with returned value" {
        state.v = 0x2000
        state.dataBuffer = 0x42

        ppuBus.memory[0x2000] = 0x55

        val value = ppu.cpuReadRegister(0x2007)

        value shouldBe 0x42
        state.ioLatch shouldBe 0x42
    }

    "palette PPUDATA read keeps upper two bits from IO latch" {
        state.v = 0x3F00
        state.ioLatch = 0xC0

        ppuBus.memory[0x3F00] = 0x25

        val value = ppu.cpuReadRegister(0x2007)

        value shouldBe 0xE5
        state.ioLatch shouldBe 0xE5
    }

    "palette PPUDATA read replaces previous lower six latch bits" {
        state.v = 0x3F00
        state.ioLatch = 0xD7

        ppuBus.memory[0x3F00] = 0x0A

        val value = ppu.cpuReadRegister(0x2007)

        value shouldBe 0xCA
        state.ioLatch shouldBe 0xCA
    }

    "reading PPUSTATUS one dot before VBlank suppresses VBlank" {
        state.scanline = 241
        state.dot = 0

        ppu.cpuReadRegister(0x2002)

        // Process dot 0.
        ppu.tick()

        // Process dot 1, where VBlank would normally begin.
        ppu.tick()

        state.status and 0x80 shouldBe 0x00
    }

    "reading PPUSTATUS at VBlank start suppresses VBlank" {
        state.scanline = 241
        state.dot = 1

        ppu.cpuReadRegister(0x2002)

        ppu.tick()

        state.status and 0x80 shouldBe 0x00
    }

    "PPUSTATUS read before suppression window does not suppress VBlank" {
        state.scanline = 240
        state.dot = 340

        ppu.cpuReadRegister(0x2002)

        // Advance to scanline 241 dot 0.
        ppu.tick()

        // Dot 0.
        ppu.tick()

        // Dot 1.
        ppu.tick()

        state.status and 0x80 shouldBe 0x80
    }

    "VBlank suppression also suppresses NMI" {
        var nmiCount = 0

        ppu = PpuNes(
            state = state,
            ppuBus = ppuBus,
            onMapperScanline = {},
            frameBuffer = frameBuffer,
        )
        ppu.onNmi = { nmiCount++ }

        state.control = 0x80
        state.scanline = 241
        state.dot = 0

        ppu.cpuReadRegister(0x2002)

        ppu.tick()
        ppu.tick()

        nmiCount shouldBe 0
    }

    "VBlank and NMI occur normally without PPUSTATUS read" {
        var nmiCount = 0

        ppu = PpuNes(
            state = state,
            ppuBus = ppuBus,
            onMapperScanline = {},
            frameBuffer = frameBuffer,
        )
        ppu.onNmi = { nmiCount++ }

        state.control = 0x80
        state.scanline = 241
        state.dot = 1

        ppu.tick()

        state.status and 0x80 shouldBe 0x80
        nmiCount shouldBe 1
    }

    "VBlank suppression only applies to one frame" {
        state.scanline = 241
        state.dot = 0

        ppu.cpuReadRegister(0x2002)

        ppu.tick()
        ppu.tick()

        state.status and 0x80 shouldBe 0x00
        state.suppressVblank shouldBe false

        state.scanline = 241
        state.dot = 1

        ppu.tick()

        state.status and 0x80 shouldBe 0x80
    }

    "PPUDATA increments VRAM address by one outside rendering" {
        state.v = 0x2000
        state.control = 0x00
        state.scanline = 241
        state.dot = 10

        ppu.cpuReadRegister(0x2007)

        state.v shouldBe 0x2001
    }

    "PPUDATA increments VRAM address by thirty two outside rendering" {
        state.v = 0x2000
        state.control = 0x04
        state.scanline = 241
        state.dot = 10

        ppu.cpuReadRegister(0x2007)

        state.v shouldBe 0x2020
    }

    "PPUDATA increments coarse X during rendering" {
        state.scanline = 10
        state.dot = 100
        state.mask = 0x08

        state.v = 0x0000

        ppu.cpuReadRegister(0x2007)

        state.v and 0x001F shouldBe 1
    }

    "PPUDATA wraps coarse X and switches horizontal nametable during rendering" {
        state.scanline = 10
        state.dot = 100
        state.mask = 0x08

        // Coarse X = 31.
        state.v = 0x001F

        ppu.cpuReadRegister(0x2007)

        state.v and 0x001F shouldBe 0
        state.v and 0x0400 shouldBe 0x0400
    }

    "PPUDATA increments fine Y during rendering" {
        state.scanline = 10
        state.dot = 100
        state.mask = 0x08

        state.v = 0x0000

        ppu.cpuReadRegister(0x2007)

        state.v and 0x7000 shouldBe 0x1000
    }

    "PPUDATA rendering increment ignores control increment setting" {
        state.scanline = 10
        state.dot = 100
        state.mask = 0x08

        // Normally selects +32.
        state.control = 0x04

        state.v = 0x0000

        ppu.cpuReadRegister(0x2007)

        // Rendering rules apply instead:
        // coarse X +1 and fine Y +1.
        state.v shouldBe 0x1001
    }

    "PPUDATA write uses rendering VRAM increment behavior" {
        state.scanline = 10
        state.dot = 100
        state.mask = 0x08

        state.v = 0x0000

        ppu.cpuWriteRegister(0x2007, 0x42)

        state.v shouldBe 0x1001
    }

    "PPUDATA uses rendering increment on pre-render scanline" {
        state.scanline = 261
        state.dot = 100
        state.mask = 0x08

        state.v = 0x0000

        ppu.cpuReadRegister(0x2007)

        state.v shouldBe 0x1001
    }

    "PPUDATA uses normal increment when rendering is disabled" {
        state.scanline = 10
        state.dot = 100
        state.mask = 0x00
        state.control = 0x00

        state.v = 0x2000

        ppu.cpuReadRegister(0x2007)

        state.v shouldBe 0x2001
    }

    "writing OAMDATA outside rendering updates OAM" {
        state.scanline = 241
        state.dot = 10
        state.oamAddress = 0x20

        ppu.cpuWriteRegister(0x2004, 0x42)

        state.oam[0x20] shouldBe 0x42
        state.oamAddress shouldBe 0x21
    }

    "writing OAMDATA during rendering does not update OAM" {
        state.scanline = 10
        state.dot = 100
        state.mask = 0x08
        state.oamAddress = 0x20

        state.oam[0x20] = 0x11

        ppu.cpuWriteRegister(0x2004, 0x42)

        state.oam[0x20] shouldBe 0x11
    }

    "writing OAMDATA during rendering does not increment OAM address" {
        state.scanline = 10
        state.dot = 100
        state.mask = 0x08
        state.oamAddress = 0x20

        ppu.cpuWriteRegister(0x2004, 0x42)

        state.oamAddress shouldBe 0x20
    }

    "reading OAMDATA outside rendering reads OAM address" {
        state.scanline = 241
        state.dot = 10
        state.oamAddress = 0x20

        state.oam[0x20] = 0x42
        state.oamDataBus = 0x11

        ppu.cpuReadRegister(0x2004) shouldBe 0x42
    }

    "reading OAMDATA during rendering exposes internal OAM bus" {
        state.scanline = 10
        state.dot = 100
        state.mask = 0x08

        state.oamAddress = 0x20
        state.oam[0x20] = 0x42

        state.oamDataBus = 0x55

        ppu.cpuReadRegister(0x2004) shouldBe 0x55
    }

    "secondary OAM clearing exposes FF on OAM data bus" {
        state.scanline = 10
        state.dot = 1
        state.mask = 0x08

        state.oamDataBus = 0x42

        ppu.tick()

        state.oamDataBus shouldBe 0xFF
    }

    "sprite evaluation exposes evaluated OAM byte on OAM data bus" {
        state.scanline = 10
        state.dot = 65
        state.mask = 0x10

        state.spriteEvaluationIndex = 0
        state.oam[0] = 0x07

        ppu.tick()

        state.oamDataBus shouldBe 0x07
    }

    "increments frame counter when scanline wraps to zero" {
        state.scanline = 261
        state.dot = 340

        val initialFrame = ppu.frame

        ppu.tick()

        ppu.frame shouldBe initialFrame + 1
        state.scanline shouldBe 0
        state.dot shouldBe 0
    }

})
