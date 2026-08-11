package frontend

import androidx.compose.ui.input.key.KeyEvent
import frontend.controllerSettings.ControllerInputMapper
import frontend.controllerSettings.InputButton
import io.VideoFilter
import nes.input.NesController
import org.jetbrains.skia.Canvas

interface Renderer {
    fun init(videoFilter: VideoFilter)

    fun present(framebuffer: ByteArray, windowWidth: Int, windowHeight: Int)

    fun draw(canvas: Canvas)

    fun close()
}

expect class PlatformAudioPipeline() : AudioPipeline {
    override fun submit(samples: ShortArray, count: Int)
}

expect class PlatformKeyboardInput(controller: NesController, inputMapper: ControllerInputMapper) : EmulatorInput {
    fun onKeyEvent(event: KeyEvent): Boolean

    fun releaseAll()

    override fun init()

    override fun poll()
}

expect class PlatformControllerInput(controller: NesController, inputMapper: ControllerInputMapper) : EmulatorInput {

    fun pressedButtons(): Set<InputButton>

    fun clearPressedBindings()

    override fun init()

    override fun poll()
}
