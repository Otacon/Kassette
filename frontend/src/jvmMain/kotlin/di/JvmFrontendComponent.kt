package di

import app.EmulatorApplication
import frontend.Config
import frontend.DelegatingEmulatorInput
import frontend.EmulatorRuntimeHost
import frontend.MainScreenViewModel
import platform.AudioPipeline
import platform.ControllerInput
import frontend.Renderer
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import platform.KeyboardInput
import frontend.VirtualControllerInput
import frontend.controllerSettings.ControllerSettingsViewModel
import nes.NesMachine

@AppScope
@DependencyGraph
interface JvmFrontendComponent {
    val emulatorApplication: EmulatorApplication

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides config: Config): JvmFrontendComponent
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
    fun keyboardInput(component: FrontendComponent): KeyboardInput = component.keyboardInput

    @AppScope
    @Provides
    fun controllerInput(component: FrontendComponent): ControllerInput = component.controllerInput

    @AppScope
    @Provides
    fun virtualControllerInput(component: FrontendComponent): VirtualControllerInput = component.virtualControllerInput

    @AppScope
    @Provides
    fun runtimeInput(component: FrontendComponent): DelegatingEmulatorInput = component.runtimeInput

    @AppScope
    @Provides
    fun runtimeHost(component: FrontendComponent): EmulatorRuntimeHost = component.runtimeHost

    @AppScope
    @Provides
    fun audio(component: FrontendComponent): AudioPipeline = component.audio

    @AppScope
    @Provides
    fun renderer(component: FrontendComponent): Renderer = component.renderer

    @AppScope
    @Provides
    fun viewModel(component: FrontendComponent): MainScreenViewModel = component.viewModel

    @AppScope
    @Provides
    fun controllerSettingsViewModel(component: FrontendComponent): ControllerSettingsViewModel = component.controllerSettingsViewModel
}
