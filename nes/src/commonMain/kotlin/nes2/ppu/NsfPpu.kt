package nes2.ppu

import nes2.cpu.ConsoleRegion

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
