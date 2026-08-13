package nes.ppu

import nes.cartridge.CartridgeSocket
import nes.cartridge.Mirroring
import nes.util.toUnsignedInt

class PpuBus(
    private val cartridgeSocket: CartridgeSocket,
) {
    private var state = PpuBusState()
    private val nametables: ByteArray get() = state.nametables
    private val paletteRam: ByteArray get() = state.paletteRam

    fun read(address: Int): Int {
        val a = address and 0x3FFF
        return when {
            a < 0x2000 -> cartridgeSocket.ppuRead(a)
            a < 0x3F00 -> nametables[mirrorNametable(a)].toUnsignedInt()
            else -> paletteRam[mirrorPalette(a)].toInt() and 0x3F
        }
    }

    fun write(address: Int, value: Int) {
        val a = address and 0x3FFF
        when {
            a < 0x2000 -> cartridgeSocket.ppuWrite(a, value)
            a < 0x3F00 -> nametables[mirrorNametable(a)] = value.toByte()
            else -> paletteRam[mirrorPalette(a)] = (value and 0x3F).toByte()
        }
    }

    fun clockScanline() {
        cartridgeSocket.clockScanline()
    }

    fun captureState(): PpuBusState = state.copy(nametables = nametables.copyOf(), paletteRam = paletteRam.copyOf())

    fun restoreState(state: PpuBusState) {
        this.state = state
    }

    private fun mirrorNametable(address: Int): Int {
        val index = (address - 0x2000) and 0x0FFF
        val table = index shr 10
        val offset = index and 0x3FF
        val physical = when (cartridgeSocket.mirroring) {
            Mirroring.VERTICAL -> table and 1
            Mirroring.HORIZONTAL -> table shr 1
            Mirroring.SINGLE_SCREEN_LOWER -> 0
            Mirroring.SINGLE_SCREEN_UPPER -> 1
            else -> table shr 1
        }
        return (physical shl 10) + offset
    }

    private fun mirrorPalette(address: Int): Int {
        var index = (address - 0x3F00) and 0x1F
        if (index == 0x10) index = 0
        if (index == 0x14) index = 4
        if (index == 0x18) index = 8
        if (index == 0x1C) index = 12
        return index
    }
}
