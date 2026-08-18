package nes2.ppu

data class PpuState(
    var control: Int = 0,
    var oamAddress: Int = 0,
    var status: Int = 0,
    val oam: IntArray = IntArray(256),
    var writeToggle: Boolean = false,
)