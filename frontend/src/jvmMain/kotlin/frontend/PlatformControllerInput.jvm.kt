package frontend

import frontend.controllerSettings.ControllerInputMapper
import frontend.controllerSettings.AXIS_NEGATIVE
import frontend.controllerSettings.AXIS_POSITIVE
import frontend.controllerSettings.InputButton
import frontend.controllerSettings.GAMEPAD_BUTTON_COUNT
import frontend.controllerSettings.NO_NES_BUTTON
import frontend.controllerSettings.POV_DOWN
import frontend.controllerSettings.POV_LEFT
import frontend.controllerSettings.POV_RIGHT
import frontend.controllerSettings.POV_UP
import frontend.controllerSettings.gamepadAxis
import frontend.controllerSettings.gamepadButton
import frontend.controllerSettings.gamepadPov
import nes.input.NesController
import net.java.games.input.Component
import net.java.games.input.Component.Identifier.Axis
import net.java.games.input.Component.Identifier.Button
import net.java.games.input.Controller
import net.java.games.input.ControllerEnvironment
import java.nio.file.Files
import java.nio.file.StandardCopyOption

actual class PlatformControllerInput actual constructor(
    private val controller: NesController,
    private val inputMapper: ControllerInputMapper,
) : EmulatorInput {

    private var ignoredBindings = emptySet<Int>()

    private val gamepad: Controller?
        get() = ControllerEnvironment.getDefaultEnvironment().controllers.firstOrNull()

    actual override fun init() {
        val os = System.getProperty("os.name").lowercase()
        val libraries = when {
            os.contains("mac") -> listOf("libjinput-osx.jnilib" to System.mapLibraryName("jinput-osx"))
            os.contains("linux") -> listOf("libjinput-linux64.so" to System.mapLibraryName("jinput-linux64"))
            os.contains("win") -> listOf(
                "jinput-raw_64.dll" to System.mapLibraryName("jinput-raw_64"),
                "jinput-dx8_64.dll" to System.mapLibraryName("jinput-dx8_64"),
            )

            else -> emptyList()
        }
        if (libraries.isEmpty()) return
        val directory = Files.createTempDirectory("kassette-jinput-")
        directory.toFile().deleteOnExit()
        libraries.forEach { (resource, fileName) ->
            val target = directory.resolve(fileName)
            PlatformControllerInput::class.java.getResourceAsStream("/$resource").use { input ->
                requireNotNull(input) { "Missing JInput native library: $resource" }
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING)
            }
            target.toFile().deleteOnExit()
        }
        System.setProperty("net.java.games.input.librarypath", directory.toString())
    }

    actual override fun poll() {
        val gamepad = gamepad ?: return
        if (!gamepad.poll()) return
        with(gamepad) {
            if (getComponent(Button._0).isPressed) pressMapped(gamepadButton(0))
            if (getComponent(Button._1).isPressed) pressMapped(gamepadButton(1))
            if (getComponent(Button._2).isPressed) pressMapped(gamepadButton(2))
            if (getComponent(Button._3).isPressed) pressMapped(gamepadButton(3))
            if (getComponent(Button._4).isPressed) pressMapped(gamepadButton(4))
            if (getComponent(Button._5).isPressed) pressMapped(gamepadButton(5))
            if (getComponent(Button._6).isPressed) pressMapped(gamepadButton(6))
            if (getComponent(Button._7).isPressed) pressMapped(gamepadButton(7))
            if (getComponent(Button._8).isPressed) pressMapped(gamepadButton(8))
            if (getComponent(Button._9).isPressed) pressMapped(gamepadButton(9))
            if (getComponent(Button._10).isPressed) pressMapped(gamepadButton(10))
            if (getComponent(Button._11).isPressed) pressMapped(gamepadButton(11))
            if (getComponent(Button._12).isPressed) pressMapped(gamepadButton(12))
            if (getComponent(Button._13).isPressed) pressMapped(gamepadButton(13))
            if (getComponent(Button._14).isPressed) pressMapped(gamepadButton(14))
            if (getComponent(Button._15).isPressed) pressMapped(gamepadButton(15))

            pressMappedPov(getComponent(Axis.POV).poll)

            val x = getComponent(Axis.X).poll
            if (x < -0.5f) pressMapped(gamepadAxis(0, AXIS_NEGATIVE))
            if (x > 0.5f) pressMapped(gamepadAxis(0, AXIS_POSITIVE))

            val y = getComponent(Axis.Y).poll
            if (y < -0.5f) pressMapped(gamepadAxis(1, AXIS_NEGATIVE))
            if (y > 0.5f) pressMapped(gamepadAxis(1, AXIS_POSITIVE))
        }

    }

    private fun pressMapped(button: InputButton) {
        val nesButton = inputMapper.map(button)
        if (nesButton != NO_NES_BUTTON) controller.press(nesButton)
    }

    private fun pressMappedPov(value: Float) {
        when (value) {
            Component.POV.UP -> pressMapped(gamepadPov(POV_UP))
            Component.POV.DOWN -> pressMapped(gamepadPov(POV_DOWN))
            Component.POV.LEFT -> pressMapped(gamepadPov(POV_LEFT))
            Component.POV.RIGHT -> pressMapped(gamepadPov(POV_RIGHT))
            Component.POV.UP_LEFT -> {
                pressMapped(gamepadPov(POV_UP))
                pressMapped(gamepadPov(POV_LEFT))
            }
            Component.POV.UP_RIGHT -> {
                pressMapped(gamepadPov(POV_UP))
                pressMapped(gamepadPov(POV_RIGHT))
            }
            Component.POV.DOWN_LEFT -> {
                pressMapped(gamepadPov(POV_DOWN))
                pressMapped(gamepadPov(POV_LEFT))
            }
            Component.POV.DOWN_RIGHT -> {
                pressMapped(gamepadPov(POV_DOWN))
                pressMapped(gamepadPov(POV_RIGHT))
            }
        }
    }

    actual fun pressedButtons(): Set<InputButton> {
        val current = currentPressedBindings()
        if (current.none { it.id in ignoredBindings }) ignoredBindings = emptySet()
        return current.filterTo(mutableSetOf()) { it.id !in ignoredBindings }
    }

    private fun currentPressedBindings(): Set<InputButton> {
        val gamepad = gamepad ?: return emptySet()
        if (!gamepad.poll()) return emptySet()
        return buildSet {
            with(gamepad) {
                if (getComponent(Button._0).isPressed) add(gamepadButton(0))
                if (getComponent(Button._1).isPressed) add(gamepadButton(1))
                if (getComponent(Button._2).isPressed) add(gamepadButton(2))
                if (getComponent(Button._3).isPressed) add(gamepadButton(3))
                if (getComponent(Button._4).isPressed) add(gamepadButton(4))
                if (getComponent(Button._5).isPressed) add(gamepadButton(5))
                if (getComponent(Button._6).isPressed) add(gamepadButton(6))
                if (getComponent(Button._7).isPressed) add(gamepadButton(7))
                if (getComponent(Button._8).isPressed) add(gamepadButton(8))
                if (getComponent(Button._9).isPressed) add(gamepadButton(9))
                if (getComponent(Button._10).isPressed) add(gamepadButton(10))
                if (getComponent(Button._11).isPressed) add(gamepadButton(11))
                if (getComponent(Button._12).isPressed) add(gamepadButton(12))
                if (getComponent(Button._13).isPressed) add(gamepadButton(13))
                if (getComponent(Button._14).isPressed) add(gamepadButton(14))
                if (getComponent(Button._15).isPressed) add(gamepadButton(15))

                components.forEach { component ->
                    val buttonIndex = component.identifier.name.toIntOrNull()
                    if (buttonIndex != null && buttonIndex < GAMEPAD_BUTTON_COUNT && component.isPressed) {
                        add(gamepadButton(buttonIndex))
                    }
                }

                addPovBindings(getComponent(Axis.POV).poll)

                val x = getComponent(Axis.X).poll
                if (x < -0.5f) add(gamepadAxis(0, AXIS_NEGATIVE))
                if (x > 0.5f) add(gamepadAxis(0, AXIS_POSITIVE))

                val y = getComponent(Axis.Y).poll
                if (y < -0.5f) add(gamepadAxis(1, AXIS_NEGATIVE))
                if (y > 0.5f) add(gamepadAxis(1, AXIS_POSITIVE))
            }
        }
    }

    private fun MutableSet<InputButton>.addPovBindings(value: Float) {
        when (value) {
            Component.POV.UP -> add(gamepadPov(POV_UP))
            Component.POV.DOWN -> add(gamepadPov(POV_DOWN))
            Component.POV.LEFT -> add(gamepadPov(POV_LEFT))
            Component.POV.RIGHT -> add(gamepadPov(POV_RIGHT))
            Component.POV.UP_LEFT -> {
                        add(gamepadPov(POV_UP))
                        add(gamepadPov(POV_LEFT))
            }
            Component.POV.UP_RIGHT -> {
                add(gamepadPov(POV_UP))
                add(gamepadPov(POV_RIGHT))
            }
            Component.POV.DOWN_LEFT -> {
                add(gamepadPov(POV_DOWN))
                add(gamepadPov(POV_LEFT))
            }
            Component.POV.DOWN_RIGHT -> {
                add(gamepadPov(POV_DOWN))
                add(gamepadPov(POV_RIGHT))
            }
        }
    }

    actual fun clearPressedBindings() {
        ignoredBindings = emptySet()
        ignoredBindings = currentPressedBindings().mapTo(mutableSetOf()) { it.id }
    }

    override fun pause() = Unit

    override fun close() = Unit

    private val Component?.poll
        get() = this?.pollData ?: 0.0f

    private val Component?.isPressed
        get() = this.poll == 1f

}
