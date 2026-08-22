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

package nes.ppu

import nes.cpu.ConsoleRegion

class NsfPpu : NesPpu() {
    override fun updateTimings(region: ConsoleRegion, overclockEnabled: Boolean) {
        super.updateTimings(region, overclockEnabled = false)
    }

    override fun processScanline() {}
    override fun drawPixel() {}
    override fun getPixelBrightness(x: Int, y: Int): Int = 0

    override fun reset(softReset: Boolean) {
        // Force a full reset for NSF playback, matching Mesen's NsfPpu behavior.
        super.reset(false)
    }

    override fun run(masterClock: Long) {
        do {
            if (cycle < 340) {
                cycle++
            } else {
                processScanlineFirstCycle()
            }
            this.masterClock += masterClockDivider.toLong()
        } while (this.masterClock + masterClockDivider <= masterClock)
    }
}
