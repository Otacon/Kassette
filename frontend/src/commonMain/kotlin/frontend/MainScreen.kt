package frontend

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import frontend.controllerSettings.ControllerSettingsDialog
import frontend.controllerSettings.ControllerSettingsViewModel
import frontend.components.Dialog
import io.VideoFilter
import platform.ControllerInput
import platform.KeyboardInput
import platform.hasTouchScreen

@Composable
fun MainScreen(
    viewModel: MainScreenViewModel,
    controllerSettingsViewModel: ControllerSettingsViewModel,
    frameBuffer: SharedFrameBuffer,
    renderer: Renderer,
    keyboardInput: KeyboardInput,
    controllerInput: ControllerInput,
    virtualControllerInput: VirtualControllerInput,
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
    val showVirtualController = remember { hasTouchScreen() }
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.onCreate()
    }

    LaunchedEffect(state.windowTitle) {
        onTitleChanged(state.windowTitle)
    }

    DisposableEffect(state.loadError != null) {
        if (state.loadError != null) {
            onDialogShown()
        }
        onDispose {
            if (state.loadError != null) {
                onDialogDismissed()
            }
        }
    }

    ComposeMenuBar(
        onOpenRom = onOpenRomClick,
        onPauseToggle = { onPauseToggleClick(!state.isPaused) },
        onReset = onResetClick,
        onSaveState = viewModel::onSaveState,
        onLoadState = viewModel::onLoadState,
        onSoundToggle = viewModel::onSoundToggle,
        gameActionsEnabled = state.isRunning,
        paused = state.isPaused,
        soundEnabled = state.soundEnabled,
        loadStateSlots = state.loadStateSlots,
        onExit = onExitClick,
        onMenuOpened = { keyboardInput.releaseAll() },
        onMenuDismissed = { focusRequestKey = !focusRequestKey },
        onControllerSettings = {
            onDialogShown()
            showControllerSettings = true
        },
        videoFilter = state.videoFilter,
        onToggleCrt = { viewModel.setVideoFilter(videoFilter = VideoFilter.CRT) },
        modifier = Modifier.fillMaxSize(),
    ) { contentModifier ->
        Box(contentModifier) {
            ComposeSkiaScreen(
                frameBuffer = frameBuffer,
                renderer = renderer,
                keyboardInput = keyboardInput,
                videoFilter = state.videoFilter,
                focusRequestKey = focusRequestKey,
                modifier = Modifier.fillMaxSize(),
            )
            if (showVirtualController) {
                VirtualController(
                    input = virtualControllerInput,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
    if (showControllerSettings) {
        ControllerSettingsDialog(
            viewModel = controllerSettingsViewModel,
            controllerInput = controllerInput,
            onDismiss = {
                showControllerSettings = false
                onDialogDismissed()
            },
        )
    }
    state.loadError?.let { error ->
        Dialog(
            title = "ROM Load Error",
            positiveText = "OK",
            onPositive = viewModel::onLoadErrorDismissed,
        ) {
            BasicText(
                text = error,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}
