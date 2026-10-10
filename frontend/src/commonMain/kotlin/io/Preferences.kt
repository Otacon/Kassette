package io

import androidx.compose.ui.input.key.Key
import frontend.controllerSettings.AXIS_NEGATIVE
import frontend.controllerSettings.AXIS_POSITIVE
import frontend.controllerSettings.InputMappings
import frontend.controllerSettings.gamepadAxis
import frontend.controllerSettings.gamepadButton
import frontend.controllerSettings.inputButton
import com.russhwolf.settings.Settings
import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.serialization.decodeValue
import com.russhwolf.settings.serialization.encodeValue
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

expect fun createSettings(name: String): Settings

@OptIn(ExperimentalSerializationApi::class, ExperimentalSettingsApi::class)
class Preferences {
    private val settings = createSettings("kassette")

    var mappings: ControllerMappings
        get() = settings.decodeValue(ControllerMappings.serializer(), MAPPINGS_KEY, DEFAULT_CONTROLLER_MAPPINGS)
        set(value) {
            settings.encodeValue(ControllerMappings.serializer(), MAPPINGS_KEY, value)
        }

    var videoFilter: VideoFilter
        get() = settings.decodeValue(VideoFilter.serializer(), VIDEO_FILTER_KEY, VideoFilter.NONE)
        set(value) {
            settings.encodeValue(VideoFilter.serializer(), VIDEO_FILTER_KEY, value)
        }

    var soundEnabled: Boolean
        get() = settings.getBoolean(SOUND_ENABLED_KEY, true)
        set(value) {
            settings.putBoolean(SOUND_ENABLED_KEY, value)
        }

    companion object {
        private const val MAPPINGS_KEY = "mappings"
        private const val VIDEO_FILTER_KEY = "videoFilter"
        private const val SOUND_ENABLED_KEY = "soundEnabled"

        private val DEFAULT_CONTROLLER_MAPPINGS = ControllerMappings(
            buttons = listOf(
                Key.Z.inputButton().id to gamepadButton(1).id,
                Key.X.inputButton().id to gamepadButton(0).id,
                Key.ShiftLeft.inputButton().id to gamepadButton(8).id,
                Key.Enter.inputButton().id to gamepadButton(9).id,
                Key.DirectionUp.inputButton().id to gamepadAxis(1, AXIS_NEGATIVE).id,
                Key.DirectionDown.inputButton().id to gamepadAxis(1, AXIS_POSITIVE).id,
                Key.DirectionLeft.inputButton().id to gamepadAxis(0, AXIS_NEGATIVE).id,
                Key.DirectionRight.inputButton().id to gamepadAxis(0, AXIS_POSITIVE).id,
            ),
        )
    }
}

@Serializable
enum class VideoFilter {
    CRT, NONE
}

@Serializable
data class ControllerMappings(
    @SerialName("buttons") val buttons: List<Pair<Int, Int>>,
)

fun ControllerMappings.toInputMappings(): InputMappings = InputMappings(buttons = buttons)

fun InputMappings.toControllerMappings(): ControllerMappings = ControllerMappings(buttons = buttons)
