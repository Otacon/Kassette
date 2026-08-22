/*
 * This file is part of Kassette.
 *
 * This Kotlin implementation is ported and adapted from MesenCE
 * (https://github.com/nesdev-org/MesenCE). MesenCE is licensed under
 * the GNU General Public License version 3.
 *
 * This modified Kotlin port is distributed under the GNU General Public
 * License version 3. See the repository LICENSE file for details.
 */

package nes.console

import nes.cpu.ConsoleRegion
import nes.cpu.NesCpuApuBridge
import nes.memory.INesMemoryHandler
import nes.memory.NesMemoryMapper

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
    fun write(openBus: Int, value: Int) {}
}

interface NesConsoleApu : NesCpuApuBridge {
    fun initConsole(console: NesConsole) {}
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
    fun updateControlDevices() {}
    fun updateInputState() {}
    fun hasPendingWrites(): Boolean = false
    fun processWrites() {}
    fun getOpenBusMask(port: Int): Int = 0xFF
}
