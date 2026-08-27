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

package nes.input

import kotlinx.serialization.Serializable
import nes.console.NesConsole
import nes.console.NesConsoleControlManager
import nes.memory.MemoryOperation
import nes.memory.MemoryRanges

interface NesControlDevice {
    val connected: Boolean get() = true
    var previousReadCycle: Long
    var previousReadValue: Int

    fun readRam(addr: Int): Int
    fun writeRam(addr: Int, value: Int) {}
    fun updateInputState() {}
    fun reset(softReset: Boolean) {}
}

fun interface NesControlDeviceProvider {
    fun getControlDevices(): Array<NesControlDevice>
}

class NesControlManager(
    private val devicesProvider: NesControlDeviceProvider = EmptyNesControlDeviceProvider,
) : NesConsoleControlManager {
    private var console: NesConsole? = null
    private var devices: Array<NesControlDevice> = EmptyControlDevices
    private var writeAddr = 0
    private var writeValue = 0
    private var writePending = 0
    private var prevReadAddr = 0
    private var inputReadFlag = false

    override fun initConsole(console: NesConsole) {
        this.console = console
        updateControlDevices()
    }

    override fun getMemoryRanges(ranges: MemoryRanges) {
        ranges.addHandler(MemoryOperation.Read, 0x4016, 0x4017)
        ranges.addHandler(MemoryOperation.Write, 0x4016)
    }

    override fun readRam(addr: Int): Int {
        val c = console
        inputReadFlag = true
        val address = addr and 0xFFFF
        var value = c?.memoryManager?.getOpenBus(getOpenBusMask(address - 0x4016)) ?: 0
        val currentDevices = devices
        var i = 0
        while (i < currentDevices.size) {
            val device = currentDevices[i]
            if (device.connected) value = value or readDevice(device, address)
            i++
        }
        prevReadAddr = address
        return value and 0xFF
    }

    override fun writeRam(addr: Int, value: Int) {
        val c = console
        writeAddr = addr and 0xFFFF
        writeValue = value and 0xFF
        writePending = if (((c?.getMasterClock() ?: 0L) and 0x01L) != 0L) 1 else 2
    }

    override fun processWrites() {
        val c = console ?: return
        if (writePending != 0 && --writePending == 0) {
            if (writeAddr == 0x4016) {
                c.mapper.epsm?.write(c.memoryManager.getOpenBus(), writeValue)
            }
            val currentDevices = devices
            var i = 0
            while (i < currentDevices.size) {
                val device = currentDevices[i]
                if (device.connected) device.writeRam(writeAddr, writeValue)
                i++
            }
        }
    }

    override fun hasPendingWrites(): Boolean = writePending > 0

    override fun reset(softReset: Boolean) {
        writePending = 0
        inputReadFlag = false
        val currentDevices = devices
        var i = 0
        while (i < currentDevices.size) {
            currentDevices[i].reset(softReset)
            i++
        }
    }

    override fun updateControlDevices() {
        devices = devicesProvider.getControlDevices()
    }

    override fun updateInputState() {
        val currentDevices = devices
        var i = 0
        while (i < currentDevices.size) {
            currentDevices[i].updateInputState()
            i++
        }
    }

    override fun getOpenBusMask(port: Int): Int {
        return when (console?.options?.controllerType) {
            NesConsoleType.Nes101 -> if (port == 0) 0xE4 else 0xE0
            NesConsoleType.Hvc001, NesConsoleType.Hvc101 -> if (port == 0) 0xF8 else 0xE0
            else -> 0xE0
        }
    }

    fun isInputReadFlagSet(): Boolean = inputReadFlag

    fun clearInputReadFlag() {
        inputReadFlag = false
    }

    fun captureSnapshot(): NesControlManagerSnapshot = NesControlManagerSnapshot(
        writeAddr = writeAddr,
        writeValue = writeValue,
        writePending = writePending,
        prevReadAddr = prevReadAddr,
        inputReadFlag = inputReadFlag,
    )

    fun restoreSnapshot(snapshot: NesControlManagerSnapshot) {
        writeAddr = snapshot.writeAddr and 0xFFFF
        writeValue = snapshot.writeValue and 0xFF
        writePending = snapshot.writePending
        prevReadAddr = snapshot.prevReadAddr and 0xFFFF
        inputReadFlag = snapshot.inputReadFlag
        updateControlDevices()
    }

    private fun readDevice(device: NesControlDevice, addr: Int): Int {
        val c = console ?: return device.readRam(addr) and 0xFF
        val cpuCycle = c.getMasterClock()
        var value = device.previousReadValue
        if (prevReadAddr != addr || device.previousReadCycle < cpuCycle - 1) {
            value = device.readRam(addr) and 0xFF
        }
        device.previousReadCycle = cpuCycle
        device.previousReadValue = value
        return value
    }
}

private val EmptyControlDevices: Array<NesControlDevice> = emptyArray()

private object EmptyNesControlDeviceProvider : NesControlDeviceProvider {
    override fun getControlDevices(): Array<NesControlDevice> = EmptyControlDevices
}

enum class NesConsoleType {
    Nes001,
    Nes101,
    Hvc001,
    Hvc101,
}

@Serializable
data class NesControlManagerSnapshot(
    val writeAddr: Int = 0,
    val writeValue: Int = 0,
    val writePending: Int = 0,
    val prevReadAddr: Int = 0,
    val inputReadFlag: Boolean = false,
)
