package nes2.cpu

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import nes2.fakes.FakeBus

class JMPTest : FreeSpec({
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



    "absolute jumps to target address" {
        state = CpuState(
            pc = 0x8000,
        )

        memory[0x8000] = 0x4C
        memory[0x8001] = 0x34
        memory[0x8002] = 0x12

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        val cycles = cpu.step()

        state.pc shouldBe 0x1234
        cycles shouldBe 3
    }

    "indirect jumps to address read from pointer" {
        state = CpuState(
            pc = 0x8000,
        )

        memory[0x8000] = 0x6C
        memory[0x8001] = 0x00
        memory[0x8002] = 0x20

        // Pointer is $2000.
        // [$2000/$2001] -> $1234
        memory[0x2000] = 0x34
        memory[0x2001] = 0x12

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        val cycles = cpu.step()

        state.pc shouldBe 0x1234
        cycles shouldBe 5
    }

    "indirect reproduces the 6502 page boundary bug" {
        state = CpuState(
            pc = 0x8000,
        )

        memory[0x8000] = 0x6C
        memory[0x8001] = 0xFF
        memory[0x8002] = 0x12

        // JMP ($12FF)
        //
        // Real 6502 behavior:
        // low byte  <- $12FF
        // high byte <- $1200
        //
        // NOT $1300.
        memory[0x12FF] = 0x34
        memory[0x1200] = 0x56

        // Put a deliberately different value here so the test
        // catches an implementation that incorrectly reads $1300.
        memory[0x1300] = 0xAB

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        val cycles = cpu.step()

        state.pc shouldBe 0x5634
        cycles shouldBe 5
    }

    "absolute target can be zero" {
        state = CpuState(
            pc = 0x8000,
        )

        memory[0x8000] = 0x4C
        memory[0x8001] = 0x00
        memory[0x8002] = 0x00

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        val cycles = cpu.step()

        state.pc shouldBe 0x0000
        cycles shouldBe 3
    }

    "absolute target can be FFFF" {
        state = CpuState(
            pc = 0x8000,
        )

        memory[0x8000] = 0x4C
        memory[0x8001] = 0xFF
        memory[0x8002] = 0xFF

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        val cycles = cpu.step()

        state.pc shouldBe 0xFFFF
        cycles shouldBe 3
    }

    "does not modify registers or flags" {
        state = CpuState(
            pc = 0x8000,
            a = 0x11,
            x = 0x22,
            y = 0x33,
            sp = 0x44,
            status = 0xEF,
        )

        memory[0x8000] = 0x4C
        memory[0x8001] = 0x34
        memory[0x8002] = 0x12

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        cpu.step()

        state.a shouldBe 0x11
        state.x shouldBe 0x22
        state.y shouldBe 0x33
        state.sp shouldBe 0x44

        state.c shouldBe true
        state.z shouldBe true
        state.i shouldBe true
        state.d shouldBe true
        state.v shouldBe true
        state.n shouldBe true
    }
})