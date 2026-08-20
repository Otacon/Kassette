package nes2.cpu

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import nes2.fakes.FakeBus

class AHXTest : FreeSpec({
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

    "indirect Y stores A AND X AND high byte plus one" {
        state.pc = 0x8000
        state.a = 0xFF
        state.x = 0xCC
        state.y = 0x01
        memory[0x8000] = 0x93
        memory[0x8001] = 0x20
        memory[0x0020] = 0x34
        memory[0x0021] = 0x12

        val cycles = cpu.step()

        memory[0x1235] shouldBe 0x00
        bus.writes shouldBe listOf(FakeBus.Write(0x1235, 0x00))
        state.a shouldBe 0xFF
        state.x shouldBe 0xCC
        state.pc shouldBe 0x8002
        cycles shouldBe 6
    }

    "absolute Y stores A AND X AND high byte plus one" {
        state.pc = 0x8000
        state.a = 0x3F
        state.x = 0x1F
        state.y = 0x01
        memory[0x8000] = 0x9F
        memory[0x8001] = 0x34
        memory[0x8002] = 0x12

        val cycles = cpu.step()

        memory[0x1235] shouldBe 0x13
        bus.writes shouldBe listOf(FakeBus.Write(0x1235, 0x13))
        state.pc shouldBe 0x8003
        cycles shouldBe 5
    }

    "absolute Y page crossing uses unstable destination" {
        state.pc = 0x8000
        state.a = 0xFF
        state.x = 0x12
        state.y = 0x01
        memory[0x8000] = 0x9F
        memory[0x8001] = 0xFF
        memory[0x8002] = 0x12

        cpu.step()

        bus.writes shouldBe listOf(FakeBus.Write(0x1200, 0x12))
    }
})
