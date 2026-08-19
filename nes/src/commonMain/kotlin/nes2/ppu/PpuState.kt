package nes2.ppu

data class PpuState(
    var control: Int = 0,
    var mask: Int = 0,
    var status: Int = 0,
    var dot: Int = 0,
    var scanline: Int = 0,

    var nametableByte: Int = 0,
    var attributeByte: Int = 0,
    var patternLowByte: Int = 0,
    var patternHighByte: Int = 0,

    var patternLowShift: Int = 0,
    var patternHighShift: Int = 0,

    var attributeLowShift: Int = 0,
    var attributeHighShift: Int = 0,

    var oamAddress: Int = 0,
    val oam: IntArray = IntArray(256),

    var v: Int = 0,
    var t: Int = 0,
    var fineX: Int = 0,

    var oddFrame: Boolean = false,
    var writeToggle: Boolean = false,

    var dataBuffer: Int = 0
)