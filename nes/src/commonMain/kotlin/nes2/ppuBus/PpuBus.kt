package nes2.ppuBus

import nes.cartridge.Mirroring
import nes.util.low8Bits
import nes2.cartridgePort.CartridgePort

interface PpuBus {

    fun read(address: Int): Int

    fun write(address: Int, value: Int)
}

class PpuBusNes(
    private val state: PpuBusState,
    private val cartridge: CartridgePort,
) : PpuBus {

    override fun read(address: Int): Int {
        return when (val ppuAddress = address and 0x3FFF) {
            in 0x0000..0x1FFF -> cartridge.ppuRead(ppuAddress)
            in 0x2000..0x3EFF -> {
                val nametableAddress = mapNametableAddress(ppuAddress)
                state.nametableRam[nametableAddress]
            }

            in 0x3F00..0x3FFF -> {
                val paletteAddress = normalizePaletteAddress(ppuAddress)
                state.paletteRam[paletteAddress]
            }

            else -> 0
        }
    }

    override fun write(address: Int, value: Int) {
        val ppuAddress = address and 0x3FFF
        val value = value.low8Bits()

        when (ppuAddress) {
            in 0x0000..0x1FFF -> cartridge.ppuWrite(ppuAddress, value)
            in 0x2000..0x3EFF -> {
                val nametableAddress = mapNametableAddress(ppuAddress)
                state.nametableRam[nametableAddress] = value
            }

            in 0x3F00..0x3FFF -> {
                val paletteAddress = normalizePaletteAddress(ppuAddress)
                state.paletteRam[paletteAddress] = value
            }
        }
    }

    private fun normalizePaletteAddress(address: Int): Int {
        var paletteAddress = (address - 0x3F00) and 0x1F

        if (paletteAddress == 0x10 || paletteAddress == 0x14 || paletteAddress == 0x18 || paletteAddress == 0x1C) {
            paletteAddress -= 0x10
        }

        return paletteAddress
    }

    private fun mapNametableAddress(address: Int): Int {
        val normalized = (address - 0x2000) % 0x1000
        val table = normalized / 0x400
        val offset = normalized and 0x03FF

        val mappedTable = when (cartridge.mirroring) {
            Mirroring.VERTICAL -> table and 0x01
            Mirroring.HORIZONTAL -> table shr 1
            Mirroring.SINGLE_SCREEN_LOWER -> 0
            Mirroring.SINGLE_SCREEN_UPPER -> 1
        }

        return mappedTable * 0x400 + offset
    }
}

