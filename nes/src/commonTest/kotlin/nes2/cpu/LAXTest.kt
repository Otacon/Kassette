package nes2.cpu

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import nes2.fakes.FakeBus

class LAXTest : FreeSpec({
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

    "immediate loads accumulator and X" {
        state.pc = 0x8000
        memory[0x8000] = 0xAB
        memory[0x8001] = 0x42

        val cycles = cpu.step()

        state.a shouldBe 0x42
        state.x shouldBe 0x42
        state.z shouldBe false
        state.n shouldBe false
        state.pc shouldBe 0x8002
        cycles shouldBe 2
    }

    "sets zero and negative flags from loaded value" {
        state.pc = 0x8000
        memory[0x8000] = 0xA7
        memory[0x8001] = 0x20
        memory[0x0020] = 0x00

        cpu.step()

        state.a shouldBe 0x00
        state.x shouldBe 0x00
        state.z shouldBe true
        state.n shouldBe false

        state.pc = 0x8002
        memory[0x8002] = 0xA7
        memory[0x8003] = 0x21
        memory[0x0021] = 0x80

        cpu.step()

        state.a shouldBe 0x80
        state.x shouldBe 0x80
        state.z shouldBe false
        state.n shouldBe true
    }

    "zero page Y wraps" {
        state.pc = 0x8000
        state.y = 0x02
        memory[0x8000] = 0xB7
        memory[0x8001] = 0xFF
        memory[0x0001] = 0x42

        val cycles = cpu.step()

        state.a shouldBe 0x42
        state.x shouldBe 0x42
        state.pc shouldBe 0x8002
        cycles shouldBe 4
    }

    "absolute" {
        state.pc = 0x8000
        memory[0x8000] = 0xAF
        memory[0x8001] = 0x34
        memory[0x8002] = 0x12
        memory[0x1234] = 0x42

        val cycles = cpu.step()

        state.a shouldBe 0x42
        state.x shouldBe 0x42
        state.pc shouldBe 0x8003
        cycles shouldBe 4
    }

    "absolute Y with page crossing penalty" {
        state.pc = 0x8000
        state.y = 0x01
        memory[0x8000] = 0xBF
        memory[0x8001] = 0xFF
        memory[0x8002] = 0x12
        memory[0x1300] = 0x42

        val cycles = cpu.step()

        state.a shouldBe 0x42
        state.x shouldBe 0x42
        state.pc shouldBe 0x8003
        cycles shouldBe 5
    }

    "indirect X" {
        state.pc = 0x8000
        state.x = 0x04
        memory[0x8000] = 0xA3
        memory[0x8001] = 0x20
        memory[0x0024] = 0x34
        memory[0x0025] = 0x12
        memory[0x1234] = 0x42

        val cycles = cpu.step()

        state.a shouldBe 0x42
        state.x shouldBe 0x42
        state.pc shouldBe 0x8002
        cycles shouldBe 6
    }

    "indirect Y with page crossing penalty" {
        state.pc = 0x8000
        state.y = 0x01
        memory[0x8000] = 0xB3
        memory[0x8001] = 0x20
        memory[0x0020] = 0xFF
        memory[0x0021] = 0x12
        memory[0x1300] = 0x42

        val cycles = cpu.step()

        state.a shouldBe 0x42
        state.x shouldBe 0x42
        state.pc shouldBe 0x8002
        cycles shouldBe 6
    }
})
