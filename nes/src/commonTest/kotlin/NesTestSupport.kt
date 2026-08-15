import nes.cartridge.Cartridge
import nes.cartridge.CartridgeSocket
import nes.cartridge.Mapper0
import nes.cartridge.Mirroring
import nes.apu.DmcDma
import nes.apu.NesApu
import nes.cpu.Cpu6502
import nes.cpu.CpuBus
import nes.input.NesController
import nes.ppu.Ppu
import nes.ppu.PpuBus
import nes.util.low8Bits

fun cpuWithProgram(program: ByteArray, start: Int = 0x8000): Triple<Cpu6502, CpuBus, Ppu> {
    val prg = ByteArray(16 * 1024)
    program.copyInto(prg, destinationOffset = start - 0x8000)
    val vector = 0x3FFC
    prg[vector] = start.low8Bits().toByte()
    prg[vector + 1] = (start shr 8).toByte()
    prg[0x3FFA] = 0x00
    prg[0x3FFB] = 0x90.toByte()
    prg[0x3FFE] = 0x00
    prg[0x3FFF] = 0x91.toByte()
    prg[0x1000] = 0xEA.toByte()
    prg[0x1100] = 0x40.toByte()
    val chr = ByteArray(8192)
    val cartridge = Cartridge(
        mirroring = Mirroring.HORIZONTAL,
        prgRom = prg,
        chr = chr,
        isChrRam = true,
        trainerPresent = false,
        mapper = Mapper0(prgRom = prg, chr = chr, isChrRam = true)
    )
    val cartridgeSocket = CartridgeSocket()
    cartridgeSocket.insert(cartridge)
    val ppu = Ppu(PpuBus(cartridgeSocket))
    val dmcDma = DmcDma()
    val bus = CpuBus(
        cartridgeSocket,
        ppu,
        NesController(),
        NesApu(dmcDma),
        dmcDma,
    )
    val cpu = Cpu6502(bus)
    cpu.reset()
    return Triple(cpu, bus, ppu)
}
