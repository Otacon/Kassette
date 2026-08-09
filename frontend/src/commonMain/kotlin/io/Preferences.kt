package io

import androidx.compose.ui.input.key.Key
import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.Settings
import com.russhwolf.settings.serialization.decodeValueOrNull
import com.russhwolf.settings.serialization.encodeValue
import frontend.controllerSettings.AXIS_NEGATIVE
import frontend.controllerSettings.AXIS_POSITIVE
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
    var mappings: ControllerMappings?
        get() = settings.decodeValueOrNull(KEY_GAMEPAD_MAPPINGS)
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
    }
}

enum class VideoFilter {
    CRT, CAST_SHADOWS, NONE
}

@Serializable
data class ControllerMappings(
    @SerialName("controller") val controller: DeviceMappings,
    @SerialName("keyboard") val keyboard: DeviceMappings,
)

@Serializable
data class DeviceMappings(
    @SerialName("buttons") val buttons: List<Int>,
)

val DEFAULT_CONTROLLER_MAPPINGS = ControllerMappings(
    keyboard = DeviceMappings(listOf(
        Key.Z.inputButton().id,
        Key.X.inputButton().id,
        Key.ShiftLeft.inputButton().id,
        Key.Enter.inputButton().id,
        Key.DirectionUp.inputButton().id,
        Key.DirectionDown.inputButton().id,
        Key.DirectionLeft.inputButton().id,
        Key.DirectionRight.inputButton().id,
    )),
    controller = DeviceMappings(listOf(
        gamepadButton(1).id,
        gamepadButton(0).id,
        gamepadButton(8).id,
        gamepadButton(9).id,
        gamepadAxis(1, AXIS_NEGATIVE).id,
        gamepadAxis(1, AXIS_POSITIVE).id,
        gamepadAxis(0, AXIS_NEGATIVE).id,
        gamepadAxis(0, AXIS_POSITIVE).id,
    )),
)
