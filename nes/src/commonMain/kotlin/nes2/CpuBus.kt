package nes2

import nes.cartridge.CartridgeSocket
import nes.util.low8Bits

interface CpuBus {
    fun read(address: Int): Int
    fun write(address: Int, value: Int)
}

class CpuBusNes(
    private val ram: IntArray,
    private val cartridge: CartridgeSocket,
    private val ppu: Ppu,
) : CpuBus {

    override fun read(address: Int): Int {
        return when (address) {
            in CPU_RAM_START..CPU_RAM_END -> ram[address and CPU_RAM_MASK].low8Bits()
            in CARTRIDGE_START..CPU_ADDRESS_MAX -> cartridge.cpuRead(address)
            in PPU_REGISTERS_START..PPU_REGISTERS_END -> ppu.cpuReadRegister(PPU_REGISTERS_START + (address and PPU_REGISTER_MASK))
            else -> 0
        }
    }

    override fun write(address: Int, value: Int) {
        when (address) {
            in CPU_RAM_START..CPU_RAM_END -> ram[address and CPU_RAM_MASK] = value
            in CARTRIDGE_START..CPU_ADDRESS_MAX -> cartridge.cpuWrite(address, value)
            in PPU_REGISTERS_START..PPU_REGISTERS_END -> ppu.cpuWriteRegister(
                PPU_REGISTERS_START + (address and PPU_REGISTER_MASK),
                value
            )
            OAM_DMA -> ppu.writeOamDma(value)
        }
    }

    companion object {
        private const val CPU_RAM_START = 0x0000
        private const val CPU_RAM_END = 0x1FFF
        private const val CPU_RAM_MASK = 0x07FF

        private const val PPU_REGISTERS_START = 0x2000
        private const val PPU_REGISTERS_END = 0x3FFF
        private const val PPU_REGISTER_MASK = 0x0007

        private const val OAM_DMA = 0x4014

        private const val CARTRIDGE_START = 0x4020
        private const val CPU_ADDRESS_MAX = 0xFFFF
    }
}