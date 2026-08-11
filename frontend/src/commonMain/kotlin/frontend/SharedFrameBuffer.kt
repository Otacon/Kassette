package frontend

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class SharedFrameBuffer : VideoOutput {
    val initialFrame = IntArray(256 * 240)
    private val _frames = MutableSharedFlow<IntArray>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val frames = _frames.asSharedFlow()

    override fun submit(framebuffer: IntArray) {
        _frames.tryEmit(framebuffer)
    }
}
