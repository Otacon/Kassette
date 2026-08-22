package di

import com.cyanotic.kassette.BuildKonfig
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.Scope
import frontend.*
import frontend.controllerSettings.ControllerInputMapper
import frontend.controllerSettings.ControllerSettingsViewModel
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import io.Nes20Db
import io.Nes20DbCsv
import io.Preferences
import io.SavestateStore
import io.platformSavestateStore
import io.toInputMappings
import nes.cartridge.InesParserComposite
import nes.cartridge.InesParserUtils
import nes.cartridge.InesParserV1
import nes.cartridge.InesParserV2
import platform.AudioPipeline
import platform.ControllerInput
import platform.KeyboardInput

@AppScope
@DependencyGraph
@Suppress("unused")
interface FrontendComponent {
    val inesParser: InesParserComposite
    val nesMachine: NesMachine
    val renderer: Renderer
    val audio: AudioPipeline
    val keyboardInput: KeyboardInput
    val controllerInput: ControllerInput
    val virtualControllerInput: VirtualControllerInput
    val runtimeInput: DelegatingEmulatorInput
    val runtimeHost: EmulatorRuntimeHost
    val viewModel: MainScreenViewModel
    val controllerSettingsViewModel: ControllerSettingsViewModel

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides config: Config): FrontendComponent
    }

    @AppScope
    @Provides
    fun inesParserUtils(): InesParserUtils = InesParserUtils()

    @AppScope
    @Provides
    fun inesParserV1(utils: InesParserUtils): InesParserV1 = InesParserV1(utils)

    @AppScope
    @Provides
    fun inesParserV2(utils: InesParserUtils): InesParserV2 = InesParserV2(utils)

    @AppScope
    @Provides
    fun nes20Db(): Nes20Db = Nes20DbCsv("nes20db.csv")

    @AppScope
    @Provides
    fun inesParser(
        inesParserV1: InesParserV1,
        inesParserV2: InesParserV2,
        nes20Db: Nes20Db,
        utils: InesParserUtils,
    ): InesParserComposite = InesParserComposite(inesParserV1, inesParserV2, nes20Db, utils)

    @AppScope
    @Provides
    fun nesMachine(): NesMachine = NesMachine()

    @AppScope
    @Provides
    fun renderer(): Renderer = Renderer()

    @AppScope
    @Provides
    fun audio(): AudioPipeline = AudioPipeline()

    @AppScope
    @Provides
    fun keyboardInput(
        machine: NesMachine,
        inputMapper: ControllerInputMapper,
    ): KeyboardInput = KeyboardInput(machine.controller, inputMapper)

    @AppScope
    @Provides
    fun controllerInput(
        machine: NesMachine,
        inputMapper: ControllerInputMapper,
    ): ControllerInput = ControllerInput(machine.controller, inputMapper)

    @AppScope
    @Provides
    fun virtualControllerInput(machine: NesMachine): VirtualControllerInput =
        VirtualControllerInput(machine.controller)

    @AppScope
    @Provides
    fun runtimeInput(
        keyboardInput: KeyboardInput,
        controllerInput: ControllerInput,
        virtualControllerInput: VirtualControllerInput,
    ): DelegatingEmulatorInput = DelegatingEmulatorInput(
        CombinedEmulatorInput(keyboardInput, controllerInput, virtualControllerInput)
    )

    @AppScope
    @Provides
    fun preferences(): Preferences = Preferences()

    @AppScope
    @Provides
    fun savestateStore(): SavestateStore = platformSavestateStore()

    @OptIn(ExperimentalSerializationApi::class)
    @AppScope
    @Provides
    fun savestateCbor(): Cbor = Cbor {
        encodeDefaults = true
        ignoreUnknownKeys = false
    }

    @AppScope
    @Provides
    fun controllerInputMapper(preferences: Preferences): ControllerInputMapper =
        ControllerInputMapper(preferences.mappings.toInputMappings())

    @AppScope
    @Provides
    fun runtimeHost(
        machine: NesMachine,
        audio: AudioPipeline,
        input: DelegatingEmulatorInput,
    ): EmulatorRuntimeHost = EmulatorRuntimeHost(
        machine = machine,
        audio = audio,
        input = input,
    )

    @AppScope
    @Provides
    fun buildKonfig(): BuildKonfig = BuildKonfig
}

@Scope
annotation class AppScope
