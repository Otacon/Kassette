package platform

import androidx.compose.ui.input.key.KeyEvent
import frontend.EmulatorInput
import frontend.controllerSettings.ControllerInputMapper
import nes.input.NesController

expect class KeyboardInput(controller: NesController, inputMapper: ControllerInputMapper) : EmulatorInput {
    fun onKeyEvent(event: KeyEvent): Boolean

    fun releaseAll()

    override fun init()

    override fun poll()
}