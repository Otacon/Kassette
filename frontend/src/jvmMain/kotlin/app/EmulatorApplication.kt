package app

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import co.touchlab.kermit.Logger
import dev.zacsweers.metro.Inject
import frontend.*
import kassette.frontend.generated.resources.Res
import kassette.frontend.generated.resources.icon
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import kotlin.system.exitProcess

@Inject
class EmulatorApplication(
    private val keyboardInput: PlatformKeyboardInput,
    private val controllerInput: PlatformControllerInput,
    private val virtualControllerInput: VirtualControllerInput,
    private val runtimeHost: EmulatorRuntimeHost,
    private val audio: PlatformAudioPipeline,
    private val renderer: PlatformRenderer,
    private val viewModel: MainScreenViewModel,
    private val controllerSettingsViewModel: frontend.controllerSettings.ControllerSettingsViewModel,
) {
    private val log = Logger.withTag("EmulatorApplication")

    fun run() {
        try {
            log.i { "Emulation started" }
            runComposeWindow()
            log.i { "Emulation finished" }
        } catch (e: Exception) {
            log.e(e) { "Runtime error" }
            exitProcess(1)
        } finally {
            audio.close()
        }
    }

    private fun runComposeWindow() {
        application {
            val coroutineScope = rememberCoroutineScope()

            val windowState = remember { WindowState(size = DpSize(768.dp, 720.dp)) }

            DisposableEffect(Unit) {
                runtimeHost.start(
                    onFps = { fps -> coroutineScope.launch { viewModel.onFpsUpdated(fps) } },
                    onError = { coroutineScope.launch { exitApplication() } },
                )
                onDispose(runtimeHost::stop)
            }

            DisposableEffect(Unit) {
                onDispose(runtimeHost::close)
            }

            Window(
                onCloseRequest = ::exitApplication,
                state = windowState,
                icon = painterResource(Res.drawable.icon)
            ) {
                DisposableEffect(window) {
                    val listener = object : WindowAdapter() {
                        override fun windowActivated(event: WindowEvent) {
                            viewModel.onAppInForeground()
                        }

                        override fun windowDeactivated(event: WindowEvent) {
                            viewModel.onAppInBackground()
                        }
                    }
                    window.addWindowListener(listener)
                    onDispose { window.removeWindowListener(listener) }
                }

                val romPicker = remember(window) { FileChooser(window) }
                MainScreen(
                    viewModel = viewModel,
                    controllerSettingsViewModel = controllerSettingsViewModel,
                    frameBuffer = runtimeHost.frameBuffer,
                    renderer = renderer,
                    keyboardInput = keyboardInput,
                    controllerInput = controllerInput,
                    virtualControllerInput = virtualControllerInput,
                    onTitleChanged = { window.title = it },
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
                    onExitClick = ::exitApplication,
                )
            }
        }
    }
}
