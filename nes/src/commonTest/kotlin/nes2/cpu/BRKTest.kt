package nes2.cpu

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import nes2.fakes.FakeBus

class BRKTest : FreeSpec({
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



    "jumps to IRQ BRK vector" {
        state = CpuState(
            pc = 0x8000,
            sp = 0xFD,
        )

        memory[0x8000] = 0x00

        memory[0xFFFE] = 0x34
        memory[0xFFFF] = 0x12

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        val cycles = cpu.step()

        state.pc shouldBe 0x1234
        cycles shouldBe 7
    }

    "pushes return address after BRK padding byte" {
        state = CpuState(
            pc = 0x8000,
            sp = 0xFD,
        )

        memory[0x8000] = 0x00

        memory[0xFFFE] = 0x34
        memory[0xFFFF] = 0x12

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        cpu.step()

        // BRK at $8000 returns to $8002.
        //
        // High byte pushed first.
        memory[0x01FD] shouldBe 0x80

        // Then low byte.
        memory[0x01FC] shouldBe 0x02

        state.sp shouldBe 0xFA
    }

    "pushes status with B and U set" {
        state = CpuState(
            pc = 0x8000,
            sp = 0xFD,
            status = 0x6B,
        )

        memory[0x8000] = 0x00

        memory[0xFFFE] = 0x34
        memory[0xFFFF] = 0x12

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        cpu.step()

        val pushedStatus = memory[0x01FB]

        // B
        (pushedStatus and 0x10) shouldBe 0x10

        // U
        (pushedStatus and 0x20) shouldBe 0x20

        // Carry
        (pushedStatus and 0x01) shouldBe 0x01

        // Zero
        (pushedStatus and 0x02) shouldBe 0x02

        // Decimal
        (pushedStatus and 0x08) shouldBe 0x08

        // Overflow
        (pushedStatus and 0x40) shouldBe 0x40
    }

    "sets interrupt disable flag" {
        state = CpuState(
            pc = 0x8000,
            sp = 0xFD,
            status = 0x20,
        )

        memory[0x8000] = 0x00

        memory[0xFFFE] = 0x34
        memory[0xFFFF] = 0x12

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        cpu.step()

        state.i shouldBe true
    }

    "pushes status before setting interrupt disable" {
        state = CpuState(
            pc = 0x8000,
            sp = 0xFD,
            status = 0x20,
        )

        memory[0x8000] = 0x00

        memory[0xFFFE] = 0x34
        memory[0xFFFF] = 0x12

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        cpu.step()

        val pushedStatus = memory[0x01FB]

        // The old I value is what gets pushed.
        (pushedStatus and 0x04) shouldBe 0

        // The live CPU status then gets I set.
        state.i shouldBe true
    }

    "stack pointer wraps" {
        state = CpuState(
            pc = 0x8000,
            sp = 0x01,
        )

        memory[0x8000] = 0x00

        memory[0xFFFE] = 0x34
        memory[0xFFFF] = 0x12

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        cpu.step()

        // Return address high.
        memory[0x0101] shouldBe 0x80

        // Return address low.
        memory[0x0100] shouldBe 0x02

        // Status wraps to $01FF.
        (memory[0x01FF] and 0x30) shouldBe 0x30

        state.sp shouldBe 0xFE
    }
})