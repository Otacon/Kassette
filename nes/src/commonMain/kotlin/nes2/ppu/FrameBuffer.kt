package nes2.ppu

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

interface FrameBuffer {
    fun writePixel(x: Int, y: Int, color: Int)
    fun frameCompleted()
}

class FramebufferNes(
    val pixels: ByteArray = ByteArray(256 * 240),
) : FrameBuffer {

    private val _frames = MutableSharedFlow<Unit>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val frames = _frames.asSharedFlow()

    override fun writePixel(x: Int, y: Int, color: Int) {
        pixels[y * 256 + x] = color.toByte()
    }

    override fun frameCompleted() {
        _frames.tryEmit(Unit)
    }
}
