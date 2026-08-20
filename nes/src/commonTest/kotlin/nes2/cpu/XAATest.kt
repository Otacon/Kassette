package nes2.cpu

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import nes2.fakes.FakeBus

class XAATest : FreeSpec({
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

    "stores unstable immediate result in accumulator" {
        state.pc = 0x8000
        state.a = 0x10
        state.x = 0xCC
        memory[0x8000] = 0x8B
        memory[0x8001] = 0xF0

        val cycles = cpu.step()

        state.a shouldBe 0xC0
        state.x shouldBe 0xCC
        state.z shouldBe false
        state.n shouldBe true
        state.pc shouldBe 0x8002
        cycles shouldBe 2
    }
})
