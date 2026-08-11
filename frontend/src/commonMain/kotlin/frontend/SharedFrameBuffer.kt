package frontend

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class SharedFrameBuffer : VideoOutput {
    val initialFrame = ByteArray(256 * 240)
    private val _frames = MutableSharedFlow<ByteArray>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val frames = _frames.asSharedFlow()

    override fun submit(framebuffer: ByteArray) {
        _frames.tryEmit(framebuffer)
    }
}
