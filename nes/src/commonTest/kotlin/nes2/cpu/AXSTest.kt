package nes2.cpu

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import nes2.fakes.FakeBus

class AXSTest : FreeSpec({
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

    "stores A AND X minus immediate into X" {
        state.pc = 0x8000
        state.a = 0xF0
        state.x = 0xCC
        memory[0x8000] = 0xCB
        memory[0x8001] = 0x40

        val cycles = cpu.step()

        state.a shouldBe 0xF0
        state.x shouldBe 0x80
        state.c shouldBe true
        state.z shouldBe false
        state.n shouldBe true
        state.pc shouldBe 0x8002
        cycles shouldBe 2
    }

    "clears carry on borrow" {
        state.pc = 0x8000
        state.a = 0x0F
        state.x = 0x0F
        memory[0x8000] = 0xCB
        memory[0x8001] = 0x10

        cpu.step()

        state.x shouldBe 0xFF
        state.c shouldBe false
        state.z shouldBe false
        state.n shouldBe true
    }
})
