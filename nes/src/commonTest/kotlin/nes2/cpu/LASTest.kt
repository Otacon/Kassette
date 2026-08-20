package nes2.cpu

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import nes2.fakes.FakeBus

class LASTest : FreeSpec({
    lateinit var memory: IntArray
    lateinit var state: CpuState
    lateinit var bus: FakeBus
    lateinit var cpu: Cpu6502

    beforeTest {
        memory = IntArray(0x10_000)
        state = CpuState()
        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
    }

    "loads memory AND stack pointer into accumulator X and stack pointer" {
        state.pc = 0x8000
        state.sp = 0xF0
        state.y = 0x01
        memory[0x8000] = 0xBB
        memory[0x8001] = 0x34
        memory[0x8002] = 0x12
        memory[0x1235] = 0xCC

        val cycles = cpu.step()

        state.a shouldBe 0xC0
        state.x shouldBe 0xC0
        state.sp shouldBe 0xC0
        state.z shouldBe false
        state.n shouldBe true
        state.pc shouldBe 0x8003
        cycles shouldBe 4
    }

    "sets zero when result is zero" {
        state.pc = 0x8000
        state.sp = 0x0F
        state.y = 0x01
        memory[0x8000] = 0xBB
        memory[0x8001] = 0x34
        memory[0x8002] = 0x12
        memory[0x1235] = 0xF0

        cpu.step()

        state.a shouldBe 0x00
        state.x shouldBe 0x00
        state.sp shouldBe 0x00
        state.z shouldBe true
        state.n shouldBe false
    }

    "absolute Y with page crossing penalty" {
        state.pc = 0x8000
        state.sp = 0xFF
        state.y = 0x01
        memory[0x8000] = 0xBB
        memory[0x8001] = 0xFF
        memory[0x8002] = 0x12
        memory[0x1300] = 0x42

        val cycles = cpu.step()

        state.a shouldBe 0x42
        state.x shouldBe 0x42
        state.sp shouldBe 0x42
        state.pc shouldBe 0x8003
        cycles shouldBe 5
    }
})
