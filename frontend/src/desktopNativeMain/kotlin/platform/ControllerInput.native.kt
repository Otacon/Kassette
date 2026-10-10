package platform

import cnames.structs.SDL_Gamepad
import frontend.EmulatorInput
import frontend.controllerSettings.AXIS_NEGATIVE
import frontend.controllerSettings.AXIS_POSITIVE
import frontend.controllerSettings.ControllerInputMapper
import frontend.controllerSettings.InputButton
import frontend.controllerSettings.NO_NES_BUTTON
import frontend.controllerSettings.gamepadAxis
import frontend.controllerSettings.gamepadButton
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import nes.input.NesController
import sdl3.SDL_CloseGamepad
import sdl3.SDL_GAMEPAD_AXIS_LEFTX
import sdl3.SDL_GAMEPAD_AXIS_LEFTY
import sdl3.SDL_GAMEPAD_BUTTON_BACK
import sdl3.SDL_GAMEPAD_BUTTON_DPAD_DOWN
import sdl3.SDL_GAMEPAD_BUTTON_DPAD_LEFT
import sdl3.SDL_GAMEPAD_BUTTON_DPAD_RIGHT
import sdl3.SDL_GAMEPAD_BUTTON_DPAD_UP
import sdl3.SDL_GAMEPAD_BUTTON_EAST
import sdl3.SDL_GAMEPAD_BUTTON_NORTH
import sdl3.SDL_GAMEPAD_BUTTON_SOUTH
import sdl3.SDL_GAMEPAD_BUTTON_START
import sdl3.SDL_GAMEPAD_BUTTON_WEST
import sdl3.SDL_GetGamepadAxis
import sdl3.SDL_GetGamepadButton
import sdl3.SDL_GetGamepads
import sdl3.SDL_INIT_GAMEPAD
import sdl3.SDL_InitSubSystem
import sdl3.SDL_IsGamepad
import sdl3.SDL_OpenGamepad
import sdl3.SDL_UpdateGamepads
import sdl3.SDL_free

@OptIn(ExperimentalForeignApi::class)
actual class ControllerInput actual constructor(
    private val controller: NesController,
    private val inputMapper: ControllerInputMapper,
) : EmulatorInput {
    private var gamepad: CPointer<SDL_Gamepad>? = null
    private var ignoredBindings = emptySet<Int>()

    actual fun pressedButtons(): Set<InputButton> {
        val current = currentPressedBindings()
        if (current.none { it.id in ignoredBindings }) ignoredBindings = emptySet()
        return current.filterTo(mutableSetOf()) { it.id !in ignoredBindings }
    }

    actual fun clearPressedBindings() {
        ignoredBindings = emptySet()
        ignoredBindings = currentPressedBindings().mapTo(mutableSetOf()) { it.id }
    }

    actual override fun init() {
        SDL_InitSubSystem(SDL_INIT_GAMEPAD)
        openFirstGamepad()
    }

    actual override fun poll() {
        currentPressedBindings().forEach(::pressMapped)
    }

    override fun close() {
        gamepad?.let { SDL_CloseGamepad(it) }
        gamepad = null
    }

    private fun pressMapped(button: InputButton) {
        val nesButton = inputMapper.map(button)
        if (nesButton != NO_NES_BUTTON) controller.press(nesButton)
    }

    private fun currentPressedBindings(): Set<InputButton> {
        if (gamepad == null) openFirstGamepad()
        val gamepad = gamepad ?: return emptySet()
        SDL_UpdateGamepads()
        return buildSet {
            addButtonIfPressed(gamepad, SDL_GAMEPAD_BUTTON_SOUTH)
            addButtonIfPressed(gamepad, SDL_GAMEPAD_BUTTON_EAST)
            addButtonIfPressed(gamepad, SDL_GAMEPAD_BUTTON_WEST)
            addButtonIfPressed(gamepad, SDL_GAMEPAD_BUTTON_NORTH)
            addButtonIfPressed(gamepad, SDL_GAMEPAD_BUTTON_BACK)
            addButtonIfPressed(gamepad, SDL_GAMEPAD_BUTTON_START)
            addButtonIfPressed(gamepad, SDL_GAMEPAD_BUTTON_DPAD_UP)
            addButtonIfPressed(gamepad, SDL_GAMEPAD_BUTTON_DPAD_DOWN)
            addButtonIfPressed(gamepad, SDL_GAMEPAD_BUTTON_DPAD_LEFT)
            addButtonIfPressed(gamepad, SDL_GAMEPAD_BUTTON_DPAD_RIGHT)
            addAxisBinding(0, axis(gamepad, SDL_GAMEPAD_AXIS_LEFTX))
            addAxisBinding(1, axis(gamepad, SDL_GAMEPAD_AXIS_LEFTY))
        }
    }

    private fun MutableSet<InputButton>.addButtonIfPressed(gamepad: CPointer<SDL_Gamepad>, button: Int) {
        if (isPressed(gamepad, button)) add(gamepadButton(button))
    }

    private fun MutableSet<InputButton>.addAxisBinding(index: Int, value: Int) {
        if (value < -AXIS_THRESHOLD) add(gamepadAxis(index, AXIS_NEGATIVE))
        if (value > AXIS_THRESHOLD) add(gamepadAxis(index, AXIS_POSITIVE))
    }

    private fun openFirstGamepad() = memScoped {
        val count = alloc<IntVar>()
        val ids = SDL_GetGamepads(count.ptr) ?: return@memScoped
        try {
            for (index in 0 until count.value) {
                val id = ids[index]
                if (SDL_IsGamepad(id)) {
                    gamepad = SDL_OpenGamepad(id)
                    if (gamepad != null) return@memScoped
                }
            }
        } finally {
            SDL_free(ids)
        }
    }

    private fun isPressed(gamepad: CPointer<SDL_Gamepad>, button: Int): Boolean =
        SDL_GetGamepadButton(gamepad, button)

    private fun axis(gamepad: CPointer<SDL_Gamepad>, axis: Int): Int =
        SDL_GetGamepadAxis(gamepad, axis).toInt()

    private companion object {
        const val AXIS_THRESHOLD = 16_000
    }
}
