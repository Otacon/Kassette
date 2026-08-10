package frontend

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import frontend.controllerSettings.ControllerSettingsDialog
import frontend.controllerSettings.ControllerSettingsViewModel
import io.VideoFilter

@Composable
fun MainScreen(
    viewModel: MainScreenViewModel,
    controllerSettingsViewModel: ControllerSettingsViewModel,
    frameBuffer: SharedFrameBuffer,
    renderer: PlatformRenderer,
    keyboardInput: PlatformKeyboardInput,
    controllerInput: PlatformControllerInput,
    onOpenRomClick: () -> Unit,
    onPauseToggleClick: (Boolean) -> Unit,
    onResetClick: () -> Unit,
    onDialogShown: () -> Unit,
    onDialogDismissed: () -> Unit,
    onTitleChanged: (String) -> Unit,
    onExitClick: (() -> Unit)? = null,
) {
    var focusRequestKey by remember { mutableStateOf(true) }
    var showControllerSettings by remember { mutableStateOf(false) }
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.onCreate()
    }

    LaunchedEffect(state.windowTitle) {
        onTitleChanged(state.windowTitle)
    }

    ComposeMenuBar(
        onOpenRom = onOpenRomClick,
        onPauseToggle = { onPauseToggleClick(!state.isPaused) },
        onReset = onResetClick,
        gameActionsEnabled = state.isRunning,
        paused = state.isPaused,
        onExit = onExitClick,
        onMenuOpened = { keyboardInput.releaseAll() },
        onMenuDismissed = { focusRequestKey = !focusRequestKey },
        onControllerSettings = {
            onDialogShown()
            showControllerSettings = true
        },
        videoFilter = state.videoFilter,
        onToggleCrt = { viewModel.setVideoFilter(videoFilter = VideoFilter.CRT) },
        onToggleCastShadow = { viewModel.setVideoFilter(videoFilter = VideoFilter.CAST_SHADOWS) },
        modifier = Modifier.fillMaxSize(),
    ) { contentModifier ->
        ComposeSkiaScreen(
            frameBuffer = frameBuffer,
            renderer = renderer,
            keyboardInput = keyboardInput,
            videoFilter = state.videoFilter,
            focusRequestKey = focusRequestKey,
            modifier = contentModifier,
        )
    }
    if(showControllerSettings) {
        ControllerSettingsDialog(
            viewModel = controllerSettingsViewModel,
            controllerInput = controllerInput,
            onDismiss = {
                showControllerSettings = false
                onDialogDismissed()
            },
        )
    }
}
