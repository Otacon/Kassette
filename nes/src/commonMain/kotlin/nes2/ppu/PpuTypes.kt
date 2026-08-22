package nes2.ppu

import kotlinx.serialization.Serializable

@Serializable
data class PPUStatusFlags(
    var spriteOverflow: Boolean = false,
    var sprite0Hit: Boolean = false,
    var verticalBlank: Boolean = false,
)

@Serializable
data class PpuControlFlags(
    var backgroundPatternAddr: Int = 0,
    var spritePatternAddr: Int = 0,
    var verticalWrite: Boolean = false,
    var largeSprites: Boolean = false,
    var secondaryPpu: Boolean = false,
    var nmiOnVerticalBlank: Boolean = false,
)

@Serializable
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

@Serializable
data class TileInfo(
    var tileAddr: Int = 0,
    var lowByte: Int = 0,
    var highByte: Int = 0,
    var paletteOffset: Int = 0,
)

@Serializable
data class NesSpriteInfo(
    var backgroundPriority: Boolean = false,
    var spriteX: Int = 0,
    var lowByte: Int = 0,
    var highByte: Int = 0,
    var paletteOffset: Int = 0,
)

@Serializable
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

@Serializable
data class NesPpuSnapshot(
    var paletteRam: IntArray = IntArray(0x20),
    var spriteRam: IntArray = IntArray(0x100),
    var secondarySpriteRam: IntArray = IntArray(0x20),
    var openBusDecayStamp: IntArray = IntArray(8),
    var spriteRamAddr: Int = 0,
    var videoRamAddr: Int = 0,
    var xScroll: Int = 0,
    var tmpVideoRamAddr: Int = 0,
    var writeToggle: Boolean = false,
    var highBitShift: Int = 0,
    var lowBitShift: Int = 0,
    var control: PpuControlFlags = PpuControlFlags(),
    var mask: PpuMaskFlags = PpuMaskFlags(),
    var paletteRamMask: Int = 0,
    var intensifyColorBits: Int = 0,
    var statusFlags: PPUStatusFlags = PPUStatusFlags(),
    var scanline: Int = 0,
    var cycle: Int = 0,
    var frameCount: Int = 0,
    var memoryReadBuffer: Int = 0,
    var region: nes2.cpu.ConsoleRegion = nes2.cpu.ConsoleRegion.Ntsc,
    var ppuBusAddress: Int = 0,
    var masterClock: Long = 0,
    var currentTilePalette: Int = 0,
    var tile: TileInfo = TileInfo(),
    var previousTilePalette: Int = 0,
    var spriteIndex: Int = 0,
    var spriteCount: Int = 0,
    var sprite0Added: Boolean = false,
    var sprite0Visible: Boolean = false,
    var oamCopybuffer: Int = 0,
    var secondaryOamAddr: Int = 0,
    var spriteInRange: Boolean = false,
    var prevRenderingEnabled: Boolean = false,
    var renderingEnabled: Boolean = false,
    var openBus: Int = 0,
    var ignoreVramRead: Int = 0,
    var spriteShifterList: IntArray = IntArray(9),
    var nextSpriteShifter: Int = 0,
    var nextSpriteShifterCycle: Int = 0,
    var activeSpriteShifters: Int = 0,
    var countingSpriteShifters: Int = 0,
    var expiredSpriteShifters: Int = 0,
    var dotSkipped: Int = 0,
    var processSprites: Boolean = false,
    var oamCopyDone: Boolean = false,
    var needStateUpdate: Boolean = false,
    var preventVblFlag: Boolean = false,
    var needVideoRamIncrement: Boolean = false,
    var overflowBugCounter: Int = 0,
    var updateVramAddr: Int = 0,
    var updateVramAddrDelay: Int = 0,
    var allowFullPpuAccess: Boolean = false,
    var ppuMemoryDataReadStateMachine: Int = 0,
    var ppuMemoryDataWriteStateMachine: Int = 0,
    var ppuMemoryDataWriteLatch: Int = 0,
    var spriteTiles: Array<NesSpriteInfo> = Array(64) { NesSpriteInfo() },
    var firstVisibleSpriteAddr: Int = 0,
    var lastVisibleSpriteAddr: Int = 0,
    var oamDecayCycles: LongArray = LongArray(0x20),
    var currentOutputBufferIndex: Int = 0,
    var outputBuffers: Array<IntArray> = Array(2) { IntArray(nes2.console.NesConstants.ScreenPixelCount) },
) {
    override fun equals(other: Any?): Boolean = other is NesPpuSnapshot &&
        paletteRam.contentEquals(other.paletteRam) &&
        spriteRam.contentEquals(other.spriteRam) &&
        secondarySpriteRam.contentEquals(other.secondarySpriteRam) &&
        openBusDecayStamp.contentEquals(other.openBusDecayStamp) &&
        spriteRamAddr == other.spriteRamAddr &&
        videoRamAddr == other.videoRamAddr &&
        xScroll == other.xScroll &&
        tmpVideoRamAddr == other.tmpVideoRamAddr &&
        writeToggle == other.writeToggle &&
        highBitShift == other.highBitShift &&
        lowBitShift == other.lowBitShift &&
        control == other.control &&
        mask == other.mask &&
        paletteRamMask == other.paletteRamMask &&
        intensifyColorBits == other.intensifyColorBits &&
        statusFlags == other.statusFlags &&
        scanline == other.scanline &&
        cycle == other.cycle &&
        frameCount == other.frameCount &&
        memoryReadBuffer == other.memoryReadBuffer &&
        region == other.region &&
        ppuBusAddress == other.ppuBusAddress &&
        masterClock == other.masterClock &&
        currentTilePalette == other.currentTilePalette &&
        tile == other.tile &&
        previousTilePalette == other.previousTilePalette &&
        spriteIndex == other.spriteIndex &&
        spriteCount == other.spriteCount &&
        sprite0Added == other.sprite0Added &&
        sprite0Visible == other.sprite0Visible &&
        oamCopybuffer == other.oamCopybuffer &&
        secondaryOamAddr == other.secondaryOamAddr &&
        spriteInRange == other.spriteInRange &&
        prevRenderingEnabled == other.prevRenderingEnabled &&
        renderingEnabled == other.renderingEnabled &&
        openBus == other.openBus &&
        ignoreVramRead == other.ignoreVramRead &&
        spriteShifterList.contentEquals(other.spriteShifterList) &&
        nextSpriteShifter == other.nextSpriteShifter &&
        nextSpriteShifterCycle == other.nextSpriteShifterCycle &&
        activeSpriteShifters == other.activeSpriteShifters &&
        countingSpriteShifters == other.countingSpriteShifters &&
        expiredSpriteShifters == other.expiredSpriteShifters &&
        dotSkipped == other.dotSkipped &&
        processSprites == other.processSprites &&
        oamCopyDone == other.oamCopyDone &&
        needStateUpdate == other.needStateUpdate &&
        preventVblFlag == other.preventVblFlag &&
        needVideoRamIncrement == other.needVideoRamIncrement &&
        overflowBugCounter == other.overflowBugCounter &&
        updateVramAddr == other.updateVramAddr &&
        updateVramAddrDelay == other.updateVramAddrDelay &&
        allowFullPpuAccess == other.allowFullPpuAccess &&
        ppuMemoryDataReadStateMachine == other.ppuMemoryDataReadStateMachine &&
        ppuMemoryDataWriteStateMachine == other.ppuMemoryDataWriteStateMachine &&
        ppuMemoryDataWriteLatch == other.ppuMemoryDataWriteLatch &&
        spriteTiles.contentEquals(other.spriteTiles) &&
        firstVisibleSpriteAddr == other.firstVisibleSpriteAddr &&
        lastVisibleSpriteAddr == other.lastVisibleSpriteAddr &&
        oamDecayCycles.contentEquals(other.oamDecayCycles) &&
        currentOutputBufferIndex == other.currentOutputBufferIndex &&
        outputBuffers.contentDeepEquals(other.outputBuffers)

    override fun hashCode(): Int {
        var result = paletteRam.contentHashCode()
        result = 31 * result + spriteRam.contentHashCode()
        result = 31 * result + secondarySpriteRam.contentHashCode()
        result = 31 * result + openBusDecayStamp.contentHashCode()
        result = 31 * result + spriteShifterList.contentHashCode()
        result = 31 * result + spriteTiles.contentHashCode()
        result = 31 * result + oamDecayCycles.contentHashCode()
        result = 31 * result + outputBuffers.contentDeepHashCode()
        result = 31 * result + frameCount
        result = 31 * result + cycle
        result = 31 * result + scanline
        return result
    }
}

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
