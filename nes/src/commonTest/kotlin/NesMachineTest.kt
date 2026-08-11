import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import nes.ConsoleRegion
import nes.NesMachine
import nes.apu.DmcDma
import nes.apu.NesApu
import nes.cartridge.Cartridge
import nes.cartridge.CartridgeSocket
import nes.cartridge.Mapper0
import nes.cartridge.Mirroring
import nes.cpu.Cpu6502
import nes.cpu.CpuBus
import nes.cpu.CpuStall
import nes.input.NesController
import nes.ppu.Ppu
import nes.ppu.PpuBus

class NesMachineTest {
    @Test
    fun `power on applies cartridge timing and clocks startup cycles`() {
        val machine = fixture(ConsoleRegion.PAL).machine

        machine.powerOn()

        assertTrue(machine.isPoweredOn.value)
        assertSame(ConsoleRegion.PAL.timing, machine.timing)
        assertSame(ConsoleRegion.PAL.timing, machine.ppu.timing)
        assertSame(ConsoleRegion.PAL.timing, machine.apu.timing)
        assertEquals(7, machine.cpu.totalCycles)
        assertTrue(machine.ppu.scanline >= 0)
    }

    @Test
    fun `run until frame clocks machine from each CPU bus cycle`() {
        val machine = fixture().machine
        machine.powerOn()
        var inputPolls = 0
        val startCycles = machine.cpu.totalCycles

        machine.runUntilFrame { inputPolls++ }

        assertTrue(machine.ppu.frameComplete)
        assertEquals(240, machine.ppu.scanline)
        assertTrue(machine.cpu.totalCycles > startCycles)
        assertTrue(inputPolls in 1..3)
    }

    @Test
    fun `soft reset preserves CPU RAM and general registers`() {
        val fixture = fixture(program = byteArrayOf(Cpu6502.OP_LDA_IMM.toByte(), 0x5A, Cpu6502.OP_JMP_ABS.toByte(), 0x02, 0x80.toByte()))
        val machine = fixture.machine
        machine.powerOn()
        machine.cpu.step()
        val stackBeforeReset = machine.cpu.sp
        fixture.cpuBus.write(0x42, 0xA5)

        machine.reset()

        assertEquals(0x5A, machine.cpu.a)
        assertEquals((stackBeforeReset - 3) and 0xFF, machine.cpu.sp)
        assertEquals(0xA5, fixture.cpuBus.read(0x42))
        assertEquals(7, machine.cpu.totalCycles)
    }

    @Test
    fun `power off only changes machine power state`() {
        val machine = fixture().machine
        machine.powerOn()

        machine.powerOff()

        assertFalse(machine.isPoweredOn.value)
    }

    private fun fixture(
        region: ConsoleRegion = ConsoleRegion.NTSC,
        program: ByteArray = byteArrayOf(Cpu6502.OP_JMP_ABS.toByte(), 0x00, 0x80.toByte()),
    ): MachineFixture {
        val prg = ByteArray(16 * 1024)
        program.copyInto(prg)
        prg[0x3FFC] = 0x00
        prg[0x3FFD] = 0x80.toByte()
        val chr = ByteArray(8192)
        val socket = CartridgeSocket()
        val ppu = Ppu(PpuBus(socket))
        val controller = NesController()
        val stall = CpuStall()
        val apu = NesApu(DmcDma(socket, stall))
        val cpuBus = CpuBus(socket, ppu, controller, apu, stall)
        val cpu = Cpu6502(cpuBus)
        val machine = NesMachine(controller, socket, ppu, apu, cpu, cpuBus)
        socket.insert(
            Cartridge(
                mirroring = Mirroring.HORIZONTAL,
                prgRom = prg,
                chr = chr,
                isChrRam = true,
                trainerPresent = false,
                mapper = Mapper0(prg, chr, isChrRam = true),
                region = region,
            )
        )
        return MachineFixture(machine, cpuBus)
    }

    private data class MachineFixture(val machine: NesMachine, val cpuBus: CpuBus)
}
