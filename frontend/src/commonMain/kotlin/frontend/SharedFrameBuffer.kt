package frontend

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import nes2.ppu.FrameBuffer

class SharedFrameBuffer : FrameBuffer {
    val pixels = ByteArray(256 * 240)
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
