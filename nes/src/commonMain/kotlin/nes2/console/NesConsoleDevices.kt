package nes2.console

import nes2.cpu.ConsoleRegion
import nes2.memory.INesMemoryHandler
import nes2.memory.NesMemoryMapper

interface NesConsoleMapper : NesMemoryMapper {
    val epsm: NesConsoleExpansionAudio? get() = null

    fun initConsole(console: NesConsole) {}
    fun hasCpuClockHook(): Boolean = false
    fun processCpuClock() {}
    fun setRegion(region: ConsoleRegion) {}
    fun endFrame() {}
    fun onAfterResetPowerOn() {}
}

interface NesConsoleExpansionAudio : INesMemoryHandler {
    fun reset() {}
}

interface NesConsolePpu : INesMemoryHandler {
    val frameCount: Int

    fun initConsole(console: NesConsole) {}
    fun run(masterClock: Long)
    fun reset(softReset: Boolean)
    fun updateTimings(region: ConsoleRegion, overclockEnabled: Boolean = true)
}

interface NesConsoleControlManager : INesMemoryHandler {
    fun initConsole(console: NesConsole) {}
    fun reset(softReset: Boolean) {}
    fun hasPendingWrites(): Boolean = false
    fun processWrites() {}
    fun getOpenBusMask(port: Int): Int = 0xFF
}
