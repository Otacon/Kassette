package nes2.ppu

data class PpuState(
    var control: Int = 0,
    var status: Int = 0,

    var oamAddress: Int = 0,
    val oam: IntArray = IntArray(256),

    var v: Int = 0,
    var t: Int = 0,
    var fineX: Int = 0,

    var writeToggle: Boolean = false,
)