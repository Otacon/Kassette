package nes2.cpu

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import nes2.fakes.FakeBus

class JSRTest : FreeSpec({
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



    "jumps to target address" {
        state = CpuState(
            pc = 0x8000,
            sp = 0xFD,
        )

        memory[0x8000] = 0x20
        memory[0x8001] = 0x34
        memory[0x8002] = 0x12

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        val cycles = cpu.step()

        state.pc shouldBe 0x1234
        cycles shouldBe 6
    }

    "pushes return address onto stack" {
        state = CpuState(
            pc = 0x8000,
            sp = 0xFD,
        )

        memory[0x8000] = 0x20
        memory[0x8001] = 0x34
        memory[0x8002] = 0x12

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        cpu.step()

        // JSR pushes $8002.
        // High byte first at $01FD.
        memory[0x01FD] shouldBe 0x80

        // Then low byte at $01FC.
        memory[0x01FC] shouldBe 0x02

        state.sp shouldBe 0xFB
    }

    "stack pointer wraps while pushing" {
        state = CpuState(
            pc = 0x8000,
            sp = 0x00,
        )

        memory[0x8000] = 0x20
        memory[0x8001] = 0x34
        memory[0x8002] = 0x12

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        cpu.step()

        memory[0x0100] shouldBe 0x80
        memory[0x01FF] shouldBe 0x02

        state.sp shouldBe 0xFE
    }

    "does not modify registers or flags" {
        state = CpuState(
            pc = 0x8000,
            a = 0x11,
            x = 0x22,
            y = 0x33,
            sp = 0xFD,
            status = 0xEF,
        )

        memory[0x8000] = 0x20
        memory[0x8001] = 0x34
        memory[0x8002] = 0x12

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        cpu.step()

        state.a shouldBe 0x11
        state.x shouldBe 0x22
        state.y shouldBe 0x33

        state.c shouldBe true
        state.z shouldBe true
        state.i shouldBe true
        state.d shouldBe true
        state.v shouldBe true
        state.n shouldBe true
    }
})