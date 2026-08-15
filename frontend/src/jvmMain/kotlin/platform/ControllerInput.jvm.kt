package platform

import co.touchlab.kermit.Logger
import frontend.EmulatorInput
import frontend.controllerSettings.*
import nes.input.NesController
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWGamepadState
import org.lwjgl.system.MemoryUtil
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import kotlin.time.Duration.Companion.seconds

actual class ControllerInput actual constructor(
    private val controller: NesController,
    private val inputMapper: ControllerInputMapper,
) : EmulatorInput {

    private var ignoredBindings = emptySet<Int>()

    private val joystick: Int
        get() = (GLFW_JOYSTICK_1..GLFW_JOYSTICK_LAST).firstOrNull { glfwJoystickPresent(it) } ?: -1
    private val log = Logger.withTag("ControllerInput")
    private val state = GLFWGamepadState.calloc()
    private var isInitialised = false

    @Synchronized
    actual override fun init() {
        Thread.sleep(2_000)
        for (i in 0..5) {
            log.i { "Trying to initialize gaming devices. Attempt $i / 5" }
            if (!glfwInit()) {
                log.e { "Unable to initialize GLFW" }
                return
            }

            val joystick = joystick
            if (joystick != -1) {
                break
            }

            glfwTerminate()
            Thread.sleep(200)
        }
        val joystick = joystick
        if (joystick < 0) {
            log.w { "No gamepad found" }
            return
        }
        try {
            loadBundledDatabase()
        } catch (e: Exception) {
            log.w(throwable = e) { "Error initializing database" }
        }
        isInitialised = true
        log.i { "Controller found" }
    }

    @Synchronized
    actual override fun poll() {
        currentPressedBindings().forEach(::pressMapped)
    }

    private fun pressMapped(button: InputButton) {
        val nesButton = inputMapper.map(button)
        if (nesButton != NO_NES_BUTTON) controller.press(nesButton)
    }

    override fun pause() = Unit

    @Synchronized
    override fun close() {
        state.free()
        glfwTerminate()
    }

    @Throws(IOException::class)
    fun loadBundledDatabase() {
        ControllerInput::class.java.getResourceAsStream("/gamecontrollerdb.txt").use { input ->
            if (input == null) {
                throw IOException("Missing resource: /gamecontrollerdb.txt")
            }
            val mappings = String(input.readAllBytes(), StandardCharsets.US_ASCII)

            val nativeString: ByteBuffer = MemoryUtil.memASCII(mappings)
            try {
                if (!glfwUpdateGamepadMappings(nativeString)) {
                    throw IOException("GLFW rejected gamecontrollerdb.txt")
                }
            } finally {
                MemoryUtil.memFree(nativeString)
            }
        }
    }

    private fun GLFWGamepadState.isPressed(button: Int): Boolean = this.buttons(button).toInt() == GLFW_PRESS

    actual fun pressedButtons(): Set<InputButton> {
        val current = currentPressedBindings()
        if (current.none { it.id in ignoredBindings }) ignoredBindings = emptySet()
        return current.filterTo(mutableSetOf()) { it.id !in ignoredBindings }
    }

    actual fun clearPressedBindings() {
        ignoredBindings = emptySet()
        ignoredBindings = currentPressedBindings().mapTo(mutableSetOf()) { it.id }
    }

    private fun currentPressedBindings(): Set<InputButton> {
        if (!isInitialised) return emptySet()
        glfwPollEvents()

        val currentJoystick = joystick
        if (currentJoystick < 0 || !glfwJoystickPresent(currentJoystick)) return emptySet()

        return if (glfwGetGamepadState(currentJoystick, state)) {
            buildSet {
                for (index in GLFW_GAMEPAD_BUTTON_A..GLFW_GAMEPAD_BUTTON_LAST) {
                    if (state.isPressed(index)) add(gamepadButton(index))
                }
                for (index in GLFW_GAMEPAD_AXIS_LEFT_X..GLFW_GAMEPAD_AXIS_LAST) {
                    addAxisBinding(index, state.axes(index))
                }
            }
        } else {
            rawPressedBindings(currentJoystick)
        }
    }

    private fun rawPressedBindings(currentJoystick: Int): Set<InputButton> = buildSet {
        glfwGetJoystickButtons(currentJoystick)?.let { buttons ->
            for (index in 0 until minOf(buttons.remaining(), GAMEPAD_BUTTON_COUNT)) {
                if (buttons.get(index).toInt() == GLFW_PRESS) add(gamepadButton(index))
            }
        }
        glfwGetJoystickAxes(currentJoystick)?.let { axes ->
            for (index in 0 until axes.remaining()) addAxisBinding(index, axes.get(index))
        }
        glfwGetJoystickHats(currentJoystick)?.let { hats ->
            for (index in 0 until hats.remaining()) addHatBindings(hats.get(index).toInt() and 0xFF)
        }
    }

    private fun MutableSet<InputButton>.addAxisBinding(index: Int, value: Float) {
        if (value < -AXIS_THRESHOLD) add(gamepadAxis(index, AXIS_NEGATIVE))
        if (value > AXIS_THRESHOLD) add(gamepadAxis(index, AXIS_POSITIVE))
    }

    private fun MutableSet<InputButton>.addHatBindings(value: Int) {
        if (value and GLFW_HAT_UP != 0) add(gamepadPov(POV_UP))
        if (value and GLFW_HAT_DOWN != 0) add(gamepadPov(POV_DOWN))
        if (value and GLFW_HAT_LEFT != 0) add(gamepadPov(POV_LEFT))
        if (value and GLFW_HAT_RIGHT != 0) add(gamepadPov(POV_RIGHT))
    }

    private companion object {
        const val AXIS_THRESHOLD = 0.5f
    }

}
