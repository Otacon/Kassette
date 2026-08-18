package nes2.cpu

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import nes2.fakes.FakeBus

class FlagInstructionsTest : FreeSpec({

    "CLC" {
        val memory = IntArray(0x10_000)
        val state = CpuState(pc = 0x8000).also {
            it.c = true
            it.z = true
            it.n = true
        }

        memory[state.pc] = 0x18

        val cycles = Cpu6502(
            bus = FakeBus(memory = memory),
            state = state,
        ).step()

        state.c shouldBe false

        state.z shouldBe true
        state.n shouldBe true

        state.pc shouldBe 0x8001
        cycles shouldBe 2
    }

    "SEC" {
        val memory = IntArray(0x10_000)
        val state = CpuState(pc = 0x8000).also {
            it.c = false
            it.z = true
            it.n = true
        }

        memory[state.pc] = 0x38

        val cycles = Cpu6502(
            bus = FakeBus(memory = memory),
            state = state,
        ).step()

        state.c shouldBe true

        state.z shouldBe true
        state.n shouldBe true

        state.pc shouldBe 0x8001
        cycles shouldBe 2
    }

    "CLI" {
        val memory = IntArray(0x10_000)
        val state = CpuState(pc = 0x8000).also {
            it.i = true
            it.c = true
            it.z = true
        }

        memory[state.pc] = 0x58

        val cycles = Cpu6502(
            bus = FakeBus(memory = memory),
            state = state,
        ).step()

        state.i shouldBe false

        state.c shouldBe true
        state.z shouldBe true

        state.pc shouldBe 0x8001
        cycles shouldBe 2
    }

    "CLI delays IRQ recognition by one instruction" {
        val memory = IntArray(0x10_000)

        val state = CpuState(
            pc = 0x8000,
            sp = 0xFD,
            irqLine = true,
            irqPollI = true,
        ).also {
            it.i = true
        }

        memory[0x8000] = 0x58 // CLI
        memory[0x8001] = 0xEA // NOP

        memory[0xFFFE] = 0x00
        memory[0xFFFF] = 0x90

        val cpu = Cpu6502(
            bus = FakeBus(memory),
            state = state,
        )

        // IRQ is masked, so CLI executes.
        cpu.step() shouldBe 2

        state.i shouldBe false
        state.pc shouldBe 0x8001

        // CLI's change hasn't affected polling yet.
        cpu.step() shouldBe 2

        state.pc shouldBe 0x8002

        // Now IRQ is seen.
        cpu.step() shouldBe 7

        state.pc shouldBe 0x9000
    }

    "SEI" {
        val memory = IntArray(0x10_000)
        val state = CpuState(pc = 0x8000).also {
            it.i = false
            it.c = true
            it.z = true
        }

        memory[state.pc] = 0x78

        val cycles = Cpu6502(
            bus = FakeBus(memory = memory),
            state = state,
        ).step()

        state.i shouldBe true

        state.c shouldBe true
        state.z shouldBe true

        state.pc shouldBe 0x8001
        cycles shouldBe 2
    }

    "SEI does not cancel an IRQ recognized with the previous I value" {
        val memory = IntArray(0x10_000)

        val state = CpuState(
            pc = 0x8000,
            sp = 0xFD,
            irqPollI = false,
        ).also {
            it.i = false
        }

        memory[0x8000] = 0x78 // SEI

        memory[0xFFFE] = 0x00
        memory[0xFFFF] = 0x90

        val cpu = Cpu6502(
            bus = FakeBus(memory),
            state = state,
        )

        cpu.step() shouldBe 2

        state.i shouldBe true

        // Represent IRQ becoming asserted during/just after SEI.
        state.irqLine = true

        // Poll used I=false from before SEI.
        cpu.step() shouldBe 7

        state.pc shouldBe 0x9000
    }

    "CLV" {
        val memory = IntArray(0x10_000)
        val state = CpuState(pc = 0x8000).also {
            it.v = true
            it.c = true
            it.n = true
        }

        memory[state.pc] = 0xB8

        val cycles = Cpu6502(
            bus = FakeBus(memory = memory),
            state = state,
        ).step()

        state.v shouldBe false

        state.c shouldBe true
        state.n shouldBe true

        state.pc shouldBe 0x8001
        cycles shouldBe 2
    }

    "CLD" {
        val memory = IntArray(0x10_000)
        val state = CpuState(pc = 0x8000).also {
            it.d = true
            it.c = true
            it.n = true
        }

        memory[state.pc] = 0xD8

        val cycles = Cpu6502(
            bus = FakeBus(memory = memory),
            state = state,
        ).step()

        state.d shouldBe false

        state.c shouldBe true
        state.n shouldBe true

        state.pc shouldBe 0x8001
        cycles shouldBe 2
    }

    "SED" {
        val memory = IntArray(0x10_000)
        val state = CpuState(pc = 0x8000).also {
            it.d = false
            it.c = true
            it.n = true
        }

        memory[state.pc] = 0xF8

        val cycles = Cpu6502(
            bus = FakeBus(memory = memory),
            state = state,
        ).step()

        state.d shouldBe true

        state.c shouldBe true
        state.n shouldBe true

        state.pc shouldBe 0x8001
        cycles shouldBe 2
    }
})