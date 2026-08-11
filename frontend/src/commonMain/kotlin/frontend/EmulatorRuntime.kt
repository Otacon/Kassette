package frontend

import nes.NesMachine

class EmulatorRuntime(
    private val machine: NesMachine,
    private val audio: AudioPipeline,
    private val input: EmulatorInput,
    private val video: VideoOutput,
) {

    suspend fun step(): Boolean {
        input.poll()
        machine.controller.poll()

        var frameRendered = false
        if (machine.isPoweredOn.value) {
            machine.runUntilFrameYielding {
                input.poll()
                machine.controller.poll()
            }
            audio.submit(machine.apu.samples, machine.apu.sampleCount)
            video.submit(machine.ppu.completedFramebuffer)
            frameRendered = true
        }
        return frameRendered
    }

    fun pause() {
        input.pause()
        machine.controller.poll()
        audio.pause()
    }

    fun close() {
        input.close()
        machine.controller.poll()
    }
}
