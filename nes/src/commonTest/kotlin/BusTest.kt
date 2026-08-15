import kotlin.test.*
import nes.ConsoleRegion
import nes.apu.DmcDma
import nes.apu.NesApu
import nes.cartridge.Cartridge
import nes.cartridge.CartridgeSocket
import nes.cartridge.Mapper0
import nes.cartridge.Mirroring
import nes.cpu.CpuBus
import nes.cpu.Cpu6502
import nes.input.NesController
import nes.ppu.Ppu
import nes.ppu.PpuBus
import nes.util.toUnsignedInt

class BusTest {
    private fun NesController.readButtons(): Int {
        write(1)
        write(0)

        var buttons = 0
        repeat(8) { index ->
            buttons = buttons or ((read() and 1) shl index)
        }
        return buttons
    }

    private fun cartridge(
        mirroring: Mirroring = Mirroring.HORIZONTAL,
        prgRom: ByteArray = ByteArray(16 * 1024),
        chr: ByteArray = ByteArray(8192),
        isChrRam: Boolean = true,
        trainerPresent: Boolean = false,
        region: ConsoleRegion = ConsoleRegion.NTSC,
    ): Cartridge {
        return Cartridge(
            mirroring = mirroring,
            prgRom = prgRom,
            chr = chr,
            isChrRam = isChrRam,
            trainerPresent = trainerPresent,
            mapper = Mapper0(prgRom = prgRom, chr = chr, isChrRam = isChrRam),
            region = region,
        )
    }

    @Test
    fun `internal RAM mirrors every 2 KiB`() {
        val (_, bus, _) = cpuWithProgram(byteArrayOf(0xEA.toByte()))

        bus.write(0x0002, 0x66)

        assertEquals(0x66, bus.read(0x0802))
    }

    @Test
    fun `PPU registers mirror through CPU bus`() {
        val (_, bus, ppu) = cpuWithProgram(byteArrayOf(0xEA.toByte()))

        bus.write(0x2008, 0x80)

        assertEquals(0x80, ppu.state.ctrl)
    }

    @Test
    fun `write-only APU registers preserve CPU open bus`() {
        val (_, bus, _) = cpuWithProgram(byteArrayOf(0xEA.toByte()))
        bus.write(0x4000, 0xA5)

        assertEquals(0xA5, bus.read(0x4008))
    }

    @Test
    fun `APU status preserves open bus bit five`() {
        val (_, bus, _) = cpuWithProgram(byteArrayOf(0xEA.toByte()))
        bus.write(0x4000, 0x20)

        assertEquals(0x20, bus.read(0x4015) and 0x20)
    }

    @Test
    fun `controller reads preserve undriven CPU open bus bits`() {
        val (_, bus, _) = cpuWithProgram(byteArrayOf(0xEA.toByte()))
        bus.write(0x4000, 0xA0)

        assertEquals(0xE0, bus.read(0x4016))
    }

    @Test
    fun `cartridge socket reads mirrored PRG ROM`() {
        val prg = ByteArray(16 * 1024)
        prg[0] = 0x12
        val socket = CartridgeSocket()
        socket.insert(cartridge(prgRom = prg))

        assertEquals(0x12, socket.cpuRead(0x8000))
        assertEquals(0x12, socket.cpuRead(0xC000))
    }

    @Test
    fun `controller reads shift button state`() {
        val controller = NesController()
        controller.press(NesController.BUTTON_A)
        controller.poll()

        controller.write(1)
        controller.write(0)

        assertEquals(1, controller.read() and 1)
        assertEquals(0, controller.read() and 1)
    }

    @Test
    fun `controller filters opposite directions`() {
        val controller = NesController()

        controller.press(NesController.BUTTON_A)
        controller.press(NesController.BUTTON_LEFT)
        controller.press(NesController.BUTTON_RIGHT)
        controller.poll()

        val buttons = controller.readButtons()

        assertTrue((buttons and (1 shl NesController.BUTTON_A)) != 0)
        assertFalse((buttons and (1 shl NesController.BUTTON_LEFT)) != 0)
        assertFalse((buttons and (1 shl NesController.BUTTON_RIGHT)) != 0)
    }

    @Test
    fun `controller serial position survives CPU bus restore`() {
        val (_, bus, _) = cpuWithProgram(byteArrayOf(0xEA.toByte()))
        bus.write(0x4016, 1)
        bus.write(0x4016, 0)
        bus.read(0x4016)
        val snapshot = bus.captureState()

        bus.read(0x4016)
        bus.restoreState(snapshot)

        assertEquals(snapshot.controller1.index, bus.captureState().controller1.index)
    }

    @Test
    fun `controller polls button presses for one frame`() {
        val controller = NesController()

        controller.press(NesController.BUTTON_A)
        controller.press(NesController.BUTTON_LEFT)
        controller.poll()

        var buttons = controller.readButtons()

        assertTrue((buttons and (1 shl NesController.BUTTON_A)) != 0)
        assertTrue((buttons and (1 shl NesController.BUTTON_LEFT)) != 0)

        controller.poll()
        buttons = controller.readButtons()

        assertEquals(0, buttons)
    }

    @Test
    fun `cartridge reads return zero after removal`() {
        val prg = ByteArray(16 * 1024)
        prg[0] = 0x12
        val socket = CartridgeSocket()
        socket.insert(cartridge(prgRom = prg))

        assertEquals(0x12, socket.cpuRead(0x8000))

        socket.remove()

        assertEquals(0, socket.cpuRead(0x8000))
    }

    @Test
    fun `OAM DMA copies CPU page into PPU OAM`() {
        val socket = CartridgeSocket()
        socket.insert(cartridge())
        val ppu = Ppu(PpuBus(socket))
        val dmcDma = DmcDma()
        val bus = CpuBus(socket, ppu, NesController(), NesApu(dmcDma), dmcDma)
        val cpu = Cpu6502(bus)
        cpu.reset()

        bus.write(0x0000, Cpu6502.OP_NOP)
        bus.write(0x0200, 0x77)
        bus.write(0x4014, 2)
        val cycles = cpu.step()

        assertEquals(0x77, ppu.state.oam[0].toUnsignedInt())
        assertEquals(515, cycles)
    }

    @Test
    fun `OAM DMA adds alignment cycle on opposite CPU parity`() {
        val socket = CartridgeSocket()
        socket.insert(cartridge())
        val ppu = Ppu(PpuBus(socket))
        val dmcDma = DmcDma()
        val bus = CpuBus(socket, ppu, NesController(), NesApu(dmcDma), dmcDma)
        val cpu = Cpu6502(bus)
        cpu.reset()
        bus.write(0, Cpu6502.OP_PHP)
        bus.write(1, Cpu6502.OP_NOP)
        cpu.step()
        bus.write(0x4014, 2)

        assertEquals(516, cpu.step())
    }

    @Test
    fun `unclocked APU length reload remains pending`() {
        val (_, bus, _) = cpuWithProgram(byteArrayOf(0xEA.toByte()))

        bus.write(0x4015, 0x01)
        bus.write(0x4003, 0x08)

        assertEquals(0, bus.read(0x4015) and 0x01)
    }

    @Test
    fun `pending DMC DMA delays the next CPU opcode read`() {
        val socket = CartridgeSocket()
        socket.insert(cartridge())
        val ppu = Ppu(PpuBus(socket))
        val dmcDma = DmcDma()
        val bus = CpuBus(socket, ppu, NesController(), NesApu(dmcDma), dmcDma)
        val cpu = Cpu6502(bus)
        cpu.reset()
        bus.write(0, Cpu6502.OP_NOP)
        dmcDma.request(0x8000)

        val cycles = cpu.step()

        assertEquals(6, cycles)
        assertEquals(1, cpu.state.pc)
    }

    @Test
    fun `DMC DMA uses three cycles on opposite alignment`() {
        val socket = CartridgeSocket()
        socket.insert(cartridge())
        val ppu = Ppu(PpuBus(socket))
        val dmcDma = DmcDma()
        val bus = CpuBus(socket, ppu, NesController(), NesApu(dmcDma), dmcDma)
        val cpu = Cpu6502(bus)
        cpu.reset()
        bus.write(0, Cpu6502.OP_PHP)
        bus.write(1, Cpu6502.OP_NOP)
        cpu.step()
        dmcDma.request(0x8000)

        assertEquals(5, cpu.step(), "Three DMA cycles plus the two-cycle NOP")
    }

    @Test
    fun `DMC overlap clocks second controller port`() {
        val socket = CartridgeSocket()
        socket.insert(cartridge())
        val ppu = Ppu(PpuBus(socket))
        val dmcDma = DmcDma()
        val bus = CpuBus(socket, ppu, NesController(), NesApu(dmcDma), dmcDma)
        val cpu = Cpu6502(bus)
        cpu.reset()
        bus.write(0, Cpu6502.OP_LDA_ABS)
        bus.write(1, 0x17)
        bus.write(2, 0x40)
        bus.setCycleListener { cycle ->
            if (cycle.type == CpuBus.CycleType.READ && cycle.address == 2) dmcDma.request(0x8000)
        }

        cpu.step()

        assertTrue(bus.captureState().controller2.index > 0)
    }

    @Test
    fun `DMC disable during halt aborts after one stall cycle`() {
        val socket = CartridgeSocket()
        socket.insert(cartridge())
        val ppu = Ppu(PpuBus(socket))
        val dmcDma = DmcDma()
        val apu = NesApu(dmcDma)
        val bus = CpuBus(socket, ppu, NesController(), apu, dmcDma)
        val cpu = Cpu6502(bus)
        cpu.reset()
        bus.write(0, Cpu6502.OP_NOP)
        val state = apu.captureState()
        state.dmc.flags[6] = true
        state.dmc.values[11] = 1
        state.dmcDma.address = 0x8000
        state.dmcDma.phase = 1
        apu.restoreState(state)
        bus.setCyclePhaseListener { _, beforeAccess -> if (beforeAccess) apu.step() }

        assertEquals(3, cpu.step())
        assertFalse(dmcDma.pending())
    }

    @Test
    fun `DMC DMA waits until a CPU read after being requested during a write instruction`() {
        val socket = CartridgeSocket()
        socket.insert(cartridge())
        val ppu = Ppu(PpuBus(socket))
        val dmcDma = DmcDma()
        val bus = CpuBus(socket, ppu, NesController(), NesApu(dmcDma), dmcDma)
        val cpu = Cpu6502(bus)
        cpu.reset()
        bus.write(0, Cpu6502.OP_STA_ABS)
        bus.write(1, 0x02)
        bus.write(2, 0x00)
        bus.write(3, Cpu6502.OP_NOP)
        val cycles = mutableListOf<CpuBus.Cycle>()
        bus.setCycleListener { cycle ->
            cycles += cycle
            if (cycle.type == CpuBus.CycleType.READ && cycle.address == 2) dmcDma.request(0x8000)
        }

        cpu.step()
        assertEquals(CpuBus.CycleType.WRITE, cycles.last().type)
        assertTrue(dmcDma.pending(), "The write cycle cannot start DMA")

        cpu.step()
        assertTrue(cycles.indexOfFirst { it.type == CpuBus.CycleType.DMA_READ } >
            cycles.indexOfFirst { it.type == CpuBus.CycleType.WRITE })
    }

    @Test
    fun `DMC DMA steals an OAM get slot and extends the transfer`() {
        val socket = CartridgeSocket()
        socket.insert(cartridge())
        val ppu = Ppu(PpuBus(socket))
        val dmcDma = DmcDma()
        val bus = CpuBus(socket, ppu, NesController(), NesApu(dmcDma), dmcDma)
        val cpu = Cpu6502(bus)
        cpu.reset()
        bus.write(0, Cpu6502.OP_NOP)
        bus.write(0x0200, 0x77)
        bus.write(0x02FF, 0x88)
        bus.write(0x4014, 2)
        var requested = false
        var dmcReadIndex = -1
        var lastOamWriteIndex = -1
        var cycleIndex = 0
        bus.setCycleListener { cycle ->
            if (!requested && cycle.type == CpuBus.CycleType.DMA_READ && cycle.address == 0x0200) {
                dmcDma.request(0x8000)
                requested = true
            }
            if (cycle.type == CpuBus.CycleType.DMA_READ && cycle.address == 0x8000) dmcReadIndex = cycleIndex
            if (cycle.type == CpuBus.CycleType.DMA_WRITE) lastOamWriteIndex = cycleIndex
            cycleIndex++
        }

        val cycles = cpu.step()

        assertTrue(cycles > 515)
        assertTrue(dmcReadIndex in 0..<lastOamWriteIndex)
        assertEquals(0x77, ppu.state.oam[0].toUnsignedInt())
        assertEquals(0x88, ppu.state.oam[255].toUnsignedInt())
    }

    @Test
    fun `PAL DMC DMA waits for an opcode fetch`() {
        val socket = CartridgeSocket()
        socket.insert(cartridge(region = ConsoleRegion.PAL))
        val ppu = Ppu(PpuBus(socket))
        val dmcDma = DmcDma()
        val bus = CpuBus(socket, ppu, NesController(), NesApu(dmcDma), dmcDma)
        val cpu = Cpu6502(bus)
        cpu.reset()
        bus.write(0, Cpu6502.OP_LDA_ABS)
        bus.write(1, 0x10)
        bus.write(2, 0x00)
        bus.write(3, Cpu6502.OP_NOP)
        var requested = false
        var dmaReads = 0
        bus.setCycleListener { cycle ->
            if (!requested && cycle.type == CpuBus.CycleType.READ && cycle.address == 0) {
                dmcDma.request(0x8000)
                requested = true
            }
            if (cycle.type == CpuBus.CycleType.DMA_READ) dmaReads++
        }

        cpu.step()
        assertEquals(0, dmaReads)
        assertTrue(dmcDma.pending())

        cpu.step()
        assertTrue(dmaReads >= 3)
    }
}
