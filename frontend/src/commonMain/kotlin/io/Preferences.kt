package io

import androidx.compose.ui.input.key.Key
import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.Settings
import com.russhwolf.settings.serialization.decodeValueOrNull
import com.russhwolf.settings.serialization.encodeValue
import frontend.controllerSettings.AXIS_NEGATIVE
import frontend.controllerSettings.AXIS_POSITIVE
import frontend.controllerSettings.InputMappings
import frontend.controllerSettings.gamepadAxis
import frontend.controllerSettings.gamepadButton
import frontend.controllerSettings.inputButton
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@OptIn(ExperimentalSerializationApi::class)
class Preferences {

    private val settings = Settings()

    @OptIn(ExperimentalSettingsApi::class)
    var mappings: ControllerMappings
        get() = runCatching {
            settings.decodeValueOrNull<ControllerMappings>(KEY_GAMEPAD_MAPPINGS)
        }.getOrNull() ?: DEFAULT_CONTROLLER_MAPPINGS
        set(value) = settings.encodeValue(key = KEY_GAMEPAD_MAPPINGS, value = value)

    var videoFilter: VideoFilter
        get() = settings.getStringOrNull(KEY_VIDEO_FILTER)
            ?.let {
                runCatching { VideoFilter.valueOf(it) }.getOrNull()
            } ?: VideoFilter.NONE
        set(value) {
            settings.putString(key = KEY_VIDEO_FILTER, value = value.name)
        }

    companion object {
        private const val KEY_VIDEO_FILTER = "video-filter"
        private const val KEY_GAMEPAD_MAPPINGS = "gamepad-mappings"

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

enum class VideoFilter {
    CRT, CAST_SHADOWS, NONE
}

@Serializable
data class ControllerMappings(
    @SerialName("buttons") val buttons: List<Pair<Int, Int>>,
)

fun ControllerMappings.toInputMappings(): InputMappings = InputMappings(buttons = buttons)

fun InputMappings.toControllerMappings(): ControllerMappings = ControllerMappings(buttons = buttons)
