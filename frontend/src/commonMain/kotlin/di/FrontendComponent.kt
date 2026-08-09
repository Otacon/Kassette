package di

import com.cyanotic.kassette.BuildKonfig
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.Scope
import dev.zacsweers.metro.createGraph
import frontend.*
import frontend.controllerSettings.ControllerInputMapper
import frontend.controllerSettings.ControllerSettingsViewModel
import io.Nes20Db
import io.Nes20DbCsv
import io.Preferences
import nes.NesMachine
import nes.cartridge.InesParserComposite
import nes.cartridge.InesParserUtils
import nes.cartridge.InesParserV1
import nes.cartridge.InesParserV2
import nes.di.NesComponent

@AppScope
@DependencyGraph
@Suppress("unused")
interface FrontendComponent {
    val inesParser: InesParserComposite
    val nesMachine: NesMachine
    val renderer: PlatformRenderer
    val audio: PlatformAudioPipeline
    val keyboardInput: PlatformKeyboardInput
    val controllerInput: PlatformControllerInput
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
    fun nesComponent(): NesComponent = createGraph<NesComponent>()

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
    fun nesMachine(nesComponent: NesComponent): NesMachine = nesComponent.nesMachine

    @AppScope
    @Provides
    fun renderer(): PlatformRenderer = PlatformRenderer()

    @AppScope
    @Provides
    fun audio(): PlatformAudioPipeline = PlatformAudioPipeline()

    @AppScope
    @Provides
    fun keyboardInput(
        machine: NesMachine,
        inputMapper: ControllerInputMapper,
    ): PlatformKeyboardInput = PlatformKeyboardInput(machine.controller, inputMapper)

    @AppScope
    @Provides
    fun controllerInput(
        machine: NesMachine,
        inputMapper: ControllerInputMapper,
    ): PlatformControllerInput = PlatformControllerInput(machine.controller, inputMapper)

    @AppScope
    @Provides
    fun runtimeInput(
        keyboardInput: PlatformKeyboardInput,
        controllerInput: PlatformControllerInput,
    ): DelegatingEmulatorInput = DelegatingEmulatorInput(CombinedEmulatorInput(keyboardInput, controllerInput))

    @AppScope
    @Provides
    fun preferences(): Preferences = Preferences()

    @AppScope
    @Provides
    fun controllerInputMapper(preferences: Preferences): ControllerInputMapper = ControllerInputMapper()

    @AppScope
    @Provides
    fun runtimeHost(
        machine: NesMachine,
        audio: PlatformAudioPipeline,
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
