package frontend

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cyanotic.kassette.BuildKonfig
import dev.zacsweers.metro.Inject
import io.Preferences
import io.VideoFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nes.ConsoleRegion
import nes.NesMachine
import nes.cartridge.InesParserComposite
import nes.cartridge.InesParseResult
import nes.cartridge.RomData
import nes.cartridge.UnzipRomResult
import nes.cartridge.unzipRom

@Inject
class MainScreenViewModel(
    private val config: Config,
    private val machine: NesMachine,
    private val runtime: EmulatorRuntimeHost,
    private val parser: InesParserComposite,
    private val buildKonfig: BuildKonfig,
    private val preferences: Preferences,
) : ViewModel() {

    private val _state = MutableStateFlow(MainWindowState())
    val state = _state.asStateFlow()

    private var rom: String? = null
    private var fps: Int? = null
    private var region: ConsoleRegion? = null
    private var dialogShown = false
    private var appInForeground = true
    private var userPaused = false

    fun onCreate() {
        viewModelScope.launch {
            machine.isPoweredOn.collect { isPoweredOn ->
                _state.update { it.copy(isRunning = isPoweredOn, isPaused = isPoweredOn && it.isPaused) }
            }
        }
        config.rom?.let { loadRom(it) }
        _state.update { it.copy(videoFilter = preferences.videoFilter) }
    }

    fun onRomSelected(romData: RomData?) {
        romData?.let { loadRom(it) }
    }

    fun onLoadErrorDismissed() = _state.update { it.copy(loadError = null) }

    fun onFpsUpdated(fps: Int) {
        this.fps = fps
        updateTitle()
    }

    fun setVideoFilter(videoFilter: VideoFilter) = _state.update {
        val newVideoFilter = if(videoFilter == it.videoFilter) VideoFilter.NONE else videoFilter
        preferences.videoFilter = newVideoFilter
        it.copy(videoFilter = newVideoFilter)
    }

    fun onResetClicked() = viewModelScope.launch {
        machine.reset()
        userPaused = false
        applyPauseState()
    }

    fun onDialogShown() {
        dialogShown = true
        applyPauseState()
    }

    fun onDialogDismissed() {
        dialogShown = false
        applyPauseState()
    }

    fun onAppInForeground() {
        appInForeground = true
        applyPauseState()
    }

    fun onAppInBackground() {
        appInForeground = false
        applyPauseState()
    }

    fun onPauseClicked(isPaused: Boolean) {
        userPaused = isPaused
        applyPauseState()
    }

    private fun loadRom(romData: RomData) = viewModelScope.launch {
        val resolvedRom = resolveRom(romData) ?: return@launch
        when (val result = parser.parse(resolvedRom)) {
            is InesParseResult.Success -> {
                val cartridge = result.cartridge
                this@MainScreenViewModel.rom = resolvedRom.name
                this@MainScreenViewModel.region = cartridge.region
                machine.powerOff()
                machine.insert(cartridge)
                machine.powerOn()
                applyPauseState()
            }

            InesParseResult.InvalidRom -> _state.update { it.copy(loadError = "Invalid ROM") }
            InesParseResult.UnknownError -> _state.update { it.copy(loadError = "Unable to load ROM") }
        }
    }

    private suspend fun resolveRom(romData: RomData): RomData? {
        if (!romData.name.endsWith(".zip", ignoreCase = true)) return romData
        val message = when (val result = unzipRom(romData)) {
            is UnzipRomResult.Success -> return result.romData
            UnzipRomResult.NotFound -> "ZIP archive does not contain a .nes ROM"
            UnzipRomResult.MultipleRoms -> "ZIP archive contains multiple .nes ROMs"
            UnzipRomResult.UnknownError -> "Unable to unzip ROM archive"
        }
        _state.update { it.copy(loadError = message) }
        return null
    }

    private fun applyPauseState() = viewModelScope.launch {
        val shouldPause = userPaused || dialogShown || !appInForeground
        _state.update { it.copy(isPaused = shouldPause) }
        if(shouldPause) {
            runtime.pause()
        } else {
            runtime.resume()
        }
        updateTitle()
    }

    private fun updateTitle() = _state.update { current ->
        val elements = buildList {
            rom?.let { add(it) }
            region?.let { add(it.name) }
            val status = if (current.isPaused) {
                "Paused"
            } else {
                fps?.let { "$it fps" }
            }
            status?.let { add(it) }
        }

        val values = if (elements.isNotEmpty()) {
            " | " + elements.joinToString(prefix = "[", postfix = "]") { it }
        } else {
            ""
        }
        current.copy(windowTitle = "Kassette v${buildKonfig.version}$values")
    }
}

data class MainWindowState(
    val isRunning: Boolean = false,
    val windowTitle: String = "",
    val showRomPicker: Boolean = false,
    val isPaused: Boolean = false,
    val videoFilter: VideoFilter = VideoFilter.NONE,
    val loadError: String? = null,
)
