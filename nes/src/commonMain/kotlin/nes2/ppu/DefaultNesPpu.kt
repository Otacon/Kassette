package nes2.ppu

import nes2.console.NesConsole
import nes2.console.NesConstants

class DefaultNesPpu(console: NesConsole) : NesPpu(console) {
    override fun storeSpriteInformation(horizontalMirror: Boolean, verticalMirror: Boolean, tileAddr: Int, lineOffset: Int, sprite: NesSpriteInfo) {}
    override fun storeTileInformation() {}
    override fun pushTileInformation() {}
    override fun removeSpriteLimit(): Boolean = console.options.ppu.removeSpriteLimit
    override fun useAdaptiveSpriteLimit(): Boolean = console.options.ppu.adaptiveSpriteLimit
    fun onBeforeSendFrame(): Any? = null

    override fun getPixelBrightness(x: Int, y: Int): Int = getPixel(x, y) and 0x3F

    fun getPixel(x: Int, y: Int): Int = currentOutputBuffer[((y and 0xFF) shl 8) or (x and 0xFF)]

    override fun processScanline() {
        if (scanline in 0 until NesConstants.ScreenHeight && cycle in 1..256) drawPixel()
    }

    override fun drawPixel() {
        val index = (scanline shl 8) + cycle - 1
        if (index !in currentOutputBuffer.indices) return
        currentOutputBuffer[index] = if (isRenderingEnabled() || ((videoRamAddr and 0x3F00) != 0x3F00)) {
            paletteRam[getPixelColor().let { if ((it and 0x03) != 0) it else 0 } and 0x1F]
        } else {
            paletteRam[videoRamAddr and 0x1F]
        }
    }
}
