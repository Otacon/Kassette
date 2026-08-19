package nes2.ppu

interface FrameBuffer {
    fun writePixel(x: Int, y: Int, color: Int)
}

class FramebufferNes(
    val pixels: ByteArray = ByteArray(256 * 240 * 4),
) : FrameBuffer {

    override fun writePixel(x: Int, y: Int, color: Int) {
        val offset = (y * 256 + x) * 4

        // color -> NES palette lookup later
        pixels[offset] = 0
        pixels[offset + 1] = 0
        pixels[offset + 2] = 0
        pixels[offset + 3] = 0xFF.toByte()
    }
}