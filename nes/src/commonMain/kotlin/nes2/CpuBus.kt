package nes2

import nes.util.low8Bits
import nes2.apu.Apu
import nes2.cartridge.CartridgePort
import nes2.controller.ControllerPort
import nes2.ppu.Ppu

interface CpuBus {
    fun read(address: Int): Int
    fun write(address: Int, value: Int)
}

class CpuBusNes(
    private val ram: IntArray,
    private val cartridge: CartridgePort,
    private val ppu: Ppu,
    private val dma: OamDma,
    private val apu: Apu,
    private val controller1: ControllerPort,
    private val controller2: ControllerPort,
) : CpuBus {

    private var openBus = 0

    override fun read(address: Int): Int {
        val value = when (address) {
            in CPU_RAM_START..CPU_RAM_END -> ram[address and CPU_RAM_MASK].low8Bits()
            in CARTRIDGE_START..CPU_ADDRESS_MAX -> cartridge.cpuRead(address, openBus)
            in PPU_REGISTERS_START..PPU_REGISTERS_END -> ppu.cpuReadRegister(PPU_REGISTERS_START + (address and PPU_REGISTER_MASK))
            in APU_REGISTERS_START..APU_REGISTERS_END -> apu.read(address)
            APU_STATUS -> apu.read(address)
            CONTROLLER_1 -> controller1.read()
            CONTROLLER_2 -> controller2.read()
            else -> openBus
        }.low8Bits()
        openBus = value
        return value
    }

    override fun write(address: Int, value: Int) {
        val v = value.low8Bits()
        openBus = v
        when (address) {
            in CPU_RAM_START..CPU_RAM_END -> ram[address and CPU_RAM_MASK] = v
            in CARTRIDGE_START..CPU_ADDRESS_MAX -> cartridge.cpuWrite(address, v)
            in PPU_REGISTERS_START..PPU_REGISTERS_END -> ppu.cpuWriteRegister(
                PPU_REGISTERS_START + (address and PPU_REGISTER_MASK),
                v
            )

            in APU_REGISTERS_START..APU_REGISTERS_END -> apu.write(address, v)
            APU_STATUS -> apu.write(address, v)
            APU_FRAME_COUNTER -> apu.write(address, v)

            CONTROLLER_STROBE -> {
                controller1.write(v)
                controller2.write(v)
            }

            OAM_DMA -> dma.start(v)
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

        private const val APU_REGISTERS_START = 0x4000
        private const val APU_REGISTERS_END = 0x4013
        private const val APU_STATUS = 0x4015
        private const val APU_FRAME_COUNTER = 0x4017

        private const val CONTROLLER_1 = 0x4016
        private const val CONTROLLER_2 = 0x4017
        private const val CONTROLLER_STROBE = 0x4016

        private const val CARTRIDGE_START = 0x4020
        private const val CPU_ADDRESS_MAX = 0xFFFF
    }
}
