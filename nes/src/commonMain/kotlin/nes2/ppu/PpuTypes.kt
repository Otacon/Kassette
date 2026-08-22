package nes2.ppu

data class PPUStatusFlags(
    var spriteOverflow: Boolean = false,
    var sprite0Hit: Boolean = false,
    var verticalBlank: Boolean = false,
)

data class PpuControlFlags(
    var backgroundPatternAddr: Int = 0,
    var spritePatternAddr: Int = 0,
    var verticalWrite: Boolean = false,
    var largeSprites: Boolean = false,
    var secondaryPpu: Boolean = false,
    var nmiOnVerticalBlank: Boolean = false,
)

data class PpuMaskFlags(
    var grayscale: Boolean = false,
    var backgroundMask: Boolean = false,
    var spriteMask: Boolean = false,
    var backgroundEnabled: Boolean = false,
    var spritesEnabled: Boolean = false,
    var intensifyRed: Boolean = false,
    var intensifyGreen: Boolean = false,
    var intensifyBlue: Boolean = false,
)

data class TileInfo(
    var tileAddr: Int = 0,
    var lowByte: Int = 0,
    var highByte: Int = 0,
    var paletteOffset: Int = 0,
)

data class NesSpriteInfo(
    var backgroundPriority: Boolean = false,
    var spriteX: Int = 0,
    var lowByte: Int = 0,
    var highByte: Int = 0,
    var paletteOffset: Int = 0,
)

data class NesPpuState(
    var statusFlags: PPUStatusFlags = PPUStatusFlags(),
    var mask: PpuMaskFlags = PpuMaskFlags(),
    var control: PpuControlFlags = PpuControlFlags(),
    var scanline: Int = 0,
    var cycle: Int = 0,
    var frameCount: Int = 0,
    var nmiScanline: Int = 0,
    var scanlineCount: Int = 0,
    var safeOamScanline: Int = 0,
    var busAddress: Int = 0,
    var memoryReadBuffer: Int = 0,
    var videoRamAddr: Int = 0,
    var tmpVideoRamAddr: Int = 0,
    var spriteRamAddr: Int = 0,
    var secondaryOamAddr: Int = 0,
    var scrollX: Int = 0,
    var writeToggle: Boolean = false,
)

enum class PpuRegisters(val id: Int) {
    Control(0x00),
    Mask(0x01),
    Status(0x02),
    SpriteAddr(0x03),
    SpriteData(0x04),
    ScrollOffsets(0x05),
    VideoMemoryAddr(0x06),
    VideoMemoryData(0x07),
    SpriteDMA(0x4014),
}
