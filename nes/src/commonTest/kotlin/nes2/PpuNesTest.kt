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
    var nmiRequested = false

    beforeTest {
        state = PpuState()
        ppuBus = FakePpuBus()
        nmiRequested = false
        ppu = PpuNes(
            state = state,
            ppuBus = ppuBus,
            onNmi = { nmiRequested = true }
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
        state.v = 0x0123

        ppuBus.memory[0x2123] = 0x42

        ppu.tick()

        state.nametableByte shouldBe 0x42
    }

    "background fetch reads attribute byte at dot 3" {
        state.scanline = 0
        state.dot = 3
        state.v = 0x0000

        ppuBus.memory[0x23C0] = 0xAB

        ppu.tick()

        state.attributeByte shouldBe 0xAB
    }

    "background fetch reads low pattern byte at dot 5" {
        state.scanline = 0
        state.dot = 5
        state.nametableByte = 0x02
        state.v = 0x0000

        // tile 2 starts at $0020
        ppuBus.memory[0x0020] = 0xAA

        ppu.tick()

        state.patternLowByte shouldBe 0xAA
    }

    "background pattern fetch can use pattern table 1" {
        state.scanline = 0
        state.dot = 5
        state.control = 0x10
        state.nametableByte = 0x02

        ppuBus.memory[0x1020] = 0xAA

        ppu.tick()

        state.patternLowByte shouldBe 0xAA
    }

    "background fetch reads high pattern byte at dot 7" {
        state.scanline = 0
        state.dot = 7
        state.nametableByte = 0x02
        state.v = 0x0000

        // tile 2 = $0020, high plane = +8
        ppuBus.memory[0x0028] = 0x55

        ppu.tick()

        state.patternHighByte shouldBe 0x55
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

        ppuBus.memory[0x1028] = 0x55

        ppu.tick()

        state.patternHighByte shouldBe 0x55
    }

    "background fetch increments coarse X at dot 8" {
        state.scanline = 0
        state.dot = 8
        state.v = 0x0005

        ppu.tick()

        state.v and 0x001F shouldBe 0x06
    }

    "coarse X wraps from 31 to 0" {
        state.scanline = 0
        state.dot = 8
        state.v = 0x001F

        ppu.tick()

        state.v and 0x001F shouldBe 0x00
    }

    "coarse X wrap switches horizontal nametable" {
        state.scanline = 0
        state.dot = 8
        state.v = 0x001F

        ppu.tick()

        state.v and 0x0400 shouldBe 0x0400
    }

    "coarse X wrap toggles horizontal nametable" {
        state.scanline = 0
        state.dot = 8
        state.v = 0x041F

        ppu.tick()

        state.v and 0x0400 shouldBe 0x0000
    }

    "incrementing coarse X preserves other VRAM address bits" {
        state.scanline = 0
        state.dot = 8
        state.v = 0x7125

        ppu.tick()

        state.v shouldBe 0x7126
    }

    "vertical scroll increments fine Y at dot 256" {
        state.scanline = 0
        state.dot = 256
        state.v = 0x0000

        ppu.tick()

        (state.v shr 12) and 0x07 shouldBe 1
    }

    "vertical scroll increments coarse Y when fine Y wraps" {
        state.scanline = 0
        state.dot = 256

        // fine Y = 7, coarse Y = 5
        state.v = (7 shl 12) or (5 shl 5)

        ppu.tick()

        (state.v shr 12) and 0x07 shouldBe 0
        (state.v shr 5) and 0x1F shouldBe 6
    }

    "coarse Y 29 wraps to 0 and switches vertical nametable" {
        state.scanline = 0
        state.dot = 256

        state.v = (7 shl 12) or (29 shl 5)

        ppu.tick()

        (state.v shr 12) and 0x07 shouldBe 0
        (state.v shr 5) and 0x1F shouldBe 0
        state.v and 0x0800 shouldBe 0x0800
    }

    "coarse Y 29 toggles vertical nametable" {
        state.scanline = 0
        state.dot = 256

        state.v = 0x0800 or (7 shl 12) or (29 shl 5)

        ppu.tick()

        state.v and 0x0800 shouldBe 0x0000
    }

    "coarse Y 31 wraps to 0 without switching vertical nametable" {
        state.scanline = 0
        state.dot = 256

        state.v = 0x0800 or (7 shl 12) or (31 shl 5)

        ppu.tick()

        (state.v shr 5) and 0x1F shouldBe 0
        state.v and 0x0800 shouldBe 0x0800
    }

    "dot 257 copies coarse X from temporary VRAM address" {
        state.scanline = 0
        state.dot = 257

        state.v = 0x0000
        state.t = 0x0015

        ppu.tick()

        state.v and 0x001F shouldBe 0x15
    }

    "dot 257 copies horizontal nametable from temporary VRAM address" {
        state.scanline = 0
        state.dot = 257

        state.v = 0x0000
        state.t = 0x0400

        ppu.tick()

        state.v and 0x0400 shouldBe 0x0400
    }

    "dot 257 can clear horizontal scroll bits" {
        state.scanline = 0
        state.dot = 257

        state.v = 0x041F
        state.t = 0x0000

        ppu.tick()

        state.v and 0x041F shouldBe 0x0000
    }

    "dot 257 preserves vertical scroll bits" {
        state.scanline = 0
        state.dot = 257

        state.v = 0x7380
        state.t = 0x0415

        ppu.tick()

        state.v and 0x7BE0 shouldBe 0x7380
    }

    "pre-render scanline copies coarse Y from temporary VRAM address" {
        state.scanline = 261
        state.dot = 280

        state.v = 0x0000
        state.t = 5 shl 5

        ppu.tick()

        (state.v shr 5) and 0x1F shouldBe 5
    }

    "pre-render scanline copies fine Y from temporary VRAM address" {
        state.scanline = 261
        state.dot = 280

        state.v = 0x0000
        state.t = 6 shl 12

        ppu.tick()

        (state.v shr 12) and 0x07 shouldBe 6
    }

    "pre-render scanline copies vertical nametable from temporary VRAM address" {
        state.scanline = 261
        state.dot = 280

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

        ppu.tick()

        state.v and 0x7BE0 shouldBe 0x7380
    }

})