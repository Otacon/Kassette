package app

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import com.cyanotic.kassette.BuildKonfig
import frontend.CombinedEmulatorInput
import frontend.Config
import frontend.EmulatorRuntimeHost
import frontend.MainScreen
import frontend.MainScreenViewModel
import frontend.NesMachine
import frontend.Renderer
import frontend.SavestateCodec
import frontend.VirtualControllerInput
import frontend.controllerSettings.ControllerInputMapper
import frontend.controllerSettings.ControllerSettingsViewModel
import io.Nes20DbCsv
import io.Preferences
import io.SavestateStore
import io.platformSavestateStore
import io.toInputMappings
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import nes.cartridge.InesParserComposite
import nes.cartridge.InesParserUtils
import nes.cartridge.InesParserV1
import nes.cartridge.InesParserV2
import nes.cartridge.RomData
import platform.AudioPipeline
import platform.ControllerInput
import platform.FileChooser
import platform.KeyboardInput
import platform.posix.SEEK_END
import platform.posix.SEEK_SET
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell

fun main(args: Array<String>) {
    Logger.setMinSeverity(Severity.Info)
    runNativeApplication(Config(rom = args.firstOrNull()?.let(::readRomData)))
}

@OptIn(ExperimentalSerializationApi::class)
private fun runNativeApplication(config: Config) {
    val preferences = Preferences()
    val inputMapper = ControllerInputMapper(preferences.mappings.toInputMappings())
    val machine = NesMachine()
    val audio = AudioPipeline()
    val keyboardInput = KeyboardInput(machine.controller, inputMapper)
    val controllerInput = ControllerInput(machine.controller, inputMapper)
    val virtualControllerInput = VirtualControllerInput(machine.controller)
    val runtimeInput = CombinedEmulatorInput(keyboardInput, controllerInput, virtualControllerInput)
    val runtimeHost = EmulatorRuntimeHost(machine, audio, runtimeInput)
    val parserUtils = InesParserUtils()
    val parser = InesParserComposite(
        inesParserV1 = InesParserV1(parserUtils),
        inesParserV2 = InesParserV2(parserUtils),
        nes20Db = Nes20DbCsv("nes20db.csv"),
        utils = parserUtils,
    )
    val savestateStore: SavestateStore = platformSavestateStore()
    val cbor = Cbor {
        encodeDefaults = true
        ignoreUnknownKeys = false
    }
    val viewModel = MainScreenViewModel(
        config = config,
        runtime = runtimeHost,
        parser = parser,
        buildKonfig = BuildKonfig,
        preferences = preferences,
        savestateStore = savestateStore,
        savestateCodec = SavestateCodec(cbor),
    )
    val controllerSettingsViewModel = ControllerSettingsViewModel(preferences, inputMapper)
    val renderer = Renderer()

    application {
        val coroutineScope = rememberCoroutineScope()
        val windowState = remember { WindowState(size = DpSize(768.dp, 720.dp)) }
        var title by remember { mutableStateOf("Kassette v${BuildKonfig.version}") }

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
            title = title,
        ) {
            val romPicker = remember { FileChooser() }

            MainScreen(
                viewModel = viewModel,
                controllerSettingsViewModel = controllerSettingsViewModel,
                frameBuffer = runtimeHost.frameBuffer,
                renderer = renderer,
                keyboardInput = keyboardInput,
                controllerInput = controllerInput,
                virtualControllerInput = virtualControllerInput,
                onTitleChanged = { title = it },
                onOpenRomClick = {
                    coroutineScope.launch {
                        viewModel.onRomSelected(romPicker.pickRom())
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

private fun readRomData(path: String): RomData {
    require(!path.endsWith(".zip", ignoreCase = true)) { "Native PoC only accepts .nes ROM files" }
    return RomData(path.fileName(), readBytes(path))
}

@OptIn(ExperimentalForeignApi::class)
private fun readBytes(path: String): ByteArray {
    val file = fopen(path, "rb") ?: error("Unable to open ROM: $path")
    try {
        check(fseek(file, 0, SEEK_END) == 0) { "Unable to seek ROM: $path" }
        val size = ftell(file)
        check(size >= 0) { "Unable to size ROM: $path" }
        check(fseek(file, 0, SEEK_SET) == 0) { "Unable to rewind ROM: $path" }
        val bytes = ByteArray(size.toInt())
        if (bytes.isEmpty()) return bytes
        val read = bytes.usePinned { pinned ->
            fread(pinned.addressOf(0), 1.convert(), bytes.size.convert(), file)
        }.toLong()
        check(read == size) { "Unable to read ROM: $path" }
        return bytes
    } finally {
        fclose(file)
    }
}

private fun String.fileName(): String = substringAfterLast('/').substringAfterLast('\\')
