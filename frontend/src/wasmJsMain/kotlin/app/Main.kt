@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, ExperimentalWasmJsInterop::class)

package app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.window.ComposeViewport
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import com.cyanotic.kassette.BuildKonfig
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.createGraphFactory
import di.WasmFrontendComponent
import frontend.*
import kotlinx.browser.document
import kotlinx.coroutines.launch
import platform.ControllerInput
import platform.KeyboardInput

fun main() {
    Logger.setMinSeverity(Severity.entries[BuildKonfig.loggingLevel])
    val appComponent = createGraphFactory<WasmFrontendComponent.Factory>().create(Config())
    val root = document.getElementById("app") ?: document.body ?: error("Missing document body")
    ComposeViewport(root) {
        val application = remember { appComponent.webEmulatorApplication }
        application.Content()
    }
}

@Inject
class WebEmulatorApplication(
    private val machine: NesMachine,
    private val keyboardInput: KeyboardInput,
    private val controllerInput: ControllerInput,
    private val virtualControllerInput: VirtualControllerInput,
    private val runtimeHost: EmulatorRuntimeHost,
    private val viewModel: MainScreenViewModel,
    private val controllerSettingsViewModel: frontend.controllerSettings.ControllerSettingsViewModel,
    private val renderer: Renderer,
) {
    private val romPicker = platform.FileChooser()

    @Composable
    fun Content() {
        val coroutineScope = rememberCoroutineScope()

        DisposableEffect(Unit) {
            val activityListener = addPageActivityListener {
                coroutineScope.launch {
                    if (isPageActive()) {
                        viewModel.onAppInForeground()
                    } else {
                        viewModel.onAppInBackground()
                    }
                }
            }
            runtimeHost.start(
                onFps = { fps -> coroutineScope.launch { viewModel.onFpsUpdated(fps) } },
                onError = { coroutineScope.launch { machine.powerOff() } },
            )
            onDispose {
                removePageActivityListener(activityListener)
                runtimeHost.stop()
            }
        }

        DisposableEffect(Unit) {
            onDispose(runtimeHost::close)
        }

        MainScreen(
            viewModel = viewModel,
            controllerSettingsViewModel = controllerSettingsViewModel,
            frameBuffer = runtimeHost.frameBuffer,
            renderer = renderer,
            keyboardInput = keyboardInput,
            controllerInput = controllerInput,
            virtualControllerInput = virtualControllerInput,
            onTitleChanged = { document.title = it },
            onOpenRomClick = {
                coroutineScope.launch {
                    val rom = romPicker.pickRom()
                    viewModel.onRomSelected(rom)
                }
            },
            onPauseToggleClick = viewModel::onPauseClicked,
            onResetClick = viewModel::onResetClicked,
            onDialogShown = viewModel::onDialogShown,
            onDialogDismissed = viewModel::onDialogDismissed,
        )
    }
}
