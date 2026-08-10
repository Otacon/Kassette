package di

import app.WebEmulatorApplication
import frontend.Config
import frontend.DelegatingEmulatorInput
import frontend.EmulatorRuntimeHost
import frontend.MainScreenViewModel
import frontend.PlatformControllerInput
import frontend.PlatformRenderer
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import frontend.PlatformKeyboardInput
import frontend.controllerSettings.ControllerSettingsViewModel
import nes.NesMachine

@AppScope
@DependencyGraph
interface WasmFrontendComponent {
    val webEmulatorApplication: WebEmulatorApplication

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides config: Config): WasmFrontendComponent
    }

    @AppScope
    @Provides
    fun frontendComponent(config: Config): FrontendComponent =
        createGraphFactory<FrontendComponent.Factory>().create(config)

    @AppScope
    @Provides
    fun nesMachine(component: FrontendComponent): NesMachine = component.nesMachine

    @AppScope
    @Provides
    fun keyboardInput(component: FrontendComponent): PlatformKeyboardInput = component.keyboardInput

    @AppScope
    @Provides
    fun controllerInput(component: FrontendComponent): PlatformControllerInput = component.controllerInput

    @AppScope
    @Provides
    fun runtimeInput(component: FrontendComponent): DelegatingEmulatorInput = component.runtimeInput

    @AppScope
    @Provides
    fun runtimeHost(component: FrontendComponent): EmulatorRuntimeHost = component.runtimeHost

    @AppScope
    @Provides
    fun renderer(component: FrontendComponent): PlatformRenderer = component.renderer

    @AppScope
    @Provides
    fun viewModel(component: FrontendComponent): MainScreenViewModel = component.viewModel

    @AppScope
    @Provides
    fun controllerSettingsViewModel(component: FrontendComponent): ControllerSettingsViewModel = component.controllerSettingsViewModel
}
