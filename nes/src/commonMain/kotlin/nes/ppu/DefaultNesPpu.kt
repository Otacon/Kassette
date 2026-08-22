/*
 * This file is part of Kassette.
 *
 * This Kotlin implementation is ported and adapted from MesenCE
 * (https://github.com/nesdev-org/MesenCE). MesenCE is licensed under
 * the GNU General Public License version 3.
 *
 * This modified Kotlin port is distributed under the GNU General Public
 * License version 3. See the repository LICENSE file for details.
 */

package nes.ppu

import nes.console.NesConstants

class DefaultNesPpu : NesPpu() {
    val frameColorIds = ByteArray(NesConstants.ScreenPixelCount)

    override fun storeSpriteInformation(horizontalMirror: Boolean, verticalMirror: Boolean, tileAddr: Int, lineOffset: Int, sprite: NesSpriteInfo) {}
    override fun storeTileInformation() {}
    override fun pushTileInformation() {}
    override fun removeSpriteLimit(): Boolean = console.options.ppu.removeSpriteLimit
    override fun useAdaptiveSpriteLimit(): Boolean = console.options.ppu.adaptiveSpriteLimit
    fun onBeforeSendFrame(): Any? = null

    override fun getPixelBrightness(x: Int, y: Int): Int = getPixel(x, y) and 0x3F

    fun getPixel(x: Int, y: Int): Int = currentOutputBuffer[((y and 0xFF) shl 8) or (x and 0xFF)]

    override fun processScanline() = processScanlineImpl()

    override fun drawPixel() {
        val index = (scanline shl 8) + cycle - 1
        if (index < 0 || index >= currentOutputBuffer.size) return
        val color = if (isRenderingEnabled() || ((videoRamAddr and 0x3F00) != 0x3F00)) {
            paletteRam[getPixelColor().let { if ((it and 0x03) != 0) it else 0 } and 0x1F]
        } else {
            paletteRam[videoRamAddr and 0x1F]
        }
        currentOutputBuffer[index] = color
        frameColorIds[index] = (color and 0x3F).toByte()
    }
}
