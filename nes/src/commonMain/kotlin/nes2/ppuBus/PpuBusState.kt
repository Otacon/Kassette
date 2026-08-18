package nes2.ppuBus

data class PpuBusState(
    val nametableRam: IntArray = IntArray(0x800),
    val paletteRam: IntArray = IntArray(0x20),
)