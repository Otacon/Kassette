package nes2.ppu

import nes2.console.NesConsole
import nes2.console.NesConstants
import nes2.console.NesConsolePpu
import nes2.cpu.ConsoleRegion
import nes2.mapper.BaseMapper
import nes2.mapper.PpuModel
import nes2.memory.INesMemoryHandler
import nes2.memory.MemoryRanges

abstract class BaseNesPpu(protected val console: NesConsole) : NesConsolePpu, INesMemoryHandler {
    protected var masterClock: Long = 0
    protected var cycle: Int = 0
    protected var scanline: Int = 0
    protected var emulatorBgEnabled: Boolean = false
    protected var emulatorSpritesEnabled: Boolean = false
    protected var videoRamAddr: Int = 0
    protected var tmpVideoRamAddr: Int = 0
    protected var highBitShift: Int = 0
    protected var lowBitShift: Int = 0
    protected var masterClockDivider: Int = 0
    protected var spriteRamAddr: Int = 0
    protected var openBus: Int = 0
    protected var xScroll: Int = 0
    protected var enableOamDecay: Boolean = false
    protected var needStateUpdate: Boolean = false
    protected var renderingEnabled: Boolean = false
    protected var prevRenderingEnabled: Boolean = false
    protected var sprite0Visible: Boolean = false
    protected var spriteCount: Int = 0
    protected var secondaryOamAddr: Int = 0
    protected var oamCopybuffer: Int = 0
    protected var spriteInRange: Boolean = false
    protected var sprite0Added: Boolean = false
    protected var overflowBugCounter: Int = 0
    protected var oamCopyDone: Boolean = false
    protected var ppuBusAddress: Int = 0
    protected var minimumDrawBgCycle: Int = 0
    protected var minimumDrawSpriteCycle: Int = 0
    protected var minimumDrawSpriteStandardCycle: Int = 0
    protected val mapper: BaseMapper get() = console.mapper as BaseMapper

    protected val paletteRam = IntArray(0x20)
    protected val secondarySpriteRam = IntArray(0x20)
    protected var tile: TileInfo = TileInfo()
    protected var vblankEnd: Int = 0
    protected var nmiScanline: Int = 0
    protected var currentTilePalette: Int = 0
    protected var previousTilePalette: Int = 0
    protected var intensifyColorBits: Int = 0
    protected var paletteRamMask: Int = 0
    protected var updateVramAddrDelay: Int = 0
    protected var spriteIndex: Int = 0
    protected var lastUpdatedPixel: Int = 0
    protected var frameCountValue: Int = 0
    protected var updateVramAddr: Int = 0
    protected var preventVblFlag: Boolean = false
    protected var writeToggle: Boolean = false
    protected var lastSprite: NesSpriteInfo? = null
    protected val control: PpuControlFlags = PpuControlFlags()
    protected val mask: PpuMaskFlags = PpuMaskFlags()
    protected val spriteRam = IntArray(0x100)
    protected val spriteTiles = Array(64) { NesSpriteInfo() }
    protected val spriteShifterList = IntArray(9) { SpriteShifterDone }
    protected var nextSpriteShifter: Int = 0
    protected var nextSpriteShifterCycle: Int = 0
    protected var activeSpriteShifters: Int = 0
    protected var countingSpriteShifters: Int = 0
    protected var expiredSpriteShifters: Int = 0
    protected var dotSkipped: Int = 0
    protected var processSprites: Boolean = false
    protected val outputBuffers = Array(2) { IntArray(NesConstants.ScreenPixelCount) }
    protected var currentOutputBuffer: IntArray = outputBuffers[0]
    protected var region: ConsoleRegion = ConsoleRegion.Ntsc
    protected var standardVblankEnd: Int = 0
    protected var standardNmiScanline: Int = 0
    protected var palSpriteEvalScanline: Int = 0
    protected var needVideoRamIncrement: Boolean = false
    protected var allowFullPpuAccess: Boolean = false
    protected var ppuMemoryDataReadStateMachine: Int = 0
    protected var ppuMemoryDataWriteStateMachine: Int = 0
    protected var ppuMemoryDataWriteLatch: Int = 0
    protected var memoryReadBuffer: Int = 0
    protected val statusFlags: PPUStatusFlags = PPUStatusFlags()
    protected var firstVisibleSpriteAddr: Int = 0
    protected var lastVisibleSpriteAddr: Int = 0
    protected var ignoreVramRead: Int = 0
    protected val openBusDecayStamp = IntArray(8)
    protected val oamDecayCycles = LongArray(0x20)

    override val frameCount: Int get() = frameCountValue
    fun getCurrentCycle(): Int = cycle
    fun getCurrentScanline(): Int = scanline
    fun getScanlineCount(): Int = vblankEnd + 2
    fun getFrameCycle(): Int = ((scanline + 1) * 341) + cycle
    fun isRenderingEnabled(): Boolean = renderingEnabled

    abstract override fun reset(softReset: Boolean)
    abstract override fun run(masterClock: Long)
    abstract fun getScreenBuffer(previousBuffer: Boolean, processGrayscaleEmphasisBits: Boolean = false): IntArray
    abstract fun getPpuModel(): PpuModel
    abstract fun getPixelBrightness(x: Int, y: Int): Int

    fun getState(): NesPpuState = NesPpuState(
        control = control.copy(),
        mask = mask.copy(),
        statusFlags = statusFlags.copy(),
        videoRamAddr = videoRamAddr,
        tmpVideoRamAddr = tmpVideoRamAddr,
        spriteRamAddr = spriteRamAddr,
        secondaryOamAddr = secondaryOamAddr,
        scrollX = xScroll,
        writeToggle = writeToggle,
        cycle = cycle,
        scanline = scanline,
        frameCount = frameCountValue,
        nmiScanline = nmiScanline,
        scanlineCount = vblankEnd + 2,
        safeOamScanline = if (region == ConsoleRegion.Ntsc) nmiScanline + 19 else palSpriteEvalScanline,
        busAddress = ppuBusAddress,
        memoryReadBuffer = memoryReadBuffer,
    )

    fun setState(state: NesPpuState) {
        copyControl(state.control)
        copyMask(state.mask)
        copyStatus(state.statusFlags)
        videoRamAddr = state.videoRamAddr and 0x7FFF
        tmpVideoRamAddr = state.tmpVideoRamAddr and 0x7FFF
        xScroll = state.scrollX and 0xFF
        writeToggle = state.writeToggle
        spriteRamAddr = state.spriteRamAddr and 0xFF
        secondaryOamAddr = state.secondaryOamAddr and 0xFF
        cycle = state.cycle
        scanline = state.scanline
        frameCountValue = state.frameCount
        ppuBusAddress = state.busAddress and 0x3FFF
        memoryReadBuffer = state.memoryReadBuffer and 0xFF
        if (renderingEnabled != (mask.backgroundEnabled || mask.spritesEnabled)) needStateUpdate = true
        updateMinimumDrawCycles()
        updateGrayscaleAndIntensifyBits()
    }

    fun getCurrentBgColor(): Int {
        val color = if ((isRenderingEnabled() && scanline < 240) || (ppuBusAddress and 0x3F00) != 0x3F00) {
            paletteRam[0]
        } else {
            paletteRam[ppuBusAddress and 0x1F]
        }
        return (color and paletteRamMask) or intensifyColorBits
    }

    fun readPaletteRam(addrValue: Int): Int {
        var addr = addrValue and 0x1F
        if (addr == 0x10 || addr == 0x14 || addr == 0x18 || addr == 0x1C) addr = addr and 0x10.inv()
        return paletteRam[addr] and 0xFF
    }

    fun writePaletteRam(addrValue: Int, valueValue: Int) {
        val value = valueValue and 0x3F
        when (val addr = addrValue and 0x1F) {
            0x00, 0x10 -> { paletteRam[0x00] = value; paletteRam[0x10] = value }
            0x04, 0x14 -> { paletteRam[0x04] = value; paletteRam[0x14] = value }
            0x08, 0x18 -> { paletteRam[0x08] = value; paletteRam[0x18] = value }
            0x0C, 0x1C -> { paletteRam[0x0C] = value; paletteRam[0x1C] = value }
            else -> paletteRam[addr] = value
        }
    }

    fun debugSendFrame() {
        val offset = maxOf(0, cycle + scanline * NesConstants.ScreenWidth)
        var i = offset
        while (i < currentOutputBuffer.size) currentOutputBuffer[i++] = 0
    }

    protected fun updateGrayscaleAndIntensifyBits() {
        if (scanline < 0 || scanline > nmiScanline) {
            updateColorBitMasks()
            return
        }

        val pixelNumber = when {
            scanline >= 240 -> 61439
            cycle < 3 -> (scanline shl 8) - 1
            cycle <= 258 -> (scanline shl 8) + cycle - 3
            else -> (scanline shl 8) + 255
        }

        if (paletteRamMask == 0x3F && intensifyColorBits == 0) {
            updateColorBitMasks()
            lastUpdatedPixel = pixelNumber
            return
        }

        while (lastUpdatedPixel < pixelNumber && lastUpdatedPixel + 1 in currentOutputBuffer.indices) {
            lastUpdatedPixel++
            currentOutputBuffer[lastUpdatedPixel] = (currentOutputBuffer[lastUpdatedPixel] and paletteRamMask) or intensifyColorBits
        }
        updateColorBitMasks()
    }

    protected fun updateColorBitMasks() {
        paletteRamMask = if (mask.grayscale) 0x30 else 0x3F
        intensifyColorBits = (if (mask.intensifyRed) 0x40 else 0) or
            (if (mask.intensifyGreen) 0x80 else 0) or
            (if (mask.intensifyBlue) 0x100 else 0)
    }

    protected fun updateMinimumDrawCycles() {
        val cfg = consoleOptions()
        minimumDrawBgCycle = if (mask.backgroundEnabled) if (mask.backgroundMask || cfg.forceBackgroundFirstColumn) 0 else 8 else 300
        minimumDrawSpriteCycle = if (mask.spritesEnabled) if (mask.spriteMask || cfg.forceSpritesFirstColumn) 0 else 8 else 300
        minimumDrawSpriteStandardCycle = if (mask.spritesEnabled) if (mask.spriteMask) 0 else 8 else 300
        emulatorBgEnabled = cfg.backgroundEnabled
        emulatorSpritesEnabled = cfg.spritesEnabled
    }

    protected fun consoleOptions() = console.options.ppu

    override fun initConsole(console: NesConsole) {}
    override fun getMemoryRanges(ranges: MemoryRanges) {}
    override fun readRam(addr: Int): Int = 0
    override fun writeRam(addr: Int, value: Int) {}

    private fun copyControl(source: PpuControlFlags) {
        control.backgroundPatternAddr = source.backgroundPatternAddr
        control.spritePatternAddr = source.spritePatternAddr
        control.verticalWrite = source.verticalWrite
        control.largeSprites = source.largeSprites
        control.secondaryPpu = source.secondaryPpu
        control.nmiOnVerticalBlank = source.nmiOnVerticalBlank
    }

    private fun copyMask(source: PpuMaskFlags) {
        mask.grayscale = source.grayscale
        mask.backgroundMask = source.backgroundMask
        mask.spriteMask = source.spriteMask
        mask.backgroundEnabled = source.backgroundEnabled
        mask.spritesEnabled = source.spritesEnabled
        mask.intensifyRed = source.intensifyRed
        mask.intensifyGreen = source.intensifyGreen
        mask.intensifyBlue = source.intensifyBlue
    }

    private fun copyStatus(source: PPUStatusFlags) {
        statusFlags.spriteOverflow = source.spriteOverflow
        statusFlags.sprite0Hit = source.sprite0Hit
        statusFlags.verticalBlank = source.verticalBlank
    }

    companion object {
        const val SpriteShifterDone = 0x8000
    }
}
