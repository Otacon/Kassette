package nes2.ppu

import nes2.console.NesConsole
import nes2.console.NesConstants

class DefaultNesPpu(console: NesConsole) : NesPpu(console) {
    fun storeSpriteInformation(horizontalMirror: Boolean, verticalMirror: Boolean, tileAddr: Int, lineOffset: Int, sprite: NesSpriteInfo) {}
    fun storeTileInformation() {}
    fun pushTileInformation() {}
    fun removeSpriteLimit(): Boolean = console.options.ppu.removeSpriteLimit
    fun useAdaptiveSpriteLimit(): Boolean = console.options.ppu.adaptiveSpriteLimit
    fun onBeforeSendFrame(): Any? = null

    override fun getScreenBuffer(previousBuffer: Boolean, processGrayscaleEmphasisBits: Boolean): IntArray =
        if (previousBuffer) outputBuffers[if (currentOutputBuffer === outputBuffers[0]) 1 else 0] else currentOutputBuffer

    override fun getPixelBrightness(x: Int, y: Int): Int = getPixel(x, y) and 0x3F

    fun getPixel(x: Int, y: Int): Int = currentOutputBuffer[((y and 0xFF) shl 8) or (x and 0xFF)]

    override fun processScanline() {
        if (scanline in 0 until NesConstants.ScreenHeight && cycle in 1..256) drawPixel()
    }

    private fun drawPixel() {
        val index = (scanline shl 8) + cycle - 1
        if (index !in currentOutputBuffer.indices) return
        currentOutputBuffer[index] = if (isRenderingEnabled() || ((videoRamAddr and 0x3F00) != 0x3F00)) {
            paletteRam[0]
        } else {
            paletteRam[videoRamAddr and 0x1F]
        }
    }
}
