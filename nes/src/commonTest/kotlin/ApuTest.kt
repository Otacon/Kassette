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
        apu.step()
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
        assertEquals(0x40, apu.cpuRead(0x4015) and 0x40)
        apu.step()
        assertTrue(apu.irqPending())
        assertEquals(0x40, apu.cpuRead(0x4015) and 0x40)
        apu.step(2)
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

        val fiveStepApu = apu()
        fiveStepApu.cpuWrite(0x4017, 0x80)
        fiveStepApu.step(4 + 37_282)
        assertFalse(fiveStepApu.irqPending())
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
        apu.step(3)
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
        apu.step(3)
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
        apu.step(3)
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
        apu.step(3)
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
        apu.step(3)
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

        assertFalse(dmcDma.pending())
        apu.step(2)
        assertFalse(dmcDma.pending())
        apu.step()
        assertTrue(dmcDma.pending())
        assertEquals(0xC000, dmcDma.requestedAddress())
    }

    @Test
    fun `noise LFSR starts at one and advances from its valid seed`() {
        val apu = apu()

        assertEquals(1, apu.captureState().noise.values[5])
        apu.step(4)

        assertEquals(0x4000, apu.captureState().noise.values[5])
    }

    @Test
    fun `frame counter mode write applies after three or four cycles`() {
        val apu = apu()
        apu.cpuWrite(0x4017, 0x80)

        apu.step(3)
        assertEquals(0, apu.captureState().frameMode)
        apu.step()

        assertEquals(1, apu.captureState().frameMode)
        assertEquals(0, apu.captureState().frameCycle)
    }

    @Test
    fun `four-step IRQ begins on first terminal sequencer cycle`() {
        val apu = apu()

        apu.step(29_827)
        assertFalse(apu.irqPending())
        apu.step()

        assertTrue(apu.irqPending())
    }

    @Test
    fun `pulse sweep period zero updates on the next half-frame after reload`() {
        val apu = apu()
        apu.cpuWrite(0x4015, 0x01)
        apu.cpuWrite(0x4001, 0x81)
        apu.cpuWrite(0x4002, 0x00)
        apu.cpuWrite(0x4003, 0x09)

        apu.step(14_913)
        assertEquals(0x100, apu.captureState().pulse1.values[1])
        apu.step(14_916)

        assertEquals(0x180, apu.captureState().pulse1.values[1])
    }

    @Test
    fun `length reload coincident with half-frame clock is suppressed`() {
        val apu = apu()
        apu.cpuWrite(0x4015, 0x01)
        apu.cpuWrite(0x4003, 0x00)
        apu.step(14_912)
        assertEquals(10, apu.captureState().pulse1.lengthCounter)

        apu.cpuWrite(0x4003, 0x08)
        apu.step()

        assertEquals(9, apu.captureState().pulse1.lengthCounter)
    }

    @Test
    fun `DMC DMA pipeline survives state restore`() {
        val sourceDma = DmcDma()
        val source = NesApu(sourceDma)
        source.cpuWrite(0x4015, 0x10)
        source.step(3)
        val snapshot = source.captureState()
        val restoredDma = DmcDma()
        val restored = NesApu(restoredDma)

        restored.restoreState(snapshot)

        assertTrue(restoredDma.pending())
        assertEquals(0xC000, restoredDma.requestedAddress())
    }

    @Test
    fun `DMC disable takes effect after its parity delay`() {
        val dma = DmcDma()
        val apu = NesApu(dma)
        apu.cpuWrite(0x4015, 0x10)
        apu.step(3)

        apu.cpuWrite(0x4015, 0)
        assertEquals(0x10, apu.cpuRead(0x4015) and 0x10)
        apu.step()
        assertEquals(0x10, apu.cpuRead(0x4015) and 0x10)
        apu.step()

        assertEquals(0, apu.cpuRead(0x4015) and 0x10)
        assertFalse(dma.pending())
    }

    @Test
    fun `PAL reset initializes DMC rate index zero`() {
        val apu = apu()
        apu.timing = ConsoleRegion.PAL.timing

        apu.reset()

        assertEquals(398, apu.captureState().dmc.values[0])
        assertEquals(397, apu.captureState().dmc.values[1])
    }

    @Test
    fun `soft reset preserves frame mode and programmed DMC sample`() {
        val apu = apu()
        apu.cpuWrite(0x4012, 0x12)
        apu.cpuWrite(0x4013, 0x34)
        apu.cpuWrite(0x4017, 0x80)
        apu.step(4)

        apu.reset(softReset = true)
        val state = apu.captureState()

        assertEquals(1, state.frameMode)
        assertEquals(0xC000 + (0x12 shl 6), state.dmc.values[3])
        assertEquals((0x34 shl 4) + 1, state.dmc.values[4])
    }

    @Test
    fun `restore does not retain mutable snapshot arrays`() {
        val apu = apu()
        apu.cpuWrite(0x4015, 0x09)
        apu.cpuWrite(0x4003, 0)
        apu.cpuWrite(0x400F, 0)
        apu.step()
        val snapshot = apu.captureState()
        val expected = snapshot.copy(
            pulse1 = snapshot.pulse1.copy(values = snapshot.pulse1.values.copyOf(), flags = snapshot.pulse1.flags.copyOf()),
            pulse2 = snapshot.pulse2.copy(values = snapshot.pulse2.values.copyOf(), flags = snapshot.pulse2.flags.copyOf()),
            triangle = snapshot.triangle.copy(values = snapshot.triangle.values.copyOf(), flags = snapshot.triangle.flags.copyOf()),
            noise = snapshot.noise.copy(values = snapshot.noise.values.copyOf(), flags = snapshot.noise.flags.copyOf()),
            dmc = snapshot.dmc.copy(values = snapshot.dmc.values.copyOf(), flags = snapshot.dmc.flags.copyOf()),
            filters = snapshot.filters.copyOf(),
        )

        apu.restoreState(snapshot)
        apu.step(10_000)

        assertEquals(expected, snapshot)
    }

    @Test
    fun `pending frame counter write survives restore`() {
        val source = apu()
        source.cpuWrite(0x4017, 0x80)
        source.step(2)
        val restored = apu()

        restored.restoreState(source.captureState())
        restored.step()
        assertEquals(0, restored.captureState().frameMode)
        restored.step()

        assertEquals(1, restored.captureState().frameMode)
    }

    @Test
    fun `NTSC DMC period table matches hardware rate index thirteen`() {
        assertEquals(84, ConsoleRegion.NTSC.timing.dmcPeriods[13])
        assertEquals(84, ConsoleRegion.DENDY.timing.dmcPeriods[13])
    }
}
