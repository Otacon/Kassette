package frontend

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class SharedFrameBuffer {
    private val blankFrame = ByteArray(256 * 240)
    private val _frameTicks = MutableStateFlow(0L)

    var currentFrame: ByteArray = blankFrame
        private set
    val frameTicks = _frameTicks.asStateFlow()

    fun submit(framebuffer: ByteArray, frameCount: Int) {
        currentFrame = framebuffer
        _frameTicks.value = frameCount.toLong()
    }
}
