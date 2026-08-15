package frontend

import nes.NesMachine
import platform.AudioPipeline

class EmulatorRuntime(
    private val machine: NesMachine,
    private val audio: AudioPipeline,
    private val input: EmulatorInput,
    private val frameBuffer: SharedFrameBuffer,
) {
    private val inputPollCallback = {
        input.poll()
        machine.controller.poll()
    }

    var soundEnabled: Boolean = true
        set(value) {
            field = value
            if (!value) audio.stop()
        }

    suspend fun step(): Boolean {
        input.poll()
        machine.controller.poll()

        var frameRendered = false
        if (machine.isPoweredOn.value) {
            machine.runUntilFrameYielding(inputPollCallback)
            if (soundEnabled) audio.submit(machine.apu.samples, machine.apu.sampleCount)
            frameBuffer.submit(machine.ppu.completedFrameColorIds)

            frameRendered = true
        }
        return frameRendered
    }

    fun pause() {
        input.pause()
        machine.controller.poll()
        audio.stop()
    }

    fun close() {
        input.close()
        machine.controller.poll()
    }
}
