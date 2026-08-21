package nes2.fakes

import nes2.ppu.FrameBuffer

class FakeFrameBuffer : FrameBuffer {

    data class WrittenPixel(
        val x: Int,
        val y: Int,
        val color: Int,
    )

    val writtenPixels = mutableListOf<WrittenPixel>()
    var frameCompleted = false

    override fun writePixel(x: Int, y: Int, color: Int) {
        writtenPixels += WrittenPixel(x = x, y = y, color = color)
    }

    override fun frameCompleted() {
        frameCompleted = true
    }
}