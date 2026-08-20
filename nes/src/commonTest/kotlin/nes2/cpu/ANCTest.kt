package nes2.cpu

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import nes2.fakes.FakeBus

class ANCTest : FreeSpec({
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

    "ANDs immediate into accumulator and copies negative to carry" {
        listOf(0x0B, 0x2B).forEach { opcode ->
            memory = IntArray(0x10_000)
            state = CpuState(pc = 0x8000, a = 0xF0)
            bus = FakeBus(memory = memory)
            cpu = Cpu6502(bus = bus, state = state)

            memory[0x8000] = opcode
            memory[0x8001] = 0x80

            val cycles = cpu.step()

            state.a shouldBe 0x80
            state.c shouldBe true
            state.z shouldBe false
            state.n shouldBe true
            state.pc shouldBe 0x8002
            cycles shouldBe 2
        }
    }

    "clears carry when result is positive" {
        state.pc = 0x8000
        state.a = 0x7F
        state.c = true
        memory[0x8000] = 0x0B
        memory[0x8001] = 0x01

        cpu.step()

        state.a shouldBe 0x01
        state.c shouldBe false
        state.z shouldBe false
        state.n shouldBe false
    }
})
