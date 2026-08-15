import kotlin.test.*
import nes.apu.DmcDma
import nes.apu.NesApu
import nes.cartridge.Cartridge
import nes.cartridge.CartridgeSocket
import nes.cartridge.Mapper0
import nes.cartridge.Mirroring
import nes.ConsoleRegion

class ApuTest {
    private fun apu(prg: ByteArray = ByteArray(16 * 1024)): NesApu {
        val chr = ByteArray(8192)
        val socket = CartridgeSocket()
        socket.insert(
            Cartridge(
                mirroring = Mirroring.HORIZONTAL,
                prgRom = prg,
                chr = chr,
                isChrRam = true,
                trainerPresent = false,
                mapper = Mapper0(prgRom = prg, chr = chr, isChrRam = true)
            )
        )
        return NesApu(DmcDma())
    }

    @Test
    fun `pulse registers produce samples`() {
        val apu = apu()
        apu.cpuWrite(0x4015, 0x01)
        apu.cpuWrite(0x4000, 0x9F)
        apu.cpuWrite(0x4002, 0x20)
        apu.cpuWrite(0x4003, 0x08)
        apu.step(4000)
        assertTrue(apu.sampleCount > 0)
        assertTrue(apu.samples.any { it.toInt() != 0 })
    }

    @Test
    fun `silent APU produces zero-centered samples`() {
        val apu = apu()

        apu.step(4000)

        assertTrue(apu.sampleCount > 0)
        assertTrue(apu.samples.take(apu.sampleCount).all { it.toInt() == 0 })
    }

    @Test
    fun `status reflects enabled length counters`() {
        val apu = apu()
        apu.cpuWrite(0x4015, 0x0F)
        apu.cpuWrite(0x4003, 0x08)
        apu.cpuWrite(0x4007, 0x08)
        apu.cpuWrite(0x400B, 0x08)
        apu.cpuWrite(0x400F, 0x08)
        assertEquals(0x0F, apu.cpuRead(0x4015) and 0x0F)
        apu.cpuWrite(0x4015, 0x00)
        assertEquals(0, apu.cpuRead(0x4015) and 0x0F)
    }

    @Test
    fun `four-step frame counter raises and status read clears IRQ`() {
        val apu = apu()

        apu.step(29_829)

        assertTrue(apu.irqPending())
        assertEquals(0x40, apu.cpuRead(0x4015) and 0x40)
        assertFalse(apu.irqPending())
        assertEquals(0, apu.cpuRead(0x4015) and 0x40)
    }

    @Test
    fun `frame IRQ can be inhibited and five-step mode does not raise it`() {
        val apu = apu()
        apu.step(29_829)
        assertTrue(apu.irqPending())

        apu.cpuWrite(0x4017, 0x40)
        assertFalse(apu.irqPending())
        apu.step(29_829)
        assertFalse(apu.irqPending())

        apu.cpuWrite(0x4017, 0x80)
        apu.step(37_282)
        assertFalse(apu.irqPending())
    }

    @Test
    fun `pulse timer below eight is muted`() {
        val apu = apu()
        apu.cpuWrite(0x4015, 0x01)
        apu.cpuWrite(0x4000, 0x9F)
        apu.cpuWrite(0x4002, 0x00)
        apu.cpuWrite(0x4003, 0x08)

        apu.step(4000)

        assertTrue(apu.sampleCount > 0)
        assertTrue(apu.samples.take(apu.sampleCount).all { it.toInt() == 0 })
    }

    @Test
    fun `pulse sweep target overflow mutes channel even when sweep is disabled`() {
        val apu = apu()
        apu.cpuWrite(0x4015, 0x01)
        apu.cpuWrite(0x4000, 0x9F)
        apu.cpuWrite(0x4001, 0x01)
        apu.cpuWrite(0x4002, 0x00)
        apu.cpuWrite(0x4003, 0x0E)

        apu.step(4000)

        assertTrue(apu.samples.take(apu.sampleCount).all { it.toInt() == 0 })
    }

    @Test
    fun `DMC direct load contributes to output`() {
        val apu = apu()

        apu.cpuWrite(0x4011, 0x40)
        apu.step(100)

        assertTrue(apu.sampleCount > 0)
        assertTrue(apu.samples.take(apu.sampleCount).any { it.toInt() != 0 })
    }

    @Test
    fun `DMC fetches sample bytes from CPU memory reader`() {
        val dmcDma = DmcDma()
        val apu = NesApu(dmcDma)
        apu.cpuWrite(0x4010, 0x0F)
        apu.cpuWrite(0x4011, 0x00)
        apu.cpuWrite(0x4012, 0x00)
        apu.cpuWrite(0x4013, 0x01)

        apu.cpuWrite(0x4015, 0x10)
        dmcDma.complete(0xFF)
        apu.step(2000)

        assertTrue((apu.cpuRead(0x4015) and 0x10) != 0)
        assertTrue(apu.samples.take(apu.sampleCount).any { it.toInt() != 0 })
    }

    @Test
    fun `DMC status clears after final sample byte is fetched`() {
        val dmcDma = DmcDma()
        val apu = NesApu(dmcDma)
        apu.cpuWrite(0x4013, 0x00)

        apu.cpuWrite(0x4015, 0x10)
        dmcDma.complete(0xFF)
        apu.step()

        assertEquals(0, apu.cpuRead(0x4015) and 0x10)
    }

    @Test
    fun `status read does not clear DMC IRQ`() {
        val dmcDma = DmcDma()
        val apu = NesApu(dmcDma)
        apu.cpuWrite(0x4010, 0x8F)
        apu.cpuWrite(0x4013, 0x00)

        apu.cpuWrite(0x4015, 0x10)
        dmcDma.complete(0)
        apu.step()

        assertEquals(0x80, apu.cpuRead(0x4015) and 0x80)
        assertEquals(0x80, apu.cpuRead(0x4015) and 0x80)
    }

    @Test
    fun `DMC buffered byte continues playing after reader is disabled`() {
        val dmcDma = DmcDma()
        val apu = NesApu(dmcDma)
        apu.cpuWrite(0x4010, 0x0F)
        apu.cpuWrite(0x4011, 0x00)
        apu.cpuWrite(0x4013, 0x00)
        apu.cpuWrite(0x4015, 0x10)
        dmcDma.complete(0xFF)
        apu.step()

        apu.cpuWrite(0x4015, 0x00)
        apu.step(1000)

        assertTrue(apu.samples.take(apu.sampleCount).any { it.toInt() != 0 })
    }

    @Test
    fun `DMC keeps output silent until initial bit counter expires`() {
        val dmcDma = DmcDma()
        val apu = NesApu(dmcDma)
        apu.cpuWrite(0x4010, 0x0F)
        apu.cpuWrite(0x4011, 0x00)
        apu.cpuWrite(0x4013, 0x00)

        apu.cpuWrite(0x4015, 0x10)
        dmcDma.complete(0xFF)
        apu.step(450)

        assertTrue(apu.samples.take(apu.sampleCount).all { it.toInt() == 0 })

        apu.step(500)

        assertTrue(apu.samples.take(apu.sampleCount).any { it.toInt() != 0 })
    }

    @Test
    fun `DMC sample fetch queues an asynchronous DMA request`() {
        val dmcDma = DmcDma()
        val apu = NesApu(dmcDma)

        apu.cpuWrite(0x4015, 0x10)

        assertTrue(dmcDma.pending())
        assertEquals(0xC000, dmcDma.requestedAddress())
    }

    @Test
    fun `NTSC DMC period table matches hardware rate index thirteen`() {
        assertEquals(84, ConsoleRegion.NTSC.timing.dmcPeriods[13])
        assertEquals(84, ConsoleRegion.DENDY.timing.dmcPeriods[13])
    }
}
