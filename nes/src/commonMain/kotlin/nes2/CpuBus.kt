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
) : CpuBus {

    override fun read(address: Int): Int {
        return when (address) {
            in CPU_RAM_START..CPU_RAM_END -> ram[address and CPU_RAM_MASK].low8Bits()
            in CARTRIDGE_START..CPU_ADDRESS_MAX -> cartridge.cpuRead(address)
            else -> 0
        }
    }

    override fun write(address: Int, value: Int) {
        when (address) {
            in CPU_RAM_START..CPU_RAM_END -> ram[address and CPU_RAM_MASK] = value
            in CARTRIDGE_START..CPU_ADDRESS_MAX -> cartridge.cpuWrite(address, value)
        }
    }

    companion object {
        private const val CPU_RAM_START = 0x0000
        private const val CPU_RAM_END = 0x1FFF

        private const val CPU_RAM_MASK = 0x07FF

        private const val CARTRIDGE_START = 0x4020
        private const val CPU_ADDRESS_MAX = 0xFFFF
    }
}