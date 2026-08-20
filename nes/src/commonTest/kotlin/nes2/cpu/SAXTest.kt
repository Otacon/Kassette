package nes2.cpu

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import nes2.fakes.FakeBus

class SAXTest : FreeSpec({
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

    "zero page stores accumulator AND X without changing flags" {
        state.pc = 0x8000
        state.a = 0xF0
        state.x = 0xCC
        state.status = 0xE3
        memory[0x8000] = 0x87
        memory[0x8001] = 0x20

        val cycles = cpu.step()

        memory[0x0020] shouldBe 0xC0
        state.a shouldBe 0xF0
        state.x shouldBe 0xCC
        state.status shouldBe 0xE3
        state.pc shouldBe 0x8002
        cycles shouldBe 3
    }

    "zero page Y wraps" {
        state.pc = 0x8000
        state.a = 0xF0
        state.x = 0xCC
        state.y = 0x02
        memory[0x8000] = 0x97
        memory[0x8001] = 0xFF

        val cycles = cpu.step()

        memory[0x0001] shouldBe 0xC0
        state.pc shouldBe 0x8002
        cycles shouldBe 4
    }

    "absolute" {
        state.pc = 0x8000
        state.a = 0xF0
        state.x = 0xCC
        memory[0x8000] = 0x8F
        memory[0x8001] = 0x34
        memory[0x8002] = 0x12

        val cycles = cpu.step()

        memory[0x1234] shouldBe 0xC0
        state.pc shouldBe 0x8003
        cycles shouldBe 4
    }

    "indirect X" {
        state.pc = 0x8000
        state.a = 0xF0
        state.x = 0x04
        memory[0x8000] = 0x83
        memory[0x8001] = 0x20
        memory[0x0024] = 0x34
        memory[0x0025] = 0x12

        val cycles = cpu.step()

        memory[0x1234] shouldBe 0x00
        state.pc shouldBe 0x8002
        cycles shouldBe 6
    }
})
