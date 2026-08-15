import kotlin.test.*
import nes.apu.DmcDma
import nes.apu.NesApu
import nes.cartridge.Cartridge
import nes.cartridge.CartridgeSocket
import nes.cartridge.Mapper0
import nes.cartridge.Mirroring
import nes.cpu.CpuBus
import nes.cpu.Cpu6502
import nes.cpu.CpuStall
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
    ): Cartridge {
        return Cartridge(
            mirroring = mirroring,
            prgRom = prgRom,
            chr = chr,
            isChrRam = isChrRam,
            trainerPresent = trainerPresent,
            mapper = Mapper0(prgRom = prgRom, chr = chr, isChrRam = isChrRam)
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

        assertEquals(0x80, ppu.ctrl)
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
        assertTrue((buttons and (1 shl NesController.BUTTON_LEFT)) != 0)
        assertFalse((buttons and (1 shl NesController.BUTTON_RIGHT)) != 0)
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
        val cpuStall = CpuStall()
        val bus = CpuBus(socket, ppu, NesController(), NesApu(DmcDma(socket, cpuStall)), cpuStall)
        val cpu = Cpu6502(bus)
        cpu.reset()

        bus.write(0x0000, Cpu6502.OP_NOP)
        bus.write(0x0200, 0x77)
        bus.write(0x4014, 2)
        val cycles = cpu.step()

        assertEquals(0x77, ppu.oam[0].toUnsignedInt())
        assertEquals(515, cycles)
    }

    @Test
    fun `OAM DMA adds alignment cycle on opposite CPU parity`() {
        val socket = CartridgeSocket()
        socket.insert(cartridge())
        val ppu = Ppu(PpuBus(socket))
        val cpuStall = CpuStall()
        val bus = CpuBus(socket, ppu, NesController(), NesApu(DmcDma(socket, cpuStall)), cpuStall)
        val cpu = Cpu6502(bus)
        cpu.reset()
        bus.write(0, Cpu6502.OP_PHP)
        bus.write(1, Cpu6502.OP_NOP)
        cpu.step()
        bus.write(0x4014, 2)

        assertEquals(516, cpu.step())
    }

    @Test
    fun `APU status routes through CPU bus`() {
        val (_, bus, _) = cpuWithProgram(byteArrayOf(0xEA.toByte()))

        bus.write(0x4015, 0x01)
        bus.write(0x4003, 0x08)

        assertTrue((bus.read(0x4015) and 0x01) != 0)
    }

    @Test
    fun `pending DMA stall delays the next CPU opcode`() {
        val socket = CartridgeSocket()
        socket.insert(cartridge())
        val ppu = Ppu(PpuBus(socket))
        val cpuStall = CpuStall()
        val bus = CpuBus(socket, ppu, NesController(), NesApu(DmcDma(socket, cpuStall)), cpuStall)
        val cpu = Cpu6502(bus)
        cpu.reset()
        bus.write(0, Cpu6502.OP_NOP)
        cpuStall.request(4)

        val stallCycles = cpu.step()

        assertEquals(4, stallCycles)
        assertEquals(0, cpu.state.pc)

        cpu.step()

        assertEquals(1, cpu.state.pc)
    }
}
