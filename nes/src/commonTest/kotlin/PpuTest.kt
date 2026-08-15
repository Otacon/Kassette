import kotlin.test.*
import nes.cartridge.Cartridge
import nes.cartridge.CartridgeSocket
import nes.cartridge.Mapper
import nes.cartridge.Mapper0
import nes.cartridge.Mirroring
import nes.ConsoleRegion
import nes.ppu.Palette
import nes.ppu.Ppu
import nes.ppu.PpuBus
import nes.util.toUnsignedInt

class PpuTest {
    @Test
    fun `four-screen mirroring keeps all nametables independent`() {
        val prg = ByteArray(16 * 1024)
        val chr = ByteArray(8 * 1024)
        val socket = CartridgeSocket()
        socket.insert(
            Cartridge(
                mirroring = Mirroring.FOUR_SCREEN,
                prgRom = prg,
                chr = chr,
                isChrRam = true,
                trainerPresent = false,
                mapper = Mapper0(prg, chr, isChrRam = true),
            ),
        )
        val bus = PpuBus(socket)

        bus.write(0x2000, 1)
        bus.write(0x2400, 2)
        bus.write(0x2800, 3)
        bus.write(0x2C00, 4)

        assertEquals(1, bus.read(0x2000))
        assertEquals(2, bus.read(0x2400))
        assertEquals(3, bus.read(0x2800))
        assertEquals(4, bus.read(0x2C00))
    }

    private fun ppu(mirroring: Mirroring = Mirroring.HORIZONTAL): Ppu {
        val chr = ByteArray(8192)
        val prgRom = ByteArray(16 * 1024)
        val cartridge = Cartridge(
            mirroring = mirroring,
            prgRom = prgRom,
            chr = chr,
            isChrRam = true,
            trainerPresent = false,
            mapper = Mapper0(prgRom = prgRom, chr = chr, isChrRam = true)
        )
        val socket = CartridgeSocket()
        socket.insert(cartridge)
        return Ppu(PpuBus(socket))
    }

    @Test
    fun `nametable addresses use cartridge mirroring`() {
        val ppu = ppu(Mirroring.VERTICAL)

        ppu.ppuWrite(0x2000, 0x22)

        assertEquals(0x22, ppu.ppuRead(0x2800))
    }

    @Test
    fun `palette addresses mirror universal background colors`() {
        val ppu = ppu()

        ppu.ppuWrite(0x3F00, 0x09)

        assertEquals(0x09, ppu.ppuRead(0x3F10))
    }

    @Test
    fun `PPU address register writes update VRAM address`() {
        val ppu = ppu()

        ppu.cpuWrite(6, 0x21)
        ppu.cpuWrite(6, 0x05)

        assertEquals(0, ppu.state.v)
        repeat(3) { ppu.step() }

        assertEquals(0x2105, ppu.state.v)
    }

    @Test
    fun `delayed PPU address commit synchronizes temporary address`() {
        val ppu = ppu()
        ppu.cpuWrite(6, 0x21)
        ppu.cpuWrite(6, 0x05)
        ppu.cpuWrite(0, 0x03)

        repeat(3) { ppu.step() }

        assertEquals(0x2105, ppu.state.v)
        assertEquals(ppu.state.v, ppu.state.t)
    }

    @Test
    fun `scroll register writes toggle write latch`() {
        val ppu = ppu()

        ppu.cpuWrite(5, 0x13)

        assertTrue(ppu.state.writeLatch)

        ppu.cpuWrite(5, 0x24)

        assertFalse(ppu.state.writeLatch)
    }

    @Test
    fun `PPUDATA reads are buffered outside palette range`() {
        val ppu = ppu()
        ppu.ppuWrite(0x2000, 0x55)
        ppu.cpuWrite(6, 0x20)
        ppu.cpuWrite(6, 0x00)
        repeat(3) { ppu.step() }

        assertEquals(0, ppu.cpuRead(7))
        repeat(6) { ppu.step() }
        assertEquals(0x55, ppu.cpuRead(7))
    }

    @Test
    fun `back to back PPUDATA read ignores second access without delaying first`() {
        val ppu = ppu()
        ppu.ppuWrite(0x2000, 0x55)
        ppu.cpuWrite(6, 0x20)
        ppu.cpuWrite(6, 0x00)
        repeat(3) { ppu.step() }

        ppu.cpuRead(7)
        ppu.cpuRead(7)
        repeat(6) { ppu.step() }

        assertEquals(0x2001, ppu.state.v)
        assertEquals(0x55, ppu.cpuRead(7))
    }

    @Test
    fun `PPUDATA classifies palette reads from physical bus during rendering`() {
        val ppu = ppu()
        ppu.ppuWrite(0x2000, 0x55)
        ppu.cpuWrite(6, 0x20)
        ppu.cpuWrite(6, 0x00)
        repeat(3) { ppu.step() }
        ppu.cpuRead(7)
        repeat(6) { ppu.step() }
        ppu.cpuWrite(6, 0x3F)
        ppu.cpuWrite(6, 0x00)
        repeat(3) { ppu.step() }
        ppu.cpuWrite(1, 0x08)
        repeat(8) { ppu.step() }

        assertEquals(0x55, ppu.cpuRead(7))
    }

    @Test
    fun `status read clears vblank flag and write latch`() {
        val ppu = ppu()

        repeat(241 * 341 + 2) { ppu.step() }

        assertTrue((ppu.state.status and 0x80) != 0)

        ppu.cpuRead(2)

        assertFalse((ppu.state.status and 0x80) != 0)
        assertFalse(ppu.state.writeLatch)
    }

    @Test
    fun `PPUCTRL enables NMI at vblank`() {
        val ppu = ppu()
        ppu.cpuWrite(0, 0x80)

        repeat(241 * 341 + 2) { ppu.step() }

        assertTrue(ppu.pollNmi())
    }

    @Test
    fun `background rendering draws non-zero pixels`() {
        val ppu = ppu()
        ppu.ppuWrite(0x2000, 1)
        ppu.ppuWrite(0x0000 + 16, 0x80)
        ppu.ppuWrite(0x3F01, 0x22)
        ppu.cpuWrite(1, 0x0A)

        repeat(2) { ppu.step() }

        assertNotEquals(0, renderedPixel(ppu, 0))
    }

    @Test
    fun `background rendering uses horizontal nametable bit`() {
        val ppu = ppu(Mirroring.VERTICAL)
        ppu.ppuWrite(0x2400, 1)
        ppu.ppuWrite(16, 0x80)
        ppu.ppuWrite(0x3F01, 0x22)
        ppu.cpuWrite(6, 0x24)
        ppu.cpuWrite(6, 0x00)
        ppu.cpuWrite(1, 0x0A)

        repeat(2) { ppu.step() }

        assertNotEquals(0, renderedPixel(ppu, 0))
    }

    @Test
    fun `sprite rendering draws non-zero pixels`() {
        val ppu = ppu()
        ppu.state.oam[0] = 0
        ppu.state.oam[1] = 1
        ppu.state.oam[3] = 0
        ppu.ppuWrite(16, 0x80)
        ppu.ppuWrite(0x3F11, 0x22)
        ppu.cpuWrite(1, 0x14)

        repeat(344) { ppu.step() }

        assertNotEquals(0, renderedPixel(ppu, 256))
    }

    @Test
    fun `eight by sixteen sprite rendering draws non-zero pixels`() {
        val ppu = ppu()
        ppu.state.oam[0] = 0
        ppu.state.oam[1] = 2
        ppu.state.oam[3] = 0
        ppu.ppuWrite(3 * 16, 0x80)
        ppu.ppuWrite(0x3F11, 0x22)
        ppu.cpuWrite(0, 0x20)
        ppu.cpuWrite(1, 0x14)

        repeat(341 * 9 + 2) { ppu.step() }

        assertNotEquals(0, renderedPixel(ppu, 8 * 256))
    }

    @Test
    fun `sprite zero hit is set when sprite overlaps background`() {
        val ppu = ppu()
        ppu.ppuWrite(0x2000, 1)
        ppu.ppuWrite(16, 0x80)
        ppu.ppuWrite(17, 0x80)
        ppu.state.oam[0] = 0
        ppu.state.oam[1] = 1
        ppu.state.oam[3] = 0
        ppu.cpuWrite(1, 0x1E)

        repeat(344) { ppu.step() }

        assertTrue((ppu.state.status and 0x40) != 0)
    }

    @Test
    fun `sprite zero hit waits for overlapping pixel cycle`() {
        val ppu = ppu()
        ppu.ppuWrite(0x2000, 1)
        ppu.ppuWrite(16, 0x08)
        ppu.ppuWrite(17, 0x08)
        ppu.state.oam[0] = 0
        ppu.state.oam[1] = 1
        ppu.state.oam[3] = 0
        ppu.cpuWrite(1, 0x1E)

        repeat(341 + 2) { ppu.step() }

        assertFalse((ppu.state.status and 0x40) != 0)

        repeat(4) { ppu.step() }

        assertTrue((ppu.state.status and 0x40) != 0)
    }

    @Test
    fun `ninth visible sprite sets overflow flag`() {
        val ppu = ppu()
        repeat(9) { sprite -> ppu.state.oam[sprite * 4] = 0 }
        ppu.cpuWrite(1, 0x10)

        repeat(341 + 2) { ppu.step() }

        assertTrue((ppu.state.status and 0x20) != 0)
    }

    @Test
    fun `background-only rendering still evaluates sprite overflow`() {
        val ppu = ppu()
        repeat(9) { sprite -> ppu.state.oam[sprite * 4] = 0 }
        ppu.cpuWrite(1, 0x08)

        repeat(341 + 2) { ppu.step() }

        assertTrue((ppu.state.status and 0x20) != 0)
    }

    @Test
    fun `rendered odd frame skips one PPU dot`() {
        val ppu = ppu()
        ppu.cpuWrite(1, 0x18)

        var initialCrossingDots = 0
        while (!ppu.state.frameComplete) {
            ppu.step()
            initialCrossingDots++
        }
        ppu.clearFrameComplete()

        var evenFrameDots = 0
        while (!ppu.state.frameComplete) {
            ppu.step()
            evenFrameDots++
        }
        ppu.clearFrameComplete()

        var oddFrameDots = 0
        while (!ppu.state.frameComplete) {
            ppu.step()
            oddFrameDots++
        }

        assertEquals(1 + 240 * 341, initialCrossingDots)
        assertEquals(341 * 262, evenFrameDots)
        assertEquals(evenFrameDots - 1, oddFrameDots)
    }

    @Test
    fun `mapper observes one sprite-table A12 rise per rendered scanline`() {
        val mapper = CountingMapper()
        val chr = ByteArray(8192)
        val prgRom = ByteArray(16 * 1024)
        val socket = CartridgeSocket()
        socket.insert(
            Cartridge(
                mirroring = Mirroring.HORIZONTAL,
                prgRom = prgRom,
                chr = chr,
                isChrRam = true,
                trainerPresent = false,
                mapper = mapper,
            )
        )
        val ppu = Ppu(PpuBus(socket))
        ppu.cpuWrite(0, 0x08)
        ppu.cpuWrite(1, 0x18)

        while (!ppu.state.frameComplete) ppu.step()
        assertEquals(240, mapper.scanlineClocks)

        ppu.clearFrameComplete()
        while (!ppu.state.frameComplete) ppu.step()

        assertEquals(481, mapper.scanlineClocks)
    }

    @Test
    fun `reset timeline crosses from pre-render end to visible start`() {
        val ppu = ppu()

        assertEquals(-1, ppu.state.scanline)
        assertEquals(340, ppu.state.cycle)

        ppu.step()

        assertEquals(0, ppu.state.scanline)
        assertEquals(0, ppu.state.cycle)
    }

    @Test
    fun `frame completes at post-render scanline dot zero`() {
        val ppu = ppu()

        while (!ppu.state.frameComplete) ppu.step()

        assertEquals(240, ppu.state.scanline)
        assertEquals(0, ppu.state.cycle)
    }

    @Test
    fun `Dendy vblank and NMI begin on scanline 291`() {
        val ppu = ppu()
        ppu.timing = ConsoleRegion.DENDY.timing
        ppu.cpuWrite(0, 0x80)

        while (ppu.state.scanline != 291 || ppu.state.cycle != 0) ppu.step()
        assertEquals(0, ppu.state.status and 0x80)

        ppu.step()

        assertEquals(0x80, ppu.state.status and 0x80)
        assertTrue(ppu.pollNmi())
    }

    @Test
    fun `regional scanline geometry uses PAL and Dendy NMI positions`() {
        assertEquals(262, ConsoleRegion.NTSC.timing.scanlinesPerFrame)
        assertEquals(241, ConsoleRegion.NTSC.timing.nmiScanline)
        assertEquals(312, ConsoleRegion.PAL.timing.scanlinesPerFrame)
        assertEquals(241, ConsoleRegion.PAL.timing.nmiScanline)
        assertEquals(312, ConsoleRegion.DENDY.timing.scanlinesPerFrame)
        assertEquals(291, ConsoleRegion.DENDY.timing.nmiScanline)
    }

    @Test
    fun `status read on vblank dot zero suppresses vblank and NMI`() {
        val ppu = ppu()
        ppu.cpuWrite(0, 0x80)
        while (ppu.state.scanline != 241 || ppu.state.cycle != 0) ppu.step()

        ppu.cpuRead(2)
        ppu.step()

        assertEquals(0, ppu.state.status and 0x80)
        assertFalse(ppu.pollNmi())
    }

    @Test
    fun `status read cancels NMI before direct polling`() {
        val ppu = ppu()
        ppu.cpuWrite(0, 0x80)
        while (ppu.state.scanline != 241 || ppu.state.cycle != 1) ppu.step()

        ppu.cpuRead(2)

        assertFalse(ppu.pollNmi())
    }

    @Test
    fun `status returns open bus in low five bits`() {
        val ppu = ppu()
        ppu.cpuWrite(1, 0x1B)

        assertEquals(0x1B, ppu.cpuRead(2) and 0x1F)
    }

    @Test
    fun `palette reads apply grayscale mask`() {
        val ppu = ppu()
        ppu.ppuWrite(0x3F00, 0x2F)
        ppu.cpuWrite(1, 0x01)
        ppu.cpuWrite(6, 0x3F)
        ppu.cpuWrite(6, 0x00)
        repeat(3) { ppu.step() }

        assertEquals(0x20, ppu.cpuRead(7))
    }

    @Test
    fun `forced blank displays palette color addressed by v`() {
        val ppu = ppu()
        ppu.ppuWrite(0x3F05, 0x22)
        ppu.cpuWrite(6, 0x3F)
        ppu.cpuWrite(6, 0x05)
        repeat(3) { ppu.step() }

        ppu.step()

        assertEquals(Palette.COLORS[0x22], ppu.framebuffer()[2])
    }

    @Test
    fun `PPUMASK advances current rendering state before fetch pipeline state`() {
        val ppu = ppu()
        ppu.ppuWrite(0x3F00, 0x21)
        ppu.ppuWrite(0x3F05, 0x22)
        ppu.cpuWrite(6, 0x3F)
        ppu.cpuWrite(6, 0x05)
        repeat(3) { ppu.step() }

        ppu.cpuWrite(1, 0x08)
        ppu.step()
        ppu.step()

        assertEquals(Palette.COLORS[0x22], ppu.framebuffer()[2], "First dot remains forced blank")
        assertEquals(Palette.COLORS[0x21], ppu.framebuffer()[3], "Current rendering state changes one dot earlier")
    }

    @Test
    fun `direct palette write invalidates rendered color cache`() {
        val ppu = ppu()
        ppu.ppuWrite(0x3F01, 0x21)
        ppu.cpuWrite(6, 0x3F)
        ppu.cpuWrite(6, 0x01)
        repeat(3) { ppu.step() }
        repeat(2) { ppu.step() }
        assertEquals(Palette.COLORS[0x21], ppu.framebuffer()[2])

        ppu.ppuWrite(0x3F01, 0x22)
        ppu.step()

        assertEquals(Palette.COLORS[0x22], ppu.framebuffer()[4])
    }

    @Test
    fun `restoring PPU bus state invalidates rendered color cache`() {
        val ppu = ppu()
        ppu.ppuWrite(0x3F01, 0x21)
        val originalBusState = ppu.ppuBusState()
        ppu.cpuWrite(6, 0x3F)
        ppu.cpuWrite(6, 0x01)
        repeat(6) { ppu.step() }
        assertEquals(Palette.COLORS[0x21], ppu.framebuffer()[2])

        ppu.ppuWrite(0x3F01, 0x22)
        ppu.step()
        ppu.restorePpuBusState(originalBusState)
        ppu.step()

        assertEquals(Palette.COLORS[0x21], ppu.framebuffer()[6])
    }

    @Test
    fun `soft reset preserves primary OAM`() {
        val ppu = ppu()
        ppu.state.oam[0x42] = 0x55

        ppu.reset(softReset = true)

        assertEquals(0x55, ppu.state.oam[0x42].toUnsignedInt())
        assertEquals(0, ppu.state.oamAddress)
    }

    @Test
    fun `hard reset preserves primary and secondary OAM memory`() {
        val ppu = ppu()
        ppu.state.oam[0x42] = 0x55
        repeat(2) { ppu.step() }
        val secondaryBeforeReset = ppu.captureState().secondaryOam.copyOf()

        ppu.reset(softReset = false)

        assertEquals(0x55, ppu.state.oam[0x42].toUnsignedInt())
        assertContentEquals(secondaryBeforeReset, ppu.captureState().secondaryOam)
    }

    @Test
    fun `PPUMASK emphasis is retained in each rendered pixel`() {
        val ppu = ppu()
        ppu.ppuWrite(0x3F00, 0x21)
        ppu.cpuWrite(1, 0x20)

        repeat(3) { ppu.step() }

        assertEquals(Palette.color(0x21 or 0x40), ppu.framebuffer()[0])
    }

    @Test
    fun `Dendy swaps red and green emphasis bits`() {
        val ppu = ppu()
        ppu.timing = ConsoleRegion.DENDY.timing
        ppu.ppuWrite(0x3F00, 0x21)
        ppu.cpuWrite(1, 0x20)

        repeat(3) { ppu.step() }

        assertEquals(Palette.color(0x21 or 0x80), ppu.framebuffer()[0])
    }

    @Test
    fun `restoring PPU state does not alias the snapshot`() {
        val ppu = ppu()
        val snapshot = ppu.captureState()

        ppu.restoreState(snapshot)
        repeat(100) { ppu.step() }

        assertEquals(0, snapshot.ppuCycle)
        assertEquals(340, snapshot.cycle)
    }

    @Test
    fun `PPU open bus bits decay after three frames`() {
        val ppu = ppu()
        ppu.cpuWrite(1, 0x1F)
        repeat(4) {
            while (!ppu.state.frameComplete) ppu.step()
            ppu.clearFrameComplete()
        }

        assertEquals(0, ppu.cpuRead(2) and 0x1F)
    }

    @Test
    fun `disabling rendering during sprite evaluation increments OAM address`() {
        val ppu = ppu()
        val control = ppu()
        for (candidate in arrayOf(ppu, control)) {
            candidate.cpuWrite(3, 0x20)
            candidate.cpuWrite(1, 0x18)
        }
        while (ppu.state.scanline != 0 || ppu.state.cycle != 100) ppu.step()
        while (control.state.scanline != 0 || control.state.cycle != 100) control.step()

        ppu.cpuWrite(1, 0)
        repeat(2) {
            ppu.step()
            control.step()
        }

        assertEquals((control.state.oamAddress + 1) and 0xFF, ppu.state.oamAddress)
    }

    @Test
    fun `first evaluated sprite remains sprite zero candidate with nonzero OAM address`() {
        val ppu = ppu()
        ppu.ppuWrite(0x2000, 1)
        ppu.ppuWrite(16, 0x80)
        ppu.ppuWrite(17, 0x80)
        ppu.state.oam[4] = 0
        ppu.state.oam[5] = 1
        ppu.state.oam[7] = 0
        ppu.cpuWrite(3, 4)
        ppu.cpuWrite(1, 0x1E)

        repeat(344) { ppu.step() }

        assertTrue((ppu.state.status and 0x40) != 0)
    }

    @Test
    fun `disabling rendering restores PPU bus address before delayed palette write`() {
        val ppu = ppu()
        ppu.cpuWrite(1, 0x18)
        ppu.cpuWrite(6, 0x3F)
        ppu.cpuWrite(6, 0x0F)
        repeat(4) { ppu.step() }
        ppu.cpuWrite(1, 0)
        ppu.step()

        ppu.cpuWrite(7, 0x11)
        repeat(5) { ppu.step() }

        assertEquals(0x11, ppu.ppuRead(0x3F0F))
        assertEquals(0, ppu.ppuRead(0x000B))
    }

    @Test
    fun `OAM DMA wraps around current OAM address`() {
        val ppu = ppu()
        val page = ByteArray(256) { it.toByte() }
        ppu.cpuWrite(3, 0xFE)

        ppu.writeOamDma(page)

        assertEquals(0, ppu.state.oam[0xFE].toUnsignedInt())
        assertEquals(1, ppu.state.oam[0xFF].toUnsignedInt())
        assertEquals(2, ppu.state.oam[0].toUnsignedInt())
        assertEquals(0xFE, ppu.state.oamAddress)
    }

    @Test
    fun `sprite priority hides sprite behind opaque background`() {
        val ppu = ppu()
        ppu.ppuWrite(0x2000, 1)
        repeat(8) {
            ppu.ppuWrite(16 + it, 0x80)
            ppu.ppuWrite(32 + it, 0x80)
        }
        ppu.ppuWrite(0x3F01, 0x21)
        ppu.ppuWrite(0x3F11, 0x22)
        ppu.state.oam[0] = 0
        ppu.state.oam[1] = 2
        ppu.state.oam[2] = 0x20
        ppu.state.oam[3] = 0
        ppu.cpuWrite(1, 0x1E)

        repeat(344) { ppu.step() }

        assertEquals(Palette.COLORS[0x21], renderedPixel(ppu, 256))
    }

    @Test
    fun `sprite priority draws sprite over transparent background`() {
        val ppu = ppu()
        ppu.ppuWrite(0x2000, 0)
        repeat(8) {
            ppu.ppuWrite(32 + it, 0x80)
        }
        ppu.ppuWrite(0x3F11, 0x22)
        ppu.state.oam[0] = 0
        ppu.state.oam[1] = 2
        ppu.state.oam[2] = 0x20
        ppu.state.oam[3] = 0
        ppu.cpuWrite(1, 0x1E)

        repeat(344) { ppu.step() }

        assertEquals(Palette.COLORS[0x22], renderedPixel(ppu, 256))
    }

    @Test
    fun `lower priority sprite cannot show through winning sprite behind background`() {
        val ppu = ppu()
        ppu.ppuWrite(0x2000, 1)
        repeat(8) {
            ppu.ppuWrite(16 + it, 0x80)
            ppu.ppuWrite(32 + it, 0x80)
            ppu.ppuWrite(48 + it, 0x80)
        }
        ppu.ppuWrite(0x3F01, 0x21)
        ppu.ppuWrite(0x3F11, 0x22)
        ppu.ppuWrite(0x3F15, 0x23)
        ppu.state.oam[0] = 0
        ppu.state.oam[1] = 2
        ppu.state.oam[2] = 0x20
        ppu.state.oam[3] = 0
        ppu.state.oam[4] = 0
        ppu.state.oam[5] = 3
        ppu.state.oam[6] = 0x01
        ppu.state.oam[7] = 0
        ppu.cpuWrite(1, 0x1E)

        repeat(344) { ppu.step() }

        assertEquals(Palette.COLORS[0x21], renderedPixel(ppu, 256))
    }

    private class CountingMapper : Mapper {
        var scanlineClocks = 0
        private var a12LowCycle = -1L

        override fun cpuRead(address: Int): Int = 0

        override fun cpuWrite(address: Int, value: Int) = Unit

        override fun ppuRead(address: Int): Int = 0

        override fun ppuWrite(address: Int, value: Int) = Unit

        override fun ppuAddressChanged(address: Int, cpuCycle: Long) {
            if ((address and 0x1000) == 0) {
                if (a12LowCycle < 0) a12LowCycle = cpuCycle
            } else {
                if (a12LowCycle >= 0 && cpuCycle - a12LowCycle >= 3) scanlineClocks++
                a12LowCycle = -1
            }
        }
    }

    private fun renderedPixel(ppu: Ppu, index: Int): Int {
        return ppu.framebuffer()[index]
    }
}
