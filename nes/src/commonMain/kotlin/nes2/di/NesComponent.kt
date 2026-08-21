package nes2.di

import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import nes.di.NesScope
import nes2.CpuBus
import nes2.CpuBusNes
import nes2.OamDma
import nes2.OamDmaNes
import nes2.apu.Apu
import nes2.apu.ApuNes
import nes2.cartridge.CartridgePort
import nes2.cartridge.CartridgePortNes
import nes2.controller.ControllerPort
import nes2.controller.ControllerPortNes
import nes2.cpu.Cpu6502
import nes2.ppu.FramebufferNes
import nes2.ppu.Ppu
import nes2.ppu.PpuNes
import nes2.ppu.PpuState
import nes2.ppuBus.PpuBus
import nes2.ppuBus.PpuBusNes
import nes2.ppuBus.PpuBusState

@NesScope
@DependencyGraph
@Suppress("unused")
interface NesComponent {

    @NesScope
    @Provides
    fun cartridgeSocket(): CartridgePort = CartridgePortNes()

    @NesScope
    @Provides
    fun ppuBus(cartridgeSocket: CartridgePort): PpuBus = PpuBusNes(
        state = PpuBusState(),
        cartridge = cartridgeSocket,
    )

    @NesScope
    @Provides
    fun ppu(ppuBus: PpuBus, cartridgeSocket: CartridgePort): Ppu = PpuNes(
        state = PpuState(),
        ppuBus = ppuBus,
        onMapperScanline = { cartridgeSocket.clockScanline() },
        frameBuffer = FramebufferNes()
    )

    @NesScope
    @Provides
    fun controllerPort(controllerPort: ControllerPort): ControllerPort = ControllerPortNes()

    @NesScope
    @Provides
    fun oamDma(ppu: Ppu, cpuBus: CpuBus): OamDma = OamDmaNes(
        cpuBus = cpuBus,
        ppu = ppu,
    )

    @NesScope
    @Provides
    fun apu(): Apu = ApuNes()

    @NesScope
    @Provides
    fun cpuBus(
        cartridgeSocket: CartridgePortNes,
        ppu: Ppu,
        controllerPort: ControllerPort,
        dma: OamDma,
        apu: Apu
    ): CpuBus =
        CpuBusNes(
            ram = IntArray(2048),
            cartridge = cartridgeSocket,
            ppu = ppu,
            dma = dma,
            apu = apu,
            controller1 = controllerPort,
            controller2 = controllerPort,
        )

    @NesScope
    @Provides
    fun cpu6502(cpuBus: CpuBus): Cpu6502 = Cpu6502(cpuBus)

}
