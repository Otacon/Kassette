package nes2.ppuBus

import nes.util.low8Bits

interface PpuBus {
    fun read(address: Int): Int
    fun write(address: Int, value: Int)
}

class PpuBusNes(
    private val state: PpuBusState,
) : PpuBus {

    override fun read(address: Int): Int {
        return when (val ppuAddress = address and 0x3FFF) {
            in 0x2000..0x3EFF -> {
                val nametableAddress = (ppuAddress - 0x2000) and 0x07FF
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
            in 0x2000..0x3EFF -> {
                val nametableAddress = (ppuAddress - 0x2000) and 0x07FF
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
}

