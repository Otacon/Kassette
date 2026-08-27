package frontend

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class SharedFrameBuffer {
    private val blankFrame = IntArray(256 * 240)
    private val _frameTicks = MutableStateFlow(0L)

    var currentFrame: IntArray = blankFrame
        private set
    val frameTicks = _frameTicks.asStateFlow()

    fun submit(framebuffer: IntArray, frameCount: Int) {
        currentFrame = framebuffer
        _frameTicks.value = frameCount.toLong()
    }
}
