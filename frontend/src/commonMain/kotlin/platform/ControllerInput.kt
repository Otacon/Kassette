package platform

import frontend.EmulatorInput
import frontend.controllerSettings.ControllerInputMapper
import frontend.controllerSettings.InputButton
import nes.input.NesController


expect class ControllerInput(controller: NesController, inputMapper: ControllerInputMapper) : EmulatorInput {

    fun pressedButtons(): Set<InputButton>

    fun clearPressedBindings()

    override fun init()

    override fun poll()
}
