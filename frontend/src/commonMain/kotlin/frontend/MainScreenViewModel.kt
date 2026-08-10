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
import nes.cartridge.RomData

@Inject
class MainScreenViewModel(
    private val config: Config,
    private val machine: NesMachine,
    private val parser: InesParserComposite,
    private val buildKonfig: BuildKonfig,
    private val preferences: Preferences,
) : ViewModel() {

    private val _state = MutableStateFlow(MainWindowState())
    val state = _state.asStateFlow()

    private var rom: String? = null
    private var fps: Int? = null
    private var region: ConsoleRegion? = null

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

    fun onFpsUpdated(fps: Int) {
        this.fps = fps
        updateTitle()
    }

    fun setVideoFilter(videoFilter: VideoFilter) = _state.update {
        val newVideoFilter = if(videoFilter == it.videoFilter) VideoFilter.NONE else videoFilter
        preferences.videoFilter = newVideoFilter
        it.copy(videoFilter = newVideoFilter)
    }

    fun setPaused(paused: Boolean) {
        val next = paused && _state.value.isRunning
        _state.update { it.copy(isPaused = next) }
        updateTitle()
    }

    private fun loadRom(romData: RomData) = viewModelScope.launch {
        this@MainScreenViewModel.rom = romData.name
        val cartridge = parser.parse(romData)
        this@MainScreenViewModel.region = cartridge.region
        machine.powerOff()
        machine.insert(cartridge)
        machine.powerOn()
        _state.update { it.copy(isPaused = false) }
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
)
